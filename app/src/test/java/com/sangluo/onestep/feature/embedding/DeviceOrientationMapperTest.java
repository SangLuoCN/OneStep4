package com.sangluo.onestep.feature.embedding;

import android.view.Surface;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeviceOrientationMapperTest {
    @Test
    public void mapsLandscapeSensorRanges() {
        assertEquals(Surface.ROTATION_270, DeviceOrientationMapper.mapLandscapeRotation(90));
        assertEquals(Surface.ROTATION_90, DeviceOrientationMapper.mapLandscapeRotation(270));
        assertEquals(-1, DeviceOrientationMapper.mapLandscapeRotation(0));
    }

    @Test
    public void recognizesStablePortraitRanges() {
        assertTrue(DeviceOrientationMapper.isStablePortrait(0));
        assertTrue(DeviceOrientationMapper.isStablePortrait(180));
        assertFalse(DeviceOrientationMapper.isStablePortrait(90));
    }

    @Test
    public void recognizesOnlyLandscapeDisplayRotations() {
        assertTrue(DeviceOrientationMapper.isLandscapeRotation(Surface.ROTATION_90));
        assertTrue(DeviceOrientationMapper.isLandscapeRotation(Surface.ROTATION_270));
        assertFalse(DeviceOrientationMapper.isLandscapeRotation(Surface.ROTATION_0));
    }
}
