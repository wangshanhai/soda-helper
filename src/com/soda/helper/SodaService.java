package com.soda.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;

public class SodaService extends AccessibilityService {
    private static SodaService instance;
    private Engine engine;
    private volatile String lastState = "空闲";

    public static SodaService get() {
        return instance;
    }

    @Override
    public void onServiceConnected() {
        Log.i("SodaHelper", "onServiceConnected");
        instance = this;
        try {
            Logs.init(this);
            Logs.i("无障碍服务已连接");
        } catch (Throwable t) {
            Log.e("SodaHelper", "日志初始化失败", t);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        Logs.w("无障碍服务被中断");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Logs.i("无障碍服务已断开，等待系统重新绑定");
        instance = null;
        return true;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopEngine();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        try {
            stopEngine();
        } catch (Throwable t) {
            Log.e("SodaHelper", "onDestroy 停止引擎失败", t);
        }
        instance = null;
        super.onDestroy();
    }

    public synchronized void startEngine() {
        if (engine != null && engine.isRunning()) {
            Logs.i("领取流程已在运行");
            FloatingProgress.show(this);
            return;
        }
        engine = new Engine(this);
        engine.start();
        lastState = "启动中";
        FloatingProgress.show(this);
        KeepAliveService.start(this);
    }

    public synchronized void stopEngine() {
        if (engine != null) {
            if (engine.isRunning()) {
                lastState = "已手动停止";
            } else {
                lastState = engine.getStatus();
            }
            engine.halt();
            engine = null;
            Logs.i("已请求停止领取流程");
        }
        FloatingProgress.hide();
        KeepAliveService.stop(this);
    }

    public synchronized boolean isEngineRunning() {
        return engine != null && engine.isRunning();
    }

    public synchronized int getRound() {
        Engine current = engine;
        return current != null ? current.getRound() : 0;
    }

    public synchronized String getProgressText() {
        Engine current = engine;
        return current != null ? current.getProgress() : "--";
    }

    public synchronized String getStateText() {
        Engine current = engine;
        if (current != null) return current.getStatus();
        return lastState;
    }

    public AccessibilityNodeInfo root() {
        return getRootInActiveWindow();
    }

    public List<AccessibilityNodeInfo> findAll(String textContains, String descContains, String viewId) {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo window : windows) {
                    AccessibilityNodeInfo root = window.getRoot();
                    if (root != null) {
                        collect(root, textContains, descContains, viewId, result, 0);
                    }
                }
            }
        } catch (Exception e) {
            Logs.w("遍历窗口失败：" + e);
        }
        if (result.isEmpty()) {
            AccessibilityNodeInfo root = root();
            if (root != null) {
                collect(root, textContains, descContains, viewId, result, 0);
            }
        }
        return result;
    }

    private void collect(AccessibilityNodeInfo node, String textContains, String descContains,
                         String viewId, List<AccessibilityNodeInfo> result, int depth) {
        if (node == null || depth > 40 || result.size() > 80) return;

        String text = node.getText() == null ? "" : node.getText().toString();
        String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        String id = node.getViewIdResourceName();

        boolean matched = (textContains != null && text.contains(textContains))
                || (descContains != null && desc.contains(descContains))
                || (viewId != null && viewId.equals(id));
        if (matched) result.add(node);

        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), textContains, descContains, viewId, result, depth + 1);
        }
    }

    public boolean tapNode(AccessibilityNodeInfo node) {
        if (node == null) return false;

        AccessibilityNodeInfo current = node;
        for (int i = 0; i < 6 && current != null; i++) {
            if (current.isClickable()
                    && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true;
            }
            current = current.getParent();
        }

        Rect rect = new Rect();
        node.getBoundsInScreen(rect);
        if (rect.width() > 0 && rect.height() > 0) {
            return tap(rect.centerX(), rect.centerY());
        }
        return false;
    }

    public boolean tap(int x, int y) {
        if (Build.VERSION.SDK_INT < 24) return false;
        Path path = new Path();
        path.moveTo(x, y);
        path.lineTo(x + 1, y + 1);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 60);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        return dispatchGesture(gesture, null, null);
    }

    @SuppressWarnings("deprecation")
    public boolean tapRatio(float ratioX, float ratioY) {
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) {
                wm.getDefaultDisplay().getRealMetrics(metrics);
            } else {
                metrics = getResources().getDisplayMetrics();
            }
            return tap((int) (metrics.widthPixels * ratioX), (int) (metrics.heightPixels * ratioY));
        } catch (Throwable t) {
            Logs.w("坐标点击失败：" + t);
            return false;
        }
    }

    public String textOf(String viewId) {
        List<AccessibilityNodeInfo> nodes = findAll(null, null, viewId);
        for (AccessibilityNodeInfo node : nodes) {
            if (node.getText() != null) return node.getText().toString();
        }
        return null;
    }

    public boolean hasText(String text) {
        return !findAll(text, null, null).isEmpty();
    }

    public void back() {
        performGlobalAction(GLOBAL_ACTION_BACK);
    }
}
