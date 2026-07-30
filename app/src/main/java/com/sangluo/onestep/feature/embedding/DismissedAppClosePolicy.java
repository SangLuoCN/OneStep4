package com.sangluo.onestep.feature.embedding;

/** Decides whether dismissing a hosted task should also stop its package. */
public final class DismissedAppClosePolicy {
    private DismissedAppClosePolicy() {
    }

    public static boolean shouldForceStop(boolean homeEntry) {
        return !homeEntry;
    }
}
