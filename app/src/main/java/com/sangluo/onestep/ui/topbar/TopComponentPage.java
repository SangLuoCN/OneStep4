package com.sangluo.onestep.ui.topbar;

import android.view.View;

/** Stable page entry displayed in the top component pager. */
public final class TopComponentPage {
    public final long id;
    public final View view;

    public TopComponentPage(long id, View view) {
        this.id = id;
        this.view = view;
    }
}
