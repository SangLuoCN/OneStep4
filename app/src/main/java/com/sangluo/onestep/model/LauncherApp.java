package com.sangluo.onestep.model;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.os.UserHandle;

import com.sangluo.onestep.hook.OneStepPrimaryHomePolicy;

/** Immutable launcher entry displayed by the desktop and window switcher. */
public final class LauncherApp {
    public final String label;
    public final String packageName;
    public final ComponentName componentName;
    public final Drawable icon;
    public final UserHandle userHandle;
    private final String launchCategory;

    public LauncherApp(String label, ComponentName componentName, Drawable icon) {
        this(label, componentName, icon, Process.myUserHandle());
    }

    public LauncherApp(String label, ComponentName componentName, Drawable icon,
                       UserHandle userHandle) {
        this(label, componentName, icon, Intent.CATEGORY_LAUNCHER, userHandle);
    }

    public static LauncherApp createHomeEntry(
            String label, ComponentName componentName, Drawable icon) {
        return new LauncherApp(label, componentName, icon, Intent.CATEGORY_HOME);
    }

    private LauncherApp(String label, ComponentName componentName, Drawable icon,
                        String launchCategory) {
        this(label, componentName, icon, launchCategory, Process.myUserHandle());
    }

    private LauncherApp(String label, ComponentName componentName, Drawable icon,
                        String launchCategory, UserHandle userHandle) {
        this.label = label;
        this.componentName = componentName;
        this.packageName = componentName.getPackageName();
        this.icon = icon;
        this.userHandle = userHandle == null ? Process.myUserHandle() : userHandle;
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

    /** Identifies one installed instance even when multiple users expose the same component. */
    public String instanceKey() {
        return componentKey() + "@" + userId();
    }

    /** UserHandle.hashCode() is the public SDK representation of its integer handle. */
    public int userId() {
        return userHandle.hashCode();
    }

    public boolean isSameInstance(LauncherApp other) {
        return other != null
                && componentName.equals(other.componentName)
                && userHandle.equals(other.userHandle);
    }

    public boolean isCurrentUser() {
        return Process.myUserHandle().equals(userHandle);
    }
}
