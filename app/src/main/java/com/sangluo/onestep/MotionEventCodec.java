package com.sangluo.onestep;

import android.os.Parcel;
import android.util.Base64;
import android.view.MotionEvent;

public final class MotionEventCodec {
    private static final int MAX_ENCODED_EVENT_LENGTH = 256 * 1024;
    private static final int MAX_POINTER_COUNT = 32;

    private MotionEventCodec() {
    }

    public static String encode(MotionEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("motion event is null");
        }
        Parcel parcel = Parcel.obtain();
        try {
            event.writeToParcel(parcel, 0);
            String encoded = Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP);
            if (encoded.length() > MAX_ENCODED_EVENT_LENGTH) {
                throw new IllegalArgumentException("motion event is too large");
            }
            return encoded;
        } finally {
            parcel.recycle();
        }
    }

    public static MotionEvent decode(String encoded) {
        if (encoded == null || encoded.isEmpty()
                || encoded.length() > MAX_ENCODED_EVENT_LENGTH) {
            throw new IllegalArgumentException("invalid encoded motion event length");
        }
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            MotionEvent event = MotionEvent.CREATOR.createFromParcel(parcel);
            validate(event);
            return event;
        } finally {
            parcel.recycle();
        }
    }

    private static void validate(MotionEvent event) {
        int pointerCount = event == null ? 0 : event.getPointerCount();
        if (pointerCount < 1 || pointerCount > MAX_POINTER_COUNT) {
            if (event != null) {
                event.recycle();
            }
            throw new IllegalArgumentException("invalid motion pointer count " + pointerCount);
        }
        int actionMasked = event.getActionMasked();
        if ((actionMasked == MotionEvent.ACTION_POINTER_DOWN
                || actionMasked == MotionEvent.ACTION_POINTER_UP)
                && event.getActionIndex() >= pointerCount) {
            event.recycle();
            throw new IllegalArgumentException("invalid motion action index");
        }
    }
}
