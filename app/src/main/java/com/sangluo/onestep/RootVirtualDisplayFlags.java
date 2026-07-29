package com.sangluo.onestep;

import android.hardware.display.DisplayManager;

/**
 * Normalizes root-owned virtual display flags without making the display secure.
 * Without the optional Zygisk hook Android blacks only FLAG_SECURE source windows, while
 * ordinary app windows remain visible on the same virtual display.
 */
final class RootVirtualDisplayFlags {
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;

    private RootVirtualDisplayFlags() {
    }

    static int forRootBridge(int requestedFlags) {
        int flags = requestedFlags | VIRTUAL_DISPLAY_FLAG_TRUSTED;
        if ((flags & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC) != 0) {
            // Public display access lets the current IME create its window. OWN_CONTENT_ONLY
            // prevents the public display from mirroring the default display while empty.
            flags |= DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            // Android 14 rejects PUBLIC together with SHOW_WHEN_LOCKED_INSECURE.
            flags &= ~VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
        }
        flags &= ~DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
        return flags;
    }
}
