package com.sangluo.onestep.feature.drag;

import android.net.Uri;

import java.io.File;

/** In-process handoff between the exported write-only provider and MainActivity. */
public final class ImageDragBridgeRegistry {
    public interface Listener {
        boolean canAccept(int callingUid, int sourceDisplayId, String sourcePackage);

        boolean onImageReady(int sourceDisplayId, String sourcePackage,
                             String mimeType, Uri sourceUri, File imageFile);
    }

    private static volatile Listener listener;

    private ImageDragBridgeRegistry() {
    }

    public static void register(Listener nextListener) {
        listener = nextListener;
    }

    public static void unregister(Listener registeredListener) {
        if (listener == registeredListener) {
            listener = null;
        }
    }

    public static boolean canAccept(
            int callingUid, int sourceDisplayId, String sourcePackage) {
        Listener current = listener;
        return current != null
                && current.canAccept(callingUid, sourceDisplayId, sourcePackage);
    }

    public static boolean onImageReady(
            int sourceDisplayId, String sourcePackage, String mimeType,
            Uri sourceUri, File imageFile) {
        Listener current = listener;
        return current != null && current.onImageReady(
                sourceDisplayId, sourcePackage, mimeType, sourceUri, imageFile);
    }
}
