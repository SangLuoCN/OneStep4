package com.sangluo.onestep.feature.drag;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ImageDragHoverPolicyTest {
    @Test
    public void findsOnlyEligibleFrameContainingPointer() {
        int[][] frames = {
                {0, 0, 700, 1000},
                {700, 0, 1000, 300},
                {700, 300, 1000, 600}
        };
        boolean[] eligible = {false, true, true};

        assertEquals(1, ImageDragHoverPolicy.findTarget(820, 120, frames, eligible));
        assertEquals(2, ImageDragHoverPolicy.findTarget(820, 420, frames, eligible));
        assertEquals(-1, ImageDragHoverPolicy.findTarget(200, 200, frames, eligible));
    }

}
