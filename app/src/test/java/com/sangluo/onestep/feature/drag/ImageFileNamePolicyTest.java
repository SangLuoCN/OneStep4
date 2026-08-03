package com.sangluo.onestep.feature.drag;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ImageFileNamePolicyTest {
    @Test
    public void mapsCommonImageMimeTypesToReceiverFriendlyExtensions() {
        assertEquals(".jpg", ImageFileNamePolicy.extensionForMime("image/jpeg"));
        assertEquals(".png", ImageFileNamePolicy.extensionForMime("image/png"));
        assertEquals(".webp", ImageFileNamePolicy.extensionForMime("image/webp"));
        assertEquals(".gif", ImageFileNamePolicy.extensionForMime("image/gif"));
        assertEquals(".heic", ImageFileNamePolicy.extensionForMime("image/heic"));
    }

    @Test
    public void fallsBackForUnknownMimeType() {
        assertEquals(".img", ImageFileNamePolicy.extensionForMime("image/*"));
        assertEquals(".img", ImageFileNamePolicy.extensionForMime(null));
    }
}
