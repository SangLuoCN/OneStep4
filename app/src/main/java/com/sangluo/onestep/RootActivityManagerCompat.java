package com.sangluo.onestep;

import java.lang.reflect.Method;

/** Resolves the activity/task service across the Android 7 through current split. */
final class RootActivityManagerCompat {
    private RootActivityManagerCompat() {
    }

    static Object getTaskService() throws ReflectiveOperationException {
        ReflectiveOperationException lastFailure = null;
        String[][] candidates = {
                {"android.app.ActivityTaskManager", "getService"},
                {"android.app.ActivityManager", "getService"},
                {"android.app.ActivityManagerNative", "getDefault"}
        };
        for (String[] candidate : candidates) {
            try {
                Class<?> managerClass = Class.forName(candidate[0]);
                Method getter = managerClass.getDeclaredMethod(candidate[1]);
                getter.setAccessible(true);
                Object service = getter.invoke(null);
                if (service != null) {
                    return service;
                }
            } catch (ReflectiveOperationException e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IllegalStateException("activity task service unavailable");
    }
}
