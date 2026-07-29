package com.sangluo.onestep.data.apps;

import android.content.Context;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Process;
import android.text.TextUtils;

import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads launchable applications with their system-provided labels and icons. */
public final class LauncherAppRepository {
    private final Context context;
    private final PackageManager packageManager;
    private final LauncherApps launcherApps;
    private final SystemThemedIconLoader themedIconLoader;

    public LauncherAppRepository(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
        launcherApps = context.getSystemService(LauncherApps.class);
        themedIconLoader = new SystemThemedIconLoader(context);
    }

    public List<LauncherApp> loadLauncherApps() {
        return loadLauncherApps(false);
    }

    public List<LauncherApp> refreshLauncherApps() {
        return loadLauncherApps(true);
    }

    public LauncherApp loadLauncherApp(String packageName) {
        Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        ComponentName preferredComponent = launchIntent == null
                ? null : launchIntent.getComponent();
        List<ResolveInfo> launcherActivities = queryLauncherActivities(packageName);
        if (preferredComponent != null) {
            for (ResolveInfo resolveInfo : launcherActivities) {
                if (preferredComponent.equals(componentNameOf(resolveInfo))) {
                    return createLauncherApp(resolveInfo);
                }
            }
        }
        return launcherActivities.isEmpty()
                ? null : createLauncherApp(launcherActivities.get(0));
    }

    private List<LauncherApp> loadLauncherApps(boolean invalidateThemeCaches) {
        List<ResolveInfo> resolveInfos = queryLauncherActivities(null);
        Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(packageManager));
        if (invalidateThemeCaches) {
            themedIconLoader.invalidateThemeCaches(resolveInfos);
        }

        List<LauncherApp> apps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (TextUtils.equals(packageName, context.getPackageName())) {
                continue;
            }
            apps.add(createLauncherApp(resolveInfo));
        }
        return apps;
    }

    private List<ResolveInfo> queryLauncherActivities(String packageName) {
        List<ResolveInfo> desktopActivities = queryLauncherService(packageName);
        if (!desktopActivities.isEmpty()) {
            return desktopActivities;
        }

        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        if (!TextUtils.isEmpty(packageName)) {
            mainIntent.setPackage(packageName);
        }
        return packageManager.queryIntentActivities(mainIntent, 0);
    }

    private List<ResolveInfo> queryLauncherService(String packageName) {
        if (launcherApps == null) {
            return Collections.emptyList();
        }
        try {
            List<LauncherActivityInfo> activityInfos = launcherApps.getActivityList(
                    packageName, Process.myUserHandle());
            List<ResolveInfo> result = new ArrayList<>(activityInfos.size());
            for (LauncherActivityInfo activityInfo : activityInfos) {
                ResolveInfo resolveInfo = resolveLauncherActivity(
                        activityInfo.getComponentName());
                if (resolveInfo != null && resolveInfo.activityInfo != null) {
                    result.add(resolveInfo);
                }
            }
            return result;
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
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

    private LauncherApp createLauncherApp(ResolveInfo resolveInfo) {
        return new LauncherApp(
                String.valueOf(resolveInfo.loadLabel(packageManager)),
                componentNameOf(resolveInfo),
                themedIconLoader.loadIcon(resolveInfo));
    }
}
