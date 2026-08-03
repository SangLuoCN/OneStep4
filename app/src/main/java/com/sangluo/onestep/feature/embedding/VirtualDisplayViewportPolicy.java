package com.sangluo.onestep.feature.embedding;

/** Keeps every 1+n slot on one stable, device-sized logical viewport. */
public final class VirtualDisplayViewportPolicy {
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
}
