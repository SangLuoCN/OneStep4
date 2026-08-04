package com.sangluo.onestep.hook;

import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves the downloaded image behind a rendered chat thumbnail. */
final class RenderedMediaSourceResolver {
    private static final String TAG = "OneStep40-MediaResolver";
    private static final int MAX_OBJECTS = 160;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_CONTAINER_ITEMS = 12;
    private static final int MAX_FILE_CANDIDATES = 32;
    private static final String[] SOURCE_ACCESSORS = {
            "getFileInLocal", "getSourceFile", "getLocalFile", "getCacheFile",
            "getFile", "getSourcePath", "getLocalPath", "getFilePath"
    };

    private RenderedMediaSourceResolver() {
    }

    static Result resolve(
            View longPressedView, ImageView imageView,
            View.OnLongClickListener originalLongClickListener) {
        if (longPressedView == null || imageView == null) {
            return null;
        }
        Search search = new Search(
                Math.max(1, imageView.getWidth()), Math.max(1, imageView.getHeight()));
        search.describeRoot("pressed", longPressedView);
        search.describeRoot("image", imageView);
        search.describeRoot("drawable", imageView.getDrawable());
        search.describeRoot("listener", originalLongClickListener);
        search.add(imageView.getDrawable(), 0);
        search.add(originalLongClickListener, 0);
        View current = longPressedView;
        for (int depth = 0; current != null && depth <= MAX_DEPTH; depth++) {
            search.describeRoot("tag" + depth, current.getTag());
            search.add(current.getTag(), 0);
            addKeyedTags(search, current);
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        search.run();
        search.logTrace();
        return search.best;
    }

    private static void addKeyedTags(Search search, View view) {
        try {
            Field field = View.class.getDeclaredField("mKeyedTags");
            field.setAccessible(true);
            Object value = field.get(view);
            if (!(value instanceof SparseArray)) {
                return;
            }
            SparseArray<?> tags = (SparseArray<?>) value;
            int count = Math.min(tags.size(), MAX_CONTAINER_ITEMS);
            for (int index = 0; index < count; index++) {
                search.describeRoot("keyTag", tags.valueAt(index));
                search.add(tags.valueAt(index), 0);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    static final class Result {
        final File file;
        final String remoteUrl;
        final String virtualPath;
        final Object qqMessageItem;
        final String mimeType;
        final int width;
        final int height;

        Result(File file, String mimeType, int width, int height) {
            this.file = file;
            this.remoteUrl = null;
            this.virtualPath = null;
            this.qqMessageItem = null;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }

        Result(String remoteUrl, String virtualPath, String mimeType, int width, int height) {
            this.file = null;
            this.remoteUrl = remoteUrl;
            this.virtualPath = virtualPath;
            this.qqMessageItem = null;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }

        Result(Object qqMessageItem, String mimeType, int width, int height) {
            this.file = null;
            this.remoteUrl = null;
            this.virtualPath = null;
            this.qqMessageItem = qqMessageItem;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }

        boolean isStreamSource() {
            return remoteUrl != null || virtualPath != null || qqMessageItem != null;
        }
    }

    private static final class Node {
        final Object value;
        final int depth;

        Node(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class Search {
        private final int renderedWidth;
        private final int renderedHeight;
        private final ArrayDeque<Node> pending = new ArrayDeque<>();
        private final IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        private final Map<String, Boolean> visitedPaths = new java.util.HashMap<>();
        private int inspectedObjects;
        private int inspectedFiles;
        private Result best;
        private Result bestKnownSource;
        private Object pendingQqMessageItem;
        private Object pendingQqFileMessageItem;
        private int pendingQqWidth;
        private int pendingQqHeight;
        private String pendingQqMimeType = "image/jpeg";
        private boolean weChatObjectSeen;
        private final StringBuilder trace = new StringBuilder();

        Search(int renderedWidth, int renderedHeight) {
            this.renderedWidth = renderedWidth;
            this.renderedHeight = renderedHeight;
        }

        void add(Object value, int depth) {
            if (value != null && depth <= MAX_DEPTH && !visited.containsKey(value)) {
                pending.addLast(new Node(value, depth));
            }
        }

        void describeRoot(String label, Object value) {
            if (value != null) {
                appendTrace(label + '=' + value.getClass().getName());
            }
        }

        void run() {
            while (!pending.isEmpty() && inspectedObjects < MAX_OBJECTS) {
                Node node = pending.removeFirst();
                Object value = node.value;
                if (value == null || visited.put(value, Boolean.TRUE) != null) {
                    continue;
                }
                inspectedObjects++;
                if (value.getClass().getName().startsWith("com.tencent.mm.")) {
                    weChatObjectSeen = true;
                }
                if (inspectedObjects <= 40) {
                    appendTrace("obj=" + value.getClass().getName());
                }
                if (inspectLeaf(value)) {
                    continue;
                }
                inspectKnownMessageModel(value, node.depth);
                inspectContainer(value, node.depth);
                if (node.depth < MAX_DEPTH && shouldInspectFields(value)) {
                    inspectAccessors(value, node.depth);
                    inspectFields(value, node.depth);
                }
            }
            if (bestKnownSource == null && pendingQqMessageItem != null) {
                bestKnownSource = new Result(
                        pendingQqMessageItem, pendingQqMimeType,
                        pendingQqWidth, pendingQqHeight);
            }
            if (bestKnownSource != null) {
                best = bestKnownSource;
            }
        }

        /**
         * QQ and WeChat retain the original media in their message model even when the
         * ImageView only contains a scaled chat thumbnail. Resolve those private models
         * before the generic reflection walk can settle on the rendered Drawable.
         */
        private void inspectKnownMessageModel(Object value, int depth) {
            String className = value.getClass().getName();
            try {
                if ("com.tencent.mobileqq.aio.msg.PicMsgItem".equals(className)) {
                    pendingQqMessageItem = value;
                    Object element = invokeNoArg(value, "L2");
                    if (element != null) {
                        addFirst(element, depth + 1);
                        appendTrace("known=qq.PicMsgItem.L2->" + element.getClass().getName());
                    }
                    return;
                }
                if ("com.tencent.mobileqq.aio.msg.ShortVideoMsgItem".equals(className)) {
                    pendingQqMessageItem = value;
                    pendingQqMimeType = "video/mp4";
                    Object element = invokeNoArg(value, "U2");
                    if (element != null) {
                        addFirst(element, depth + 1);
                        appendTrace("known=qq.ShortVideoMsgItem.U2->"
                                + element.getClass().getName());
                    }
                    inspectQqVideoAccessor(value, "K2");
                    inspectQqVideoAccessor(value, "L2");
                    inspectQqVideoAccessor(value, "P2");
                    inspectQqVideoAccessor(value, "S2");
                    inspectQqVideoAccessor(value, "T2");
                    inspectQqVideoAccessor(value, "W2");
                    inspectQqVideoAccessor(value, "X2");
                    inspectQqVideoAccessor(value, "Y2");
                    inspectQqVideoAccessor(value, "Z2");
                    return;
                }
                if ("com.tencent.mobileqq.aio.msg.FileMsgItem".equals(className)) {
                    pendingQqFileMessageItem = value;
                    Object element = invokeNoArg(value, "M2");
                    if (element != null) {
                        addFirst(element, depth + 1);
                        appendTrace("known=qq.FileMsgItem.M2->"
                                + element.getClass().getName());
                    }
                    return;
                }
                if ("com.tencent.qqnt.kernel.nativeinterface.PicElement".equals(className)) {
                    int width = intValue(invokeNoArg(value, "getPicWidth"));
                    int height = intValue(invokeNoArg(value, "getPicHeight"));
                    pendingQqWidth = width;
                    pendingQqHeight = height;
                    String fileName = stringValue(invokeNoArg(value, "getFileName"));
                    if (!TextUtils.isEmpty(fileName)) {
                        pendingQqMimeType = mimeFromPath(fileName);
                    }
                    String sourcePath = stringValue(invokeNoArg(value, "getSourcePath"));
                    if (TextUtils.isEmpty(sourcePath)) {
                        sourcePath = stringField(value, "sourcePath");
                    }
                    inspectKnownSource(sourcePath, null, width, height, "qq.sourcePath");
                    String originUrl = stringValue(invokeNoArg(value, "getOriginImageUrl"));
                    if (TextUtils.isEmpty(originUrl)) {
                        originUrl = stringField(value, "originImageUrl");
                    }
                    String downloadIndex = stringValue(
                            invokeNoArg(value, "getDownloadIndex"));
                    appendTrace("qq.downloadIndex=" + summarize(downloadIndex));
                    inspectKnownSource(null, originUrl, width, height, "qq.originImageUrl");
                    return;
                }
                if ("com.tencent.qqnt.kernel.nativeinterface.VideoElement".equals(className)) {
                    int width = intValue(invokeNoArg(value, "getThumbWidth"));
                    int height = intValue(invokeNoArg(value, "getThumbHeight"));
                    pendingQqWidth = width;
                    pendingQqHeight = height;
                    pendingQqMimeType = "video/mp4";
                    String sourcePath = stringValue(invokeNoArg(value, "getFilePath"));
                    if (TextUtils.isEmpty(sourcePath)) {
                        sourcePath = stringField(value, "filePath");
                    }
                    inspectKnownSource(
                            sourcePath, null, width, height,
                            "qq.video.filePath", "video/mp4");
                    return;
                }
                if ("com.tencent.qqnt.kernel.nativeinterface.FileElement".equals(className)) {
                    String fileName = stringValue(invokeNoArg(value, "getFileName"));
                    String fileMimeType = mimeFromPath(fileName);
                    if (fileMimeType.startsWith("video/")) {
                        pendingQqMessageItem = pendingQqFileMessageItem;
                        pendingQqMimeType = fileMimeType;
                        int width = intValue(invokeNoArg(value, "getPicWidth"));
                        int height = intValue(invokeNoArg(value, "getPicHeight"));
                        pendingQqWidth = width;
                        pendingQqHeight = height;
                        String sourcePath = stringValue(invokeNoArg(value, "getFilePath"));
                        inspectKnownSource(
                                sourcePath, null, width, height,
                                "qq.fileVideo.filePath", fileMimeType);
                        appendTrace("known=qq.FileElement.video:" + summarize(fileName));
                    }
                    return;
                }
                if (isClassOrSubclass(
                        value.getClass(), "com.tencent.mm.ui.chatting.viewitems.es")) {
                    Object message = invokeNoArg(value, "c");
                    if (message != null) {
                        addFirst(message, depth + 1);
                        appendTrace("known=wechat.es.c->" + message.getClass().getName());
                    }
                    return;
                }
                if ("com.tencent.mm.storage.e9".equals(className)
                        || isWeChatMessageSubclass(value.getClass())) {
                    if (isWeChatVideoMessage(value)) {
                        resolveWeChatVideoMessage(value, depth);
                    } else {
                        resolveWeChatMessage(value);
                    }
                }
            } catch (Throwable error) {
                appendTrace("known-error=" + className + ':' + error.getClass().getSimpleName());
            }
        }

        private void inspectQqVideoAccessor(Object messageItem, String methodName) {
            try {
                String value = stringValue(invokeNoArg(messageItem, methodName));
                if (!TextUtils.isEmpty(value)
                        && (value.startsWith("/") || value.startsWith("file://"))) {
                    inspectKnownSource(value, null, 0, 0,
                            "qq.video." + methodName, "video/mp4");
                }
            } catch (Throwable ignored) {
            }
        }

        private void resolveWeChatMessage(Object message) {
            Object path = invokeWeChatBigImagePath(message);
            String pathValue = stringValue(path);
            if (TextUtils.isEmpty(pathValue)) {
                appendTrace("wechat.bigPath=empty");
                return;
            }
            appendTrace("wechat.bigPath=" + summarize(pathValue));
            if (pathValue.startsWith("/") || pathValue.startsWith("file://")) {
                inspectKnownSource(pathValue, null, 0, 0, "wechat.bigImgPath");
            } else {
                Result candidate = new Result(null, pathValue, "image/jpeg", 0, 0);
                if (bestKnownSource == null) {
                    bestKnownSource = candidate;
                }
            }
        }

        private boolean isWeChatVideoMessage(Object message) {
            try {
                Object video = invokeNoArg(message, "isVideo");
                if (video instanceof Boolean) {
                    return (Boolean) video;
                }
                Object type = invokeNoArg(message, "getType");
                return type instanceof Number
                        && (((Number) type).intValue() == 43
                        || ((Number) type).intValue() == 62);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private void resolveWeChatVideoMessage(Object message, int depth) {
            try {
                String fileName = stringValue(invokeNoArg(message, "y0"));
                if (TextUtils.isEmpty(fileName)) {
                    fileName = stringValue(invokeNoArg(message, "U1"));
                }
                ClassLoader loader = message.getClass().getClassLoader();
                String resolvedPath = resolveWeChatVideoPath(message, fileName, loader);
                if (!TextUtils.isEmpty(resolvedPath)) {
                    inspectKnownSource(
                            resolvedPath, null, 0, 0,
                            "wechat.video.fullPath", "video/mp4");
                }
                Class<?> videoServiceClass = Class.forName(
                        "v21.o2", false, loader);
                Method serviceGetter = findNoArgMethod(videoServiceClass, "kj");
                Object videoService = serviceGetter == null
                        ? null : serviceGetter.invoke(null);
                if (videoService != null) {
                    Method storageGetter = findNoArgMethod(videoServiceClass, "qj");
                    Object storage = storageGetter == null
                            ? null : storageGetter.invoke(videoService);
                    if (storage != null) {
                        Method byFileName = findCompatibleMethod(
                                storage.getClass(), "g", String.class);
                        Object byFileNameRecord = byFileName == null || TextUtils.isEmpty(fileName)
                                ? null : byFileName.invoke(storage, fileName);
                        String recordPath = stringField(byFileNameRecord, "C");
                        if (!TextUtils.isEmpty(recordPath)) {
                            inspectKnownSource(
                                    recordPath, null, 0, 0,
                                    "wechat.video.recordPath", "video/mp4");
                        }
                        Method byMessage = findCompatibleTwoArgumentMethod(
                                storage.getClass(), "h",
                                message.getClass(), String.class);
                        Object records = byMessage == null
                                ? null : byMessage.invoke(storage, message, null);
                        if (records instanceof Collection) {
                            for (Object record : (Collection<?>) records) {
                                String path = stringField(record, "C");
                                if (!TextUtils.isEmpty(path)) {
                                    inspectKnownSource(
                                            path, null, 0, 0,
                                            "wechat.video.recordPath", "video/mp4");
                                }
                                add(record, depth + 1);
                            }
                        }
                        if (TextUtils.isEmpty(resolvedPath)
                                && TextUtils.isEmpty(recordPath)
                                && !TextUtils.isEmpty(fileName)) {
                            Method videoRootMethod = findNoArgMethod(
                                    videoServiceClass, "ij");
                            String root = videoRootMethod == null
                                    ? null : stringValue(videoRootMethod.invoke(videoService));
                            if (!TextUtils.isEmpty(root)) {
                                String separator = root.endsWith("/") ? "" : "/";
                                inspectKnownSource(
                                        root + separator + fileName + ".mp4",
                                        null, 0, 0,
                                        "wechat.video.mp4Fallback", "video/mp4");
                            }
                        }
                    }
                }
                if (!TextUtils.isEmpty(fileName) && isLikelyVideoPath(fileName)) {
                    inspectPath(fileName);
                }
                appendTrace("known=wechat.video:" + summarize(fileName));
            } catch (Throwable error) {
                appendTrace("wechat.video-error=" + error.getClass().getSimpleName());
            }
        }

        private String resolveWeChatVideoPath(
                Object message, String fileName, ClassLoader loader) {
            if (TextUtils.isEmpty(fileName)) {
                return null;
            }
            try {
                Class<?> mediaPathApi = Class.forName("qh3.u0", false, loader);
                Class<?> serviceLocator = Class.forName("pa5.n0", false, loader);
                Method getService = serviceLocator.getDeclaredMethod("c", Class.class);
                getService.setAccessible(true);
                Object service = getService.invoke(null, mediaPathApi);
                if (service == null) {
                    return null;
                }
                Class<?> resourceType = Class.forName("in5.f0", false, loader);
                Field videoField = resourceType.getDeclaredField("s");
                videoField.setAccessible(true);
                Object videoResource = videoField.get(null);
                Class<?> messageType = Class.forName(
                        "com.tencent.mm.storage.e9", false, loader);
                Method fullPath = service.getClass().getMethod(
                        "Fj", messageType, resourceType, String.class, boolean.class);
                String path = stringValue(fullPath.invoke(
                        service, message, videoResource, fileName, false));
                appendTrace("wechat.video.Fj=" + summarize(path));
                return path;
            } catch (Throwable error) {
                appendTrace("wechat.video.Fj-error="
                        + error.getClass().getSimpleName());
                return null;
            }
        }

        private Object invokeWeChatBigImagePath(Object message) {
            try {
                ClassLoader loader = message.getClass().getClassLoader();
                Class<?> storageClass = Class.forName("o11.b1", false, loader);
                Method lj = findNoArgMethod(storageClass, "lj");
                Object storage = lj == null ? null : lj.invoke(null);
                if (storage == null) {
                    return null;
                }
                Method f1 = findCompatibleMethod(storage.getClass(), "F1", message.getClass());
                return f1 == null ? null : f1.invoke(storage, message);
            } catch (Throwable error) {
                appendTrace("wechat.F1-error=" + error.getClass().getSimpleName());
                return null;
            }
        }

        private void inspectKnownSource(
                String path, String url, int width, int height, String label) {
            inspectKnownSource(path, url, width, height, label, null);
        }

        private void inspectKnownSource(
                String path, String url, int width, int height,
                String label, String preferredMimeType) {
            if (!TextUtils.isEmpty(path)) {
                String normalized = path;
                if (normalized.startsWith("file://")) {
                    try {
                        normalized = Uri.parse(normalized).getPath();
                    } catch (RuntimeException ignored) {
                        normalized = null;
                    }
                }
                if (!TextUtils.isEmpty(normalized) && normalized.startsWith("/")) {
                    appendTrace(label + "=" + normalized);
                    Result candidate = decodeKnownFile(
                            new File(normalized), preferredMimeType);
                    if (candidate != null) {
                        bestKnownSource = candidate;
                    } else if (label.startsWith("wechat.") && bestKnownSource == null) {
                        bestKnownSource = new Result(
                                null, normalized,
                                preferredMime(preferredMimeType, normalized), width, height);
                    }
                } else if (!TextUtils.isEmpty(normalized)
                        && label.startsWith("wechat.") && bestKnownSource == null) {
                    bestKnownSource = new Result(
                            null, normalized,
                            preferredMime(preferredMimeType, normalized), width, height);
                }
            }
            if (!TextUtils.isEmpty(url)
                    && (url.startsWith("https://") || url.startsWith("http://"))) {
                appendTrace(label + "=" + summarize(url));
                if (bestKnownSource == null) {
                    bestKnownSource = new Result(url, null,
                            preferredMime(preferredMimeType, url), width, height);
                }
            }
        }

        private Result decodeKnownFile(File file) {
            return decodeKnownFile(file, null);
        }

        private Result decodeKnownFile(File file, String preferredMimeType) {
            if (file == null || !file.isFile() || !file.canRead() || file.length() <= 0L) {
                return null;
            }
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (Exception ignored) {
                path = file.getAbsolutePath();
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);
            appendTrace("known-file=" + options.outWidth + 'x' + options.outHeight
                    + ':' + file.length() + ':' + path);
            if (options.outWidth > 0 && options.outHeight > 0) {
                return new Result(file,
                        TextUtils.isEmpty(options.outMimeType)
                                ? mimeFromPath(path) : options.outMimeType,
                        options.outWidth, options.outHeight);
            }
            String candidateMime = preferredMime(preferredMimeType, path);
            if (!candidateMime.startsWith("video/") && !isLikelyVideoPath(path)) {
                return null;
            }
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(path);
                int width = parsePositiveInt(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int height = parsePositiveInt(retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                String hasVideo = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                if (width <= 0 || height <= 0 || "no".equalsIgnoreCase(hasVideo)) {
                    return null;
                }
                appendTrace("known-video=" + width + 'x' + height
                        + ':' + file.length() + ':' + path);
                return new Result(file,
                        candidateMime.startsWith("video/")
                                ? candidateMime : "video/mp4",
                        width, height);
            } catch (RuntimeException ignored) {
                return null;
            } finally {
                try {
                    retriever.release();
                } catch (IOException | RuntimeException ignored) {
                }
            }
        }

        private static boolean isWeChatMessageSubclass(Class<?> type) {
            return type != null && type.getName().startsWith("com.tencent.mm.storage.")
                    && findNoArgMethod(type, "getMsgId") != null
                    && findNoArgMethod(type, "y0") != null;
        }

        private static boolean isClassOrSubclass(Class<?> type, String className) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                if (className.equals(current.getName())) {
                    return true;
                }
                current = current.getSuperclass();
            }
            return false;
        }

        private static Object invokeNoArg(Object value, String methodName) throws Exception {
            Method method = findNoArgMethod(value.getClass(), methodName);
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(value);
        }

        private static String stringField(Object value, String name) {
            if (value == null) {
                return null;
            }
            Class<?> type = value.getClass();
            while (type != null && type != Object.class) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return stringValue(field.get(value));
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static int intValue(Object value) {
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        private static String stringValue(Object value) {
            return value instanceof CharSequence ? value.toString() : null;
        }

        private void addFirst(Object value, int depth) {
            if (value != null && depth <= MAX_DEPTH && !visited.containsKey(value)) {
                pending.addFirst(new Node(value, depth));
            }
        }

        void logTrace() {
            appendTrace("objects=" + inspectedObjects + ",files=" + inspectedFiles);
            if (best != null) {
                String location = best.file != null ? best.file.getAbsolutePath()
                        : best.remoteUrl != null ? best.remoteUrl
                        : best.virtualPath != null ? best.virtualPath : "qq-kernel-download";
                appendTrace("best=" + best.width + 'x' + best.height + ':' + location);
            }
            String value = trace.toString();
            for (int offset = 0, part = 1; offset < value.length(); part++) {
                int end = Math.min(value.length(), offset + 3000);
                Log.i(TAG, "part=" + part + " " + value.substring(offset, end));
                offset = end;
            }
        }

        private boolean inspectLeaf(Object value) {
            if (value instanceof File) {
                inspectFile((File) value);
                return true;
            }
            if (value instanceof Uri) {
                Uri uri = (Uri) value;
                if ("file".equals(uri.getScheme())) {
                    inspectFile(new File(uri.getPath()));
                }
                return true;
            }
            if (value instanceof URL) {
                URL url = (URL) value;
                if ("file".equals(url.getProtocol())) {
                    inspectFile(new File(url.getPath()));
                }
                return true;
            }
            if (value instanceof CharSequence) {
                inspectPath(value.toString());
                return true;
            }
            Class<?> type = value.getClass();
            return type.isPrimitive() || value instanceof Number
                    || value instanceof Boolean || value instanceof Character
                    || value instanceof Class || type.isEnum();
        }

        private void inspectContainer(Object value, int depth) {
            if (value instanceof SparseArray) {
                SparseArray<?> array = (SparseArray<?>) value;
                for (int index = 0;
                     index < Math.min(array.size(), MAX_CONTAINER_ITEMS); index++) {
                    add(array.valueAt(index), depth + 1);
                }
                return;
            }
            if (value instanceof Map) {
                int count = 0;
                for (Object item : ((Map<?, ?>) value).values()) {
                    add(item, depth + 1);
                    if (++count >= MAX_CONTAINER_ITEMS) {
                        break;
                    }
                }
                return;
            }
            if (value instanceof Collection) {
                int count = 0;
                for (Object item : (Collection<?>) value) {
                    add(item, depth + 1);
                    if (++count >= MAX_CONTAINER_ITEMS) {
                        break;
                    }
                }
                return;
            }
            if (value.getClass().isArray() && !value.getClass().getComponentType().isPrimitive()) {
                int count = Math.min(Array.getLength(value), MAX_CONTAINER_ITEMS);
                for (int index = 0; index < count; index++) {
                    add(Array.get(value, index), depth + 1);
                }
            }
        }

        private void inspectAccessors(Object value, int depth) {
            for (String methodName : SOURCE_ACCESSORS) {
                Method method = findNoArgMethod(value.getClass(), methodName);
                if (method == null) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    add(method.invoke(value), depth + 1);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
        }

        private void inspectFields(Object value, int depth) {
            Class<?> type = value.getClass();
            while (type != null && type != Object.class) {
                String typeName = type.getName();
                if (typeName.startsWith("android.") && !(value instanceof Drawable)) {
                    break;
                }
                Field[] fields;
                try {
                    fields = type.getDeclaredFields();
                } catch (RuntimeException error) {
                    break;
                }
                for (Field field : fields) {
                    if (Modifier.isStatic(field.getModifiers())
                            || field.getType().isPrimitive()) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object fieldValue = field.get(value);
                        if (isInterestingField(field.getName()) && fieldValue != null) {
                            appendTrace("field=" + value.getClass().getSimpleName()
                                    + '.' + field.getName() + ':' + summarize(fieldValue));
                        }
                        add(fieldValue, depth + 1);
                    } catch (IllegalAccessException | RuntimeException ignored) {
                    }
                }
                type = type.getSuperclass();
            }
        }

        private void inspectPath(String value) {
            if (TextUtils.isEmpty(value)) {
                return;
            }
            String path = value;
            if (path.startsWith("file://")) {
                try {
                    path = Uri.parse(path).getPath();
                } catch (RuntimeException ignored) {
                    return;
                }
            }
            if (TextUtils.isEmpty(path) || path.charAt(0) != '/') {
                if (weChatObjectSeen && isLikelyVideoPath(path)
                        && bestKnownSource == null) {
                    bestKnownSource = new Result(
                            null, path, mimeFromPath(path), 0, 0);
                    appendTrace("wechat.video-vfs=" + summarize(path));
                }
                return;
            }
            int query = path.indexOf('?');
            if (query > 0) {
                path = path.substring(0, query);
            }
            inspectFile(new File(path));
        }

        private void inspectFile(File file) {
            if (file == null || inspectedFiles >= MAX_FILE_CANDIDATES) {
                return;
            }
            String path;
            try {
                path = file.getCanonicalPath();
            } catch (Exception ignored) {
                path = file.getAbsolutePath();
            }
            if (visitedPaths.put(path, Boolean.TRUE) != null
                    || !file.isFile() || !file.canRead() || file.length() <= 0L) {
                return;
            }
            inspectedFiles++;
            Result candidate = decodeKnownFile(file);
            if (candidate == null) {
                return;
            }
            long renderedArea = (long) renderedWidth * renderedHeight;
            long candidateArea = (long) candidate.width * candidate.height;
            if (!candidate.mimeType.startsWith("video/")
                    && candidateArea <= renderedArea * 2L) {
                return;
            }
            if (best == null || score(path, candidate.width, candidate.height, file.length())
                    > score(best.file.getPath(), best.width, best.height, best.file.length())) {
                best = candidate;
            }
        }

        private static boolean shouldInspectFields(Object value) {
            if (value instanceof Drawable) {
                return true;
            }
            String name = value.getClass().getName();
            return name.startsWith("com.tencent.")
                    || name.startsWith("kotlin.")
                    || name.contains("LongClick")
                    || name.contains("Message")
                    || name.contains("Image")
                    || name.contains("Video");
        }

        private static boolean isInterestingField(String fieldName) {
            String normalized = fieldName.toLowerCase(Locale.ROOT);
            return normalized.contains("path") || normalized.contains("url")
                    || normalized.contains("file") || normalized.contains("md5")
                    || normalized.contains("msg") || normalized.contains("image")
                    || normalized.contains("thumb") || normalized.contains("origin")
                    || normalized.contains("big") || normalized.contains("localid")
                    || normalized.contains("video");
        }

        private void appendTrace(String value) {
            if (trace.length() >= 6000) {
                return;
            }
            if (trace.length() > 0) {
                trace.append(" | ");
            }
            trace.append(value);
        }

        private static String summarize(Object value) {
            if (value == null) {
                return "null";
            }
            if (!(value instanceof CharSequence) && !(value instanceof Number)
                    && !(value instanceof File) && !(value instanceof Uri)
                    && !(value instanceof URL)) {
                return value.getClass().getName();
            }
            String text = String.valueOf(value).replace('\n', ' ').replace('\r', ' ');
            return text.length() <= 180 ? text : text.substring(0, 180);
        }

        private static long score(String path, int width, int height, long bytes) {
            long score = (long) width * height * 16L + Math.min(bytes, 64L * 1024L * 1024L);
            String normalized = path.toLowerCase(Locale.ROOT);
            if (normalized.contains("origin") || normalized.contains("original")
                    || normalized.contains("large") || normalized.contains("/big")) {
                score += 1L << 50;
            }
            if (normalized.contains("thumb") || normalized.contains("thumbnail")
                    || normalized.contains("small") || normalized.contains("preview")) {
                score -= 1L << 48;
            }
            return score;
        }

        private static String mimeFromPath(String path) {
            String normalized = path.toLowerCase(Locale.ROOT);
            int query = normalized.indexOf('?');
            if (query >= 0) {
                normalized = normalized.substring(0, query);
            }
            if (normalized.endsWith(".png")) {
                return "image/png";
            }
            if (normalized.endsWith(".webp")) {
                return "image/webp";
            }
            if (normalized.endsWith(".gif")) {
                return "image/gif";
            }
            if (normalized.endsWith(".mp4") || normalized.endsWith(".m4v")) {
                return "video/mp4";
            }
            if (normalized.endsWith(".mov")) {
                return "video/quicktime";
            }
            if (normalized.endsWith(".webm")) {
                return "video/webm";
            }
            if (normalized.endsWith(".3gp")) {
                return "video/3gpp";
            }
            if (normalized.endsWith(".mkv")) {
                return "video/x-matroska";
            }
            return "image/jpeg";
        }

        private static String preferredMime(String preferredMimeType, String path) {
            return TextUtils.isEmpty(preferredMimeType)
                    ? mimeFromPath(path) : preferredMimeType;
        }

        private static boolean isLikelyVideoPath(String path) {
            return mimeFromPath(path).startsWith("video/");
        }

        private static int parsePositiveInt(String value) {
            if (TextUtils.isEmpty(value)) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        private static Method findNoArgMethod(Class<?> type, String name) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                try {
                    Method method = current.getDeclaredMethod(name);
                    return method.getParameterTypes().length == 0 ? method : null;
                } catch (NoSuchMethodException ignored) {
                    current = current.getSuperclass();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
            return null;
        }

        private static Method findCompatibleMethod(
                Class<?> type, String name, Class<?> argumentType) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                Method[] methods;
                try {
                    methods = current.getDeclaredMethods();
                } catch (RuntimeException error) {
                    return null;
                }
                for (Method method : methods) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (method.getName().equals(name) && parameterTypes.length == 1
                            && parameterTypes[0].isAssignableFrom(argumentType)) {
                        try {
                            method.setAccessible(true);
                        } catch (RuntimeException ignored) {
                        }
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }

        private static Method findCompatibleTwoArgumentMethod(
                Class<?> type, String name,
                Class<?> firstArgumentType, Class<?> secondArgumentType) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                Method[] methods;
                try {
                    methods = current.getDeclaredMethods();
                } catch (RuntimeException error) {
                    return null;
                }
                for (Method method : methods) {
                    Class<?>[] parameterTypes = method.getParameterTypes();
                    if (method.getName().equals(name) && parameterTypes.length == 2
                            && parameterTypes[0].isAssignableFrom(firstArgumentType)
                            && parameterTypes[1].isAssignableFrom(secondArgumentType)) {
                        try {
                            method.setAccessible(true);
                        } catch (RuntimeException ignored) {
                        }
                        return method;
                    }
                }
                current = current.getSuperclass();
            }
            return null;
        }
    }
}
