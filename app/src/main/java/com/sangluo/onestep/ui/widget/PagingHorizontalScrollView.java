package com.sangluo.onestep.ui.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/** Horizontal pager that snaps its content to full-width pages. */
public final class PagingHorizontalScrollView extends HorizontalScrollView {
    private static final int SNAP_DELAY_MS = 90;
    private static final int FLING_PAGE_THRESHOLD = 450;
    private final Runnable snapRunnable = this::snapToNearestPage;
    private boolean consumedFling;

    public PagingHorizontalScrollView(Context context) {
        super(context);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            consumedFling = false;
            removeCallbacks(snapRunnable);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(snapRunnable);
            if (!consumedFling) {
                postDelayed(snapRunnable, SNAP_DELAY_MS);
            }
        }
        return handled;
    }

    @Override
    public void fling(int velocityX) {
        consumedFling = true;
        removeCallbacks(snapRunnable);
        int pageWidth = Math.max(1, getWidth());
        int currentPage = Math.round(getScrollX() / (float) pageWidth);
        int targetPage = currentPage;
        if (Math.abs(velocityX) > FLING_PAGE_THRESHOLD) {
            targetPage = velocityX > 0
                    ? (int) Math.floor(getScrollX() / (float) pageWidth) + 1
                    : (int) Math.ceil(getScrollX() / (float) pageWidth) - 1;
        }
        smoothScrollTo(clampPage(targetPage) * pageWidth, 0);
    }

    public void snapToNearestPage() {
        int pageWidth = Math.max(1, getWidth());
        int targetPage = clampPage(Math.round(getScrollX() / (float) pageWidth));
        smoothScrollTo(targetPage * pageWidth, 0);
    }

    private int clampPage(int page) {
        return Math.max(0, Math.min(page, getMaxPageIndex()));
    }

    private int getMaxPageIndex() {
        if (getChildCount() == 0 || getWidth() <= 0) {
            return 0;
        }
        int contentWidth = getChildAt(0).getWidth();
        int pageCount = Math.max(1, (contentWidth + getWidth() - 1) / getWidth());
        return pageCount - 1;
    }
}
