package com.sangluo.onestep.system.display;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DisplayOwnerPolicyTest {
    @Test
    public void currentOwnerCanMutateDisplay() {
        Object owner = new Object();

        assertTrue(DisplayOwnerPolicy.matches(owner, owner));
    }

    @Test
    public void staleOrMissingOwnerCannotMutateDisplay() {
        Object owner = new Object();

        assertFalse(DisplayOwnerPolicy.matches(owner, new Object()));
        assertFalse(DisplayOwnerPolicy.matches(owner, null));
        assertFalse(DisplayOwnerPolicy.matches(null, owner));
    }
}
