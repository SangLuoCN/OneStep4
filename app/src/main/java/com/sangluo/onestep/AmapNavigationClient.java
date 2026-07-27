package com.sangluo.onestep;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AmapNavigationClient implements AutoCloseable {
    public static final String PACKAGE_NAME = "com.autonavi.minimap";

    private static final String TAG = "OneStep40Amap";
    private static final String SERVICE_ACTION =
            "com.autonavi.minimap.service.NAVIGATION_SERVICE";
    private static final int TYPE_NAVIGATION_STATE = 4;
    private static final long STATE_STALE_TIMEOUT_MS = 8_000L;

    public interface Listener {
        void onNavigationStateChanged(NavigationState state);
    }

    public static final class NavigationState {
        public final int eventType;
        public final int maneuverId;
        public final int segmentRemainDistanceMeters;
        public final int speedKph;
        public final int routeRemainTimeSeconds;
        public final int routeRemainDistanceMeters;
        public final String currentRoadName;
        public final String nextRoadName;

        NavigationState(int eventType, int maneuverId, int segmentRemainDistanceMeters,
                        int speedKph, int routeRemainTimeSeconds,
                        int routeRemainDistanceMeters, String currentRoadName, String nextRoadName) {
            this.eventType = eventType;
            this.maneuverId = maneuverId;
            this.segmentRemainDistanceMeters = segmentRemainDistanceMeters;
            this.speedKph = speedKph;
            this.routeRemainTimeSeconds = routeRemainTimeSeconds;
            this.routeRemainDistanceMeters = routeRemainDistanceMeters;
            this.currentRoadName = currentRoadName;
            this.nextRoadName = nextRoadName;
        }
    }

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final LinkSdkCallback callback = new LinkSdkCallback();
    private final Runnable clearStaleState = () -> publishState(null);
    private LinkSdkControl control;
    private boolean bound;
    private boolean closed;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (closed) {
                return;
            }
            control = new LinkSdkControl(service);
            try {
                String authentication = control.authenticate();
                boolean registered = control.registerListener(callback);
                Log.i(TAG, "Connected to AMap navigation service: registered=" + registered
                        + ", authenticated=" + authenticationSucceeded(authentication));
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Register AMap navigation callback failed: "
                        + e.getClass().getSimpleName());
                clearState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            control = null;
            clearState();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            control = null;
            clearState();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            control = null;
            clearState();
        }
    };

    public AmapNavigationClient(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        if (closed || bound) {
            return;
        }
        Intent intent = new Intent(SERVICE_ACTION);
        intent.setPackage(PACKAGE_NAME);
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                Log.i(TAG, "AMap navigation service is unavailable");
            }
        } catch (RuntimeException e) {
            bound = false;
            Log.w(TAG, "Bind AMap navigation service failed: "
                    + e.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        mainHandler.removeCallbacks(clearStaleState);
        LinkSdkControl currentControl = control;
        control = null;
        if (currentControl != null) {
            try {
                currentControl.unregisterListener(callback);
            } catch (RemoteException | RuntimeException e) {
                Log.w(TAG, "Unregister AMap navigation callback failed: "
                        + e.getClass().getSimpleName());
            }
        }
        if (bound) {
            try {
                context.unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
    }

    private void handlePayload(String payload) {
        if (closed || payload == null || payload.isEmpty()) {
            return;
        }
        try {
            String decoded = decodePayload(payload);
            JSONObject root = new JSONObject(decoded);
            JSONArray entries = parseArray(root.opt("datas"));
            if (entries == null) {
                return;
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null || entry.optInt("type", -1) != TYPE_NAVIGATION_STATE) {
                    continue;
                }
                JSONObject data = parseObject(entry.opt("data"));
                if (data != null) {
                    publishState(parseNavigationState(data));
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse AMap navigation callback failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private NavigationState parseNavigationState(JSONObject data) {
        return new NavigationState(
                data.optInt("eventType", 0),
                data.optInt("curManeuverID", 0),
                Math.max(0, data.optInt("segmentRemainDist", 0)),
                Math.max(0, (int) Math.round(data.optDouble("speed", 0d))),
                Math.max(0, data.optInt("routeRemainTime", 0)),
                Math.max(0, data.optInt("routeRemainDist", 0)),
                cleanText(data.optString("curRouteName", "")),
                cleanText(data.optString("nextRouteName", "")));
    }

    private void publishState(NavigationState state) {
        mainHandler.post(() -> {
            if (closed) {
                return;
            }
            mainHandler.removeCallbacks(clearStaleState);
            listener.onNavigationStateChanged(state);
            if (state != null) {
                mainHandler.postDelayed(clearStaleState, STATE_STALE_TIMEOUT_MS);
            }
        });
    }

    private void clearState() {
        publishState(null);
    }

    private static boolean authenticationSucceeded(String authentication) {
        if (authentication == null || authentication.isEmpty()) {
            return false;
        }
        try {
            return "success".equals(new JSONObject(authentication).optString("status"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String cleanText(String value) {
        String cleaned = value == null ? "" : value.trim();
        return "null".equalsIgnoreCase(cleaned) ? "" : cleaned;
    }

    private static JSONArray parseArray(Object value) {
        if (value instanceof JSONArray) {
            return (JSONArray) value;
        }
        if (value instanceof String) {
            try {
                return new JSONArray((String) value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static JSONObject parseObject(Object value) {
        if (value instanceof JSONObject) {
            return (JSONObject) value;
        }
        if (value instanceof String) {
            try {
                return new JSONObject((String) value);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String decodePayload(String payload) throws Exception {
        JSONObject envelope = new JSONObject(payload);
        if (!envelope.optBoolean("encrypted", false)) {
            return payload;
        }
        byte[] bytes = Base64.decode(envelope.getString("content"), Base64.DEFAULT);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        int aadLength = buffer.getInt();
        if (aadLength < 0 || aadLength > 1024 || buffer.remaining() < aadLength + 4) {
            throw new IllegalArgumentException("Invalid AAD length");
        }
        byte[] aad = new byte[aadLength];
        buffer.get(aad);
        int keyLength = buffer.getInt();
        if ((keyLength != 16 && keyLength != 24 && keyLength != 32)
                || buffer.remaining() < keyLength + 28) {
            throw new IllegalArgumentException("Invalid AES key length");
        }
        byte[] key = new byte[keyLength];
        buffer.get(key);
        byte[] nonce = new byte[12];
        buffer.get(nonce);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private final class LinkSdkCallback extends Binder implements IInterface {
        private static final String DESCRIPTOR =
                "com.amap.linksdk.aidldefine.ILinkSdkCallback";

        LinkSdkCallback() {
            attachInterface(this, DESCRIPTOR);
        }

        @Override
        public IBinder asBinder() {
            return this;
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
            if (code != 1 && code != 2) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(DESCRIPTOR);
            String payload = data.readString();
            ParcelFileDescriptor fd = code == 2 && data.readInt() != 0
                    ? ParcelFileDescriptor.CREATOR.createFromParcel(data) : null;
            handlePayload(payload);
            if (fd != null) {
                readTransferredPayload(fd);
            }
            if (reply != null) {
                reply.writeNoException();
            }
            return true;
        }

        private void readTransferredPayload(ParcelFileDescriptor fd) {
            try (ParcelFileDescriptor.AutoCloseInputStream input =
                         new ParcelFileDescriptor.AutoCloseInputStream(fd);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                handlePayload(output.toString(StandardCharsets.UTF_8.name()));
            } catch (Exception e) {
                Log.w(TAG, "Read AMap navigation callback FD failed: "
                        + e.getClass().getSimpleName());
            }
        }
    }

    private static final class LinkSdkControl {
        private static final String DESCRIPTOR =
                "com.amap.linksdk.aidldefine.ILinkSdkControl";
        private final IBinder binder;

        LinkSdkControl(IBinder binder) {
            this.binder = binder;
        }

        String authenticate() throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                binder.transact(1, data, reply, 0);
                reply.readException();
                return reply.readString();
            } finally {
                reply.recycle();
                data.recycle();
            }
        }

        boolean registerListener(IInterface callback) throws RemoteException {
            return transactListener(3, callback);
        }

        boolean unregisterListener(IInterface callback) throws RemoteException {
            return transactListener(4, callback);
        }

        private boolean transactListener(int transaction, IInterface callback)
                throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeStrongBinder(callback.asBinder());
                binder.transact(transaction, data, reply, 0);
                reply.readException();
                return reply.readInt() != 0;
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
    }
}
