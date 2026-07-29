package com.sangluo.onestep.hook;

import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Adds the zero-height framework overlay before virtual-display apps bind. */
public final class OneStepStatusBarOverlayHook {
    private static final String TAG = "OneStepStatusOverlay";
    private static final String DISPLAY_NAME_PREFIX = "OneStepSlot-";
    private static final String[] SUPERVISOR_CLASSES = {
            "com.android.server.wm.ActivityTaskSupervisor",
            "com.android.server.am.ActivityStackSupervisor"
    };
    private static final String[] ACTIVITY_RECORD_CLASSES = {
            "com.android.server.wm.ActivityRecord",
            "com.android.server.am.ActivityRecord"
    };
    private static final String[] PROCESS_RECORD_CLASSES = {
            "com.android.server.am.ProcessRecord"
    };
    private static final String[] ROOT_CONTAINER_CLASSES = {
            "com.android.server.wm.RootWindowContainer",
            "com.android.server.am.ActivityStackSupervisor"
    };
    private static final String OVERLAY_PATH =
            "/system/etc/onestep/OneStepStatusBarZeroOverlay.apk";
    private static final String IDMAP_PATH =
            "/data/resource-cache/system@etc@onestep@OneStepStatusBarZeroOverlay.apk@idmap";
    private static final long CLASS_WAIT_TIMEOUT_MILLIS = 120_000L;
    private static final long CLASS_WAIT_INTERVAL_MILLIS = 100L;
    private static final long PENDING_TIMEOUT_MILLIS = 120_000L;
    private static final Map<String, Long> pendingProcesses = new ConcurrentHashMap<>();
    private static final Set<String> overlaidProcesses = ConcurrentHashMap.newKeySet();
    private static final StatusBarOverlayCrashGuard crashGuard =
            new StatusBarOverlayCrashGuard();

    private static volatile boolean installed;
    private static volatile boolean installationStarted;
    private static volatile long lastNotReadyLogUptime;

    private OneStepStatusBarOverlayHook() {
    }

    public static synchronized void bootstrap(ClassLoader systemServerClassLoader) {
        if (installed || installationStarted) {
            return;
        }
        if (systemServerClassLoader == null) {
            Log.e(TAG, "system_server class loader unavailable");
            return;
        }
        installationStarted = true;
        Thread installer = new Thread(
                () -> awaitAndInstall(systemServerClassLoader),
                "OneStepStatusOverlayInstaller");
        installer.setContextClassLoader(systemServerClassLoader);
        installer.setDaemon(true);
        installer.start();
    }

    private static void awaitAndInstall(ClassLoader fallbackClassLoader) {
        long deadline = android.os.SystemClock.uptimeMillis() + CLASS_WAIT_TIMEOUT_MILLIS;
        Throwable lastFailure = null;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                ResolvedClasses resolved = resolveClasses(fallbackClassLoader);
                installHooks(resolved);
                return;
            } catch (ClassNotFoundException e) {
                lastFailure = e;
                android.os.SystemClock.sleep(CLASS_WAIT_INTERVAL_MILLIS);
            } catch (Throwable t) {
                Log.e(TAG, "could not install display-scoped framework overlay hook",
                        unwrap(t));
                return;
            }
        }
        Log.e(TAG, "timed out waiting for activity manager classes", lastFailure);
    }

    private static synchronized void installHooks(ResolvedClasses resolved) {
        if (installed) {
            return;
        }
        try {
            XposedBridge.disableHiddenApiRestrictions();
            Class<?> supervisorClass = resolved.supervisorClass;
            Class<?> applicationThreadProxyClass = resolved.applicationThreadProxyClass;
            int activityHooks = installActivityStartHooks(supervisorClass);
            int bindHooks = installBindApplicationHooks(applicationThreadProxyClass);
            int processDeathHooks = installProcessDeathHooks(
                    resolved.activityManagerServiceClass);
            if (activityHooks == 0) {
                throw new IllegalStateException("activity launch methods unavailable");
            }
            deoptimizeActivityLaunchCallers(resolved.classLoader, supervisorClass);
            installed = true;
            Log.i(TAG, "display-scoped framework overlay hook installed: activity="
                    + activityHooks + ", bind=" + bindHooks
                    + ", processDeath=" + processDeathHooks);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private static ResolvedClasses resolveClasses(ClassLoader fallback)
            throws ClassNotFoundException {
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
                Class<?> supervisorClass = findFirstClass(candidate, SUPERVISOR_CLASSES);
                Class<?> proxyClass = Class.forName(
                        "android.app.IApplicationThread$Stub$Proxy", false, candidate);
                Class<?> activityManagerServiceClass = Class.forName(
                        "com.android.server.am.ActivityManagerService", false, candidate);
                return new ResolvedClasses(
                        candidate, supervisorClass, proxyClass, activityManagerServiceClass);
            } catch (ClassNotFoundException e) {
                lastFailure = e;
            }
        }
        throw new ClassNotFoundException(
                "activity manager classes unavailable in " + candidates.size()
                        + " active class loaders",
                lastFailure);
    }

    private static void addClassLoaderChain(Set<ClassLoader> candidates, ClassLoader loader) {
        ClassLoader current = loader;
        while (current != null && candidates.add(current)) {
            current = current.getParent();
        }
    }

    private static final class ResolvedClasses {
        final ClassLoader classLoader;
        final Class<?> supervisorClass;
        final Class<?> applicationThreadProxyClass;
        final Class<?> activityManagerServiceClass;

        ResolvedClasses(ClassLoader classLoader, Class<?> supervisorClass,
                        Class<?> applicationThreadProxyClass,
                        Class<?> activityManagerServiceClass) {
            this.classLoader = classLoader;
            this.supervisorClass = supervisorClass;
            this.applicationThreadProxyClass = applicationThreadProxyClass;
            this.activityManagerServiceClass = activityManagerServiceClass;
        }
    }

    private static int installActivityStartHooks(Class<?> supervisorClass) {
        XC_MethodHook callback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object activityRecord = findActivityRecordArgument(param.args);
                    if (activityRecord != null) {
                        handleActivityStart(param.thisObject, activityRecord);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "activity start overlay decision failed", unwrap(t));
                }
            }
        };
        int installedHooks = 0;
        for (String methodName : new String[]{
                "realStartActivityLocked",
                "startSpecificActivity",
                "startSpecificActivityLocked"}) {
            installedHooks += hookActivityRecordMethods(supervisorClass, methodName, callback);
        }
        return installedHooks;
    }

    private static int hookActivityRecordMethods(Class<?> owner, String methodName,
                                                 XC_MethodHook callback) {
        int installedHooks = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())
                    || !hasParameterNamed(method, ACTIVITY_RECORD_CLASSES)) {
                continue;
            }
            try {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, callback);
                XposedBridge.deoptimizeMethod(method);
                installedHooks++;
            } catch (Throwable t) {
                Log.w(TAG, "could not hook " + owner.getName() + "#" + methodName,
                        unwrap(t));
            }
        }
        return installedHooks;
    }

    private static int installBindApplicationHooks(Class<?> proxyClass) {
        XC_MethodHook callback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    int appInfoIndex = findArgumentIndex(param.args, ApplicationInfo.class);
                    if (appInfoIndex < 0) {
                        return;
                    }
                    ApplicationInfo appInfo = (ApplicationInfo) param.args[appInfoIndex];
                    String processName = findProcessName(param.args, appInfo);
                    String key = processKey(appInfo.uid, processName);
                    Long markedAt = pendingProcesses.remove(key);
                    boolean markedForVirtualDisplay = markedAt != null
                            && android.os.SystemClock.uptimeMillis() - markedAt
                            <= PENDING_TIMEOUT_MILLIS;
                    if (!markedForVirtualDisplay && !hasOverlayPath(appInfo)) {
                        return;
                    }
                    if (crashGuard.isDisabled(key)) {
                        param.args[appInfoIndex] = copyWithOverlay(appInfo, false);
                        overlaidProcesses.remove(key);
                        crashGuard.clearApplied(key);
                        Log.w(TAG, "binding process without zero-status-bar overlay after "
                                + "repeated fast deaths: " + processName);
                        return;
                    }
                    if (!overlayReady()) {
                        param.args[appInfoIndex] = copyWithOverlay(appInfo, false);
                        logOverlayNotReady();
                        return;
                    }
                    param.args[appInfoIndex] = copyWithOverlay(appInfo, true);
                    overlaidProcesses.add(key);
                    crashGuard.markApplied(key, android.os.SystemClock.uptimeMillis());
                    Log.i(TAG, "binding virtual-display process with zero status bar: "
                            + processName);
                } catch (Throwable t) {
                    Log.e(TAG, "bindApplication overlay update failed", unwrap(t));
                }
            }
        };
        int installedHooks = 0;
        for (Method method : proxyClass.getDeclaredMethods()) {
            if (!"bindApplication".equals(method.getName())
                    || !hasParameterType(method, ApplicationInfo.class)) {
                continue;
            }
            try {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, callback);
                XposedBridge.deoptimizeMethod(method);
                installedHooks++;
            } catch (Throwable t) {
                Log.w(TAG, "could not hook IApplicationThread.Proxy#bindApplication",
                        unwrap(t));
            }
        }
        return installedHooks;
    }

    private static int installProcessDeathHooks(Class<?> activityManagerServiceClass) {
        XC_MethodHook callback = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    Object processRecord = findNamedArgument(param.args, PROCESS_RECORD_CLASSES);
                    if (processRecord != null) {
                        handleProcessDeath(processRecord);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "process death overlay fallback failed", unwrap(t));
                }
            }
        };
        int installedHooks = hookNamedArgumentMethods(
                activityManagerServiceClass,
                "handleAppDiedLocked",
                PROCESS_RECORD_CLASSES,
                callback);
        if (installedHooks == 0) {
            installedHooks = hookNamedArgumentMethods(
                    activityManagerServiceClass,
                    "appDiedLocked",
                    PROCESS_RECORD_CLASSES,
                    callback);
        }
        if (installedHooks == 0) {
            Log.w(TAG, "process death callback unavailable; crash-loop fallback disabled");
        }
        return installedHooks;
    }

    private static int hookNamedArgumentMethods(Class<?> owner, String methodName,
                                                String[] argumentClassNames,
                                                XC_MethodHook callback) {
        int installedHooks = 0;
        for (Method method : owner.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())
                    || !hasParameterNamed(method, argumentClassNames)) {
                continue;
            }
            try {
                method.setAccessible(true);
                XposedBridge.hookMethod(method, callback);
                XposedBridge.deoptimizeMethod(method);
                installedHooks++;
            } catch (Throwable t) {
                Log.w(TAG, "could not hook " + owner.getName() + "#" + methodName,
                        unwrap(t));
            }
        }
        return installedHooks;
    }

    private static void handleProcessDeath(Object processRecord) {
        Object processNameValue = readFieldOrNull(processRecord, "processName");
        Object uidValue = readFieldOrNull(processRecord, "uid");
        if (!(processNameValue instanceof String) || !(uidValue instanceof Integer)) {
            return;
        }
        String processName = (String) processNameValue;
        String key = processKey((Integer) uidValue, processName);
        pendingProcesses.remove(key);
        overlaidProcesses.remove(key);
        if (crashGuard.recordProcessDeath(key, android.os.SystemClock.uptimeMillis())) {
            Log.w(TAG, "disabled zero-status-bar overlay for this boot after repeated "
                    + "fast process deaths: " + processName);
        }
    }

    private static void handleActivityStart(Object supervisor, Object activityRecord)
            throws ReflectiveOperationException {
        Boolean oneStepDisplay = isOneStepDisplay(activityRecord);
        if (oneStepDisplay == null) {
            return;
        }
        ActivityInfo activityInfo = activityInfoFor(activityRecord);
        if (activityInfo == null || activityInfo.applicationInfo == null) {
            return;
        }
        ApplicationInfo originalAppInfo = activityInfo.applicationInfo;
        String processName = processNameFor(activityRecord, activityInfo);
        String key = processKey(originalAppInfo.uid, processName);
        if (oneStepDisplay && crashGuard.isDisabled(key)) {
            pendingProcesses.remove(key);
            overlaidProcesses.remove(key);
            crashGuard.clearApplied(key);
            if (hasOverlayPath(originalAppInfo)) {
                ActivityInfo adjustedActivityInfo = new ActivityInfo(activityInfo);
                adjustedActivityInfo.applicationInfo = copyWithOverlay(originalAppInfo, false);
                if (!writeActivityInfo(activityRecord, adjustedActivityInfo)) {
                    Log.w(TAG, "ActivityRecord info field unavailable; could not remove "
                            + "disabled overlay path");
                }
            }
            return;
        }
        if (oneStepDisplay && !overlayReady()) {
            logOverlayNotReady();
            return;
        }
        if (!oneStepDisplay
                && !pendingProcesses.containsKey(key)
                && !overlaidProcesses.contains(key)
                && !hasOverlayPath(originalAppInfo)) {
            return;
        }

        ApplicationInfo adjustedAppInfo = copyWithOverlay(originalAppInfo, oneStepDisplay);
        ActivityInfo adjustedActivityInfo = new ActivityInfo(activityInfo);
        adjustedActivityInfo.applicationInfo = adjustedAppInfo;
        if (!writeActivityInfo(activityRecord, adjustedActivityInfo)) {
            Log.w(TAG, "ActivityRecord info field unavailable; leaving resources unchanged");
            return;
        }
        if (oneStepDisplay) {
            pendingProcesses.put(key, android.os.SystemClock.uptimeMillis());
        } else {
            pendingProcesses.remove(key);
            crashGuard.clearApplied(key);
        }

        boolean sentToRunningProcess = scheduleApplicationInfoChanged(
                supervisor, activityRecord, processName, adjustedAppInfo);
        Log.i(TAG, "virtual-display resource decision: process=" + processName
                + ", enabled=" + oneStepDisplay
                + ", running=" + sentToRunningProcess);
        if (sentToRunningProcess) {
            pendingProcesses.remove(key);
            if (oneStepDisplay) {
                overlaidProcesses.add(key);
                crashGuard.markApplied(key, android.os.SystemClock.uptimeMillis());
            } else {
                overlaidProcesses.remove(key);
            }
        }
    }

    private static boolean scheduleApplicationInfoChanged(Object supervisor,
                                                           Object activityRecord,
                                                           String processName,
                                                           ApplicationInfo appInfo) {
        try {
            Object processController = readFieldOrNull(activityRecord, "app");
            if (processController == null) {
                Object service = readField(supervisor, "mService");
                processController = invokeCompatible(
                        service, "getProcessController", processName, appInfo.uid);
            }
            if (processController == null
                    || !Boolean.TRUE.equals(invokeCompatible(processController, "hasThread"))) {
                return false;
            }
            Object applicationThread = invokeCompatible(processController, "getThread");
            if (applicationThread == null) {
                return false;
            }
            invokeCompatible(applicationThread, "scheduleApplicationInfoChanged", appInfo);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "could not refresh resources in running process " + processName,
                    unwrap(t));
            return false;
        }
    }

    private static ApplicationInfo copyWithOverlay(ApplicationInfo source, boolean enabled)
            throws ReflectiveOperationException {
        ApplicationInfo copy = new ApplicationInfo(source);
        if (enabled) {
            if (!updatePathField(copy, "overlayPaths", true)) {
                updatePathField(copy, "resourceDirs", true);
            }
        } else {
            updatePathField(copy, "overlayPaths", false);
            updatePathField(copy, "resourceDirs", false);
        }
        return copy;
    }

    private static boolean overlayReady() {
        return new File(OVERLAY_PATH).isFile() && new File(IDMAP_PATH).isFile();
    }

    private static void logOverlayNotReady() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastNotReadyLogUptime < 30_000L) {
            return;
        }
        lastNotReadyLogUptime = now;
        Log.w(TAG, "overlay or idmap not ready; leaving application resources unchanged");
    }

    private static boolean updatePathField(ApplicationInfo appInfo, String fieldName,
                                           boolean enabled) throws ReflectiveOperationException {
        Field field = findFieldOrNull(appInfo.getClass(), fieldName);
        if (field == null) {
            return false;
        }
        Object current = field.get(appInfo);
        String[] paths = current instanceof String[] ? (String[]) current : null;
        field.set(appInfo, StatusBarOverlayPathPolicy.update(paths, OVERLAY_PATH, enabled));
        return true;
    }

    private static boolean hasOverlayPath(ApplicationInfo appInfo) {
        return pathFieldContains(appInfo, "resourceDirs")
                || pathFieldContains(appInfo, "overlayPaths");
    }

    private static boolean pathFieldContains(ApplicationInfo appInfo, String fieldName) {
        try {
            Field field = findFieldOrNull(appInfo.getClass(), fieldName);
            Object value = field == null ? null : field.get(appInfo);
            return value instanceof String[]
                    && StatusBarOverlayPathPolicy.contains((String[]) value, OVERLAY_PATH);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static ActivityInfo activityInfoFor(Object activityRecord) {
        Object info = readFieldOrNull(activityRecord, "info");
        if (!(info instanceof ActivityInfo)) {
            info = readFieldOrNull(activityRecord, "mActivityInfo");
        }
        return info instanceof ActivityInfo ? (ActivityInfo) info : null;
    }

    private static boolean writeActivityInfo(Object activityRecord, ActivityInfo activityInfo) {
        for (String fieldName : new String[]{"info", "mActivityInfo"}) {
            Field field = findFieldOrNull(activityRecord.getClass(), fieldName);
            if (field == null || !ActivityInfo.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.set(activityRecord, activityInfo);
                return true;
            } catch (IllegalAccessException | RuntimeException ignored) {
            }
        }
        return false;
    }

    private static String processNameFor(Object activityRecord, ActivityInfo activityInfo) {
        Object value = readFieldOrNull(activityRecord, "processName");
        if (value instanceof String && !((String) value).isEmpty()) {
            return (String) value;
        }
        if (activityInfo.processName != null && !activityInfo.processName.isEmpty()) {
            return activityInfo.processName;
        }
        return activityInfo.applicationInfo.processName;
    }

    private static String findProcessName(Object[] args, ApplicationInfo appInfo) {
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof String && arg.equals(appInfo.processName)) {
                    return (String) arg;
                }
            }
            if (args.length > 0 && args[0] instanceof String) {
                return (String) args[0];
            }
        }
        return appInfo.processName;
    }

    private static String processKey(int uid, String processName) {
        return uid + ":" + String.valueOf(processName);
    }

    private static Boolean isOneStepDisplay(Object activityRecord) {
        try {
            Object displayContent = invokeCompatible(activityRecord, "getDisplayContent");
            if (displayContent == null) {
                displayContent = readFieldOrNull(activityRecord, "mDisplayContent");
            }
            if (displayContent != null) {
                String displayName = displayNameFromInfoOwner(displayContent);
                if (displayName != null) {
                    return displayName.startsWith(DISPLAY_NAME_PREFIX);
                }
            }
            Object displayId = invokeCompatible(activityRecord, "getDisplayId");
            if (!(displayId instanceof Integer)) {
                return null;
            }
            Class<?> managerClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
            Object manager = invokeStaticCompatible(managerClass, "getInstance");
            Object displayInfo = invokeCompatible(manager, "getDisplayInfo", displayId);
            Object name = readFieldOrNull(displayInfo, "name");
            return name instanceof String ? ((String) name).startsWith(DISPLAY_NAME_PREFIX) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String displayNameFromInfoOwner(Object displayContent) {
        Object displayInfo;
        try {
            displayInfo = invokeCompatible(displayContent, "getDisplayInfo");
        } catch (ReflectiveOperationException ignored) {
            displayInfo = readFieldOrNull(displayContent, "mDisplayInfo");
        }
        Object name = readFieldOrNull(displayInfo, "name");
        return name instanceof String ? (String) name : null;
    }

    private static Object findActivityRecordArgument(Object[] args) {
        return findNamedArgument(args, ACTIVITY_RECORD_CLASSES);
    }

    private static Object findNamedArgument(Object[] args, String[] classNames) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null) {
                continue;
            }
            String className = arg.getClass().getName();
            for (String candidate : classNames) {
                if (candidate.equals(className)) {
                    return arg;
                }
            }
        }
        return null;
    }

    private static Class<?> findFirstClass(ClassLoader classLoader, String[] classNames)
            throws ClassNotFoundException {
        ClassNotFoundException lastFailure = null;
        for (String className : classNames) {
            try {
                return Class.forName(className, false, classLoader);
            } catch (ClassNotFoundException e) {
                lastFailure = e;
            }
        }
        throw lastFailure == null ? new ClassNotFoundException() : lastFailure;
    }

    private static boolean hasParameterNamed(Method method, String[] classNames) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            for (String className : classNames) {
                if (className.equals(parameterType.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasParameterType(Method method, Class<?> type) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (type.isAssignableFrom(parameterType)) {
                return true;
            }
        }
        return false;
    }

    private static void deoptimizeActivityLaunchCallers(ClassLoader classLoader,
                                                        Class<?> supervisorClass) {
        deoptimizeMethodsAcceptingActivityRecord(supervisorClass);
        for (String className : ROOT_CONTAINER_CLASSES) {
            try {
                Class<?> rootClass = Class.forName(className, false, classLoader);
                deoptimizeMethodsAcceptingActivityRecord(rootClass);
                for (Class<?> nestedClass : rootClass.getDeclaredClasses()) {
                    deoptimizeMethodsAcceptingActivityRecord(nestedClass);
                }
                return;
            } catch (ClassNotFoundException ignored) {
            } catch (Throwable t) {
                Log.w(TAG, "could not deoptimize activity launch callers in " + className,
                        unwrap(t));
                return;
            }
        }
    }

    private static void deoptimizeMethodsAcceptingActivityRecord(Class<?> owner) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!hasParameterNamed(method, ACTIVITY_RECORD_CLASSES)) {
                continue;
            }
            try {
                method.setAccessible(true);
                XposedBridge.deoptimizeMethod(method);
            } catch (Throwable t) {
                Log.w(TAG, "could not deoptimize " + owner.getName() + "#"
                        + method.getName(), unwrap(t));
            }
        }
    }

    private static int findArgumentIndex(Object[] args, Class<?> type) {
        if (args == null) {
            return -1;
        }
        for (int i = 0; i < args.length; i++) {
            if (type.isInstance(args[i])) {
                return i;
            }
        }
        return -1;
    }

    private static Object readField(Object target, String name)
            throws ReflectiveOperationException {
        if (target == null) {
            throw new NullPointerException(name);
        }
        Field field = findFieldOrNull(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        return field.get(target);
    }

    private static Object readFieldOrNull(Object target, String name) {
        try {
            return readField(target, name);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findFieldOrNull(Class<?> type, String name) {
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
        return null;
    }

    private static Object invokeCompatible(Object target, String name, Object... args)
            throws ReflectiveOperationException {
        if (target == null) {
            throw new NullPointerException(name);
        }
        Method method = findCompatibleMethod(target.getClass(), name, args);
        if (method == null) {
            throw new NoSuchMethodException(name);
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw new InvocationTargetException(cause);
        }
    }

    private static Object invokeStaticCompatible(Class<?> type, String name, Object... args)
            throws ReflectiveOperationException {
        Method method = findCompatibleMethod(type, name, args);
        if (method == null) {
            throw new NoSuchMethodException(name);
        }
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ReflectiveOperationException) {
                throw (ReflectiveOperationException) cause;
            }
            throw new InvocationTargetException(cause);
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)
                        || method.getParameterTypes().length != args.length
                        || !parametersMatch(method.getParameterTypes(), args)) {
                    continue;
                }
                method.setAccessible(true);
                return method;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) {
                if (parameterTypes[i].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> parameterType = parameterTypes[i].isPrimitive()
                    ? boxedType(parameterTypes[i]) : parameterTypes[i];
            if (!parameterType.isInstance(args[i])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        if (primitive == short.class) return Short.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        return primitive;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof InvocationTargetException
                || current instanceof java.lang.reflect.UndeclaredThrowableException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
