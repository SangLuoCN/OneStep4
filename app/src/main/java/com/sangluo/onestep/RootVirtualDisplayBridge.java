package com.sangluo.onestep;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Owns secure virtual displays inside the root app_process bridge. */
public final class RootVirtualDisplayBridge extends Binder {
    private static final String TAG = "OneStepDisplayBridge";

    public static final String DESCRIPTOR = "com.sangluo.onestep.IRootVirtualDisplayBridge";
    public static final String SERVICE_NAME_PREFIX = "onestep_secure_display_";
    public static final int TRANSACTION_CREATE = IBinder.FIRST_CALL_TRANSACTION;
    public static final int TRANSACTION_RESIZE = IBinder.FIRST_CALL_TRANSACTION + 1;
    public static final int TRANSACTION_SET_SURFACE = IBinder.FIRST_CALL_TRANSACTION + 2;
    public static final int TRANSACTION_RELEASE = IBinder.FIRST_CALL_TRANSACTION + 3;
    public static final int TRANSACTION_PING = IBinder.FIRST_CALL_TRANSACTION + 4;

    private static final int ROOT_UID = 0;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_PROTECTED_BUFFERS = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;

    private final int allowedUid;
    private final String bridgeToken;
    private final DisplayManager displayManager;
    private final Map<Integer, DisplayRecord> displays = new HashMap<>();

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
                    // Android rejects PUBLIC + SECURE. Root-owned private displays are made
                    // accessible by launchDisplayAccessActivity() after creation.
                    if ((requestedFlags & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC) != 0) {
                        continue;
                    }
                    int secureFlags = requestedFlags
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE
                            | VIRTUAL_DISPLAY_FLAG_SUPPORTS_PROTECTED_BUFFERS
                            | VIRTUAL_DISPLAY_FLAG_TRUSTED;
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
