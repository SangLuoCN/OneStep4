package com.sangluo.onestep.hook;

/** Limits the MIUI HOME lifetime bypass to displays owned by OneStep. */
final class HyperOsEmbeddedHomePolicy {
    private static final String ACTION_MAIN = "android.intent.action.MAIN";
    private static final String MIUI_HOME_PACKAGE = "com.miui.home";
    private static final String SECONDARY_DISPLAY_LAUNCHER =
            "com.miui.home.launcher.SecondaryDisplayLauncher";

    private HyperOsEmbeddedHomePolicy() {
    }

    static boolean shouldKeepLauncherAlive(String displayName) {
        return displayName != null
                && displayName.startsWith(OneStepPrimaryHomePolicy.DISPLAY_NAME_PREFIX);
    }

    static boolean shouldSuppressSecondaryLauncher(String packageName,
                                                    String className,
                                                    String displayName) {
        return MIUI_HOME_PACKAGE.equals(packageName)
                && SECONDARY_DISPLAY_LAUNCHER.equals(className)
                && shouldKeepLauncherAlive(displayName);
    }

    static boolean shouldSuppressRedundantHomeLaunch(String action,
                                                     boolean hasHomeCategory,
                                                     boolean hasSecondaryHomeCategory,
                                                     int categoryCount,
                                                     String displayName) {
        return ACTION_MAIN.equals(action)
                && categoryCount == 1
                && (hasHomeCategory || hasSecondaryHomeCategory)
                && shouldKeepLauncherAlive(displayName);
    }

}
