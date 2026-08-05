package com.sangluo.onestep.feature.embedding;

/** Distinguishes a transient display startup from a real IME policy failure. */
public final class VirtualDisplayImePolicyReadinessPolicy {
    public enum Decision {
        READY,
        RETRY,
        REJECT
    }

    private VirtualDisplayImePolicyReadinessPolicy() {
    }

    public static Decision evaluate(int displayId, boolean hasVirtualDisplay,
                                    boolean closing, long nowUptimeMs,
                                    long readyDeadlineUptimeMs) {
        if (closing) {
            return Decision.REJECT;
        }
        if (displayId > 0 && hasVirtualDisplay) {
            return Decision.READY;
        }
        return nowUptimeMs < readyDeadlineUptimeMs
                ? Decision.RETRY : Decision.REJECT;
    }
}
