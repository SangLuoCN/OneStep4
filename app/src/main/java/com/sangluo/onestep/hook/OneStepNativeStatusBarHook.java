package com.sangluo.onestep.hook;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Starts SystemUI's own status-bar controllers for OneStep virtual displays. */
public final class OneStepNativeStatusBarHook {
    private static final String TAG = "OneStepNativeStatusBar";
    private static final String DISPLAY_NAME_PREFIX = "OneStepSlot-";
    private static final int FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 6;
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String LOADED_APK_CLASS = "android.app.LoadedApk";
    private static final String STARTER_CLASS =
            "com.android.systemui.statusbar.core.MultiDisplayStatusBarStarter";
    private static final String COMMAND_QUEUE_CLASS =
            "com.android.systemui.statusbar.CommandQueue";
    private static final Set<Integer> STARTED_DISPLAYS = new HashSet<>();

    private static boolean loaderHookInstalled;
    private static boolean targetHookInstalled;
    private static Object starter;

    private OneStepNativeStatusBarHook() {
    }

    /** Standalone Zygisk entry, installed before SystemUI's APK class loader exists. */
    public static synchronized void bootstrap(ClassLoader frameworkClassLoader) {
        if (loaderHookInstalled || targetHookInstalled || frameworkClassLoader == null) {
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
            Log.i(TAG, "Waiting for the SystemUI class loader");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "LoadedApk class-loader hook is unavailable", e);
        } catch (Throwable t) {
            Log.e(TAG, "Could not prepare native multi-display status bar hook", t);
        }
    }

    /** LSPosed entry, called with SystemUI's APK class loader. */
    public static synchronized void install(ClassLoader classLoader) {
        if (targetHookInstalled || classLoader == null) {
            return;
        }
        try {
            HookBridgeCompat.disableHiddenApiRestrictions();
            Class<?> starterClass = Class.forName(STARTER_CLASS, false, classLoader);
            for (Constructor<?> constructor : starterClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                XposedBridge.hookMethod(constructor, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        starter = param.thisObject;
                    }
                });
            }
            Method start = starterClass.getDeclaredMethod("start");
            start.setAccessible(true);
            XposedBridge.hookMethod(start, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    starter = param.thisObject;
                    scheduleScan(param.thisObject, 0L);
                    scheduleScan(param.thisObject, 300L);
                }
            });

            Class<?> commandQueueClass = Class.forName(COMMAND_QUEUE_CLASS, false, classLoader);
            Method displayAdded = commandQueueClass.getDeclaredMethod(
                    "onDisplayAddSystemDecorations", int.class);
            displayAdded.setAccessible(true);
            XposedBridge.hookMethod(displayAdded, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1 && param.args[0] instanceof Integer) {
                        int displayId = (Integer) param.args[0];
                        if (displayId > 0) {
                            scheduleDisplay(param.args[0], 100L);
                        }
                    }
                }
            });
            HookBridgeCompat.deoptimizeMethod(start);
            HookBridgeCompat.deoptimizeMethod(displayAdded);
            targetHookInstalled = true;
            Log.i(TAG, "SystemUI native multi-display status bar hook installed");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "SystemUI native multi-display status bar is unavailable", e);
        } catch (Throwable t) {
            Log.e(TAG, "Could not install native multi-display status bar hook", t);
        }
    }

    private static void scheduleDisplay(Object displayId, long delayMillis) {
        final int id = (Integer) displayId;
        Handler handler = mainHandler();
        if (handler == null) {
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ensureDisplayStarted(starter, id);
            }
        }, delayMillis);
    }

    private static void scheduleScan(final Object targetStarter, long delayMillis) {
        Handler handler = mainHandler();
        if (handler == null) {
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanDisplays(targetStarter);
            }
        }, delayMillis);
    }

    private static Handler mainHandler() {
        Looper looper = Looper.getMainLooper();
        return looper == null ? null : new Handler(looper);
    }

    private static void scanDisplays(Object targetStarter) {
        try {
            Context context = currentApplication();
            if (context == null) {
                return;
            }
            DisplayManager displayManager =
                    (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (displayManager == null) {
                return;
            }
            for (Display display : displayManager.getDisplays()) {
                ensureDisplayStarted(targetStarter, display.getDisplayId());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not scan displays for native status bar", t);
        }
    }

    private static void ensureDisplayStarted(Object targetStarter, int displayId) {
        if (targetStarter == null || displayId <= 0 || !isOneStepDisplay(displayId)) {
            return;
        }
        synchronized (STARTED_DISPLAYS) {
            if (STARTED_DISPLAYS.contains(displayId)) {
                return;
            }
        }
        try {
            Object orchestratorStore = readField(
                    targetStarter, "multiDisplayStatusBarOrchestratorStore");
            Object initializerStore = readField(targetStarter, "statusBarInitializerStore");
            Object orchestrator = invokeForDisplay(orchestratorStore, displayId);
            Object initializer = invokeForDisplay(initializerStore, displayId);
            if (orchestrator == null || initializer == null) {
                Log.w(TAG, "Native status bar stores are not ready for display " + displayId);
                return;
            }
            invokeNoArg(orchestrator, "start");
            invokeNoArg(initializer, "start");
            Object lightBarStore = readField(targetStarter, "lightBarControllerStore");
            if (lightBarStore != null) {
                invokeForDisplay(lightBarStore, displayId);
            }
            synchronized (STARTED_DISPLAYS) {
                STARTED_DISPLAYS.add(displayId);
            }
            Log.i(TAG, "Started SystemUI native status bar for display " + displayId);
        } catch (Throwable t) {
            Log.e(TAG, "Could not start native status bar for display " + displayId, t);
        }
    }

    private static boolean isOneStepDisplay(int displayId) {
        try {
            Context context = currentApplication();
            if (context == null) {
                return false;
            }
            DisplayManager displayManager =
                    (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager == null ? null : displayManager.getDisplay(displayId);
            return display != null
                    && display.getName() != null
                    && display.getName().startsWith(DISPLAY_NAME_PREFIX)
                    && (display.getFlags() & FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS) != 0;
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not inspect display " + displayId, e);
            return false;
        }
    }

    private static Object invokeForDisplay(Object store, int displayId) throws Exception {
        if (store == null) {
            return null;
        }
        Method method = store.getClass().getMethod("forDisplay", int.class);
        method.setAccessible(true);
        return method.invoke(store, displayId);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invokeNoArg(Object target, String name) throws Exception {
        Method method = target.getClass().getMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    private static Context currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            return application instanceof Context ? (Context) application : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
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
                Log.w(TAG, "Could not identify LoadedApk package", e);
                return false;
            }
        }
    }
}
