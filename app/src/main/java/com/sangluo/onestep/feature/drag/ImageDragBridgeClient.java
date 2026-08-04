package com.sangluo.onestep.feature.drag;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executor;

/** Client side of the explicit media-preview pipe owned by OneStep. */
public final class ImageDragBridgeClient {
    private static final String TAG = "OneStep40-ImageBridge";
    private static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";
    private static final String SERVICE_CLASS =
            "com.sangluo.onestep.feature.drag.ImageDragBridgeService";
    private static final Object QQ_ORIGINAL_CANDIDATE_LOCK = new Object();
    private static QqOriginalCandidate qqOriginalCandidate;

    private ImageDragBridgeClient() {
    }

    /** Publishes QQ's decoded source file while its rich-media pipeline still owns it. */
    public static void publishQqOriginalCandidate(String sourcePath) {
        if (!ImageDragFeatureGate.isEnabled() || TextUtils.isEmpty(sourcePath)) {
            return;
        }
        File source = new File(sourcePath);
        if (!source.isFile() || !source.canRead() || source.length() <= 0L) {
            return;
        }
        QqOriginalCandidate candidate = new QqOriginalCandidate(
                source, android.os.SystemClock.uptimeMillis());
        synchronized (QQ_ORIGINAL_CANDIDATE_LOCK) {
            qqOriginalCandidate = candidate;
            QQ_ORIGINAL_CANDIDATE_LOCK.notifyAll();
        }
        Log.i(TAG, "QQ original source published: path=" + sourcePath
                + ", bytes=" + source.length());
    }

    private static void clearQqOriginalCandidate() {
        synchronized (QQ_ORIGINAL_CANDIDATE_LOCK) {
            qqOriginalCandidate = null;
        }
    }

    private static File qqOriginalCandidate(int expectedWidth, int expectedHeight) {
        QqOriginalCandidate candidate;
        synchronized (QQ_ORIGINAL_CANDIDATE_LOCK) {
            candidate = qqOriginalCandidate;
        }
        if (candidate == null
                || android.os.SystemClock.uptimeMillis() - candidate.publishedAt > 10_000L
                || !candidate.file.isFile() || !candidate.file.canRead()
                || candidate.file.length() <= 0L) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(candidate.file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        int expectedLong = Math.max(expectedWidth, expectedHeight);
        int expectedShort = Math.min(expectedWidth, expectedHeight);
        int actualLong = Math.max(bounds.outWidth, bounds.outHeight);
        int actualShort = Math.min(bounds.outWidth, bounds.outHeight);
        if (expectedLong > 0 && expectedShort > 0
                && (actualLong * 10L < expectedLong * 9L
                || actualShort * 10L < expectedShort * 9L)) {
            Log.w(TAG, "Rejected QQ source candidate dimensions: actual="
                    + bounds.outWidth + 'x' + bounds.outHeight + ", expected="
                    + expectedWidth + 'x' + expectedHeight);
            return null;
        }
        Log.i(TAG, "QQ original source accepted: "
                + bounds.outWidth + 'x' + bounds.outHeight
                + ", bytes=" + candidate.file.length());
        return candidate.file;
    }

    private static final class QqOriginalCandidate {
        final File file;
        final long publishedAt;

        QqOriginalCandidate(File file, long publishedAt) {
            this.file = file;
            this.publishedAt = publishedAt;
        }
    }

    public static boolean transfer(
            Context context, Uri sourceUri, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled()) {
            return false;
        }
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

    /** Transfers an already decoded app image when the source app does not expose a URI. */
    public static boolean transferBitmap(
            Context context, Bitmap bitmap, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled() || bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        Bitmap snapshot;
        try {
            snapshot = bitmap.copy(Bitmap.Config.ARGB_8888, false);
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot snapshot generic image bitmap", e);
            return false;
        }
        if (snapshot == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        TransferConnection connection = new TransferConnection(
                applicationContext, null, null, sourceDisplayId,
                sourcePackage, TextUtils.isEmpty(mimeType) ? "image/png" : mimeType,
                maxBytes, executor, snapshot);
        Intent intent = new Intent().setComponent(
                new ComponentName(ONE_STEP_PACKAGE, SERVICE_CLASS));
        try {
            boolean bound = applicationContext.bindService(
                    intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND);
            if (!bound) {
                snapshot.recycle();
                Log.e(TAG, "OneStep image bridge rejected generic bitmap bind");
            }
            return bound;
        } catch (RuntimeException e) {
            snapshot.recycle();
            Log.e(TAG, "Cannot bind generic bitmap image bridge service", e);
            return false;
        }
    }

    /** Transfers a source app's decoded image cache file without rasterizing its View. */
    public static boolean transferFile(
            Context context, File sourceFile, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled()
                || sourceFile == null || !sourceFile.isFile() || !sourceFile.canRead()) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        TransferConnection connection = new TransferConnection(
                applicationContext, null, null, sourceDisplayId,
                sourcePackage, TextUtils.isEmpty(mimeType) ? "image/*" : mimeType,
                maxBytes, executor, null, sourceFile, null, null, null);
        Intent intent = new Intent().setComponent(
                new ComponentName(ONE_STEP_PACKAGE, SERVICE_CLASS));
        try {
            boolean bound = applicationContext.bindService(
                    intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND);
            if (!bound) {
                Log.e(TAG, "OneStep image bridge rejected source file bind");
            }
            return bound;
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot bind source file image bridge", e);
            return false;
        }
    }

    /** Streams an exact original-image URL retained by the source app's message model. */
    public static boolean transferUrl(
            Context context, String sourceUrl, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled() || TextUtils.isEmpty(sourceUrl)
                || (!sourceUrl.startsWith("https://") && !sourceUrl.startsWith("http://"))) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        TransferConnection connection = new TransferConnection(
                applicationContext, null, null, sourceDisplayId,
                sourcePackage, TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType,
                maxBytes, executor, null, null, sourceUrl, null, null);
        return bind(applicationContext, connection, "remote original image");
    }

    /** Streams a WeChat VFS path from inside the hooked WeChat process. */
    public static boolean transferWeChatPath(
            Context context, String sourcePath, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled()
                || !"com.tencent.mm".equals(sourcePackage) || TextUtils.isEmpty(sourcePath)) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        TransferConnection connection = new TransferConnection(
                applicationContext, null, null, sourceDisplayId,
                sourcePackage, TextUtils.isEmpty(mimeType) ? "image/jpeg" : mimeType,
                maxBytes, executor, null, null, null, sourcePath, null);
        return bind(applicationContext, connection, "WeChat original image");
    }

    /** Uses QQ's own authenticated rich-media service to materialize an original image. */
    public static boolean transferQqMessage(
            Context context, Object qqMessageItem, int sourceDisplayId, String sourcePackage,
            String mimeType, long maxBytes, Executor executor) {
        if (!ImageDragFeatureGate.isEnabled()
                || !"com.tencent.mobileqq".equals(sourcePackage) || qqMessageItem == null) {
            return false;
        }
        Context applicationContext = context.getApplicationContext();
        TransferConnection connection = new TransferConnection(
                applicationContext, null, null, sourceDisplayId,
                sourcePackage, TextUtils.isEmpty(mimeType) ? "image/*" : mimeType,
                maxBytes, executor, null, null, null, null, qqMessageItem);
        return bind(applicationContext, connection, "QQ original image");
    }

    private static boolean bind(
            Context applicationContext, TransferConnection connection, String description) {
        Intent intent = new Intent().setComponent(
                new ComponentName(ONE_STEP_PACKAGE, SERVICE_CLASS));
        try {
            boolean bound = applicationContext.bindService(
                    intent, connection, Context.BIND_AUTO_CREATE | Context.BIND_NOT_FOREGROUND);
            if (!bound) {
                Log.e(TAG, "OneStep image bridge rejected " + description + " bind");
            }
            return bound;
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot bind " + description + " bridge", e);
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
        private final Bitmap bitmap;
        private final File sourceFile;
        private final String sourceUrl;
        private final String virtualPath;
        private final Object qqMessageItem;
        private boolean unbound;

        TransferConnection(
                Context context, Uri sourceUri, Uri shareUri, int sourceDisplayId,
                String sourcePackage, String mimeType, long maxBytes, Executor executor) {
            this(context, sourceUri, shareUri, sourceDisplayId, sourcePackage, mimeType,
                    maxBytes, executor, null, null, null, null, null);
        }

        TransferConnection(
                Context context, Uri sourceUri, Uri shareUri, int sourceDisplayId,
                String sourcePackage, String mimeType, long maxBytes, Executor executor,
                Bitmap bitmap) {
            this(context, sourceUri, shareUri, sourceDisplayId, sourcePackage, mimeType,
                    maxBytes, executor, bitmap, null, null, null, null);
        }

        TransferConnection(
                Context context, Uri sourceUri, Uri shareUri, int sourceDisplayId,
                String sourcePackage, String mimeType, long maxBytes, Executor executor,
                Bitmap bitmap, File sourceFile, String sourceUrl, String virtualPath,
                Object qqMessageItem) {
            this.context = context;
            this.sourceUri = sourceUri;
            this.shareUri = shareUri;
            this.sourceDisplayId = sourceDisplayId;
            this.sourcePackage = sourcePackage;
            this.mimeType = mimeType;
            this.maxBytes = maxBytes;
            this.executor = executor;
            this.bitmap = bitmap;
            this.sourceFile = sourceFile;
            this.sourceUrl = sourceUrl;
            this.virtualPath = virtualPath;
            this.qqMessageItem = qqMessageItem;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                executor.execute(() -> writePreview(service));
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot schedule media preview pipe", e);
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

        private void writePreview(IBinder service) {
            long copied = 0L;
            try (ParcelFileDescriptor descriptor = openPipe(service);
                 OutputStream output = descriptor == null ? null
                         : new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                if (output == null) {
                    throw new IOException("media preview pipe unavailable");
                }
                if (bitmap != null) {
                    ByteArrayOutputStream encoded = new ByteArrayOutputStream(128 * 1024);
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, encoded)) {
                        throw new IOException("generic image bitmap encoding failed");
                    }
                    byte[] bytes = encoded.toByteArray();
                    if (bytes.length == 0 || bytes.length > maxBytes) {
                        throw new IOException("generic image exceeds OneStep limit");
                    }
                    output.write(bytes);
                    output.flush();
                    copied = bytes.length;
                } else if (sourceFile != null) {
                    try (InputStream input = new FileInputStream(sourceFile)) {
                        copied = copy(input, output, maxBytes);
                    }
                } else if (qqMessageItem != null) {
                    try (InputStream input = openQqMedia(qqMessageItem)) {
                        copied = copy(input, output, maxBytes);
                    }
                } else if (sourceUrl != null) {
                    copied = copyRemoteUrl(sourceUrl, output, maxBytes);
                } else if (virtualPath != null) {
                    if (ImageDragSourcePolicy.isVideoMimeType(mimeType)) {
                        try (InputStream input = openWeChatPath(virtualPath)) {
                            copied = copy(input, output, maxBytes);
                        }
                    } else {
                        copied = writeWeChatOriginal(virtualPath, output, maxBytes);
                    }
                } else if (ImageDragSourcePolicy.isVideoMimeType(mimeType)) {
                    copied = writeVideoThumbnail(output);
                } else {
                    try (InputStream input = context.getContentResolver()
                            .openInputStream(sourceUri)) {
                        if (input == null) {
                            throw new IOException("source image unavailable");
                        }
                        copied = copy(input, output, maxBytes);
                    }
                }
                Log.i(TAG, "Media preview sent, bytes=" + copied
                        + ", display=" + sourceDisplayId);
            } catch (IOException | RemoteException | RuntimeException e) {
                Log.e(TAG, "Media preview send failed after bytes=" + copied, e);
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
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
                data.writeInt(payloadContainsOriginalMedia() ? 1 : 0);
                data.writeString(shareUri == null ? null : shareUri.toString());
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

        private boolean payloadContainsOriginalMedia() {
            return !ImageDragSourcePolicy.isVideoMimeType(mimeType)
                    || sourceFile != null
                    || sourceUrl != null
                    || virtualPath != null
                    || qqMessageItem != null;
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

        private long writeVideoThumbnail(OutputStream output) throws IOException {
            Uri thumbnailUri = shareUri == null ? sourceUri : shareUri;
            Bitmap thumbnail = context.getContentResolver().loadThumbnail(
                    thumbnailUri, new Size(512, 512), null);
            if (thumbnail == null || thumbnail.isRecycled()) {
                throw new IOException("video thumbnail unavailable");
            }
            try {
                ByteArrayOutputStream encoded = new ByteArrayOutputStream(128 * 1024);
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, encoded)) {
                    throw new IOException("video thumbnail encoding failed");
                }
                byte[] bytes = encoded.toByteArray();
                if (bytes.length == 0 || bytes.length > maxBytes) {
                    throw new IOException("video thumbnail exceeds OneStep drag limit");
                }
                output.write(bytes);
                output.flush();
                return bytes.length;
            } finally {
                thumbnail.recycle();
            }
        }

        private long copyRemoteUrl(
                String sourceUrl, OutputStream output, long maxBytes) throws IOException {
            HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl)
                    .openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(30_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept",
                    ImageDragSourcePolicy.isVideoMimeType(mimeType)
                            ? "video/*" : "image/*");
            try {
                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("original image HTTP " + responseCode);
                }
                long contentLength = connection.getContentLengthLong();
                if (contentLength > maxBytes) {
                    throw new IOException("original image exceeds OneStep limit");
                }
                try (InputStream input = connection.getInputStream()) {
                    return copy(input, output, maxBytes);
                }
            } finally {
                connection.disconnect();
            }
        }

        private InputStream openWeChatPath(String sourcePath) throws IOException {
            try {
                Class<?> vfs = Class.forName(
                        "com.tencent.mm.vfs.w6", false, context.getClassLoader());
                Method open = vfs.getDeclaredMethod("E", String.class);
                open.setAccessible(true);
                Object stream = open.invoke(null, sourcePath);
                if (stream instanceof InputStream) {
                    return (InputStream) stream;
                }
                throw new IOException("WeChat VFS returned no input stream");
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (sourcePath.startsWith("/")) {
                    File sourceFile = new File(sourcePath);
                    if (sourceFile.isFile() && sourceFile.canRead()) {
                        return new FileInputStream(sourceFile);
                    }
                }
                throw new IOException("Cannot open WeChat VFS original", e);
            }
        }

        private long writeWeChatOriginal(
                String sourcePath, OutputStream output, long maxBytes) throws IOException {
            Bitmap decoded = decodeWeChatOriginal(sourcePath);
            if (decoded == null || decoded.isRecycled()) {
                throw new IOException("WeChat original decoder returned no bitmap");
            }
            try {
                ByteArrayOutputStream encoded = new ByteArrayOutputStream(512 * 1024);
                if (!decoded.compress(Bitmap.CompressFormat.JPEG, 100, encoded)) {
                    throw new IOException("WeChat original image encoding failed");
                }
                byte[] bytes = encoded.toByteArray();
                if (bytes.length == 0 || bytes.length > maxBytes) {
                    throw new IOException("WeChat original image exceeds OneStep limit");
                }
                output.write(bytes);
                output.flush();
                Log.i(TAG, "WeChat original decoded through app codec: "
                        + decoded.getWidth() + "x" + decoded.getHeight()
                        + ", bytes=" + bytes.length);
                return bytes.length;
            } finally {
                decoded.recycle();
            }
        }

        private Bitmap decodeWeChatOriginal(String sourcePath) {
            ClassLoader loader = context.getClassLoader();
            Bitmap bitmap = invokeStaticBitmapDecoder(
                    loader, "com.tencent.mm.graphics.e", "c",
                    new Class<?>[]{String.class}, new Object[]{sourcePath});
            if (bitmap != null) {
                Log.i(TAG, "WeChat original decoded by MMBitmapFactory");
                return bitmap;
            }
            bitmap = invokeStaticBitmapDecoder(
                    loader, "com.tencent.mm.sdk.platformtools.MMNativeJpeg", "decodeAsBitmap",
                    new Class<?>[]{String.class}, new Object[]{sourcePath});
            if (bitmap != null) {
                Log.i(TAG, "WeChat original decoded by MMNativeJpeg");
                return bitmap;
            }
            try (InputStream input = openWeChatPath(sourcePath)) {
                bitmap = invokeStaticBitmapDecoder(
                        loader, "com.tencent.mm.graphics.e", "f",
                        new Class<?>[]{InputStream.class}, new Object[]{input});
                if (bitmap != null) {
                    Log.i(TAG, "WeChat original decoded from VFS by MMBitmapFactory");
                }
                return bitmap;
            } catch (IOException e) {
                Log.w(TAG, "Cannot open WeChat original for app codec", e);
                return null;
            }
        }

        private static Bitmap invokeStaticBitmapDecoder(
                ClassLoader loader, String className, String methodName,
                Class<?>[] parameterTypes, Object[] arguments) {
            try {
                Class<?> decoderClass = Class.forName(className, false, loader);
                Method decoder = decoderClass.getDeclaredMethod(methodName, parameterTypes);
                decoder.setAccessible(true);
                Object decoded = decoder.invoke(null, arguments);
                return decoded instanceof Bitmap ? (Bitmap) decoded : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Log.w(TAG, "WeChat decoder unavailable: " + className + "." + methodName, e);
                return null;
            }
        }

        private InputStream openQqMedia(Object messageItem) throws IOException {
            try {
                String messageClassName = messageItem.getClass().getName();
                boolean shortVideoMessage =
                        "com.tencent.mobileqq.aio.msg.ShortVideoMsgItem".equals(
                                messageClassName);
                boolean fileMessage = "com.tencent.mobileqq.aio.msg.FileMsgItem".equals(
                        messageClassName);
                boolean video = ImageDragSourcePolicy.isVideoMimeType(mimeType)
                        || shortVideoMessage;
                Object mediaElement = invokeNoArg(messageItem,
                        fileMessage ? "M2" : shortVideoMessage ? "U2" : "L2");
                Object msgElement = invokeNoArg(messageItem,
                        fileMessage ? "N2" : shortVideoMessage ? "Q2" : "K2");
                Object msgRecord = invokeNoArg(messageItem, "getMsgRecord");
                if (mediaElement == null || msgElement == null || msgRecord == null) {
                    throw new IOException("QQ message model incomplete");
                }
                Object service = qqRichMediaService(messageItem.getClass().getClassLoader());
                if (service == null) {
                    throw new IOException("QQ rich-media service unavailable");
                }
                File cached = findQqMediaFile(
                        service, mediaElement, video, fileMessage);
                if (cached != null) {
                    Log.i(TAG, "QQ original media already cached: bytes=" + cached.length());
                    return new FileInputStream(cached);
                }
                int expectedWidth = numberValue(invokeNoArg(
                        mediaElement, shortVideoMessage
                                ? "getThumbWidth" : "getPicWidth"));
                int expectedHeight = numberValue(invokeNoArg(
                        mediaElement, shortVideoMessage
                                ? "getThumbHeight" : "getPicHeight"));
                if (!video) {
                    clearQqOriginalCandidate();
                }
                requestQqOriginalDownload(service, msgRecord, msgElement);
                long deadline = android.os.SystemClock.uptimeMillis() + 8_000L;
                do {
                    if (!video) {
                        File sourceCandidate = qqOriginalCandidate(
                                expectedWidth, expectedHeight);
                        if (sourceCandidate != null) {
                            return new FileInputStream(sourceCandidate);
                        }
                    }
                    cached = findQqMediaFile(
                            service, mediaElement, video, fileMessage);
                    if (cached != null) {
                        Log.i(TAG, "QQ original media download ready: bytes="
                                + cached.length());
                        return new FileInputStream(cached);
                    }
                    android.os.SystemClock.sleep(80L);
                } while (android.os.SystemClock.uptimeMillis() < deadline);
                throw new IOException("QQ original media download timed out");
            } catch (IOException e) {
                throw e;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IOException("Cannot resolve QQ original media", e);
            }
        }

        private Object qqRichMediaService(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> apiClass = Class.forName(
                    "com.tencent.qqnt.msg.api.IRichMediaService", false, loader);
            Class<?> qRouteClass = Class.forName(
                    "com.tencent.mobileqq.qroute.QRoute", false, loader);
            Method api = qRouteClass.getDeclaredMethod("api", Class.class);
            api.setAccessible(true);
            return api.invoke(null, apiClass);
        }

        private void requestQqOriginalDownload(
                Object service, Object msgRecord, Object msgElement)
                throws ReflectiveOperationException {
            ClassLoader loader = msgRecord.getClass().getClassLoader();
            Class<?> requestClass = Class.forName(
                    "com.tencent.qqnt.kernel.nativeinterface.RichDownLoadReq", false, loader);
            Constructor<?> requestConstructor = null;
            for (Constructor<?> constructor : requestClass.getDeclaredConstructors()) {
                if (constructor.getParameterTypes().length == 12) {
                    requestConstructor = constructor;
                    break;
                }
            }
            if (requestConstructor == null) {
                throw new NoSuchMethodException("RichDownLoadReq constructor");
            }
            requestConstructor.setAccessible(true);
            Object request = requestConstructor.newInstance(
                    1, 0,
                    longField(msgRecord, "msgId"), longField(msgRecord, "msgRandom"),
                    longField(msgRecord, "msgSeq"), longField(msgRecord, "msgTime"),
                    intField(msgRecord, "chatType"), stringField(msgRecord, "senderUid"),
                    stringField(msgRecord, "peerUid"), stringField(msgRecord, "guildId"),
                    msgElement, Integer.valueOf(0));
            Method download = findMethod(
                    service.getClass(), "downloadRichMediaInVisit", requestClass);
            if (download == null) {
                throw new NoSuchMethodException("downloadRichMediaInVisit");
            }
            download.invoke(service, request);
            Log.i(TAG, "QQ original download requested through IRichMediaService");
        }

        private File findQqMediaFile(
                Object service, Object mediaElement,
                boolean video, boolean fileMessage)
                throws ReflectiveOperationException {
            String sourcePath = stringValue(invokeNoArg(
                    mediaElement, video ? "getFilePath" : "getSourcePath"));
            File direct = readableFile(sourcePath);
            if (direct != null) {
                return direct;
            }
            String fileName = stringValue(invokeNoArg(mediaElement, "getFileName"));
            String md5 = stringValue(invokeNoArg(
                    mediaElement, fileMessage ? "getFileMd5"
                            : video ? "getVideoMd5" : "getMd5HexStr"));
            Method directoriesMethod = fileMessage
                    ? findMethod(service.getClass(), "getFileMediaFileDirs")
                    : video
                    ? findMethod(service.getClass(), "getVideoMediaFileDirs")
                    : findMethod(service.getClass(), "getPicMediaFileDirs", boolean.class);
            Object directories = directoriesMethod == null ? null
                    : fileMessage || video ? directoriesMethod.invoke(service)
                    : directoriesMethod.invoke(service, false);
            if (!(directories instanceof Collection)) {
                return null;
            }
            for (Object directoryValue : (Collection<?>) directories) {
                if (!(directoryValue instanceof CharSequence)) {
                    continue;
                }
                File found = findNamedFile(
                        new File(directoryValue.toString()), fileName, md5, 0, new int[]{0});
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private static File findNamedFile(
                File directory, String fileName, String md5, int depth, int[] inspected) {
            if (directory == null || depth > 4 || inspected[0] >= 4_000
                    || !directory.isDirectory() || !directory.canRead()) {
                return null;
            }
            File[] files = directory.listFiles();
            if (files == null) {
                return null;
            }
            for (File file : files) {
                if (++inspected[0] >= 4_000) {
                    return null;
                }
                if (file.isFile() && file.canRead() && file.length() > 0L) {
                    String name = file.getName();
                    if ((!TextUtils.isEmpty(fileName) && name.equalsIgnoreCase(fileName))
                            || (!TextUtils.isEmpty(md5)
                            && name.toLowerCase(java.util.Locale.ROOT)
                            .contains(md5.toLowerCase(java.util.Locale.ROOT)))) {
                        return file;
                    }
                } else if (file.isDirectory()) {
                    File found = findNamedFile(file, fileName, md5, depth + 1, inspected);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        private static File readableFile(String path) {
            if (TextUtils.isEmpty(path)) {
                return null;
            }
            File file = new File(path);
            return file.isFile() && file.canRead() && file.length() > 0L ? file : null;
        }

        private static Object invokeNoArg(Object target, String name)
                throws ReflectiveOperationException {
            Method method = findMethod(target.getClass(), name);
            return method == null ? null : method.invoke(target);
        }

        private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                try {
                    Method method = current.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static Object fieldValue(Object target, String name)
                throws ReflectiveOperationException {
            Class<?> current = target.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    current = current.getSuperclass();
                }
            }
            return null;
        }

        private static long longField(Object target, String name)
                throws ReflectiveOperationException {
            Object value = fieldValue(target, name);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        }

        private static int intField(Object target, String name)
                throws ReflectiveOperationException {
            Object value = fieldValue(target, name);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        private static String stringField(Object target, String name)
                throws ReflectiveOperationException {
            return stringValue(fieldValue(target, name));
        }

        private static String stringValue(Object value) {
            return value instanceof CharSequence ? value.toString() : "";
        }

        private static int numberValue(Object value) {
            return value instanceof Number ? ((Number) value).intValue() : 0;
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
