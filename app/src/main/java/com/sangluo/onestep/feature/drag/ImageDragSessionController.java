package com.sangluo.onestep.feature.drag;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.io.File;
import java.io.IOException;

/** Owns the image preview view attached to the OneStep display-0 content hierarchy. */
public final class ImageDragSessionController {
    private static final String TAG = "OneStep40-ImageDrag";

    public interface Callbacks {
        ViewGroup previewContainer();

        int previewDisplayId();

        View workspace();

        Rect[] windowFrames();

        int slotCount();

        boolean canDropOnSlot(int sourceSlot, int candidateSlot);

        void cancelInjectedSourceTouch(int sourceSlot);

        void showShareTargets(String mimeType);

        int findShareTarget(float rawX, float rawY);

        void setHoveredShareTarget(int targetIndex);

        void hideShareTargets();

        void deliverToShareTarget(
                int targetIndex, File imageFile, String mimeType, Uri sourceUri);

        void deliverToSlot(int slot, File imageFile, String mimeType, Uri sourceUri);

        int dp(float value);
    }

    private static final int PREVIEW_CONTENT_DP = 64;
    private static final int PREVIEW_DECODE_EDGE_PX = 512;
    private static final int PREVIEW_PADDING_DP = 0;
    private static final int TOUCH_POINT_OFFSET_DP = 30;
    private final Callbacks callbacks;
    private final int[] anchorLocation = new int[2];
    private final int[] workspaceLocation = new int[2];

    private int sourceSlot = -1;
    private int hoverSlot = -1;
    private int hoverShareTarget = -1;
    private File imageFile;
    private String mimeType;
    private Uri sourceUri;
    private Bitmap previewBitmap;
    private ImageDragPreviewSurface preview;
    private ViewGroup previewHost;
    private boolean previewAttached;

    public ImageDragSessionController(Callbacks callbacks) {
        this.callbacks = callbacks;
    }

    public boolean begin(int sourceSlot, File imageFile, String mimeType, Uri sourceUri,
                         float rawX, float rawY) {
        cancel();
        if (sourceSlot < 0 || imageFile == null || !imageFile.isFile()) {
            return false;
        }
        Bitmap bitmap = decodePreview(imageFile, mimeType);
        ViewGroup container = callbacks.previewContainer();
        if (bitmap == null || !isDefaultDisplayContainer(container)) {
            Log.w(TAG, bitmap == null
                    ? "Cannot decode original image received by OneStep: " + imageFile.length()
                    : "Display 0 preview container unavailable");
            if (bitmap != null) {
                bitmap.recycle();
            }
            return false;
        }
        this.sourceSlot = sourceSlot;
        this.imageFile = imageFile;
        this.mimeType = mimeType;
        this.sourceUri = sourceUri;
        previewBitmap = bitmap;
        preview = createPreview(container, bitmap);
        try {
            container.addView(preview);
            previewHost = container;
            previewAttached = true;
            preview.bringToFront();
        } catch (RuntimeException e) {
            Log.e(TAG, "Cannot attach top preview surface to OneStep display 0", e);
            cancel();
            return false;
        }
        callbacks.showShareTargets(mimeType);
        callbacks.cancelInjectedSourceTouch(sourceSlot);
        updatePosition(rawX, rawY);
        updateHover(rawX, rawY);
        Log.i(TAG, "Top preview surface attached inside OneStep display 0 root");
        return true;
    }

    private boolean isDefaultDisplayContainer(ViewGroup container) {
        if (container == null || !container.isAttachedToWindow()
                || callbacks.previewDisplayId() != Display.DEFAULT_DISPLAY) {
            return false;
        }
        Display display = container.getDisplay();
        return display != null && display.getDisplayId() == Display.DEFAULT_DISPLAY;
    }

    private static Bitmap decodePreview(File imageFile, String mimeType) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFile.getAbsolutePath(), bounds);
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            int sampleSize = 1;
            int largestEdge = Math.max(bounds.outWidth, bounds.outHeight);
            while (largestEdge / (sampleSize * 2) >= PREVIEW_DECODE_EDGE_PX) {
                sampleSize *= 2;
            }
            BitmapFactory.Options preview = new BitmapFactory.Options();
            preview.inSampleSize = sampleSize;
            return BitmapFactory.decodeFile(imageFile.getAbsolutePath(), preview);
        }
        if (!ImageDragSourcePolicy.isVideoMimeType(mimeType)) {
            return null;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(imageFile.getAbsolutePath());
            int width = parsePositiveInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height = parsePositiveInt(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int largestEdge = Math.max(width, height);
            if (largestEdge > 0) {
                float scale = Math.min(1f,
                        (float) PREVIEW_DECODE_EDGE_PX / largestEdge);
                return retriever.getScaledFrameAtTime(
                        -1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        Math.max(1, Math.round(width * scale)),
                        Math.max(1, Math.round(height * scale)));
            }
            return retriever.getFrameAtTime(
                    -1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (RuntimeException error) {
            Log.w(TAG, "Cannot decode video drag preview", error);
            return null;
        } finally {
            try {
                retriever.release();
            } catch (IOException | RuntimeException ignored) {
            }
        }
    }

    private static int parsePositiveInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public boolean isActiveForSource(int slot) {
        return previewAttached && preview != null && sourceSlot == slot;
    }

    public boolean onTouch(int slot, MotionEvent event) {
        if (!isActiveForSource(slot) || event == null) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                updatePosition(event.getRawX(), event.getRawY());
                updateHover(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                updatePosition(event.getRawX(), event.getRawY());
                updateHover(event.getRawX(), event.getRawY());
                requestDrop();
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return true;
            default:
                return true;
        }
    }

    public void cancel() {
        setHoverTargets(-1, -1);
        callbacks.hideShareTargets();
        removePreviewView();
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        File abandoned = imageFile;
        imageFile = null;
        mimeType = null;
        sourceUri = null;
        sourceSlot = -1;
        if (abandoned != null && abandoned.isFile() && !abandoned.delete()) {
            abandoned.deleteOnExit();
        }
    }

    private ImageDragPreviewSurface createPreview(ViewGroup parent, Bitmap bitmap) {
        int padding = callbacks.dp(PREVIEW_PADDING_DP);
        int contentSize = callbacks.dp(PREVIEW_CONTENT_DP);
        int totalSize = contentSize + padding * 2;
        ImageDragPreviewSurface surface = new ImageDragPreviewSurface(
                parent, bitmap, padding, contentSize,
                callbacks.dp(5), Math.max(1, callbacks.dp(1)));
        surface.setLayoutParams(new FrameLayout.LayoutParams(totalSize, totalSize));
        return surface;
    }

    private void updatePosition(float rawX, float rawY) {
        if (preview == null) {
            return;
        }
        ViewGroup container = callbacks.previewContainer();
        if (container == null) {
            return;
        }
        container.getLocationOnScreen(anchorLocation);
        int width = preview.getLayoutParams().width;
        int height = preview.getLayoutParams().height;
        float localX = rawX - anchorLocation[0];
        float localY = rawY - anchorLocation[1];
        float maxLeft = Math.max(0f, container.getWidth() - width);
        float maxTop = Math.max(0f, container.getHeight() - height);
        float left = Math.max(0f, Math.min(localX - width * 0.5f, maxLeft));
        float top = Math.max(0f, Math.min(
                localY - height - callbacks.dp(TOUCH_POINT_OFFSET_DP), maxTop));
        preview.setX(left);
        preview.setY(top);
    }

    private void updateHover(float rawX, float rawY) {
        int shareTarget = callbacks.findShareTarget(rawX, rawY);
        if (shareTarget >= 0) {
            setHoverTargets(shareTarget, -1);
            return;
        }
        View workspace = callbacks.workspace();
        if (workspace == null) {
            setHoverTargets(-1, -1);
            return;
        }
        workspace.getLocationOnScreen(workspaceLocation);
        float workspaceX = rawX - workspaceLocation[0];
        float workspaceY = rawY - workspaceLocation[1];
        boolean[] eligible = new boolean[callbacks.slotCount()];
        for (int slot = 0; slot < eligible.length; slot++) {
            eligible[slot] = callbacks.canDropOnSlot(sourceSlot, slot);
        }
        Rect[] frames = callbacks.windowFrames();
        int[][] frameValues = new int[frames == null ? 0 : frames.length][];
        for (int slot = 0; slot < frameValues.length; slot++) {
            Rect frame = frames[slot];
            if (frame != null) {
                frameValues[slot] = new int[]{
                        frame.left, frame.top, frame.right, frame.bottom};
            }
        }
        int target = ImageDragHoverPolicy.findTarget(
                workspaceX, workspaceY, frameValues, eligible);
        setHoverTargets(-1, target);
    }

    private void setHoverTargets(int shareTarget, int slot) {
        if (shareTarget == hoverShareTarget && slot == hoverSlot) {
            return;
        }
        hoverShareTarget = shareTarget;
        hoverSlot = slot;
        callbacks.setHoveredShareTarget(shareTarget);
        if (preview != null) {
            preview.animate().cancel();
            preview.animate()
                    .scaleX(shareTarget < 0 && slot < 0 ? 1f : 1.08f)
                    .scaleY(shareTarget < 0 && slot < 0 ? 1f : 1.08f)
                    .setDuration(120L)
                    .start();
        }
    }

    private void requestDrop() {
        int shareTarget = hoverShareTarget;
        int targetSlot = hoverSlot;
        if (shareTarget < 0 && targetSlot < 0) {
            cancel();
            return;
        }
        File deliveredFile = imageFile;
        String deliveredMime = mimeType;
        Uri deliveredUri = sourceUri;
        removePreviewWithoutDeletingFile();
        if (shareTarget >= 0) {
            callbacks.deliverToShareTarget(
                    shareTarget, deliveredFile, deliveredMime, deliveredUri);
        } else {
            callbacks.deliverToSlot(targetSlot, deliveredFile, deliveredMime, deliveredUri);
        }
    }

    private void removePreviewWithoutDeletingFile() {
        removePreviewView();
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        sourceSlot = -1;
        hoverSlot = -1;
        hoverShareTarget = -1;
        imageFile = null;
        mimeType = null;
        sourceUri = null;
        callbacks.setHoveredShareTarget(-1);
        callbacks.hideShareTargets();
    }

    private void removePreviewView() {
        ImageDragPreviewSurface previewToRemove = preview;
        if (previewToRemove != null) {
            previewToRemove.animate().cancel();
            previewToRemove.releasePreview();
        }
        if (previewAttached && previewToRemove != null) {
            if (previewHost != null) {
                previewHost.removeView(previewToRemove);
            }
        }
        previewAttached = false;
        previewHost = null;
        preview = null;
    }

    /** A view-owned Surface layer, not a WindowManager overlay. */
    private static final class ImageDragPreviewSurface extends SurfaceView
            implements SurfaceHolder.Callback2 {
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint imagePaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect sourceRect = new Rect();
        private final RectF imageRect = new RectF();
        private final RectF outerRect = new RectF();
        private final Path imageClipPath = new Path();
        private final int padding;
        private final int contentSize;
        private final int cornerRadius;
        private final int borderWidth;
        private Bitmap bitmap;
        private boolean released;

        ImageDragPreviewSurface(
                ViewGroup parent, Bitmap bitmap, int padding, int contentSize,
                int cornerRadius, int borderWidth) {
            super(parent.getContext());
            this.bitmap = bitmap;
            this.padding = padding;
            this.contentSize = contentSize;
            this.cornerRadius = cornerRadius;
            this.borderWidth = borderWidth;
            setZOrderOnTop(true);
            setFocusable(false);
            setFocusableInTouchMode(false);
            setClickable(false);
            getHolder().setFormat(PixelFormat.TRANSLUCENT);
            getHolder().addCallback(this);
            fillPaint.setColor(0xf5ffffff);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(borderWidth);
            borderPaint.setColor(0x66000000);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            drawPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            drawPreview();
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        @Override
        public void surfaceRedrawNeeded(SurfaceHolder holder) {
            drawPreview();
        }

        void releasePreview() {
            released = true;
            bitmap = null;
            getHolder().removeCallback(this);
        }

        private void drawPreview() {
            Bitmap current = bitmap;
            SurfaceHolder holder = getHolder();
            if (released || current == null || current.isRecycled()
                    || !holder.getSurface().isValid()) {
                return;
            }
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                float borderInset = borderWidth * 0.5f;
                outerRect.set(borderInset, borderInset,
                        getWidth() - borderInset, getHeight() - borderInset);
                canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, fillPaint);
                imageRect.set(padding, padding,
                        padding + contentSize, padding + contentSize);
                calculateCenterCrop(current, sourceRect);
                int restoreCount = canvas.save();
                imageClipPath.reset();
                imageClipPath.addRoundRect(
                        imageRect, cornerRadius, cornerRadius, Path.Direction.CW);
                canvas.clipPath(imageClipPath);
                canvas.drawBitmap(current, sourceRect, imageRect, imagePaint);
                canvas.restoreToCount(restoreCount);
                canvas.drawRoundRect(outerRect, cornerRadius, cornerRadius, borderPaint);
            } catch (RuntimeException e) {
                Log.w(TAG, "Draw image drag preview surface failed", e);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                    } catch (RuntimeException ignored) {
                    }
                }
            }
        }

        private static void calculateCenterCrop(Bitmap bitmap, Rect output) {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            if (width > height) {
                int left = (width - height) / 2;
                output.set(left, 0, left + height, height);
            } else {
                int top = (height - width) / 2;
                output.set(0, top, width, top + width);
            }
        }
    }
}
