package com.sangluo.onestep.hook;

final class RootVirtualDisplayCompatPolicy {
    static final int ANDROID_13_SDK = 33;
    static final int ROOT_UID = 0;
    static final String DISPLAY_MANAGER_SERVICE_CLASS =
            "com.android.server.display.DisplayManagerService";
    static final String DISPLAY_MANAGER_BINDER_SERVICE_CLASS =
            DISPLAY_MANAGER_SERVICE_CLASS + "$BinderService";

    private RootVirtualDisplayCompatPolicy() {
    }

    static boolean needsCompatHook(int sdkInt) {
        return sdkInt <= ANDROID_13_SDK;
    }

    static boolean shouldBypassPackageValidation(int sdkInt, int uid) {
        return needsCompatHook(sdkInt) && uid == ROOT_UID;
    }

    static String validationOwnerClassName(int sdkInt) {
        return sdkInt >= ANDROID_13_SDK
                ? DISPLAY_MANAGER_SERVICE_CLASS
                : DISPLAY_MANAGER_BINDER_SERVICE_CLASS;
    }
}
