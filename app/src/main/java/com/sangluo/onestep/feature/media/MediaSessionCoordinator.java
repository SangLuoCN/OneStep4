package com.sangluo.onestep.feature.media;

import android.content.Context;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.sangluo.onestep.MediaNotificationListenerService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects and observes the media session that should drive the top media component. */
public final class MediaSessionCoordinator implements AutoCloseable {
    private static final String TAG = "OneStep40";

    public interface Listener {
        void onMediaStateChanged(MediaController controller,
                                 MediaNotificationListenerService.MediaNotificationSnapshot
                                         notification,
                                 boolean permissionDenied,
                                 boolean sessionChanged);

        void onQueueChanged();

        void onSessionAccessDenied();
    }

    private final Context context;
    private final Handler callbackHandler;
    private final Listener listener;
    private final MediaSessionManager sessionManager;
    private final Map<MediaSession.Token, ObservedController> observedControllers =
            new HashMap<>();
    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            notifyStateChanged(false);
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            notifyStateChanged(false);
        }

        @Override
        public void onQueueChanged(List<MediaSession.QueueItem> queue) {
            listener.onQueueChanged();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    private MediaController activeController;
    private MediaNotificationListenerService.MediaNotificationSnapshot activeNotification;
    private List<String> availableSourcePackages = Collections.emptyList();
    private String selectedSourcePackage;
    private boolean permissionDenied;
    private boolean closed;

    private final class ObservedController {
        final MediaController controller;
        final MediaController.Callback callback;
        private boolean wasPlaying;

        ObservedController(MediaController controller) {
            this.controller = controller;
            wasPlaying = isPlaying(controller);
            callback = new MediaController.Callback() {
                @Override
                public void onPlaybackStateChanged(PlaybackState state) {
                    boolean playing = state != null
                            && state.getState() == PlaybackState.STATE_PLAYING;
                    boolean startedPlaying = playing && !wasPlaying;
                    wasPlaying = playing;
                    if (startedPlaying) {
                        focusSourceFromPlayback(ObservedController.this.controller.getPackageName());
                    }
                    refresh();
                }

                @Override
                public void onMetadataChanged(MediaMetadata metadata) {
                    refresh();
                }

                @Override
                public void onSessionDestroyed() {
                    refresh();
                }
            };
        }
    }

    public MediaSessionCoordinator(Context context, Handler callbackHandler, Listener listener) {
        this.context = context.getApplicationContext();
        this.callbackHandler = callbackHandler;
        this.listener = listener;
        sessionManager = (MediaSessionManager) context.getSystemService(
                Context.MEDIA_SESSION_SERVICE);
    }

    public void refresh() {
        if (closed) {
            return;
        }
        try {
            List<MediaNotificationListenerService.MediaNotificationSnapshot> notifications =
                    MediaNotificationListenerService.getSnapshots();
            if (sessionManager == null) {
                syncObservedControllers(Collections.emptyList());
                availableSourcePackages = collectSourcePackages(
                        Collections.emptyList(), notifications);
                clearMissingSourceSelection();
                activeNotification = findBestNotification(
                        notifications, selectedSourcePackage);
                setActiveController(null);
                return;
            }

            List<MediaController> controllers = loadActiveControllers();
            availableSourcePackages = collectSourcePackages(controllers, notifications);
            syncObservedControllers(controllers);
            focusMostRecentPlaybackWhenUnselected(controllers);
            clearMissingSourceSelection();
            MediaNotificationListenerService.MediaNotificationSnapshot preferredNotification =
                    findBestNotification(notifications, selectedSourcePackage);
            String preferredPackage = !TextUtils.isEmpty(selectedSourcePackage)
                    ? selectedSourcePackage
                    : preferredNotification == null ? null : preferredNotification.packageName;
            MediaController selected = selectController(controllers, preferredPackage);
            if (!TextUtils.isEmpty(selectedSourcePackage) && selected != null
                    && !TextUtils.equals(selectedSourcePackage, selected.getPackageName())) {
                selected = null;
            }

            activeNotification = findBestNotification(notifications, preferredPackage);
            if (activeNotification == null && selected != null) {
                activeNotification = findBestNotification(
                        notifications, selected.getPackageName());
            }
            if (activeNotification == null) {
                activeNotification = preferredNotification;
            }
            if (selected == null && activeNotification != null
                    && activeNotification.sessionToken != null) {
                try {
                    selected = new MediaController(context, activeNotification.sessionToken);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Create media controller from notification failed: "
                            + e.getClass().getSimpleName());
                }
            }
            if (selected != null && (activeNotification == null
                    || !TextUtils.equals(activeNotification.packageName,
                    selected.getPackageName()))) {
                activeNotification = findBestNotification(
                        notifications, selected.getPackageName());
            }
            setActiveController(selected);
        } catch (Throwable t) {
            Log.w(TAG, "Refresh media controller failed: " + t.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        MediaController previous = activeController;
        activeController = null;
        if (previous != null) {
            try {
                previous.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
            }
        }
        clearObservedControllers();
        activeNotification = null;
        availableSourcePackages = Collections.emptyList();
        selectedSourcePackage = null;
        notifyStateChanged(previous != null);
    }

    public List<String> getAvailableSourcePackages() {
        return new ArrayList<>(availableSourcePackages);
    }

    public boolean selectSource(String packageName) {
        if (TextUtils.isEmpty(packageName) || !availableSourcePackages.contains(packageName)) {
            return false;
        }
        selectedSourcePackage = packageName;
        refresh();
        return true;
    }

    private void focusSourceFromPlayback(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        selectedSourcePackage = packageName;
    }

    private void focusMostRecentPlaybackWhenUnselected(List<MediaController> controllers) {
        if (!TextUtils.isEmpty(selectedSourcePackage)) {
            return;
        }
        MediaController latestPlaying = findMostRecentlyUpdatedPlayingController(controllers);
        if (latestPlaying == null) {
            return;
        }
        selectedSourcePackage = latestPlaying.getPackageName();
    }

    private void clearMissingSourceSelection() {
        if (!TextUtils.isEmpty(selectedSourcePackage)
                && !availableSourcePackages.contains(selectedSourcePackage)) {
            selectedSourcePackage = null;
        }
    }

    private void syncObservedControllers(List<MediaController> controllers) {
        Set<MediaSession.Token> currentTokens = new HashSet<>();
        for (MediaController controller : controllers) {
            if (controller == null || controller.getSessionToken() == null) {
                continue;
            }
            MediaSession.Token token = controller.getSessionToken();
            currentTokens.add(token);
            if (observedControllers.containsKey(token)) {
                continue;
            }
            ObservedController observed = new ObservedController(controller);
            try {
                controller.registerCallback(observed.callback, callbackHandler);
                observedControllers.put(token, observed);
            } catch (RuntimeException e) {
                Log.w(TAG, "Register source media callback failed: "
                        + e.getClass().getSimpleName());
            }
        }

        Iterator<Map.Entry<MediaSession.Token, ObservedController>> iterator =
                observedControllers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MediaSession.Token, ObservedController> entry = iterator.next();
            if (currentTokens.contains(entry.getKey())) {
                continue;
            }
            unregisterObservedController(entry.getValue());
            iterator.remove();
        }
    }

    private void clearObservedControllers() {
        for (ObservedController observed : observedControllers.values()) {
            unregisterObservedController(observed);
        }
        observedControllers.clear();
    }

    private void unregisterObservedController(ObservedController observed) {
        try {
            observed.controller.unregisterCallback(observed.callback);
        } catch (RuntimeException ignored) {
        }
    }

    private List<MediaController> loadActiveControllers() {
        permissionDenied = false;
        try {
            return sessionManager.getActiveSessions(null);
        } catch (SecurityException e) {
            Log.w(TAG, "MEDIA_CONTENT_CONTROL not granted, try notification listener: "
                    + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            Log.w(TAG, "Read media sessions failed: " + e.getClass().getSimpleName());
            return Collections.emptyList();
        } catch (Throwable t) {
            Log.w(TAG, "Read media sessions failed: " + t.getClass().getSimpleName());
            return Collections.emptyList();
        }

        try {
            return sessionManager.getActiveSessions(
                    MediaNotificationListenerService.getComponentName(context));
        } catch (SecurityException e) {
            permissionDenied = true;
            listener.onSessionAccessDenied();
            Log.w(TAG, "Notification listener media sessions unavailable: "
                    + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            Log.w(TAG, "Read listener media sessions failed: " + e.getClass().getSimpleName());
        } catch (Throwable t) {
            Log.w(TAG, "Read listener media sessions failed: " + t.getClass().getSimpleName());
        }
        return Collections.emptyList();
    }

    private MediaController selectController(List<MediaController> controllers,
                                             String preferredPackage) {
        MediaController selected = null;
        if (!TextUtils.isEmpty(preferredPackage)) {
            selected = findControllerForPackage(controllers, preferredPackage, true);
            if (selected == null) {
                selected = findControllerForPackage(controllers, preferredPackage, false);
            }
        }
        if (selected == null) {
            selected = findMostRecentlyUpdatedPlayingController(controllers);
        }
        if (selected == null) {
            for (MediaController controller : controllers) {
                if (controller.getMetadata() != null) {
                    selected = controller;
                    break;
                }
            }
        }
        return selected == null && !controllers.isEmpty() ? controllers.get(0) : selected;
    }

    private MediaController findMostRecentlyUpdatedPlayingController(
            List<MediaController> controllers) {
        MediaController selected = null;
        long selectedUpdateTime = Long.MIN_VALUE;
        for (MediaController controller : controllers) {
            PlaybackState state = controller == null ? null : controller.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                continue;
            }
            long updateTime = state.getLastPositionUpdateTime();
            if (selected == null || updateTime > selectedUpdateTime) {
                selected = controller;
                selectedUpdateTime = updateTime;
            }
        }
        return selected;
    }

    private MediaController findControllerForPackage(List<MediaController> controllers,
                                                     String packageName,
                                                     boolean requirePlaying) {
        for (MediaController controller : controllers) {
            if (TextUtils.equals(controller.getPackageName(), packageName)
                    && (!requirePlaying || isPlaying(controller))) {
                return controller;
            }
        }
        return null;
    }

    private MediaNotificationListenerService.MediaNotificationSnapshot findBestNotification(
            List<MediaNotificationListenerService.MediaNotificationSnapshot> snapshots,
            String preferredPackage) {
        if (snapshots.isEmpty()) {
            return null;
        }
        if (!TextUtils.isEmpty(preferredPackage)) {
            for (MediaNotificationListenerService.MediaNotificationSnapshot snapshot : snapshots) {
                if (TextUtils.equals(snapshot.packageName, preferredPackage)) {
                    return snapshot;
                }
            }
        }
        for (MediaNotificationListenerService.MediaNotificationSnapshot snapshot : snapshots) {
            if (snapshot.sessionToken != null) {
                return snapshot;
            }
        }
        return snapshots.get(0);
    }

    private List<String> collectSourcePackages(
            List<MediaController> controllers,
            List<MediaNotificationListenerService.MediaNotificationSnapshot> notifications) {
        Set<String> packages = new LinkedHashSet<>();
        for (MediaNotificationListenerService.MediaNotificationSnapshot notification
                : notifications) {
            if (notification != null && !TextUtils.isEmpty(notification.packageName)) {
                packages.add(notification.packageName);
            }
        }
        for (MediaController controller : controllers) {
            if (controller != null && !TextUtils.isEmpty(controller.getPackageName())) {
                packages.add(controller.getPackageName());
            }
        }
        return new ArrayList<>(packages);
    }

    private void setActiveController(MediaController controller) {
        MediaController previous = activeController;
        boolean changed = !controlsSameSession(previous, controller);
        if (!changed) {
            notifyStateChanged(false);
            return;
        }
        if (previous != null) {
            try {
                previous.unregisterCallback(controllerCallback);
            } catch (RuntimeException ignored) {
            }
        }
        activeController = controller;
        if (controller != null) {
            try {
                controller.registerCallback(controllerCallback, callbackHandler);
            } catch (RuntimeException e) {
                Log.w(TAG, "Register media callback failed: " + e.getClass().getSimpleName());
            }
        }
        notifyStateChanged(true);
    }

    private void notifyStateChanged(boolean sessionChanged) {
        listener.onMediaStateChanged(
                activeController, activeNotification, permissionDenied, sessionChanged);
    }

    private static boolean controlsSameSession(MediaController first, MediaController second) {
        if (first == second) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        MediaSession.Token firstToken = first.getSessionToken();
        MediaSession.Token secondToken = second.getSessionToken();
        return firstToken != null && firstToken.equals(secondToken);
    }

    private static boolean isPlaying(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }
}
