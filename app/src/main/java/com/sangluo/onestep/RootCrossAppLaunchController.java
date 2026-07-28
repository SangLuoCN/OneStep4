package com.sangluo.onestep;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Intercepts cross-package starts while a OneStep virtual display owns focus. */
final class RootCrossAppLaunchController extends Binder {
    private static final String TAG = "OneStepLaunchRouter";
    private static final String DESCRIPTOR = "android.app.IActivityController";

    private final RootVirtualDisplayBridge displayBridge;
    private final int activityStartingTransaction;
    private final int activityResumingTransaction;
    private final int appCrashedTransaction;
    private final int appEarlyNotRespondingTransaction;
    private final int appNotRespondingTransaction;
    private final int systemNotRespondingTransaction;

    private RootCrossAppLaunchController(RootVirtualDisplayBridge displayBridge,
                                         Class<?> controllerStubClass)
            throws ReflectiveOperationException {
        this.displayBridge = displayBridge;
        activityStartingTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_activityStarting");
        activityResumingTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_activityResuming");
        appCrashedTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_appCrashed");
        appEarlyNotRespondingTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_appEarlyNotResponding");
        appNotRespondingTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_appNotResponding");
        systemNotRespondingTransaction = readTransaction(
                controllerStubClass, "TRANSACTION_systemNotResponding");
        attachInterface(null, DESCRIPTOR);
    }

    static RootCrossAppLaunchController install(RootVirtualDisplayBridge displayBridge)
            throws ReflectiveOperationException {
        Class<?> activityTaskManagerClass = Class.forName("android.app.ActivityTaskManager");
        Method getService = activityTaskManagerClass.getDeclaredMethod("getService");
        getService.setAccessible(true);
        Object activityTaskManager = getService.invoke(null);
        if (activityTaskManager == null) {
            throw new IllegalStateException("activity task manager unavailable");
        }

        Class<?> controllerInterface = Class.forName("android.app.IActivityController");
        Class<?> controllerStubClass = Class.forName("android.app.IActivityController$Stub");
        RootCrossAppLaunchController controller = new RootCrossAppLaunchController(
                displayBridge, controllerStubClass);
        Object controllerProxy = Proxy.newProxyInstance(
                controllerInterface.getClassLoader(),
                new Class<?>[] {controllerInterface},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("asBinder".equals(name)) {
                        return controller;
                    }
                    if ("toString".equals(name)) {
                        return "OneStepRootCrossAppLaunchController";
                    }
                    if ("hashCode".equals(name)) {
                        return System.identityHashCode(proxy);
                    }
                    if ("equals".equals(name)) {
                        return proxy == (args == null ? null : args[0]);
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return true;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    return null;
                });
        Method setActivityController = activityTaskManager.getClass().getMethod(
                "setActivityController", controllerInterface, boolean.class);
        setActivityController.setAccessible(true);
        setActivityController.invoke(activityTaskManager, controllerProxy, false);
        Log.i(TAG, "installed root activity launch controller");
        return controller;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        data.enforceInterface(DESCRIPTOR);
        if (code == activityStartingTransaction) {
            Intent intent = data.readInt() == 0 ? null : Intent.CREATOR.createFromParcel(data);
            String targetPackage = data.readString();
            boolean allow = shouldAllowStart(intent, targetPackage);
            reply.writeNoException();
            reply.writeInt(allow ? 1 : 0);
            return true;
        }
        if (code == activityResumingTransaction) {
            data.readString();
            reply.writeNoException();
            reply.writeInt(1);
            return true;
        }
        if (code == appCrashedTransaction) {
            data.readString();
            data.readInt();
            data.readString();
            data.readString();
            data.readLong();
            data.readString();
            reply.writeNoException();
            reply.writeInt(0);
            return true;
        }
        if (code == appEarlyNotRespondingTransaction
                || code == appNotRespondingTransaction) {
            data.readString();
            data.readInt();
            data.readString();
            reply.writeNoException();
            reply.writeInt(0);
            return true;
        }
        if (code == systemNotRespondingTransaction) {
            data.readString();
            reply.writeNoException();
            reply.writeInt(0);
            return true;
        }
        return super.onTransact(code, data, reply, flags);
    }

    private boolean shouldAllowStart(Intent intent, String targetPackage) {
        if (intent == null || targetPackage == null || targetPackage.isEmpty()
                || displayBridge.consumeLaunchBypass(targetPackage)) {
            return true;
        }
        try {
            RootVirtualDisplayBridge.RoutingSource source =
                    displayBridge.getArmedRoutingSource(targetPackage);
            if (source == null) {
                return true;
            }
            boolean routed = displayBridge.routeCrossAppLaunch(
                    source.displayId, source.packageName, new Intent(intent), targetPackage);
            if (routed) {
                Log.i(TAG, "intercepted cross-app launch: source=" + source.packageName
                        + " display=" + source.displayId + " target=" + targetPackage
                        + " action=" + intent.getAction()
                        + " component=" + intent.getComponent());
            }
            return !routed;
        } catch (RuntimeException e) {
            Log.w(TAG, "cross-app launch inspection failed: "
                    + e.getClass().getSimpleName());
            return true;
        }
    }

    private static int readTransaction(Class<?> stubClass, String fieldName)
            throws ReflectiveOperationException {
        Field field = stubClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

}
