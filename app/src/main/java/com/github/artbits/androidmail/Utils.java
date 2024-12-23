package com.github.artbits.androidmail;

import android.annotation.SuppressLint;
import android.content.Context;
import android.icu.text.SimpleDateFormat;
import android.text.TextUtils;
import android.widget.Toast;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat format = new SimpleDateFormat("yyyy-M-dd HH:mm");


    public static boolean isNullOrEmpty(Object... args) {
        for (Object o : args) {
            if (o == null) {
                return true;
            }
            if (o instanceof String && ((String) o).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmail(String email) {
        if (TextUtils.isEmpty(email)) return false;
        Pattern p = Pattern.compile("\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*");//复杂匹配
        Matcher m = p.matcher(email);
        return m.matches();
    }

    public static void toast(Context context, String s) {
        Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
    }


    public static String getDate(long time) {
        return format.format(new Date(time));
    }

}
