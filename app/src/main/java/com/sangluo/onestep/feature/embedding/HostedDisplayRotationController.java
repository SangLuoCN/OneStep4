package com.sangluo.onestep.feature.embedding;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.util.Log;
import android.view.OrientationEventListener;

/** Owns display and device-orientation listener registration for embedded displays. */
public final class HostedDisplayRotationController implements AutoCloseable {
    private static final String TAG = "OneStep40";

    public interface Listener {
        void onHostedDisplayChanged(int displayId);

        void onPhysicalLandscapeRotationChanged(int rotation);
    }

    private final DisplayManager displayManager;
    private final Handler handler;
    private final Listener listener;
    private final DisplayManager.DisplayListener displayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                }

                @Override
                public void onDisplayRemoved(int displayId) {
                }

                @Override
                public void onDisplayChanged(int displayId) {
                    listener.onHostedDisplayChanged(displayId);
                }
            };
    private OrientationEventListener orientationListener;
    private boolean displayListenerRegistered;
    private boolean orientationListenerEnabled;
    private int latestLandscapeRotation = -1;

    public HostedDisplayRotationController(Context context, Handler handler, Listener listener) {
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        this.handler = handler;
        this.listener = listener;
        orientationListener = new OrientationEventListener(context) {
            @Override
            public void onOrientationChanged(int orientation) {
                handleOrientationChanged(orientation);
            }
        };
        if (!orientationListener.canDetectOrientation()) {
            Log.w(TAG, "Device orientation sensor unavailable for hosted fullscreen video");
            orientationListener = null;
        }
    }

    public void register() {
        if (displayManager == null || displayListenerRegistered) {
            return;
        }
        displayManager.registerDisplayListener(displayListener, handler);
        displayListenerRegistered = true;
    }

    public void enable() {
        if (orientationListener == null || orientationListenerEnabled) {
            return;
        }
        orientationListener.enable();
        orientationListenerEnabled = true;
    }

    public void disable() {
        if (orientationListener == null || !orientationListenerEnabled) {
            return;
        }
        orientationListener.disable();
        orientationListenerEnabled = false;
    }

    public int getLatestLandscapeRotation() {
        return latestLandscapeRotation;
    }

    public void clearLatestRotation() {
        latestLandscapeRotation = -1;
    }

    @Override
    public void close() {
        disable();
        if (displayManager != null && displayListenerRegistered) {
            try {
                displayManager.unregisterDisplayListener(displayListener);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unregister hosted display rotation listener failed: "
                        + e.getClass().getSimpleName());
            }
        }
        displayListenerRegistered = false;
        orientationListener = null;
        latestLandscapeRotation = -1;
    }

    private void handleOrientationChanged(int orientation) {
        int landscapeRotation = DeviceOrientationMapper.mapLandscapeRotation(orientation);
        if (landscapeRotation < 0) {
            if (DeviceOrientationMapper.isStablePortrait(orientation)) {
                latestLandscapeRotation = -1;
            }
            return;
        }
        if (landscapeRotation == latestLandscapeRotation) {
            return;
        }
        latestLandscapeRotation = landscapeRotation;
        listener.onPhysicalLandscapeRotationChanged(landscapeRotation);
    }
}
