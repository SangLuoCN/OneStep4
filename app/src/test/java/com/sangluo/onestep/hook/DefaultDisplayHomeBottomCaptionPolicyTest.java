package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DefaultDisplayHomeBottomCaptionPolicyTest {
    @Test
    public void suppressesOnlyDefaultDisplayCaptionForOneStepHome() {
        assertTrue(DefaultDisplayHomeBottomCaptionPolicy.shouldSuppress(
                0, "com.sangluo.onestep"));
        assertFalse(DefaultDisplayHomeBottomCaptionPolicy.shouldSuppress(
                3, "com.sangluo.onestep"));
        assertFalse(DefaultDisplayHomeBottomCaptionPolicy.shouldSuppress(
                0, "com.miui.home"));
        assertFalse(DefaultDisplayHomeBottomCaptionPolicy.shouldSuppress(0, null));
    }
}
