package com.soda.helper;

import android.content.Context;
import android.content.SharedPreferences;

public final class DonatePrompt {
    private DonatePrompt() {
    }

    public static void markSuccess(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences("usage", Context.MODE_PRIVATE);
            prefs.edit().putInt("successCount",
                    prefs.getInt("successCount", 0) + 1).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean shouldShow(Context context) {
        try {
            SharedPreferences donate = context.getSharedPreferences("donate", Context.MODE_PRIVATE);
            if (donate.getBoolean("shown", false)) return false;
            SharedPreferences usage = context.getSharedPreferences("usage", Context.MODE_PRIVATE);
            return usage.getInt("successCount", 0) >= 1;
        } catch (Exception e) {
            return false;
        }
    }

    public static void markShown(Context context) {
        try {
            context.getSharedPreferences("donate", Context.MODE_PRIVATE)
                    .edit().putBoolean("shown", true).apply();
        } catch (Exception ignored) {
        }
    }
}
