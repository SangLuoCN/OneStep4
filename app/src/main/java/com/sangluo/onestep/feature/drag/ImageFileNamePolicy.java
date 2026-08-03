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
        return ".img";
    }
}
