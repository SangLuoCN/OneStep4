package com.sangluo.onestep.hook;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;

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
    private static final String MIUI_HOME_APPLICATION_CLASS =
            "com.miui.home.launcher.Application";
    private static final String OVERVIEW_COMPONENT_OBSERVER_CLASS =
            "com.miui.home.recents.OverviewComponentObserver";
    private static final String RECENTS_VIEW_CLASS =
            "com.miui.home.recents.views.RecentsView";
    private static final String TASK_VIEW_CLASS =
            "com.miui.home.recents.views.TaskView";
    private static final String WINDOW_ELEMENT_CLASS =
            "com.miui.home.recents.anim.WindowElement";

    private static boolean loaderHookInstalled;
    private static boolean targetHooksInstalled;
    private static boolean localOverviewHomeLogged;
    private static WeakReference<Activity> embeddedLauncher = new WeakReference<>(null);
    private static final ThreadLocal<Activity> localOverviewLauncher = new ThreadLocal<>();

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
            boolean localOverviewHomeHookInstalled = installLocalOverviewHomeHook(
                    targetClassLoader);
            boolean hostedTaskLaunchHookInstalled = installHostedTaskLaunchHook(
                    targetClassLoader);
            targetHooksInstalled = true;
            Log.i(TAG, "HyperOS third-party HOME gesture bypass installed; embeddedHome="
                    + embeddedHomeHookInstalled + ", localOverviewHome="
                    + localOverviewHomeHookInstalled + ", hostedTaskLaunch="
                    + hostedTaskLaunchHookInstalled);
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
                    Object launcher = readField(param.thisObject, "mLauncher");
                    String displayName = activityDisplayName(launcher);
                    if (HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive(displayName)) {
                        rememberEmbeddedLauncher(launcher, displayName);
                        param.setResult(false);
                        Log.i(TAG, "Keep MIUI HOME alive on " + displayName);
                    }
                }
            });

            HookBridgeCompat.deoptimizeMethod(needKillSelf);
            deoptimizeNoArgMethod(fallbackHomeCompat, "startFallbackHomeIfNeed");
            Class<?> baseLauncher = Class.forName(
                    BASE_LAUNCHER_CLASS, false, targetClassLoader);
            Method onResume = baseLauncher.getDeclaredMethod("onResume");
            onResume.setAccessible(true);
            XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    String displayName = activityDisplayName(param.thisObject);
                    if (HyperOsEmbeddedHomePolicy.shouldKeepLauncherAlive(displayName)) {
                        rememberEmbeddedLauncher(param.thisObject, displayName);
                    }
                }
            });
            deoptimizeNoArgMethod(baseLauncher, "onCreateBeforeSetCreatedFlag");
            HookBridgeCompat.deoptimizeMethod(onResume);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "MIUI Home embedded-launcher guard is not present");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "could not install MIUI Home embedded-launcher bypass", t);
            return false;
        }
    }

    private static boolean installLocalOverviewHomeHook(ClassLoader targetClassLoader) {
        try {
            Class<?> baseLauncherClass = Class.forName(
                    BASE_LAUNCHER_CLASS, false, targetClassLoader);
            Class<?> applicationClass = Class.forName(
                    MIUI_HOME_APPLICATION_CLASS, false, targetClassLoader);
            Method getLauncher = applicationClass.getDeclaredMethod("getLauncher");
            getLauncher.setAccessible(true);
            XposedBridge.hookMethod(getLauncher, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity launcher = localOverviewLauncher.get();
                    if (launcher != null) {
                        param.setResult(launcher);
                    }
                }
            });

            Class<?> observerClass = Class.forName(
                    OVERVIEW_COMPONENT_OBSERVER_CLASS, false, targetClassLoader);
            Method isHomeAndOverviewSame = observerClass.getDeclaredMethod(
                    "isHomeAndOverviewSame");
            isHomeAndOverviewSame.setAccessible(true);
            XposedBridge.hookMethod(isHomeAndOverviewSame, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!Boolean.FALSE.equals(param.getResult())) {
                        return;
                    }
                    try {
                        Activity launcher = localOverviewLauncher.get();
                        String displayName = activityDisplayName(launcher);
                        String defaultHomePackage = defaultHomePackage(launcher);
                        if (HyperOsEmbeddedHomePolicy.shouldUseEmbeddedOverviewHome(
                                displayName, defaultHomePackage)) {
                            param.setResult(true);
                            if (!localOverviewHomeLogged) {
                                localOverviewHomeLogged = true;
                                Log.i(TAG, "Treat MIUI overview as local HOME on "
                                        + displayName);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "local overview HOME check failed", t);
                    }
                }
            });

            Class<?> recentsViewClass = Class.forName(
                    RECENTS_VIEW_CLASS, false, targetClassLoader);
            Method startHome = recentsViewClass.getDeclaredMethod("startHome");
            Method exitOverviewState = recentsViewClass.getDeclaredMethod(
                    "exitOverviewState");
            startHome.setAccessible(true);
            exitOverviewState.setAccessible(true);
            XC_MethodHook localOverviewScope = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Activity launcher = activityFromViewContext(param.thisObject);
                    String displayName = activityDisplayName(launcher);
                    String defaultHomePackage = defaultHomePackage(launcher);
                    if (baseLauncherClass.isInstance(launcher)
                            && HyperOsEmbeddedHomePolicy.shouldUseEmbeddedOverviewHome(
                            displayName, defaultHomePackage)) {
                        localOverviewLauncher.set(launcher);
                        rememberEmbeddedLauncher(launcher, displayName);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    localOverviewLauncher.remove();
                }
            };
            XposedBridge.hookMethod(startHome, localOverviewScope);
            XposedBridge.hookMethod(exitOverviewState, localOverviewScope);

            Class<?> taskViewClass = Class.forName(
                    TASK_VIEW_CLASS, false, targetClassLoader);
            Method launchTask = taskViewClass.getDeclaredMethod(
                    "launchTask", boolean.class, boolean.class, boolean.class,
                    boolean.class, int.class, int.class);
            launchTask.setAccessible(true);
            XposedBridge.hookMethod(launchTask, localOverviewScope);

            HookBridgeCompat.deoptimizeMethod(getLauncher);
            HookBridgeCompat.deoptimizeMethod(isHomeAndOverviewSame);
            HookBridgeCompat.deoptimizeMethod(startHome);
            HookBridgeCompat.deoptimizeMethod(exitOverviewState);
            HookBridgeCompat.deoptimizeMethod(launchTask);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "MIUI Home local overview HOME hook is not present");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "could not install MIUI Home local overview HOME hook", t);
            return false;
        }
    }

    private static boolean installHostedTaskLaunchHook(ClassLoader targetClassLoader) {
        try {
            Class<?> taskViewClass = Class.forName(
                    TASK_VIEW_CLASS, false, targetClassLoader);
            Class<?> windowElementClass = Class.forName(
                    WINDOW_ELEMENT_CLASS, false, targetClassLoader);
            int hookedMethods = hookActivityOptionsFactories(
                    taskViewClass, true) + hookActivityOptionsFactories(
                    windowElementClass, false);
            if (hookedMethods == 0) {
                Log.i(TAG, "MIUI Home hosted task ActivityOptions hooks are not present");
                return false;
            }
            return true;
        } catch (ClassNotFoundException e) {
            Log.i(TAG, "MIUI Home hosted task launch classes are not present");
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "could not install MIUI Home hosted task launch hook", t);
            return false;
        }
    }

    private static int hookActivityOptionsFactories(Class<?> type, boolean viewIsReceiver) {
        int hookedMethods = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (!"getActivityOptions".equals(method.getName())
                    || !ActivityOptions.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!(param.getResult() instanceof ActivityOptions)) {
                        return;
                    }
                    Object source = viewIsReceiver
                            ? param.thisObject
                            : param.args.length == 0 ? null : param.args[0];
                    if (!(source instanceof View)) {
                        return;
                    }
                    Display display = ((View) source).getDisplay();
                    String displayName = display == null ? null : display.getName();
                    if (display == null || display.getDisplayId() == Display.DEFAULT_DISPLAY
                            || !HyperOsEmbeddedHomePolicy.shouldUseLocalOverviewHome(
                            displayName)) {
                        return;
                    }
                    ((ActivityOptions) param.getResult()).setLaunchDisplayId(
                            display.getDisplayId());
                    Log.i(TAG, "Keep recents task launch on " + displayName
                            + ", displayId=" + display.getDisplayId());
                }
            });
            HookBridgeCompat.deoptimizeMethod(method);
            hookedMethods++;
        }
        return hookedMethods;
    }

    private static Activity activityFromViewContext(Object view) {
        if (!(view instanceof View)) {
            return null;
        }
        Context context = ((View) view).getContext();
        while (context != null) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            if (!(context instanceof ContextWrapper)) {
                return null;
            }
            Context baseContext = ((ContextWrapper) context).getBaseContext();
            if (baseContext == context) {
                return null;
            }
            context = baseContext;
        }
        return null;
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

    private static void rememberEmbeddedLauncher(Object launcher, String displayName) {
        if (!(launcher instanceof Activity) || embeddedLauncher.get() == launcher) {
            return;
        }
        embeddedLauncher = new WeakReference<>((Activity) launcher);
        localOverviewHomeLogged = false;
        Log.i(TAG, "Track embedded MIUI HOME instance on " + displayName);
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
