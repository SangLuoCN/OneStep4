package com.sangluo.onestep.ui.background;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Builds and shares the blurred workspace wallpaper used by window placeholders. */
public final class BlurredBackgroundView extends View {
    private static final String TAG = "OneStep40";
    private static final int BLUR_DOWNSAMPLE = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final int[] backgroundLocation = new int[2];
    private final int[] targetLocation = new int[2];
    private final RectF sharedBackgroundDestination = new RectF();
    private final Executor buildExecutor;
    private final Supplier<Drawable> backgroundSupplier;
    private final BooleanSupplier shouldDeferApply;
    private final BooleanSupplier destroyed;
    private final Runnable dependentInvalidator;
    private Bitmap blurredBitmap;
    private int cachedWidth;
    private int cachedHeight;
    private int backgroundBuildGeneration;
    private boolean backgroundBuildPending;

    public BlurredBackgroundView(Context context, Executor buildExecutor,
                                 Supplier<Drawable> backgroundSupplier,
                                 BooleanSupplier shouldDeferApply,
                                 BooleanSupplier destroyed,
                                 Runnable dependentInvalidator) {
        super(context);
        this.buildExecutor = buildExecutor;
        this.backgroundSupplier = backgroundSupplier;
        this.shouldDeferApply = shouldDeferApply;
        this.destroyed = destroyed;
        this.dependentInvalidator = dependentInvalidator;
    }

    public void refreshBackground() {
        clearCache();
        dependentInvalidator.run();
        invalidate();
    }

    public void drawSharedBackground(Canvas canvas, View target) {
        if (blurredBitmap == null || blurredBitmap.isRecycled()
                || getWidth() <= 0 || getHeight() <= 0
                || target.getWidth() <= 0 || target.getHeight() <= 0) {
            return;
        }
        getLocationInWindow(backgroundLocation);
        target.getLocationInWindow(targetLocation);
        float left = backgroundLocation[0] - targetLocation[0];
        float top = backgroundLocation[1] - targetLocation[1];
        sharedBackgroundDestination.set(left, top, left + getWidth(), top + getHeight());
        canvas.drawBitmap(blurredBitmap, null, sharedBackgroundDestination, paint);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width != oldWidth || height != oldHeight) {
            refreshBackground();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        ensureBackgroundCache();
        if (blurredBitmap != null && !blurredBitmap.isRecycled()) {
            canvas.drawBitmap(blurredBitmap, null,
                    new Rect(0, 0, getWidth(), getHeight()), paint);
        }
    }

    private void ensureBackgroundCache() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (blurredBitmap != null && !blurredBitmap.isRecycled()
                && cachedWidth == width && cachedHeight == height) {
            return;
        }
        if (backgroundBuildPending) {
            return;
        }
        recycleBlurredBitmap();
        int generation = ++backgroundBuildGeneration;
        int radius = Math.max(2, dp(18) / BLUR_DOWNSAMPLE);
        backgroundBuildPending = true;
        try {
            buildExecutor.execute(() -> buildBackgroundCache(generation, width, height, radius));
        } catch (RuntimeException e) {
            backgroundBuildPending = false;
            Log.w(TAG, "Queue blurred background failed: " + e.getClass().getSimpleName());
        }
    }

    private void buildBackgroundCache(int generation, int width, int height, int radius) {
        Bitmap result = null;
        try {
            Bitmap rendered = renderAspectFillBackground(backgroundSupplier.get(), width, height);
            result = blurBitmap(rendered, radius);
            new Canvas(result).drawColor(0x4872956f);
        } catch (OutOfMemoryError | RuntimeException e) {
            Log.w(TAG, "Build blurred background failed: " + e.getClass().getSimpleName());
        }
        Bitmap completed = result;
        post(() -> applyBackgroundCache(generation, width, height, completed));
    }

    private void applyBackgroundCache(int generation, int width, int height, Bitmap result) {
        if (shouldDeferApply.getAsBoolean()) {
            postDelayed(() -> applyBackgroundCache(generation, width, height, result), 64L);
            return;
        }
        if (generation != backgroundBuildGeneration || destroyed.getAsBoolean()
                || width != getWidth() || height != getHeight()) {
            recycleBitmap(result);
            return;
        }
        backgroundBuildPending = false;
        recycleBlurredBitmap();
        blurredBitmap = result;
        if (result != null) {
            cachedWidth = width;
            cachedHeight = height;
        }
        dependentInvalidator.run();
        invalidate();
    }

    private Bitmap renderAspectFillBackground(Drawable drawable, int width, int height) {
        int bitmapWidth = Math.max(1, width / BLUR_DOWNSAMPLE);
        int bitmapHeight = Math.max(1, height / BLUR_DOWNSAMPLE);
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int sourceWidth = drawable.getIntrinsicWidth() > 0
                ? drawable.getIntrinsicWidth() : bitmapWidth;
        int sourceHeight = drawable.getIntrinsicHeight() > 0
                ? drawable.getIntrinsicHeight() : bitmapHeight;
        float scale = Math.max(bitmapWidth / (float) sourceWidth,
                bitmapHeight / (float) sourceHeight);
        int drawWidth = Math.max(1, Math.round(sourceWidth * scale));
        int drawHeight = Math.max(1, Math.round(sourceHeight * scale));
        int left = Math.round((bitmapWidth - drawWidth) / 2f);
        int top = Math.round((bitmapHeight - drawHeight) / 2f);
        Rect oldBounds = new Rect();
        drawable.copyBounds(oldBounds);
        drawable.setBounds(left, top, left + drawWidth, top + drawHeight);
        drawable.draw(canvas);
        drawable.setBounds(oldBounds);
        return bitmap;
    }

    private Bitmap blurBitmap(Bitmap source, int radius) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (radius <= 0 || width <= 1 || height <= 1) {
            return source;
        }
        int[] pixels = new int[width * height];
        int[] temp = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int iteration = 0; iteration < 2; iteration++) {
            blurHorizontal(pixels, temp, width, height, radius);
            blurVertical(temp, pixels, width, height, radius);
        }
        source.setPixels(pixels, 0, width, 0, 0, width, height);
        return source;
    }

    private void blurHorizontal(int[] input, int[] output, int width, int height, int radius) {
        int windowSize = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int rowOffset = y * width;
            int alpha = 0;
            int red = 0;
            int green = 0;
            int blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int color = input[rowOffset + clampIndex(offset, width)];
                alpha += Color.alpha(color);
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
            }
            for (int x = 0; x < width; x++) {
                output[rowOffset + x] = Color.argb(alpha / windowSize, red / windowSize,
                        green / windowSize, blue / windowSize);
                if (x + 1 < width) {
                    int leaving = input[rowOffset + clampIndex(x - radius, width)];
                    int entering = input[rowOffset + clampIndex(x + radius + 1, width)];
                    alpha += Color.alpha(entering) - Color.alpha(leaving);
                    red += Color.red(entering) - Color.red(leaving);
                    green += Color.green(entering) - Color.green(leaving);
                    blue += Color.blue(entering) - Color.blue(leaving);
                }
            }
        }
    }

    private void blurVertical(int[] input, int[] output, int width, int height, int radius) {
        int windowSize = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            int alpha = 0;
            int red = 0;
            int green = 0;
            int blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int color = input[clampIndex(offset, height) * width + x];
                alpha += Color.alpha(color);
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
            }
            for (int y = 0; y < height; y++) {
                output[y * width + x] = Color.argb(alpha / windowSize, red / windowSize,
                        green / windowSize, blue / windowSize);
                if (y + 1 < height) {
                    int leaving = input[clampIndex(y - radius, height) * width + x];
                    int entering = input[clampIndex(y + radius + 1, height) * width + x];
                    alpha += Color.alpha(entering) - Color.alpha(leaving);
                    red += Color.red(entering) - Color.red(leaving);
                    green += Color.green(entering) - Color.green(leaving);
                    blue += Color.blue(entering) - Color.blue(leaving);
                }
            }
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int clampIndex(int index, int size) {
        return Math.max(0, Math.min(size - 1, index));
    }

    private void clearCache() {
        backgroundBuildGeneration++;
        backgroundBuildPending = false;
        recycleBlurredBitmap();
    }

    private void recycleBlurredBitmap() {
        recycleBitmap(blurredBitmap);
        blurredBitmap = null;
        cachedWidth = 0;
        cachedHeight = 0;
    }

    private void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}
