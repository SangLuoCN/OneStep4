package com.sangluo.onestep.system.display;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.sangluo.onestep.RootVirtualDisplayBridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Binder client for secure virtual displays owned by the root bridge process. */
public final class RootVirtualDisplayBridgeClient {
    private static final String TAG = "OneStep40";

    private final IBinder ownerToken = new Binder();
    private IBinder service;

    public CreateResult create(String bridgeToken, int slot, String name,
                               int width, int height, int densityDpi,
                               Surface surface, int[] flagCandidates) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            IBinder target = requireService();
            writeHeader(data, bridgeToken);
            data.writeInt(slot);
            data.writeString(name);
            data.writeInt(width);
            data.writeInt(height);
            data.writeInt(densityDpi);
            writeSurface(data, surface);
            data.writeIntArray(flagCandidates);
            data.writeStrongBinder(ownerToken);
            target.transact(RootVirtualDisplayBridge.TRANSACTION_CREATE, data, reply, 0);
            reply.readException();
            return new CreateResult(reply.readInt(), reply.readInt(), reply.readString());
        } catch (RemoteException | RuntimeException e) {
            service = null;
            Log.w(TAG, "Root secure display create failed: " + describe(e));
            return new CreateResult(-1, 0, describe(e));
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public boolean resize(String bridgeToken, int slot,
                          int width, int height, int densityDpi) {
        return transactBoolean(bridgeToken, RootVirtualDisplayBridge.TRANSACTION_RESIZE,
                data -> {
                    data.writeInt(slot);
                    data.writeInt(width);
                    data.writeInt(height);
                    data.writeInt(densityDpi);
                });
    }

    public boolean setSurface(String bridgeToken, int slot, Surface surface) {
        return transactBoolean(bridgeToken, RootVirtualDisplayBridge.TRANSACTION_SET_SURFACE,
                data -> {
                    data.writeInt(slot);
                    writeSurface(data, surface);
                });
    }

    public boolean release(String bridgeToken, int slot) {
        return transactBoolean(bridgeToken, RootVirtualDisplayBridge.TRANSACTION_RELEASE,
                data -> data.writeInt(slot));
    }

    public Integer getBridgeUid(String bridgeToken) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            IBinder target = requireService();
            writeHeader(data, bridgeToken);
            target.transact(RootVirtualDisplayBridge.TRANSACTION_PING, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (RemoteException | RuntimeException e) {
            service = null;
            return null;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public void close() {
        service = null;
    }

    private boolean transactBoolean(String bridgeToken, int transaction,
                                    ParcelWriter writer) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            IBinder target = requireService();
            writeHeader(data, bridgeToken);
            writer.write(data);
            target.transact(transaction, data, reply, 0);
            reply.readException();
            return reply.readInt() != 0;
        } catch (RemoteException | RuntimeException e) {
            service = null;
            Log.w(TAG, "Root secure display transaction failed: " + describe(e));
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private IBinder requireService() {
        if (service != null && service.isBinderAlive()) {
            return service;
        }
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getDeclaredMethod("getService", String.class);
            getService.setAccessible(true);
            Object result = getService.invoke(null,
                    RootVirtualDisplayBridge.SERVICE_NAME_PREFIX + android.os.Process.myUid());
            if (!(result instanceof IBinder)) {
                throw new IllegalStateException("root display service unavailable");
            }
            service = (IBinder) result;
            return service;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException
                 | InvocationTargetException e) {
            throw new IllegalStateException("cannot resolve root display service", e);
        }
    }

    private static void writeHeader(Parcel data, String bridgeToken) {
        data.writeInterfaceToken(RootVirtualDisplayBridge.DESCRIPTOR);
        data.writeString(bridgeToken);
    }

    private static void writeSurface(Parcel data, Surface surface) {
        if (surface == null) {
            data.writeInt(0);
            return;
        }
        data.writeInt(1);
        surface.writeToParcel(data, 0);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    private interface ParcelWriter {
        void write(Parcel data);
    }

    public static final class CreateResult {
        public final int displayId;
        public final int selectedFlags;
        public final String failure;

        CreateResult(int displayId, int selectedFlags, String failure) {
            this.displayId = displayId;
            this.selectedFlags = selectedFlags;
            this.failure = failure == null ? "" : failure;
        }

        public boolean isSuccess() {
            return displayId > 0;
        }
    }
}
