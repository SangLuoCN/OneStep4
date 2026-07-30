package com.sangluo.onestep.hook;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Allows a marked primary HOME activity to create a separate OneStep-display instance. */
public final class OneStepPrimaryHomeHook {
    private static final String TAG = "OneStepPrimaryHome";
    private static final String ACTIVITY_STARTER_CLASS =
            "com.android.server.wm.ActivityStarter";
    private static final String ACTIVITY_START_INTERCEPTOR_CLASS =
            "com.android.server.wm.ActivityStartInterceptor";
    private static final String ACTIVITY_RECORD_CLASS =
            "com.android.server.wm.ActivityRecord";
    private static final String ACTIVE_MARKER =
            "/data/system/onestep-primary-home-hook-active";
    private static final long CLASS_WAIT_TIMEOUT_MS = 120_000L;
    private static final long CLASS_WAIT_INTERVAL_MS = 100L;

    private static volatile boolean installed;

    private OneStepPrimaryHomeHook() {
    }

    public static void bootstrap(ClassLoader systemServerClassLoader) {
        if (systemServerClassLoader == null) {
            Log.e(TAG, "system_server class loader unavailable");
            return;
        }
        Thread installer = new Thread(
                () -> awaitAndInstall(systemServerClassLoader),
                "OneStepPrimaryHomeInstaller");
        installer.setContextClassLoader(systemServerClassLoader);
        installer.setDaemon(true);
        installer.start();
    }

    private static void awaitAndInstall(ClassLoader fallback) {
        long deadline = android.os.SystemClock.uptimeMillis() + CLASS_WAIT_TIMEOUT_MS;
        Throwable lastFailure = null;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                Class<?> activityStarter = findSystemServerClass(
                        ACTIVITY_STARTER_CLASS, fallback);
                Class<?> activityStartInterceptor = findSystemServerClass(
                        ACTIVITY_START_INTERCEPTOR_CLASS, fallback);
                install(activityStarter, activityStartInterceptor);
                return;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                lastFailure = e;
                android.os.SystemClock.sleep(CLASS_WAIT_INTERVAL_MS);
            } catch (Throwable t) {
                Log.e(TAG, "primary HOME hook installation failed", t);
                return;
            }
        }
        Log.e(TAG, "timed out waiting for ActivityStarter", lastFailure);
    }

    private static synchronized void install(Class<?> activityStarterClass,
                                             Class<?> activityStartInterceptorClass)
            throws NoSuchMethodException {
        if (installed) {
            return;
        }
        HookBridgeCompat.disableHiddenApiRestrictions();
        Method target = null;
        for (Method method : activityStarterClass.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if ("setInitialState".equals(method.getName())
                    && parameterTypes.length > 0
                    && ACTIVITY_RECORD_CLASS.equals(parameterTypes[0].getName())) {
                target = method;
                break;
            }
        }
        if (target == null) {
            throw new NoSuchMethodException(
                    ACTIVITY_STARTER_CLASS + "#setInitialState(ActivityRecord, ...)");
        }
        target.setAccessible(true);
        XposedBridge.hookMethod(target, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (param.args.length > 0 && param.args[0] != null) {
                        adaptMarkedHomeLaunch(param.thisObject, param.args[0]);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "marked HOME launch adaptation failed", t);
                }
            }
        });
        Method interceptHome = activityStartInterceptorClass.getDeclaredMethod(
                "interceptHomeIfNeeded");
        interceptHome.setAccessible(true);
        XposedBridge.hookMethod(interceptHome, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (shouldPreservePrimaryHome(param.thisObject)) {
                        param.setResult(false);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "primary HOME interception decision failed", t);
                }
            }
        });
        HookBridgeCompat.deoptimizeMethod(target);
        deoptimizeSetInitialStateCallers(activityStarterClass);
        HookBridgeCompat.deoptimizeMethod(interceptHome);
        deoptimizeHomeInterceptorCaller(activityStartInterceptorClass);
        installed = true;
        markActive();
        Log.i(TAG, "installed without changing existing system_server hook policies");
    }

    private static boolean shouldPreservePrimaryHome(Object interceptor)
            throws ReflectiveOperationException {
        Object rawIntent = readField(interceptor, "mIntent");
        if (!(rawIntent instanceof Intent)) {
            return false;
        }
        Intent intent = (Intent) rawIntent;
        if (!intent.getBooleanExtra(
                OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME, false)) {
            return false;
        }
        ComponentName component = intent.getComponent();
        String targetPackage = component == null ? null : component.getPackageName();
        String callingPackage = stringField(interceptor, "mCallingPackage");
        Object taskDisplayArea = readField(interceptor, "mPresumableLaunchDisplayArea");
        int displayId = taskDisplayArea == null
                ? -1 : integerValue(invokeNoArgs(taskDisplayArea, "getDisplayId"), -1);
        String displayName = displayName(displayId);
        boolean preserve = OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                intent.getAction(),
                intent.hasCategory(Intent.CATEGORY_HOME),
                callingPackage,
                targetPackage,
                displayName);
        if (preserve) {
            Log.i(TAG, "preserving marked primary HOME before secondary rewrite: component="
                    + component + ", display=" + displayId + "/" + displayName);
        } else {
            Log.w(TAG, "ignored marked HOME before secondary rewrite: caller="
                    + callingPackage + ", target=" + targetPackage
                    + ", display=" + displayId + "/" + displayName);
        }
        return preserve;
    }

    private static void deoptimizeSetInitialStateCallers(Class<?> activityStarterClass) {
        for (Method method : activityStarterClass.getDeclaredMethods()) {
            if (!"startActivityInner".equals(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                HookBridgeCompat.deoptimizeMethod(method);
            } catch (Throwable t) {
                Log.w(TAG, "could not deoptimize ActivityStarter#startActivityInner", t);
            }
        }
    }

    private static void deoptimizeHomeInterceptorCaller(Class<?> interceptorClass) {
        for (Method method : interceptorClass.getDeclaredMethods()) {
            if (!"intercept".equals(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                HookBridgeCompat.deoptimizeMethod(method);
            } catch (Throwable t) {
                Log.w(TAG, "could not deoptimize ActivityStartInterceptor#intercept", t);
            }
        }
    }

    private static void markActive() {
        try {
            File marker = new File(ACTIVE_MARKER);
            if (!marker.exists() && !marker.createNewFile()) {
                Log.w(TAG, "could not create primary HOME hook marker");
                return;
            }
            if (!marker.setLastModified(System.currentTimeMillis())) {
                Log.w(TAG, "could not update primary HOME hook marker");
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not mark primary HOME hook active", e);
        }
    }

    private static void adaptMarkedHomeLaunch(Object activityStarter,
                                              Object activityRecord)
            throws ReflectiveOperationException {
        Object rawIntent = readField(activityRecord, "intent");
        if (!(rawIntent instanceof Intent)) {
            return;
        }
        Intent intent = (Intent) rawIntent;
        boolean requested = intent.getBooleanExtra(
                OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME, false);
        if (!requested) {
            return;
        }
        intent.removeExtra(OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME);

        String launchedFromPackage = stringField(activityRecord, "launchedFromPackage");
        ComponentName component = intent.getComponent();
        String targetPackage = component == null ? null : component.getPackageName();
        int displayId = preferredDisplayId(activityStarter);
        String displayName = displayName(displayId);
        if (!OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                intent.getAction(),
                intent.hasCategory(Intent.CATEGORY_HOME),
                launchedFromPackage,
                targetPackage,
                displayName)) {
            Log.w(TAG, "ignored invalid marked HOME launch: caller="
                    + launchedFromPackage + ", target=" + targetPackage
                    + ", display=" + displayId + "/" + displayName);
            return;
        }

        Object rawInfo = readField(activityRecord, "info");
        if (!(rawInfo instanceof ActivityInfo)) {
            return;
        }
        ActivityInfo activityInfo = (ActivityInfo) rawInfo;
        int originalLaunchMode = activityInfo.launchMode;
        activityInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
        writeIntField(activityRecord, "launchMode", ActivityInfo.LAUNCH_MULTIPLE);
        writeIntField(activityStarter, "mLaunchMode", ActivityInfo.LAUNCH_MULTIPLE);
        Log.i(TAG, "primary HOME workspace enabled: component=" + component
                + ", display=" + displayId + ", originalLaunchMode="
                + originalLaunchMode);
    }

    private static int preferredDisplayId(Object activityStarter)
            throws ReflectiveOperationException {
        Object taskDisplayArea = readField(activityStarter, "mPreferredTaskDisplayArea");
        if (taskDisplayArea == null) {
            return -1;
        }
        Object value = invokeNoArgs(taskDisplayArea, "getDisplayId");
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static String displayName(int displayId) {
        if (displayId < 0) {
            return null;
        }
        try {
            Class<?> managerClass = Class.forName(
                    "android.hardware.display.DisplayManagerGlobal");
            Object manager = invokeStaticNoArgs(managerClass, "getInstance");
            Method getDisplayInfo = findMethod(manager.getClass(),
                    "getDisplayInfo", int.class);
            Object displayInfo = getDisplayInfo.invoke(manager, displayId);
            Object name = readField(displayInfo, "name");
            return name instanceof String ? (String) name : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "cannot resolve display name for " + displayId + ": "
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    private static Class<?> findSystemServerClass(String className, ClassLoader fallback)
            throws ClassNotFoundException {
        Set<ClassLoader> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        addClassLoaderChain(candidates, fallback);
        addClassLoaderChain(candidates, Thread.currentThread().getContextClassLoader());
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                addClassLoaderChain(candidates, thread.getContextClassLoader());
            }
        } catch (RuntimeException ignored) {
        }
        ClassNotFoundException lastFailure = null;
        for (ClassLoader candidate : candidates) {
            try {
                return Class.forName(className, false, candidate);
            } catch (ClassNotFoundException e) {
                lastFailure = e;
            }
        }
        throw new ClassNotFoundException(className, lastFailure);
    }

    private static void addClassLoaderChain(Set<ClassLoader> candidates,
                                            ClassLoader loader) {
        ClassLoader current = loader;
        while (current != null && candidates.add(current)) {
            current = current.getParent();
        }
    }

    private static Object invokeNoArgs(Object target, String name)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name);
        return method.invoke(target);
    }

    private static Object invokeStaticNoArgs(Class<?> target, String name)
            throws ReflectiveOperationException {
        Method method = findMethod(target, name);
        return method.invoke(null);
    }

    private static Method findMethod(Class<?> type, String name,
                                     Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }

    private static Object readField(Object target, String name)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Field field = findField(target.getClass(), name);
        return field.get(target);
    }

    private static String stringField(Object target, String name)
            throws ReflectiveOperationException {
        Object value = readField(target, name);
        return value instanceof String ? (String) value : null;
    }

    private static int integerValue(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static void writeIntField(Object target, String name, int value)
            throws ReflectiveOperationException {
        findField(target.getClass(), name).setInt(target, value);
    }

    private static Field findField(Class<?> type, String name)
            throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }
}
