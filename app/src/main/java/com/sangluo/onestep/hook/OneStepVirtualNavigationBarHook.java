package com.sangluo.onestep.hook;

import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Keeps virtual-display navigation insets while hiding its visual controls. */
public final class OneStepVirtualNavigationBarHook {
    private static final String TAG = "OneStepVirtualNavBar";
    private static final String WINDOW_MANAGER_GLOBAL_CLASS =
            "android.view.WindowManagerGlobal";
    private static final String VIEW_ROOT_IMPL_CLASS = "android.view.ViewRootImpl";
    private static final Set<Integer> LOGGED_DISPLAYS = new HashSet<>();
    private static final Set<Integer> LOGGED_BOTTOM_CAPTION_DISPLAYS = new HashSet<>();

    private static boolean installed;

    private OneStepVirtualNavigationBarHook() {
    }

    public static synchronized boolean install(ClassLoader ignoredClassLoader) {
        if (installed) {
            return true;
        }
        try {
            Class<?> windowManagerGlobalClass = Class.forName(WINDOW_MANAGER_GLOBAL_CLASS);
            int windowManagerHooks = 0;
            for (Method method : windowManagerGlobalClass.getDeclaredMethods()) {
                String name = method.getName();
                if (!"addView".equals(name) && !"updateViewLayout".equals(name)) {
                    continue;
                }
                method.setAccessible(true);
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        suppressVirtualNavigationWindow(param.args);
                    }
                });
                HookBridgeCompat.deoptimizeMethod(method);
                windowManagerHooks++;
            }
            int viewRootHooks = installViewRootHooks();
            installed = windowManagerHooks > 0 || viewRootHooks > 0;
            Log.i(TAG, "Virtual navigation hooks installed: windowManager="
                    + windowManagerHooks + ", viewRoot=" + viewRootHooks);
            return installed;
        } catch (Throwable t) {
            Log.e(TAG, "Could not install virtual navigation window hooks", t);
            return false;
        }
    }

    private static int installViewRootHooks() throws ClassNotFoundException {
        Class<?> viewRootClass = Class.forName(VIEW_ROOT_IMPL_CLASS);
        int hookedMethods = 0;
        for (Method method : viewRootClass.getDeclaredMethods()) {
            String name = method.getName();
            if (!"setView".equals(name) && !"setLayoutParams".equals(name)) {
                continue;
            }
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    suppressViewRootBottomCaption(param.thisObject, param.args);
                }
            });
            HookBridgeCompat.deoptimizeMethod(method);
            hookedMethods++;
        }
        return hookedMethods;
    }

    private static void suppressVirtualNavigationWindow(Object[] args) {
        try {
            View view = findArgument(args, View.class);
            WindowManager.LayoutParams attributes = findArgument(
                    args, WindowManager.LayoutParams.class);
            Display display = findArgument(args, Display.class);
            if (display == null && view != null) {
                display = view.getDisplay();
            }
            String displayName = display == null ? null : display.getName();
            if (attributes == null) {
                return;
            }
            boolean hideNavigationWindow = VirtualNavigationBarPolicy.shouldHide(
                    displayName, attributes.type);
            boolean hideBottomCaption = VirtualNavigationBarPolicy.shouldHideBottomCaption(
                    displayName, attributes.getTitle());
            if (!hideNavigationWindow && !hideBottomCaption) {
                return;
            }

            suppressWindow(attributes, view);
            int displayId = display == null ? -1 : display.getDisplayId();
            Set<Integer> loggedDisplays = hideBottomCaption
                    ? LOGGED_BOTTOM_CAPTION_DISPLAYS : LOGGED_DISPLAYS;
            synchronized (loggedDisplays) {
                if (loggedDisplays.add(displayId)) {
                    String hiddenElement = hideBottomCaption
                            ? "MIUI gesture handle" : "navigation window";
                    Log.i(TAG, "Hidden virtual " + hiddenElement
                            + " while preserving nav height: display=" + displayId);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not suppress virtual navigation window", t);
        }
    }

    private static void suppressViewRootBottomCaption(Object viewRoot, Object[] args) {
        try {
            WindowManager.LayoutParams attributes = findArgument(
                    args, WindowManager.LayoutParams.class);
            if (attributes == null) {
                return;
            }
            Display display = readDisplay(viewRoot);
            View view = findArgument(args, View.class);
            if (display == null && view != null) {
                display = view.getDisplay();
            }
            String displayName = display == null ? null : display.getName();
            if (!VirtualNavigationBarPolicy.shouldHideBottomCaption(
                    displayName, attributes.getTitle())) {
                return;
            }

            suppressWindow(attributes, view);
            int displayId = display == null ? -1 : display.getDisplayId();
            synchronized (LOGGED_BOTTOM_CAPTION_DISPLAYS) {
                if (LOGGED_BOTTOM_CAPTION_DISPLAYS.add(displayId)) {
                    Log.i(TAG, "Hidden windowless MIUI gesture handle at ViewRootImpl: display="
                            + displayId + ", title=" + attributes.getTitle());
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not suppress windowless MIUI gesture handle", t);
        }
    }

    private static Display readDisplay(Object viewRoot) throws ReflectiveOperationException {
        if (viewRoot == null) {
            return null;
        }
        Field field = viewRoot.getClass().getDeclaredField("mDisplay");
        field.setAccessible(true);
        Object value = field.get(viewRoot);
        return value instanceof Display ? (Display) value : null;
    }

    private static void suppressWindow(WindowManager.LayoutParams attributes, View view) {
        attributes.alpha = 0f;
        attributes.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        attributes.flags &= ~WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        if (view != null) {
            view.setAlpha(0f);
        }
    }

    private static <T> T findArgument(Object[] args, Class<T> type) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (type.isInstance(arg)) {
                return type.cast(arg);
            }
        }
        return null;
    }
}
