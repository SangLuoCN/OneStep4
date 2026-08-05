package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VirtualNavigationInputPolicyTest {
    @Test
    public void suppressesSequencesStartingInsideReservedBottomRegion() {
        assertFalse(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                2277f, 2400, 122));
        assertTrue(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                2278f, 2400, 122));
        assertTrue(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                2399f, 2400, 122));
    }

    @Test
    public void ignoresInvalidGeometry() {
        assertFalse(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                100f, 0, 122));
        assertFalse(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                100f, 2400, 0));
        assertFalse(VirtualNavigationInputPolicy.startsInReservedBottomRegion(
                -1f, 2400, 122));
    }
}
