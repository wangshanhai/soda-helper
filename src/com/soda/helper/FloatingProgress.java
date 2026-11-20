package com.soda.helper;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class FloatingProgress {
    private static FloatingProgress instance;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager windowManager;
    private LinearLayout root;
    private TextView titleView;
    private TextView progressView;
    private TextView stateView;
    private WindowManager.LayoutParams params;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private FloatingProgress(Context context) {
        this.context = context.getApplicationContext();
    }

    public static void show(final Context context) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (instance != null) return;
                if (!Settings.canDrawOverlays(context)) {
                    Logs.w("未授权悬浮窗，跳过进度浮窗");
                    return;
                }
                instance = new FloatingProgress(context);
                instance.attach();
            }
        });
    }

    public static void hide() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (instance != null) {
                    instance.detach();
                    instance = null;
                }
            }
        });
    }

    public static void finish(final String state, final String progress) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (instance == null) return;
                instance.showFinal(state, progress);
            }
        });
    }

    private void showFinal(String state, String progress) {
        handler.removeCallbacks(tick);
        if (titleView != null) titleView.setText("汽水助手 · 已结束");
        if (progressView != null) {
            progressView.setText(progress == null || progress.length() == 0 ? "--" : progress);
        }
        if (stateView != null) stateView.setText(state);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                hide();
            }
        }, 8000);
    }

    private void attach() {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) return;

        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.setMinimumWidth(dp(210));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#E6212121"));
        background.setCornerRadius(dp(14));
        root.setBackground(background);
        root.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setText("汽水助手");
        titleView.setTextSize(12);
        titleView.setTextColor(Color.WHITE);
        header.addView(titleView, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView stop = new TextView(context);
        stop.setText("停止");
        stop.setTextSize(11);
        stop.setTextColor(Color.WHITE);
        GradientDrawable stopBackground = new GradientDrawable();
        stopBackground.setColor(Color.parseColor("#33FFFFFF"));
        stopBackground.setCornerRadius(dp(8));
        stop.setBackground(stopBackground);
        stop.setPadding(dp(8), dp(2), dp(8), dp(2));
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SodaService service = SodaService.get();
                if (service != null) service.stopEngine();
                hide();
            }
        });
        header.addView(stop);
        root.addView(header);

        progressView = new TextView(context);
        progressView.setText("--");
        progressView.setTextSize(14);
        progressView.setTextColor(Color.WHITE);
        progressView.setPadding(0, dp(4), 0, 0);
        root.addView(progressView);

        stateView = new TextView(context);
        stateView.setText("准备中");
        stateView.setTextSize(11);
        stateView.setTextColor(Color.parseColor("#B3FFFFFF"));
        stateView.setPadding(0, dp(2), 0, 0);
        root.addView(stateView);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(210);

        header.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (event.getRawX() - downX);
                        params.y = startY + (int) (event.getRawY() - downY);
                        if (windowManager != null && root != null) {
                            windowManager.updateViewLayout(root, params);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            windowManager.addView(root, params);
            handler.post(tick);
            Logs.i("进度悬浮窗已显示");
        } catch (Throwable t) {
            Logs.w("添加悬浮窗失败：" + t);
            root = null;
        }
    }

    private void refresh() {
        SodaService service = SodaService.get();
        int round = service != null ? service.getRound() : 0;
        String progress = service != null ? service.getProgressText() : "--";
        String state = service != null ? service.getStateText() : "已停止";

        titleView.setText(round > 0 ? "汽水助手 · 第 " + round + " 轮" : "汽水助手");
        progressView.setText(progress == null || progress.length() == 0 ? "--" : progress);
        stateView.setText(state);
    }

    private void detach() {
        handler.removeCallbacksAndMessages(null);
        try {
            if (windowManager != null && root != null) {
                windowManager.removeView(root);
            }
        } catch (Throwable t) {
            Logs.w("移除悬浮窗失败：" + t);
        }
        root = null;
        Logs.i("进度悬浮窗已关闭");
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
