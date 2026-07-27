package com.sangluo.onestep.feature.embedding;

import android.view.View;

import com.sangluo.onestep.model.LauncherApp;

/** Common operations supported by embedded application backends. */
public interface EmbeddedAppHost {
    boolean isAvailable();

    View getView();

    boolean start(LauncherApp app);

    void refreshContainerSize();

    void sendBack();

    void sendHome();

    void invalidateTaskResolution();

    void closeApp(String packageName, Runnable onClosed);

    void release();

    String getUnavailableReason();
}
