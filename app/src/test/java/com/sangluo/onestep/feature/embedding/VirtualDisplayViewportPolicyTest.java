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
}
