package com.soda.helper;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Engine extends Thread {
    private final SodaService service;
    private volatile boolean running = true;
    private int rounds;
    private int failures;
    private volatile int roundNo;
    private volatile String progressText = "--";
    private volatile String stateText = "启动中";
    private volatile String stopReason;
    private volatile boolean manualStop;
    private volatile boolean thirtyDayCap;

    public Engine(SodaService service) {
        this.service = service;
    }

    public boolean isRunning() {
        return running;
    }

    public int getRound() {
        return roundNo;
    }

    public String getProgress() {
        return progressText;
    }

    public String getStatus() {
        return stateText;
    }

    private void setStatus(String status) {
        stateText = status;
    }

    public void halt() {
        manualStop = true;
        running = false;
        interrupt();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run() {
        Logs.i("领取流程启动");
        stateText = "启动中";
        PowerManager.WakeLock wakeLock = null;
        try {
            PowerManager powerManager = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "SodaHelper:engine");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
            launchSoda();
            setStatus("进入「我的」页");
            if (!ensureProfilePage()) {
                stopReason = "无法进入「我的」页";
                Logs.e("无法进入「我的」页面，流程结束");
                return;
            }

            while (running && rounds < Config.MAX_ROUNDS) {
                if (isThirtyDayCap()) {
                    stopReason = "已领满 " + Config.MAX_FREE_DAYS + " 天，明天再来";
                    break;
                }
                String before = service.textOf(Config.ID_PROGRESS);
                if (before == null) {
                    if (!ensureProfilePage()) break;
                    before = service.textOf(Config.ID_PROGRESS);
                }
                if (before != null) progressText = before;
                int currentDays = parseDays(before);
                if (currentDays >= Config.MAX_FREE_DAYS) {
                    stopReason = "已领满 " + Config.MAX_FREE_DAYS + " 天，明天再来";
                    Logs.i("已达到免费听天数上限");
                    break;
                }
                if (!hasContinueButton()) {
                    stopReason = currentDays > 0
                            ? "今日已领完（当前 " + currentDays + " 天）"
                            : "今日已领完";
                    Logs.i("没有「继续领」按钮，今日可能已领完");
                    break;
                }

                rounds++;
                roundNo = rounds;
                setStatus("第 " + rounds + " 轮 · 等待继续领");
                Logs.i("第 " + rounds + " 轮开始，当前进度：" + before);
                if (!tapContinue()) {
                    failures++;
                    Logs.w("点击「继续领」失败，连续失败 " + failures);
                    if (failures >= Config.MAX_FAILURES) {
                        stopReason = "连续失败 " + failures + " 次";
                        break;
                    }
                    continue;
                }

                setStatus("等待广告中…");
                if (!handleAdFlow()) {
                    failures++;
                    Logs.w("广告流程未正常完成，连续失败 " + failures);
                } else {
                    failures = 0;
                }
                if (failures >= Config.MAX_FAILURES) {
                    stopReason = "连续失败 " + failures + " 次";
                    Logs.w("连续失败达到上限，停止");
                    break;
                }

                sleepMs(2000);
                dismissUpsellSheet();
                ensureProfilePage();
                setStatus("校验进度中…");
                String after = service.textOf(Config.ID_PROGRESS);
                if (after != null) progressText = after;
                Logs.i("进度变化：" + before + " -> " + after);
                if (after != null && after.equals(before)) {
                    failures++;
                    Logs.w("进度没有变化，连续失败 " + failures);
                } else if (after != null) {
                    failures = 0;
                    DonatePrompt.markSuccess(service);
                }
                if (failures >= Config.MAX_FAILURES) {
                    stopReason = "连续失败 " + failures + " 次";
                    Logs.w("连续失败达到上限，停止");
                    break;
                }
            }
            if (stopReason == null && rounds >= Config.MAX_ROUNDS) {
                stopReason = "达到轮次上限";
            }
        } catch (Throwable t) {
            stopReason = "异常停止";
            Logs.e("流程异常：" + t);
        } finally {
            running = false;
            stateText = stopReason != null ? stopReason : "已停止";
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            KeepAliveService.stop(service);
            if (manualStop) {
                FloatingProgress.hide();
            } else {
                FloatingProgress.finish(stateText, progressText);
                Notify.finished(service, stateText, "当前进度：" + progressText);
            }
            Logs.i("领取流程结束：" + stateText);
        }
    }

    private void launchSoda() {
        try {
            Intent intent = service.getPackageManager().getLaunchIntentForPackage(Config.TARGET_PACKAGE);
            if (intent == null) {
                Logs.e("找不到汽水音乐：" + Config.TARGET_PACKAGE);
                return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            service.startActivity(intent);
            Logs.i("已启动汽水音乐");
            sleepMs(5000);
        } catch (Exception e) {
            Logs.e("启动汽水音乐失败：" + e);
        }
    }

    private boolean ensureProfilePage() {
        for (int i = 0; i < 6 && running; i++) {
            if (detectThirtyDayCap()) return true;
            if (skipSplashAd()) {
                sleepMs(1200);
                continue;
            }
            dismissUpsellSheet();
            if (service.textOf(Config.ID_PROGRESS) != null) return true;

            if (tapDesc("坚持退出")) {
                sleepMs(1000);
                continue;
            }
            if (!service.findAll(null, "领取成功", null).isEmpty()) {
                tapDesc("领取成功");
                sleepMs(1000);
                service.tapRatio(0.5f, 0.625f);
                sleepMs(1200);
                continue;
            }
            if (!service.findAll(null, "可领取奖励", null).isEmpty()) {
                service.back();
                Logs.i("退出未完成的广告");
                sleepMs(1200);
                continue;
            }

            List<AccessibilityNodeInfo> tabs = service.findAll(Config.TEXT_PROFILE_TAB, null, null);
            if (!tabs.isEmpty() && service.tapNode(tabs.get(0))) {
                Logs.i("点击「我的」标签");
            } else {
                service.tapRatio(0.87f, 0.94f);
                Logs.i("点击底部「我的」位置");
            }
            sleepMs(1500);
            dismissUpsellSheet();
            if (service.textOf(Config.ID_PROGRESS) != null) return true;
        }
        return service.textOf(Config.ID_PROGRESS) != null;
    }

    private void dismissUpsellSheet() {
        if (hasAny("提前领") || hasAny("提前享")
                || hasAny("解锁第") || hasAny("免费时长畅听")) {
            service.back();
            Logs.i("关闭底部推荐弹窗");
            sleepMs(1200);
        }
    }

    private boolean skipSplashAd() {
        List<AccessibilityNodeInfo> nodes = service.findAll("跳过", null, null);
        if (nodes.isEmpty()) {
            nodes = service.findAll(null, "跳过", null);
        }
        if (!nodes.isEmpty() && service.tapNode(nodes.get(0))) {
            Logs.i("跳过开屏广告");
            return true;
        }
        if (hasAny("开通SVIP免广告")) {
            service.tapRatio(0.88f, 0.10f);
            Logs.i("点击开屏广告跳过位置");
            return true;
        }
        return false;
    }

    private boolean hasAny(String keyword) {
        return !service.findAll(keyword, null, null).isEmpty()
                || !service.findAll(null, keyword, null).isEmpty();
    }

    private boolean hasContinueButton() {
        if (!service.findAll(null, null, Config.ID_CONTINUE).isEmpty()) return true;
        return service.hasText(Config.TEXT_CONTINUE);
    }

    private boolean tapContinue() {
        List<AccessibilityNodeInfo> byId = service.findAll(null, null, Config.ID_CONTINUE);
        if (!byId.isEmpty() && service.tapNode(byId.get(0))) {
            Logs.i("点击「继续领」");
            return true;
        }
        List<AccessibilityNodeInfo> byText = service.findAll(Config.TEXT_CONTINUE, null, null);
        if (!byText.isEmpty() && service.tapNode(byText.get(0))) {
            Logs.i("点击「继续领」文本");
            return true;
        }
        Logs.i("点击「继续领」坐标兜底");
        return service.tapRatio(0.80f, 0.512f);
    }

    private boolean handleAdFlow() {
        long deadline = SystemClock.uptimeMillis() + Config.AD_TIMEOUT_MS;
        boolean sawAd = false;
        int successTaps = 0;
        long lastLogAt = 0;

        while (running && SystemClock.uptimeMillis() < deadline) {
            if (service.textOf(Config.ID_PROGRESS) != null) {
                Logs.i("已回到「我的」页，结束广告等待");
                return true;
            }
            if (SystemClock.uptimeMillis() - lastLogAt > 15000) {
                lastLogAt = SystemClock.uptimeMillis();
                Logs.i("等待广告流程中…");
            }

            // 1. 奖励弹窗：坚持退出才回到页面，领取奖励会再拉起广告
            if (tapDesc("坚持退出")) {
                sleepMs(1200);
                return true;
            }

            // 2. 优惠券弹窗
            if (service.hasText("恭喜您获得一张惊喜券")) {
                service.tapRatio(0.937f, 0.294f);
                Logs.i("关闭优惠券弹窗");
                sleepMs(800);
                continue;
            }

            // 3. 直播间倒计时弹窗，点取消
            if (service.hasText("进入直播间")) {
                List<AccessibilityNodeInfo> cancel = service.findAll("取消", null, null);
                if (!cancel.isEmpty() && service.tapNode(cancel.get(0))) {
                    Logs.i("取消进入直播间");
                    sleepMs(800);
                    continue;
                }
            }

            // 4. 已经进入直播间，点右上角关闭
            if (service.hasText("更多直播") || service.hasText("人气榜") || service.hasText("直播间同价")) {
                service.tapRatio(0.95f, 0.066f);
                Logs.i("关闭直播间");
                sleepMs(1200);
                continue;
            }

            // 5. 广告结束，点顶部「领取成功，关闭」
            if (!service.findAll(null, "领取成功", null).isEmpty()) {
                sawAd = true;
                if (successTaps < 2) {
                    successTaps++;
                    tapDesc("领取成功");
                    sleepMs(1200);
                } else {
                    Logs.i("奖励弹窗不可见，点击「坚持退出」坐标");
                    service.tapRatio(0.5f, 0.625f);
                    sleepMs(1500);
                    successTaps = 0;
                    return true;
                }
                continue;
            }

            // 6. 广告倒计时中，继续等待
            if (!service.findAll(null, "可领取奖励", null).isEmpty()) {
                sawAd = true;
                sleepMs(800);
                continue;
            }

            // 7. 不点「领取奖励」，它会再拉起一个广告
            if (!service.findAll(null, "领取奖励", null).isEmpty()) {
                sleepMs(800);
                continue;
            }

            sleepMs(600);
        }

        Logs.w(sawAd ? "广告流程超时" : "未检测到广告流程");
        service.back();
        sleepMs(1000);
        return false;
    }

    private boolean tapDesc(String keyword) {
        List<AccessibilityNodeInfo> nodes = service.findAll(null, keyword, null);
        if (nodes.isEmpty()) return false;
        boolean tapped = service.tapNode(nodes.get(0));
        if (tapped) Logs.i("点击：" + keyword);
        return tapped;
    }

    private int parseDays(String text) {
        if (text == null) return -1;
        Matcher matcher = Pattern.compile("(\\d+)\\s*天").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private boolean detectThirtyDayCap() {
        if (hasAny("第30日畅听") || hasAny("当前已解锁30天") || hasAny("已解锁30天")) {
            thirtyDayCap = true;
            if (progressText == null || progressText.equals("--")) {
                progressText = "第 30 天";
            }
            return true;
        }
        return thirtyDayCap;
    }

    private boolean isThirtyDayCap() {
        return thirtyDayCap || detectThirtyDayCap();
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
