package com.sangluo.onestep.feature.embedding;

/** Selects the focus-demotion mechanism supported by each Android version. */
public final class HostedInputFocusPolicy {
    private static final int ANDROID_17_API = 37;

    private HostedInputFocusPolicy() {
    }

    public static boolean shouldUseDemotedFocusWindow(int sdkInt) {
        // Android 17 stops the hosted task when a Presentation takes its display focus.
        return sdkInt < ANDROID_17_API;
    }
}
