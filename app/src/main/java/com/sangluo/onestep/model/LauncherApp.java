package com.sangluo.onestep.model;

import android.graphics.drawable.Drawable;

/** Immutable launcher entry displayed by the desktop and window switcher. */
public final class LauncherApp {
    public final String label;
    public final String packageName;
    public final Drawable icon;

    public LauncherApp(String label, String packageName, Drawable icon) {
        this.label = label;
        this.packageName = packageName;
        this.icon = icon;
    }
}
