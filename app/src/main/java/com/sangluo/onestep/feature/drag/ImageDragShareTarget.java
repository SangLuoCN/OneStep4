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
            "com.android.bluetooth", "com.android.bluetooth.opp.BluetoothOppLauncherActivity");

    private final String packageName;
    private final String activityName;

    ImageDragShareTarget(String packageName, String activityName) {
        this.packageName = packageName;
        this.activityName = activityName;
    }

    public String packageName() {
        return packageName;
    }

    public String activityName() {
        return activityName;
    }

    public boolean matchesActivity(String candidateName) {
        if (candidateName == null) {
            return false;
        }
        if (activityName.equals(candidateName)) {
            return true;
        }
        return candidateName.startsWith(".")
                && activityName.equals(packageName + candidateName);
    }

    public static ImageDragShareTarget fromIndex(int index) {
        ImageDragShareTarget[] targets = values();
        return index >= 0 && index < targets.length ? targets[index] : null;
    }
}
