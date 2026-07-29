package com.sangluo.onestep.hook;

import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Installs the system_server-only secure-window policy used by OneStep displays. */
public final class OneStepSecureWindowHook {
    private static final String TAG = "OneStepSecureHook";
    private static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";
    private static final String DISPLAY_NAME_PREFIX = "OneStepSlot-";
    private static final long CLASS_WAIT_TIMEOUT_MS = 120_000L;
    private static final long CLASS_WAIT_INTERVAL_MS = 100L;
    private static final Object STATE_LOCK = new Object();
    private static final Set<Object> protectedHostSurfaces = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private static final Set<Object> decorSuppressedDisplays = Collections.newSetFromMap(
            new IdentityHashMap<>());

    private static volatile boolean installed;
    private static boolean hostProtectionActive;

    private OneStepSecureWindowHook() {
    }

    public static void bootstrap(ClassLoader systemServerClassLoader) {
        if (systemServerClassLoader == null) {
            Log.e(TAG, "system_server class loader unavailable");
            return;
        }
        Thread installer = new Thread(
                () -> awaitAndInstall(systemServerClassLoader),
                "OneStepSecureHookInstaller");
        installer.setContextClassLoader(systemServerClassLoader);
        installer.setDaemon(true);
        installer.start();
    }

    private static void awaitAndInstall(ClassLoader classLoader) {
        long deadline = android.os.SystemClock.uptimeMillis() + CLASS_WAIT_TIMEOUT_MS;
        Throwable lastFailure = null;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                ResolvedWindowManagerClasses resolved = resolveWindowManagerClasses(classLoader);
                installHooks(resolved.windowState, resolved.rootWindowContainer,
                        resolved.displayContent, resolved.classLoader);
                return;
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                lastFailure = e;
                android.os.SystemClock.sleep(CLASS_WAIT_INTERVAL_MS);
            } catch (Throwable t) {
                Log.e(TAG, "secure-window hook installation failed", t);
                return;
            }
        }
        Log.e(TAG, "timed out waiting for WindowManager classes", lastFailure);
    }

    private static ResolvedWindowManagerClasses resolveWindowManagerClasses(
            ClassLoader fallback) throws ClassNotFoundException {
        Set<ClassLoader> candidates = Collections.newSetFromMap(new IdentityHashMap<>());
        addClassLoaderChain(candidates, fallback);
        addClassLoaderChain(candidates, Thread.currentThread().getContextClassLoader());
        try {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                addClassLoaderChain(candidates, thread.getContextClassLoader());
            }
        } catch (Throwable ignored) {
        }

        ClassNotFoundException lastFailure = null;
        for (ClassLoader candidate : candidates) {
            try {
                Class<?> windowState = Class.forName(
                        "com.android.server.wm.WindowState", false, candidate);
                Class<?> rootWindowContainer = Class.forName(
                        "com.android.server.wm.RootWindowContainer", false, candidate);
                Class<?> displayContent = null;
                try {
                    // This hook is optional. Do not make an existing secure-window hook
                    // installation depend on a vendor-specific DisplayContent class shape.
                    displayContent = Class.forName(
                            "com.android.server.wm.DisplayContent", false, candidate);
                } catch (ClassNotFoundException ignored) {
                }
                Log.i(TAG, "resolved WindowManager classes with " + candidate);
                return new ResolvedWindowManagerClasses(
                        candidate, windowState, rootWindowContainer, displayContent);
            } catch (ClassNotFoundException e) {
                lastFailure = e;
            }
        }
        throw new ClassNotFoundException(
                "WindowManager classes unavailable in " + candidates.size()
                        + " active class loaders",
                lastFailure);
    }

    private static void addClassLoaderChain(Set<ClassLoader> candidates, ClassLoader loader) {
        ClassLoader current = loader;
        while (current != null && candidates.add(current)) {
            current = current.getParent();
        }
    }

    private static final class ResolvedWindowManagerClasses {
        final ClassLoader classLoader;
        final Class<?> windowState;
        final Class<?> rootWindowContainer;
        final Class<?> displayContent;

        ResolvedWindowManagerClasses(ClassLoader classLoader, Class<?> windowState,
                                     Class<?> rootWindowContainer, Class<?> displayContent) {
            this.classLoader = classLoader;
            this.windowState = windowState;
            this.rootWindowContainer = rootWindowContainer;
            this.displayContent = displayContent;
        }
    }

    private static synchronized void installHooks(Class<?> windowStateClass,
                                                  Class<?> rootWindowContainerClass,
                                                  Class<?> displayContentClass,
                                                  ClassLoader classLoader)
            throws NoSuchMethodException {
        if (installed) {
            return;
        }
        try {
            XposedBridge.disableHiddenApiRestrictions();
        } catch (Throwable t) {
            Log.w(TAG, "hidden API relaxation unavailable", t);
        }

        installSystemDecorHooks(displayContentClass);

        Method isSecureLocked = windowStateClass.getDeclaredMethod("isSecureLocked");
        isSecureLocked.setAccessible(true);
        XposedBridge.hookMethod(isSecureLocked, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (!Boolean.TRUE.equals(param.getResult())
                            || !hasFlagSecure(param.thisObject)
                            || !isOneStepDisplayWindow(param.thisObject)) {
                        return;
                    }
                    Object root = rootWindowContainerFor(param.thisObject);
                    if (root != null && setHostProtection(root, true)) {
                        // Protect the physical OneStep window before exposing this source layer.
                        param.setResult(false);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "isSecureLocked hook failed; preserving original policy", t);
                }
            }
        });

        Method placement = rootWindowContainerClass.getDeclaredMethod("performSurfacePlacement");
        placement.setAccessible(true);
        XposedBridge.hookMethod(placement, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                reconcileHostProtection(param.thisObject);
            }
        });

        deoptimizeCallers(classLoader,
                "com.android.server.wm.WindowState", "setInitialSurfaceControlProperties");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.WindowState", "setSecureLocked");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.WindowStateAnimator", "createSurfaceLocked");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.RootWindowContainer", "refreshSecureSurfaceState");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.WindowManagerService", "shouldShowSystemDecors");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.DisplayContent", "isHomeSupported");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.DisplayContent", "shouldWaitForSystemDecorWindowsOnBoot");
        deoptimizeCallers(classLoader,
                "com.android.server.wm.DisplayContent", "configureDisplayPolicy");

        installed = true;
        Log.i(TAG, "installed for displays named " + DISPLAY_NAME_PREFIX + "*");
    }

    private static void installSystemDecorHooks(Class<?> displayContentClass) {
        if (displayContentClass == null) {
            Log.w(TAG, "DisplayContent unavailable; system-decoration hook skipped");
            return;
        }
        final Method[] displayContentMethods;
        try {
            displayContentMethods = displayContentClass.getDeclaredMethods();
        } catch (Throwable t) {
            Log.e(TAG, "DisplayContent methods unavailable; system-decoration hook skipped", t);
            return;
        }
        int installedHooks = 0;
        for (String methodName : new String[]{
                "supportsSystemDecorations", "isSystemDecorationsSupported"}) {
            for (Method method : displayContentMethods) {
                if (!methodName.equals(method.getName())
                        || method.getParameterCount() != 0
                        || method.getReturnType() != boolean.class) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (!isOneStepDisplayContent(param.thisObject)) {
                                return;
                            }
                            param.setResult(false);
                            synchronized (STATE_LOCK) {
                                if (decorSuppressedDisplays.add(param.thisObject)) {
                                    Log.i(TAG, "suppressed system decorations for "
                                            + getDisplayName(param.thisObject));
                                }
                            }
                        }
                    });
                    installedHooks++;
                } catch (Throwable t) {
                    Log.e(TAG, "could not hook DisplayContent#" + methodName, t);
                }
            }
        }
        if (installedHooks == 0) {
            Log.w(TAG, "DisplayContent system-decoration method unavailable");
        } else {
            Log.i(TAG, "installed " + installedHooks + " system-decoration hook(s)");
        }
    }

    private static void deoptimizeCallers(ClassLoader classLoader, String className,
                                          String methodName) {
        try {
            Class<?> clazz = Class.forName(className, false, classLoader);
            for (Method method : clazz.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.deoptimizeMethod(method);
            }
        } catch (Throwable t) {
            Log.w(TAG, "could not deoptimize " + className + "#" + methodName, t);
        }
    }

    private static void reconcileHostProtection(Object rootWindowContainer) {
        try {
            final boolean[] secureVisible = {false};
            forAllWindows(rootWindowContainer, window -> {
                if (!secureVisible[0]
                        && hasFlagSecure(window)
                        && isOneStepDisplayWindow(window)
                        && isOnScreen(window)) {
                    secureVisible[0] = true;
                }
            });
            setHostProtection(rootWindowContainer, secureVisible[0]);
        } catch (Throwable t) {
            Log.e(TAG, "secure-window visibility reconciliation failed", t);
        }
    }

    private static boolean setHostProtection(Object rootWindowContainer, boolean active) {
        synchronized (STATE_LOCK) {
            Set<Object> currentHostSurfaces = Collections.newSetFromMap(new IdentityHashMap<>());
            if (active) {
                forAllWindows(rootWindowContainer, window -> {
                    if (isOneStepHostWindow(window) && isOnScreen(window)) {
                        try {
                            Object surface = invokeNoArgs(window, "getSurfaceControl");
                            if (isValidSurface(surface)) {
                                currentHostSurfaces.add(surface);
                            }
                        } catch (ReflectiveOperationException ignored) {
                        }
                    }
                });
            }

            if (active == hostProtectionActive
                    && protectedHostSurfaces.equals(currentHostSurfaces)) {
                return !active || hostProtectionActive;
            }

            Object transaction = null;
            boolean previousProtectionActive = hostProtectionActive;
            try {
                Class<?> surfaceClass = Class.forName("android.view.SurfaceControl");
                Class<?> transactionClass = Class.forName(
                        "android.view.SurfaceControl$Transaction");
                Method setSecure = transactionClass.getDeclaredMethod(
                        "setSecure", surfaceClass, boolean.class);
                Method apply = transactionClass.getDeclaredMethod("apply");
                setSecure.setAccessible(true);
                apply.setAccessible(true);
                transaction = transactionClass.getDeclaredConstructor().newInstance();

                for (Object surface : protectedHostSurfaces) {
                    if (!currentHostSurfaces.contains(surface) && isValidSurface(surface)) {
                        setSecure.invoke(transaction, surface, false);
                    }
                }
                for (Object surface : currentHostSurfaces) {
                    setSecure.invoke(transaction, surface, true);
                }
                apply.invoke(transaction);
                protectedHostSurfaces.clear();
                protectedHostSurfaces.addAll(currentHostSurfaces);
                hostProtectionActive = active && !currentHostSurfaces.isEmpty();
                if (hostProtectionActive != previousProtectionActive) {
                    Log.i(TAG, "host capture protection=" + hostProtectionActive);
                }
                return !active || hostProtectionActive;
            } catch (Throwable t) {
                Log.e(TAG, "could not update OneStep host capture protection", unwrap(t));
                return false;
            } finally {
                closeTransaction(transaction);
            }
        }
    }

    private static boolean hasFlagSecure(Object windowState) {
        try {
            Object attrs = readField(windowState, "mAttrs");
            return attrs instanceof WindowManager.LayoutParams
                    && ((((WindowManager.LayoutParams) attrs).flags
                    & WindowManager.LayoutParams.FLAG_SECURE) != 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOneStepDisplayWindow(Object windowState) {
        try {
            Object displayContent = invokeNoArgs(windowState, "getDisplayContent");
            return isOneStepDisplayContent(displayContent);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOneStepDisplayContent(Object displayContent) {
        String name = getDisplayName(displayContent);
        return name != null && name.startsWith(DISPLAY_NAME_PREFIX);
    }

    private static String getDisplayName(Object displayContent) {
        try {
            Object displayInfo = invokeNoArgs(displayContent, "getDisplayInfo");
            Object name = readField(displayInfo, "name");
            return name instanceof String ? (String) name : null;
        } catch (Throwable firstFailure) {
            try {
                Object displayInfo = readField(displayContent, "mDisplayInfo");
                Object name = readField(displayInfo, "name");
                return name instanceof String ? (String) name : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private static boolean isOneStepHostWindow(Object windowState) {
        try {
            Object packageName = invokeNoArgs(windowState, "getOwningPackage");
            Object displayId = invokeNoArgs(windowState, "getDisplayId");
            return ONE_STEP_PACKAGE.equals(packageName)
                    && displayId instanceof Integer
                    && ((Integer) displayId) == Display.DEFAULT_DISPLAY;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isOnScreen(Object windowState) {
        try {
            return Boolean.TRUE.equals(invokeNoArgs(windowState, "isOnScreen"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static Object rootWindowContainerFor(Object windowState) {
        try {
            Object windowManagerService = readField(windowState, "mWmService");
            return readField(windowManagerService, "mRoot");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void forAllWindows(Object rootWindowContainer, Consumer<Object> consumer) {
        if (rootWindowContainer == null) {
            return;
        }
        try {
            Method method = findMethod(rootWindowContainer.getClass(),
                    "forAllWindows", Consumer.class, boolean.class);
            method.invoke(rootWindowContainer, consumer, true);
        } catch (InvocationTargetException e) {
            throw propagate(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw propagate(e);
        }
    }

    private static Object invokeNoArgs(Object target, String methodName)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName);
        try {
            return method.invoke(target);
        } catch (InvocationTargetException e) {
            throw new ReflectiveOperationException(e.getCause());
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
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

    private static Object readField(Object target, String fieldName)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "#" + fieldName);
    }

    private static boolean isValidSurface(Object surface) {
        if (surface == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(invokeNoArgs(surface, "isValid"));
        } catch (Throwable t) {
            return false;
        }
    }

    private static void closeTransaction(Object transaction) {
        if (transaction == null) {
            return;
        }
        try {
            invokeNoArgs(transaction, "close");
        } catch (Throwable ignored) {
        }
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        }
        return new IllegalStateException(throwable);
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getCause() != null) {
            return ((InvocationTargetException) throwable).getCause();
        }
        return throwable;
    }
}
