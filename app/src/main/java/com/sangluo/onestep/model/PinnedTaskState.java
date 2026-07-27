package com.sangluo.onestep.model;

import android.graphics.Rect;

/** Snapshot of the currently pinned task returned by the root input bridge. */
public final class PinnedTaskState {
    public final boolean active;
    public final int taskId;
    public final Rect bounds;

    public PinnedTaskState(boolean active, int taskId, Rect bounds) {
        this.active = active;
        this.taskId = taskId;
        this.bounds = bounds == null ? new Rect() : new Rect(bounds);
    }
}
