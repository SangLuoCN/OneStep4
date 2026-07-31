package com.sangluo.onestep.hook;

import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed backend that reuses the framework's existing XposedBridge instance. */
public final class OneStepLsposedEntry implements IXposedHookLoadPackage {
    private static final String TAG = "OneStepLsposed";
    private static final String SYSTEM_FRAMEWORK_PACKAGE = "android";
    private static final String SYSTEM_SERVER_PROCESS = "android";
    private static final String SECURE_WINDOW_PROPERTY = "onestep.hook.secure";
    private static final String STATUS_BAR_PROPERTY = "onestep.hook.statusbar";
    private static final String PRIMARY_HOME_ENHANCEMENT_PROPERTY =
            "onestep.hook.primaryhome_enhancement";
    private static final String ACTIVE_MARKER =
            "/data/system/onestep-lsposed-backend-active";
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (loadPackageParam == null
                || !SYSTEM_FRAMEWORK_PACKAGE.equals(loadPackageParam.packageName)
                || !SYSTEM_SERVER_PROCESS.equals(loadPackageParam.processName)
                || !STARTED.compareAndSet(false, true)) {
            return;
        }
        markBackendActive();
        boolean secureWindowEnabled = readBooleanProperty(SECURE_WINDOW_PROPERTY);
        boolean statusBarEnabled = readBooleanProperty(STATUS_BAR_PROPERTY);
        boolean primaryHomeEnhancementEnabled =
                readBooleanProperty(PRIMARY_HOME_ENHANCEMENT_PROPERTY);
        Log.i(TAG, "LSPosed backend selected: secure=" + secureWindowEnabled
                + ", statusbar=" + statusBarEnabled
                + ", primaryHome=" + primaryHomeEnhancementEnabled);
        OneStepRootVirtualDisplayCompatHook.bootstrap(loadPackageParam.classLoader);
        if (secureWindowEnabled) {
            OneStepSecureWindowHook.bootstrap(loadPackageParam.classLoader);
        }
        if (statusBarEnabled) {
            OneStepStatusBarOverlayHook.bootstrap(loadPackageParam.classLoader);
        }
        if (primaryHomeEnhancementEnabled) {
            OneStepPrimaryHomeHook.bootstrap(loadPackageParam.classLoader);
        }
    }

    private static boolean readBooleanProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod(
                    "get", String.class, String.class);
            get.setAccessible(true);
            return "1".equals(get.invoke(null, key, "0"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, "Cannot read hook property " + key, e);
            return false;
        }
    }

    private static void markBackendActive() {
        try {
            File marker = new File(ACTIVE_MARKER);
            if (!marker.exists() && !marker.createNewFile()) {
                Log.w(TAG, "Could not create LSPosed backend marker");
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Could not mark LSPosed backend active", e);
        }
    }
}
