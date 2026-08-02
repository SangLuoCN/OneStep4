package com.sangluo.onestep;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VirtualDisplayDensityPolicyTest {
    @Test
    public void phoneFollowsHostDensityInsteadOfFixedLogicalWidth() {
        assertEquals(480, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1080, 2336, 480, false));
    }

    @Test
    public void foldPaneUsesHostDensityWithoutFixedPixelUpscaling() {
        assertEquals(390, VirtualDisplayDensityPolicy.calculateDensityDpi(
                886, 1577, 390, false));
    }

    @Test
    public void densityNeverDropsBelowThreeHundredDpi() {
        assertEquals(300, VirtualDisplayDensityPolicy.calculateDensityDpi(
                600, 1200, 160, false));
    }

    @Test
    public void landscapeTabletExposesTabletLogicalSpace() {
        assertEquals(300, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1921, 1128, 320, true));
    }

    @Test
    public void portraitTabletExposesTabletLogicalSpace() {
        assertEquals(319, VirtualDisplayDensityPolicy.calculateDensityDpi(
                1198, 2088, 320, true));
    }

    @Test
    public void tabletDensityNeverDropsBelowMinimum() {
        assertEquals(300, VirtualDisplayDensityPolicy.calculateDensityDpi(
                600, 1200, 320, true));
    }

    @Test
    public void landscapeTabletExpandsPixelsWithoutChangingDensity() {
        float scale = VirtualDisplayDensityPolicy.calculateTabletPixelScale(
                1921, 1128, 300, true);

        assertEquals(2555, Math.round(1921 * scale));
        assertEquals(1500, Math.round(1128 * scale));
    }

    @Test
    public void phoneDoesNotExpandVirtualDisplayPixels() {
        assertEquals(1f, VirtualDisplayDensityPolicy.calculateTabletPixelScale(
                1921, 1128, 300, false), 0f);
    }

    @Test
    public void roomyTabletDoesNotExpandVirtualDisplayPixels() {
        assertEquals(1f, VirtualDisplayDensityPolicy.calculateTabletPixelScale(
                2560, 1600, 300, true), 0f);
    }
}
