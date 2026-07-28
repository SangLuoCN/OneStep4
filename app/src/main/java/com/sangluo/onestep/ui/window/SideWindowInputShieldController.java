package com.sangluo.onestep.ui.window;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/** Places touch-only application sub-windows above side-window content. */
public final class SideWindowInputShieldController {
    public interface Callbacks {
        boolean shouldShieldSlot(int slot);
        OneStepWindowView windowView(int slot);
    }

    private static final String TAG = "OneStepSideInput";

    private final Activity activity;
    private final WindowManager windowManager;
    private final Callbacks callbacks;
    private final Shield[] shields;
    private final int[] location = new int[2];
    private boolean released;

    public SideWindowInputShieldController(Activity activity, int slotCount,
                                           Callbacks callbacks) {
        this.activity = activity;
        this.windowManager = activity.getWindowManager();
        this.callbacks = callbacks;
        this.shields = new Shield[slotCount];
    }

    public void update() {
        if (released || activity.isFinishing()) {
            return;
        }
        View decor = activity.getWindow().getDecorView();
        IBinder windowToken = decor.getWindowToken();
        if (windowToken == null) {
            decor.post(this::update);
            return;
        }
        for (int slot = 0; slot < shields.length; slot++) {
            OneStepWindowView target = callbacks.windowView(slot);
            boolean shouldShow = callbacks.shouldShieldSlot(slot)
                    && target != null && target.isShown()
                    && target.getWidth() > 0 && target.getHeight() > 0;
            if (!shouldShow) {
                hideShield(slot);
                continue;
            }
            target.getLocationOnScreen(location);
            showOrUpdateShield(slot, target, windowToken,
                    location[0], location[1], target.getWidth(), target.getHeight());
        }
    }

    public void hideAll() {
        for (int slot = 0; slot < shields.length; slot++) {
            hideShield(slot);
        }
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        for (int slot = 0; slot < shields.length; slot++) {
            destroyShield(slot);
        }
    }

    private void showOrUpdateShield(int slot, OneStepWindowView target, IBinder windowToken,
                                    int x, int y, int width, int height) {
        Shield shield = shields[slot];
        if (shield == null) {
            View inputView = new View(activity);
            inputView.setBackgroundColor(Color.TRANSPARENT);
            inputView.setClickable(true);
            inputView.setFocusable(false);
            inputView.setContentDescription("侧屏手势区域");
            inputView.setOnTouchListener((view, event) -> forwardTouch(slot, event));
            WindowManager.LayoutParams params = createLayoutParams(
                    slot, windowToken, x, y, width, height);
            try {
                windowManager.addView(inputView, params);
                shields[slot] = new Shield(inputView, params);
                Log.i(TAG, "Side input shield added: slot=" + slot
                        + ", frame=" + x + "," + y + " " + width + "x" + height);
            } catch (RuntimeException e) {
                Log.w(TAG, "Add side input shield failed: slot=" + slot, e);
            }
            return;
        }
        WindowManager.LayoutParams params = shield.layoutParams;
        shield.view.setVisibility(View.VISIBLE);
        if (params.token == windowToken && params.x == x && params.y == y
                && params.width == width && params.height == height) {
            return;
        }
        params.token = windowToken;
        params.x = x;
        params.y = y;
        params.width = width;
        params.height = height;
        try {
            windowManager.updateViewLayout(shield.view, params);
        } catch (RuntimeException e) {
            Log.w(TAG, "Update side input shield failed: slot=" + slot, e);
            destroyShield(slot);
        }
    }

    private WindowManager.LayoutParams createLayoutParams(int slot, IBinder windowToken,
                                                           int x, int y,
                                                           int width, int height) {
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                width, height, WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                flags, PixelFormat.TRANSLUCENT);
        params.token = windowToken;
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        params.setTitle("OneStepSideInputShield-" + slot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0);
        }
        return params;
    }

    private boolean forwardTouch(int slot, MotionEvent event) {
        OneStepWindowView target = callbacks.windowView(slot);
        if (target == null || !callbacks.shouldShieldSlot(slot)) {
            return true;
        }
        float rawX = event.getRawX();
        float rawY = event.getRawY();
        target.getLocationOnScreen(location);
        MotionEvent forwarded = MotionEvent.obtain(event);
        try {
            forwarded.setLocation(rawX - location[0], rawY - location[1]);
            target.dispatchTouchEvent(forwarded);
        } finally {
            forwarded.recycle();
        }
        return true;
    }

    private void hideShield(int slot) {
        Shield shield = shields[slot];
        if (shield != null) {
            shield.view.setVisibility(View.GONE);
        }
    }

    private void destroyShield(int slot) {
        Shield shield = shields[slot];
        if (shield == null) {
            return;
        }
        shields[slot] = null;
        try {
            windowManager.removeViewImmediate(shield.view);
        } catch (IllegalArgumentException ignored) {
            // The parent activity may already have removed all of its child windows.
        }
    }

    private static final class Shield {
        final View view;
        final WindowManager.LayoutParams layoutParams;

        Shield(View view, WindowManager.LayoutParams layoutParams) {
            this.view = view;
            this.layoutParams = layoutParams;
        }
    }
}
