package com.sangluo.onestep.hook;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
    private static final String MIUI_DECORATION_HOME_BOTTOM_CLASS =
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor."
                    + "decoration.MiuiDecorationHomeBottom";
    private static final String NAVIGATION_BAR_CONTROLLER_CLASS =
            "com.android.systemui.navigationbar.NavigationBarControllerImpl";

    private static boolean loaderHookInstalled;
    private static boolean targetHookInstalled;
    private static boolean gestureRestrictionHookInstalled;
    private static boolean homeBottomCaptionHookInstalled;
    private static boolean homeBottomCaptionSuppressedLogged;
    private static boolean defaultDisplayNavigationHookInstalled;
    private static boolean defaultDisplayNavigationSuppressedLogged;

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
            boolean captionHookInstalled = installDefaultDisplayHomeBottomCaptionHook(
                    targetClassLoader);
            boolean navigationHookInstalled = installDefaultDisplayNavigationHook(
                    targetClassLoader);
            boolean gestureHookInstalled = installGestureRestrictionHook(targetClassLoader);
            targetHookInstalled = captionHookInstalled || navigationHookInstalled
                    || gestureHookInstalled;
            Log.i(TAG, "HyperOS SystemUI hooks installed; homeBottomCaption="
                    + captionHookInstalled + ", defaultDisplayNavigation="
                    + navigationHookInstalled + ", gestureRestriction="
                    + gestureHookInstalled);
        } catch (Throwable t) {
            Log.e(TAG, "could not install SystemUI gesture bypass", t);
        }
    }

    private static boolean installDefaultDisplayNavigationHook(
            ClassLoader targetClassLoader) {
        if (defaultDisplayNavigationHookInstalled) {
            return true;
        }
        try {
            Class<?> controllerClass = Class.forName(
                    NAVIGATION_BAR_CONTROLLER_CLASS, false, targetClassLoader);
            Method shouldCreate = controllerClass.getDeclaredMethod(
                    "shouldCreateNavBarAndTaskBar", int.class);
            shouldCreate.setAccessible(true);
            XposedBridge.hookMethod(shouldCreate, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length != 1 || !(param.args[0] instanceof Integer)) {
                        return;
                    }
                    try {
                        int displayId = (Integer) param.args[0];
                        Context context = context(param.thisObject);
                        if (DefaultDisplayHomeBottomCaptionPolicy.shouldSuppress(
                                displayId, defaultHomePackage(context))) {
                            param.setResult(false);
                            if (!defaultDisplayNavigationSuppressedLogged) {
                                defaultDisplayNavigationSuppressedLogged = true;
                                Log.i(TAG, "Remove default-display navigation bar and gesture host");
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "could not evaluate default-display navigation", t);
                    }
                }
            });
            HookBridgeCompat.deoptimizeMethod(shouldCreate);
            defaultDisplayNavigationHookInstalled = true;
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            Log.w(TAG, "default-display navigation hook is unavailable", e);
            return false;
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

    private static boolean installDefaultDisplayHomeBottomCaptionHook(
            ClassLoader targetClassLoader) {
        if (homeBottomCaptionHookInstalled) {
            return true;
        }
        try {
            Class<?> homeBottomClass = Class.forName(
                    MIUI_DECORATION_HOME_BOTTOM_CLASS, false, targetClassLoader);
            Method needCaption = homeBottomClass.getDeclaredMethod("needCaption");
            needCaption.setAccessible(true);
            XposedBridge.hookMethod(needCaption, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        ActivityManager.RunningTaskInfo taskInfo = taskInfo(param.thisObject);
                        Context context = context(param.thisObject);
                        if (taskInfo != null && DefaultDisplayHomeBottomCaptionPolicy
                                .shouldSuppress(displayId(taskInfo),
                                        defaultHomePackage(context))) {
                            param.setResult(false);
                            if (!homeBottomCaptionSuppressedLogged) {
                                homeBottomCaptionSuppressedLogged = true;
                                Log.i(TAG, "Suppress default-display MIUI home bottom caption");
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "could not evaluate MIUI home bottom caption", t);
                    }
                }
            });
            HookBridgeCompat.deoptimizeMethod(needCaption);
            homeBottomCaptionHookInstalled = true;
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            Log.w(TAG, "MIUI home bottom-caption hook is unavailable", e);
            return false;
        }
    }

    private static ActivityManager.RunningTaskInfo taskInfo(Object decoration)
            throws ReflectiveOperationException {
        Object value = readField(decoration, "mRunningTaskInfo");
        return value instanceof ActivityManager.RunningTaskInfo
                ? (ActivityManager.RunningTaskInfo) value : null;
    }

    private static Context context(Object decoration) throws ReflectiveOperationException {
        Object value = readField(decoration, "mContext");
        return value instanceof Context ? (Context) value : null;
    }

    private static int displayId(ActivityManager.RunningTaskInfo taskInfo)
            throws ReflectiveOperationException {
        Object value = readField(taskInfo, "displayId");
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static String defaultHomePackage(Context context) {
        if (context == null) {
            return null;
        }
        try {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME);
            ComponentName component = homeIntent.resolveActivity(
                    context.getPackageManager());
            return component == null ? null : component.getPackageName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object readField(Object target, String name)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
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
