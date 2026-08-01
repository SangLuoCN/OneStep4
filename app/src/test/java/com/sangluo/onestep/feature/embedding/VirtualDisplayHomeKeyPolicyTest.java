package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VirtualDisplayHomeKeyPolicyTest {
    @Test
    public void android11SkipsVirtualHomeKey() {
        assertFalse(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(30));
    }

    @Test
    public void otherSupportedVersionsKeepExistingBehavior() {
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(29));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(31));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(32));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(33));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(34));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(35));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(36));
        assertTrue(VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(37));
    }
}
