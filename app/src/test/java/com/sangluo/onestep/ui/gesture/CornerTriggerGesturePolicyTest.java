package com.sangluo.onestep.ui.gesture;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CornerTriggerGesturePolicyTest {
    @Test
    public void acceptsNaturalInwardDownwardSwipesFromBothCorners() {
        assertTrue(CornerTriggerGesturePolicy.matches(true, 120f, 50f, 100));
        assertTrue(CornerTriggerGesturePolicy.matches(false, -120f, 50f, 100));
    }

    @Test
    public void rejectsWrongDirectionAndInsufficientMovement() {
        assertFalse(CornerTriggerGesturePolicy.matches(true, -140f, 80f, 100));
        assertFalse(CornerTriggerGesturePolicy.matches(false, 140f, 80f, 100));
        assertFalse(CornerTriggerGesturePolicy.matches(true, 90f, 80f, 100));
        assertFalse(CornerTriggerGesturePolicy.matches(true, 140f, 20f, 100));
    }

    @Test
    public void rejectsMostlyVerticalStatusBarStylePulls() {
        assertFalse(CornerTriggerGesturePolicy.matches(true, 110f, 500f, 100));
        assertFalse(CornerTriggerGesturePolicy.matches(false, -110f, 500f, 100));
    }
}
