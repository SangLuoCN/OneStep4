package com.sangluo.onestep.data.apps;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;

import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Loads launchable applications with their system-provided labels and icons. */
public final class LauncherAppRepository {
    private static final int ZTE_CLONE_USER_ID = 999;

    private final Context context;
    private final PackageManager packageManager;
    private final LauncherApps launcherApps;
    private final UserManager userManager;
    private final SystemThemedIconLoader themedIconLoader;

    public LauncherAppRepository(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        launcherApps = context.getSystemService(LauncherApps.class);
        userManager = context.getSystemService(UserManager.class);
        themedIconLoader = new SystemThemedIconLoader(context);
    }

    public List<LauncherApp> loadLauncherApps() {
        return loadLauncherApps(false);
    }

    public List<LauncherApp> refreshLauncherApps() {
        return loadLauncherApps(true);
    }

    public List<LauncherApp> loadHomeApps() {
        return loadHomeApps(false);
    }

    /** Returns every selectable HOME candidate, including OneStep itself. */
    public List<LauncherApp> loadDefaultHomeApps() {
        return loadHomeApps(true);
    }

    private List<LauncherApp> loadHomeApps(boolean includeOneStep) {
        List<ResolveInfo> resolveInfos = queryHomeActivities();
        Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(packageManager));
        List<LauncherApp> apps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (!isSelectableHomeActivity(resolveInfo, includeOneStep)) {
                continue;
            }
            apps.add(createHomeApp(resolveInfo));
        }
        return apps;
    }

    public LauncherApp loadHomeApp(ComponentName componentName) {
        if (componentName == null
                || TextUtils.equals(componentName.getPackageName(), context.getPackageName())) {
            return null;
        }
        for (ResolveInfo resolveInfo : queryHomeActivities()) {
            if (isSelectableHomeActivity(resolveInfo, false)
                    && componentName.equals(componentNameOf(resolveInfo))) {
                return createHomeApp(resolveInfo);
            }
        }
        return null;
    }

    public LauncherApp loadLauncherApp(String packageName) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        ComponentName preferredComponent = launchIntent == null
                ? null : launchIntent.getComponent();
        List<LauncherActivityEntry> launcherActivities = queryLauncherActivities(packageName);
        if (preferredComponent != null) {
            for (LauncherActivityEntry entry : launcherActivities) {
                if (entry.isCurrentUser()
                        && preferredComponent.equals(entry.componentName())) {
                    return createLauncherApp(entry);
                }
            }
        }
        return launcherActivities.isEmpty()
                ? null : createLauncherApp(preferCurrentUser(launcherActivities));
    }

    private List<LauncherApp> loadLauncherApps(boolean invalidateThemeCaches) {
        List<LauncherActivityEntry> entries = queryLauncherActivities(null);
        entries.sort(Comparator
                .comparing(LauncherActivityEntry::label, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(entry -> entry.userHandle.hashCode()));
        if (invalidateThemeCaches) {
            List<ResolveInfo> resolveInfos = new ArrayList<>();
            for (LauncherActivityEntry entry : entries) {
                if (entry.resolveInfo != null) {
                    resolveInfos.add(entry.resolveInfo);
                }
            }
            themedIconLoader.invalidateThemeCaches(resolveInfos);
        }

        List<LauncherApp> apps = new ArrayList<>();
        for (LauncherActivityEntry entry : entries) {
            String packageName = entry.componentName().getPackageName();
            if (TextUtils.equals(packageName, context.getPackageName())) {
                continue;
            }
            apps.add(createLauncherApp(entry));
        }
        return apps;
    }

    private List<LauncherActivityEntry> queryLauncherActivities(String packageName) {
        List<LauncherActivityEntry> desktopActivities = queryLauncherService(packageName);
        if (!desktopActivities.isEmpty()) {
            return desktopActivities;
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (!TextUtils.isEmpty(packageName)) {
            mainIntent.setPackage(packageName);
        }
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        List<LauncherActivityEntry> entries = new ArrayList<>(resolveInfos.size());
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo != null) {
                entries.add(new LauncherActivityEntry(
                        resolveInfo, null, Process.myUserHandle()));
            }
        }
        return entries;
    }

    private List<ResolveInfo> queryHomeActivities() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);
        return packageManager.queryIntentActivities(
                homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
    }

    private boolean isSelectableHomeActivity(ResolveInfo resolveInfo,
                                             boolean includeOneStep) {
        return resolveInfo != null
                && resolveInfo.activityInfo != null
                && resolveInfo.priority >= 0
                && (includeOneStep || !TextUtils.equals(
                resolveInfo.activityInfo.packageName, context.getPackageName()));
    }

    private List<LauncherActivityEntry> queryLauncherService(String packageName) {
        if (launcherApps == null) {
            return Collections.emptyList();
        }
        List<UserHandle> profiles = userManager == null
                ? Collections.singletonList(Process.myUserHandle())
                : userManager.getUserProfiles();
        List<LauncherActivityEntry> result = new ArrayList<>();
        for (UserHandle profile : profiles) {
            try {
                List<LauncherActivityInfo> activityInfos = launcherApps.getActivityList(
                        packageName, profile);
                for (LauncherActivityInfo activityInfo : activityInfos) {
                    ResolveInfo resolveInfo = resolveLauncherActivity(
                            activityInfo.getComponentName());
                    result.add(new LauncherActivityEntry(
                            resolveInfo, activityInfo, profile));
                }
            } catch (RuntimeException ignored) {
                // A profile may be visible to UserManager but unavailable to LauncherApps.
            }
        }
        return result;
    }

    private ResolveInfo resolveLauncherActivity(ComponentName componentName) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(componentName);
        return packageManager.resolveActivity(intent, 0);
    }

    private ComponentName componentNameOf(ResolveInfo resolveInfo) {
        return new ComponentName(resolveInfo.activityInfo.packageName,
                resolveInfo.activityInfo.name);
    }

    private LauncherApp createLauncherApp(LauncherActivityEntry entry) {
        return new LauncherApp(
                entry.label(),
                entry.componentName(),
                entry.loadIcon(themedIconLoader, context),
                entry.userHandle);
    }

    private LauncherApp createHomeApp(ResolveInfo resolveInfo) {
        return LauncherApp.createHomeEntry(
                String.valueOf(resolveInfo.loadLabel(packageManager)),
                componentNameOf(resolveInfo),
                themedIconLoader.loadIcon(resolveInfo));
    }

    private LauncherActivityEntry preferCurrentUser(List<LauncherActivityEntry> entries) {
        for (LauncherActivityEntry entry : entries) {
            if (entry.isCurrentUser()) {
                return entry;
            }
        }
        return entries.get(0);
    }

    private final class LauncherActivityEntry {
        final ResolveInfo resolveInfo;
        final LauncherActivityInfo activityInfo;
        final UserHandle userHandle;

        LauncherActivityEntry(ResolveInfo resolveInfo, LauncherActivityInfo activityInfo,
                              UserHandle userHandle) {
            this.resolveInfo = resolveInfo;
            this.activityInfo = activityInfo;
            this.userHandle = userHandle;
        }

        ComponentName componentName() {
            return activityInfo != null
                    ? activityInfo.getComponentName() : componentNameOf(resolveInfo);
        }

        String label() {
            return String.valueOf(activityInfo != null
                    ? activityInfo.getLabel() : resolveInfo.loadLabel(packageManager));
        }

        boolean isCurrentUser() {
            return Process.myUserHandle().equals(userHandle);
        }

        android.graphics.drawable.Drawable loadIcon(
                SystemThemedIconLoader iconLoader, Context iconContext) {
            if (isCurrentUser() && resolveInfo != null) {
                return iconLoader.loadIcon(resolveInfo);
            }
            if (activityInfo != null) {
                if (userHandle.hashCode() == ZTE_CLONE_USER_ID) {
                    android.graphics.drawable.Drawable baseIcon = resolveInfo != null
                            ? iconLoader.loadIcon(resolveInfo)
                            : activityInfo.getIcon(iconContext.getResources()
                            .getDisplayMetrics().densityDpi);
                    return iconLoader.addCloneBadge(baseIcon);
                }
                return activityInfo.getBadgedIcon(
                        iconContext.getResources().getDisplayMetrics().densityDpi);
            }
            return iconLoader.loadIcon(resolveInfo);
        }
    }
}
