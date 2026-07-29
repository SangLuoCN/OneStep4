package com.sangluo.onestep.hook;

import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

import de.robv.android.xposed.XposedBridge;

/** Optional Xposed API extensions shared by Aliuhook and LSPosed backends. */
final class HookBridgeCompat {
    private static final String TAG = "OneStepHookCompat";

    private HookBridgeCompat() {
    }

    static void disableHiddenApiRestrictions() {
        invokeOptional("disableHiddenApiRestrictions", new Class<?>[0]);
    }

    static void deoptimizeMethod(Member member) {
        invokeOptional("deoptimizeMethod", new Class<?>[]{Member.class}, member);
    }

    private static void invokeOptional(String name, Class<?>[] parameterTypes,
                                       Object... arguments) {
        try {
            Method method = XposedBridge.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(null, arguments);
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Xposed backend does not expose " + name);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException e) {
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null
                    ? e.getCause() : e;
            Log.w(TAG, "Xposed backend call failed: " + name, cause);
        }
    }
}
