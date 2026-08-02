package com.sangluo.onestep.ui.window;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.RequiresApi;

import com.sangluo.onestep.RootVirtualDisplayHost;
import com.sangluo.onestep.feature.embedding.EmbeddedAppHost;
import com.sangluo.onestep.model.LauncherApp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.sangluo.onestep.data.settings.OneStepSettings.MAX_SIDE_WINDOWS;

public final class WindowAnimationController {
    public interface Callbacks {
        OneStepWindowView[] windowViews();
        EmbeddedAppHost[] embeddedHosts();
        LauncherApp[] windowApps();
        ViewGroup workspace();
        Handler mainHandler();
        int activeMainSlot();
        void beginCriticalSection();
        void applyWindowZOrder();
        void refreshAllEmbeddedSlotLayouts();
        void scheduleDeferredWorkFlush();
    }

    private static final String TAG = "OneStep40";
    private static final int MAX_WINDOWS = MAX_SIDE_WINDOWS + 2;
    private static final int WINDOW_FRAME_SWITCH_ANIMATION_MS = 200;
    private static final int WINDOW_SURFACE_ANIMATION_LAYER_BASE = 10_000;
    private static final long POST_ANIMATION_ROLE_REFRESH_DELAY_MS = 48L;

    private final Callbacks callbacks;
    private ValueAnimator windowSurfaceAnimator;
    private List<WindowSurfaceAnimationTarget> windowSurfaceAnimationTargets =
            Collections.emptyList();
    private boolean running;
    private int generation;
    private long lastAnimationEndUptime;

    public WindowAnimationController(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public void animate(Rect[] targetRects, Runnable onAnimationFinished) {
        animate(targetRects, onAnimationFinished, true);
    }

    public void animate(Rect[] targetRects, Runnable onAnimationFinished,
                        boolean allowSurfaceLayerAnimation) {
        int animationGeneration = ++generation;
        running = true;
        int changingWindowCount = 0;
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            OneStepWindowView windowView = windowViews()[slot];
            if (windowView.getVisibility() == View.VISIBLE
                    && !getWindowFrame(windowView).equals(targetRects[slot])) {
                changingWindowCount++;
            }
        }
        if (changingWindowCount == 0) {
            finishWindowFrameAnimation(targetRects, onAnimationFinished);
            return;
        }
        callbacks.beginCriticalSection();
        if (allowSurfaceLayerAnimation
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            List<WindowSurfaceAnimationTarget> surfaceTargets =
                    collectWindowSurfaceAnimationTargets(targetRects, changingWindowCount);
            if (!surfaceTargets.isEmpty()) {
                animateWindowSurfaceLayers(animationGeneration, targetRects, surfaceTargets,
                        onAnimationFinished);
                return;
            }
        }
        final int[] remainingAnimations = {changingWindowCount};
        callbacks.applyWindowZOrder();
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            OneStepWindowView windowView = windowViews()[slot];
            windowView.animate().cancel();
            if (windowView.getVisibility() != View.VISIBLE) {
                setWindowFrame(windowView, targetRects[slot]);
                resetWindowTransform(windowView);
                continue;
            }
            Rect currentRect = getWindowFrame(windowView);
            Rect targetRect = targetRects[slot];
            if (currentRect.equals(targetRect)) {
                resetWindowTransform(windowView);
                continue;
            }
            float targetScaleX = targetRect.width() / Math.max(1f, currentRect.width());
            float targetScaleY = targetRect.height() / Math.max(1f, currentRect.height());
            float targetTranslationX;
            float targetTranslationY;
            if (windowView.requiresUniformFrameScale()) {
                float uniformScale = Math.min(targetScaleX, targetScaleY);
                targetScaleX = uniformScale;
                targetScaleY = uniformScale;
                windowView.setPivotX(currentRect.width() / 2f);
                windowView.setPivotY(currentRect.height() / 2f);
                targetTranslationX = (targetRect.left + targetRect.right
                        - currentRect.left - currentRect.right) / 2f;
                targetTranslationY = (targetRect.top + targetRect.bottom
                        - currentRect.top - currentRect.bottom) / 2f;
            } else {
                configureWindowPivot(windowView, currentRect, targetRect);
                targetTranslationX = calculateTranslationX(
                        windowView, currentRect, targetRect, targetScaleX);
                targetTranslationY = calculateTranslationY(
                        windowView, currentRect, targetRect, targetScaleY);
            }
            windowView.animate()
                    .translationX(targetTranslationX)
                    .translationY(targetTranslationY)
                    .scaleX(targetScaleX)
                    .scaleY(targetScaleY)
                    .setDuration(WINDOW_FRAME_SWITCH_ANIMATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(() -> {
                        if (animationGeneration != generation) {
                            return;
                        }
                        remainingAnimations[0]--;
                        if (remainingAnimations[0] == 0) {
                            finishWindowFrameAnimation(targetRects, onAnimationFinished);
                        }
                    })
                    .start();
        }
    }

    private List<WindowSurfaceAnimationTarget> collectWindowSurfaceAnimationTargets(
            Rect[] targetRects, int changingWindowCount) {
        if (changingWindowCount < 2) {
            return Collections.emptyList();
        }
        int[] workspaceLocation = new int[2];
        workspace().getLocationInWindow(workspaceLocation);
        List<WindowSurfaceAnimationTarget> targets = new ArrayList<>(changingWindowCount);
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            OneStepWindowView windowView = windowViews()[slot];
            if (windowView.getVisibility() != View.VISIBLE) {
                continue;
            }
            Rect currentRect = getWindowFrame(windowView);
            Rect targetRect = targetRects[slot];
            if (currentRect.equals(targetRect)) {
                continue;
            }
            EmbeddedAppHost embeddedHost = embeddedHosts()[slot];
            if (windowApps()[slot] == null || !(embeddedHost instanceof RootVirtualDisplayHost)) {
                return Collections.emptyList();
            }
            RootVirtualDisplayHost host = (RootVirtualDisplayHost) embeddedHost;
            if (!host.ensureWindowAnimationLeash()) {
                return Collections.emptyList();
            }
            targets.add(new WindowSurfaceAnimationTarget(slot, host, currentRect, targetRect,
                    workspaceLocation[0], workspaceLocation[1]));
        }
        return targets.size() == changingWindowCount ? targets : Collections.emptyList();
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void animateWindowSurfaceLayers(int animationGeneration, Rect[] targetRects,
                                            List<WindowSurfaceAnimationTarget> targets,
                                            Runnable onAnimationFinished) {
        cancel();
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        for (WindowSurfaceAnimationTarget target : targets) {
            int layer = WINDOW_SURFACE_ANIMATION_LAYER_BASE + target.slot;
            if (target.slot == activeMainSlot()) {
                layer += MAX_WINDOWS;
            }
            target.host.prepareWindowSurfaceAnimation(transaction, layer);
        }
        transaction.apply();
        Log.d(TAG, "Window switch uses SurfaceControl leashes: count=" + targets.size());

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        windowSurfaceAnimator = animator;
        windowSurfaceAnimationTargets = targets;
        animator.setDuration(WINDOW_FRAME_SWITCH_ANIMATION_MS);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        final long animationStartNanos = SystemClock.elapsedRealtimeNanos();
        final long[] lastFrameStartNanos = {0L};
        final long[] frameIntervalTotalNanos = {0L};
        final long[] maxFrameIntervalNanos = {0L};
        final long[] transactionTotalNanos = {0L};
        final long[] maxTransactionNanos = {0L};
        final int[] frameCount = {0};
        animator.addUpdateListener(valueAnimator -> {
            if (animationGeneration != generation
                    || windowSurfaceAnimator != valueAnimator) {
                return;
            }
            long frameStartNanos = SystemClock.elapsedRealtimeNanos();
            if (lastFrameStartNanos[0] != 0L) {
                long intervalNanos = frameStartNanos - lastFrameStartNanos[0];
                frameIntervalTotalNanos[0] += intervalNanos;
                maxFrameIntervalNanos[0] = Math.max(maxFrameIntervalNanos[0], intervalNanos);
            }
            lastFrameStartNanos[0] = frameStartNanos;
            frameCount[0]++;
            float progress = (float) valueAnimator.getAnimatedValue();
            for (WindowSurfaceAnimationTarget target : targets) {
                target.apply(transaction, progress);
            }
            transaction.apply();
            long transactionNanos = SystemClock.elapsedRealtimeNanos() - frameStartNanos;
            transactionTotalNanos[0] += transactionNanos;
            maxTransactionNanos[0] = Math.max(maxTransactionNanos[0], transactionNanos);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                long animatorEndNanos = SystemClock.elapsedRealtimeNanos();
                if (cancelled || animationGeneration != generation
                        || windowSurfaceAnimator != animation) {
                    transaction.close();
                    return;
                }
                transaction.addTransactionCommittedListener(
                        command -> mainHandler().post(command),
                        () -> finishCommittedWindowSurfaceAnimation(animationGeneration,
                                animation, targetRects, targets, onAnimationFinished,
                                animationStartNanos, animatorEndNanos, frameCount[0],
                                frameIntervalTotalNanos[0], maxFrameIntervalNanos[0],
                                transactionTotalNanos[0], maxTransactionNanos[0]));
                // This empty committed transaction is a barrier for all animation transactions
                // previously applied from this UI thread.
                transaction.apply();
                transaction.close();
            }
        });
        animator.start();
    }

    private void finishCommittedWindowSurfaceAnimation(
            int animationGeneration, Animator animation, Rect[] targetRects,
            List<WindowSurfaceAnimationTarget> targets, Runnable onAnimationFinished,
            long animationStartNanos, long animatorEndNanos, int frameCount,
            long frameIntervalTotalNanos, long maxFrameIntervalNanos,
            long transactionTotalNanos, long maxTransactionNanos) {
        if (animationGeneration != generation
                || windowSurfaceAnimator != animation) {
            return;
        }
        windowSurfaceAnimator = null;
        windowSurfaceAnimationTargets = Collections.emptyList();
        long finishStartNanos = SystemClock.elapsedRealtimeNanos();
        finishWindowFrameAnimation(targetRects, onAnimationFinished, targets);
        long finishEndNanos = SystemClock.elapsedRealtimeNanos();
        int intervalCount = Math.max(0, frameCount - 1);
        double averageIntervalMs = intervalCount == 0 ? 0d
                : frameIntervalTotalNanos / (double) intervalCount / 1_000_000d;
        double averageTransactionMs = frameCount == 0 ? 0d
                : transactionTotalNanos / (double) frameCount / 1_000_000d;
        Log.i(TAG, String.format(Locale.US,
                "Surface switch animation metrics: total=%.3fms, animator=%.3fms, "
                        + "finish=%.3fms, frames=%d, intervalAvg=%.3fms, "
                        + "intervalMax=%.3fms, transactionAvg=%.3fms, "
                        + "transactionMax=%.3fms, surfaces=%d",
                (finishEndNanos - animationStartNanos) / 1_000_000d,
                (animatorEndNanos - animationStartNanos) / 1_000_000d,
                (finishEndNanos - finishStartNanos) / 1_000_000d,
                frameCount, averageIntervalMs, maxFrameIntervalNanos / 1_000_000d,
                averageTransactionMs, maxTransactionNanos / 1_000_000d, targets.size()));
    }

    public void cancel() {
        ValueAnimator animator = windowSurfaceAnimator;
        List<WindowSurfaceAnimationTarget> targets = windowSurfaceAnimationTargets;
        windowSurfaceAnimator = null;
        windowSurfaceAnimationTargets = Collections.emptyList();
        if (animator != null) {
            animator.cancel();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !targets.isEmpty()) {
            restoreWindowSurfaceLayersImmediately(targets);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void restoreWindowSurfaceLayersImmediately(
            List<WindowSurfaceAnimationTarget> targets) {
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        for (WindowSurfaceAnimationTarget target : targets) {
            target.host.resetWindowSurfaceAnimation(transaction);
        }
        transaction.apply();
        transaction.close();
    }

    private void finishWindowFrameAnimation(Rect[] targetRects, Runnable onAnimationFinished) {
        finishWindowFrameAnimation(targetRects, onAnimationFinished, Collections.emptyList());
    }

    private void finishWindowFrameAnimation(Rect[] targetRects, Runnable onAnimationFinished,
                                            List<WindowSurfaceAnimationTarget> surfaceTargets) {
        running = false;
        lastAnimationEndUptime = SystemClock.uptimeMillis();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            workspace().suppressLayout(true);
        }
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            OneStepWindowView windowView = windowViews()[slot];
            setWindowFrame(windowView, targetRects[slot]);
            if (surfaceTargets.isEmpty()) {
                resetWindowTransform(windowView);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            workspace().suppressLayout(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !surfaceTargets.isEmpty()) {
            callbacks.applyWindowZOrder();
            finishWindowSurfaceAnimations(surfaceTargets);
        }
        Runnable refreshAction = onAnimationFinished != null
                ? onAnimationFinished : callbacks::refreshAllEmbeddedSlotLayouts;
        workspace().postDelayed(refreshAction, POST_ANIMATION_ROLE_REFRESH_DELAY_MS);
        callbacks.scheduleDeferredWorkFlush();
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void finishWindowSurfaceAnimations(List<WindowSurfaceAnimationTarget> targets) {
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        for (WindowSurfaceAnimationTarget target : targets) {
            target.host.resetWindowSurfaceAnimation(transaction);
        }
        transaction.apply();
        transaction.close();
        workspace().invalidate();
    }

    private final class WindowSurfaceAnimationTarget {
        final int slot;
        final RootVirtualDisplayHost host;
        final Rect startRect;
        final Rect endRect;

        WindowSurfaceAnimationTarget(int slot, RootVirtualDisplayHost host, Rect startRect,
                                     Rect endRect, int windowOffsetX, int windowOffsetY) {
            this.slot = slot;
            this.host = host;
            this.startRect = new Rect(startRect);
            this.endRect = new Rect(endRect);
            this.startRect.offset(windowOffsetX, windowOffsetY);
            this.endRect.offset(windowOffsetX, windowOffsetY);
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        void apply(SurfaceControl.Transaction transaction, float progress) {
            float left = startRect.left + (endRect.left - startRect.left) * progress;
            float top = startRect.top + (endRect.top - startRect.top) * progress;
            float scaleX = 1f + (endRect.width() / Math.max(1f, startRect.width()) - 1f)
                    * progress;
            float scaleY = 1f + (endRect.height() / Math.max(1f, startRect.height()) - 1f)
                    * progress;
            host.applyWindowSurfaceAnimation(transaction,
                    left - scaleX * startRect.left,
                    top - scaleY * startRect.top,
                    scaleX, scaleY);
        }
    }


    public boolean isRunning() {
        return running;
    }

    public long getLastAnimationEndUptime() {
        return lastAnimationEndUptime;
    }

    public void cancelAndReset() {
        generation++;
        cancel();
        running = false;
    }

    private OneStepWindowView[] windowViews() { return callbacks.windowViews(); }
    private EmbeddedAppHost[] embeddedHosts() { return callbacks.embeddedHosts(); }
    private LauncherApp[] windowApps() { return callbacks.windowApps(); }
    private ViewGroup workspace() { return callbacks.workspace(); }
    private Handler mainHandler() { return callbacks.mainHandler(); }
    private int activeMainSlot() { return callbacks.activeMainSlot(); }

    private static void configureWindowPivot(View view, android.graphics.Rect currentRect,
                                             android.graphics.Rect targetRect) {
        float pivotX;
        if (targetRect.left == currentRect.left) {
            pivotX = 0f;
        } else if (targetRect.right == currentRect.right) {
            pivotX = currentRect.width();
        } else {
            pivotX = currentRect.width() / 2f;
        }
        float pivotY;
        if (targetRect.top == currentRect.top) {
            pivotY = 0f;
        } else if (targetRect.bottom == currentRect.bottom) {
            pivotY = currentRect.height();
        } else {
            pivotY = currentRect.height() / 2f;
        }
        view.setPivotX(pivotX);
        view.setPivotY(pivotY);
    }

    private static float calculateTranslationX(
            View view, android.graphics.Rect currentRect, android.graphics.Rect targetRect,
            float scaleX) {
        return targetRect.left - currentRect.left - view.getPivotX() * (1f - scaleX);
    }

    private static float calculateTranslationY(
            View view, android.graphics.Rect currentRect, android.graphics.Rect targetRect,
            float scaleY) {
        return targetRect.top - currentRect.top - view.getPivotY() * (1f - scaleY);
    }

    private static android.graphics.Rect getWindowFrame(View view) {
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        if (!(rawParams instanceof android.widget.FrameLayout.LayoutParams)) {
            return new android.graphics.Rect();
        }
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) rawParams;
        return new android.graphics.Rect(params.leftMargin, params.topMargin,
                params.leftMargin + params.width, params.topMargin + params.height);
    }

    private static void setWindowFrame(View view, android.graphics.Rect rect) {
        android.widget.FrameLayout.LayoutParams params =
                (android.widget.FrameLayout.LayoutParams) view.getLayoutParams();
        if (params == null) {
            params = new android.widget.FrameLayout.LayoutParams(rect.width(), rect.height());
        }
        params.width = rect.width();
        params.height = rect.height();
        params.leftMargin = rect.left;
        params.topMargin = rect.top;
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.START;
        view.setLayoutParams(params);
    }

    private static void resetWindowTransform(View view) {
        view.animate().cancel();
        view.setTranslationX(0f);
        view.setTranslationY(0f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setPivotX(view.getWidth() / 2f);
        view.setPivotY(view.getHeight() / 2f);
    }
}
