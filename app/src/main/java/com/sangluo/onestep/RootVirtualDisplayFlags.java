package com.sangluo.onestep;

import android.hardware.display.DisplayManager;
import android.os.Build;

/**
 * Normalizes root-owned virtual display flags without making the display secure.
 * Without the optional Zygisk hook Android blacks only FLAG_SECURE source windows, while
 * ordinary app windows remain visible on the same virtual display.
 */
final class RootVirtualDisplayFlags {
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int DISPLAY_FLAG_TRUSTED = 1 << 7;
    private static final int ANDROID_11_SDK = 30;

    private RootVirtualDisplayFlags() {
    }

    static int forRootBridge(int requestedFlags) {
        return forRootBridge(requestedFlags, Build.VERSION.SDK_INT);
    }

    static int forRootBridge(int requestedFlags, int sdkInt) {
        int flags = requestedFlags;
        if (supportsTrustedVirtualDisplays(sdkInt)) {
            flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED;
        } else {
            flags &= ~VIRTUAL_DISPLAY_FLAG_TRUSTED;
        }
        if ((flags & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC) != 0) {
            // Public display access lets the current IME create its window. OWN_CONTENT_ONLY
            // prevents the public display from mirroring the default display while empty.
            flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            // Android 14 rejects PUBLIC together with SHOW_WHEN_LOCKED_INSECURE.
            flags &= ~VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
        }
        flags &= ~VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
        flags &= ~DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
        return flags;
    }

    static boolean hasRequiredTrustedDisplay(int sdkInt, boolean rootManaged,
                                             int actualDisplayFlags) {
        return !rootManaged
                || !supportsTrustedVirtualDisplays(sdkInt)
                || (actualDisplayFlags >= 0
                && (actualDisplayFlags & DISPLAY_FLAG_TRUSTED) != 0);
    }

    private static boolean supportsTrustedVirtualDisplays(int sdkInt) {
        return sdkInt >= ANDROID_11_SDK;
    }
}
