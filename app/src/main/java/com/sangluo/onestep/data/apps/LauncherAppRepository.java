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

    public LauncherAppRepository(Context context) {
        this.context = context.getApplicationContext();
        packageManager = context.getPackageManager();
    }

    public List<LauncherApp> loadLauncherApps() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(mainIntent, 0);
        Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(packageManager));

        List<LauncherApp> apps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (TextUtils.equals(packageName, context.getPackageName())) {
                continue;
            }
            apps.add(new LauncherApp(
                    String.valueOf(resolveInfo.loadLabel(packageManager)),
                    packageName,
                    resolveInfo.loadIcon(packageManager)));
        }
        return apps;
    }
}
