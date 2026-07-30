package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HostedBackExitPolicyTest {
    @Test
    public void existingTaskKeepsCurrentApp() {
        assertEquals(HostedBackExitPolicy.Action.KEEP_APP,
                HostedBackExitPolicy.afterScan(true, true, 0, true));
    }

    @Test
    public void firstConfirmedMissingScanShowsDesktop() {
        assertEquals(HostedBackExitPolicy.Action.SHOW_DESKTOP,
                HostedBackExitPolicy.afterScan(true, false, 0, true));
    }

    @Test
    public void laterConfirmedMissingScanAlsoShowsDesktop() {
        assertEquals(HostedBackExitPolicy.Action.SHOW_DESKTOP,
                HostedBackExitPolicy.afterScan(true, false, 1, true));
    }

    @Test
    public void failedScansNeverReplaceAppWithoutEvidence() {
        assertEquals(HostedBackExitPolicy.Action.RETRY,
                HostedBackExitPolicy.afterScan(false, false, 0, true));
        assertEquals(HostedBackExitPolicy.Action.KEEP_APP,
                HostedBackExitPolicy.afterScan(false, false, 0, false));
    }
}
