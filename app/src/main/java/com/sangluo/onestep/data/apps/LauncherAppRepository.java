package com.sangluo.onestep.data.apps;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;

import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loads launchable applications with their system-provided labels and icons. */
public final class LauncherAppRepository {
    private final Context context;
    private final PackageManager packageManager;
    private final SystemThemedIconLoader themedIconLoader;

    public LauncherAppRepository(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
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
        if (launchIntent == null) {
            return null;
        }
        ResolveInfo resolveInfo = packageManager.resolveActivity(launchIntent, 0);
        return resolveInfo == null || resolveInfo.activityInfo == null
                ? null : createLauncherApp(resolveInfo);
    }

    private List<LauncherApp> loadLauncherApps(boolean invalidateThemeCaches) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
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

    private LauncherApp createLauncherApp(ResolveInfo resolveInfo) {
        return new LauncherApp(
                String.valueOf(resolveInfo.loadLabel(packageManager)),
                resolveInfo.activityInfo.packageName,
                themedIconLoader.loadIcon(resolveInfo));
    }
}
