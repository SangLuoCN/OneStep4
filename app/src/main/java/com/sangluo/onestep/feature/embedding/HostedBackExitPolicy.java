package com.sangluo.onestep.feature.embedding;

/** Replaces a returned app only after a successful scan proves its task is absent. */
public final class HostedBackExitPolicy {
    private static final int REQUIRED_MISSING_SCANS = 1;

    public enum Action {
        KEEP_APP,
        RETRY,
        SHOW_DESKTOP
    }

    private HostedBackExitPolicy() {
    }

    public static Action afterScan(boolean scanSucceeded, boolean taskPresent,
                                   int previousMissingScans, boolean canRetry) {
        if (scanSucceeded && taskPresent) {
            return Action.KEEP_APP;
        }
        if (scanSucceeded && previousMissingScans + 1 >= REQUIRED_MISSING_SCANS) {
            return Action.SHOW_DESKTOP;
        }
        return canRetry ? Action.RETRY : Action.KEEP_APP;
    }
}
