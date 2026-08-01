package com.sangluo.onestep.hook;

import android.util.Log;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Removes HyperOS Settings' UI-only block on selecting third-party HOME apps. */
final class HyperOsThirdPartyHomeBypassHook {
    private static final String TAG = "OneStepHyperOsHome";
    private static final String TARGET_CLASS =
            "com.android.settings.applications.DefaultHomeSettings$DefaultHomeSettingsFragment";

    private static boolean installed;

    private HyperOsThirdPartyHomeBypassHook() {
    }

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) {
            return;
        }
        try {
            Class<?> targetClass = Class.forName(TARGET_CLASS, false, classLoader);
            Method target = targetClass.getDeclaredMethod(
                    "shouldBlockThirdDesktop", String.class);
            target.setAccessible(true);
            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(false);
                }
            });
            HookBridgeCompat.deoptimizeMethod(target);
            installed = true;
            Log.i(TAG, "HyperOS third-party HOME restriction bypass installed");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            Log.i(TAG, "HyperOS default HOME restriction is not present");
        } catch (Throwable t) {
            Log.e(TAG, "Could not install HyperOS third-party HOME bypass", t);
        }
    }
}
