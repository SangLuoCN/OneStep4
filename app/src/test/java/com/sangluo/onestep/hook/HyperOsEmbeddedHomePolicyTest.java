package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HyperOsEmbeddedHomePolicyTest {
    @Test
    public void keepsLauncherAliveOnOneStepDisplay() {
        assertTrue(HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive(
                "OneStepSlot-0/P6581/G1"));
    }

    @Test
    public void leavesOtherDisplaysUntouched() {
        assertFalse(HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive("Built-in display"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive("OneStep"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive(null));
    }

    @Test
    public void suppressesOnlyMiuiSecondaryLauncherOnOneStepDisplay() {
        assertTrue(HyperOsEmbeddedHomePolicy.shouldSuppressSecondaryLauncher(
                "com.miui.home",
                "com.miui.home.launcher.SecondaryDisplayLauncher",
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressSecondaryLauncher(
                "com.miui.home",
                "com.miui.home.launcher.Launcher",
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressSecondaryLauncher(
                "com.miui.home",
                "com.miui.home.launcher.SecondaryDisplayLauncher",
                "Built-in display"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressSecondaryLauncher(
                "com.example.launcher",
                "com.miui.home.launcher.SecondaryDisplayLauncher",
                "OneStepSlot-1/P6581/G1"));
    }

    @Test
    public void suppressesOnlyExactHomeLaunchOnOneStepDisplay() {
        assertTrue(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.MAIN", true, false, 1,
                "OneStepSlot-1/P6581/G1"));
        assertTrue(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.MAIN", false, true, 1,
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.VIEW", true, false, 1,
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.MAIN", false, false, 0,
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.MAIN", true, false, 2,
                "OneStepSlot-1/P6581/G1"));
        assertFalse(HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                "android.intent.action.MAIN", true, false, 1,
                "Built-in display"));
    }

}
