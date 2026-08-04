package com.sangluo.onestep.feature.drag;

/** Standard ACTION_SEND destinations exposed while a media drag is active. */
public enum ImageDragShareTarget {
    WECHAT_TIMELINE(
            "com.tencent.mm", "com.tencent.mm.ui.tools.ShareToTimeLineUI"),
    WECHAT_FAVORITE(
            "com.tencent.mm", "com.tencent.mm.ui.tools.AddFavoriteUI"),
    QQ_FAVORITE(
            "com.tencent.mobileqq", "cooperation.qqfav.widget.QfavJumpActivity"),
    QQ_COMPUTER(
            "com.tencent.mobileqq", "com.tencent.mobileqq.activity.qfileJumpActivity"),
    BLUETOOTH(
            "com.android.bluetooth", false,
            "com.android.bluetooth.opp.BluetoothOppLauncherActivity"),
    PRINT(
            "com.android.printspooler", false,
            "com.android.printspooler.ui.MiPrintControlActivity"),
    ALIPAY(
            "com.eg.android.AlipayGphone",
            "com.alipay.mobile.quinox.splash.ShareDispenseActivity"),
    DOUYIN(
            "com.ss.android.ugc.aweme",
            "com.ss.android.ugc.aweme.share.ui.SystemShareNewActivity",
            "com.ss.android.ugc.aweme.share.OpenPlatformShareRealActivity"),
    JD(
            "com.jingdong.app.mall",
            "com.jingdong.app.mall.open.PhotoBuyActivity"),
    EMAIL(
            "com.android.email",
            "com.wps.multiwindow.main.HomeActivity",
            "com.android.email.activity.ComposeActivityEmailExternal",
            "com.android.mail.compose.ComposeActivity"),
    NOTES(
            "com.miui.notes",
            "com.miui.notes.ui.activity.IntermediaryActivity",
            "com.miui.notes.ui.SystemShareMiddleActivity"),
    SCANNER(
            "com.xiaomi.scanner", "com.xiaomi.scanner.app.ScanActivity");

    private final String packageName;
    private final boolean usesAppInstance;
    private final String[] activityNames;

    ImageDragShareTarget(String packageName, String... activityNames) {
        this(packageName, true, activityNames);
    }

    ImageDragShareTarget(
            String packageName, boolean usesAppInstance, String... activityNames) {
        this.packageName = packageName;
        this.usesAppInstance = usesAppInstance;
        this.activityNames = activityNames;
    }

    public String packageName() {
        return packageName;
    }

    public String activityName() {
        return activityNames[0];
    }

    public boolean usesAppInstance() {
        return usesAppInstance;
    }

    public boolean initializesAppBeforeColdStartShare() {
        return usesAppInstance;
    }

    public int activityMatchPriority(String candidateName) {
        if (candidateName == null) {
            return -1;
        }
        for (int index = 0; index < activityNames.length; index++) {
            String activityName = activityNames[index];
            if (activityName.equals(candidateName)
                    || (candidateName.startsWith(".")
                    && activityName.equals(packageName + candidateName))) {
                return index;
            }
        }
        return -1;
    }

    public boolean matchesActivity(String candidateName) {
        return activityMatchPriority(candidateName) >= 0;
    }

    public static ImageDragShareTarget fromIndex(int index) {
        ImageDragShareTarget[] targets = values();
        return index >= 0 && index < targets.length ? targets[index] : null;
    }
}
