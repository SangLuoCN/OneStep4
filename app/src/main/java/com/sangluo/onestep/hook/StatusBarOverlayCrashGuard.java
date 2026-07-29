package com.sangluo.onestep.hook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Disables the overlay for processes that repeatedly die immediately after it is applied. */
final class StatusBarOverlayCrashGuard {
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final long DEFAULT_POST_APPLY_WINDOW_MILLIS = 5_000L;
    private static final long DEFAULT_FAILURE_WINDOW_MILLIS = 15_000L;

    private final int failureThreshold;
    private final long postApplyWindowMillis;
    private final long failureWindowMillis;
    private final Map<String, Long> appliedAtByProcess = new HashMap<>();
    private final Map<String, FailureState> failuresByProcess = new HashMap<>();
    private final Set<String> disabledProcesses = new HashSet<>();

    StatusBarOverlayCrashGuard() {
        this(DEFAULT_FAILURE_THRESHOLD,
                DEFAULT_POST_APPLY_WINDOW_MILLIS,
                DEFAULT_FAILURE_WINDOW_MILLIS);
    }

    StatusBarOverlayCrashGuard(int failureThreshold, long postApplyWindowMillis,
                               long failureWindowMillis) {
        this.failureThreshold = failureThreshold;
        this.postApplyWindowMillis = postApplyWindowMillis;
        this.failureWindowMillis = failureWindowMillis;
    }

    synchronized void markApplied(String processKey, long now) {
        if (!disabledProcesses.contains(processKey)) {
            appliedAtByProcess.putIfAbsent(processKey, now);
        }
    }

    synchronized void clearApplied(String processKey) {
        appliedAtByProcess.remove(processKey);
    }

    synchronized boolean recordProcessDeath(String processKey, long now) {
        Long appliedAt = appliedAtByProcess.remove(processKey);
        if (appliedAt == null || now < appliedAt || now - appliedAt > postApplyWindowMillis) {
            failuresByProcess.remove(processKey);
            return false;
        }

        FailureState state = failuresByProcess.get(processKey);
        if (state == null || now < state.firstFailureAt
                || now - state.firstFailureAt > failureWindowMillis) {
            state = new FailureState(now, 1);
        } else {
            state = new FailureState(state.firstFailureAt, state.failureCount + 1);
        }
        failuresByProcess.put(processKey, state);
        if (state.failureCount < failureThreshold) {
            return false;
        }

        failuresByProcess.remove(processKey);
        return disabledProcesses.add(processKey);
    }

    synchronized boolean isDisabled(String processKey) {
        return disabledProcesses.contains(processKey);
    }

    private static final class FailureState {
        final long firstFailureAt;
        final int failureCount;

        FailureState(long firstFailureAt, int failureCount) {
            this.firstFailureAt = firstFailureAt;
            this.failureCount = failureCount;
        }
    }
}
