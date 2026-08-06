package com.sangluo.onestep;

import android.app.Application;
import android.os.UserManager;

import com.sangluo.onestep.data.apps.LauncherAppRepository;

/** Warms the shared launcher catalog when another OneStep component starts first. */
public final class OneStepApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        UserManager userManager = getSystemService(UserManager.class);
        if (userManager == null || userManager.isUserUnlocked()) {
            LauncherAppRepository.preloadLauncherApps(this);
        }
    }
}
