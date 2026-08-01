package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Rect;
import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import com.sangluo.onestep.system.root.SystemServiceFailurePolicy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

@SuppressLint({"BlockedPrivateApi", "DiscouragedPrivateApi", "PrivateApi"})
public final class RootInputBridge {
    private static final String TAG = "OneStepInputBridge";
    private static final int INJECT_INPUT_EVENT_MODE_ASYNC = 0;
    private static final int VIRTUAL_TOUCH_DEVICE_ID = 0;
    private static final int ARG_ALLOWED_UID = 0;
    private static final int ARG_BRIDGE_TOKEN = 1;
    private static final String SOCKET_NAME_PREFIX = "onestep_input_bridge_";
    public static final String HELLO_RESPONSE_PREFIX = "onestep-input-bridge-v21";
    private static final int ROOT_UID = 0;
    private static final int SHELL_UID = 2000;
    private static final int WINDOWING_MODE_PINNED = 2;
    private static final int ACTIVITY_TYPE_UNDEFINED = 0;
    private static final int PIP_DRAG_STEPS = 16;
    private static final int PIP_DOCK_POSITION_TOLERANCE_PX = 24;
    private static final long PIP_DRAG_STEP_INTERVAL_MS = 18L;
    private static final long PIP_DRAG_RELEASE_SETTLE_MS = 80L;
    private static final long PIP_DOCK_LEASE_TIMEOUT_MS = 1800L;
    private static final long PIP_DOCK_WATCHDOG_INTERVAL_MS = 300L;
    private static final long FAILURE_LOG_THROTTLE_MS = 2000L;

    private final int allowedUid;
    private final String bridgeToken;
    private final ActivityManager activityManager;
    private final Object inputManager;
    private final Method injectInputEventMethod;
    private Object activityTaskManagerService;
    private Method focusTopTaskMethod;
    private Method removeTaskMethod;
    private Method moveRootTaskToDisplayMethod;
    private Method getRootTaskInfoMethod;
    private Method setDisplayIdMethod;
    private Object windowManagerService;
    private Method setDisplayImePolicyMethod;
    private Method getDisplayImePolicyMethod;
    private Method setShouldShowImeMethod;
    private Method shouldShowImeMethod;
    private Method thawDisplayRotationMethod;
    private Method freezeDisplayRotationMethod;
    private Method setFixedToUserRotationMethod;
    private Method setIgnoreOrientationRequestMethod;
    private final Object pipDockLock = new Object();
    private int dockedPipTaskId = -1;
    private Rect dockedPipRestoreBounds;
    private long pipDockLeaseUptime;
    private long lastFailureLogUptime;

    private RootInputBridge(int allowedUid, String bridgeToken, Context systemContext)
            throws ReflectiveOperationException {
        this.allowedUid = allowedUid;
        this.bridgeToken = bridgeToken;
        activityManager = (ActivityManager) systemContext.getSystemService(
                Context.ACTIVITY_SERVICE);
        disableHiddenApiChecks();
        Object resolvedInputManager = null;
        Method resolvedInjectInputEventMethod = null;
        String[] directManagerClasses = {
                "android.hardware.input.InputManagerGlobal",
                "android.hardware.input.InputManager"
        };
        for (String className : directManagerClasses) {
            try {
                Class<?> managerClass = Class.forName(className);
                Method getInstanceMethod = managerClass.getDeclaredMethod("getInstance");
                getInstanceMethod.setAccessible(true);
                Object candidate = getInstanceMethod.invoke(null);
                Method injectMethod = candidate == null ? null
                        : findInjectInputEventMethod(candidate.getClass());
                if (injectMethod == null) {
                    continue;
                }
                injectMethod.setAccessible(true);
                resolvedInputManager = candidate;
                resolvedInjectInputEventMethod = injectMethod;
                break;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // OEM releases may move or wrap the singleton; use the next compatibility path.
            }
        }
        if (resolvedInputManager == null || resolvedInjectInputEventMethod == null) {
            resolvedInputManager = systemContext.getSystemService(Context.INPUT_SERVICE);
            resolvedInjectInputEventMethod = resolvedInputManager == null ? null
                    : findInjectInputEventMethod(resolvedInputManager.getClass());
        }
        if (resolvedInputManager == null || resolvedInjectInputEventMethod == null) {
            throw new IllegalStateException("input service unavailable");
        }
        inputManager = resolvedInputManager;
        injectInputEventMethod = resolvedInjectInputEventMethod;
        injectInputEventMethod.setAccessible(true);
        Log.i(TAG, "input injection backend=" + inputManager.getClass().getName());
    }

    public static void main(String[] args) {
        int bridgeUid = android.os.Process.myUid();
        if (bridgeUid != ROOT_UID && bridgeUid != SHELL_UID) {
            Log.e(TAG, "bridge requires root or shell uid, actual=" + bridgeUid);
            return;
        }
        int allowedUid = parseAllowedUid(args);
        if (allowedUid <= 0) {
            Log.e(TAG, "missing allowed uid");
            return;
        }
        String bridgeToken = parseBridgeToken(args);
        if (bridgeToken == null) {
            Log.e(TAG, "missing bridge token");
            return;
        }
        Log.i(TAG, "bridge starting uid=" + android.os.Process.myUid()
                + " allowedUid=" + allowedUid
                + " bridgeToken=" + bridgeToken);
        try {
            disableHiddenApiChecks();
            Context systemContext = createSystemContext();
            RootInputBridge bridge = new RootInputBridge(
                    allowedUid, bridgeToken, systemContext);
            RootVirtualDisplayBridge.publish(systemContext, allowedUid, bridgeToken);
            bridge.serve();
        } catch (Throwable throwable) {
            Log.e(TAG, "bridge crashed", throwable);
        }
    }

    private static void disableHiddenApiChecks() {
        try {
            Class<?> vmRuntimeClass = Class.forName("dalvik.system.VMRuntime");
            Method getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime");
            Method setHiddenApiExemptions = vmRuntimeClass.getDeclaredMethod(
                    "setHiddenApiExemptions", String[].class);
            Object vmRuntime = getRuntime.invoke(null);
            setHiddenApiExemptions.invoke(vmRuntime, (Object) new String[] {"L"});
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "hidden api exemption unavailable: " + e.getClass().getSimpleName());
        }
    }

    private static int parseAllowedUid(String[] args) {
        if (args == null || args.length <= ARG_ALLOWED_UID) {
            return -1;
        }
        try {
            return Integer.parseInt(args[ARG_ALLOWED_UID]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String parseBridgeToken(String[] args) {
        if (args == null || args.length <= ARG_BRIDGE_TOKEN) {
            return null;
        }
        String token = args[ARG_BRIDGE_TOKEN];
        return token == null || token.trim().isEmpty() ? null : token.trim();
    }

    private static Context createSystemContext() throws ReflectiveOperationException {
        if (Looper.myLooper() == null) {
            Looper.prepareMainLooper();
        }
        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Method systemMain = activityThreadClass.getDeclaredMethod("systemMain");
        systemMain.setAccessible(true);
        Object activityThread = systemMain.invoke(null);
        Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
        getSystemContext.setAccessible(true);
        Object context = getSystemContext.invoke(activityThread);
        if (!(context instanceof Context)) {
            throw new IllegalStateException("system context unavailable");
        }
        return (Context) context;
    }

    private static Method findInjectInputEventMethod(Class<?> type) {
        try {
            return type.getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (NoSuchMethodException ignored) {
            // Hidden/OEM implementations commonly declare the method on a non-public superclass.
        }
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(
                        "injectInputEvent", InputEvent.class, int.class);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void serve() throws IOException {
        Thread pipLeaseWatchdog = new Thread(this::watchPipDockLease,
                "OneStepPipLease");
        pipLeaseWatchdog.setDaemon(true);
        pipLeaseWatchdog.start();
        String socketName = SOCKET_NAME_PREFIX + allowedUid;
        try (LocalServerSocket serverSocket = new LocalServerSocket(socketName)) {
            Log.i(TAG, "listening " + socketName);
            while (true) {
                LocalSocket socket = serverSocket.accept();
                Thread clientThread = new Thread(() -> handleClient(socket),
                        "OneStepInputClient");
                clientThread.start();
            }
        }
    }

    private void handleClient(LocalSocket socket) {
        try (LocalSocket acceptedSocket = socket;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(acceptedSocket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(acceptedSocket.getOutputStream()))) {
            if (!isAllowedClient(acceptedSocket)) {
                Log.w(TAG, "reject client");
                return;
            }
            boolean handshaken = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("hello")) {
                    if (!isValidHello(trimmed)) {
                        Log.w(TAG, "reject hello token");
                        return;
                    }
                    writer.write(HELLO_RESPONSE_PREFIX);
                    writer.write(' ');
                    writer.write(bridgeToken);
                    writer.write(' ');
                    writer.write(String.valueOf(android.os.Process.myUid()));
                    writer.write('\n');
                    writer.flush();
                    handshaken = true;
                    continue;
                }
                if (!handshaken) {
                    Log.w(TAG, "reject command before hello");
                    return;
                }
                String response = handleCommand(line);
                if (response != null) {
                    writer.write(response);
                    writer.write('\n');
                    writer.flush();
                }
            }
        } catch (StaleSystemServiceException e) {
            Log.e(TAG, "system service connection died; stopping stale bridge: "
                    + describeThrowable(e));
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "client disconnected: " + describeThrowable(e));
        }
    }

    private boolean isAllowedClient(LocalSocket socket) throws IOException {
        Credentials credentials = socket.getPeerCredentials();
        int uid = credentials.getUid();
        return uid == allowedUid || uid == 0;
    }

    private boolean isValidHello(String line) {
        return ("hello " + bridgeToken).equals(line);
    }

    private String handleCommand(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0) {
            return null;
        }
        try {
            if ("motionEvent".equals(parts[0]) && parts.length == 4) {
                injectMotionEvent(parts);
            } else if ("motion".equals(parts[0])
                    && (parts.length == 7 || parts.length == 15 || parts.length == 16)) {
                injectMotion(parts);
            } else if ("focusDisplay".equals(parts[0]) && parts.length == 2) {
                return focusDisplay(parts);
            } else if ("removeTask".equals(parts[0]) && parts.length == 2) {
                return removeTask(parts);
            } else if ("moveTaskToDisplay".equals(parts[0]) && parts.length == 4) {
                return moveTaskToDisplay(parts);
            } else if ("key".equals(parts[0]) && parts.length == 3) {
                injectKey(parts);
            } else if ("imePolicy".equals(parts[0]) && parts.length == 3) {
                return setDisplayImePolicy(parts);
            } else if ("displayRotationAuto".equals(parts[0]) && parts.length == 2) {
                return setDisplayRotationAuto(parts);
            } else if ("displayLandscapeRotation".equals(parts[0]) && parts.length == 3) {
                return setDisplayLandscapeRotation(parts);
            } else if ("pipState".equals(parts[0]) && parts.length == 1) {
                return getPipStateResponse();
            } else if ("pipDock".equals(parts[0]) && parts.length == 10) {
                return dockPip(parts);
            } else if ("pipUndock".equals(parts[0]) && parts.length == 6) {
                return undockPip(parts);
            } else if (!"ping".equals(parts[0])) {
                Log.w(TAG, "unknown command: " + line);
            }
        } catch (RuntimeException | ReflectiveOperationException e) {
            throwIfSystemServiceDead(e);
            Log.w(TAG, "inject failed: " + describeThrowable(e));
        }
        return null;
    }

    private void injectMotionEvent(String[] parts) throws ReflectiveOperationException {
        int displayId = Integer.parseInt(parts[1]);
        long traceId = Long.parseLong(parts[2]);
        MotionEvent event = MotionEventCodec.decode(parts[3]);
        int actionMasked = event.getActionMasked();
        int pointerIndex = Math.min(event.getActionIndex(), event.getPointerCount() - 1);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);
        float pressure = event.getPressure(pointerIndex);
        int deviceId = event.getDeviceId();
        String actionName = motionActionName(event.getAction());
        logVirtualMotionReceived(traceId, displayId, actionName,
                event.getDownTime(), event.getEventTime(), x, y, pressure,
                event.getPointerCount());

        int injectMode = getMotionInjectMode(actionMasked);
        long startWallTime = System.currentTimeMillis();
        boolean accepted = false;
        try {
            getSetDisplayIdMethod().invoke(event, displayId);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            RootVirtualDisplayBridge.noteVirtualInput(displayId);
            Object result = injectInputEventMethod.invoke(inputManager, event, injectMode);
            accepted = !Boolean.FALSE.equals(result);
            if (!accepted) {
                logFailure("motion " + actionName + " display=" + displayId
                        + " traceId=" + traceId, "returned false");
            }
        } finally {
            event.recycle();
            logVirtualDispatchFinished(traceId, displayId, actionName, startWallTime,
                    accepted, injectMode, deviceId, pressure);
        }
    }

    @SuppressLint("NewApi")
    private void injectMotion(String[] parts)
            throws InvocationTargetException, IllegalAccessException {
        int displayId = Integer.parseInt(parts[1]);
        int action = Integer.parseInt(parts[2]);
        long downTime = Long.parseLong(parts[3]);
        long eventTime = Long.parseLong(parts[4]);
        float x = Float.parseFloat(parts[5]);
        float y = Float.parseFloat(parts[6]);
        float pressure = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
                ? 0f : 1f;
        float size = 1f;
        int metaState = 0;
        int buttonState = 0;
        float xPrecision = 1f;
        float yPrecision = 1f;
        int edgeFlags = 0;
        int toolType = MotionEvent.TOOL_TYPE_FINGER;
        long traceId = 0L;
        if (parts.length >= 15) {
            pressure = Float.parseFloat(parts[7]);
            size = Float.parseFloat(parts[8]);
            metaState = Integer.parseInt(parts[9]);
            buttonState = Integer.parseInt(parts[10]);
            xPrecision = Float.parseFloat(parts[11]);
            yPrecision = Float.parseFloat(parts[12]);
            edgeFlags = Integer.parseInt(parts[13]);
            toolType = Integer.parseInt(parts[14]);
        }
        if (parts.length >= 16) {
            traceId = Long.parseLong(parts[15]);
        }

        int actionMasked = action & MotionEvent.ACTION_MASK;
        if (actionMasked == MotionEvent.ACTION_UP
                || actionMasked == MotionEvent.ACTION_CANCEL) {
            pressure = 0f;
            buttonState = 0;
        } else if (pressure <= 0f) {
            pressure = 1f;
        }

        String actionName = motionActionName(action);
        logVirtualMotionReceived(traceId, displayId, actionName, downTime, eventTime,
                x, y, pressure, 1);

        int source = InputDevice.SOURCE_TOUCHSCREEN;
        int deviceId = VIRTUAL_TOUCH_DEVICE_ID;
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[1];
        properties[0] = new MotionEvent.PointerProperties();
        properties[0].id = 0;
        properties[0].toolType = toolType > 0 ? toolType : MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[1];
        coords[0] = new MotionEvent.PointerCoords();
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = pressure;
        coords[0].size = size;
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, 1,
                properties, coords, metaState, buttonState, xPrecision, yPrecision,
                deviceId, edgeFlags, source, displayId, 0, MotionEvent.CLASSIFICATION_NONE);
        int injectMode = getMotionInjectMode(actionMasked);
        long startWallTime = System.currentTimeMillis();
        boolean accepted = false;
        try {
            RootVirtualDisplayBridge.noteVirtualInput(displayId);
            Object result = injectInputEventMethod.invoke(inputManager, event, injectMode);
            accepted = !Boolean.FALSE.equals(result);
            if (!accepted) {
                logFailure("motion " + actionName + " display=" + displayId
                        + " traceId=" + traceId, "returned false");
            }
        } finally {
            event.recycle();
            logVirtualDispatchFinished(traceId, displayId, actionName, startWallTime,
                    accepted, injectMode, deviceId, pressure);
        }
    }

    private int getMotionInjectMode(int actionMasked) {
        return INJECT_INPUT_EVENT_MODE_ASYNC;
    }

    private String focusDisplay(String[] parts) {
        int displayId = Integer.parseInt(parts[1]);
        boolean success = false;
        String failure = "";
        try {
            getFocusTopTaskMethod().invoke(getActivityTaskManagerService(), displayId);
            success = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        int priority = success ? Log.INFO : Log.WARN;
        Log.println(priority, TAG, "Focused display=" + displayId
                + " success=" + success
                + (failure.isEmpty() ? "" : " failure=" + failure));
        return "focusDisplay " + displayId + " " + success;
    }

    private String removeTask(String[] parts) {
        int taskId = Integer.parseInt(parts[1]);
        boolean success = false;
        String failure = "";
        try {
            Object result = getRemoveTaskMethod().invoke(
                    getActivityTaskManagerService(), taskId);
            success = Boolean.TRUE.equals(result);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        int priority = success ? Log.INFO : Log.WARN;
        Log.println(priority, TAG, "Removed task=" + taskId
                + " success=" + success
                + (failure.isEmpty() ? "" : " failure=" + failure));
        return "removeTask " + taskId + " " + success;
    }

    private String moveTaskToDisplay(String[] parts) {
        int taskId = Integer.parseInt(parts[1]);
        int displayId = Integer.parseInt(parts[2]);
        ComponentName component = ComponentName.unflattenFromString(parts[3]);
        boolean success = false;
        String failure = "";
        try {
            if (component == null) {
                throw new IllegalArgumentException("invalid task component");
            }
            getMoveRootTaskToDisplayMethod().invoke(
                    getActivityTaskManagerService(), taskId, displayId);
            if (activityManager == null) {
                throw new IllegalStateException("activity service unavailable");
            }
            activityManager.moveTaskToFront(taskId, 0);
            refreshActivityOnDisplay(component, displayId);
            success = true;
        } catch (ReflectiveOperationException | IOException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        int priority = success ? Log.INFO : Log.WARN;
        Log.println(priority, TAG, "Moved task=" + taskId + " to display=" + displayId
                + " success=" + success
                + (failure.isEmpty() ? "" : " failure=" + failure));
        return "moveTaskToDisplay " + taskId + " " + displayId + " " + success;
    }

    private static void refreshActivityOnDisplay(ComponentName component, int displayId)
            throws IOException {
        Process process = new ProcessBuilder(
                "/system/bin/am", "start",
                "--display", String.valueOf(displayId),
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.DEFAULT",
                "-n", component.flattenToString())
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append(' ');
                }
                output.append(line);
            }
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            process.destroy();
            Thread.currentThread().interrupt();
            throw new IOException("activity refresh interrupted", e);
        }
        if (exitCode != 0) {
            throw new IOException("activity refresh failed: " + output);
        }
    }

    private Object getActivityTaskManagerService() throws ReflectiveOperationException {
        if (activityTaskManagerService == null) {
            activityTaskManagerService = RootActivityManagerCompat.getTaskService();
        }
        return activityTaskManagerService;
    }

    private Method getFocusTopTaskMethod() throws ReflectiveOperationException {
        if (focusTopTaskMethod == null) {
            focusTopTaskMethod = getActivityTaskManagerService().getClass()
                    .getMethod("focusTopTask", int.class);
            focusTopTaskMethod.setAccessible(true);
        }
        return focusTopTaskMethod;
    }

    private Method getRemoveTaskMethod() throws ReflectiveOperationException {
        if (removeTaskMethod == null) {
            removeTaskMethod = getActivityTaskManagerService().getClass()
                    .getMethod("removeTask", int.class);
            removeTaskMethod.setAccessible(true);
        }
        return removeTaskMethod;
    }

    private Method getMoveRootTaskToDisplayMethod() throws ReflectiveOperationException {
        if (moveRootTaskToDisplayMethod == null) {
            moveRootTaskToDisplayMethod = getActivityTaskManagerService().getClass()
                    .getMethod("moveRootTaskToDisplay", int.class, int.class);
            moveRootTaskToDisplayMethod.setAccessible(true);
        }
        return moveRootTaskToDisplayMethod;
    }

    private String getPipStateResponse() {
        try {
            PinnedTaskState state = getPinnedTaskState();
            if (state == null) {
                clearStalePipDockLease(-1);
                return "pipState none";
            }
            clearStalePipDockLease(state.taskId);
            Rect bounds = state.bounds;
            return String.format(Locale.US, "pipState active %d %d %d %d %d",
                    state.taskId, bounds.left, bounds.top, bounds.right, bounds.bottom);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            logFailure("query PiP", describeThrowable(e));
            return "pipState error";
        }
    }

    private String dockPip(String[] parts) {
        int taskId;
        Rect targetBounds;
        Rect restoreBounds;
        try {
            taskId = Integer.parseInt(parts[1]);
            targetBounds = parseRect(parts, 2);
            restoreBounds = parseRect(parts, 6);
        } catch (NumberFormatException e) {
            return "pipDock -1 false";
        }
        if (targetBounds.isEmpty() || restoreBounds.isEmpty()) {
            return "pipDock " + taskId + " false";
        }

        boolean success = false;
        String failure = "";
        try {
            PinnedTaskState state = getPinnedTaskState();
            if (state != null && state.taskId == taskId) {
                success = movePinnedTask(state, targetBounds);
            }
            if (success) {
                synchronized (pipDockLock) {
                    dockedPipTaskId = taskId;
                    dockedPipRestoreBounds = new Rect(restoreBounds);
                    pipDockLeaseUptime = SystemClock.uptimeMillis();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        if (!success) {
            Log.w(TAG, "PiP dock failed task=" + taskId
                    + (failure.isEmpty() ? "" : " failure=" + failure));
        }
        return "pipDock " + taskId + " " + success;
    }

    private String undockPip(String[] parts) {
        int taskId;
        Rect restoreBounds;
        try {
            taskId = Integer.parseInt(parts[1]);
            restoreBounds = parseRect(parts, 2);
        } catch (NumberFormatException e) {
            return "pipUndock -1 false";
        }
        boolean success = restoreBounds.isEmpty();
        try {
            PinnedTaskState state = getPinnedTaskState();
            if (state == null) {
                success = true;
            } else if (state.taskId == taskId && !restoreBounds.isEmpty()) {
                success = movePinnedTask(state, restoreBounds);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            Log.w(TAG, "PiP undock failed task=" + taskId + " failure="
                    + describeThrowable(e));
        }
        if (success) {
            synchronized (pipDockLock) {
                if (dockedPipTaskId == taskId) {
                    clearPipDockLeaseLocked();
                }
            }
        }
        return "pipUndock " + taskId + " " + success;
    }

    private Rect parseRect(String[] parts, int offset) throws NumberFormatException {
        return new Rect(Integer.parseInt(parts[offset]), Integer.parseInt(parts[offset + 1]),
                Integer.parseInt(parts[offset + 2]), Integer.parseInt(parts[offset + 3]));
    }

    private PinnedTaskState getPinnedTaskState() throws ReflectiveOperationException {
        Object rootTaskInfo = getGetRootTaskInfoMethod().invoke(
                getActivityTaskManagerService(), WINDOWING_MODE_PINNED,
                ACTIVITY_TYPE_UNDEFINED);
        if (rootTaskInfo == null) {
            return null;
        }
        int taskId = readTaskId(rootTaskInfo);
        Rect bounds = readTaskBounds(rootTaskInfo);
        return taskId > 0 && bounds != null && !bounds.isEmpty()
                ? new PinnedTaskState(taskId, bounds) : null;
    }

    private int readTaskId(Object rootTaskInfo) throws ReflectiveOperationException {
        Object taskId = readOptionalField(rootTaskInfo, "taskId");
        if (taskId instanceof Integer && (Integer) taskId > 0) {
            return (Integer) taskId;
        }
        Object childTaskIds = readOptionalField(rootTaskInfo, "childTaskIds");
        if (childTaskIds instanceof int[] && ((int[]) childTaskIds).length > 0) {
            int[] ids = (int[]) childTaskIds;
            return ids[ids.length - 1];
        }
        return -1;
    }

    private Rect readTaskBounds(Object rootTaskInfo) throws ReflectiveOperationException {
        Object directBounds = readOptionalField(rootTaskInfo, "bounds");
        if (directBounds instanceof Rect && !((Rect) directBounds).isEmpty()) {
            return new Rect((Rect) directBounds);
        }
        Object childTaskBounds = readOptionalField(rootTaskInfo, "childTaskBounds");
        if (childTaskBounds instanceof Rect[] && ((Rect[]) childTaskBounds).length > 0) {
            Rect[] bounds = (Rect[]) childTaskBounds;
            Rect childBounds = bounds[bounds.length - 1];
            if (childBounds != null && !childBounds.isEmpty()) {
                return new Rect(childBounds);
            }
        }
        Object configuration = readOptionalField(rootTaskInfo, "configuration");
        Object windowConfiguration = readOptionalField(configuration, "windowConfiguration");
        if (windowConfiguration == null) {
            return null;
        }
        Method getBounds = windowConfiguration.getClass().getMethod("getBounds");
        getBounds.setAccessible(true);
        Object bounds = getBounds.invoke(windowConfiguration);
        return bounds instanceof Rect ? new Rect((Rect) bounds) : null;
    }

    private Object readOptionalField(Object target, String name)
            throws IllegalAccessException {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean movePinnedTask(PinnedTaskState state, Rect targetBounds)
            throws ReflectiveOperationException {
        if (Math.abs(state.bounds.left - targetBounds.left)
                <= PIP_DOCK_POSITION_TOLERANCE_PX
                && Math.abs(state.bounds.top - targetBounds.top)
                <= PIP_DOCK_POSITION_TOLERANCE_PX) {
            return true;
        }
        long downTime = SystemClock.uptimeMillis();
        float startX = state.bounds.exactCenterX();
        float startY = state.bounds.exactCenterY();
        float endX = targetBounds.exactCenterX();
        float endY = targetBounds.exactCenterY();
        boolean accepted = injectPipDragEvent(downTime, downTime,
                MotionEvent.ACTION_DOWN, startX, startY);
        for (int step = 1; step <= PIP_DRAG_STEPS && accepted; step++) {
            SystemClock.sleep(PIP_DRAG_STEP_INTERVAL_MS);
            float fraction = step / (float) PIP_DRAG_STEPS;
            float x = startX + (endX - startX) * fraction;
            float y = startY + (endY - startY) * fraction;
            accepted = injectPipDragEvent(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, x, y);
        }
        SystemClock.sleep(PIP_DRAG_RELEASE_SETTLE_MS);
        boolean released = injectPipDragEvent(downTime, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, endX, endY);
        if (accepted && released) {
            Log.i(TAG, "Moved native PiP task=" + state.taskId
                    + " from=" + state.bounds.toShortString()
                    + " toward=" + targetBounds.toShortString());
        }
        return accepted && released;
    }

    private boolean injectPipDragEvent(long downTime, long eventTime, int action,
                                       float x, float y)
            throws ReflectiveOperationException {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            getSetDisplayIdMethod().invoke(event, Display.DEFAULT_DISPLAY);
            Object result = injectInputEventMethod.invoke(
                    inputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC);
            return !Boolean.FALSE.equals(result);
        } finally {
            event.recycle();
        }
    }

    private synchronized Method getGetRootTaskInfoMethod()
            throws ReflectiveOperationException {
        if (getRootTaskInfoMethod == null) {
            getRootTaskInfoMethod = getActivityTaskManagerService().getClass().getMethod(
                    "getRootTaskInfo", int.class, int.class);
            getRootTaskInfoMethod.setAccessible(true);
        }
        return getRootTaskInfoMethod;
    }

    private void watchPipDockLease() {
        while (true) {
            SystemClock.sleep(PIP_DOCK_WATCHDOG_INTERVAL_MS);
            int taskId;
            Rect restoreBounds;
            synchronized (pipDockLock) {
                if (dockedPipTaskId <= 0 || dockedPipRestoreBounds == null
                        || SystemClock.uptimeMillis() - pipDockLeaseUptime
                        <= PIP_DOCK_LEASE_TIMEOUT_MS) {
                    continue;
                }
                taskId = dockedPipTaskId;
                restoreBounds = new Rect(dockedPipRestoreBounds);
                pipDockLeaseUptime = SystemClock.uptimeMillis();
            }
            try {
                PinnedTaskState state = getPinnedTaskState();
                boolean restored = state == null || state.taskId != taskId
                        || movePinnedTask(state, restoreBounds);
                if (restored) {
                    synchronized (pipDockLock) {
                        if (dockedPipTaskId == taskId) {
                            clearPipDockLeaseLocked();
                        }
                    }
                    Log.i(TAG, "Restored expired PiP dock lease task=" + taskId);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                throwIfSystemServiceDead(e);
                Log.w(TAG, "Restore expired PiP dock lease failed: "
                        + describeThrowable(e));
            }
        }
    }

    private void clearStalePipDockLease(int currentTaskId) {
        synchronized (pipDockLock) {
            if (dockedPipTaskId > 0 && dockedPipTaskId != currentTaskId) {
                clearPipDockLeaseLocked();
            }
        }
    }

    private void clearPipDockLeaseLocked() {
        dockedPipTaskId = -1;
        dockedPipRestoreBounds = null;
        pipDockLeaseUptime = 0L;
    }

    private static final class PinnedTaskState {
        final int taskId;
        final Rect bounds;

        PinnedTaskState(int taskId, Rect bounds) {
            this.taskId = taskId;
            this.bounds = new Rect(bounds);
        }
    }

    private String setDisplayImePolicy(String[] parts) {
        int displayId;
        int requestedPolicy;
        try {
            displayId = Integer.parseInt(parts[1]);
            requestedPolicy = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            Log.w(TAG, "invalid IME policy command");
            return "imePolicy -1 -1 -1 false";
        }
        int actualPolicy = -1;
        boolean success = false;
        boolean changed = false;
        String failure = "";
        try {
            try {
                Object actual = getGetDisplayImePolicyMethod().invoke(
                        getWindowManagerService(), displayId);
                if (actual instanceof Integer) {
                    actualPolicy = (Integer) actual;
                }
                if (actualPolicy != requestedPolicy) {
                    getSetDisplayImePolicyMethod().invoke(
                            getWindowManagerService(), displayId, requestedPolicy);
                    changed = true;
                    actual = getGetDisplayImePolicyMethod().invoke(
                            getWindowManagerService(), displayId);
                    if (actual instanceof Integer) {
                        actualPolicy = (Integer) actual;
                    }
                }
            } catch (NoSuchMethodException e) {
                // Android 10 exposes the older boolean form. false means the IME falls back to
                // the default display instead of being hosted by this virtual display.
                boolean shouldShowLocally = requestedPolicy == 0;
                Object actual = getShouldShowImeMethod().invoke(
                        getWindowManagerService(), displayId);
                if (actual instanceof Boolean) {
                    actualPolicy = (Boolean) actual ? 0 : requestedPolicy;
                }
                if (actualPolicy != requestedPolicy) {
                    getSetShouldShowImeMethod().invoke(
                            getWindowManagerService(), displayId, shouldShowLocally);
                    if (displayId != Display.DEFAULT_DISPLAY) {
                        getSetShouldShowImeMethod().invoke(
                                getWindowManagerService(), Display.DEFAULT_DISPLAY, true);
                    }
                    changed = true;
                    actual = getShouldShowImeMethod().invoke(
                            getWindowManagerService(), displayId);
                    if (actual instanceof Boolean) {
                        actualPolicy = (Boolean) actual ? 0 : requestedPolicy;
                    }
                }
            }
            success = actualPolicy == requestedPolicy;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        if (changed || !success) {
            Log.i(TAG, "Display IME policy"
                    + " display=" + displayId
                    + " requested=" + requestedPolicy
                    + " actual=" + actualPolicy
                    + " changed=" + changed
                    + " success=" + success
                    + " bridgeUid=" + android.os.Process.myUid()
                    + (failure.length() == 0 ? "" : " failure=" + failure));
        }
        return "imePolicy " + displayId + " " + requestedPolicy + " "
                + actualPolicy + " " + success;
    }

    private String setDisplayRotationAuto(String[] parts) {
        int displayId;
        try {
            displayId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "displayRotationAuto -1 false";
        }
        if (displayId <= Display.DEFAULT_DISPLAY) {
            return "displayRotationAuto " + displayId + " false";
        }
        boolean success = false;
        String failure = "";
        try {
            Object windowManager = getWindowManagerService();
            invokeSetIgnoreOrientationRequest(windowManager, displayId, false);
            invokeSetFixedToUserRotation(windowManager, displayId,
                    getFixedToUserRotationDisabled());
            invokeThawDisplayRotation(windowManager, displayId);
            success = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        Log.println(success ? Log.INFO : Log.WARN, TAG,
                "Content-driven display rotation display=" + displayId
                        + " success=" + success
                        + " bridgeUid=" + android.os.Process.myUid()
                        + (failure.isEmpty() ? "" : " failure=" + failure));
        return "displayRotationAuto " + displayId + " " + success;
    }

    private String setDisplayLandscapeRotation(String[] parts) {
        int displayId;
        int rotation;
        try {
            displayId = Integer.parseInt(parts[1]);
            rotation = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return "displayLandscapeRotation -1 -1 false";
        }
        if (displayId <= Display.DEFAULT_DISPLAY
                || (rotation != Surface.ROTATION_90 && rotation != Surface.ROTATION_270)) {
            return "displayLandscapeRotation " + displayId + " " + rotation + " false";
        }
        boolean success = false;
        String failure = "";
        try {
            Object windowManager = getWindowManagerService();
            invokeSetIgnoreOrientationRequest(windowManager, displayId, false);
            invokeSetFixedToUserRotation(windowManager, displayId,
                    getFixedToUserRotationDisabled());
            invokeFreezeDisplayRotation(windowManager, displayId, rotation);
            success = true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            throwIfSystemServiceDead(e);
            failure = describeThrowable(e);
        }
        Log.println(success ? Log.INFO : Log.WARN, TAG,
                "Landscape sensor rotation display=" + displayId
                        + " rotation=" + rotation
                        + " success=" + success
                        + " bridgeUid=" + android.os.Process.myUid()
                        + (failure.isEmpty() ? "" : " failure=" + failure));
        return "displayLandscapeRotation " + displayId + " " + rotation + " " + success;
    }

    private void invokeThawDisplayRotation(Object windowManager, int displayId)
            throws ReflectiveOperationException {
        Method method = getThawDisplayRotationMethod();
        if (method.getParameterTypes().length == 2) {
            method.invoke(windowManager, displayId, "OneStepRootInputBridge");
        } else {
            method.invoke(windowManager, displayId);
        }
    }

    private void invokeFreezeDisplayRotation(Object windowManager, int displayId, int rotation)
            throws ReflectiveOperationException {
        Method method = getFreezeDisplayRotationMethod();
        if (method.getParameterTypes().length == 3) {
            method.invoke(windowManager, displayId, rotation, "OneStepRootInputBridge");
        } else {
            method.invoke(windowManager, displayId, rotation);
        }
    }

    private void invokeSetFixedToUserRotation(Object windowManager, int displayId, int mode)
            throws ReflectiveOperationException {
        Method method = getSetFixedToUserRotationMethod();
        if (method != null) {
            method.invoke(windowManager, displayId, mode);
        }
    }

    private void invokeSetIgnoreOrientationRequest(Object windowManager, int displayId,
                                                   boolean ignore)
            throws ReflectiveOperationException {
        Method method = getSetIgnoreOrientationRequestMethod();
        if (method != null) {
            method.invoke(windowManager, displayId, ignore);
        }
    }

    private synchronized Object getWindowManagerService() throws ReflectiveOperationException {
        if (windowManagerService == null) {
            Class<?> windowManagerGlobalClass = Class.forName(
                    "android.view.WindowManagerGlobal");
            Method getServiceMethod = windowManagerGlobalClass.getDeclaredMethod(
                    "getWindowManagerService");
            getServiceMethod.setAccessible(true);
            windowManagerService = getServiceMethod.invoke(null);
        }
        return windowManagerService;
    }

    private synchronized Method getSetDisplayImePolicyMethod()
            throws ReflectiveOperationException {
        if (setDisplayImePolicyMethod == null) {
            setDisplayImePolicyMethod = getWindowManagerService().getClass().getMethod(
                    "setDisplayImePolicy", int.class, int.class);
            setDisplayImePolicyMethod.setAccessible(true);
        }
        return setDisplayImePolicyMethod;
    }

    private synchronized Method getGetDisplayImePolicyMethod()
            throws ReflectiveOperationException {
        if (getDisplayImePolicyMethod == null) {
            getDisplayImePolicyMethod = getWindowManagerService().getClass().getMethod(
                    "getDisplayImePolicy", int.class);
            getDisplayImePolicyMethod.setAccessible(true);
        }
        return getDisplayImePolicyMethod;
    }

    private synchronized Method getSetShouldShowImeMethod()
            throws ReflectiveOperationException {
        if (setShouldShowImeMethod == null) {
            setShouldShowImeMethod = getWindowManagerService().getClass().getMethod(
                    "setShouldShowIme", int.class, boolean.class);
            setShouldShowImeMethod.setAccessible(true);
        }
        return setShouldShowImeMethod;
    }

    private synchronized Method getShouldShowImeMethod()
            throws ReflectiveOperationException {
        if (shouldShowImeMethod == null) {
            shouldShowImeMethod = getWindowManagerService().getClass().getMethod(
                    "shouldShowIme", int.class);
            shouldShowImeMethod.setAccessible(true);
        }
        return shouldShowImeMethod;
    }

    private synchronized Method getThawDisplayRotationMethod()
            throws NoSuchMethodException, ReflectiveOperationException {
        if (thawDisplayRotationMethod == null) {
            thawDisplayRotationMethod = findMethod(
                    getWindowManagerService().getClass(), "thawDisplayRotation",
                    new Class<?>[]{int.class, String.class},
                    new Class<?>[]{int.class});
        }
        return thawDisplayRotationMethod;
    }

    private synchronized Method getFreezeDisplayRotationMethod()
            throws NoSuchMethodException, ReflectiveOperationException {
        if (freezeDisplayRotationMethod == null) {
            freezeDisplayRotationMethod = findMethod(
                    getWindowManagerService().getClass(), "freezeDisplayRotation",
                    new Class<?>[]{int.class, int.class, String.class},
                    new Class<?>[]{int.class, int.class});
        }
        return freezeDisplayRotationMethod;
    }

    private synchronized Method getSetFixedToUserRotationMethod()
            throws ReflectiveOperationException {
        if (setFixedToUserRotationMethod == null) {
            setFixedToUserRotationMethod = findOptionalMethod(
                    getWindowManagerService().getClass(), "setFixedToUserRotation",
                    int.class, int.class);
        }
        return setFixedToUserRotationMethod;
    }

    private synchronized Method getSetIgnoreOrientationRequestMethod()
            throws ReflectiveOperationException {
        if (setIgnoreOrientationRequestMethod == null) {
            setIgnoreOrientationRequestMethod = findOptionalMethod(
                    getWindowManagerService().getClass(), "setIgnoreOrientationRequest",
                    int.class, boolean.class);
        }
        return setIgnoreOrientationRequestMethod;
    }

    private int getFixedToUserRotationDisabled() {
        return getFixedToUserRotationMode("FIXED_TO_USER_ROTATION_DISABLED", 1);
    }

    private int getFixedToUserRotationMode(String fieldName, int fallback) {
        try {
            Class<?> interfaceClass = Class.forName("android.view.IWindowManager");
            return interfaceClass.getField(fieldName).getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private Method findMethod(Class<?> type, String name, Class<?>[]... signatures)
            throws NoSuchMethodException {
        for (Class<?>[] signature : signatures) {
            Method method = findOptionalMethod(type, name, signature);
            if (method != null) {
                return method;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private Method findOptionalMethod(Class<?> type, String name, Class<?>... signature) {
        try {
            Method method = type.getMethod(name, signature);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = type.getDeclaredMethod(name, signature);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignoredAgain) {
                return null;
            }
        }
    }

    private void injectKey(String[] parts) throws ReflectiveOperationException {
        int displayId = Integer.parseInt(parts[1]);
        int keyCode = Integer.parseInt(parts[2]);
        int source = InputDevice.SOURCE_KEYBOARD;
        int deviceId = getInputDeviceId(source);
        long now = SystemClock.uptimeMillis();
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
                0, deviceId, 0, 0, source), displayId);
        injectKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0,
                0, deviceId, 0, 0, source), displayId);
    }

    private void injectKeyEvent(KeyEvent event, int displayId)
            throws ReflectiveOperationException {
        getSetDisplayIdMethod().invoke(event, displayId);
        event.setSource(InputDevice.SOURCE_KEYBOARD);
        RootVirtualDisplayBridge.noteVirtualInput(displayId);
        Object result = injectInputEventMethod.invoke(inputManager, event,
                INJECT_INPUT_EVENT_MODE_ASYNC);
        if (Boolean.FALSE.equals(result)) {
            Log.w(TAG, "key inject returned false display=" + displayId
                    + " keyCode=" + event.getKeyCode());
        }
    }

    private Method getSetDisplayIdMethod() throws ReflectiveOperationException {
        if (setDisplayIdMethod == null) {
            setDisplayIdMethod = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
            setDisplayIdMethod.setAccessible(true);
        }
        return setDisplayIdMethod;
    }

    private int getInputDeviceId(int source) {
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int deviceId : deviceIds) {
            InputDevice device = InputDevice.getDevice(deviceId);
            if (device != null && device.supportsSource(source)) {
                return deviceId;
            }
        }
        return 0;
    }

    private String motionActionName(int action) {
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(action & MotionEvent.ACTION_MASK);
        }
    }

    private void logVirtualMotionReceived(long traceId, int displayId, String actionName,
                                          long downTime, long eventTime, float x, float y,
                                          float pressure, int pointerCount) {
        if (!"DOWN".equals(actionName) && !"UP".equals(actionName)
                && !"POINTER_DOWN".equals(actionName)
                && !"POINTER_UP".equals(actionName)
                && !"CANCEL".equals(actionName)) {
            return;
        }
        long receiveWallTime = System.currentTimeMillis();
        long eventWallTime = toWallTimeMillis(eventTime);
        if ("DOWN".equals(actionName)) {
            Log.i(TAG, "TouchTrace virtual-received action=" + actionName
                    + " traceId=" + traceId
                    + " display=" + displayId
                    + " downTimestamp=" + formatTimestamp(eventWallTime)
                    + " receiveTimestamp=" + formatTimestamp(receiveWallTime)
                    + " eventUptimeMs=" + eventTime
                    + " pointers=" + pointerCount
                    + " x=" + formatFloat(x)
                    + " y=" + formatFloat(y)
                    + " pressure=" + formatFloat(pressure));
            return;
        }
        Log.i(TAG, "TouchTrace virtual-received action=" + actionName
                + " traceId=" + traceId
                + " display=" + displayId
                + " downTimestamp=" + formatTimestamp(toWallTimeMillis(downTime))
                + " upTimestamp=" + formatTimestamp(eventWallTime)
                + " receiveTimestamp=" + formatTimestamp(receiveWallTime)
                + " durationMs=" + Math.max(0L, eventTime - downTime)
                + " eventUptimeMs=" + eventTime
                + " pointers=" + pointerCount
                + " x=" + formatFloat(x)
                + " y=" + formatFloat(y)
                + " pressure=" + formatFloat(pressure));
    }

    private void logVirtualDispatchFinished(long traceId, int displayId, String actionName,
                                            long startWallTime, boolean accepted,
                                            int injectMode, int deviceId, float pressure) {
        if (!"DOWN".equals(actionName) && !"UP".equals(actionName)
                && !"POINTER_DOWN".equals(actionName)
                && !"POINTER_UP".equals(actionName)
                && !"CANCEL".equals(actionName)) {
            return;
        }
        long finishWallTime = System.currentTimeMillis();
        Log.i(TAG, "TouchTrace virtual-dispatched motion " + actionName
                + " display=" + displayId
                + " traceId=" + traceId
                + " mode=" + injectMode
                + " deviceId=" + deviceId
                + " pressure=" + formatFloat(pressure)
                + " accepted=" + accepted
                + " startTimestamp=" + formatTimestamp(startWallTime)
                + " finishTimestamp=" + formatTimestamp(finishWallTime)
                + " costMs=" + Math.max(0L, finishWallTime - startWallTime));
    }

    private long toWallTimeMillis(long uptimeMillis) {
        long nowWallTime = System.currentTimeMillis();
        long nowUptime = SystemClock.uptimeMillis();
        return nowWallTime - Math.max(0L, nowUptime - uptimeMillis);
    }

    private String formatTimestamp(long wallTimeMillis) {
        return String.format(Locale.US, "%tF %<tT.%<tL", wallTimeMillis);
    }

    private String formatFloat(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private void logFailure(String description, String reason) {
        long now = SystemClock.uptimeMillis();
        if (now - lastFailureLogUptime < FAILURE_LOG_THROTTLE_MS) {
            return;
        }
        lastFailureLogUptime = now;
        Log.w(TAG, "direct input failed: " + description + " " + reason);
    }

    private static void throwIfSystemServiceDead(Throwable throwable) {
        if (SystemServiceFailurePolicy.isStaleSystemService(throwable)) {
            throw new StaleSystemServiceException(throwable);
        }
    }

    private static final class StaleSystemServiceException extends RuntimeException {
        StaleSystemServiceException(Throwable cause) {
            super(cause);
        }
    }

    private String describeThrowable(Throwable throwable) {
        Throwable cause = throwable instanceof InvocationTargetException
                && throwable.getCause() != null ? throwable.getCause() : throwable;
        String message = cause.getMessage();
        if (message == null || message.length() == 0) {
            return cause.getClass().getSimpleName();
        }
        return cause.getClass().getSimpleName() + ": " + message;
    }
}
