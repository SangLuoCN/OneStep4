package com.sangluo.onestep.feature.drag;

/** Defines which source processes may publish media into OneStep. */
public final class ImageDragSourcePolicy {
    public static final String GOOGLE_PHOTOS_PACKAGE = "com.google.android.apps.photos";
    public static final String QQ_PACKAGE = "com.tencent.mobileqq";
    public static final String WECHAT_PACKAGE = "com.tencent.mm";

    private ImageDragSourcePolicy() {
    }

    public static boolean isAllowed(String packageName, int displayId) {
        return displayId > 0
                && (GOOGLE_PHOTOS_PACKAGE.equals(packageName)
                || isUniversalSourcePackage(packageName));
    }

    public static boolean isUniversalSourcePackage(String packageName) {
        return QQ_PACKAGE.equals(packageName) || WECHAT_PACKAGE.equals(packageName);
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
