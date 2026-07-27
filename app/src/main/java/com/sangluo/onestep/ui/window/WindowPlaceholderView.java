package com.sangluo.onestep.ui.window;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;

/** Draws the shared workspace background and placeholder border for an empty window. */
public final class WindowPlaceholderView extends FrameLayout {
    public interface BackgroundDrawer {
        void draw(Canvas canvas, WindowPlaceholderView target);
    }

    private final Drawable border;
    private final BackgroundDrawer backgroundDrawer;
    private boolean borderVisible = true;

    public WindowPlaceholderView(Context context, Drawable border,
                                 BackgroundDrawer backgroundDrawer) {
        super(context);
        this.border = border;
        this.backgroundDrawer = backgroundDrawer;
        setWillNotDraw(false);
    }

    public void setBorderVisible(boolean visible) {
        if (borderVisible == visible) {
            return;
        }
        borderVisible = visible;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        backgroundDrawer.draw(canvas, this);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (borderVisible) {
            border.setBounds(0, 0, getWidth(), getHeight());
            border.draw(canvas);
        }
    }
}
