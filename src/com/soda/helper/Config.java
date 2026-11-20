package com.soda.helper;

public final class Config {
    public static final String TARGET_PACKAGE = "com.luna.music";

    // 我的页「免费听」进度，如：5天 再看1次领第6天畅听
    public static final String ID_PROGRESS = "com.luna.music:id/j6z";
    // 「继续领」按钮
    public static final String ID_CONTINUE = "com.luna.music:id/j5t";

    public static final String TEXT_CONTINUE = "继续领";
    public static final String TEXT_PROFILE_TAB = "我的";

    public static final int MAX_ROUNDS = 30;
    public static final int MAX_FAILURES = 3;
    public static final int MAX_FREE_DAYS = 30;
    public static final long AD_TIMEOUT_MS = 150000L;

    private Config() {
    }
}
