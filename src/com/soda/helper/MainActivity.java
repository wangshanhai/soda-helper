package com.soda.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int COLOR_TEXT = Color.rgb(23, 23, 23);
    private static final int COLOR_MUTED = Color.rgb(82, 82, 91);
    private static final int COLOR_OK = Color.rgb(10, 184, 97);
    private static final int COLOR_BAD = Color.rgb(232, 70, 58);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView banner;
    private TextView accessibilityStatus;
    private TextView batteryStatus;
    private TextView overlayStatus;
    private TextView notificationStatus;
    private TextView statusView;
    private TextView logView;
    private Button accessibilityButton;
    private boolean autoStarted;
    private int autoRetries;
    private boolean donateShownThisSession;

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            logView.setText(Logs.tail(80));
            handler.postDelayed(this, 1000);
        }
    };

    private final Runnable autoStartTask = new Runnable() {
        @Override
        public void run() {
            if (autoStarted) return;
            SodaService service = SodaService.get();
            if (service == null) {
                if (autoRetries++ < 5) {
                    handler.postDelayed(this, 800);
                } else {
                    Toast.makeText(MainActivity.this,
                            "无障碍已开启但服务未连接，请在设置里关闭再开启一次",
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            autoStarted = true;
            service.startEngine();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logs.init(this);
        setContentView(buildUi());
        handler.post(refreshTask);
        handler.postDelayed(autoStartTask, 800);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showGuideIfNeeded();
            }
        }, 600);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!autoStarted && SodaService.get() != null) {
            autoRetries = 0;
            handler.removeCallbacks(autoStartTask);
            handler.post(autoStartTask);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                maybeShowDonatePrompt();
            }
        }, 1000);
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 247, 248));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("汽水音乐 · 自动领时长");
        title.setTextSize(20);
        title.setTextColor(COLOR_TEXT);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("打开即开始 · 退出即停止");
        subtitle.setTextSize(12);
        subtitle.setTextColor(COLOR_MUTED);
        subtitle.setPadding(0, dp(2), 0, 0);
        root.addView(subtitle);

        banner = new TextView(this);
        banner.setText("还差一步：开启无障碍服务后才能自动领取，点击这里去开启");
        banner.setTextSize(13);
        banner.setTextColor(COLOR_BAD);
        banner.setBackgroundColor(Color.rgb(255, 241, 240));
        banner.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = dp(12);
        banner.setLayoutParams(bannerParams);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        root.addView(banner);

        LinearLayout guideCard = card();
        guideCard.addView(sectionTitle("使用说明"));
        guideCard.addView(body("1. 打开本工具后，会自动跳到汽水音乐开始领取。" +
                "\n2. 领取期间请保持屏幕亮着，不要手动操作汽水音乐。" +
                "\n3. 退出本工具即停止领取，不会在后台继续点击。" +
                "\n4. 广告需要真实播放，每天次数到上限后会自动停止。" +
                "\n5. 领取时可拖动悬浮窗查看轮次和进度。" +
                "\n6. 领满 30 天或异常停止时会发通知，建议开启通知权限。"));
        root.addView(guideCard);

        LinearLayout prepCard = card();
        prepCard.addView(sectionTitle("准备工作（首次使用）"));

        LinearLayout accessibilityRow = row();
        TextView accessibilityLabel = stepLabel("① 无障碍服务");
        accessibilityRow.addView(accessibilityLabel, weight());
        accessibilityStatus = statusText();
        accessibilityRow.addView(accessibilityStatus);
        accessibilityButton = actionButton("去开启");
        accessibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAccessibilitySettings();
            }
        });
        accessibilityRow.addView(accessibilityButton);
        prepCard.addView(accessibilityRow);
        prepCard.addView(hint("设置 → 无障碍 → 已下载的应用 → 汽水助手 → 开启"));

        LinearLayout batteryRow = row();
        TextView batteryLabel = stepLabel("② 后台运行");
        batteryRow.addView(batteryLabel, weight());
        batteryStatus = statusText();
        batteryRow.addView(batteryStatus);
        Button batteryButton = actionButton("去设置");
        batteryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openBatterySettings();
            }
        });
        batteryRow.addView(batteryButton);
        prepCard.addView(batteryRow);
        prepCard.addView(hint("设置 → 电池 → 应用耗电管理 → 汽水助手 → 允许后台运行"));

        LinearLayout overlayRow = row();
        overlayRow.addView(stepLabel("③ 悬浮进度窗"), weight());
        overlayStatus = statusText();
        overlayRow.addView(overlayStatus);
        Button overlayButton = actionButton("去设置");
        overlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openOverlaySettings();
            }
        });
        overlayRow.addView(overlayButton);
        prepCard.addView(overlayRow);
        prepCard.addView(hint("设置 → 应用管理 → 汽水助手 → 显示在其他应用上层"));

        LinearLayout notifyRow = row();
        notifyRow.addView(stepLabel("④ 通知提醒"), weight());
        notificationStatus = statusText();
        notifyRow.addView(notificationStatus);
        Button notifyButton = actionButton("去设置");
        notifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNotificationSettings();
            }
        });
        notifyRow.addView(notifyButton);
        prepCard.addView(notifyRow);
        prepCard.addView(hint("设置 → 应用管理 → 汽水助手 → 通知管理 → 允许通知"));
        root.addView(prepCard);

        LinearLayout statusCard = card();
        statusCard.addView(sectionTitle("运行状态"));
        statusView = new TextView(this);
        statusView.setTextSize(14);
        statusView.setTextColor(COLOR_TEXT);
        statusView.setPadding(0, dp(6), 0, dp(4));
        statusCard.addView(statusView);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setPadding(0, dp(8), 0, 0);

        Button start = new Button(this);
        start.setText("开始");
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SodaService service = SodaService.get();
                if (service == null) {
                    showGuideDialog();
                    return;
                }
                autoStarted = true;
                service.startEngine();
            }
        });
        buttonRow.addView(start, weight());

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SodaService service = SodaService.get();
                if (service != null) service.stopEngine();
            }
        });
        LinearLayout.LayoutParams stopParams = weight();
        stopParams.leftMargin = dp(8);
        buttonRow.addView(stop, stopParams);
        statusCard.addView(buttonRow);
        root.addView(statusCard);

        LinearLayout logCard = card();
        logCard.addView(sectionTitle("运行日志"));
        logView = new TextView(this);
        logView.setTextSize(11);
        logView.setTextColor(COLOR_MUTED);
        logView.setMaxLines(14);
        logView.setPadding(0, dp(6), 0, 0);
        logCard.addView(logView);
        root.addView(logCard);

        LinearLayout supportCard = card();
        supportCard.addView(sectionTitle("支持作者"));
        supportCard.addView(body("这个工具不收费，也没有广告。" +
                "\n如果它帮你自动看完了每天的广告、多听几天歌，愿意的话可以请我喝杯奶茶。" +
                "\n不着急，觉得有用再说。"));
        Button supportButton = new Button(this);
        supportButton.setText("请作者喝杯奶茶");
        supportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DonateActivity.class));
            }
        });
        LinearLayout.LayoutParams supportParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        supportParams.topMargin = dp(8);
        supportCard.addView(supportButton, supportParams);
        root.addView(supportCard);

        return scroll;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);
        return card;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(COLOR_TEXT);
        view.setPadding(0, 0, 0, dp(4));
        return view;
    }

    private TextView body(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(COLOR_MUTED);
        view.setLineSpacing(dp(3), 1f);
        view.setPadding(0, dp(2), 0, 0);
        return view;
    }

    private TextView hint(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(11);
        view.setTextColor(Color.rgb(130, 130, 140));
        view.setPadding(dp(2), dp(2), 0, dp(6));
        return view;
    }

    private TextView stepLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(COLOR_TEXT);
        return view;
    }

    private TextView statusText() {
        TextView view = new TextView(this);
        view.setText("未开启");
        view.setTextSize(12);
        view.setTextColor(COLOR_BAD);
        view.setPadding(dp(8), 0, dp(8), 0);
        return view;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), dp(4), dp(10), dp(4));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, 0);
        return row;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private void showGuideIfNeeded() {
        SharedPreferences prefs = getSharedPreferences("guide", MODE_PRIVATE);
        if (prefs.getBoolean("shown", false) || isAccessibilityEnabled()) return;
        prefs.edit().putBoolean("shown", true).apply();
        showGuideDialog();
    }

    private void showGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle("使用前准备")
                .setMessage("1. 开启无障碍服务\n2. 允许后台运行\n\n开启后，打开本工具会自动跳到汽水音乐开始领取；退出本工具即停止。")
                .setPositiveButton("去开启无障碍", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openAccessibilitySettings();
                    }
                })
                .setNegativeButton("知道了", null)
                .show();
    }

    private void maybeShowDonatePrompt() {
        if (donateShownThisSession) return;
        if (!autoStarted) return;
        SodaService service = SodaService.get();
        if (service != null && service.isEngineRunning()) return;
        if (!DonatePrompt.shouldShow(this)) return;
        donateShownThisSession = true;
        DonatePrompt.markShown(this);
        new AlertDialog.Builder(this)
                .setTitle("今天也帮你领到了")
                .setMessage("这个工具不收费，也没有广告。\n如果它帮你省下了时间，愿意的话可以请我喝杯奶茶。\n不着急，觉得有用再说。")
                .setPositiveButton("请作者喝杯奶茶", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(MainActivity.this, DonateActivity.class));
                    }
                })
                .setNegativeButton("以后再说", null)
                .show();
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开无障碍设置：" + e, Toast.LENGTH_SHORT).show();
        }
    }

    private void openBatterySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception err) {
                Toast.makeText(this, "无法打开电池设置：" + err, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openOverlaySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开悬浮窗设置：" + e, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus() {
        boolean accessibility = isAccessibilityEnabled();
        boolean battery = isBatteryIgnoring();
        SodaService service = SodaService.get();
        boolean running = service != null && service.isEngineRunning();

        accessibilityStatus.setText(accessibility ? "已开启" : "未开启");
        accessibilityStatus.setTextColor(accessibility ? COLOR_OK : COLOR_BAD);
        accessibilityButton.setText(accessibility ? "重新设置" : "去开启");

        batteryStatus.setText(battery ? "已允许" : "未设置");
        batteryStatus.setTextColor(battery ? COLOR_OK : COLOR_BAD);

        boolean overlay = isOverlayGranted();
        overlayStatus.setText(overlay ? "已授权" : "未授权");
        overlayStatus.setTextColor(overlay ? COLOR_OK : COLOR_BAD);

        boolean notifyEnabled = areNotificationsEnabled();
        notificationStatus.setText(notifyEnabled ? "已开启" : "已关闭");
        notificationStatus.setTextColor(notifyEnabled ? COLOR_OK : COLOR_BAD);

        boolean bound = service != null;
        if (accessibility && bound) {
            banner.setVisibility(View.GONE);
        } else {
            banner.setVisibility(View.VISIBLE);
            banner.setText(accessibility
                    ? "无障碍已开启，但服务未连接，点击这里关闭再开启一次"
                    : "还差一步：开启无障碍服务后才能自动领取，点击这里去开启");
        }

        if (!accessibility) {
            statusView.setText("等待第 1 步：开启无障碍服务");
            statusView.setTextColor(COLOR_BAD);
        } else if (!bound) {
            statusView.setText("服务未连接：请在设置里关闭再开启一次无障碍");
            statusView.setTextColor(COLOR_BAD);
        } else if (running) {
            statusView.setText("正在自动领取，请勿操作汽水音乐");
            statusView.setTextColor(COLOR_OK);
        } else {
            String last = service != null ? service.getStateText() : "已停止";
            if (last != null && !last.equals("已停止") && !last.equals("空闲")
                    && !last.equals("启动中") && !last.equals("已手动停止")) {
                statusView.setText("上次结束：" + last);
            } else if ("已手动停止".equals(last)) {
                statusView.setText("已停止领取");
            } else {
                statusView.setText("已就绪，打开后会自动开始领取");
            }
            statusView.setTextColor(COLOR_TEXT);
        }
    }

    private boolean isAccessibilityEnabled() {
        try {
            int master = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 0);
            if (master != 1) return false;
            String enabled = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (enabled == null) return false;
            String component = getPackageName() + "/" + SodaService.class.getName();
            return enabled.contains(component);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBatteryIgnoring() {
        try {
            if (Build.VERSION.SDK_INT < 23) return true;
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            return powerManager != null
                    && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isOverlayGranted() {
        try {
            return Settings.canDrawOverlays(this);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean areNotificationsEnabled() {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            return manager != null && manager.areNotificationsEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception err) {
                Toast.makeText(this, "无法打开通知设置：" + err, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (isFinishing()) {
            SodaService service = SodaService.get();
            if (service != null) service.stopEngine();
        }
        super.onDestroy();
    }
}
