package com.github.artbits.mailkit;

import android.util.Log;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.mail.FetchProfile;
import javax.mail.Flags;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.mail.search.FromStringTerm;
import javax.mail.search.RecipientStringTerm;
import javax.mail.search.SubjectTerm;

class MailFolder {
    private final String TAG = "MailFolder";
    private final MailKit.Config config;
    private final String folderName;


    MailFolder(MailKit.Config config, String folderName) {
        this.config = config;
        this.folderName = folderName;
    }

    public void sync(long[] localUIDArray, Consumer<List<MailKit.Msg>> consumer, Consumer<List<Long>> deleteConsumer, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            synchronized (MailFolder.this) {
                Log.e("uidHandler", "startSync ");
                try(IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                    long start = System.currentTimeMillis();
                    final Semaphore semaphore = new Semaphore(0);
                    if (localUIDArray.length != 0) {
                        Arrays.sort(localUIDArray);
                    }
                    //获取message数组
                    Message[] msgList = folder.getMessages();
                    MailKit.thread.execute(() -> {
                        try {
                            long[] newArray = UIDHandler.syncNewUIDArray(folder, localUIDArray, msgList);
                            if (newArray != null && newArray.length > 0) {
                                Message[] messages = folder.getMessagesByUID(newArray);
                                FetchProfile fetchProfile = new FetchProfile();
                                fetchProfile.add(FetchProfile.Item.ENVELOPE);
                                fetchProfile.add(FetchProfile.Item.FLAGS);
                                folder.fetch(messages, fetchProfile);
                                List<MailKit.Msg> newMsgList = new ArrayList<>();
                                for (Message message : messages) {
                                    IMAPMessage imapMessage = (IMAPMessage) message;
                                    long uid = folder.getUID(imapMessage);
                                    MailKit.Msg msg = Tools.getMsgHead(uid, imapMessage);
                                    if (msg != null) {
                                        Log.e("uidHandler", "onResult, onNewMsg:" + msg);
                                        newMsgList.add(msg);
                                    }
                                }
                                Log.e(TAG, "syncNewUIDArray Success");
                                MailKit.handler.post(() -> consumer.accept(newMsgList));
                            }
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        } finally {
                            semaphore.release();
                        }
                    });

                    MailKit.thread.execute(() -> {
                        try {
                            long[] delArray = UIDHandler.syncDeletedUIDArray(folder, localUIDArray, msgList);
                            final List<Long> delUIDList = new ArrayList<>();
                            if (delArray != null) {
                                for (long uid : delArray) {
                                    delUIDList.add(uid);
                                }
                            }
                            Log.e(TAG, "syncDeletedUIDArray Success");
                            MailKit.handler.post(() ->deleteConsumer.accept(delUIDList));
                        } catch (MessagingException e) {
                            throw new RuntimeException(e);
                        } finally {
                            semaphore.release();
                        }
                    });
                    if (!semaphore.tryAcquire(2, 120, TimeUnit.SECONDS)) {
                        Log.e(TAG, "sync timeOut");
                    } else {
                        Log.e(TAG, "sync Success");
                    }
                } catch (Exception e) {
                    MailKit.handler.post(() -> consumer2.accept(e));
                }
            }
        });
        MailKit.thread.execute(() -> {

        });
    }


    public void load(long minUID, Consumer<List<MailKit.Msg>> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            synchronized (MailFolder.this) {
                try(IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                    long[] uidList = UIDHandler.nextUIDArray(folder, minUID);
                    Message[] messages = folder.getMessagesByUID(uidList);
                    FetchProfile fetchProfile = new FetchProfile();
                    fetchProfile.add(FetchProfile.Item.ENVELOPE);
                    fetchProfile.add(FetchProfile.Item.FLAGS);
                    folder.fetch(messages, fetchProfile);
                    List<MailKit.Msg> msgList = new ArrayList<>();
                    for (Message message: messages){
                        IMAPMessage imapMessage = (IMAPMessage) message;
                        long uid = folder.getUID(imapMessage);
                        MailKit.Msg msg = Tools.getMsgHead(uid, imapMessage);
                        if (msg != null) {
                            msgList.add(msg);
                        }
                    }
                    MailKit.handler.post(() -> consumer1.accept(msgList));
                } catch (MessagingException e) {
                    MailKit.handler.post(() -> consumer2.accept(e));
                }
            }
        });
    }


    public void getMsg(long uid, Consumer<MailKit.Msg> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                IMAPMessage imapMessage = (IMAPMessage) folder.getMessageByUID(uid);
                if (imapMessage != null) {
                    MailKit.Msg msg = Tools.toMsg(uid, imapMessage);
                    MailKit.handler.post(() -> consumer1.accept(msg));
                }
            } catch (Exception e) {
                MailKit.handler.post(() -> consumer2.accept(e));
            }
        });
    }


    public void count(BiConsumer<Integer, Integer> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                int total = folder.getMessageCount();
                int unreadCount = folder.getUnreadMessageCount();
                MailKit.handler.post(() -> consumer1.accept(total, unreadCount));
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer2.accept(e));
            }
        });
    }


    public void move(String targetFolderName, long[] uidList, Runnable runnable, Consumer<Exception> consumer) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config);
                 IMAPFolder originalFolder = Tools.getFolder(store, folderName, config);
                 IMAPFolder targetFolder = Tools.getFolder(store, targetFolderName, config)) {
                Message[] msgList = originalFolder.getMessagesByUID(uidList);
                originalFolder.copyMessages(msgList, targetFolder);
                originalFolder.setFlags(msgList, new Flags(Flags.Flag.DELETED), true);
                MailKit.handler.post(runnable);
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer.accept(e));
            }
        });
    }


    public void delete(long[] uidList, Runnable runnable, Consumer<Exception> consumer) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                Message[] msgList = folder.getMessagesByUID(uidList);
                folder.setFlags(msgList, new Flags(Flags.Flag.DELETED), true);
                MailKit.handler.post(runnable);
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer.accept(e));
            }
        });
    }


    public void star(long[] uidList, boolean status, Runnable runnable, Consumer<Exception> consumer) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                Message[] msgList = folder.getMessagesByUID(uidList);
                folder.setFlags(msgList, new Flags(Flags.Flag.FLAGGED), status);
                MailKit.handler.post(runnable);
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer.accept(e));
            }
        });
    }


    public void readStatus(long[] uidList, boolean status, Runnable runnable, Consumer<Exception> consumer) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                Message[] msgList = folder.getMessagesByUID(uidList);
                folder.setFlags(msgList, new Flags(Flags.Flag.SEEN), status);
                MailKit.handler.post(runnable);
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer.accept(e));
            }
        });
    }


    public void searchBySubject(String subject, Consumer<List<MailKit.Msg>> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                SubjectTerm subjectTerm = new SubjectTerm(subject);
                Message[] messages = folder.search(subjectTerm);
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                folder.fetch(messages, fp);
                List<MailKit.Msg> msgList = Tools.getMsgHeads(folder, messages);
                MailKit.handler.post(() -> consumer1.accept(msgList));
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer2.accept(e));
            }
        });
    }


    public void searchByFrom(String nickname, Consumer<List<MailKit.Msg>> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                FromStringTerm fromStringTerm = new FromStringTerm(nickname);
                Message[] messages = folder.search(fromStringTerm);
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                folder.fetch(messages, fp);
                List<MailKit.Msg> msgList = Tools.getMsgHeads(folder, messages);
                MailKit.handler.post(() -> consumer1.accept(msgList));
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer2.accept(e));
            }
        });
    }


    public void searchByTo(String nickname, Consumer<List<MailKit.Msg>> consumer1, Consumer<Exception> consumer2) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                RecipientStringTerm stringTerm = new RecipientStringTerm(MimeMessage.RecipientType.TO, nickname);
                Message[] messages = folder.search(stringTerm);
                FetchProfile fp = new FetchProfile();
                fp.add(FetchProfile.Item.ENVELOPE);
                folder.fetch(messages, fp);
                List<MailKit.Msg> msgList = Tools.getMsgHeads(folder, messages);
                MailKit.handler.post(() -> consumer1.accept(msgList));
            } catch (MessagingException e) {
                MailKit.handler.post(() -> consumer2.accept(e));
            }
        });
    }


    void save(MailKit.Draft draft, Runnable runnable, Consumer<Exception> consumer) {
        MailKit.thread.execute(() -> {
            try (IMAPStore store = Tools.getStore(config); IMAPFolder folder = Tools.getFolder(store, folderName, config)) {
                MimeMessage message = Tools.toMimeMessage(config, draft);
                folder.appendMessages(new MimeMessage[]{message});
                MailKit.handler.post(runnable);
            } catch (MalformedURLException | MessagingException e) {
                MailKit.handler.post(() -> consumer.accept(e));
            }
        });
    }

}