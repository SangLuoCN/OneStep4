package com.sangluo.onestep.ui.media;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.TypedValue;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.sangluo.onestep.MediaNotificationListenerService;
import com.sangluo.onestep.R;
import com.sangluo.onestep.feature.media.MediaSessionCoordinator;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.ui.widget.AspectRatioImageView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class MediaPlaybackPanel implements AutoCloseable {
    public interface Callbacks {
        boolean deferWindowSwitchUiWork(int flags);
        ShellCommandResult runPrivilegedCommand(
                String command, String description, boolean logOutput);
        String shellQuote(String value);
        void openComponentApp(String packageName);
        FrameLayout rootContainer();
        int topMediaAreaHeight();
        int topMediaPlayerTopMargin();
        int dp(float value);
    }

    private static final int DEFERRED_MEDIA_SESSION_REFRESH = 1;
    private static final int DEFERRED_MEDIA_UI_REFRESH = 1 << 1;
    private static final int DEFERRED_PLAYLIST_REFRESH = 1 << 2;
    private static final int TOP_COMPONENT_BACKGROUND_COLOR = 0xc0000000;
    private static final int MEDIA_SOURCE_LABEL_MAX_CHARACTERS = 5;
    private static final String TAG = "OneStep40";

    private final Activity activity;
    private final Handler mediaHandler;
    private final ExecutorService mediaRootExecutor;
    private final Callbacks callbacks;
    private boolean mediaSessionPermissionDenied;
    private boolean mediaNotificationAccessRequested;
    private boolean mediaMonitoringActive;
    private MediaNotificationListenerService.MediaNotificationSnapshot activeMediaNotification;
    private MediaSessionCoordinator mediaSessionCoordinator;
    private MediaController activeMediaController;
    private ImageView mediaArtworkView;
    private TextView mediaTitleView;
    private TextView mediaArtistView;
    private ImageView mediaFavoriteView;
    private TextView mediaPlayPauseView;
    private TextView mediaElapsedTimeView;
    private TextView mediaDurationView;
    private View mediaProgressFill;
    private ImageView mediaSourceAppIconView;
    private PopupWindow mediaSourcePopup;
    private FrameLayout playlistPanel;
    private ScrollView playlistScrollView;
    private LinearLayout playlistContent;
    private String playlistSelectionKey = "";
    private boolean playlistAutoScrollPending;
    private int mediaQueueNavigationGeneration;

    public MediaPlaybackPanel(Activity activity, Handler mediaHandler,
                              ExecutorService mediaRootExecutor, Callbacks callbacks) {
        this.activity = activity;
        this.mediaHandler = mediaHandler;
        this.mediaRootExecutor = mediaRootExecutor;
        this.callbacks = callbacks;
    }

    public View createView() {
        LinearLayout player = new LinearLayout(activity);
        player.setOrientation(LinearLayout.HORIZONTAL);
        player.setGravity(Gravity.BOTTOM);
        player.setPadding(dp(10), dp(8), dp(10), dp(8));
        player.setBackground(makeRoundedBackground(
                TOP_COMPONENT_BACKGROUND_COLOR, dp(12)));
        player.setContentDescription("音乐播放器");
        player.setOnClickListener(v -> callbacks.openComponentApp(getCurrentMediaPackageName()));

        mediaArtworkView = new ImageView(activity);
        mediaArtworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        mediaArtworkView.setBackground(makePanelBackground(0x4a6a966f, 0x24ffffff, dp(7)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaArtworkView.setClipToOutline(true);
        }
        mediaArtworkView.setImageResource(R.drawable.music_artwork_default);
        LinearLayout.LayoutParams artworkLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        artworkLp.gravity = Gravity.BOTTOM;
        player.addView(mediaArtworkView, artworkLp);

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        infoLp.leftMargin = dp(11);
        infoLp.rightMargin = dp(6);
        player.addView(info, infoLp);

        LinearLayout textRow = new LinearLayout(activity);
        textRow.setGravity(Gravity.CENTER_VERTICAL);
        info.addView(textRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

        mediaTitleView = new TextView(activity);
        mediaTitleView.setText("未播放");
        mediaTitleView.setSingleLine(true);
        mediaTitleView.setEllipsize(TextUtils.TruncateAt.END);
        mediaTitleView.setGravity(Gravity.CENTER_VERTICAL);
        mediaTitleView.setTextColor(0xe6ffffff);
        mediaTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(mediaTitleView, 13);
        textRow.addView(mediaTitleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        mediaArtistView = new TextView(activity);
        mediaArtistView.setText("One Step");
        mediaArtistView.setSingleLine(true);
        mediaArtistView.setEllipsize(TextUtils.TruncateAt.END);
        mediaArtistView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        mediaArtistView.setTextColor(0x99ffffff);
        setDpTextSize(mediaArtistView, 9);
        textRow.addView(mediaArtistView, new LinearLayout.LayoutParams(dp(104),
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout progressRow = new LinearLayout(activity);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams progressRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(15));
        progressRowLp.topMargin = dp(3);
        info.addView(progressRow, progressRowLp);

        mediaElapsedTimeView = createMediaProgressTimeView(Gravity.START);
        progressRow.addView(mediaElapsedTimeView, new LinearLayout.LayoutParams(dp(34),
                ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout progress = new FrameLayout(activity);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(0, dp(4), 1f);
        progressLp.leftMargin = dp(2);
        progressLp.rightMargin = dp(2);
        progressRow.addView(progress, progressLp);

        View progressTrack = new View(activity);
        progressTrack.setBackground(makePanelBackground(0x33ffffff, 0x00ffffff, dp(2)));
        progress.addView(progressTrack, matchFrame());

        mediaProgressFill = new View(activity);
        mediaProgressFill.setBackground(makePanelBackground(0xff2f80ff, 0x002f80ff, dp(2)));
        progress.addView(mediaProgressFill, new FrameLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        mediaDurationView = createMediaProgressTimeView(Gravity.END);
        progressRow.addView(mediaDurationView, new LinearLayout.LayoutParams(dp(34),
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout controlsRow = new LinearLayout(activity);
        controlsRow.setGravity(Gravity.BOTTOM);
        controlsRow.setWeightSum(4f);
        info.addView(controlsRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mediaFavoriteView = new ImageView(activity);
        mediaFavoriteView.setImageResource(R.drawable.music_favorite_outline);
        mediaFavoriteView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mediaFavoriteView.setPadding(dp(3), dp(3), dp(3), dp(3));
        mediaFavoriteView.setContentDescription("收藏");
        mediaFavoriteView.setOnClickListener(v -> dispatchMediaFavorite());
        controlsRow.addView(mediaFavoriteView, createMusicControlLayoutParams());
        addMusicControlSpacer(controlsRow);

        TextView previous = createMusicControl("◀", "上一曲");
        previous.setOnClickListener(v -> dispatchMediaPrevious());
        controlsRow.addView(previous, createMusicControlLayoutParams());
        addMusicControlSpacer(controlsRow);

        mediaPlayPauseView = createMusicControl("▶", "播放/暂停");
        mediaPlayPauseView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        mediaPlayPauseView.setOnClickListener(v -> dispatchMediaPlayPause());
        controlsRow.addView(mediaPlayPauseView, createMusicControlLayoutParams());
        addMusicControlSpacer(controlsRow);

        TextView next = createMusicControl("▶", "下一曲");
        next.setOnClickListener(v -> dispatchMediaNext());
        controlsRow.addView(next, createMusicControlLayoutParams());
        addMusicControlSpacer(controlsRow);

        mediaSourceAppIconView = new ImageView(activity);
        mediaSourceAppIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        mediaSourceAppIconView.setPadding(0, dp(5), 0, 0);
        mediaSourceAppIconView.setVisibility(View.INVISIBLE);
        mediaSourceAppIconView.setOnClickListener(v -> toggleMediaSourcePopup());
        controlsRow.addView(mediaSourceAppIconView, createMusicControlLayoutParams());
        updateMediaSourceAppIcon(getCurrentMediaPackageName());
        return player;
    }

    private TextView createMediaProgressTimeView(int gravity) {
        TextView view = new TextView(activity);
        view.setText("0:00");
        view.setSingleLine(true);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        view.setTextColor(0xa6ffffff);
        setDpTextSize(view, 8);
        return view;
    }

    private LinearLayout.LayoutParams createMusicControlLayoutParams() {
        return new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private void addMusicControlSpacer(LinearLayout controlsRow) {
        controlsRow.addView(new View(activity), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    }

    private TextView createMusicControl(String symbol, String description) {
        TextView control = new TextView(activity);
        control.setText(symbol);
        control.setGravity(Gravity.CENTER);
        control.setTextColor(0xe6ffffff);
        control.setTypeface(Typeface.DEFAULT_BOLD);
        control.setContentDescription(description);
        setDpTextSize(control, 13);
        return control;
    }

    public void start() {
        mediaSessionCoordinator = new MediaSessionCoordinator(
                activity, mediaHandler, new MediaSessionCoordinator.Listener() {
            @Override
            public void onMediaStateChanged(MediaController controller,
                                            MediaNotificationListenerService
                                                    .MediaNotificationSnapshot notification,
                                            boolean permissionDenied,
                                            boolean sessionChanged) {
                activeMediaController = controller;
                activeMediaNotification = notification;
                mediaSessionPermissionDenied = permissionDenied;
                if (sessionChanged) {
                    mediaQueueNavigationGeneration++;
                }
                updateMediaUi();
            }

            @Override
            public void onQueueChanged() {
                updatePlaylistPanel();
            }

            @Override
            public void onSessionAccessDenied() {
                ensureMediaNotificationAccess();
            }
        });
        ensureMediaNotificationAccess();
        mediaMonitoringActive = true;
        refreshActiveMediaController();
        updateMediaUi();
    }

    public void refreshActiveMediaController() {
        if (!mediaMonitoringActive) {
            return;
        }
        if (deferWindowSwitchUiWork(DEFERRED_MEDIA_SESSION_REFRESH)) {
            return;
        }
        if (mediaSessionCoordinator != null) {
            mediaSessionCoordinator.refresh();
        }
    }

    private void ensureMediaNotificationAccess() {
        ComponentName componentName = MediaNotificationListenerService.getComponentName(activity);
        if (isNotificationListenerEnabled(componentName)) {
            try {
                NotificationListenerService.requestRebind(componentName);
            } catch (RuntimeException ignored) {
            }
            return;
        }
        if (mediaNotificationAccessRequested) {
            return;
        }
        mediaNotificationAccessRequested = true;
        mediaRootExecutor.execute(() -> {
            boolean enabled = enableNotificationListenerWithShell(componentName);
            mediaHandler.post(() -> {
                if (enabled || isNotificationListenerEnabled(componentName)) {
                    try {
                        NotificationListenerService.requestRebind(componentName);
                    } catch (RuntimeException ignored) {
                    }
                    mediaNotificationAccessRequested = false;
                    mediaSessionPermissionDenied = false;
                    mediaHandler.postDelayed(this::refreshActiveMediaController, 500);
                    mediaHandler.postDelayed(this::refreshActiveMediaController, 1500);
                } else {
                    showMediaUnavailable("需要通知访问");
                    mediaHandler.postDelayed(() -> {
                        mediaNotificationAccessRequested = false;
                        refreshActiveMediaController();
                    }, 5000);
                }
            });
        });
    }

    private boolean isNotificationListenerEnabled(ComponentName componentName) {
        String enabledListeners = Settings.Secure.getString(activity.getContentResolver(),
                "enabled_notification_listeners");
        return isComponentListed(enabledListeners, componentName.flattenToString())
                || isComponentListed(enabledListeners, componentName.flattenToShortString());
    }

    private boolean isComponentListed(String enabledListeners, String component) {
        if (TextUtils.isEmpty(enabledListeners) || TextUtils.isEmpty(component)) {
            return false;
        }
        String normalized = ":" + enabledListeners + ":";
        return normalized.contains(":" + component + ":");
    }

    private boolean enableNotificationListenerWithShell(ComponentName componentName) {
        String component = componentName.flattenToString();
        ShellCommandResult allowResult = runMainPrivilegedCommand(
                "cmd notification allow_listener " + mainShellQuote(component),
                "allow notification listener", true);
        if (allowResult.exitCode == 0) {
            return true;
        }

        ShellCommandResult currentResult = runMainPrivilegedCommand(
                "settings get secure enabled_notification_listeners",
                "read notification listeners", false);
        String current = currentResult.output == null ? "" : currentResult.output.trim();
        if (TextUtils.equals(current, "null")) {
            current = "";
        }
        if (isComponentListed(current, component)
                || isComponentListed(current, componentName.flattenToShortString())) {
            return true;
        }

        String next = TextUtils.isEmpty(current) ? component : current + ":" + component;
        ShellCommandResult putResult = runMainPrivilegedCommand(
                "settings put secure enabled_notification_listeners " + mainShellQuote(next),
                "write notification listeners", true);
        return putResult.exitCode == 0;
    }

    private boolean isControllerPlaying(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return state != null && state.getState() == PlaybackState.STATE_PLAYING;
    }

    public void updateMediaUi() {
        if (deferWindowSwitchUiWork(DEFERRED_MEDIA_UI_REFRESH)) {
            return;
        }
        if (mediaTitleView == null) {
            return;
        }
        if (activeMediaController == null) {
            if (activeMediaNotification != null) {
                updateMediaUiFromNotification(activeMediaNotification);
                updatePlaylistPanel();
                return;
            }
            showMediaUnavailable(mediaSessionPermissionDenied ? "需要媒体/通知权限" : "未播放");
            updatePlaylistPanel();
            return;
        }

        MediaMetadata metadata = activeMediaController.getMetadata();
        MediaNotificationListenerService.MediaNotificationSnapshot notification =
                activeMediaNotification != null
                        && TextUtils.equals(activeMediaNotification.packageName,
                        activeMediaController.getPackageName())
                        ? activeMediaNotification : null;
        String title = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                notification == null ? "" : notification.title,
                getAppLabel(activeMediaController.getPackageName()));
        String artist = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                notification == null ? "" : notification.text,
                notification == null ? "" : notification.subText,
                activeMediaController.getPackageName());

        mediaTitleView.setText(title);
        mediaArtistView.setText(artist);
        Bitmap artwork = firstNonNullBitmap(
                getMetadataBitmap(metadata, MediaMetadata.METADATA_KEY_ART),
                getMetadataBitmap(metadata, MediaMetadata.METADATA_KEY_ALBUM_ART),
                getMetadataBitmap(metadata, MediaMetadata.METADATA_KEY_DISPLAY_ICON),
                notification == null ? null : notification.artwork);
        if (artwork != null) {
            mediaArtworkView.setImageBitmap(artwork);
        } else {
            mediaArtworkView.setImageResource(R.drawable.music_artwork_default);
        }

        mediaPlayPauseView.setText(isControllerPlaying(activeMediaController) ? "Ⅱ" : "▶");
        mediaPlayPauseView.setContentDescription(
                isControllerPlaying(activeMediaController) ? "暂停" : "播放");
        updateMediaSourceAppIcon(activeMediaController.getPackageName());
        updateMediaFavoriteControl(activeMediaController);
        updateProgress();
        updatePlaylistPanel();
    }

    private void updateMediaUiFromNotification(
            MediaNotificationListenerService.MediaNotificationSnapshot notification) {
        mediaTitleView.setText(firstNonEmpty(notification.title,
                getAppLabel(notification.packageName)));
        mediaArtistView.setText(firstNonEmpty(notification.text,
                notification.subText, notification.packageName));
        if (notification.artwork != null) {
            mediaArtworkView.setImageBitmap(notification.artwork);
        } else {
            mediaArtworkView.setImageResource(R.drawable.music_artwork_default);
        }
        mediaPlayPauseView.setText(notification.likelyPlaying ? "Ⅱ" : "▶");
        mediaPlayPauseView.setContentDescription(notification.likelyPlaying ? "暂停" : "播放");
        updateMediaSourceAppIcon(notification.packageName);
        updateMediaFavoriteControl(null);
        setMediaProgressFillWidth(0);
        setMediaProgressTimes(0L, 0L);
    }

    private void showMediaUnavailable(String text) {
        if (mediaTitleView == null) {
            return;
        }
        mediaTitleView.setText(text);
        mediaArtistView.setText("One Step");
        mediaArtworkView.setImageResource(R.drawable.music_artwork_default);
        mediaPlayPauseView.setText("▶");
        updateMediaSourceAppIcon(null);
        updateMediaFavoriteControl(null);
        setMediaProgressFillWidth(0);
        setMediaProgressTimes(0L, 0L);
    }

    private String getCurrentMediaPackageName() {
        if (activeMediaController != null) {
            return activeMediaController.getPackageName();
        }
        return activeMediaNotification == null ? "" : activeMediaNotification.packageName;
    }

    private void updateMediaSourceAppIcon(String packageName) {
        if (mediaSourceAppIconView == null) {
            return;
        }
        if (TextUtils.isEmpty(packageName)) {
            mediaSourceAppIconView.setImageResource(R.drawable.music_source_default);
            mediaSourceAppIconView.setContentDescription("查看音频来源");
            mediaSourceAppIconView.setVisibility(View.VISIBLE);
            return;
        }
        Drawable icon = loadMediaSourceIcon(packageName);
        if (icon == null) {
            mediaSourceAppIconView.setImageResource(R.drawable.music_source_default);
        } else {
            mediaSourceAppIconView.setImageDrawable(icon);
        }
        mediaSourceAppIconView.setContentDescription(
                "切换音频来源，当前：" + getAppLabel(packageName));
        mediaSourceAppIconView.setVisibility(View.VISIBLE);
    }

    private Drawable loadMediaSourceIcon(String packageName) {
        try {
            return activity.getPackageManager().getApplicationIcon(packageName);
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "Load media source icon failed: package=" + packageName
                    + ", error=" + e.getClass().getSimpleName());
            return null;
        }
    }

    private void toggleMediaSourcePopup() {
        if (mediaSourcePopup != null && mediaSourcePopup.isShowing()) {
            mediaSourcePopup.dismiss();
            return;
        }
        showMediaSourcePopup();
    }

    private void showMediaSourcePopup() {
        if (mediaSourceAppIconView == null || mediaSourceAppIconView.getWindowToken() == null) {
            return;
        }
        hidePlaylistPanel();
        List<String> sourcePackages = mediaSessionCoordinator == null
                ? null : mediaSessionCoordinator.getAvailableSourcePackages();

        LinearLayout sourceContent = new LinearLayout(activity);
        sourceContent.setOrientation(LinearLayout.VERTICAL);
        sourceContent.setPadding(dp(4), dp(4), dp(4), dp(4));
        sourceContent.setBackground(makePanelBackground(0xf21b211d, 0x40ffffff, dp(10)));

        int rowCount;
        if (sourcePackages == null || sourcePackages.isEmpty()) {
            TextView emptyView = createMediaSourceLabel("暂无音乐来源", false);
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setTextColor(0x99ffffff);
            sourceContent.addView(emptyView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            rowCount = 1;
        } else {
            String currentPackage = getCurrentMediaPackageName();
            for (String packageName : sourcePackages) {
                sourceContent.addView(createMediaSourceRow(
                        packageName, TextUtils.equals(packageName, currentPackage)),
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
            }
            rowCount = sourcePackages.size();
        }

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scrollView.addView(sourceContent, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int popupWidth = calculateMediaSourcePopupWidth(sourcePackages);
        int popupHeight = dp(Math.min(rowCount, 5) * 44 + 8);
        mediaSourcePopup = new PopupWindow(scrollView, popupWidth, popupHeight, true);
        mediaSourcePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mediaSourcePopup.setOutsideTouchable(true);
        mediaSourcePopup.setElevation(dp(8));
        mediaSourcePopup.setOnDismissListener(() -> mediaSourcePopup = null);
        mediaSourcePopup.showAsDropDown(
                mediaSourceAppIconView, 0, dp(10), Gravity.END);
    }

    private int calculateMediaSourcePopupWidth(List<String> sourcePackages) {
        int contentWidth = 0;
        if (sourcePackages == null || sourcePackages.isEmpty()) {
            TextView emptyLabel = createMediaSourceLabel("暂无音乐来源", false);
            emptyLabel.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            contentWidth = emptyLabel.getMeasuredWidth() + dp(24);
        } else {
            String currentPackage = getCurrentMediaPackageName();
            for (String packageName : sourcePackages) {
                TextView label = createMediaSourceLabel(
                        formatMediaSourceLabel(getAppLabel(packageName)),
                        TextUtils.equals(packageName, currentPackage));
                label.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                contentWidth = Math.max(contentWidth, label.getMeasuredWidth() + dp(62));
            }
        }
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int maximumWidth = Math.max(dp(1), Math.min(dp(280), screenWidth - dp(16)));
        return Math.min(Math.max(dp(128), contentWidth), maximumWidth);
    }

    private View createMediaSourceRow(String packageName, boolean selected) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int horizontalPadding = dp(8);
        int verticalPadding = dp(selected ? 1 : 4);
        row.setPadding(horizontalPadding, verticalPadding,
                horizontalPadding, verticalPadding);
        if (selected) {
            row.setBackground(makeRoundedBackground(0x52ffffff, dp(7)));
        }

        ImageView iconView = new ImageView(activity);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Drawable icon = loadMediaSourceIcon(packageName);
        if (icon == null) {
            iconView.setImageResource(R.drawable.music_source_default);
        } else {
            iconView.setImageDrawable(icon);
        }
        row.addView(iconView, new LinearLayout.LayoutParams(dp(28), dp(28)));

        String appLabel = getAppLabel(packageName);
        TextView labelView = createMediaSourceLabel(formatMediaSourceLabel(appLabel), selected);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        labelLp.leftMargin = dp(10);
        row.addView(labelView, labelLp);
        row.setContentDescription(selected ? appLabel + "，当前来源" : appLabel);
        row.setOnClickListener(v -> selectMediaSource(packageName));
        return row;
    }

    private String formatMediaSourceLabel(String appLabel) {
        if (TextUtils.isEmpty(appLabel)) {
            return "";
        }
        if (appLabel.length() <= MEDIA_SOURCE_LABEL_MAX_CHARACTERS) {
            return appLabel;
        }
        return appLabel.substring(0, MEDIA_SOURCE_LABEL_MAX_CHARACTERS) + "...";
    }

    private TextView createMediaSourceLabel(String text, boolean selected) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        label.setTextColor(0xe6ffffff);
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        setDpTextSize(label, 12);
        return label;
    }

    private void selectMediaSource(String packageName) {
        if (mediaSourcePopup != null) {
            mediaSourcePopup.dismiss();
        }
        if (mediaSessionCoordinator != null
                && mediaSessionCoordinator.selectSource(packageName)) {
            callbacks.openComponentApp(packageName);
        }
    }

    public void updateProgress() {
        if (activeMediaController == null || mediaProgressFill == null) {
            return;
        }
        MediaMetadata metadata = activeMediaController.getMetadata();
        PlaybackState state = activeMediaController.getPlaybackState();
        long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long position = state == null ? 0 : Math.max(0, state.getPosition());
        if (duration > 0 && state != null && state.getState() == PlaybackState.STATE_PLAYING
                && state.getLastPositionUpdateTime() > 0L) {
            long elapsed = SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
            position += Math.round(elapsed * state.getPlaybackSpeed());
        }
        if (duration > 0) {
            position = Math.min(position, duration);
            setMediaProgressTimes(position, duration);
            ViewParent parent = mediaProgressFill.getParent();
            if (!(parent instanceof View) || ((View) parent).getWidth() <= 0) {
                mediaProgressFill.post(this::updateProgress);
                return;
            }
            int parentWidth = ((View) parent).getWidth();
            int fillWidth = Math.round(parentWidth * (position / (float) duration));
            setMediaProgressFillWidth(fillWidth);
        } else {
            setMediaProgressFillWidth(0);
            setMediaProgressTimes(position, 0L);
        }
    }

    private void setMediaProgressTimes(long positionMillis, long durationMillis) {
        if (mediaElapsedTimeView != null) {
            setTextIfChanged(mediaElapsedTimeView, formatMediaTime(positionMillis));
        }
        if (mediaDurationView != null) {
            setTextIfChanged(mediaDurationView, formatMediaTime(durationMillis));
        }
    }

    private void setTextIfChanged(TextView view, String text) {
        if (view != null && !TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
    }

    private String formatMediaTime(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void setMediaProgressFillWidth(int width) {
        ViewGroup.LayoutParams lp = mediaProgressFill.getLayoutParams();
        int targetWidth = Math.max(0, width);
        if (lp.width == targetWidth) {
            return;
        }
        lp.width = targetWidth;
        mediaProgressFill.setLayoutParams(lp);
    }

    private void updateMediaFavoriteControl(MediaController controller) {
        if (mediaFavoriteView == null) {
            return;
        }
        PlaybackState state = controller == null ? null : controller.getPlaybackState();
        PlaybackState.CustomAction customAction = findMediaFavoriteAction(state);
        boolean ratingSupported = state != null
                && (state.getActions() & PlaybackState.ACTION_SET_RATING) != 0;
        boolean favorite = isCurrentMediaFavorite(controller);
        if (!favorite && customAction != null) {
            favorite = textContainsAny(customAction.getAction(), "unlike", "unfavorite",
                    "remove_favorite", "removefavorite", "取消收藏")
                    || textContainsAny(String.valueOf(customAction.getName()),
                    "取消收藏", "取消喜欢", "unlike", "unfavorite");
        }
        boolean enabled = customAction != null || ratingSupported;
        mediaFavoriteView.setImageResource(favorite
                ? R.drawable.music_favorite_selected
                : R.drawable.music_favorite_outline);
        mediaFavoriteView.setContentDescription(favorite ? "取消收藏" : "收藏");
        mediaFavoriteView.setEnabled(enabled);
        mediaFavoriteView.setAlpha(enabled ? 1f : 0.45f);
    }

    private boolean isCurrentMediaFavorite(MediaController controller) {
        MediaMetadata metadata = controller == null ? null : controller.getMetadata();
        Rating rating = metadata == null ? null
                : metadata.getRating(MediaMetadata.METADATA_KEY_USER_RATING);
        return rating != null && rating.getRatingStyle() == Rating.RATING_HEART
                && rating.isRated() && rating.hasHeart();
    }

    private PlaybackState.CustomAction findMediaFavoriteAction(PlaybackState state) {
        if (state == null || state.getCustomActions() == null) {
            return null;
        }
        for (PlaybackState.CustomAction action : state.getCustomActions()) {
            if (action == null) {
                continue;
            }
            String actionId = action.getAction();
            String actionName = String.valueOf(action.getName());
            if (textContainsAny(actionId, "favorite", "favourite", "collect", "like", "heart",
                    "收藏", "喜欢")
                    || textContainsAny(actionName, "favorite", "favourite", "collect", "like",
                    "heart", "收藏", "喜欢")) {
                return action;
            }
        }
        return null;
    }

    private void dispatchMediaFavorite() {
        MediaController controller = activeMediaController;
        if (controller == null) {
            refreshActiveMediaController();
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        PlaybackState.CustomAction customAction = findMediaFavoriteAction(state);
        try {
            if (customAction != null) {
                controller.getTransportControls().sendCustomAction(
                        customAction.getAction(), customAction.getExtras());
            } else if (state != null
                    && (state.getActions() & PlaybackState.ACTION_SET_RATING) != 0) {
                controller.getTransportControls().setRating(
                        Rating.newHeartRating(!isCurrentMediaFavorite(controller)));
            } else {
                return;
            }
            mediaHandler.postDelayed(this::updateMediaUi, 250);
        } catch (RuntimeException e) {
            Log.w(TAG, "Favorite media action failed: " + e.getClass().getSimpleName());
        }
    }

    private boolean textContainsAny(String text, String... values) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.US);
        for (String value : values) {
            if (!TextUtils.isEmpty(value)
                    && normalized.contains(value.toLowerCase(Locale.US))) {
                return true;
            }
        }
        return false;
    }

    private void dispatchMediaPrevious() {
        if (activeMediaController != null) {
            activeMediaController.getTransportControls().skipToPrevious();
            refreshActiveMediaController();
        }
    }

    private void dispatchMediaPlayPause() {
        if (activeMediaController == null) {
            refreshActiveMediaController();
            return;
        }
        if (isControllerPlaying(activeMediaController)) {
            activeMediaController.getTransportControls().pause();
        } else {
            activeMediaController.getTransportControls().play();
        }
        mediaHandler.postDelayed(this::updateMediaUi, 150);
    }

    private void dispatchMediaNext() {
        if (activeMediaController != null) {
            activeMediaController.getTransportControls().skipToNext();
            refreshActiveMediaController();
        }
    }

    private void togglePlaylistPanel() {
        if (playlistPanel != null && playlistPanel.getParent() != null) {
            ((ViewGroup) playlistPanel.getParent()).removeView(playlistPanel);
            return;
        }
        showPlaylistPanel();
    }

    public boolean hidePlaylistPanel() {
        if (playlistPanel == null || playlistPanel.getParent() == null) {
            return false;
        }
        ((ViewGroup) playlistPanel.getParent()).removeView(playlistPanel);
        return true;
    }

    private void showPlaylistPanel() {
        if (rootContainer() == null) {
            return;
        }
        if (mediaSourcePopup != null) {
            mediaSourcePopup.dismiss();
        }
        if (playlistPanel == null) {
            playlistPanel = new FrameLayout(activity);
            playlistPanel.setBackground(makePanelBackground(0xdd182018, 0x40ffffff, dp(6)));
            playlistPanel.setPadding(dp(10), dp(8), dp(10), dp(8));

            playlistScrollView = new ScrollView(activity);
            playlistScrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
            playlistContent = new LinearLayout(activity);
            playlistContent.setOrientation(LinearLayout.VERTICAL);
            playlistScrollView.addView(playlistContent, new ScrollView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            playlistPanel.addView(playlistScrollView, matchFrame());
        }
        playlistAutoScrollPending = true;
        updatePlaylistPanel();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(336), dp(220),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = getTopMediaAreaHeight() + dp(4);
        rootContainer().addView(playlistPanel, lp);
        playlistPanel.bringToFront();
    }

    public void updatePlaylistPanel() {
        if (deferWindowSwitchUiWork(DEFERRED_PLAYLIST_REFRESH)) {
            return;
        }
        if (playlistContent == null || playlistPanel == null) {
            return;
        }
        playlistContent.removeAllViews();

        String queueTitle = activeMediaController == null ? ""
                : String.valueOf(activeMediaController.getQueueTitle());
        TextView header = createPlaylistText(firstNonEmpty(queueTitle, "当前播放列表"),
                12, 0xffffffff, true);
        playlistContent.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(26)));

        List<MediaSession.QueueItem> queue = activeMediaController == null
                ? null : activeMediaController.getQueue();
        if (queue == null || queue.isEmpty()) {
            String currentTrack = getCurrentMediaLine();
            if (!TextUtils.isEmpty(currentTrack)) {
                TextView currentRow = createPlaylistText("▶ " + currentTrack, 10,
                        0xffffffff, true);
                currentRow.setPadding(dp(8), 0, dp(8), 0);
                currentRow.setBackground(makeRoundedBackground(0x553f80ff, dp(5)));
                playlistContent.addView(currentRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
            }
            playlistContent.addView(createPlaylistText("播放器未向系统公开完整列表", 10,
                    0xbfffffff, false), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
            return;
        }

        int activeIndex = findPublishedMediaQueueIndex(activeMediaController, queue);
        if (activeIndex < 0) {
            header.setText("当前播放");
            String currentTrack = getCurrentMediaLine();
            TextView selectedRow = null;
            if (!TextUtils.isEmpty(currentTrack)) {
                selectedRow = createPlaylistText("▶ " + currentTrack, 10,
                        0xffffffff, true);
                selectedRow.setPadding(dp(8), 0, dp(8), 0);
                selectedRow.setBackground(makeRoundedBackground(0x553f80ff, dp(5)));
                LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
                currentLp.topMargin = dp(1);
                playlistContent.addView(selectedRow, currentLp);
            }
            playlistContent.addView(createPlaylistText("播放器未同步当前列表", 10,
                    0xbfffffff, false), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
            scrollPlaylistToSelection(selectedRow, "current:" + currentTrack);
            return;
        }
        TextView selectedRow = null;
        for (int index = 0; index < queue.size(); index++) {
            MediaSession.QueueItem item = queue.get(index);
            String line = item.getDescription() == null ? "未知曲目"
                    : firstNonEmpty(String.valueOf(item.getDescription().getTitle()),
                    String.valueOf(item.getDescription().getSubtitle()));
            boolean active = index == activeIndex;
            TextView row = createPlaylistText((active ? "▶ " : "   ") + line, 10,
                    active ? 0xffffffff : 0xcfffffff, active);
            row.setPadding(dp(8), 0, dp(8), 0);
            row.setBackground(active
                    ? makeRoundedBackground(0x553f80ff, dp(5))
                    : null);
            final int targetIndex = index;
            row.setOnClickListener(v -> dispatchMediaQueueItem(queue, item, targetIndex));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
            rowLp.topMargin = dp(1);
            playlistContent.addView(row, rowLp);
            if (active) {
                selectedRow = row;
            }
        }
        String selectionKey = "queue:" + activeIndex + ":"
                + queue.get(activeIndex).getQueueId();
        scrollPlaylistToSelection(selectedRow, selectionKey);
    }

    private void scrollPlaylistToSelection(TextView selectedRow, String selectionKey) {
        boolean shouldScroll = playlistAutoScrollPending
                || !TextUtils.equals(playlistSelectionKey, selectionKey);
        playlistAutoScrollPending = false;
        playlistSelectionKey = selectionKey;
        if (!shouldScroll || selectedRow == null || playlistScrollView == null) {
            return;
        }
        TextView target = selectedRow;
        playlistScrollView.post(() -> playlistScrollView.smoothScrollTo(0,
                Math.max(0, target.getTop() - dp(26))));
    }

    private int findCurrentMediaQueueIndex(MediaController controller,
                                           List<MediaSession.QueueItem> queue) {
        if (controller == null || queue == null || queue.isEmpty()) {
            return -1;
        }
        PlaybackState state = controller.getPlaybackState();
        long activeQueueId = state == null
                ? MediaSession.QueueItem.UNKNOWN_ID : state.getActiveQueueItemId();
        if (activeQueueId != MediaSession.QueueItem.UNKNOWN_ID) {
            for (int index = 0; index < queue.size(); index++) {
                if (queue.get(index).getQueueId() == activeQueueId) {
                    return index;
                }
            }
        }

        MediaMetadata metadata = controller.getMetadata();
        String mediaId = metadata == null ? ""
                : firstNonEmpty(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
        String title = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE));
        String artist = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE));
        int titleMatch = -1;
        for (int index = 0; index < queue.size(); index++) {
            MediaDescription description = queue.get(index).getDescription();
            if (description == null) {
                continue;
            }
            if (!TextUtils.isEmpty(mediaId)
                    && TextUtils.equals(mediaId, description.getMediaId())) {
                return index;
            }
            String itemTitle = description.getTitle() == null
                    ? "" : String.valueOf(description.getTitle());
            if (!TextUtils.equals(title, itemTitle)) {
                continue;
            }
            if (titleMatch < 0) {
                titleMatch = index;
            }
            String itemArtist = description.getSubtitle() == null
                    ? "" : String.valueOf(description.getSubtitle());
            if (!TextUtils.isEmpty(artist) && TextUtils.equals(artist, itemArtist)) {
                return index;
            }
        }
        return titleMatch;
    }

    private int findPublishedMediaQueueIndex(MediaController controller,
                                             List<MediaSession.QueueItem> queue) {
        if (controller == null || queue == null || queue.isEmpty()) {
            return -1;
        }
        PlaybackState state = controller.getPlaybackState();
        long activeQueueId = state == null
                ? MediaSession.QueueItem.UNKNOWN_ID : state.getActiveQueueItemId();
        if (activeQueueId == MediaSession.QueueItem.UNKNOWN_ID) {
            return -1;
        }
        for (int index = 0; index < queue.size(); index++) {
            if (queue.get(index).getQueueId() == activeQueueId) {
                return index;
            }
        }
        return -1;
    }

    private void dispatchMediaQueueItem(List<MediaSession.QueueItem> queue,
                                        MediaSession.QueueItem item, int targetIndex) {
        MediaController controller = activeMediaController;
        if (controller == null || item == null) {
            refreshActiveMediaController();
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        long actions = state == null ? 0L : state.getActions();
        MediaDescription description = item.getDescription();
        MediaController.TransportControls controls = controller.getTransportControls();

        if (item.getQueueId() != MediaSession.QueueItem.UNKNOWN_ID
                && (actions & PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM) != 0L) {
            controls.skipToQueueItem(item.getQueueId());
            scheduleMediaQueueSelectionRefresh();
            return;
        }
        if (description != null && !TextUtils.isEmpty(description.getMediaId())
                && (actions & PlaybackState.ACTION_PLAY_FROM_MEDIA_ID) != 0L) {
            controls.playFromMediaId(description.getMediaId(), description.getExtras());
            scheduleMediaQueueSelectionRefresh();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && description != null
                && description.getMediaUri() != null
                && (actions & PlaybackState.ACTION_PLAY_FROM_URI) != 0L) {
            controls.playFromUri(description.getMediaUri(), description.getExtras());
            scheduleMediaQueueSelectionRefresh();
            return;
        }

        dispatchMediaQueueItemBySteps(queue, targetIndex, controller);
    }

    private void dispatchMediaQueueItemBySteps(List<MediaSession.QueueItem> queue,
                                               int targetIndex,
                                               MediaController controller) {
        if (controller == null || controller != activeMediaController) {
            return;
        }
        PlaybackState state = controller.getPlaybackState();
        long actions = state == null ? 0L : state.getActions();
        int currentIndex = findPublishedMediaQueueIndex(controller, queue);
        if (currentIndex < 0) {
            updatePlaylistPanel();
            Toast.makeText(activity, "播放列表已更新", Toast.LENGTH_SHORT).show();
            return;
        }
        int stepCount = Math.abs(targetIndex - currentIndex);
        boolean moveNext = targetIndex > currentIndex;
        long requiredAction = moveNext
                ? PlaybackState.ACTION_SKIP_TO_NEXT : PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        if (stepCount == 0) {
            return;
        }
        if ((actions & requiredAction) == 0L) {
            Toast.makeText(activity, "当前播放器不支持列表切歌", Toast.LENGTH_SHORT).show();
            return;
        }

        int generation = ++mediaQueueNavigationGeneration;
        for (int step = 0; step < stepCount; step++) {
            long delay = step * 220L;
            mediaHandler.postDelayed(() -> {
                if (generation != mediaQueueNavigationGeneration
                        || controller != activeMediaController) {
                    return;
                }
                if (moveNext) {
                    controller.getTransportControls().skipToNext();
                } else {
                    controller.getTransportControls().skipToPrevious();
                }
            }, delay);
        }
        mediaHandler.postDelayed(() -> {
            if (generation == mediaQueueNavigationGeneration) {
                updateMediaUi();
            }
        }, stepCount * 220L + 180L);
    }

    private void scheduleMediaQueueSelectionRefresh() {
        mediaQueueNavigationGeneration++;
        mediaHandler.postDelayed(this::updateMediaUi, 150L);
        mediaHandler.postDelayed(this::updateMediaUi, 500L);
    }

    private String getCurrentMediaLine() {
        MediaMetadata metadata = activeMediaController == null
                ? null : activeMediaController.getMetadata();
        String title = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_TITLE),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                activeMediaNotification == null ? "" : activeMediaNotification.title);
        String artist = firstNonEmpty(
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                getMetadataText(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                activeMediaNotification == null ? "" : activeMediaNotification.text);
        if (TextUtils.isEmpty(title)) {
            return "";
        }
        return TextUtils.isEmpty(artist) ? title : title + " - " + artist;
    }

    private TextView createPlaylistText(String text, float sizeDp, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        setDpTextSize(view, sizeDp);
        return view;
    }

    private String getMetadataText(MediaMetadata metadata, String key) {
        CharSequence value = metadata == null ? null : metadata.getText(key);
        return value == null ? "" : String.valueOf(value);
    }

    private Bitmap getMetadataBitmap(MediaMetadata metadata, String key) {
        return metadata == null ? null : metadata.getBitmap(key);
    }

    private Bitmap firstNonNullBitmap(Bitmap... values) {
        for (Bitmap value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value) && !"null".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private String getAppLabel(String packageName) {
        try {
            return String.valueOf(activity.getPackageManager().getApplicationLabel(
                    activity.getPackageManager().getApplicationInfo(packageName, 0)));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return packageName;
        }
    }



    public void resume() {
        mediaMonitoringActive = true;
    }

    public void pause() {
        mediaMonitoringActive = false;
    }

    @Override public void close() {
        pause();
        if (mediaSourcePopup != null) {
            mediaSourcePopup.dismiss();
            mediaSourcePopup = null;
        }
        if (mediaSessionCoordinator != null) {
            mediaSessionCoordinator.close();
            mediaSessionCoordinator = null;
        }
    }

    private boolean deferWindowSwitchUiWork(int flags) {
        return callbacks.deferWindowSwitchUiWork(flags);
    }
    private ShellCommandResult runMainPrivilegedCommand(
            String command, String description, boolean logOutput) {
        return callbacks.runPrivilegedCommand(command, description, logOutput);
    }
    private String mainShellQuote(String value) { return callbacks.shellQuote(value); }
    private FrameLayout rootContainer() { return callbacks.rootContainer(); }
    private int getTopMediaAreaHeight() { return callbacks.topMediaAreaHeight(); }
    private int getTopMediaPlayerTopMargin() { return callbacks.topMediaPlayerTopMargin(); }
    private int dp(float value) { return callbacks.dp(value); }
    private static FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private static GradientDrawable makeRoundedBackground(int fillColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }
    private static GradientDrawable makePanelBackground(
            int fillColor, int strokeColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, strokeColor);
        return drawable;
    }
    private static void setDpTextSize(TextView view, float value) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, value);
    }
}
