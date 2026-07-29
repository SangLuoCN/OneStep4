package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusBarOverlayCrashGuardTest {
    @Test
    public void disablesOnlyAfterRepeatedFastDeaths() {
        StatusBarOverlayCrashGuard guard = new StatusBarOverlayCrashGuard(3, 5_000L, 15_000L);

        guard.markApplied("10001:app", 1_000L);
        assertFalse(guard.recordProcessDeath("10001:app", 2_000L));
        guard.markApplied("10001:app", 3_000L);
        assertFalse(guard.recordProcessDeath("10001:app", 4_000L));
        guard.markApplied("10001:app", 5_000L);
        assertTrue(guard.recordProcessDeath("10001:app", 6_000L));
        assertTrue(guard.isDisabled("10001:app"));
    }

    @Test
    public void ignoresDeathsOutsidePostApplyWindow() {
        StatusBarOverlayCrashGuard guard = new StatusBarOverlayCrashGuard(2, 1_000L, 10_000L);

        guard.markApplied("10001:app", 1_000L);
        assertFalse(guard.recordProcessDeath("10001:app", 2_001L));
        guard.markApplied("10001:app", 3_000L);
        assertFalse(guard.recordProcessDeath("10001:app", 3_500L));
        guard.markApplied("10001:app", 4_000L);
        assertFalse(guard.recordProcessDeath("10001:app", 5_001L));

        assertFalse(guard.isDisabled("10001:app"));
    }

    @Test
    public void firstApplyTimestampIsNotExtendedByRepeatedLaunches() {
        StatusBarOverlayCrashGuard guard = new StatusBarOverlayCrashGuard(1, 1_000L, 10_000L);

        guard.markApplied("10001:app", 1_000L);
        guard.markApplied("10001:app", 1_900L);

        assertFalse(guard.recordProcessDeath("10001:app", 2_100L));
        assertFalse(guard.isDisabled("10001:app"));
    }

    @Test
    public void ignoresProcessThatWasNeverOverlaid() {
        StatusBarOverlayCrashGuard guard = new StatusBarOverlayCrashGuard(1, 5_000L, 10_000L);

        assertFalse(guard.recordProcessDeath("10001:app", 1_000L));
        assertFalse(guard.isDisabled("10001:app"));
    }
}
