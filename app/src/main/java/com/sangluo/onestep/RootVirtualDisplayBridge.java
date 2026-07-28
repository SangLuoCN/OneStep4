package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Owns secure virtual displays inside the root app_process bridge. */
public final class RootVirtualDisplayBridge extends Binder {
    private static final String TAG = "OneStepDisplayBridge";

    public static final String DESCRIPTOR = "com.sangluo.onestep.IRootVirtualDisplayBridge";
    public static final String LAUNCH_CALLBACK_DESCRIPTOR =
            "com.sangluo.onestep.ICrossAppLaunchCallback";
    public static final int LAUNCH_CALLBACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
    public static final String SERVICE_NAME_PREFIX = "onestep_secure_display_";
    public static final int TRANSACTION_CREATE = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TRANSACTION_RESIZE = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION + 2;
    public static final int TRANSACTION_RELEASE = IBinder.FIRST_CALL_TRANSACTION + 3;
    public static final int TRANSACTION_PING = IBinder.FIRST_CALL_TRANSACTION + 4;
    public static final int TRANSACTION_REGISTER_LAUNCH_CALLBACK =
            IBinder.FIRST_CALL_TRANSACTION + 5;
    public static final int TRANSACTION_ALLOW_NEXT_LAUNCH =
            IBinder.FIRST_CALL_TRANSACTION + 6;
    public static final int TRANSACTION_UPDATE_LAUNCH_SOURCE =
            IBinder.FIRST_CALL_TRANSACTION + 7;
    public static final int TRANSACTION_CREATE_DISPLAY_MIRROR =
            IBinder.FIRST_CALL_TRANSACTION + 8;
    public static final int TRANSACTION_SET_SKIP_SCREENSHOT =
            IBinder.FIRST_CALL_TRANSACTION + 9;
    public static final int TRANSACTION_SET_DROP_INPUT_MODE =
            IBinder.FIRST_CALL_TRANSACTION + 10;

    private static final int ROOT_UID = 0;
    private static final long LAUNCH_BYPASS_TIMEOUT_MS = 3000L;
    private static final long ROUTING_INPUT_ARM_TIMEOUT_MS = 5000L;
    private static volatile RootVirtualDisplayBridge publishedBridge;

    private final int allowedUid;
    private final String bridgeToken;
    private final DisplayManager displayManager;
    private final Map<Integer, DisplayRecord> displays = new HashMap<>();
    private final Object launchRoutingLock = new Object();
    private final Map<String, LaunchBypass> launchBypasses = new HashMap<>();
    private LaunchCallbackRecord launchCallback;
    private int routingSourceDisplayId = Display.DEFAULT_DISPLAY;
    private String routingSourcePackage = "";
    private int lastInputDisplayId = Display.DEFAULT_DISPLAY;
    private long lastInputUptime;
    @SuppressWarnings("FieldCanBeLocal")
    private RootCrossAppLaunchController launchController;

    private RootVirtualDisplayBridge(Context context, int allowedUid, String bridgeToken) {
        this.allowedUid = allowedUid;
        this.bridgeToken = bridgeToken;
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        attachInterface(null, DESCRIPTOR);
    }

    public static RootVirtualDisplayBridge publish(Context context, int allowedUid,
                                                   String bridgeToken)
            throws ReflectiveOperationException {
        if (android.os.Process.myUid() != ROOT_UID) {
            throw new SecurityException("secure display bridge requires uid 0");
        }
        RootVirtualDisplayBridge bridge = new RootVirtualDisplayBridge(
                context, allowedUid, bridgeToken);
        Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
        Method addService;
        try {
            addService = serviceManagerClass.getDeclaredMethod(
                    "addService", String.class, IBinder.class, boolean.class, int.class);
            addService.setAccessible(true);
            addService.invoke(null, serviceName(allowedUid), bridge, false, 0);
        } catch (NoSuchMethodException ignored) {
            addService = serviceManagerClass.getDeclaredMethod(
                    "addService", String.class, IBinder.class);
            addService.setAccessible(true);
            addService.invoke(null, serviceName(allowedUid), bridge);
        }
        Log.i(TAG, "published service=" + serviceName(allowedUid)
                + " uid=" + android.os.Process.myUid());
        publishedBridge = bridge;
        try {
            bridge.launchController = RootCrossAppLaunchController.install(bridge);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, "cross-app launch controller unavailable", e);
        }
        return bridge;
    }

    private static String serviceName(int uid) {
        return SERVICE_NAME_PREFIX + uid;
    }

    @Override
    protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        if (code == INTERFACE_TRANSACTION) {
            reply.writeString(DESCRIPTOR);
            return true;
        }
        data.enforceInterface(DESCRIPTOR);
        enforceCaller(data.readString());
        try {
            switch (code) {
                case TRANSACTION_CREATE:
                    handleCreate(data, reply);
                    return true;
                case TRANSACTION_RESIZE:
                    handleResize(data, reply);
                    return true;
                case TRANSACTION_SET_SURFACE:
                    handleSetSurface(data, reply);
                    return true;
                case TRANSACTION_RELEASE:
                    handleRelease(data, reply);
                    return true;
                case TRANSACTION_PING:
                    reply.writeNoException();
                    reply.writeInt(android.os.Process.myUid());
                    return true;
                case TRANSACTION_REGISTER_LAUNCH_CALLBACK:
                    handleRegisterLaunchCallback(data, reply);
                    return true;
                case TRANSACTION_ALLOW_NEXT_LAUNCH:
                    handleAllowNextLaunch(data, reply);
                    return true;
                case TRANSACTION_UPDATE_LAUNCH_SOURCE:
                    handleUpdateLaunchSource(data, reply);
                    return true;
                case TRANSACTION_CREATE_DISPLAY_MIRROR:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        handleCreateDisplayMirror(data, reply);
                    } else {
                        reply.writeNoException();
                        reply.writeInt(0);
                        reply.writeString("display mirroring requires Android 11 or newer");
                    }
                    return true;
                case TRANSACTION_SET_SKIP_SCREENSHOT:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        handleSetSkipScreenshot(data, reply);
                    } else {
                        reply.writeNoException();
                        reply.writeInt(0);
                    }
                    return true;
                case TRANSACTION_SET_DROP_INPUT_MODE:
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        handleSetDropInputMode(data, reply);
                    } else {
                        reply.writeNoException();
                        reply.writeInt(0);
                    }
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "transaction failed code=" + code, e);
            reply.writeException(e);
            return true;
        }
    }

    private void enforceCaller(String requestToken) {
        int callingUid = Binder.getCallingUid();
        if (callingUid != allowedUid && callingUid != ROOT_UID) {
            throw new SecurityException("caller uid not allowed: " + callingUid);
        }
        if (!bridgeToken.equals(requestToken)) {
            throw new SecurityException("bridge token mismatch");
        }
    }

    private void handleCreate(Parcel data, Parcel reply) {
        int slot = data.readInt();
        String name = data.readString();
        int width = data.readInt();
        int height = data.readInt();
        int densityDpi = data.readInt();
        Surface surface = readSurface(data);
        int[] requestedCandidates = data.createIntArray();
        IBinder ownerToken = data.readStrongBinder();

        int displayId = -1;
        int selectedFlags = 0;
        String failure = "no flag candidates";
        synchronized (displays) {
            releaseLocked(slot);
            if (surface != null && surface.isValid() && requestedCandidates != null) {
                for (int requestedFlags : requestedCandidates) {
                    int secureFlags = SecureVirtualDisplayFlags.forRootBridge(requestedFlags);
                    VirtualDisplay candidate = null;
                    try {
                        candidate = displayManager.createVirtualDisplay(
                                name, width, height, densityDpi, surface, secureFlags);
                        Display display = candidate == null ? null : candidate.getDisplay();
                        if (display == null) {
                            failure = "create returned no display for flags=" + secureFlags;
                            if (candidate != null) {
                                candidate.release();
                            }
                            continue;
                        }
                        DisplayRecord record = new DisplayRecord(
                                slot, candidate, surface, ownerToken);
                        record.linkToOwnerDeath();
                        displays.put(slot, record);
                        displayId = display.getDisplayId();
                        selectedFlags = secureFlags;
                        launchDisplayAccessActivity(displayId);
                        failure = "";
                        break;
                    } catch (RuntimeException e) {
                        displayId = -1;
                        selectedFlags = 0;
                        failure = "flags=" + secureFlags + ":"
                                + e.getClass().getSimpleName() + ":" + e.getMessage();
                        Log.w(TAG, "candidate failed slot=" + slot
                                + " flags=0x" + Integer.toHexString(secureFlags), e);
                        DisplayRecord record = displays.get(slot);
                        if (record != null && record.display == candidate) {
                            releaseLocked(slot);
                            candidate = null;
                        }
                        if (candidate != null) {
                            candidate.release();
                        }
                    }
                }
            }
        }
        if (displayId <= Display.DEFAULT_DISPLAY) {
            if (surface != null) {
                surface.release();
            }
            Log.e(TAG, "create failed slot=" + slot + " reason=" + failure);
        } else {
            Log.i(TAG, "created slot=" + slot + " display=" + displayId
                    + " flags=0x" + Integer.toHexString(selectedFlags));
        }
        reply.writeNoException();
        reply.writeInt(displayId);
        reply.writeInt(selectedFlags);
        reply.writeString(failure);
    }

    private void handleResize(Parcel data, Parcel reply) {
        int slot = data.readInt();
        int width = data.readInt();
        int height = data.readInt();
        int densityDpi = data.readInt();
        boolean success = false;
        synchronized (displays) {
            DisplayRecord record = displays.get(slot);
            if (record != null) {
                record.display.resize(width, height, densityDpi);
                success = true;
            }
        }
        reply.writeNoException();
        reply.writeInt(success ? 1 : 0);
    }

    private void launchDisplayAccessActivity(int displayId) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    "/system/bin/am", "start",
                    "--user", "0",
                    "--display", String.valueOf(displayId),
                    "-a", "android.intent.action.MAIN",
                    "-n", "com.sangluo.onestep/.SecondaryHomeActivity",
                    "--ez", SecondaryHomeActivity.EXTRA_BACKGROUND_ONLY, "true",
                    "-f", String.valueOf(0x10010000))
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                throw new IllegalStateException("display access activity start timed out");
            }
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
            if (process.exitValue() != 0) {
                throw new IllegalStateException("display access activity failed: " + output);
            }
            Log.i(TAG, "launched display access activity on display=" + displayId
                    + " output=" + output);
        } catch (IOException e) {
            throw new IllegalStateException("cannot launch display access activity", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("display access activity start interrupted", e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private void handleSetSurface(Parcel data, Parcel reply) {
        int slot = data.readInt();
        Surface nextSurface = readSurface(data);
        boolean success = false;
        synchronized (displays) {
            DisplayRecord record = displays.get(slot);
            if (record != null) {
                record.setSurface(nextSurface);
                nextSurface = null;
                success = true;
            }
        }
        if (nextSurface != null) {
            nextSurface.release();
        }
        reply.writeNoException();
        reply.writeInt(success ? 1 : 0);
    }

    private void handleRelease(Parcel data, Parcel reply) {
        int slot = data.readInt();
        boolean released;
        synchronized (displays) {
            released = releaseLocked(slot);
        }
        reply.writeNoException();
        reply.writeInt(released ? 1 : 0);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void handleCreateDisplayMirror(Parcel data, Parcel reply) {
        int slot = data.readInt();
        int requestedDisplayId = data.readInt();
        SurfaceControl mirror = null;
        String failure = "display not found";
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            synchronized (displays) {
                DisplayRecord record = displays.get(slot);
                Display display = record == null ? null : record.display.getDisplay();
                if (display != null && display.getDisplayId() == requestedDisplayId) {
                    record.releaseMirror();
                    try {
                        mirror = createDisplayMirror(requestedDisplayId);
                        if (mirror != null && mirror.isValid()) {
                            record.mirror = mirror;
                            failure = "";
                        } else {
                            if (mirror != null) {
                                mirror.release();
                                mirror = null;
                            }
                            failure = "window manager returned no mirror";
                        }
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        failure = e.getClass().getSimpleName()
                                + (e.getMessage() == null ? "" : ":" + e.getMessage());
                        Log.w(TAG, "create display mirror failed slot=" + slot
                                + " display=" + requestedDisplayId, e);
                    }
                }
            }
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
        reply.writeNoException();
        if (mirror == null) {
            reply.writeInt(0);
        } else {
            reply.writeInt(1);
            // Keep the root-process reference alive for the lifetime of the virtual display.
            mirror.writeToParcel(reply, 0);
        }
        reply.writeString(failure);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static SurfaceControl createDisplayMirror(int displayId)
            throws ReflectiveOperationException {
        Class<?> windowManagerGlobalClass = Class.forName("android.view.WindowManagerGlobal");
        Method getWindowManagerService = windowManagerGlobalClass.getDeclaredMethod(
                "getWindowManagerService");
        getWindowManagerService.setAccessible(true);
        Object windowManager = getWindowManagerService.invoke(null);
        if (windowManager == null) {
            throw new IllegalStateException("window manager unavailable");
        }

        Constructor<SurfaceControl> constructor = SurfaceControl.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SurfaceControl mirror = constructor.newInstance();
        try {
            Method mirrorDisplay = windowManager.getClass().getMethod(
                    "mirrorDisplay", int.class, SurfaceControl.class);
            mirrorDisplay.setAccessible(true);
            Object result = mirrorDisplay.invoke(windowManager, displayId, mirror);
            if (!(result instanceof Boolean) || !((Boolean) result)) {
                mirror.release();
                return null;
            }
            return mirror;
        } catch (InvocationTargetException e) {
            mirror.release();
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        } catch (ReflectiveOperationException | RuntimeException e) {
            mirror.release();
            throw e;
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private void handleSetSkipScreenshot(Parcel data, Parcel reply) {
        SurfaceControl target = data.readInt() == 0
                ? null : SurfaceControl.CREATOR.createFromParcel(data);
        boolean skipScreenshot = data.readInt() != 0;
        boolean applied = false;
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            if (target != null && target.isValid()) {
                setSkipScreenshot(target, skipScreenshot);
                applied = true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Set skip-screenshot failed", e);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
            if (target != null) {
                target.release();
            }
        }
        reply.writeNoException();
        reply.writeInt(applied ? 1 : 0);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("BlockedPrivateApi")
    private static void setSkipScreenshot(SurfaceControl target, boolean skipScreenshot)
            throws ReflectiveOperationException {
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setSkipScreenshot", SurfaceControl.class, boolean.class);
            method.setAccessible(true);
            method.invoke(transaction, target, skipScreenshot);
            transaction.apply();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void handleSetDropInputMode(Parcel data, Parcel reply) {
        SurfaceControl target = data.readInt() == 0
                ? null : SurfaceControl.CREATOR.createFromParcel(data);
        boolean dropInput = data.readInt() != 0;
        boolean applied = false;
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            if (target != null && target.isValid()) {
                setDropInputMode(target, dropInput);
                applied = true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Set mirror drop-input mode failed", e);
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
            if (target != null) {
                target.release();
            }
        }
        reply.writeNoException();
        reply.writeInt(applied ? 1 : 0);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("BlockedPrivateApi")
    private static void setDropInputMode(SurfaceControl target, boolean dropInput)
            throws ReflectiveOperationException {
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setDropInputMode", SurfaceControl.class, int.class);
            method.setAccessible(true);
            // android.gui.DropInputMode: NONE=0, ALL=1.
            method.invoke(transaction, target, dropInput ? 1 : 0);
            transaction.apply();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw e;
        }
    }

    private void handleRegisterLaunchCallback(Parcel data, Parcel reply) {
        IBinder callback = data.readStrongBinder();
        boolean registered = false;
        synchronized (launchRoutingLock) {
            clearLaunchCallbackLocked();
            if (callback != null && callback.isBinderAlive()) {
                LaunchCallbackRecord record = new LaunchCallbackRecord(callback);
                record.linkToDeath();
                launchCallback = record;
                registered = true;
            }
        }
        Log.i(TAG, "cross-app launch callback registered=" + registered);
        reply.writeNoException();
        reply.writeInt(registered ? 1 : 0);
    }

    private void handleAllowNextLaunch(Parcel data, Parcel reply) {
        String packageName = data.readString();
        boolean accepted = packageName != null && !packageName.isEmpty();
        if (accepted) {
            synchronized (launchRoutingLock) {
                LaunchBypass bypass = launchBypasses.get(packageName);
                if (bypass == null) {
                    bypass = new LaunchBypass();
                    launchBypasses.put(packageName, bypass);
                }
                bypass.remaining++;
                bypass.expiresAt = SystemClock.uptimeMillis() + LAUNCH_BYPASS_TIMEOUT_MS;
            }
        }
        reply.writeNoException();
        reply.writeInt(accepted ? 1 : 0);
    }

    private void handleUpdateLaunchSource(Parcel data, Parcel reply) {
        int displayId = data.readInt();
        String packageName = data.readString();
        boolean enabled = data.readInt() != 0;
        boolean accepted = !enabled || (displayId > Display.DEFAULT_DISPLAY
                && packageName != null && !packageName.isEmpty());
        if (accepted) {
            synchronized (launchRoutingLock) {
                routingSourceDisplayId = enabled ? displayId : Display.DEFAULT_DISPLAY;
                routingSourcePackage = enabled ? packageName : "";
                if (!enabled) {
                    lastInputDisplayId = Display.DEFAULT_DISPLAY;
                    lastInputUptime = 0L;
                }
            }
        }
        reply.writeNoException();
        reply.writeInt(accepted ? 1 : 0);
    }

    static void noteVirtualInput(int displayId) {
        RootVirtualDisplayBridge bridge = publishedBridge;
        if (bridge == null || displayId <= Display.DEFAULT_DISPLAY) {
            return;
        }
        synchronized (bridge.launchRoutingLock) {
            bridge.lastInputDisplayId = displayId;
            bridge.lastInputUptime = SystemClock.uptimeMillis();
        }
    }

    RoutingSource getArmedRoutingSource(String targetPackage) {
        synchronized (launchRoutingLock) {
            long inputAge = SystemClock.uptimeMillis() - lastInputUptime;
            if (routingSourceDisplayId <= Display.DEFAULT_DISPLAY
                    || routingSourcePackage.isEmpty()
                    || routingSourcePackage.equals(targetPackage)
                    || lastInputDisplayId != routingSourceDisplayId
                    || lastInputUptime <= 0L
                    || inputAge < 0L || inputAge > ROUTING_INPUT_ARM_TIMEOUT_MS) {
                return null;
            }
            return new RoutingSource(routingSourceDisplayId, routingSourcePackage);
        }
    }

    boolean consumeLaunchBypass(String packageName) {
        synchronized (launchRoutingLock) {
            LaunchBypass bypass = launchBypasses.get(packageName);
            if (bypass == null) {
                return false;
            }
            if (bypass.expiresAt < SystemClock.uptimeMillis()) {
                launchBypasses.remove(packageName);
                return false;
            }
            bypass.remaining--;
            if (bypass.remaining <= 0) {
                launchBypasses.remove(packageName);
            }
            return true;
        }
    }

    boolean routeCrossAppLaunch(int sourceDisplayId, String sourcePackage,
                                Intent intent, String targetPackage) {
        if (intent == null || targetPackage == null || targetPackage.isEmpty()
                || "com.sangluo.onestep".equals(targetPackage)) {
            return false;
        }
        boolean knownDisplay = false;
        synchronized (displays) {
            for (DisplayRecord record : displays.values()) {
                Display display = record.display.getDisplay();
                if (display != null && display.getDisplayId() == sourceDisplayId) {
                    knownDisplay = true;
                    break;
                }
            }
        }
        if (!knownDisplay) {
            return false;
        }
        LaunchCallbackRecord callback;
        synchronized (launchRoutingLock) {
            callback = launchCallback;
        }
        boolean routed = callback != null && callback.route(
                sourceDisplayId, sourcePackage, intent, targetPackage);
        if (routed) {
            synchronized (launchRoutingLock) {
                lastInputUptime = 0L;
            }
        }
        return routed;
    }

    private void clearLaunchCallbackLocked() {
        if (launchCallback != null) {
            launchCallback.unlinkToDeath();
            launchCallback = null;
        }
    }

    private boolean releaseLocked(int slot) {
        DisplayRecord record = displays.remove(slot);
        if (record == null) {
            return false;
        }
        record.release();
        Log.i(TAG, "released slot=" + slot);
        return true;
    }

    private static Surface readSurface(Parcel data) {
        return data.readInt() == 0 ? null : Surface.CREATOR.createFromParcel(data);
    }

    private static final class LaunchBypass {
        int remaining;
        long expiresAt;
    }

    static final class RoutingSource {
        final int displayId;
        final String packageName;

        RoutingSource(int displayId, String packageName) {
            this.displayId = displayId;
            this.packageName = packageName;
        }
    }

    private final class LaunchCallbackRecord implements IBinder.DeathRecipient {
        final IBinder callback;

        LaunchCallbackRecord(IBinder callback) {
            this.callback = callback;
        }

        void linkToDeath() {
            try {
                callback.linkToDeath(this, 0);
            } catch (RemoteException e) {
                throw new IllegalStateException("launch callback already dead", e);
            }
        }

        void unlinkToDeath() {
            callback.unlinkToDeath(this, 0);
        }

        boolean route(int sourceDisplayId, String sourcePackage,
                      Intent intent, String targetPackage) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(LAUNCH_CALLBACK_DESCRIPTOR);
                data.writeInt(sourceDisplayId);
                data.writeString(sourcePackage);
                data.writeInt(1);
                intent.writeToParcel(data, 0);
                data.writeString(targetPackage);
                callback.transact(LAUNCH_CALLBACK_TRANSACTION, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "cross-app launch callback failed: "
                        + e.getClass().getSimpleName());
                synchronized (launchRoutingLock) {
                    if (launchCallback == this) {
                        clearLaunchCallbackLocked();
                    }
                }
                return false;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        @Override
        public void binderDied() {
            synchronized (launchRoutingLock) {
                if (launchCallback == this) {
                    launchCallback = null;
                }
            }
        }
    }

    private final class DisplayRecord implements IBinder.DeathRecipient {
        final int slot;
        final VirtualDisplay display;
        final IBinder ownerToken;
        Surface surface;
        SurfaceControl mirror;

        DisplayRecord(int slot, VirtualDisplay display, Surface surface, IBinder ownerToken) {
            this.slot = slot;
            this.display = display;
            this.surface = surface;
            this.ownerToken = ownerToken;
        }

        void linkToOwnerDeath() {
            if (ownerToken == null) {
                throw new IllegalArgumentException("missing owner token");
            }
            try {
                ownerToken.linkToDeath(this, 0);
            } catch (RemoteException e) {
                throw new IllegalStateException("display owner already dead", e);
            }
        }

        void setSurface(Surface nextSurface) {
            display.setSurface(nextSurface);
            Surface previous = surface;
            surface = nextSurface;
            if (previous != null && previous != nextSurface) {
                previous.release();
            }
        }

        void release() {
            ownerToken.unlinkToDeath(this, 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                releaseMirror();
            }
            try {
                display.setSurface(null);
            } catch (RuntimeException ignored) {
                // Release still tears down the display if the producer surface already died.
            }
            display.release();
            if (surface != null) {
                surface.release();
                surface = null;
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        void releaseMirror() {
            if (mirror != null) {
                mirror.release();
                mirror = null;
            }
        }

        @Override
        public void binderDied() {
            synchronized (displays) {
                DisplayRecord current = displays.get(slot);
                if (current == this) {
                    releaseLocked(slot);
                }
            }
        }
    }
}
