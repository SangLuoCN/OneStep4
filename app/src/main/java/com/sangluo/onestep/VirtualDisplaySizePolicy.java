package com.sangluo.onestep;

/** Keeps an embedded virtual display within the host SurfaceView's maximum layer extent. */
public final class VirtualDisplaySizePolicy {
    private VirtualDisplaySizePolicy() {
    }

    public static int[] capLongEdge(int width, int height, int maxLongEdge) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        maxLongEdge = Math.max(1, maxLongEdge);
        int longEdge = Math.max(width, height);
        if (longEdge <= maxLongEdge) {
            return new int[] {width, height};
        }
        float scale = maxLongEdge / (float) longEdge;
        return new int[] {
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale))
        };
    }
}
