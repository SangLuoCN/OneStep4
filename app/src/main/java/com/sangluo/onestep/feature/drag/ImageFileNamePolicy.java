package com.sangluo.onestep.feature.drag;

import java.util.Locale;

/** Keeps shared-image cache names compatible with strict receiving applications. */
public final class ImageFileNamePolicy {
    private ImageFileNamePolicy() {
    }

    public static String extensionForMime(String mimeType) {
        if (mimeType == null) {
            return ".img";
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (normalized.contains("jpeg") || normalized.contains("jpg")) {
            return ".jpg";
        }
        if (normalized.contains("png")) {
            return ".png";
        }
        if (normalized.contains("webp")) {
            return ".webp";
        }
        if (normalized.contains("gif")) {
            return ".gif";
        }
        if (normalized.contains("heic")) {
            return ".heic";
        }
        if (normalized.contains("mp4")) {
            return ".mp4";
        }
        if (normalized.contains("quicktime") || normalized.contains("mov")) {
            return ".mov";
        }
        if (normalized.contains("webm")) {
            return ".webm";
        }
        if (normalized.contains("3gpp") || normalized.contains("3gp")) {
            return ".3gp";
        }
        if (normalized.contains("matroska") || normalized.contains("mkv")) {
            return ".mkv";
        }
        return ".img";
    }

    /** The bridge stores a decodable still preview even when the shared media is a video. */
    public static String previewExtensionForMime(String mimeType) {
        return ImageDragSourcePolicy.isVideoMimeType(mimeType)
                ? ".jpg" : extensionForMime(mimeType);
    }
}
