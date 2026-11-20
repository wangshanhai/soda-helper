package com.soda.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class Notify {
    private static final String CHANNEL_ID = "soda_finish";
    private static final int NOTIFICATION_ID = 1002;

    private Notify() {
    }

    public static void finished(Context context, String title, String text) {
        try {
            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager == null) return;

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "领取完成提醒", NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("汽水助手领取完成或异常停止时的提醒");
                channel.setSound(null, null);
                manager.createNotificationChannel(channel);
            }

            Intent open = new Intent(context, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent content = PendingIntent.getActivity(context, 2, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(context, CHANNEL_ID)
                    : new Notification.Builder(context);

            builder.setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(content)
                    .setAutoCancel(true);

            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable t) {
            Logs.w("发送完成通知失败：" + t);
        }
    }
}
