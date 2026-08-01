package com.sangluo.onestep.hook;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Keeps HyperOS gesture navigation active while a third-party HOME remains selected. */
public final class HyperOsGestureNavigationBypassHook {
    private static final String TAG = "OneStepHyperOsGesture";
    private static final String MIUI_HOME_PACKAGE = "com.miui.home";
    private static final String LOADED_APK_CLASS = "android.app.LoadedApk";
    private static final String BUILD_CONFIG_UTILS_CLASS =
            "com.miui.home.common.utils.BuildConfigUtils";
    private static final String BASE_RECENTS_IMPL_CLASS =
            "com.miui.home.recents.BaseRecentsImpl";
    private static final String FALLBACK_HOME_COMPAT_CLASS =
            "com.miui.home.launcher.FallbackHomeCompat";
    private static final String BASE_LAUNCHER_CLASS =
            "com.miui.home.launcher.BaseLauncher";
    private static final String RECENTS_COMMON_STATE_CLASS =
            "com.miui.home.recents.anim.StateManager$CommonState";

    private static boolean loaderHookInstalled;
    private static boolean targetHooksInstalled;

    private HyperOsGestureNavigationBypassHook() {
    }

    /** Standalone Zygisk entry, installed before the target APK class loader exists. */
    public static synchronized void bootstrap(ClassLoader frameworkClassLoader) {
        if (loaderHookInstalled || targetHooksInstalled || frameworkClassLoader == null) {
            return;
        }
        if (!isHyperOs()) {
            Log.i(TAG, "non-HyperOS system; gesture bypass skipped");
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
                    if (targetHooksInstalled || !isMiuiHomeLoadedApk(param.thisObject)) {
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
            Log.i(TAG, "waiting for the MIUI Home class loader");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "LoadedApk class-loader hook is unavailable", e);
        } catch (Throwable t) {
            Log.e(TAG, "could not prepare HyperOS gesture bypass", t);
        }
    }

    /** LSPosed entry, called with MIUI Home's class loader. */
    public static synchronized void install(ClassLoader targetClassLoader) {
        if (targetHooksInstalled || targetClassLoader == null) {
            return;
        }
        if (!isHyperOs()) {
            Log.i(TAG, "non-HyperOS system; gesture bypass skipped");
            return;
        }
        try {
            HookBridgeCompat.disableHiddenApiRestrictions();
            Class<?> buildConfigUtils = Class.forName(
                    BUILD_CONFIG_UTILS_CLASS, false, targetClassLoader);
            Method homeCheck = buildConfigUtils.getDeclaredMethod(
                    "isUseMiuiHomeAsDefaultHome", Context.class);
            homeCheck.setAccessible(true);
            XposedBridge.hookMethod(homeCheck, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(true);
                }
            });

            Class<?> baseRecentsImpl = Class.forName(
                    BASE_RECENTS_IMPL_CLASS, false, targetClassLoader);
            Method gestureHostCheck = baseRecentsImpl.getDeclaredMethod(
                    "setIsUseMiuiHomeAsDefaultHome", boolean.class);
            gestureHostCheck.setAccessible(true);
            XposedBridge.hookMethod(gestureHostCheck, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && Boolean.FALSE.equals(param.args[0])) {
                        param.args[0] = true;
                    }
                }
            });

            HookBridgeCompat.deoptimizeMethod(homeCheck);
            HookBridgeCompat.deoptimizeMethod(gestureHostCheck);
            boolean embeddedHomeHookInstalled = installEmbeddedHomeHook(targetClassLoader);
            boolean recentsHomeExitHookInstalled = installRecentsHomeExitHook(
                    targetClassLoader);
            targetHooksInstalled = true;
            Log.i(TAG, "HyperOS third-party HOME gesture bypass installed; embeddedHome="
                    + embeddedHomeHookInstalled + ", recentsHomeExit="
                    + recentsHomeExitHookInstalled);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "target MIUI Home gesture restriction is not present");
        } catch (Throwable t) {
            Log.e(TAG, "could not install HyperOS gesture bypass", t);
        }
    }

    private static boolean installEmbeddedHomeHook(ClassLoader targetClassLoader) {
        try {
            Class<?> fallbackHomeCompat = Class.forName(
                    FALLBACK_HOME_COMPAT_CLASS, false, targetClassLoader);
            Method needKillSelf = fallbackHomeCompat.getDeclaredMethod("needKillSelf");
            needKillSelf.setAccessible(true);
            XposedBridge.hookMethod(needKillSelf, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String displayName = launcherDisplayName(param.thisObject);
                    if (HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive(displayName)) {
                        param.setResult(false);
                        Log.i(TAG, "Keep MIUI HOME alive on " + displayName);
                    }
                }
            });

            HookBridgeCompat.deoptimizeMethod(needKillSelf);
            deoptimizeNoArgMethod(fallbackHomeCompat, "startFallbackHomeIfNeed");
            Class<?> baseLauncher = Class.forName(
                    BASE_LAUNCHER_CLASS, false, targetClassLoader);
            deoptimizeNoArgMethod(baseLauncher, "onCreateBeforeSetCreatedFlag");
            deoptimizeNoArgMethod(baseLauncher, "onResume");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "MIUI Home embedded-launcher guard is not present");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "could not install MIUI Home embedded-launcher bypass", t);
            return false;
        }
    }

    private static boolean installRecentsHomeExitHook(ClassLoader targetClassLoader) {
        try {
            Class<?> baseLauncher = Class.forName(
                    BASE_LAUNCHER_CLASS, false, targetClassLoader);
            Method superStartActivity = baseLauncher.getDeclaredMethod(
                    "superStartActivity", Intent.class, Bundle.class, boolean.class);
            superStartActivity.setAccessible(true);
            Method onNewIntent = baseLauncher.getDeclaredMethod(
                    "onNewIntent", Intent.class);
            onNewIntent.setAccessible(true);
            XposedBridge.hookMethod(superStartActivity, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Intent intent = param.args.length == 0
                                || !(param.args[0] instanceof Intent)
                                ? null : (Intent) param.args[0];
                        String displayName = activityDisplayName(param.thisObject);
                        if (shouldSuppressRedundantHomeLaunch(intent, displayName)) {
                            param.setResult(null);
                            onNewIntent.invoke(param.thisObject, intent);
                            Log.i(TAG, "Deliver embedded HOME to the current MIUI Launcher on "
                                    + displayName);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "embedded recents HOME delivery failed", t);
                    }
                }
            });
            HookBridgeCompat.deoptimizeMethod(superStartActivity);
            HookBridgeCompat.deoptimizeMethod(onNewIntent);

            Class<?> commonState = Class.forName(
                    RECENTS_COMMON_STATE_CLASS, false, targetClassLoader);
            deoptimizeMethodsNamed(commonState, "startActivity");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "MIUI Home recents HOME exit hook is not present");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "could not install MIUI Home recents HOME exit hook", t);
            return false;
        }
    }

    private static boolean shouldSuppressRedundantHomeLaunch(Intent intent,
                                                             String displayName) {
        if (intent == null) {
            return false;
        }
        Set<String> categories = intent.getCategories();
        return HyperOsEmbeddedHomePolicy.shouldSuppressRedundantHomeLaunch(
                intent.getAction(),
                intent.hasCategory(Intent.CATEGORY_HOME),
                intent.hasCategory(Intent.CATEGORY_SECONDARY_HOME),
                categories == null ? 0 : categories.size(),
                displayName);
    }

    private static String launcherDisplayName(Object fallbackHomeCompat) {
        Object launcher = readField(fallbackHomeCompat, "mLauncher");
        return activityDisplayName(launcher);
    }

    private static String activityDisplayName(Object launcher) {
        if (!(launcher instanceof Activity)) {
            return null;
        }
        Activity activity = (Activity) launcher;
        try {
            Display display = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? activity.getDisplay()
                    : activity.getWindowManager().getDefaultDisplay();
            return display == null ? null : display.getName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object readField(Object target, String name) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException | RuntimeException e) {
                return null;
            }
        }
        return null;
    }

    private static void deoptimizeNoArgMethod(Class<?> type, String name) {
        try {
            Method method = type.getDeclaredMethod(name);
            method.setAccessible(true);
            HookBridgeCompat.deoptimizeMethod(method);
        } catch (NoSuchMethodException | LinkageError | RuntimeException e) {
            Log.w(TAG, "could not deoptimize " + type.getName() + "#" + name);
        }
    }

    private static void deoptimizeMethodsNamed(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (!name.equals(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                HookBridgeCompat.deoptimizeMethod(method);
            } catch (LinkageError | RuntimeException e) {
                Log.w(TAG, "could not deoptimize " + type.getName() + "#" + name, e);
            }
        }
    }

    private static boolean isMiuiHomeLoadedApk(Object loadedApk) {
        if (loadedApk == null) {
            return false;
        }
        try {
            Method getPackageName = loadedApk.getClass().getDeclaredMethod("getPackageName");
            getPackageName.setAccessible(true);
            return MIUI_HOME_PACKAGE.equals(getPackageName.invoke(loadedApk));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            try {
                Field packageName = loadedApk.getClass().getDeclaredField("mPackageName");
                packageName.setAccessible(true);
                return MIUI_HOME_PACKAGE.equals(packageName.get(loadedApk));
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
