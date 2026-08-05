package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class VirtualDisplayImePolicyReadinessPolicyTest {
    @Test
    public void retriesWhileVirtualDisplayIsStarting() {
        assertEquals(VirtualDisplayImePolicyReadinessPolicy.Decision.RETRY,
                VirtualDisplayImePolicyReadinessPolicy.evaluate(
                        -1, false, false, 100L, 2100L));
    }

    @Test
    public void proceedsWhenVirtualDisplayIsReady() {
        assertEquals(VirtualDisplayImePolicyReadinessPolicy.Decision.READY,
                VirtualDisplayImePolicyReadinessPolicy.evaluate(
                        5, true, false, 100L, 2100L));
    }

    @Test
    public void rejectsWhenHostIsClosing() {
        assertEquals(VirtualDisplayImePolicyReadinessPolicy.Decision.REJECT,
                VirtualDisplayImePolicyReadinessPolicy.evaluate(
                        5, true, true, 100L, 2100L));
    }

    @Test
    public void rejectsWhenDisplayReadinessTimesOut() {
        assertEquals(VirtualDisplayImePolicyReadinessPolicy.Decision.REJECT,
                VirtualDisplayImePolicyReadinessPolicy.evaluate(
                        -1, false, false, 2100L, 2100L));
    }
}
