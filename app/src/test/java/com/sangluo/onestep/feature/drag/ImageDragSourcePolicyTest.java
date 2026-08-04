package com.sangluo.onestep.feature.drag;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageDragSourcePolicyTest {
    @Test
    public void acceptsOnlyConfiguredSourceAppsOnVirtualDisplays() {
        assertTrue(ImageDragSourcePolicy.isAllowed(
                "com.google.android.apps.photos", 7));
        assertFalse(ImageDragSourcePolicy.isAllowed(
                "com.google.android.apps.photos", 0));
        assertTrue(ImageDragSourcePolicy.isAllowed("com.tencent.mobileqq", 7));
        assertTrue(ImageDragSourcePolicy.isAllowed("com.tencent.mm", 7));
        assertFalse(ImageDragSourcePolicy.isAllowed("com.example.gallery", 7));
        assertFalse(ImageDragSourcePolicy.isAllowed("com.sangluo.onestep", 7));
    }

    @Test
    public void universalHookTargetsOnlyQqAndWeChat() {
        assertTrue(ImageDragSourcePolicy.isUniversalSourcePackage(
                "com.tencent.mobileqq"));
        assertTrue(ImageDragSourcePolicy.isUniversalSourcePackage("com.tencent.mm"));
        assertFalse(ImageDragSourcePolicy.isUniversalSourcePackage(
                "com.google.android.apps.photos"));
        assertFalse(ImageDragSourcePolicy.isUniversalSourcePackage("com.example.gallery"));
    }

    @Test
    public void acceptsImagesAndVideos() {
        assertTrue(ImageDragSourcePolicy.isImageMimeType("image/jpeg"));
        assertTrue(ImageDragSourcePolicy.isImageMimeType("image/*"));
        assertFalse(ImageDragSourcePolicy.isImageMimeType("video/mp4"));
        assertFalse(ImageDragSourcePolicy.isImageMimeType(null));
        assertTrue(ImageDragSourcePolicy.isVideoMimeType("video/mp4"));
        assertTrue(ImageDragSourcePolicy.isSupportedMediaMimeType("video/mp4"));
        assertFalse(ImageDragSourcePolicy.isSupportedMediaMimeType("text/plain"));
    }
}
