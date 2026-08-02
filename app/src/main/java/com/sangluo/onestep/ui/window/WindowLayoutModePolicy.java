package com.sangluo.onestep.ui.window;

/** Selects the single- or dual-main layout without coupling it to rotation policy. */
public final class WindowLayoutModePolicy {
    private static final int LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP = 600;
    private static final float DUAL_MAIN_MIN_NORMALIZED_ASPECT_RATIO = 0.72f;

    private WindowLayoutModePolicy() {
    }

    public static boolean isLargeScreen(int smallestScreenWidthDp) {
        return smallestScreenWidthDp >= LARGE_SCREEN_MIN_SMALLEST_WIDTH_DP;
    }

    public static boolean shouldUseDualMain(
            int smallestScreenWidthDp, int screenWidthDp, int screenHeightDp) {
        if (!isLargeScreen(smallestScreenWidthDp)) {
            return false;
        }
        if (screenWidthDp <= 0 || screenHeightDp <= 0) {
            return true;
        }
        int shortEdgeDp = Math.min(screenWidthDp, screenHeightDp);
        int longEdgeDp = Math.max(screenWidthDp, screenHeightDp);
        return shortEdgeDp / (float) longEdgeDp
                >= DUAL_MAIN_MIN_NORMALIZED_ASPECT_RATIO;
    }
}
