package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DismissedAppClosePolicyTest {
    @Test
    public void ordinaryAppIsForceStoppedAfterDismissal() {
        assertTrue(DismissedAppClosePolicy.shouldForceStop(false));
    }

    @Test
    public void homePackageStaysRunningAfterDismissal() {
        assertFalse(DismissedAppClosePolicy.shouldForceStop(true));
    }
}
