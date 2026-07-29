package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Applies the WindowManager system-decor policy for a hosted virtual display. */
final class VirtualDisplaySystemDecorController {
    private VirtualDisplaySystemDecorController() {
    }

    @SuppressLint("BlockedPrivateApi")
    static Result disable(Context context, int displayId) {
        if (context == null || displayId <= Display.DEFAULT_DISPLAY) {
            return Result.failure("invalid display");
        }
        Object windowManager = context.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return Result.failure("window service unavailable");
        }

        boolean requested = false;
        try {
            Method setter = findMethod(windowManager, "setShouldShowSystemDecors",
                    int.class, boolean.class);
            setter.invoke(windowManager, displayId, false);
            requested = true;

            Method getter = findMethod(windowManager, "shouldShowSystemDecors", int.class);
            Object actual = getter.invoke(windowManager, displayId);
            return actual instanceof Boolean
                    ? Result.verified(requested, (Boolean) actual)
                    : Result.unverified(requested, "invalid policy result");
        } catch (NoSuchMethodException e) {
            return Result.unverified(requested, "system-decor API unavailable");
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Result.unverified(requested, describe(e));
        }
    }

    private static Method findMethod(Object target, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        try {
            Method method = WindowManager.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            Method method = target.getClass().getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }
    }

    private static String describe(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    static final class Result {
        final boolean requested;
        final Boolean shouldShowSystemDecorations;
        final String failure;

        private Result(boolean requested, Boolean shouldShowSystemDecorations,
                       String failure) {
            this.requested = requested;
            this.shouldShowSystemDecorations = shouldShowSystemDecorations;
            this.failure = failure;
        }

        static Result verified(boolean requested, boolean shouldShowSystemDecorations) {
            return new Result(requested, shouldShowSystemDecorations, "");
        }

        static Result unverified(boolean requested, String failure) {
            return new Result(requested, null, failure);
        }

        static Result failure(String failure) {
            return new Result(false, null, failure);
        }

        boolean isConfirmedDisabled() {
            return requested && Boolean.FALSE.equals(shouldShowSystemDecorations);
        }

        String actualValue() {
            return shouldShowSystemDecorations == null
                    ? "unknown" : shouldShowSystemDecorations.toString();
        }
    }
}
