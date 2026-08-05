package com.sangluo.onestep.data.apps;

import android.content.pm.ApplicationInfo;

/** Identifies preinstalled HOME apps without depending on an OEM package name. */
public final class HomeActivityPolicy {
    private HomeActivityPolicy() {
    }

    public static boolean isSystemHome(int applicationFlags, String sourceDir) {
        int systemFlags = ApplicationInfo.FLAG_SYSTEM
                | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
        if ((applicationFlags & systemFlags) != 0) {
            return true;
        }
        return sourceDir != null
                && (sourceDir.startsWith("/system/")
                || sourceDir.startsWith("/system_ext/")
                || sourceDir.startsWith("/product/")
                || sourceDir.startsWith("/vendor/")
                || sourceDir.startsWith("/odm/"));
    }
}
