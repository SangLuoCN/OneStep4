package com.sangluo.onestep.ui.widget;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Keeps child layout dimensions stable and scales the viewport into available bounds. */
public final class FixedViewportFrameLayout extends FrameLayout {
    private final FrameLayout viewport;
    private int viewportWidth;
    private int viewportHeight;
    private boolean cropToFill;

    public FixedViewportFrameLayout(Context context) {
        super(context);
        setClipChildren(true);
        setClipToPadding(true);
        viewport = new FrameLayout(context);
        super.addView(viewport, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public FrameLayout getViewport() {
        return viewport;
    }

    public void setCropToFill(boolean cropToFill) {
        if (this.cropToFill == cropToFill) {
            return;
        }
        this.cropToFill = cropToFill;
        requestLayout();
    }

    public void setViewportSize(int width, int height) {
        if (width <= 0 || height <= 0
                || (viewportWidth == width && viewportHeight == height)) {
            return;
        }
        viewportWidth = width;
        viewportHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        if (viewportWidth <= 0 && width > 0) {
            viewportWidth = width;
        }
        if (viewportHeight <= 0 && height > 0) {
            viewportHeight = height;
        }
        viewport.measure(
                MeasureSpec.makeMeasureSpec(Math.max(0, viewportWidth), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(Math.max(0, viewportHeight), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int width = right - left;
        int height = bottom - top;
        if (viewportWidth <= 0 || viewportHeight <= 0 || width <= 0 || height <= 0) {
            viewport.layout(0, 0, Math.max(0, width), Math.max(0, height));
            return;
        }
        float widthScale = width / (float) viewportWidth;
        float heightScale = height / (float) viewportHeight;
        float scale = cropToFill
                ? Math.max(widthScale, heightScale)
                : Math.min(widthScale, heightScale);
        viewport.layout(0, 0, viewportWidth, viewportHeight);
        viewport.setPivotX(0f);
        viewport.setPivotY(0f);
        viewport.setScaleX(scale);
        viewport.setScaleY(scale);
        viewport.setTranslationX((width - viewportWidth * scale) / 2f);
        viewport.setTranslationY((height - viewportHeight * scale) / 2f);
    }
}
