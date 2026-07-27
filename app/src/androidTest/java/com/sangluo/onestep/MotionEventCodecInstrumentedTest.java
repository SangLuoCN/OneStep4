package com.sangluo.onestep;

import static org.junit.Assert.assertEquals;

import android.view.InputDevice;
import android.view.MotionEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MotionEventCodecInstrumentedTest {
    @Test
    public void roundTripPreservesMultiPointerEvent() {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[2];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[2];
        for (int i = 0; i < 2; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i + 3;
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = 120f + i * 300f;
            coordinates[i].y = 240f + i * 400f;
            coordinates[i].pressure = 0.5f + i * 0.2f;
            coordinates[i].size = 0.3f + i * 0.1f;
        }
        int action = MotionEvent.ACTION_POINTER_DOWN
                | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
        MotionEvent source = MotionEvent.obtain(100L, 120L, action, 2,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);

        MotionEvent decoded = MotionEventCodec.decode(MotionEventCodec.encode(source));
        try {
            assertEquals(MotionEvent.ACTION_POINTER_DOWN, decoded.getActionMasked());
            assertEquals(1, decoded.getActionIndex());
            assertEquals(2, decoded.getPointerCount());
            assertEquals(3, decoded.getPointerId(0));
            assertEquals(4, decoded.getPointerId(1));
            assertEquals(120f, decoded.getX(0), 0.001f);
            assertEquals(640f, decoded.getY(1), 0.001f);
            assertEquals(0.7f, decoded.getPressure(1), 0.001f);
        } finally {
            source.recycle();
            decoded.recycle();
        }
    }
}
