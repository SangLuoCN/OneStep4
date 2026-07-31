package com.sangluo.onestep.hook;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Backports Android 14's root package-name exemption for virtual displays. */
public final class OneStepRootVirtualDisplayCompatHook {
    private static final String TAG = "OneStepRootDisplayCompat";
    private static final String DISPLAY_MANAGER_SERVICE_CLASS =
            RootVirtualDisplayCompatPolicy.DISPLAY_MANAGER_SERVICE_CLASS;
    private static final String DISPLAY_MANAGER_BINDER_SERVICE_CLASS =
            RootVirtualDisplayCompatPolicy.DISPLAY_MANAGER_BINDER_SERVICE_CLASS;
    private static final String ACTIVE_MARKER =
            "/data/system/onestep-root-display-compat-hook-active";
    private static final long CLASS_WAIT_TIMEOUT_MS = 120_000L;
    private static final long CLASS_WAIT_INTERVAL_MS = 100L;

    private static boolean installationStarted;
    private static boolean installed;

    private OneStepRootVirtualDisplayCompatHook() {
    }

    public static synchronized void bootstrap(ClassLoader systemServerClassLoader) {
        if (installed || installationStarted) {
            return;
        }
        clearActiveMarker();
        if (!RootVirtualDisplayCompatPolicy.needsCompatHook(Build.VERSION.SDK_INT)) {
            Log.i(TAG, "platform already exempts root package-name validation");
            return;
        }
        if (systemServerClassLoader == null) {
            Log.e(TAG, "system_server class loader unavailable");
            return;
        }
        installationStarted = true;
        Thread installer = new Thread(
                () -> awaitAndInstall(systemServerClassLoader, Build.VERSION.SDK_INT),
                "OneStepRootDisplayCompatInstaller");
        installer.setContextClassLoader(systemServerClassLoader);
        installer.setDaemon(true);
        installer.start();
    }

    private static void awaitAndInstall(ClassLoader fallbackClassLoader, int sdkInt) {
        long deadline = android.os.SystemClock.uptimeMillis() + CLASS_WAIT_TIMEOUT_MS;
        ReflectiveOperationException lastFailure = null;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                install(fallbackClassLoader, sdkInt);
                synchronized (OneStepRootVirtualDisplayCompatHook.class) {
                    installed = true;
                    installationStarted = false;
                }
                markActive();
                return;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                lastFailure = e;
                android.os.SystemClock.sleep(CLASS_WAIT_INTERVAL_MS);
            } catch (Throwable throwable) {
                Log.e(TAG, "root virtual-display compatibility hook installation failed",
                        throwable);
                synchronized (OneStepRootVirtualDisplayCompatHook.class) {
                    installationStarted = false;
                }
                return;
            }
        }
        Log.e(TAG, "timed out waiting for DisplayManagerService", lastFailure);
        synchronized (OneStepRootVirtualDisplayCompatHook.class) {
            installationStarted = false;
        }
    }

    private static void install(ClassLoader fallbackClassLoader, int sdkInt)
            throws ClassNotFoundException, NoSuchMethodException {
        Method validationMethod = resolveValidationMethod(fallbackClassLoader, sdkInt);
        HookBridgeCompat.disableHiddenApiRestrictions();
        Class<?> ownerClass = validationMethod.getDeclaringClass();
        validationMethod.setAccessible(true);
        XposedBridge.hookMethod(validationMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length > 0 && param.args[0] instanceof Integer
                        && RootVirtualDisplayCompatPolicy.shouldBypassPackageValidation(
                                Build.VERSION.SDK_INT, (Integer) param.args[0])) {
                    param.setResult(true);
                }
            }
        });
        HookBridgeCompat.deoptimizeMethod(validationMethod);
        deoptimizeVirtualDisplayCallers(ownerClass);
        Log.i(TAG, "installed on " + ownerClass.getName() + "#validatePackageName");
    }

    private static Method resolveValidationMethod(ClassLoader fallbackClassLoader, int sdkInt)
            throws ClassNotFoundException, NoSuchMethodException {
        String preferred = RootVirtualDisplayCompatPolicy.validationOwnerClassName(sdkInt);
        String fallback = DISPLAY_MANAGER_SERVICE_CLASS.equals(preferred)
                ? DISPLAY_MANAGER_BINDER_SERVICE_CLASS : DISPLAY_MANAGER_SERVICE_CLASS;
        ReflectiveOperationException lastFailure = null;
        for (ClassLoader classLoader : activeClassLoaders(fallbackClassLoader)) {
            try {
                return findValidationMethod(classLoader, preferred);
            } catch (ReflectiveOperationException e) {
                lastFailure = e;
            }
            try {
                return findValidationMethod(classLoader, fallback);
            } catch (ReflectiveOperationException e) {
                lastFailure = e;
            }
        }
        if (lastFailure instanceof NoSuchMethodException) {
            throw (NoSuchMethodException) lastFailure;
        }
        throw new ClassNotFoundException(
                "DisplayManagerService validation method unavailable", lastFailure);
    }

    private static Method findValidationMethod(ClassLoader classLoader, String className)
            throws ReflectiveOperationException {
        Class<?> ownerClass = Class.forName(className, false, classLoader);
        return ownerClass.getDeclaredMethod("validatePackageName", int.class, String.class);
    }

    private static Set<ClassLoader> activeClassLoaders(ClassLoader fallback) {
        Set<ClassLoader> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        addClassLoaderChain(candidates, fallback);
        addClassLoaderChain(candidates, Thread.currentThread().getContextClassLoader());
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                addClassLoaderChain(candidates, thread.getContextClassLoader());
            }
        } catch (RuntimeException ignored) {
        }
        return candidates;
    }

    private static void addClassLoaderChain(Set<ClassLoader> candidates, ClassLoader loader) {
        ClassLoader current = loader;
        while (current != null && candidates.add(current)) {
            current = current.getParent();
        }
    }

    private static void deoptimizeVirtualDisplayCallers(Class<?> ownerClass) {
        for (Method method : ownerClass.getDeclaredMethods()) {
            String name = method.getName();
            if ("createVirtualDisplay".equals(name)
                    || "createVirtualDisplayInternal".equals(name)) {
                HookBridgeCompat.deoptimizeMethod(method);
            }
        }
    }

    private static void clearActiveMarker() {
        try {
            File marker = new File(ACTIVE_MARKER);
            if (marker.exists() && !marker.delete()) {
                Log.w(TAG, "could not clear stale active marker");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "could not clear active marker", e);
        }
    }

    private static void markActive() {
        try {
            File marker = new File(ACTIVE_MARKER);
            if (!marker.exists() && !marker.createNewFile()) {
                Log.w(TAG, "could not create active marker");
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "could not mark compatibility hook active", e);
        }
    }
}
