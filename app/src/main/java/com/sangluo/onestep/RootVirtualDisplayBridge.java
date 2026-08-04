package com.sangluo.onestep;

import android.content.Context;
import android.content.Intent;
import android.app.ActivityOptions;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import com.sangluo.onestep.system.display.DisplayOwnerPolicy;
import com.sangluo.onestep.system.root.SystemServiceFailurePolicy;
import com.sangluo.onestep.feature.drag.ImageDragSourcePolicy;
import com.sangluo.onestep.feature.drag.ImageShareIntentParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Owns virtual displays inside the root app_process bridge. */
public final class RootVirtualDisplayBridge extends Binder {
    private static final String TAG = "OneStepDisplayBridge";

    public static final String DESCRIPTOR = "com.sangluo.onestep.IRootVirtualDisplayBridge";
    public static final String LAUNCH_CALLBACK_DESCRIPTOR =
            "com.sangluo.onestep.ICrossAppLaunchCallback";
    public static final int LAUNCH_CALLBACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TASK_EVENT_CALLBACK_TRANSACTION =
            IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TASK_EVENT_MOVED_TO_FRONT = 1;
    public static final int TASK_EVENT_REMOVAL_STARTED = 2;
    public static final int TASK_EVENT_STACK_CHANGED = 3;
    public static final String SERVICE_NAME_PREFIX = "onestep_display_";
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
    public static final int TRANSACTION_START_ACTIVITY_AS_USER =
            IBinder.FIRST_CALL_TRANSACTION + 8;
    private static final int ROOT_UID = 0;
    private static final long LAUNCH_BYPASS_TIMEOUT_MS = 3000L;
    private static final long ROUTING_INPUT_ARM_TIMEOUT_MS = 5000L;
    private static volatile RootVirtualDisplayBridge publishedBridge;

    private final int allowedUid;
    private final String bridgeToken;
    private final Context context;
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
    @SuppressWarnings("FieldCanBeLocal")
    private RootTaskStackObserver taskStackObserver;

    private RootVirtualDisplayBridge(Context context, int allowedUid, String bridgeToken) {
        this.allowedUid = allowedUid;
        this.bridgeToken = bridgeToken;
        this.context = context;
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        attachInterface(null, DESCRIPTOR);
    }

    public static RootVirtualDisplayBridge publish(Context context, int allowedUid,
                                                   String bridgeToken)
            throws ReflectiveOperationException {
        if (android.os.Process.myUid() != ROOT_UID) {
            throw new SecurityException("display bridge requires uid 0");
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
        try {
            bridge.taskStackObserver = RootTaskStackObserver.install(bridge);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, "task stack observer unavailable", e);
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
                case TRANSACTION_START_ACTIVITY_AS_USER:
                    handleStartActivityAsUser(data, reply);
                    return true;
                default:
                    return super.onTransact(code, data, reply, flags);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "transaction failed code=" + code, e);
            String failure = SystemServiceFailurePolicy.describeCauseChain(e);
            reply.setDataSize(0);
            reply.setDataPosition(0);
            if (code == TRANSACTION_CREATE) {
                writeCreateFailure(reply, failure);
            } else {
                // Parcel only marshals a fixed set of exception classes. Unknown runtime
                // exceptions are otherwise encoded as EX_NONE and disappear at the client.
                reply.writeException(new IllegalStateException(failure));
            }
            return true;
        }
    }

    private static void writeCreateFailure(Parcel reply, String failure) {
        reply.writeNoException();
        reply.writeInt(-1);
        reply.writeInt(0);
        reply.writeString(failure);
        reply.writeInt(0);
        reply.writeString("");
        reply.writeInt(0);
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
        if (ownerToken == null) {
            if (surface != null) {
                surface.release();
            }
            throw new IllegalArgumentException("missing display owner token");
        }

        int displayId = -1;
        int selectedFlags = 0;
        boolean homeSupportRequested = false;
        boolean primaryHomeHookActive = false;
        String homeSupportFailure = "";
        String failure = "no flag candidates";
        synchronized (displays) {
            releaseLocked(slot);
            if (surface != null && surface.isValid() && requestedCandidates != null) {
                for (int requestedFlags : requestedCandidates) {
                    int displayFlags = RootVirtualDisplayFlags.forRootBridge(requestedFlags);
                    VirtualDisplay candidate = null;
                    try {
                        VirtualDisplayHomeSupport.CreationResult creation =
                                VirtualDisplayHomeSupport.create(
                                        displayManager, name, width, height, densityDpi,
                                        surface, displayFlags);
                        candidate = creation.display;
                        homeSupportRequested = creation.homeSupportRequested;
                        homeSupportFailure = creation.homeSupportFailure;
                        primaryHomeHookActive = creation.primaryHomeHookActive;
                        Display display = candidate == null ? null : candidate.getDisplay();
                        if (display == null) {
                            failure = "create returned no display for flags=" + displayFlags;
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
                        selectedFlags = displayFlags;
                        launchDisplayAccessActivity(displayId);
                        failure = "";
                        break;
                    } catch (RuntimeException e) {
                        if (SystemServiceFailurePolicy.isStaleSystemService(e)) {
                            throw e;
                        }
                        displayId = -1;
                        selectedFlags = 0;
                        failure = "flags=" + displayFlags + ":"
                                + e.getClass().getSimpleName() + ":" + e.getMessage();
                        Log.w(TAG, "candidate failed slot=" + slot
                                + " flags=0x" + Integer.toHexString(displayFlags), e);
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
        if (displayId > Display.DEFAULT_DISPLAY) {
            VirtualDisplaySystemDecorController.Result decorResult =
                    VirtualDisplaySystemDecorController.disable(context, displayId);
            int priority = decorResult.isConfirmedDisabled() ? Log.INFO : Log.WARN;
            Log.println(priority, TAG, "system decorations policy"
                    + " display=" + displayId
                    + " requested=" + decorResult.requested
                    + " actual=" + decorResult.actualValue()
                    + (decorResult.failure.isEmpty()
                    ? "" : " failure=" + decorResult.failure));
        }
        if (displayId <= Display.DEFAULT_DISPLAY) {
            if (surface != null) {
                surface.release();
            }
            Log.e(TAG, "create failed slot=" + slot + " reason=" + failure);
        } else {
            Log.i(TAG, "created slot=" + slot + " display=" + displayId
                    + " flags=0x" + Integer.toHexString(selectedFlags)
                    + " homeSupport=" + homeSupportRequested
                    + (homeSupportFailure.isEmpty()
                    ? "" : " homeSupportFallback=" + homeSupportFailure));
        }
        reply.writeNoException();
        reply.writeInt(displayId);
        reply.writeInt(selectedFlags);
        reply.writeString(failure);
        reply.writeInt(homeSupportRequested ? 1 : 0);
        reply.writeString(homeSupportFailure);
        reply.writeInt(primaryHomeHookActive ? 1 : 0);
    }

    private void handleResize(Parcel data, Parcel reply) {
        int slot = data.readInt();
        int width = data.readInt();
        int height = data.readInt();
        int densityDpi = data.readInt();
        IBinder ownerToken = data.readStrongBinder();
        boolean success = false;
        synchronized (displays) {
            DisplayRecord record = displays.get(slot);
            if (record != null && record.isOwnedBy(ownerToken)) {
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
                    "-n", "com.sangluo.onestep/.DisplayAccessActivity",
                    "-f", String.valueOf(0x10010000))
                    .redirectErrorStream(true)
                    .start();
            waitForProcess(process, 2000L);
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
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void waitForProcess(Process process, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (true) {
            try {
                process.exitValue();
                return;
            } catch (IllegalThreadStateException stillRunning) {
                if (SystemClock.uptimeMillis() >= deadline) {
                    process.destroy();
                    throw new IllegalStateException(
                            "display access activity start timed out");
                }
                SystemClock.sleep(20L);
            }
        }
    }

    private void handleSetSurface(Parcel data, Parcel reply) {
        int slot = data.readInt();
        Surface nextSurface = readSurface(data);
        IBinder ownerToken = data.readStrongBinder();
        boolean success = false;
        synchronized (displays) {
            DisplayRecord record = displays.get(slot);
            if (record != null && record.isOwnedBy(ownerToken)) {
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
        IBinder ownerToken = data.readStrongBinder();
        boolean released;
        synchronized (displays) {
            DisplayRecord record = displays.get(slot);
            released = record != null && record.isOwnedBy(ownerToken)
                    && releaseLocked(slot);
        }
        reply.writeNoException();
        reply.writeInt(released ? 1 : 0);
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

    private void handleStartActivityAsUser(Parcel data, Parcel reply) {
        int sourceUserId = data.readInt();
        int targetUserId = data.readInt();
        int displayId = data.readInt();
        Intent intent = data.readInt() == 0
                ? null : Intent.CREATOR.createFromParcel(data);
        boolean launched = false;
        if (intent != null && targetUserId >= 0 && displayId > Display.DEFAULT_DISPLAY) {
            long identity = Binder.clearCallingIdentity();
            try {
                // Content URIs originating in the owner profile must retain their source
                // user when ActivityTaskManager grants them to a cloned profile.
                try {
                    Method prepareToLeaveUser = Intent.class.getDeclaredMethod(
                            "prepareToLeaveUser", int.class);
                    prepareToLeaveUser.setAccessible(true);
                    prepareToLeaveUser.invoke(intent, sourceUserId);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Older releases may not expose this hidden Intent helper.
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(displayId);
                Method startActivityAsUser = Context.class.getDeclaredMethod(
                        "startActivityAsUser", Intent.class, android.os.Bundle.class,
                        UserHandle.class);
                startActivityAsUser.setAccessible(true);
                java.lang.reflect.Constructor<UserHandle> userHandleConstructor =
                        UserHandle.class.getDeclaredConstructor(int.class);
                userHandleConstructor.setAccessible(true);
                startActivityAsUser.invoke(
                        context, intent, options.toBundle(),
                        userHandleConstructor.newInstance(targetUserId));
                launched = true;
            } catch (ReflectiveOperationException | RuntimeException e) {
                Log.e(TAG, "root cross-user share launch failed user=" + targetUserId
                        + " display=" + displayId, e);
            } finally {
                Binder.restoreCallingIdentity(identity);
            }
        }
        reply.writeNoException();
        reply.writeInt(launched ? 1 : 0);
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

    void consumeArmedRoutingSource(RoutingSource source) {
        if (source == null) {
            return;
        }
        synchronized (launchRoutingLock) {
            if (source.displayId == routingSourceDisplayId
                    && source.packageName.equals(routingSourcePackage)
                    && source.displayId == lastInputDisplayId) {
                lastInputUptime = 0L;
            }
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
        ImageShareIntentParser.Payload sharedImage =
                ImageShareIntentParser.find(intent);
        ParcelFileDescriptor sharedDescriptor = sharedImage == null
                ? null : openSharedMedia(sharedImage.uri, sharedImage.mimeType);
        boolean routed;
        try {
            routed = callback != null && callback.route(
                    sourceDisplayId, sourcePackage, intent, targetPackage,
                    sharedImage == null ? "" : sharedImage.mimeType, sharedDescriptor);
        } finally {
            if (sharedDescriptor != null) {
                try {
                    sharedDescriptor.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (routed) {
            synchronized (launchRoutingLock) {
                lastInputUptime = 0L;
            }
        }
        return routed;
    }

    private ParcelFileDescriptor openSharedMedia(Uri uri, String mimeType) {
        if (uri == null) {
            return null;
        }
        try {
            ParcelFileDescriptor descriptor = context.getContentResolver()
                    .openFileDescriptor(uri, "r");
            if (descriptor != null) {
                return descriptor;
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "share URI openFile failed: " + e.getClass().getSimpleName()
                    + ", uri=" + uri);
        }
        try {
            android.content.res.AssetFileDescriptor asset = context.getContentResolver()
                    .openTypedAssetFileDescriptor(uri,
                            ImageDragSourcePolicy.isSupportedMediaMimeType(mimeType)
                                    ? mimeType : "*/*", null);
            if (asset != null) {
                try {
                    return ParcelFileDescriptor.dup(asset.getParcelFileDescriptor().getFileDescriptor());
                } finally {
                    asset.close();
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "share URI openTyped failed: " + e.getClass().getSimpleName()
                    + ", uri=" + uri);
        }
        String path = uri.getPath();
        if (path != null && path.startsWith("/raw/")) {
            String decoded = Uri.decode(path.substring("/raw/".length()));
            if (decoded.startsWith("/storage/emulated/") || decoded.startsWith("/sdcard/")) {
                try {
                    return ParcelFileDescriptor.open(new java.io.File(decoded),
                            ParcelFileDescriptor.MODE_READ_ONLY);
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, "share raw path open failed: " + e.getClass().getSimpleName());
                }
            }
        }
        return null;
    }

    void notifyTaskEvent(int event, int displayId, int taskId, String packageName,
                         String componentName) {
        if (displayId > Display.DEFAULT_DISPLAY && !ownsDisplay(displayId)) {
            return;
        }
        LaunchCallbackRecord callback;
        synchronized (launchRoutingLock) {
            callback = launchCallback;
        }
        if (callback != null) {
            callback.notifyTaskEvent(event, displayId, taskId, packageName, componentName);
        }
    }

    private boolean ownsDisplay(int displayId) {
        synchronized (displays) {
            for (DisplayRecord record : displays.values()) {
                Display display = record.display.getDisplay();
                if (display != null && display.getDisplayId() == displayId) {
                    return true;
                }
            }
        }
        return false;
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
                      Intent intent, String targetPackage,
                      String sharedImageMimeType, ParcelFileDescriptor sharedDescriptor) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(LAUNCH_CALLBACK_DESCRIPTOR);
                data.writeInt(sourceDisplayId);
                data.writeString(sourcePackage);
                data.writeInt(1);
                intent.writeToParcel(data, 0);
                data.writeString(targetPackage);
                data.writeString(sharedImageMimeType);
                data.writeInt(sharedDescriptor == null ? 0 : 1);
                if (sharedDescriptor != null) {
                    sharedDescriptor.writeToParcel(data, 0);
                }
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

        void notifyTaskEvent(int event, int displayId, int taskId, String packageName,
                             String componentName) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(LAUNCH_CALLBACK_DESCRIPTOR);
                data.writeInt(event);
                data.writeInt(displayId);
                data.writeInt(taskId);
                data.writeString(packageName);
                data.writeString(componentName);
                callback.transact(TASK_EVENT_CALLBACK_TRANSACTION,
                        data, reply, 0);
                reply.readException();
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "activity-resuming callback failed: "
                        + e.getClass().getSimpleName());
                synchronized (launchRoutingLock) {
                    if (launchCallback == this) {
                        clearLaunchCallbackLocked();
                    }
                }
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

        boolean isOwnedBy(IBinder requestingOwner) {
            return DisplayOwnerPolicy.matches(ownerToken, requestingOwner);
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
