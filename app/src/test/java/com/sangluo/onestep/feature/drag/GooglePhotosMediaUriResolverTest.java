package com.sangluo.onestep.feature.drag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class GooglePhotosMediaUriResolverTest {
    @Test
    public void unwrapsLocalMediaStoreUri() {
        assertEquals("content://media/external/images/media/195",
                GooglePhotosMediaUriResolver.resolve(
                        "content://com.google.android.apps.photos.contentprovider/-1/1/"
                                + "content%3A%2F%2Fmedia%2Fexternal%2Fimages%2Fmedia%2F195/"
                                + "ORIGINAL/NONE/image%2Fpng/870226471"));
    }

    @Test
    public void rejectsNonMediaAndMalformedWrappers() {
        assertNull(GooglePhotosMediaUriResolver.resolve(
                "content://com.google.android.apps.photos.contentprovider/-1/1/"
                        + "content%3A%2F%2Fexample.invalid%2Fimage/ORIGINAL"));
        assertNull(GooglePhotosMediaUriResolver.resolve("content://media/external/images/1"));
    }
}
