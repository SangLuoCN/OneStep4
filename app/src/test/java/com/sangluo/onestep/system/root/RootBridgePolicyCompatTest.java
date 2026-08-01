package com.sangluo.onestep.system.root;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootBridgePolicyCompatTest {
    @Test
    public void android11SkipsLivePolicyReload() {
        assertFalse(RootBridgePolicyCompat.shouldApplyLivePolicy(30));
    }

    @Test
    public void otherSupportedVersionsKeepExistingBehavior() {
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(29));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(31));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(32));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(33));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(34));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(35));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(36));
        assertTrue(RootBridgePolicyCompat.shouldApplyLivePolicy(37));
    }
}
