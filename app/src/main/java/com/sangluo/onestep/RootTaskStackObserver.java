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
    private final int taskMovedToFrontTransaction;
    private final int taskRemovalStartedTransaction;
    @SuppressWarnings("FieldCanBeLocal")
    private final Object listenerProxy;

    private RootTaskStackObserver(RootVirtualDisplayBridge displayBridge,
                                  Class<?> listenerInterface, Class<?> listenerStub)
            throws ReflectiveOperationException {
        this.displayBridge = displayBridge;
        taskMovedToFrontTransaction = readTransaction(
                listenerStub, "TRANSACTION_onTaskMovedToFront");
        taskRemovalStartedTransaction = readTransaction(
                listenerStub, "TRANSACTION_onTaskRemovalStarted");
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
        Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
        Method getService = activityTaskManagerClass.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object activityTaskManager = getService.invoke(null);
        if (activityTaskManager == null) {
            throw new IllegalStateException("activity task manager unavailable");
        }

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
        if (code == taskMovedToFrontTransaction) {
            notifyTaskEvent(RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT,
                    readTaskInfo(data));
            return true;
        }
        if (code == taskRemovalStartedTransaction) {
            notifyTaskEvent(RootVirtualDisplayBridge.TASK_EVENT_REMOVAL_STARTED,
                    readTaskInfo(data));
            return true;
        }
        return true;
    }

    private void notifyTaskEvent(int event, ActivityManager.RunningTaskInfo taskInfo) {
        if (taskInfo == null) {
            return;
        }
        int displayId = readDisplayId(taskInfo);
        ComponentName topActivity = taskInfo.topActivity;
        String packageName = topActivity == null ? "" : topActivity.getPackageName();
        displayBridge.notifyTaskEvent(event, displayId, taskInfo.taskId, packageName);
    }

    private static ActivityManager.RunningTaskInfo readTaskInfo(Parcel data) {
        return data.readInt() == 0
                ? null : ActivityManager.RunningTaskInfo.CREATOR.createFromParcel(data);
    }

    private static int readDisplayId(ActivityManager.RunningTaskInfo taskInfo) {
        try {
            Field field = taskInfo.getClass().getField("displayId");
            return field.getInt(taskInfo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static int readTransaction(Class<?> stubClass, String fieldName)
            throws ReflectiveOperationException {
        Field field = stubClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }
}
