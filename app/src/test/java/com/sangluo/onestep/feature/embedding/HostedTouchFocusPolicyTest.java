package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HostedTouchFocusPolicyTest {
    @Test
    public void keepsOrdinaryContentTouchesOnHostedDisplay() {
        assertFalse(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                540f, 1200f, 0, 0, 1080, 2400,
                48, 0, 48, 72));
    }

    @Test
    public void reservesBottomAndSideGestureEdgesForPhysicalDisplay() {
        assertTrue(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                540f, 2350f, 0, 0, 1080, 2400,
                48, 0, 48, 72));
        assertTrue(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                20f, 1200f, 0, 0, 1080, 2400,
                48, 0, 48, 72));
        assertTrue(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                1060f, 1200f, 0, 0, 1080, 2400,
                48, 0, 48, 72));
    }

    @Test
    public void handlesOffsetWindowsAndMissingGestureInsets() {
        assertTrue(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                620f, 2170f, 100, 200, 1100, 2200,
                40, 0, 40, 60));
        assertFalse(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                620f, 2170f, 100, 200, 1100, 2200,
                0, 0, 0, 0));
        assertFalse(HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                50f, 1200f, 100, 200, 1100, 2200,
                40, 0, 40, 60));
    }
}
