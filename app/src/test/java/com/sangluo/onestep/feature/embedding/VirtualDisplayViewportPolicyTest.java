package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VirtualDisplayViewportPolicyTest {
    @Test
    public void singleMainLayoutKeepsOneWorkspaceSpecForEverySlot() {
        assertTrue(VirtualDisplayViewportPolicy.shouldUseWorkspaceSpec(false));
    }

    @Test
    public void dualMainPaneStillAdaptsToLargeScreenLayout() {
        assertFalse(VirtualDisplayViewportPolicy.shouldUseWorkspaceSpec(true));
    }

    @Test
    public void phoneSingleMainLayoutKeepsStableVirtualDisplay() {
        assertFalse(VirtualDisplayViewportPolicy.shouldResizeForContainerLayout(
                false, false, false));
    }

    @Test
    public void largeScreenSingleMainLayoutCanResizeVirtualDisplay() {
        assertTrue(VirtualDisplayViewportPolicy.shouldResizeForContainerLayout(
                true, false, false));
    }

    @Test
    public void leavingDualMainLayoutCanResizeVirtualDisplay() {
        assertTrue(VirtualDisplayViewportPolicy.shouldResizeForContainerLayout(
                false, false, true));
    }

    @Test
    public void matchingFullscreenAspectKeepsCurrentLayoutPath() {
        assertFalse(VirtualDisplayViewportPolicy.shouldRefreshForTargetAspect(
                1116, 2480, 1116, 2480));
    }

    @Test
    public void smallFullscreenAspectDifferenceKeepsCurrentLayoutPath() {
        assertFalse(VirtualDisplayViewportPolicy.shouldRefreshForTargetAspect(
                1080, 2273, 1116, 2480));
    }

    @Test
    public void largeFullscreenAspectDifferenceRequestsSizeRefresh() {
        assertTrue(VirtualDisplayViewportPolicy.shouldRefreshForTargetAspect(
                1080, 1922, 2076, 2152));
    }

    @Test
    public void largeOneStepPaneAspectDifferenceRequestsSizeRefresh() {
        assertTrue(VirtualDisplayViewportPolicy.shouldRefreshForTargetAspect(
                2076, 2152, 1080, 1922));
    }

    @Test
    public void missingDimensionsDoNotForceTargetRefresh() {
        assertFalse(VirtualDisplayViewportPolicy.shouldRefreshForTargetAspect(
                0, 0, 1116, 2480));
    }
}
