package com.sangluo.onestep.hook;

final class StatusBarOverlayDisplayPolicy {
    private static final int DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 6;

    private StatusBarOverlayDisplayPolicy() {
    }

    static boolean shouldApply(int displayFlags) {
        return (displayFlags & DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS) == 0;
    }
}
