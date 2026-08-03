package com.sangluo.onestep.feature.drag;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageDragSourcePolicyTest {
    @Test
    public void acceptsOnlyGooglePhotosOnVirtualDisplay() {
        assertTrue(ImageDragSourcePolicy.isAllowed(
                "com.google.android.apps.photos", 7));
        assertFalse(ImageDragSourcePolicy.isAllowed(
                "com.google.android.apps.photos", 0));
        assertFalse(ImageDragSourcePolicy.isAllowed("com.example.gallery", 7));
    }

    @Test
    public void acceptsOnlyImageMimeTypes() {
        assertTrue(ImageDragSourcePolicy.isImageMimeType("image/jpeg"));
        assertTrue(ImageDragSourcePolicy.isImageMimeType("image/*"));
        assertFalse(ImageDragSourcePolicy.isImageMimeType("video/mp4"));
        assertFalse(ImageDragSourcePolicy.isImageMimeType(null));
    }
}
