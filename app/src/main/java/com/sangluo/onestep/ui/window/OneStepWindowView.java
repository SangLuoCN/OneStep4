package com.sangluo.onestep.ui.window;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.sangluo.onestep.R;
import com.sangluo.onestep.model.LauncherApp;
import com.sangluo.onestep.ui.widget.FixedViewportFrameLayout;

/** Window frame that switches between desktop, placeholder, overlay, and live app content. */
public final class OneStepWindowView extends FrameLayout {
    public interface Callbacks {
        View createDesktopHome();

        void configureDesktopHomeViewport(OneStepWindowView windowView);

        void drawSharedBackground(android.graphics.Canvas canvas, View target);

        boolean isVerticalWindowLayout();

        int getSideDismissDirection();

        int getSideDismissDistancePx();

        boolean canDismissSlot(int slot);

        boolean movedPastSideDismissThreshold(float dx, float dy);

        void dismissSideWindow(int slot);

        void settleSideWindowBack(OneStepWindowView windowView);

        boolean shouldRetainEmbeddedSurface(int slot);
    }

    private final Callbacks callbacks;
    private boolean mainWindow;
    private boolean mainWindowModeInitialized;
    private int slot = -1;
    private float sideTouchDownX;
    private float sideTouchDownY;
    private boolean sideTouchMoved;
    private final FrameLayout embeddedContainer;
    private final WindowPlaceholderView placeholder;
    private View internalOverlay;
    private boolean requestedLiveAppVisible;
    private View desktopHome;
    private boolean desktopHomeShown;
    private LauncherApp boundApp;
    private final ImageView emptyMark;

    public OneStepWindowView(Context context, boolean mainWindow, Drawable placeholderBorder,
                             Callbacks callbacks) {
        super(context);
        this.mainWindow = mainWindow;
        this.callbacks = callbacks;
        setClipToOutline(false);
        setClipChildren(true);
        setClipToPadding(true);
        setBackgroundColor(Color.TRANSPARENT);

        embeddedContainer = new FrameLayout(context);
        embeddedContainer.setBackgroundColor(Color.BLACK);
        embeddedContainer.setClipChildren(true);
        embeddedContainer.setClipToPadding(true);
        embeddedContainer.setVisibility(GONE);
        addView(embeddedContainer, matchFrame());

        placeholder = new WindowPlaceholderView(
                context, placeholderBorder, callbacks::drawSharedBackground);
        placeholder.setPadding(dp(1), dp(1), dp(1), dp(1));
        addView(placeholder, matchFrame());

        desktopHome = mainWindow ? callbacks.createDesktopHome() : null;
        desktopHomeShown = desktopHome != null;
        if (desktopHome != null) {
            placeholder.addView(desktopHome, matchFrame());
        }

        emptyMark = createEmptyWindowMark(context);
        placeholder.addView(emptyMark,
                new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER));
        setMainWindowMode(mainWindow);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // Side windows are previews, never interactive app surfaces. Consume the complete
        // gesture here so no event can reach the embedded SurfaceView during state changes.
        if (!mainWindow && slot >= 0) {
            return handleSideWindowTouch(event);
        }
        return super.dispatchTouchEvent(event);
    }

    public void attachEmbeddedHost(View hostView) {
        ViewParent parent = hostView.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(hostView);
        }
        embeddedContainer.removeAllViews();
        embeddedContainer.addView(hostView, matchFrame());
    }

    public void setLiveAppVisible(boolean visible) {
        requestedLiveAppVisible = visible;
        applyLiveAppVisibility();
    }

    public int getEmbeddedContentWidth() {
        return embeddedContainer.getWidth();
    }

    public int getEmbeddedContentHeight() {
        return embeddedContainer.getHeight();
    }

    public void showInternalOverlay(View overlay) {
        ViewParent parent = overlay.getParent();
        if (parent instanceof OneStepWindowView) {
            ((OneStepWindowView) parent).hideInternalOverlay(overlay);
        } else if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlay);
        }
        internalOverlay = overlay;
        applyLiveAppVisibility();
        addView(overlay, matchFrame());
        overlay.bringToFront();
    }

    public void hideInternalOverlay(View overlay) {
        if (overlay != null && overlay.getParent() == this) {
            removeView(overlay);
        }
        if (internalOverlay == overlay) {
            internalOverlay = null;
            applyLiveAppVisibility();
        }
    }

    public boolean hasInternalOverlay() {
        return internalOverlay != null && internalOverlay.getParent() == this;
    }

    public boolean requiresUniformFrameScale() {
        return hasInternalOverlay() || isDesktopHomeShown();
    }

    public void showDesktopHome() {
        ensureDesktopHome();
        if (desktopHome == null) {
            return;
        }
        desktopHomeShown = true;
        applyBoundContent();
        callbacks.configureDesktopHomeViewport(this);
    }

    public void hideDesktopHome() {
        if (!desktopHomeShown) {
            return;
        }
        desktopHomeShown = false;
        applyBoundContent();
    }

    public boolean isDesktopHomeShown() {
        return desktopHomeShown && desktopHome != null;
    }

    public void updateDesktopHomeViewport(int width, int height) {
        if (!isDesktopHomeShown() || !(desktopHome instanceof FixedViewportFrameLayout)) {
            return;
        }
        ((FixedViewportFrameLayout) desktopHome).setViewportSize(width, height);
    }

    public void setMainWindowMode(boolean mainWindow) {
        if (mainWindowModeInitialized && this.mainWindow == mainWindow) {
            return;
        }
        this.mainWindow = mainWindow;
        mainWindowModeInitialized = true;
        if (placeholder.getVisibility() == VISIBLE) {
            applyMainWindowPlaceholderStyle();
        }
    }

    public void rebuildDesktopHomeIfNeeded() {
        if (desktopHome == null) {
            if (desktopHomeShown) {
                ensureDesktopHome();
            }
            return;
        }
        placeholder.removeView(desktopHome);
        desktopHome = callbacks.createDesktopHome();
        if (desktopHome != null) {
            placeholder.addView(desktopHome, 0, matchFrame());
            desktopHome.setVisibility(desktopHomeShown ? VISIBLE : GONE);
        }
        setMainWindowMode(mainWindow);
        if (desktopHomeShown && desktopHome != null) {
            callbacks.configureDesktopHomeViewport(this);
        }
    }

    public void invalidatePlaceholderBackground() {
        placeholder.invalidate();
    }

    public void bind(LauncherApp app, int slot) {
        this.slot = slot;
        boundApp = app;
        if (app != null) {
            desktopHomeShown = false;
        }
        applyBoundContent();
    }

    private void ensureDesktopHome() {
        if (desktopHome != null) {
            return;
        }
        desktopHome = callbacks.createDesktopHome();
        if (desktopHome != null) {
            placeholder.addView(desktopHome, 0, matchFrame());
        }
    }

    private void applyBoundContent() {
        boolean showDesktopHome = desktopHomeShown && desktopHome != null;
        placeholder.setBorderVisible(!showDesktopHome);
        int placeholderInset = showDesktopHome ? 0 : dp(1);
        if (placeholder.getPaddingLeft() != placeholderInset) {
            placeholder.setPadding(placeholderInset, placeholderInset,
                    placeholderInset, placeholderInset);
        }
        if (desktopHome != null) {
            desktopHome.setVisibility(showDesktopHome ? VISIBLE : GONE);
        }
        if (showDesktopHome) {
            emptyMark.setVisibility(GONE);
            setAlpha(1f);
            setLiveAppVisible(false);
            return;
        }
        if (boundApp == null) {
            emptyMark.setVisibility(VISIBLE);
            setAlpha(1f);
            setLiveAppVisible(false);
            return;
        }
        emptyMark.setVisibility(GONE);
        setAlpha(1f);
    }

    private boolean handleSideWindowTouch(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sideTouchDownX = event.getRawX();
                sideTouchDownY = event.getRawY();
                sideTouchMoved = false;
                animate().cancel();
                ViewParent parent = getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                updateSideWindowDrag(event);
                return true;
            case MotionEvent.ACTION_UP:
                finishSideWindowTouch(event);
                return true;
            case MotionEvent.ACTION_CANCEL:
                callbacks.settleSideWindowBack(this);
                return true;
            default:
                return true;
        }
    }

    private void updateSideWindowDrag(MotionEvent event) {
        float dx = event.getRawX() - sideTouchDownX;
        float dy = event.getRawY() - sideTouchDownY;
        float touchSlop = dp(5);
        if (dx * dx + dy * dy > touchSlop * touchSlop) {
            sideTouchMoved = true;
        }
        if (!sideTouchMoved) {
            return;
        }
        float visualOffset;
        if (callbacks.isVerticalWindowLayout()) {
            visualOffset = Math.max(0f, dy);
            setTranslationX(0f);
            setTranslationY(visualOffset);
        } else {
            int direction = callbacks.getSideDismissDirection();
            visualOffset = dx * direction > 0 ? dx : 0f;
            setTranslationX(visualOffset);
            setTranslationY(0f);
        }
        float progress = Math.min(1f,
                Math.abs(visualOffset) / Math.max(1f, callbacks.getSideDismissDistancePx()));
        setAlpha(1f - progress * 0.46f);
    }

    private void finishSideWindowTouch(MotionEvent event) {
        float dx = event.getRawX() - sideTouchDownX;
        float dy = event.getRawY() - sideTouchDownY;
        if (sideTouchMoved && callbacks.canDismissSlot(slot)
                && callbacks.movedPastSideDismissThreshold(dx, dy)) {
            callbacks.dismissSideWindow(slot);
        } else if (!sideTouchMoved) {
            performClick();
        } else {
            callbacks.settleSideWindowBack(this);
        }
    }

    private void applyLiveAppVisibility() {
        boolean visible = requestedLiveAppVisible && internalOverlay == null;
        boolean retainSurface = internalOverlay == null && !visible && slot >= 0
                && callbacks.shouldRetainEmbeddedSurface(slot);
        embeddedContainer.setVisibility(visible || retainSurface ? VISIBLE : GONE);
        embeddedContainer.setAlpha(visible ? 1f : 0f);
        placeholder.setVisibility(visible ? GONE : VISIBLE);
        if (visible) {
            embeddedContainer.bringToFront();
            embeddedContainer.requestLayout();
        } else {
            placeholder.bringToFront();
            applyMainWindowPlaceholderStyle();
        }
        if (internalOverlay != null && internalOverlay.getParent() == this) {
            internalOverlay.bringToFront();
        }
    }

    private void applyMainWindowPlaceholderStyle() {
        setBackgroundColor(Color.TRANSPARENT);
    }

    private ImageView createEmptyWindowMark(Context context) {
        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.add);
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mark.setContentDescription("空窗口");
        return mark;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
