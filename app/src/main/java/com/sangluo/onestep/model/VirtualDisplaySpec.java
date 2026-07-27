package com.sangluo.onestep.model;

/** Pixel dimensions and density used when creating an embedded virtual display. */
public final class VirtualDisplaySpec {
    public final int width;
    public final int height;
    public final int densityDpi;

    public VirtualDisplaySpec(int width, int height, int densityDpi) {
        this.width = width;
        this.height = height;
        this.densityDpi = densityDpi;
    }
}
