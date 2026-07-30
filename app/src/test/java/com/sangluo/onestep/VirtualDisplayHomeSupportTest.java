package com.sangluo.onestep;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Build;

import org.junit.Test;

public class VirtualDisplayHomeSupportTest {
    @Test
    public void virtualDisplayConfigIsUsedFromAndroid14() {
        assertFalse(VirtualDisplayHomeSupport.supportsVirtualDisplayConfig(
                Build.VERSION_CODES.TIRAMISU));
        assertTrue(VirtualDisplayHomeSupport.supportsVirtualDisplayConfig(
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE));
    }

    @Test
    public void homeSupportRequiresEnhancementAndActiveHook() {
        int supportedSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE;

        assertTrue(VirtualDisplayHomeSupport.shouldRequestHomeSupport(
                supportedSdk, true, true));
        assertFalse(VirtualDisplayHomeSupport.shouldRequestHomeSupport(
                supportedSdk, false, true));
        assertFalse(VirtualDisplayHomeSupport.shouldRequestHomeSupport(
                supportedSdk, true, false));
        assertFalse(VirtualDisplayHomeSupport.shouldRequestHomeSupport(
                Build.VERSION_CODES.TIRAMISU, true, true));
    }

    @Test
    public void enhancedHomeLaunchRequiresHomeEntrySupportedDisplayAndHook() {
        assertTrue(VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                true, true, true));
        assertFalse(VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                false, true, true));
        assertFalse(VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                true, false, true));
        assertFalse(VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                true, true, false));
    }
}
