package com.sangluo.onestep.system.root;

/** Controls whether the root bridge may reload SELinux policy at runtime. */
public final class RootBridgePolicyCompat {
    private static final int ANDROID_11_API = 30;

    private RootBridgePolicyCompat() {
    }

    public static boolean shouldApplyLivePolicy(int sdkInt) {
        // Reloading policy on Android 11 AVDs invalidates the vendor gralloc service mapping.
        return sdkInt != ANDROID_11_API;
    }
}
