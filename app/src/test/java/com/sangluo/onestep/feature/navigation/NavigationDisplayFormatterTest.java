package com.sangluo.onestep.feature.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NavigationDisplayFormatterTest {
    @Test
    public void formatsDistanceAtUnitBoundaries() {
        assertEquals("999 米", NavigationDisplayFormatter.formatNavigationDistance(999));
        assertEquals("1.0 公里", NavigationDisplayFormatter.formatNavigationDistance(1000));
        assertEquals("10 公里", NavigationDisplayFormatter.formatNavigationDistance(10_000));
    }

    @Test
    public void formatsRoadTransitionAndMissingRoads() {
        assertEquals("未知道路", NavigationDisplayFormatter.formatNavigationRoads(null, ""));
        assertEquals("中山路", NavigationDisplayFormatter.formatNavigationRoads("中山路", null));
        assertEquals("中山路 → 人民路",
                NavigationDisplayFormatter.formatNavigationRoads("中山路", "人民路"));
    }

    @Test
    public void roundsRemainingTimeUpToMinutes() {
        assertEquals("1分钟", NavigationDisplayFormatter.formatNavigationRemainTime(1));
        assertEquals("1时1分", NavigationDisplayFormatter.formatNavigationRemainTime(3601));
    }
}
