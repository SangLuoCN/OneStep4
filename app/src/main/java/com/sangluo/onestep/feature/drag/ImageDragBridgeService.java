package com.sangluo.onestep.feature.drag;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit Binder bridge that receives a decodable media preview from a hooked source app. */
public final class ImageDragBridgeService extends Service {
    public static final String DESCRIPTOR =
            "com.sangluo.onestep.feature.drag.IImageDragBridge";
    public static final int TRANSACTION_OPEN_TRANSFER = IBinder.FIRST_CALL_TRANSACTION;

    private static final String TAG = "OneStep40-ImageBridge";
    private static final long MAX_IMAGE_BYTES = 512L * 1024L * 1024L;
    private static final long STALE_FILE_MS = 10L * 60L * 1000L;

    private final ExecutorService transferExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneStep-image-bridge");
                thread.setDaemon(true);
                return thread;
            });

    private final Binder bridge = new Binder() {
        {
            attachInterface(null, DESCRIPTOR);
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(DESCRIPTOR);
                return true;
            }
            if (code != TRANSACTION_OPEN_TRANSFER) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(DESCRIPTOR);
            int callingUid = Binder.getCallingUid();
            int sourceDisplayId = data.readInt();
            String sourcePackage = data.readString();
            String mimeType = data.readString();
            boolean payloadContainsOriginalMedia = data.readInt() != 0;
            Uri sourceUri = parseContentUri(data.readString());
            ParcelFileDescriptor writeEnd = null;
            try {
                if (!isAccepted(callingUid, sourceDisplayId, sourcePackage, mimeType)) {
                    Log.w(TAG, "Media preview pipe rejected: uid=" + callingUid
                            + ", display=" + sourceDisplayId
                            + ", source=" + sourcePackage);
                    reply.writeNoException();
                    reply.writeInt(0);
                    return true;
                }
                File destination = newDestinationFile(
                        mimeType, payloadContainsOriginalMedia);
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                ParcelFileDescriptor readEnd = pipe[0];
                writeEnd = pipe[1];
                transferExecutor.execute(() -> receivePreview(
                        readEnd, destination, sourceDisplayId,
                        sourcePackage, mimeType, sourceUri));
                reply.writeNoException();
                reply.writeInt(1);
                writeEnd.writeToParcel(reply, Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                writeEnd = null;
                Log.i(TAG, "Media preview pipe opened for display=" + sourceDisplayId);
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, "Cannot open media preview pipe", e);
                reply.setDataSize(0);
                reply.setDataPosition(0);
                reply.writeException(new IllegalStateException(e.getMessage()));
            } finally {
                close(writeEnd);
            }
            return true;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        cleanupStaleFiles();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return bridge;
    }

    @Override
    public void onDestroy() {
        transferExecutor.shutdown();
        super.onDestroy();
    }

    private boolean isAccepted(
            int callingUid, int sourceDisplayId, String sourcePackage, String mimeType) {
        return sourceDisplayId > 0
                && ImageDragSourcePolicy.isAllowed(sourcePackage, sourceDisplayId)
                && ImageDragSourcePolicy.isSupportedMediaMimeType(mimeType)
                && ImageDragBridgeRegistry.canAccept(
                callingUid, sourceDisplayId, sourcePackage);
    }

    private File newDestinationFile(
            String mimeType, boolean payloadContainsOriginalMedia) throws IOException {
        File directory = new File(getCacheDir(), "drag");
        if ((!directory.isDirectory() && !directory.mkdirs()) || !directory.isDirectory()) {
            throw new IOException("drag cache directory unavailable");
        }
        return new File(directory, "incoming-" + UUID.randomUUID()
                + (payloadContainsOriginalMedia
                ? ImageFileNamePolicy.extensionForMime(mimeType)
                : ImageFileNamePolicy.previewExtensionForMime(mimeType)));
    }

    private void receivePreview(
            ParcelFileDescriptor readEnd, File destination, int sourceDisplayId,
            String sourcePackage, String mimeType, Uri sourceUri) {
        boolean accepted = false;
        long copied = 0L;
        try (ParcelFileDescriptor.AutoCloseInputStream input =
                     new ParcelFileDescriptor.AutoCloseInputStream(readEnd);
             FileOutputStream output = new FileOutputStream(destination)) {
            copied = copy(input, output);
            accepted = ImageDragBridgeRegistry.onImageReady(
                    sourceDisplayId, sourcePackage, mimeType, sourceUri, destination);
            Log.i(TAG, "Media preview received=" + accepted
                    + ", bytes=" + copied + ", display=" + sourceDisplayId);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Media preview receive failed after bytes=" + copied, e);
        } finally {
            if (!accepted) {
                delete(destination);
            }
        }
    }

    private static Uri parseContentUri(String value) {
        if (value == null) {
            return null;
        }
        try {
            Uri uri = Uri.parse(value);
            return "content".equals(uri.getScheme()) ? uri : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static long copy(InputStream input, FileOutputStream output) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        long copied = 0L;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) {
                continue;
            }
            copied += count;
            if (copied > MAX_IMAGE_BYTES) {
                throw new IOException("media preview exceeds OneStep drag limit");
            }
            output.write(buffer, 0, count);
        }
        output.flush();
        if (copied == 0L) {
            throw new IOException("empty media preview");
        }
        return copied;
    }

    private void cleanupStaleFiles() {
        File[] files = new File(getCacheDir(), "drag").listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - STALE_FILE_MS;
        for (File file : files) {
            if (file.getName().startsWith("incoming-") && file.lastModified() < cutoff) {
                delete(file);
            }
        }
    }

    private static void close(ParcelFileDescriptor descriptor) {
        if (descriptor == null) {
            return;
        }
        try {
            descriptor.close();
        } catch (IOException ignored) {
        }
    }

    private static void delete(File file) {
        if (file != null && file.isFile() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
