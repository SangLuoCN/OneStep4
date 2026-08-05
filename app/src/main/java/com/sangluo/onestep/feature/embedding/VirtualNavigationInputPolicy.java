package com.sangluo.onestep.feature.embedding;

/** Defines the non-interactive area occupied by a hidden virtual-display navigation bar. */
public final class VirtualNavigationInputPolicy {
    private VirtualNavigationInputPolicy() {
    }

    public static boolean startsInReservedBottomRegion(
            float y, int displayHeight, int reservedHeight) {
        if (displayHeight <= 0 || reservedHeight <= 0 || y < 0f) {
            return false;
        }
        int clampedHeight = Math.min(displayHeight, reservedHeight);
        return y >= displayHeight - clampedHeight;
    }
}
