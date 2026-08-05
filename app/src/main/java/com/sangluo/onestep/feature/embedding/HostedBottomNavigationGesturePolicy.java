package com.sangluo.onestep.feature.embedding;

/** Locates the system navigation gesture input region on a hosted display. */
public final class HostedBottomNavigationGesturePolicy {
    // MIUI Home uses 20.5dp when the gesture line is hidden. Stay inside that window so the
    // forwarded DOWN is always owned by the system GestureStubHome.
    private static final float NAVIGATION_START_REGION_DP = 18f;

    private HostedBottomNavigationGesturePolicy() {
    }

    public static boolean startsInNavigationRegion(
            float viewY, int viewHeight, int displayHeight, int displayDensityDpi) {
        if (viewHeight <= 0 || displayHeight <= 0 || displayDensityDpi <= 0
                || viewY < 0f || viewY > viewHeight) {
            return false;
        }
        float displayY = viewY * displayHeight / viewHeight;
        float navigationRegionPx = NAVIGATION_START_REGION_DP
                * displayDensityDpi / 160f;
        return displayY >= displayHeight - navigationRegionPx;
    }
}
