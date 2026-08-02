package com.sangluo.onestep.ui.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MainPaneFullscreenPolicyTest {
    @Test
    public void leftSideRailSelectsRightmostMainPane() {
        assertEquals(0, MainPaneFullscreenPolicy.selectEdgeMainSlot(
                2076,
                0, 1040, 2076,
                1, 522, 1038,
                1));
    }

    @Test
    public void rightSideRailSelectsLeftmostMainPane() {
        assertEquals(0, MainPaneFullscreenPolicy.selectEdgeMainSlot(
                2076,
                0, 0, 516,
                1, 518, 1034,
                1));
    }

    @Test
    public void swappedMainPanesSelectsNewEdgePane() {
        assertEquals(1, MainPaneFullscreenPolicy.selectEdgeMainSlot(
                2076,
                0, 522, 1038,
                1, 1040, 2076,
                0));
    }

    @Test
    public void equalEdgeDistanceKeepsCurrentMainPane() {
        assertEquals(1, MainPaneFullscreenPolicy.selectEdgeMainSlot(
                2076,
                0, 0, 1037,
                1, 1039, 2076,
                1));
    }
}
