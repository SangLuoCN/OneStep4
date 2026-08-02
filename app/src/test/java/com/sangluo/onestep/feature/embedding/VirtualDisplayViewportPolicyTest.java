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
}
