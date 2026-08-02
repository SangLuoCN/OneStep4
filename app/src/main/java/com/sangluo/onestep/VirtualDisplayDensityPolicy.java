package com.sangluo.onestep;

/** Preserves each host pane's logical scale while exposing tablet space on large screens. */
public final class VirtualDisplayDensityPolicy {
    private static final int MIN_DENSITY_DPI = 300;
    private static final int TABLET_MIN_LOGICAL_SHORT_EDGE_DP = 600;
    private static final int TABLET_TARGET_LOGICAL_SHORT_EDGE_DP = 800;

    private VirtualDisplayDensityPolicy() {
    }

    public static int calculateDensityDpi(
            int referenceWidth, int referenceHeight,
            int virtualWidth, int virtualHeight,
            int hostDensityDpi, boolean useTabletDensity) {
        float qualityScale = Math.min(
                virtualWidth / (float) Math.max(1, referenceWidth),
                virtualHeight / (float) Math.max(1, referenceHeight));
        int scaledHostDensityDpi = Math.max(MIN_DENSITY_DPI,
                Math.round(Math.max(MIN_DENSITY_DPI, hostDensityDpi) * qualityScale));
        if (!useTabletDensity) {
            return scaledHostDensityDpi;
        }

        int virtualShortEdge = Math.min(virtualWidth, virtualHeight);
        int maxTabletDensityDpi = Math.max(MIN_DENSITY_DPI,
                (int) Math.floor(virtualShortEdge * 160f
                        / TABLET_MIN_LOGICAL_SHORT_EDGE_DP));
        return Math.min(scaledHostDensityDpi, maxTabletDensityDpi);
    }

    public static float calculateTabletPixelScale(
            int virtualWidth, int virtualHeight,
            int densityDpi, boolean useTabletDensity) {
        if (!useTabletDensity) {
            return 1f;
        }
        int virtualShortEdge = Math.min(virtualWidth, virtualHeight);
        float logicalShortEdgeDp = virtualShortEdge * 160f
                / Math.max(MIN_DENSITY_DPI, densityDpi);
        return Math.max(1f, TABLET_TARGET_LOGICAL_SHORT_EDGE_DP
                / Math.max(1f, logicalShortEdgeDp));
    }
}
