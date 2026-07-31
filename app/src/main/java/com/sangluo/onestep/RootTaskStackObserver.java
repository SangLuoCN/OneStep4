package com.sangluo.onestep;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Reports task changes with their real virtual-display IDs from the root process. */
final class RootTaskStackObserver extends Binder {
    private static final String TAG = "OneStepTaskObserver";
    private static final String DESCRIPTOR = "android.app.ITaskStackListener";

    private final RootVirtualDisplayBridge displayBridge;
    private final int taskStackChangedTransaction;
    private final int taskMovedToFrontTransaction;
    private final int taskRemovalStartedTransaction;
    private final boolean movedToFrontUsesTaskInfo;
    private final boolean removalStartedUsesTaskInfo;
    @SuppressWarnings("FieldCanBeLocal")
    private final Object listenerProxy;

    private RootTaskStackObserver(RootVirtualDisplayBridge displayBridge,
                                  Class<?> listenerInterface, Class<?> listenerStub)
            throws ReflectiveOperationException {
        this.displayBridge = displayBridge;
        taskStackChangedTransaction = readOptionalTransaction(
                listenerStub, "TRANSACTION_onTaskStackChanged");
        Method movedToFront = findListenerMethod(listenerInterface, "onTaskMovedToFront");
        Method removalStarted = findListenerMethod(listenerInterface, "onTaskRemovalStarted");
        taskMovedToFrontTransaction = movedToFront == null ? -1 : readOptionalTransaction(
                listenerStub, "TRANSACTION_onTaskMovedToFront");
        taskRemovalStartedTransaction = removalStarted == null ? -1 : readOptionalTransaction(
                listenerStub, "TRANSACTION_onTaskRemovalStarted");
        movedToFrontUsesTaskInfo = usesTaskInfoPayload(movedToFront);
        removalStartedUsesTaskInfo = usesTaskInfoPayload(removalStarted);
        attachInterface(null, DESCRIPTOR);
        listenerProxy = Proxy.newProxyInstance(
                listenerInterface.getClassLoader(), new Class<?>[] {listenerInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("asBinder".equals(name)) {
                        return this;
                    }
                    if ("toString".equals(name)) {
                        return "OneStepRootTaskStackObserver";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == (args == null ? null : args[0]);
                    }
                    return null;
                });
    }

    static RootTaskStackObserver install(RootVirtualDisplayBridge displayBridge)
            throws ReflectiveOperationException {
        Object activityTaskManager = RootActivityManagerCompat.getTaskService();
        Class<?> listenerInterface = Class.forName("android.app.ITaskStackListener");
        Class<?> listenerStub = Class.forName("android.app.ITaskStackListener$Stub");
        RootTaskStackObserver observer = new RootTaskStackObserver(
                displayBridge, listenerInterface, listenerStub);
        Method register = activityTaskManager.getClass().getMethod(
                "registerTaskStackListener", listenerInterface);
        register.setAccessible(true);
        register.invoke(activityTaskManager, observer.listenerProxy);
        Log.i(TAG, "installed root task stack observer");
        return observer;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            if (reply != null) {
                reply.writeString(DESCRIPTOR);
            }
            return true;
        }
        data.enforceInterface(DESCRIPTOR);
        if (code == taskStackChangedTransaction) {
            displayBridge.notifyTaskEvent(
                    RootVirtualDisplayBridge.TASK_EVENT_STACK_CHANGED, -1, -1, "");
            return true;
        }
        if (code == taskMovedToFrontTransaction) {
            readAndNotifyTaskEvent(RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT,
                    movedToFrontUsesTaskInfo, data);
            return true;
        }
        if (code == taskRemovalStartedTransaction) {
            readAndNotifyTaskEvent(RootVirtualDisplayBridge.TASK_EVENT_REMOVAL_STARTED,
                    removalStartedUsesTaskInfo, data);
            return true;
        }
        return true;
    }

    private void readAndNotifyTaskEvent(int event, boolean usesTaskInfo, Parcel data) {
        if (!usesTaskInfo) {
            displayBridge.notifyTaskEvent(event, -1, data.readInt(), "");
            return;
        }
        notifyTaskEvent(event, readTaskInfo(data));
    }

    private void notifyTaskEvent(int event, ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return;
        }
        int displayId = readDisplayId(taskInfo);
        ComponentName topActivity = taskInfo.topActivity;
        String packageName = topActivity == null ? "" : topActivity.getPackageName();
        displayBridge.notifyTaskEvent(
                event, displayId, readTaskId(taskInfo), packageName);
    }

    private static ActivityManager.RunningTaskInfo readTaskInfo(Parcel data) {
        return data.readInt() == 0
                ? null : ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data);
    }

    private static int readDisplayId(ActivityManager.RunningTaskInfo taskInfo) {
        return readIntField(taskInfo, "displayId", -1);
    }

    private static int readTaskId(ActivityManager.RunningTaskInfo taskInfo) {
        int taskId = readIntField(taskInfo, "taskId", -1);
        return taskId > 0 ? taskId : readIntField(taskInfo, "id", -1);
    }

    private static int readIntField(Object target, String name, int fallback) {
        try {
            Field field = target.getClass().getField(name);
            return field.getInt(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }

    private static Method findListenerMethod(Class<?> listenerInterface, String name) {
        for (Method method : listenerInterface.getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1) {
                return method;
            }
        }
        return null;
    }

    static boolean usesTaskInfoPayload(Method method) {
        return method != null && method.getParameterTypes()[0] != int.class;
    }

    private static int readOptionalTransaction(Class<?> stubClass, String fieldName) {
        try {
            Field field = stubClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }
}
