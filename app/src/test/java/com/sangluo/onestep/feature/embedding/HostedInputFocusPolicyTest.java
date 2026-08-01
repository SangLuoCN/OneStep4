package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostedInputFocusPolicyTest {
    @Test
    public void supportedVersionsBeforeAndroid17KeepFocusGuard() {
        assertTrue(HostedInputFocusPolicy.shouldUseDemotedFocusWindow(29));
        assertTrue(HostedInputFocusPolicy.shouldUseDemotedFocusWindow(36));
    }

    @Test
    public void android17AndLaterSkipFocusGuard() {
        assertFalse(HostedInputFocusPolicy.shouldUseDemotedFocusWindow(37));
        assertFalse(HostedInputFocusPolicy.shouldUseDemotedFocusWindow(38));
    }
}
