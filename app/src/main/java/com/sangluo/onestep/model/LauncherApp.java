package com.sangluo.onestep.model;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;

import com.sangluo.onestep.hook.OneStepPrimaryHomePolicy;

/** Immutable launcher entry displayed by the desktop and window switcher. */
public final class LauncherApp {
    public final String label;
    public final String packageName;
    public final ComponentName componentName;
    public final Drawable icon;
    private final String launchCategory;

    public LauncherApp(String label, ComponentName componentName, Drawable icon) {
        this(label, componentName, icon, Intent.CATEGORY_LAUNCHER);
    }

    public static LauncherApp createHomeEntry(
            String label, ComponentName componentName, Drawable icon) {
        return new LauncherApp(label, componentName, icon, Intent.CATEGORY_HOME);
    }

    private LauncherApp(String label, ComponentName componentName, Drawable icon,
                        String launchCategory) {
        this.label = label;
        this.componentName = componentName;
        this.packageName = componentName.getPackageName();
        this.icon = icon;
        this.launchCategory = launchCategory;
    }

    public Intent createLaunchIntent() {
        return createLaunchIntent(false);
    }

    public Intent createLaunchIntent(boolean primaryHomeEnhancementActive) {
        boolean enhancedHomeLaunch = isHomeEntry() && primaryHomeEnhancementActive;
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(enhancedHomeLaunch
                        ? Intent.CATEGORY_HOME : Intent.CATEGORY_LAUNCHER)
                .setComponent(componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (enhancedHomeLaunch) {
            intent.putExtra(OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME, true);
        }
        return intent;
    }

    public boolean isHomeEntry() {
        return Intent.CATEGORY_HOME.equals(launchCategory);
    }

    public String componentKey() {
        return componentName.flattenToString();
    }
}
