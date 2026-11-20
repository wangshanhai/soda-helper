package com.soda.helper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "soda_engine";
    private static final int NOTIFICATION_ID = 1001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updater = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            handler.postDelayed(this, 3000);
        }
    };

    public static void start(Context context) {
        try {
            Intent intent = new Intent(context, KeepAliveService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Throwable t) {
            Logs.w("启动前台保活服务失败：" + t);
        }
    }

    public static void stop(Context context) {
        try {
            context.stopService(new Intent(context, KeepAliveService.class));
        } catch (Throwable t) {
            Logs.w("停止前台保活服务失败：" + t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        handler.post(updater);
        Logs.i("前台保活服务已启动");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Logs.i("前台保活服务已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID, "领取状态", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("汽水助手自动领取运行状态");
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("汽水助手正在领取")
                .setContentText(statusText())
                .setContentIntent(content)
                .setOngoing(true);

        try {
            Intent stop = new Intent(this, StopReceiver.class);
            PendingIntent stopPending = PendingIntent.getBroadcast(this, 1, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, R.mipmap.ic_launcher),
                    "停止", stopPending).build());
        } catch (Throwable t) {
            Logs.w("通知停止按钮添加失败：" + t);
        }
        return builder.build();
    }

    private void updateNotification() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification());
            }
        } catch (Throwable t) {
            Logs.w("更新通知失败：" + t);
        }
    }

    private String statusText() {
        SodaService service = SodaService.get();
        if (service == null) return "等待无障碍服务";
        int round = service.getRound();
        String state = service.getStateText();
        return (round > 0 ? "第 " + round + " 轮 · " : "") + state;
    }
}
