package com.sangluo.onestep.feature.drag;

/** Defines the single gallery source supported by OneStep media dragging. */
public final class ImageDragSourcePolicy {
    public static final String GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos";

    private ImageDragSourcePolicy() {
    }

    public static boolean isAllowed(String packageName, int displayId) {
        return displayId > 0 && GOOGLE_PHOTOS_PACKAGE.equals(packageName);
    }

    public static boolean isImageMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public static boolean isVideoMimeType(String mimeType) {
        return mimeType != null && mimeType.startsWith("video/");
    }

    public static boolean isSupportedMediaMimeType(String mimeType) {
        return isImageMimeType(mimeType) || isVideoMimeType(mimeType);
    }
}
