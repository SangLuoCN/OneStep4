package com.sangluo.onestep.hook;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.sangluo.onestep.feature.drag.ImageDragBridgeClient;
import com.sangluo.onestep.feature.drag.ImageDragFeatureGate;
import com.sangluo.onestep.feature.drag.ImageDragSourcePolicy;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Generic source-side media extraction for apps that render content in ImageView. */
public final class OneStepUniversalImageDragHook {
    private static final String TAG = "OneStep40-UniversalDrag";
    private static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";
    private static final String QQ_PACKAGE = "com.tencent.mobileqq";
    private static final String GOOGLE_PHOTOS_PACKAGE =
            ImageDragSourcePolicy.GOOGLE_PHOTOS_PACKAGE;
    private static final long LONG_PRESS_DEDUP_MS = 1500L;
    private static final long IMAGE_URI_RETENTION_MS = 5000L;
    private static final long QQ_TEMP_REDIRECT_MS = 12_000L;
    private static final int MIN_IMAGE_VIEW_DP = 56;
    private static final int MAX_MEDIA_SEARCH_DEPTH = 6;
    private static final int MAX_DRAWABLE_BITMAP_EDGE_PX = 2048;
    private static final long MAX_IMAGE_BYTES = 512L * 1024L * 1024L;

    private static final AtomicBoolean INSTALL_REQUESTED = new AtomicBoolean();
    private static final ExecutorService TRANSFER_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneStep-universal-image-transfer");
                thread.setDaemon(true);
                return thread;
            });
    private static final Map<View, Uri> IMAGE_URIS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Long> IMAGE_URI_TIMES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, View.OnLongClickListener> WRAPPED_LONG_CLICK_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, View.OnLongClickListener> ORIGINAL_LONG_CLICK_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Boolean> QQ_PATH_HOOKED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Boolean> QQ_COMPRESSION_HOOKED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean QQ_CLASS_WATCHER_INSTALLED = new AtomicBoolean();
    private static volatile long qqTempRedirectUntilUptime;
    private static volatile WeakReference<View> lastLongPressedView =
            new WeakReference<>(null);
    private static volatile long lastLongPressUptime;
    private static final ThreadLocal<Boolean> IN_SET_IMAGE_URI =
            new ThreadLocal<>();

    private OneStepUniversalImageDragHook() {
    }

    /** Binary-compatible entry used by the standalone Zygisk payload. */
    public static void install(String packageName, String processName) {
        install(packageName, processName, null);
    }

    public static void install(
            String packageName, String processName, ClassLoader hostClassLoader) {
        if (!ImageDragFeatureGate.isEnabled()
                || !shouldInstall(packageName, processName)
                || !INSTALL_REQUESTED.compareAndSet(false, true)) {
            return;
        }
        try {
            hookImageSourceMethods();
            hookQqClassLoading(packageName);
            hookQqLegacyThumbnailPath(
                    packageName, hostClassLoader != null
                            ? hostClassLoader : loadClassLoader(packageName));
            hookLongPress();
            hookNativeDragStart();
            Log.i(TAG, "Universal image drag hooks installed for " + packageName);
        } catch (Throwable throwable) {
            INSTALL_REQUESTED.set(false);
            Log.e(TAG, "Universal image drag hook installation failed for " + packageName,
                    throwable);
        }
    }

    private static ClassLoader loadClassLoader(String packageName) {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            if (application instanceof android.app.Application
                    && packageName.equals(((android.app.Application) application).getPackageName())) {
                return ((android.app.Application) application).getClassLoader();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader == null
                ? OneStepUniversalImageDragHook.class.getClassLoader() : contextLoader;
    }

    /**
     * QQ 9.x still derives temporary thumbnail paths below the pre-scoped-storage Tencent
     * directory. Redirect only that temporary output to QQ's app-specific external directory;
     * the message original remains owned and downloaded by QQ's rich-media service.
     */
    private static void hookQqLegacyThumbnailPath(
            String packageName, ClassLoader classLoader) {
        if (!QQ_PACKAGE.equals(packageName) || classLoader == null) {
            return;
        }
        try {
            Class<?> pathClass = Class.forName(
                    "com.tencent.mobileqq.pic.compress.f", false, classLoader);
            hookQqLegacyThumbnailPathClass(pathClass);
            Class<?> compressionClass = Class.forName(
                    "com.tencent.mobileqq.pic.compress.d", false, classLoader);
            hookQqCompressionDestinationClass(compressionClass);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "QQ legacy thumbnail path hook unavailable", e);
        }
    }

    private static void hookQqClassLoading(String packageName) throws NoSuchMethodException {
        if (!QQ_PACKAGE.equals(packageName)
                || !QQ_CLASS_WATCHER_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        Method loadClass = ClassLoader.class.getDeclaredMethod(
                "loadClass", String.class, boolean.class);
        loadClass.setAccessible(true);
        XposedBridge.hookMethod(loadClass, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.hasThrowable() || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof String)
                        || !(param.getResult() instanceof Class<?>)) {
                    return;
                }
                hookLoadedQqCompressionClass(
                        (String) param.args[0], (Class<?>) param.getResult());
            }
        });
        HookBridgeCompat.deoptimizeMethod(loadClass);
        try {
            Class<?> baseDexClassLoader = Class.forName("dalvik.system.BaseDexClassLoader");
            Method findClass = baseDexClassLoader.getDeclaredMethod("findClass", String.class);
            findClass.setAccessible(true);
            XposedBridge.hookMethod(findClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || param.args == null || param.args.length == 0
                            || !(param.args[0] instanceof String)
                            || !(param.getResult() instanceof Class<?>)) {
                        return;
                    }
                    hookLoadedQqCompressionClass(
                            (String) param.args[0], (Class<?>) param.getResult());
                }
            });
            HookBridgeCompat.deoptimizeMethod(findClass);
        } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException e) {
            Log.w(TAG, "BaseDexClassLoader watcher unavailable", e);
        }
        Log.i(TAG, "QQ compression ClassLoader watcher installed");
    }

    private static void hookLoadedQqCompressionClass(String name, Class<?> loadedClass) {
        try {
            if ("com.tencent.mobileqq.pic.compress.f".equals(name)) {
                hookQqLegacyThumbnailPathClass(loadedClass);
            } else if ("com.tencent.mobileqq.pic.compress.d".equals(name)) {
                hookQqCompressionDestinationClass(loadedClass);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Cannot hook dynamically loaded QQ compression class: " + name, e);
        }
    }

    private static void hookQqLegacyThumbnailPathClass(Class<?> pathClass)
            throws ReflectiveOperationException {
        synchronized (QQ_PATH_HOOKED) {
            if (QQ_PATH_HOOKED.containsKey(pathClass)) {
                return;
            }
        }
        Method pathMethod = pathClass.getDeclaredMethod(
                    "o", String.class, boolean.class);
        pathMethod.setAccessible(true);
        XposedBridge.hookMethod(pathMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object result = param.getResult();
                if (!(result instanceof String) || !isQqTempRedirectActive()) {
                    return;
                }
                String legacyPath = (String) result;
                String prefix = "/storage/emulated/0/Tencent/MobileQQ/chatpic/Temp/";
                if (!legacyPath.startsWith(prefix)) {
                    return;
                }
                File redirected = qqTemporaryFile(
                        legacyPath.substring(prefix.length()));
                param.setResult(redirected.getAbsolutePath());
                Log.i(TAG, "Redirected QQ temporary thumbnail path: "
                        + legacyPath + " -> " + redirected.getAbsolutePath());
            }
        });
        HookBridgeCompat.deoptimizeMethod(pathMethod);
        synchronized (QQ_PATH_HOOKED) {
            QQ_PATH_HOOKED.put(pathClass, Boolean.TRUE);
        }
        Log.i(TAG, "QQ legacy thumbnail path hook installed: loader="
                + pathClass.getClassLoader());
    }

    private static void hookQqCompressionDestinationClass(Class<?> compressionClass)
            throws ReflectiveOperationException {
        synchronized (QQ_COMPRESSION_HOOKED) {
            if (QQ_COMPRESSION_HOOKED.containsKey(compressionClass)) {
                return;
            }
        }
        Method compress = compressionClass.getDeclaredMethod("b");
        Field infoField = compressionClass.getDeclaredField("k");
        Class<?> infoClass = infoField.getType();
        Field sourceField = infoClass.getDeclaredField("o");
        Field destinationField = infoClass.getDeclaredField("s");
        compress.setAccessible(true);
        infoField.setAccessible(true);
        sourceField.setAccessible(true);
        destinationField.setAccessible(true);
        XposedBridge.hookMethod(compress, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                    throws IllegalAccessException {
                if (!isQqTempRedirectActive()) {
                    return;
                }
                Object info = infoField.get(param.thisObject);
                if (info == null) {
                    return;
                }
                Object sourceValue = sourceField.get(info);
                String source = sourceValue instanceof String ? (String) sourceValue : "";
                if (!TextUtils.isEmpty(source)) {
                    ImageDragBridgeClient.publishQqOriginalCandidate(source);
                }
                Object destinationValue = destinationField.get(info);
                String legacyPrefix =
                        "/storage/emulated/0/Tencent/MobileQQ/chatpic/Temp/";
                if (destinationValue instanceof String
                        && ((String) destinationValue).startsWith(legacyPrefix)) {
                    File redirected = qqTemporaryFile(
                            ((String) destinationValue).substring(legacyPrefix.length()));
                    destinationField.set(info, redirected.getAbsolutePath());
                    Log.i(TAG, "Replaced existing QQ CompressInfo destination: "
                            + redirected.getAbsolutePath());
                    return;
                }
                if (destinationValue != null) {
                    return;
                }
                if (TextUtils.isEmpty(source)) {
                    return;
                }
                String suffix = "Cache_" + Integer.toHexString(source.hashCode())
                        + '_' + SystemClock.uptimeMillis() + ".jpg";
                File redirected = qqTemporaryFile(suffix);
                destinationField.set(info, redirected.getAbsolutePath());
                Log.i(TAG, "Redirected QQ CompressInfo destination: "
                        + redirected.getAbsolutePath());
            }
        });
        HookBridgeCompat.deoptimizeMethod(compress);
        synchronized (QQ_COMPRESSION_HOOKED) {
            QQ_COMPRESSION_HOOKED.put(compressionClass, Boolean.TRUE);
        }
        Log.i(TAG, "QQ compression destination hook installed: loader="
                + compressionClass.getClassLoader());
    }

    private static boolean isQqTempRedirectActive() {
        return SystemClock.uptimeMillis() <= qqTempRedirectUntilUptime;
    }

    private static File qqTemporaryFile(String suffix) {
        File redirected = new File(
                "/storage/emulated/0/Download/OneStep4/qq/" + suffix);
        File parent = redirected.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return redirected;
    }

    private static boolean shouldInstall(String packageName, String processName) {
        return !TextUtils.isEmpty(packageName)
                && TextUtils.equals(packageName, processName)
                && !TextUtils.equals(packageName, ONE_STEP_PACKAGE)
                && !TextUtils.equals(packageName, GOOGLE_PHOTOS_PACKAGE)
                && !TextUtils.equals(packageName, "android")
                && !packageName.startsWith("com.android.systemui")
                && !packageName.startsWith("com.android.settings")
                && !packageName.startsWith("com.miui.home");
    }

    private static void hookImageSourceMethods() throws NoSuchMethodException {
        Method setImageUri = ImageView.class.getDeclaredMethod("setImageURI", Uri.class);
        setImageUri.setAccessible(true);
        XposedBridge.hookMethod(setImageUri, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                IN_SET_IMAGE_URI.set(Boolean.TRUE);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View)) {
                    IN_SET_IMAGE_URI.remove();
                    return;
                }
                Uri uri = param.args != null && param.args.length > 0
                        && param.args[0] instanceof Uri ? (Uri) param.args[0] : null;
                setImageUri((View) param.thisObject, uri);
                IN_SET_IMAGE_URI.remove();
            }
        });
        HookBridgeCompat.deoptimizeMethod(setImageUri);

        Method setImageDrawable = ImageView.class.getDeclaredMethod(
                "setImageDrawable", Drawable.class);
        setImageDrawable.setAccessible(true);
        XposedBridge.hookMethod(setImageDrawable, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) {
                    View view = (View) param.thisObject;
                    if (param.args == null || param.args.length == 0
                            || param.args[0] == null || !hasRecentImageUri(view)) {
                        clearImageUri(view);
                    }
                }
            }
        });
        HookBridgeCompat.deoptimizeMethod(setImageDrawable);

        Method setImageBitmap = ImageView.class.getDeclaredMethod(
                "setImageBitmap", Bitmap.class);
        setImageBitmap.setAccessible(true);
        XposedBridge.hookMethod(setImageBitmap, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) {
                    View view = (View) param.thisObject;
                    if (!hasRecentImageUri(view)) {
                        clearImageUri(view);
                    }
                }
            }
        });
        HookBridgeCompat.deoptimizeMethod(setImageBitmap);

        Method setImageResource = ImageView.class.getDeclaredMethod(
                "setImageResource", int.class);
        setImageResource.setAccessible(true);
        XposedBridge.hookMethod(setImageResource, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject instanceof View) {
                    clearImageUri((View) param.thisObject);
                }
            }
        });
        HookBridgeCompat.deoptimizeMethod(setImageResource);
    }

    private static void hookLongPress() throws NoSuchMethodException {
        Method performLongClick = View.class.getDeclaredMethod("performLongClick");
        performLongClick.setAccessible(true);
        XposedBridge.hookMethod(performLongClick, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                interceptLongPress(param);
            }
        });
        HookBridgeCompat.deoptimizeMethod(performLongClick);
        try {
            Method performLongClickWithFlags = View.class.getDeclaredMethod(
                    "performLongClick", int.class);
            performLongClickWithFlags.setAccessible(true);
            XposedBridge.hookMethod(performLongClickWithFlags, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    interceptLongPress(param);
                }
            });
            HookBridgeCompat.deoptimizeMethod(performLongClickWithFlags);
        } catch (NoSuchMethodException ignored) {
            // The flags overload is not available on older Android releases.
        }

        Method setOnLongClickListener = View.class.getDeclaredMethod(
                "setOnLongClickListener", View.OnLongClickListener.class);
        setOnLongClickListener.setAccessible(true);
        XposedBridge.hookMethod(setOnLongClickListener, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.thisObject instanceof View) || param.args == null
                        || param.args.length == 0
                        || !(param.args[0] instanceof View.OnLongClickListener)) {
                    return;
                }
                View view = (View) param.thisObject;
                View.OnLongClickListener original =
                        (View.OnLongClickListener) param.args[0];
                View.OnLongClickListener wrapped = new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View clickedView) {
                        if (tryStartImageTransfer(clickedView)) {
                            return true;
                        }
                        return original.onLongClick(clickedView);
                    }
                };
                ORIGINAL_LONG_CLICK_LISTENERS.put(view, original);
                WRAPPED_LONG_CLICK_LISTENERS.put(view, wrapped);
                param.args[0] = wrapped;
            }
        });
        HookBridgeCompat.deoptimizeMethod(setOnLongClickListener);
    }

    private static void hookNativeDragStart() throws NoSuchMethodException {
        Method startDragAndDrop = View.class.getDeclaredMethod(
                "startDragAndDrop", ClipData.class, View.DragShadowBuilder.class,
                Object.class, int.class);
        startDragAndDrop.setAccessible(true);
        XposedBridge.hookMethod(startDragAndDrop, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                interceptNativeDrag(param);
            }
        });
        HookBridgeCompat.deoptimizeMethod(startDragAndDrop);
    }

    private static void interceptNativeDrag(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof View)
                || param.args == null || param.args.length == 0
                || !(param.args[0] instanceof ClipData)) {
            return;
        }
        View sourceView = (View) param.thisObject;
        Display display = sourceView.getDisplay();
        if (display == null || !ImageDragSourcePolicy.isAllowed(
                sourceView.getContext().getPackageName(), display.getDisplayId())) {
            return;
        }
        ClipData clipData = (ClipData) param.args[0];
        Uri uri = firstMediaUri(clipData);
        if (uri == null) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        View previous = lastLongPressedView.get();
        if (previous == sourceView && now - lastLongPressUptime < LONG_PRESS_DEDUP_MS) {
            param.setResult(true);
            return;
        }
        String mimeType = firstMediaMimeType(clipData.getDescription());
        if (TextUtils.isEmpty(mimeType)) {
            return;
        }
        boolean bound = ImageDragBridgeClient.transfer(
                sourceView.getContext(), uri, display.getDisplayId(),
                sourceView.getContext().getPackageName(), mimeType,
                MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
        if (bound) {
            lastLongPressedView = new WeakReference<>(sourceView);
            lastLongPressUptime = now;
            param.setResult(true);
            Log.i(TAG, "Native media drag intercepted: package="
                    + sourceView.getContext().getPackageName()
                    + ", display=" + display.getDisplayId() + ", uri=" + uri);
        }
    }

    private static void interceptLongPress(XC_MethodHook.MethodHookParam param) {
        if (!(param.thisObject instanceof View)) {
            return;
        }
        View longPressedView = (View) param.thisObject;
        Display display = longPressedView.getDisplay();
        if (display == null || !ImageDragSourcePolicy.isAllowed(
                longPressedView.getContext().getPackageName(), display.getDisplayId())) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        View previous = lastLongPressedView.get();
        if (previous == longPressedView && now - lastLongPressUptime < LONG_PRESS_DEDUP_MS) {
            param.setResult(true);
            return;
        }
        boolean bound = tryStartImageTransfer(longPressedView);
        if (bound) {
            lastLongPressedView = new WeakReference<>(longPressedView);
            lastLongPressUptime = now;
            param.setResult(true);
            Log.i(TAG, "Universal image long press consumed: package="
                    + longPressedView.getContext().getPackageName()
                    + ", display=" + display.getDisplayId());
        }
    }

    private static boolean tryStartImageTransfer(View longPressedView) {
        if (longPressedView == null) {
            return false;
        }
        ImageView imageView = findMediaImageView(longPressedView, 0);
        Display display = longPressedView.getDisplay();
        if (display == null || !ImageDragSourcePolicy.isAllowed(
                longPressedView.getContext().getPackageName(), display.getDisplayId())
                || imageView == null || !isLikelyMediaView(imageView)) {
            return false;
        }
        Uri uri = imageUri(imageView);
        String mimeType = resolveMimeType(imageView.getContext().getContentResolver(), uri);
        boolean bound = false;
        try {
            if (uri != null && "content".equals(uri.getScheme())) {
                bound = ImageDragBridgeClient.transfer(
                        longPressedView.getContext(), uri, display.getDisplayId(),
                        longPressedView.getContext().getPackageName(), mimeType,
                        MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
            }
            if (!bound) {
                long resolveStarted = SystemClock.uptimeMillis();
                RenderedMediaSourceResolver.Result source =
                        RenderedMediaSourceResolver.resolve(
                                longPressedView, imageView,
                                originalLongClickListener(longPressedView));
                if (source != null) {
                    if (source.file != null) {
                        bound = ImageDragBridgeClient.transferFile(
                                longPressedView.getContext(), source.file,
                                display.getDisplayId(),
                                longPressedView.getContext().getPackageName(),
                                source.mimeType, MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
                    } else if (!TextUtils.isEmpty(source.remoteUrl)) {
                        bound = ImageDragBridgeClient.transferUrl(
                                longPressedView.getContext(), source.remoteUrl,
                                display.getDisplayId(),
                                longPressedView.getContext().getPackageName(),
                                source.mimeType, MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
                    } else if (!TextUtils.isEmpty(source.virtualPath)) {
                        bound = ImageDragBridgeClient.transferWeChatPath(
                                longPressedView.getContext(), source.virtualPath,
                                display.getDisplayId(),
                                longPressedView.getContext().getPackageName(),
                                source.mimeType, MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
                    } else if (source.qqMessageItem != null) {
                        hookQqLegacyThumbnailPath(
                                longPressedView.getContext().getPackageName(),
                                loadClassLoader(
                                        longPressedView.getContext().getPackageName()));
                        hookQqLegacyThumbnailPath(
                                longPressedView.getContext().getPackageName(),
                                source.qqMessageItem.getClass().getClassLoader());
                        qqTempRedirectUntilUptime = SystemClock.uptimeMillis()
                                + QQ_TEMP_REDIRECT_MS;
                        bound = ImageDragBridgeClient.transferQqMessage(
                                longPressedView.getContext(), source.qqMessageItem,
                                display.getDisplayId(),
                                longPressedView.getContext().getPackageName(),
                                source.mimeType, MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
                    }
                    if (bound) {
                        mimeType = source.mimeType;
                        long sourceBytes = source.file == null ? -1L : source.file.length();
                        Log.i(TAG, "Resolved original message image: package="
                                + longPressedView.getContext().getPackageName()
                                + ", pixels=" + source.width + "x" + source.height
                                + ", bytes=" + sourceBytes
                                + ", kind=" + (source.file != null ? "file"
                                : source.remoteUrl != null ? "remote"
                                : source.virtualPath != null ? "wechat-vfs" : "qq-kernel")
                                + ", resolveMs="
                                + (SystemClock.uptimeMillis() - resolveStarted));
                    }
                }
            }
            if (!bound) {
                Log.w(TAG, "Original message image unavailable; thumbnail transfer suppressed: "
                        + longPressedView.getContext().getPackageName());
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Universal image extraction failed for "
                    + longPressedView.getContext().getPackageName(), e);
        }
        if (bound) {
            lastLongPressedView = new WeakReference<>(longPressedView);
            lastLongPressUptime = SystemClock.uptimeMillis();
            Log.i(TAG, "Universal image transfer bound: package="
                    + longPressedView.getContext().getPackageName()
                    + ", display=" + display.getDisplayId()
                    + ", uri=" + (uri == null ? "bitmap" : uri));
        }
        return bound;
    }

    private static View.OnLongClickListener originalLongClickListener(View view) {
        View current = view;
        for (int depth = 0; current != null && depth <= MAX_MEDIA_SEARCH_DEPTH; depth++) {
            View.OnLongClickListener listener = ORIGINAL_LONG_CLICK_LISTENERS.get(current);
            if (listener != null) {
                return listener;
            }
            android.view.ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static ImageView findMediaImageView(View view, int depth) {
        if (view instanceof ImageView) {
            return (ImageView) view;
        }
        if (!(view instanceof ViewGroup) || depth >= MAX_MEDIA_SEARCH_DEPTH) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        ImageView best = null;
        long bestArea = 0L;
        for (int index = 0; index < group.getChildCount(); index++) {
            ImageView candidate = findMediaImageView(group.getChildAt(index), depth + 1);
            if (candidate == null || !isLikelyMediaView(candidate)) {
                continue;
            }
            long area = (long) candidate.getWidth() * candidate.getHeight();
            if (area > bestArea) {
                best = candidate;
                bestArea = area;
            }
        }
        return best;
    }

    private static boolean isLikelyMediaView(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (!imageView.isShown() || !imageView.isEnabled() || drawable == null
                || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
            return false;
        }
        float density = imageView.getResources().getDisplayMetrics().density;
        int minSize = Math.round(MIN_IMAGE_VIEW_DP * density);
        return Math.min(imageView.getWidth(), imageView.getHeight()) >= minSize;
    }

    private static BitmapExtraction currentBitmap(ImageView imageView) {
        Drawable drawable = imageView.getDrawable();
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            return bitmap == null || bitmap.isRecycled()
                    ? null : new BitmapExtraction(bitmap, false, "bitmap-drawable");
        }
        if (drawable == null) {
            return null;
        }
        Bitmap nestedBitmap = largestNestedBitmap(drawable);
        if (nestedBitmap != null) {
            return new BitmapExtraction(nestedBitmap, false, "nested-bitmap");
        }
        int width = Math.max(1,
                Math.max(imageView.getWidth(), drawable.getIntrinsicWidth()));
        int height = Math.max(1,
                Math.max(imageView.getHeight(), drawable.getIntrinsicHeight()));
        float scale = Math.min(1f, (float) MAX_DRAWABLE_BITMAP_EDGE_PX
                / Math.max(width, height));
        int bitmapWidth = Math.max(1, Math.round(width * scale));
        int bitmapHeight = Math.max(1, Math.round(height * scale));
        Bitmap bitmap = Bitmap.createBitmap(
                bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Rect originalBounds = new Rect(drawable.getBounds());
        try {
            drawable.setBounds(0, 0, bitmapWidth, bitmapHeight);
            drawable.draw(new Canvas(bitmap));
            return new BitmapExtraction(bitmap, true, "intrinsic-render");
        } catch (RuntimeException e) {
            bitmap.recycle();
            Log.w(TAG, "Cannot render custom image drawable", e);
            return null;
        } finally {
            drawable.setBounds(originalBounds);
        }
    }

    private static Bitmap largestNestedBitmap(Drawable root) {
        ArrayDeque<Object> pending = new ArrayDeque<>();
        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        pending.add(root);
        Bitmap best = null;
        int inspected = 0;
        while (!pending.isEmpty() && inspected++ < 64) {
            Object value = pending.removeFirst();
            if (value == null || visited.put(value, Boolean.TRUE) != null) {
                continue;
            }
            if (value instanceof Bitmap) {
                Bitmap bitmap = (Bitmap) value;
                if (!bitmap.isRecycled() && (best == null
                        || (long) bitmap.getWidth() * bitmap.getHeight()
                        > (long) best.getWidth() * best.getHeight())) {
                    best = bitmap;
                }
                continue;
            }
            Class<?> type = value.getClass();
            while (type != null && type != Object.class) {
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
                        Object nested = field.get(value);
                        if (nested instanceof Bitmap || nested instanceof Drawable) {
                            pending.addLast(nested);
                        }
                    } catch (IllegalAccessException | RuntimeException ignored) {
                    }
                }
                type = type.getSuperclass();
            }
        }
        return best;
    }

    private static final class BitmapExtraction {
        final Bitmap bitmap;
        final boolean owned;
        final String source;

        BitmapExtraction(Bitmap bitmap, boolean owned, String source) {
            this.bitmap = bitmap;
            this.owned = owned;
            this.source = source;
        }
    }

    private static Uri imageUri(ImageView imageView) {
        synchronized (IMAGE_URIS) {
            return IMAGE_URIS.get(imageView);
        }
    }

    private static void setImageUri(View view, Uri uri) {
        synchronized (IMAGE_URIS) {
            if (uri == null || !"content".equals(uri.getScheme())) {
                IMAGE_URIS.remove(view);
                IMAGE_URI_TIMES.remove(view);
            } else {
                IMAGE_URIS.put(view, uri);
                IMAGE_URI_TIMES.put(view, SystemClock.uptimeMillis());
            }
        }
    }

    private static boolean hasRecentImageUri(View view) {
        if (Boolean.TRUE.equals(IN_SET_IMAGE_URI.get())) {
            return true;
        }
        synchronized (IMAGE_URIS) {
            Long updatedAt = IMAGE_URI_TIMES.get(view);
            return updatedAt != null
                    && SystemClock.uptimeMillis() - updatedAt <= IMAGE_URI_RETENTION_MS;
        }
    }

    private static void clearImageUri(View view) {
        synchronized (IMAGE_URIS) {
            IMAGE_URIS.remove(view);
            IMAGE_URI_TIMES.remove(view);
        }
    }

    private static String resolveMimeType(ContentResolver resolver, Uri uri) {
        if (resolver != null && uri != null) {
            try {
                String mimeType = resolver.getType(uri);
                if (ImageDragSourcePolicy.isSupportedMediaMimeType(mimeType)) {
                    return mimeType;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return "image/*";
    }

    private static Uri firstMediaUri(ClipData clipData) {
        if (clipData == null) {
            return null;
        }
        for (int index = 0; index < clipData.getItemCount(); index++) {
            Uri uri = clipData.getItemAt(index).getUri();
            if (uri != null && "content".equals(uri.getScheme())) {
                return uri;
            }
        }
        return null;
    }

    private static String firstMediaMimeType(ClipDescription description) {
        if (description == null) {
            return "image/*";
        }
        for (int index = 0; index < description.getMimeTypeCount(); index++) {
            String mimeType = description.getMimeType(index);
            if (ImageDragSourcePolicy.isSupportedMediaMimeType(mimeType)) {
                return mimeType;
            }
        }
        return null;
    }
}
