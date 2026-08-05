package com.sangluo.onestep.hook;

import android.content.ComponentName;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Prevents SystemUI from disabling gestures solely because HOME is third-party. */
public final class HyperOsSystemUiGestureNavigationBypassHook {
    private static final String TAG = "OneStepHyperOsSystemUi";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String LOADED_APK_CLASS = "android.app.LoadedApk";
    private static final String PHONE_STATE_MONITOR_CONTROLLER_CLASS =
            "com.android.systemui.assist.PhoneStateMonitorController";
    private static boolean loaderHookInstalled;
    private static boolean targetHookInstalled;
    private static boolean gestureRestrictionHookInstalled;

    private HyperOsSystemUiGestureNavigationBypassHook() {
    }

    /** Standalone Zygisk entry, installed before the target APK class loader exists. */
    public static synchronized void bootstrap(ClassLoader frameworkClassLoader) {
        if (loaderHookInstalled || targetHookInstalled || frameworkClassLoader == null) {
            return;
        }
        if (!isHyperOs()) {
            Log.i(TAG, "non-HyperOS system; SystemUI gesture bypass skipped");
            return;
        }
        try {
            HookBridgeCompat.disableHiddenApiRestrictions();
            Class<?> loadedApkClass = Class.forName(
                    LOADED_APK_CLASS, false, frameworkClassLoader);
            Method getClassLoader = loadedApkClass.getDeclaredMethod("getClassLoader");
            getClassLoader.setAccessible(true);
            XposedBridge.hookMethod(getClassLoader, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (targetHookInstalled || !isSystemUiLoadedApk(param.thisObject)) {
                        return;
                    }
                    Object result = param.getResult();
                    if (result instanceof ClassLoader) {
                        install((ClassLoader) result);
                    }
                }
            });
            HookBridgeCompat.deoptimizeMethod(getClassLoader);
            loaderHookInstalled = true;
            Log.i(TAG, "waiting for the SystemUI class loader");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "LoadedApk class-loader hook is unavailable", e);
        } catch (Throwable t) {
            Log.e(TAG, "could not prepare SystemUI gesture bypass", t);
        }
    }

    /** LSPosed entry, called with SystemUI's class loader. */
    public static synchronized void install(ClassLoader targetClassLoader) {
        if (targetHookInstalled || targetClassLoader == null) {
            return;
        }
        if (!isHyperOs()) {
            Log.i(TAG, "non-HyperOS system; SystemUI gesture bypass skipped");
            return;
        }
        try {
            HookBridgeCompat.disableHiddenApiRestrictions();
            boolean gestureHookInstalled = installGestureRestrictionHook(targetClassLoader);
            targetHookInstalled = gestureHookInstalled;
            Log.i(TAG, "HyperOS SystemUI hooks installed; gestureRestriction="
                    + gestureHookInstalled);
        } catch (Throwable t) {
            Log.e(TAG, "could not install SystemUI gesture bypass", t);
        }
    }

    private static boolean installGestureRestrictionHook(ClassLoader targetClassLoader) {
        if (gestureRestrictionHookInstalled) {
            return true;
        }
        try {
            Class<?> controllerClass = Class.forName(
                    PHONE_STATE_MONITOR_CONTROLLER_CLASS, false, targetClassLoader);
            Method defaultHomeChanged = controllerClass.getDeclaredMethod(
                    "onDefaultHomeChanged", ComponentName.class);
            defaultHomeChanged.setAccessible(true);
            XposedBridge.hookMethod(defaultHomeChanged, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // The original method only forces force_fsg_nav_bar=false for
                    // HOME packages outside Xiaomi's hard-coded allowlist.
                    param.setResult(null);
                }
            });
            HookBridgeCompat.deoptimizeMethod(defaultHomeChanged);
            gestureRestrictionHookInstalled = true;
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "target SystemUI gesture restriction is not present");
            return false;
        }
    }

    private static boolean isSystemUiLoadedApk(Object loadedApk) {
        if (loadedApk == null) {
            return false;
        }
        try {
            Method getPackageName = loadedApk.getClass().getDeclaredMethod("getPackageName");
            getPackageName.setAccessible(true);
            return SYSTEM_UI_PACKAGE.equals(getPackageName.invoke(loadedApk));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Field packageName = loadedApk.getClass().getDeclaredField("mPackageName");
                packageName.setAccessible(true);
                return SYSTEM_UI_PACKAGE.equals(packageName.get(loadedApk));
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.w(TAG, "could not identify LoadedApk package", e);
                return false;
            }
        }
    }

    private static boolean isHyperOs() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);
            Object value = get.invoke(null, "ro.mi.os.version.name", "");
            return value instanceof String && ((String) value).startsWith("OS");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "could not read HyperOS version property", e);
            return false;
        }
    }
}
