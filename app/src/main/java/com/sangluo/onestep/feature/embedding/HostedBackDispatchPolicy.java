package com.sangluo.onestep.feature.embedding;

import android.view.Display;
import android.view.KeyEvent;

/** Keeps hosted Back routing independent from app-specific task structure. */
public final class HostedBackDispatchPolicy {
    private HostedBackDispatchPolicy() {
    }

    public static boolean shouldTrySystemNavigation(int displayId, int keyCode) {
        return displayId > Display.DEFAULT_DISPLAY && keyCode == KeyEvent.KEYCODE_BACK;
    }

    public static boolean shouldDispatchBeforeExitCheck(
            boolean hasApp, boolean homeEntry, boolean mainDisplaySlot) {
        return hasApp && !homeEntry && mainDisplaySlot;
    }
}
