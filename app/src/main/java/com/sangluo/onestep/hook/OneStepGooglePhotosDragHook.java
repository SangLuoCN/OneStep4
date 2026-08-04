package com.sangluo.onestep.hook;

import android.app.Application;
import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.View;

import com.sangluo.onestep.feature.drag.ImageDragBridgeClient;
import com.sangluo.onestep.feature.drag.ImageDragFeatureGate;
import com.sangluo.onestep.feature.drag.ImageDragSourcePolicy;
import com.sangluo.onestep.feature.drag.GooglePhotosMediaUriResolver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/** Reuses Google Photos' MediaModel-to-ClipData pipeline for OneStep media dragging. */
public final class OneStepGooglePhotosDragHook {
    private static final String TAG = "OneStep40-GPhotos";
    private static final long EXPECTED_DRAG_TIMEOUT_MS = 15_000L;
    private static final long MAX_IMAGE_BYTES = 512L * 1024L * 1024L;

    private static final AtomicBoolean INSTALL_REQUESTED = new AtomicBoolean();
    private static final AtomicBoolean INTERNAL_HOOKS_INSTALLED = new AtomicBoolean();
    private static final ExecutorService TRANSFER_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneStep-GooglePhotos-transfer");
                thread.setDaemon(true);
                return thread;
            });

    private static volatile WeakReference<View> expectedSourceView =
            new WeakReference<>(null);
    private static volatile long expectedDragDeadline;

    private static Field holderItemField;
    private static Field holderViewField;
    private static Field mediaItemMediaField;
    private static Field selectionDragStateField;
    private static Field selectionManagerProviderField;
    private static Method mediaGetFeatureMethod;
    private static Method featureGetMediaModelMethod;
    private static Method providerGetMethod;
    private static Method singletonListMethod;
    private static Method managerStartMethod;

    private OneStepGooglePhotosDragHook() {
    }

    public static void install(String packageName, String processName) {
        if (!ImageDragFeatureGate.isEnabled()
                || !TextUtils.equals(ImageDragSourcePolicy.GOOGLE_PHOTOS_PACKAGE, packageName)
                || !TextUtils.equals(packageName, processName)
                || !INSTALL_REQUESTED.compareAndSet(false, true)) {
            return;
        }
        try {
            HookBridgeCompat.disableHiddenApiRestrictions();
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            XposedBridge.hookMethod(attach, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    installForApplication(param.thisObject);
                }
            });
            HookBridgeCompat.deoptimizeMethod(attach);

            Method callApplicationOnCreate = Instrumentation.class.getDeclaredMethod(
                    "callApplicationOnCreate", Application.class);
            callApplicationOnCreate.setAccessible(true);
            XposedBridge.hookMethod(callApplicationOnCreate, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object application = param.args == null || param.args.length == 0
                            ? null : param.args[0];
                    installForApplication(application);
                }
            });
            HookBridgeCompat.deoptimizeMethod(callApplicationOnCreate);
            installForApplication(currentApplication());
            Log.i(TAG, "Google Photos application bootstrap hooks installed");
        } catch (Throwable throwable) {
            INSTALL_REQUESTED.set(false);
            Log.e(TAG, "Google Photos attach hook installation failed", throwable);
        }
    }

    private static void installForApplication(Object candidate) {
        if (!(candidate instanceof Application)) {
            return;
        }
        Application application = (Application) candidate;
        installInternal(application, application.getClassLoader());
    }

    private static Application currentApplication() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method currentApplication = activityThread.getDeclaredMethod("currentApplication");
            currentApplication.setAccessible(true);
            Object application = currentApplication.invoke(null);
            return application instanceof Application ? (Application) application : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.d(TAG, "Google Photos Application is not available yet");
            return null;
        }
    }

    private static void installInternal(Context context, ClassLoader classLoader) {
        if (!INTERNAL_HOOKS_INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> selectionBehaviorClass = Class.forName("aeow", false, classLoader);
            Class<?> holderClass = Class.forName("aepd", false, classLoader);
            Class<?> mediaItemClass = Class.forName("aeni", false, classLoader);
            Class<?> mediaContainerClass = Class.forName("_1874", false, classLoader);
            Class<?> mediaFeatureClass = Class.forName("_198", false, classLoader);
            Class<?> mediaModelClass = Class.forName(
                    "com.google.android.apps.photos.mediamodel.MediaModel",
                    false, classLoader);
            Class<?> providerClass = Class.forName("ytj", false, classLoader);
            Class<?> immutableListClass = Class.forName("bboe", false, classLoader);
            Class<?> managerClass = Class.forName("vcy", false, classLoader);

            holderItemField = findField(holderClass, "aa");
            holderViewField = findField(holderClass, "a");
            mediaItemMediaField = findField(mediaItemClass, "a");
            selectionDragStateField = findField(selectionBehaviorClass, "q");
            selectionManagerProviderField = findField(selectionBehaviorClass, "n");
            mediaGetFeatureMethod = mediaContainerClass.getMethod("c", Class.class);
            featureGetMediaModelMethod = mediaFeatureClass.getMethod("t");
            providerGetMethod = providerClass.getDeclaredMethod("a");
            singletonListMethod = immutableListClass.getDeclaredMethod("l", Object.class);
            managerStartMethod = managerClass.getDeclaredMethod(
                    "a", View.class, mediaModelClass, immutableListClass);
            makeAccessible(mediaGetFeatureMethod, featureGetMediaModelMethod,
                    providerGetMethod, singletonListMethod, managerStartMethod);

            Method longPress = selectionBehaviorClass.getDeclaredMethod("gH", holderClass);
            longPress.setAccessible(true);
            XposedBridge.hookMethod(longPress, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    interceptGridLongPress(param);
                }
            });
            HookBridgeCompat.deoptimizeMethod(longPress);

            Method startDragAndDrop = View.class.getDeclaredMethod(
                    "startDragAndDrop", ClipData.class, View.DragShadowBuilder.class,
                    Object.class, int.class);
            startDragAndDrop.setAccessible(true);
            XposedBridge.hookMethod(startDragAndDrop, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    interceptPreparedDrag(param, context.getApplicationContext());
                }
            });
            HookBridgeCompat.deoptimizeMethod(startDragAndDrop);
            Log.i(TAG, "Google Photos 7.8 grid drag hooks installed");
        } catch (Throwable throwable) {
            INTERNAL_HOOKS_INSTALLED.set(false);
            Log.e(TAG, "Google Photos internal hook installation failed", throwable);
        }
    }

    private static void interceptGridLongPress(XC_MethodHook.MethodHookParam param) {
        Object holder = param.args == null || param.args.length == 0 ? null : param.args[0];
        View sourceView = getView(holder);
        Display display = sourceView == null ? null : sourceView.getDisplay();
        int displayId = display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
        String packageName = sourceView == null
                ? "" : sourceView.getContext().getPackageName();
        if (!ImageDragSourcePolicy.isAllowed(packageName, displayId)) {
            return;
        }
        boolean started = false;
        try {
            started = startGooglePhotosDrag(param.thisObject, holder, sourceView);
        } catch (Throwable throwable) {
            clearExpectedDrag();
            Log.e(TAG, "Cannot start Google Photos media drag", unwrap(throwable));
        }
        // On the OneStep display this hook is the only long-press path. Never fall back to
        // Google Photos selection mode when media extraction fails.
        param.setResult(true);
        Log.i(TAG, "Google Photos grid long press consumed: started=" + started
                + ", display=" + displayId);
    }

    private static boolean startGooglePhotosDrag(
            Object selectionBehavior, Object holder, View sourceView)
            throws ReflectiveOperationException {
        if (selectionBehavior == null || holder == null || sourceView == null
                || selectionDragStateField.get(selectionBehavior) != null) {
            return false;
        }
        Object adapterItem = holderItemField.get(holder);
        Object media = adapterItem == null ? null : mediaItemMediaField.get(adapterItem);
        if (media == null) {
            return false;
        }
        Object feature = mediaGetFeatureMethod.invoke(media, mediaFeatureClass());
        Object mediaModel = feature == null ? null : featureGetMediaModelMethod.invoke(feature);
        Object managerProvider = selectionManagerProviderField.get(selectionBehavior);
        Object manager = managerProvider == null ? null : providerGetMethod.invoke(managerProvider);
        if (mediaModel == null || manager == null) {
            return false;
        }
        Object mediaList = singletonListMethod.invoke(null, media);
        expectedSourceView = new WeakReference<>(sourceView);
        expectedDragDeadline = SystemClock.uptimeMillis() + EXPECTED_DRAG_TIMEOUT_MS;
        Object dragState = managerStartMethod.invoke(manager, sourceView, mediaModel, mediaList);
        if (dragState == null) {
            clearExpectedDrag();
            return false;
        }
        selectionDragStateField.set(selectionBehavior, dragState);
        return true;
    }

    private static Class<?> mediaFeatureClass() {
        return featureGetMediaModelMethod.getDeclaringClass();
    }

    private static void interceptPreparedDrag(
            XC_MethodHook.MethodHookParam param, Context applicationContext) {
        if (!(param.thisObject instanceof View)) {
            return;
        }
        View sourceView = (View) param.thisObject;
        View expected = expectedSourceView.get();
        if (expected != sourceView || SystemClock.uptimeMillis() > expectedDragDeadline) {
            return;
        }
        clearExpectedDrag();
        ClipData clipData = param.args != null && param.args.length > 0
                && param.args[0] instanceof ClipData ? (ClipData) param.args[0] : null;
        DragPayload payload = DragPayload.from(sourceView, clipData);
        if (payload != null) {
            try {
                TRANSFER_EXECUTOR.execute(() -> publish(applicationContext, payload));
            } catch (RuntimeException e) {
                Log.e(TAG, "Cannot schedule Google Photos media transfer", e);
            }
        } else {
            Log.w(TAG, "Google Photos produced no media URI for the requested drag");
        }
        // Returning false makes Google Photos immediately clean up its internal drag state.
        // OneStep owns the visible drag preview and the remaining touch sequence.
        param.setResult(false);
    }

    private static void publish(Context context, DragPayload payload) {
        ContentResolver resolver = context.getContentResolver();
        String mimeType = resolveMimeType(resolver, payload.uri, payload.mimeType);
        if (!ImageDragSourcePolicy.isSupportedMediaMimeType(mimeType)) {
            Log.w(TAG, "Reject unsupported Google Photos media payload: " + mimeType);
            return;
        }
        boolean bound = ImageDragBridgeClient.transfer(
                context, payload.uri, payload.displayId,
                ImageDragSourcePolicy.GOOGLE_PHOTOS_PACKAGE,
                mimeType, MAX_IMAGE_BYTES, TRANSFER_EXECUTOR);
        Log.i(TAG, "Google Photos media bridge bound=" + bound
                + ", display=" + payload.displayId + ", mime=" + mimeType);
    }

    private static String resolveMimeType(
            ContentResolver resolver, Uri uri, String fallback) {
        try {
            String resolved = resolver.getType(uri);
            if (ImageDragSourcePolicy.isSupportedMediaMimeType(resolved)) {
                return resolved;
            }
        } catch (RuntimeException ignored) {
        }
        Uri mediaStoreUri = GooglePhotosMediaUriResolver.resolve(uri);
        if (mediaStoreUri != null) {
            try {
                String resolved = resolver.getType(mediaStoreUri);
                if (ImageDragSourcePolicy.isSupportedMediaMimeType(resolved)) {
                    return resolved;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return fallback;
    }

    private static View getView(Object holder) {
        try {
            Object value = holder == null ? null : holderViewField.get(holder);
            return value instanceof View ? (View) value : null;
        } catch (IllegalAccessException | RuntimeException e) {
            return null;
        }
    }

    private static void clearExpectedDrag() {
        expectedSourceView = new WeakReference<>(null);
        expectedDragDeadline = 0L;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static void makeAccessible(Method... methods) {
        for (Method method : methods) {
            method.setAccessible(true);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException
                && ((InvocationTargetException) throwable).getCause() != null) {
            return ((InvocationTargetException) throwable).getCause();
        }
        return throwable;
    }

    private static final class DragPayload {
        final Uri uri;
        final String mimeType;
        final int displayId;

        DragPayload(Uri uri, String mimeType, int displayId) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.displayId = displayId;
        }

        static DragPayload from(View sourceView, ClipData clipData) {
            Display display = sourceView.getDisplay();
            if (clipData == null || display == null
                    || display.getDisplayId() <= Display.DEFAULT_DISPLAY) {
                return null;
            }
            String mimeType = firstMediaMimeType(clipData.getDescription());
            for (int index = 0; index < clipData.getItemCount(); index++) {
                Uri uri = clipData.getItemAt(index).getUri();
                if (uri != null) {
                    return new DragPayload(uri, mimeType, display.getDisplayId());
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
            return "image/*";
        }
    }
}
