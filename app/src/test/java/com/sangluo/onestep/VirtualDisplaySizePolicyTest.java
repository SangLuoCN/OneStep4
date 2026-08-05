package com.sangluo.onestep;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class VirtualDisplaySizePolicyTest {
    @Test
    public void capsPortraitLongEdgeWithoutChangingAspectRatio() {
        assertArrayEquals(new int[] {982, 2480},
                VirtualDisplaySizePolicy.capLongEdge(1080, 2727, 2480));
    }

    @Test
    public void leavesDimensionsWithinLimitUntouched() {
        assertArrayEquals(new int[] {834, 2106},
                VirtualDisplaySizePolicy.capLongEdge(834, 2106, 2480));
    }
}
