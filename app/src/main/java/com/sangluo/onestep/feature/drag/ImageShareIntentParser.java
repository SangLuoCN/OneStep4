package com.sangluo.onestep.feature.drag;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

/** Extracts the first image URI from Gallery's chooser/share intent variants. */
public final class ImageShareIntentParser {
    private static final String ACTION_XMAN_SHARE_MANAGER =
            "miui.intent.action.XMAN_SHARE_MANAGER";
    private static final int MAX_NESTED_INTENTS = 4;

    private ImageShareIntentParser() {
    }

    public static Payload find(Intent root) {
        if (root == null) {
            return null;
        }
        Intent current = root;
        Uri uri = null;
        String mimeType = "";
        for (int depth = 0; depth < MAX_NESTED_INTENTS && current != null; depth++) {
            if (!TextUtils.isEmpty(current.getType())) {
                mimeType = current.getType();
            }
            uri = firstClipUri(current);
            if (uri == null) {
                uri = firstStreamUri(current);
            }
            Intent nested = nestedIntent(current);
            if (nested == null) {
                break;
            }
            current = nested;
        }
        if (uri == null) {
            uri = firstClipUri(root);
        }
        if (uri == null) {
            uri = firstStreamUri(root);
        }
        if (uri == null) {
            return null;
        }
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = queryMimeType(uri);
        }
        if (TextUtils.isEmpty(mimeType)) {
            mimeType = "image/*";
        }
        return mimeType.startsWith("image/") ? new Payload(uri, mimeType) : null;
    }

    private static Intent nestedIntent(Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_CHOOSER.equals(action)
                && !ACTION_XMAN_SHARE_MANAGER.equals(action)) {
            return null;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
            }
            return intent.getParcelableExtra(Intent.EXTRA_INTENT);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Uri firstClipUri(Intent intent) {
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            return null;
        }
        for (int i = 0; i < clipData.getItemCount(); i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            if (uri != null) {
                return uri;
            }
        }
        return null;
    }

    private static Uri firstStreamUri(Intent intent) {
        try {
            Bundle extras = intent.getExtras();
            if (extras == null || !extras.containsKey(Intent.EXTRA_STREAM)) {
                return null;
            }
            Object value = extras.get(Intent.EXTRA_STREAM);
            if (value instanceof Uri) {
                return (Uri) value;
            }
            if (value instanceof java.util.ArrayList) {
                java.util.ArrayList<?> values = (java.util.ArrayList<?>) value;
                for (Object item : values) {
                    if (item instanceof Uri) {
                        return (Uri) item;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Some vendor Parcelable implementations reject reads from another process.
        }
        return null;
    }

    private static String queryMimeType(Uri uri) {
        String query = uri.getQuery();
        if (query == null) {
            return "";
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator > 0 && "mimeType".equals(Uri.decode(part.substring(0, separator)))) {
                return Uri.decode(part.substring(separator + 1));
            }
        }
        return "";
    }

    public static final class Payload {
        public final Uri uri;
        public final String mimeType;

        Payload(Uri uri, String mimeType) {
            this.uri = uri;
            this.mimeType = mimeType;
        }
    }
}
