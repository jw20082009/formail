package com.github.artbits.androidmail.view;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;

import com.github.artbits.androidmail.App;
import com.github.artbits.androidmail.R;
import com.github.artbits.androidmail.Utils;
import com.github.artbits.androidmail.databinding.ActivityConfigBinding;
import com.github.artbits.androidmail.store.UserInfo;
import com.github.artbits.mailkit.MailKit;

import java.util.regex.Pattern;

public class ConfigActivity extends BaseActivity {
    private final String TAG = "ConfigActivity";
    private ActivityConfigBinding binding;
    private UserInfo userInfo;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = setContentView(this, R.layout.activity_config);

        userInfo = App.db.collection(UserInfo.class).findFirst();
        boolean isLogin = (userInfo != null);
        setToolbar(binding.toolbar, "服务器配置", isLogin);
        binding.accountText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                Log.e(TAG, "beforeTextChanged:" + s);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Log.e(TAG, "onTextChanged:" + s);
            }

            @Override
            public void afterTextChanged(Editable s) {
                Log.e(TAG, "afterTextChanged:" + s);
                String account = s.toString();
                if (!Utils.isEmail(account) || (binding.accountText.getTag() instanceof String && TextUtils.equals((CharSequence) binding.accountText.getTag(), account))) {
                    return;
                }
                binding.accountText.setTag(account);
                UserInfo info = new UserInfo();
                info.password = binding.passwordText.getText().toString();
                info.nickname = account.substring(0, account.lastIndexOf("@"));
                info.SMTPHost = "smtp." + account.substring(account.lastIndexOf("@") + 1);
                info.SMTPPort = binding.smtpEncryptionSwt.isChecked()? 465: 993;
                info.SMTPSSLEnable = binding.smtpEncryptionSwt.isChecked();
                info.IMAPHost = "imap." + account.substring(account.lastIndexOf("@") + 1);
                info.IMAPPort = 993;
                info.IMAPSSLEnable = binding.imapEncryptionSwt.isChecked();
                initData(info, false);
            }
        });
        binding.smtpEncryptionSwt.setOnCheckedChangeListener((buttonView, isChecked) -> binding.smtpPortText.setText(isChecked? "465": "993"));
        if (isLogin) {
            initData(userInfo);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_config, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        if (item.getItemId() == R.id.config_confirm) {
            auth();
        }
        return super.onOptionsItemSelected(item);
    }

    private void initData(UserInfo userInfo) {
        initData(userInfo, true);
    }

    private void initData(UserInfo userInfo, boolean refreshAccount) {
        if (refreshAccount) {
            binding.accountText.setText(userInfo.account);
        }
        binding.passwordText.setText(userInfo.password);
        binding.nicknameText.setText(userInfo.nickname);
        binding.smtpHostText.setText(userInfo.SMTPHost);
        binding.smtpPortText.setText(String.valueOf(userInfo.SMTPPort));
        binding.imapHostText.setText(userInfo.IMAPHost);
        binding.imapPortText.setText(String.valueOf(userInfo.IMAPPort));
        binding.smtpEncryptionSwt.setChecked(userInfo.SMTPSSLEnable);
        binding.imapEncryptionSwt.setChecked(userInfo.IMAPSSLEnable);
    }


    private void auth() {
        String account = binding.accountText.getText().toString();
        String password = binding.passwordText.getText().toString();
        String nickname = binding.nicknameText.getText().toString();
        String smtpHost = binding.smtpHostText.getText().toString();
        String smtpPort = binding.smtpPortText.getText().toString();
        String imapHost = binding.imapHostText.getText().toString();
        String imapPort = binding.imapPortText.getText().toString();
        if (Utils.isNullOrEmpty(account, password, nickname, smtpHost, smtpPort, imapHost, imapPort)) {
            Utils.toast(this, "配置参数都不能为空");
            return;
        }

        MailKit.Config config = new MailKit.Config(c -> {
           c.account = account;
           c.password = password;
           c.nickname = nickname;
           c.SMTPHost = smtpHost;
           c.IMAPHost = imapHost;
           c.SMTPPort = Integer.valueOf(smtpPort);
           c.IMAPPort = Integer.valueOf(imapPort);
           c.SMTPSSLEnable = binding.smtpEncryptionSwt.isChecked();
           c.IMAPSSLEnable = binding.imapEncryptionSwt.isChecked();
        });

        LoadingDialog dialog = new LoadingDialog();
        dialog.setTipWord("检查邮箱配置中...");
        dialog.show();

        MailKit.auth(config, () -> {
            if (userInfo == null) {
                App.db.collection(UserInfo.class).save(UserInfo.of(u -> {
                    u.account = config.account;
                    u.password = config.password;
                    u.nickname = config.nickname;
                    u.SMTPHost = config.SMTPHost;
                    u.SMTPPort = config.SMTPPort;
                    u.IMAPHost = config.IMAPHost;
                    u.IMAPPort = config.IMAPPort;
                    u.SMTPSSLEnable = config.SMTPSSLEnable;
                    u.IMAPSSLEnable = config.IMAPSSLEnable;
                }));
                dialog.dismiss();
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                userInfo.account = config.account;
                userInfo.password = config.password;
                userInfo.nickname = config.nickname;
                userInfo.SMTPHost = config.SMTPHost;
                userInfo.SMTPPort = config.SMTPPort;
                userInfo.IMAPHost = config.IMAPHost;
                userInfo.IMAPPort = config.IMAPPort;
                userInfo.SMTPSSLEnable = config.SMTPSSLEnable;
                userInfo.IMAPSSLEnable = config.IMAPSSLEnable;
                App.db.collection(UserInfo.class).save(userInfo);
                finish();
            }
        }, e -> {
            dialog.dismiss();
            Utils.toast(this, e.getMessage());
        });
    }

}