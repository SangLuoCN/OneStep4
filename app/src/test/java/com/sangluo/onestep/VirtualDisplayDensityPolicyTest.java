package com.sangluo.onestep;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VirtualDisplayDensityPolicyTest {
    @Test
    public void phoneKeepsCompactLogicalWidth() {
        assertEquals(440, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1080, 2336, 1080, 2336, 440, false));
    }

    @Test
    public void landscapeTabletExposesTabletLogicalSpace() {
        assertEquals(300, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1921, 1128, 1921, 1128, 320, true));
    }

    @Test
    public void portraitTabletExposesTabletLogicalSpace() {
        assertEquals(319, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1198, 2088, 1198, 2088, 320, true));
    }

    @Test
    public void qualityUpscalingAlsoScalesTabletDensity() {
        assertEquals(288, VirtualDisplayDensityPolicy.calculateDensityDpi(
                600, 1200, 1080, 2160, 320, true));
    }
}
