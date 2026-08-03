package com.sangluo.onestep.feature.drag;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;

/** Client side of the explicit original-image pipe owned by OneStep. */
public final class ImageDragBridgeClient {
    private static final String TAG = "OneStep40-ImageBridge";
    private static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";
    private static final String SERVICE_CLASS =
            "com.sangluo.onestep.feature.drag.ImageDragBridgeService";

    private ImageDragBridgeClient() {
    }

    public static boolean transfer(
            Context context, Uri sourceUri, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        Context applicationContext = context.getApplicationContext();
        Uri localMediaUri = GooglePhotosMediaUriResolver.resolve(sourceUri);
        Uri shareUri = localMediaUri == null ? sourceUri : localMediaUri;
        try {
            applicationContext.grantUriPermission(
                    ONE_STEP_PACKAGE, shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException e) {
            // The source process still owns the URI and can always stream the preview.
            // A failed re-grant must not suppress the visible drag session.
            Log.w(TAG, "Original URI re-grant unavailable; preview transfer continues: "
                    + shareUri.getAuthority(), e);
        }
        TransferConnection connection = new TransferConnection(
                applicationContext, sourceUri, shareUri, sourceDisplayId,
                sourcePackage, mimeType, maxBytes, executor);
        Intent intent = new Intent().setComponent(
                new ComponentName(ONE_STEP_PACKAGE, SERVICE_CLASS));
        try {
            boolean bound = applicationContext.bindService(
                    intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND);
            if (!bound) {
                Log.e(TAG, "OneStep image bridge service rejected explicit bind");
            }
            return bound;
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot bind OneStep image bridge service", e);
            return false;
        }
    }

    private static final class TransferConnection implements ServiceConnection {
        private final Context context;
        private final Uri sourceUri;
        private final Uri shareUri;
        private final int sourceDisplayId;
        private final String sourcePackage;
        private final String mimeType;
        private final long maxBytes;
        private final Executor executor;
        private boolean unbound;

        TransferConnection(
                Context context, Uri sourceUri, Uri shareUri, int sourceDisplayId,
                String sourcePackage, String mimeType, long maxBytes, Executor executor) {
            this.context = context;
            this.sourceUri = sourceUri;
            this.shareUri = shareUri;
            this.sourceDisplayId = sourceDisplayId;
            this.sourcePackage = sourcePackage;
            this.mimeType = mimeType;
            this.maxBytes = maxBytes;
            this.executor = executor;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                executor.execute(() -> writeOriginal(service));
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot schedule original image pipe", e);
                unbind();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unbound = true;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.e(TAG, "OneStep image bridge binding died");
            unbind();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.e(TAG, "OneStep image bridge returned a null binding");
            unbind();
        }

        private void writeOriginal(IBinder service) {
            long copied = 0L;
            try (ParcelFileDescriptor descriptor = openPipe(service);
                 InputStream input = context.getContentResolver().openInputStream(sourceUri);
                 OutputStream output = descriptor == null ? null
                         : new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                if (input == null || output == null) {
                    throw new IOException("original image pipe unavailable");
                }
                copied = copy(input, output, maxBytes);
                Log.i(TAG, "Original image sent, bytes=" + copied
                        + ", display=" + sourceDisplayId);
            } catch (IOException | RemoteException | RuntimeException e) {
                Log.e(TAG, "Original image send failed after bytes=" + copied, e);
            } finally {
                unbind();
            }
        }

        private ParcelFileDescriptor openPipe(IBinder service) throws RemoteException {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(ImageDragBridgeService.DESCRIPTOR);
                data.writeInt(sourceDisplayId);
                data.writeString(sourcePackage);
                data.writeString(mimeType);
                data.writeString(shareUri.toString());
                if (!service.transact(
                        ImageDragBridgeService.TRANSACTION_OPEN_TRANSFER, data, reply, 0)) {
                    throw new RemoteException("image bridge transaction rejected");
                }
                reply.readException();
                return reply.readInt() == 1
                        ? ParcelFileDescriptor.CREATOR.createFromParcel(reply) : null;
            } finally {
                data.recycle();
                reply.recycle();
            }
        }

        private void unbind() {
            if (unbound) {
                return;
            }
            unbound = true;
            try {
                context.unbindService(this);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private static long copy(
            InputStream input, OutputStream output, long maxBytes) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        long copied = 0L;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            copied += count;
            if (copied > maxBytes) {
                throw new IOException("image exceeds OneStep drag limit");
            }
            output.write(buffer, 0, count);
        }
        output.flush();
        if (copied == 0L) {
            throw new IOException("empty image");
        }
        return copied;
    }
}
