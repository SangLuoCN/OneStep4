package com.sangluo.onestep.feature.embedding;

import android.view.View;

import com.sangluo.onestep.model.LauncherApp;

/** Common operations supported by embedded application backends. */
public interface EmbeddedAppHost {
    boolean isAvailable();

    View getView();

    default boolean canStartBeforeLayout() {
        return false;
    }

    boolean start(LauncherApp app);

    default boolean restart(LauncherApp app) {
        return start(app);
    }

    void refreshContainerSize();

    /**
     * Refreshes the host after a layout change. Virtual-display implementations may use the
     * force flag when a stable settings change alters the logical viewport.
     */
    default void refreshContainerSize(boolean forceVirtualDisplayResize) {
        refreshContainerSize();
    }

    void sendBack();

    void sendHome();

    void invalidateTaskResolution();

    void closeApp(LauncherApp app, Runnable onClosed);

    void release();

    String getUnavailableReason();
}
