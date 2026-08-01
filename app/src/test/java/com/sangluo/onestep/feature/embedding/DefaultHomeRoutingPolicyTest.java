package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DefaultHomeRoutingPolicyTest {
    @Test
    public void interceptsPhysicalHomeOnlyWhenOneStepIsDefault() {
        assertTrue(DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                "com.sangluo.onestep", "com.sangluo.onestep"));
        assertFalse(DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                "com.sangluo.onestep", "com.miui.home"));
        assertFalse(DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                "com.sangluo.onestep", "com.example.launcher"));
        assertFalse(DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                "com.sangluo.onestep", null));
        assertFalse(DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                null, "com.sangluo.onestep"));
    }
}
