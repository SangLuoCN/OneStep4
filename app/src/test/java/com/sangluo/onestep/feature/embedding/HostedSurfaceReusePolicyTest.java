package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostedSurfaceReusePolicyTest {
    @Test
    public void pendingImageShareSkipsStaleTaskValidation() {
        assertFalse(HostedSurfaceReusePolicy.shouldValidateReusedTask(true));
        assertTrue(HostedSurfaceReusePolicy.shouldValidateReusedTask(false));
    }

    @Test
    public void matchingVisibleSurfaceStaysVisibleDuringValidation() {
        assertTrue(HostedSurfaceReusePolicy.shouldKeepVisibleDuringValidation(
                true, false, true));
        assertFalse(HostedSurfaceReusePolicy.shouldBeginReveal(
                true, false, false, true));
    }

    @Test
    public void newLaunchAlwaysStartsConcealedReveal() {
        assertTrue(HostedSurfaceReusePolicy.shouldBeginReveal(
                false, false, false, false));
    }

    @Test
    public void resolvedReuseDoesNotRestartReveal() {
        assertFalse(HostedSurfaceReusePolicy.shouldBeginReveal(
                true, true, false, false));
    }

    @Test
    public void pendingRevealIsNotRestartedByRepeatedSync() {
        assertFalse(HostedSurfaceReusePolicy.shouldBeginReveal(
                true, false, true, false));
    }

    @Test
    public void invisibleUnresolvedReuseRestartsReveal() {
        assertTrue(HostedSurfaceReusePolicy.shouldBeginReveal(
                true, false, false, false));
    }
}
