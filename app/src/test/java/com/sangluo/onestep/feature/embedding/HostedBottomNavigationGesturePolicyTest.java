package com.sangluo.onestep.feature.embedding;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HostedBottomNavigationGesturePolicyTest {
    @Test
    public void recognizesBottomNavigationRegionAfterViewScaling() {
        assertTrue(HostedBottomNavigationGesturePolicy.startsInNavigationRegion(
                990f, 1000, 2480, 454));
        assertFalse(HostedBottomNavigationGesturePolicy.startsInNavigationRegion(
                950f, 1000, 2480, 454));
    }

    @Test
    public void rejectsInvalidDimensionsAndThresholds() {
        assertFalse(HostedBottomNavigationGesturePolicy.startsInNavigationRegion(
                990f, 0, 2480, 454));
        assertFalse(HostedBottomNavigationGesturePolicy.startsInNavigationRegion(
                990f, 1000, 2480, 0));
    }
}
