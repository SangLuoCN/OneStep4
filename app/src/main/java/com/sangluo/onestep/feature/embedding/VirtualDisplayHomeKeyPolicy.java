package com.sangluo.onestep.feature.embedding;

/** Decides whether a system HOME key may be injected into a virtual display. */
public final class VirtualDisplayHomeKeyPolicy {
    private static final int ANDROID_11_API = 30;

    private VirtualDisplayHomeKeyPolicy() {
    }

    public static boolean shouldInjectHomeKey(int sdkInt) {
        // Android 11 can dispatch HOME after its target display has already been removed.
        return sdkInt != ANDROID_11_API;
    }
}
