package com.soda.helper;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;

public final class Logs {
    private static final String TAG = "SodaHelper";
    private static final Object LOCK = new Object();
    private static File logFile;

    private Logs() {
    }

    public static void init(Context context) {
        File dir = new File(context.getFilesDir(), "logs");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "创建日志目录失败");
        }
        String day = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        logFile = new File(dir, day + ".log");
    }

    public static void i(String message) {
        write("INFO", message);
    }

    public static void w(String message) {
        write("WARN", message);
    }

    public static void e(String message) {
        write("ERROR", message);
    }

    private static void write(String level, String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date());
        String line = "[" + time + "][" + level + "] " + message;
        Log.i(TAG, line);
        if (logFile == null) return;
        synchronized (LOCK) {
            try {
                FileWriter writer = new FileWriter(logFile, true);
                writer.write(line + "\n");
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "写日志失败", e);
            }
        }
    }

    public static String tail(int lines) {
        if (logFile == null || !logFile.exists()) return "";
        synchronized (LOCK) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(logFile));
                LinkedList<String> list = new LinkedList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    list.add(line);
                    if (list.size() > lines) list.removeFirst();
                }
                StringBuilder sb = new StringBuilder();
                for (String item : list) {
                    sb.append(item).append('\n');
                }
                return sb.toString();
            } catch (IOException e) {
                return "";
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
    }
}
