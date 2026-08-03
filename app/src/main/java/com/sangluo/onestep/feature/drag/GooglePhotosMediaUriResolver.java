package com.sangluo.onestep.feature.drag;

import android.net.Uri;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Extracts the local MediaStore URI wrapped by Google Photos' drag provider. */
public final class GooglePhotosMediaUriResolver {
    private static final String WRAPPER_PREFIX =
            "content://com.google.android.apps.photos.contentprovider/";
    private static final String MEDIA_PREFIX = "content://media/";

    private GooglePhotosMediaUriResolver() {
    }

    public static Uri resolve(Uri wrapperUri) {
        String resolved = resolve(wrapperUri == null ? null : wrapperUri.toString());
        return resolved == null ? null : Uri.parse(resolved);
    }

    static String resolve(String wrapperUri) {
        if (wrapperUri == null || !wrapperUri.startsWith(WRAPPER_PREFIX)) {
            return null;
        }
        String path = wrapperUri.substring(WRAPPER_PREFIX.length());
        String[] segments = path.split("/", -1);
        if (segments.length < 3) {
            return null;
        }
        String nested;
        try {
            nested = URLDecoder.decode(segments[2], StandardCharsets.UTF_8.name());
        } catch (IllegalArgumentException e) {
            return null;
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
        return nested.startsWith(MEDIA_PREFIX) ? nested : null;
    }
}
