package com.sangluo.onestep.feature.drag;

/** Selects the standard friend-sharing activity for apps with multiple share routes. */
public final class ImageShareTargetPolicy {
    public static final String QQ_PACKAGE = "com.tencent.mobileqq";
    public static final String QQ_FRIEND_ACTIVITY =
            "com.tencent.mobileqq.activity.JumpActivity";
    public static final String QQ_FRIEND_PICKER_ACTIVITY =
            "com.tencent.mobileqq.activity.ForwardRecentActivity";
    public static final String WECHAT_PACKAGE = "com.tencent.mm";
    public static final String WECHAT_FRIEND_ACTIVITY =
            "com.tencent.mm.ui.tools.ShareImgUI";
    private static final String WECHAT_LAUNCHER_ACTIVITY =
            "com.tencent.mm.ui.LauncherUI";

    private ImageShareTargetPolicy() {
    }

    public static String requiredActivity(String packageName) {
        if (QQ_PACKAGE.equals(packageName)) {
            return QQ_FRIEND_ACTIVITY;
        }
        if (WECHAT_PACKAGE.equals(packageName)) {
            return WECHAT_FRIEND_ACTIVITY;
        }
        return null;
    }

    public static boolean isShareUiReady(String packageName, String componentName) {
        return isShareUiReady(packageName, componentName, null);
    }

    public static boolean isShareUiReady(
            String packageName, String componentName, String initialActivityName) {
        if (componentName == null || componentName.contains("SplashActivity")) {
            return false;
        }
        if (QQ_PACKAGE.equals(packageName)) {
            boolean friendRoute = QQ_FRIEND_ACTIVITY.equals(initialActivityName)
                    || (initialActivityName != null
                    && initialActivityName.endsWith("/.activity.JumpActivity"));
            if (friendRoute || initialActivityName == null) {
                return componentName.endsWith(QQ_FRIEND_PICKER_ACTIVITY)
                        || componentName.endsWith("/.activity.ForwardRecentActivity");
            }
            if (componentMatchesActivity(
                    packageName, componentName, initialActivityName)) {
                return false;
            }
            return !componentName.endsWith(QQ_FRIEND_ACTIVITY)
                    && !componentName.endsWith("/.activity.JumpActivity");
        }
        if (WECHAT_PACKAGE.equals(packageName) && initialActivityName != null) {
            return !componentName.endsWith(WECHAT_LAUNCHER_ACTIVITY)
                    && !componentName.endsWith("/.ui.LauncherUI");
        }
        return true;
    }

    private static boolean componentMatchesActivity(
            String packageName, String componentName, String activityName) {
        if (componentName.endsWith(activityName)) {
            return true;
        }
        String prefix = packageName + ".";
        return activityName.startsWith(prefix)
                && componentName.endsWith("/." + activityName.substring(prefix.length()));
    }
}
