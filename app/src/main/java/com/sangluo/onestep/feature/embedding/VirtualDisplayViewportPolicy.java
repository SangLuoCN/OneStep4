package com.sangluo.onestep.feature.embedding;

/** Keeps every 1+n slot on one stable, device-sized logical viewport. */
public final class VirtualDisplayViewportPolicy {
    private static final float TARGET_ASPECT_RATIO_TOLERANCE = 0.10f;

    private VirtualDisplayViewportPolicy() {
    }

    public static boolean shouldUseWorkspaceSpec(boolean dualMainLayout) {
        return !dualMainLayout;
    }

    public static boolean shouldResizeForContainerLayout(
            boolean largeScreenDevice,
            boolean targetDualMainLayout,
            boolean leavingDualMainLayout) {
        return largeScreenDevice || targetDualMainLayout || leavingDualMainLayout;
    }

    public static boolean shouldRefreshForTargetAspect(
            int currentWidth, int currentHeight, int targetWidth, int targetHeight) {
        if (currentWidth <= 0 || currentHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return false;
        }
        float currentAspectRatio = currentWidth / (float) currentHeight;
        float targetAspectRatio = targetWidth / (float) targetHeight;
        float relativeDifference = Math.abs(currentAspectRatio - targetAspectRatio)
                / Math.max(0.0001f, targetAspectRatio);
        return relativeDifference > TARGET_ASPECT_RATIO_TOLERANCE;
    }
}
