package com.sangluo.onestep.feature.embedding;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import com.sangluo.onestep.SecondaryHomeActivity;
import com.sangluo.onestep.model.LauncherApp;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

/** Compatibility backend for systems exposing the hidden ActivityView API. */
public final class HiddenActivityViewHost implements EmbeddedAppHost {
    @FunctionalInterface
    public interface AppCloser {
        void close(String packageName, Runnable onClosed);
    }

    private final PackageManager packageManager;
    private final Context context;
    private final BooleanSupplier startAllowed;
    private final AppCloser appCloser;
    private Object activityView;
    private View view;
    private Method startActivityMethod;
    private Method releaseMethod;
    private String launchWithoutAnimationPackage = "";
    private String unavailableReason = "系统未暴露 ActivityView/TaskView";

    public HiddenActivityViewHost(Context context, BooleanSupplier startAllowed,
                                  AppCloser appCloser) {
        this.context = context;
        packageManager = context.getPackageManager();
        this.startAllowed = startAllowed;
        this.appCloser = appCloser;
        initialize(context);
    }

    @Override
    public boolean isAvailable() {
        return activityView != null && view != null && startActivityMethod != null;
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public boolean start(LauncherApp app) {
        if (!isAvailable()) {
            return false;
        }
        if (!startAllowed.getAsBoolean()) {
            unavailableReason = "启动已取消";
            return false;
        }

        Intent launchIntent = packageManager.getLaunchIntentForPackage(app.packageName);
        if (launchIntent == null) {
            unavailableReason = "找不到启动入口";
            return false;
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (TextUtils.equals(launchWithoutAnimationPackage, app.packageName)) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            launchWithoutAnimationPackage = "";
        }
        Object[] args = makeStartArguments(startActivityMethod.getParameterTypes(), launchIntent);
        if (args == null) {
            unavailableReason = "ActivityView 启动参数不匹配";
            return false;
        }

        try {
            startActivityMethod.invoke(activityView, args);
            return true;
        } catch (IllegalAccessException e) {
            unavailableReason = "隐藏 API 被拦截";
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            unavailableReason = cause == null ? "嵌入启动失败" : cause.getClass().getSimpleName();
        } catch (RuntimeException e) {
            unavailableReason = e.getClass().getSimpleName();
        }
        return false;
    }

    @Override
    public void suppressNextLaunchAnimation(String packageName) {
        launchWithoutAnimationPackage = packageName == null ? "" : packageName;
    }

    @Override
    public void refreshContainerSize() {
        if (view != null) {
            view.requestLayout();
            view.invalidate();
        }
    }

    @Override
    public void sendBack() {
        sendKey(KeyEvent.KEYCODE_BACK);
    }

    @Override
    public void sendHome() {
        if (!isAvailable()) {
            return;
        }
        Intent intent = new Intent(context, SecondaryHomeActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .putExtra(SecondaryHomeActivity.EXTRA_BACKGROUND_ONLY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        Object[] args = makeStartArguments(startActivityMethod.getParameterTypes(), intent);
        if (args == null) {
            sendKey(KeyEvent.KEYCODE_HOME);
            return;
        }
        try {
            startActivityMethod.invoke(activityView, args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            sendKey(KeyEvent.KEYCODE_HOME);
        }
    }

    @Override
    public void invalidateTaskResolution() {
    }

    @Override
    public void closeApp(String packageName, Runnable onClosed) {
        appCloser.close(packageName, onClosed);
    }

    @Override
    public void release() {
        if (activityView == null || releaseMethod == null) {
            return;
        }
        try {
            releaseMethod.invoke(activityView);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
        }
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason;
    }

    private void sendKey(int keyCode) {
        if (view == null) {
            return;
        }
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        view.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void initialize(Context context) {
        try {
            Class<?> activityViewClass = Class.forName("android.app.ActivityView");
            activityView = createActivityView(activityViewClass, context);
            if (!(activityView instanceof View)) {
                activityView = null;
                unavailableReason = "ActivityView 不是 View";
                return;
            }
            view = (View) activityView;
            view.setBackgroundColor(Color.BLACK);
            startActivityMethod = findStartActivityMethod(activityViewClass);
            releaseMethod = findOptionalNoArgMethod(activityViewClass, "release");
            unavailableReason = "";
        } catch (ClassNotFoundException e) {
            unavailableReason = "系统没有 ActivityView";
        } catch (NoSuchMethodException e) {
            unavailableReason = "ActivityView 缺少 startActivity";
        } catch (ReflectiveOperationException e) {
            unavailableReason = "隐藏 API 不可访问";
        } catch (LinkageError | RuntimeException e) {
            unavailableReason = e.getClass().getSimpleName();
        }
    }

    private Object createActivityView(Class<?> activityViewClass, Context context)
            throws ReflectiveOperationException {
        Constructor<?> constructor = findConstructor(activityViewClass, Context.class);
        if (constructor != null) {
            constructor.setAccessible(true);
            return constructor.newInstance(context);
        }
        constructor = findConstructor(activityViewClass, Context.class, AttributeSet.class);
        if (constructor != null) {
            constructor.setAccessible(true);
            return constructor.newInstance(context, null);
        }
        constructor = findConstructor(
                activityViewClass, Context.class, AttributeSet.class, int.class);
        if (constructor != null) {
            constructor.setAccessible(true);
            return constructor.newInstance(context, null, 0);
        }
        throw new NoSuchMethodException("ActivityView constructor");
    }

    private Constructor<?> findConstructor(Class<?> targetClass, Class<?>... parameterTypes) {
        try {
            return targetClass.getDeclaredConstructor(parameterTypes);
        } catch (NoSuchMethodException ignored) {
            try {
                return targetClass.getConstructor(parameterTypes);
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private Method findStartActivityMethod(Class<?> activityViewClass)
            throws NoSuchMethodException {
        try {
            Method method = activityViewClass.getMethod("startActivity", Intent.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = findCompatibleStartMethod(activityViewClass.getMethods());
            if (method == null) {
                method = findCompatibleStartMethod(activityViewClass.getDeclaredMethods());
            }
            if (method == null) {
                throw new NoSuchMethodException("startActivity");
            }
            method.setAccessible(true);
            return method;
        }
    }

    private Method findCompatibleStartMethod(Method[] methods) {
        for (Method method : methods) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (!TextUtils.equals(method.getName(), "startActivity")
                    || parameterTypes.length == 0
                    || !Intent.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }
            if (makeStartArguments(parameterTypes, new Intent()) != null) {
                return method;
            }
        }
        return null;
    }

    private Method findOptionalNoArgMethod(Class<?> targetClass, String name) {
        try {
            Method method = targetClass.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = targetClass.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private Object[] makeStartArguments(Class<?>[] parameterTypes, Intent launchIntent) {
        if (parameterTypes.length == 0 || !Intent.class.isAssignableFrom(parameterTypes[0])) {
            return null;
        }
        Object[] args = new Object[parameterTypes.length];
        args[0] = launchIntent;
        for (int i = 1; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            if (parameterType == ActivityOptions.class) {
                args[i] = ActivityOptions.makeBasic();
            } else if (parameterType == Bundle.class) {
                args[i] = Bundle.EMPTY;
            } else if (TextUtils.equals(parameterType.getName(), "android.os.UserHandle")) {
                args[i] = android.os.Process.myUserHandle();
            } else if (parameterType.isPrimitive()) {
                return null;
            } else {
                args[i] = null;
            }
        }
        return args;
    }
}
