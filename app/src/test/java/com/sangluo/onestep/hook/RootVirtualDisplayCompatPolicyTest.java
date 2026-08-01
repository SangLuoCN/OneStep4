package com.sangluo.onestep.hook;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RootVirtualDisplayCompatPolicyTest {
    private static final int SYSTEM_UID = 1000;

    @Test
    public void rootIsExemptOnlyFromAndroid10ThroughAndroid13() {
        assertFalse(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(28, 0));
        assertTrue(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(29, 0));
        assertTrue(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(33, 0));
        assertFalse(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(34, 0));
    }

    @Test
    public void nonRootUidAlwaysUsesPlatformValidation() {
        assertFalse(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(
                33, SYSTEM_UID));
        assertFalse(RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(
                33, 10_175));
    }

    @Test
    public void validationMethodMovedToOuterServiceInAndroid13() {
        assertEquals("com.android.server.display.DisplayManagerService$BinderService",
                RootVirtualDisplayCompatPolicy.validationOwnerClassName(32));
        assertEquals("com.android.server.display.DisplayManagerService",
                RootVirtualDisplayCompatPolicy.validationOwnerClassName(33));
    }
}
