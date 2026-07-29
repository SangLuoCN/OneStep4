package com.sangluo.onestep.model;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

/** Immutable launcher entry displayed by the desktop and window switcher. */
public final class LauncherApp {
    public final String label;
    public final String packageName;
    public final ComponentName componentName;
    public final Drawable icon;

    public LauncherApp(String label, ComponentName componentName, Drawable icon) {
        this.label = label;
        this.componentName = componentName;
        this.packageName = componentName.getPackageName();
        this.icon = icon;
    }

    public Intent createLaunchIntent() {
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    public String componentKey() {
        return componentName.flattenToString();
    }
}
