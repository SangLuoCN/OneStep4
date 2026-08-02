package com.sangluo.onestep.ui.window;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WindowLayoutModePolicyTest {
    @Test
    public void phoneUsesSingleMainLayout() {
        assertFalse(WindowLayoutModePolicy.isLargeScreen(411));
        assertFalse(WindowLayoutModePolicy.shouldUseDualMain(411, 411, 891));
    }

    @Test
    public void elongatedTabletUsesSingleMainInEitherOrientation() {
        assertTrue(WindowLayoutModePolicy.isLargeScreen(800));
        assertFalse(WindowLayoutModePolicy.shouldUseDualMain(800, 800, 1280));
        assertFalse(WindowLayoutModePolicy.shouldUseDualMain(800, 1280, 800));
    }

    @Test
    public void fourByThreeTabletUsesDualMainLayout() {
        assertTrue(WindowLayoutModePolicy.shouldUseDualMain(800, 800, 1067));
    }

    @Test
    public void unfoldedProFoldUsesDualMainLayout() {
        assertTrue(WindowLayoutModePolicy.shouldUseDualMain(852, 852, 883));
    }

    @Test
    public void missingDimensionsPreserveLargeScreenBehavior() {
        assertTrue(WindowLayoutModePolicy.shouldUseDualMain(600, 0, 0));
    }
}
