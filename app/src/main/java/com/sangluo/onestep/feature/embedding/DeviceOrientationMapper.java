package com.sangluo.onestep.feature.embedding;

import android.view.OrientationEventListener;
import android.view.Surface;

/** Converts physical sensor angles into the landscape rotations used by hosted displays. */
public final class DeviceOrientationMapper {
    private static final int LANDSCAPE_MIN_DEGREES = 60;
    private static final int LANDSCAPE_MAX_DEGREES = 120;
    private static final int REVERSE_LANDSCAPE_MIN_DEGREES = 240;
    private static final int REVERSE_LANDSCAPE_MAX_DEGREES = 300;
    private static final int PORTRAIT_EDGE_DEGREES = 30;
    private static final int UPSIDE_DOWN_MIN_DEGREES = 150;
    private static final int UPSIDE_DOWN_MAX_DEGREES = 210;

    private DeviceOrientationMapper() {
    }

    public static int mapLandscapeRotation(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            return -1;
        }
        if (orientation >= LANDSCAPE_MIN_DEGREES && orientation <= LANDSCAPE_MAX_DEGREES) {
            return Surface.ROTATION_270;
        }
        if (orientation >= REVERSE_LANDSCAPE_MIN_DEGREES
                && orientation <= REVERSE_LANDSCAPE_MAX_DEGREES) {
            return Surface.ROTATION_90;
        }
        return -1;
    }

    public static boolean isStablePortrait(int orientation) {
        return orientation != OrientationEventListener.ORIENTATION_UNKNOWN
                && (orientation <= PORTRAIT_EDGE_DEGREES
                || orientation >= 360 - PORTRAIT_EDGE_DEGREES
                || (orientation >= UPSIDE_DOWN_MIN_DEGREES
                && orientation <= UPSIDE_DOWN_MAX_DEGREES));
    }

    public static boolean isLandscapeRotation(int rotation) {
        return rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
    }
}
