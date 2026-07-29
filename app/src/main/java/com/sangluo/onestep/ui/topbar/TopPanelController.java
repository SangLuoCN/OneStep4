package com.sangluo.onestep.ui.topbar;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.app.Presentation;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.Rating;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.AttachedSurfaceControl;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.sangluo.onestep.data.settings.OneStepSettings;
import com.sangluo.onestep.data.settings.OneStepSettingsStore;
import com.sangluo.onestep.data.apps.LauncherAppRepository;
import com.sangluo.onestep.feature.embedding.EmbeddedAppHost;
import com.sangluo.onestep.feature.embedding.DeviceOrientationMapper;
import com.sangluo.onestep.feature.embedding.EmbeddedStartEpochStore;
import com.sangluo.onestep.feature.embedding.HiddenActivityViewHost;
import com.sangluo.onestep.feature.embedding.HostedDisplayRotationController;
import com.sangluo.onestep.feature.embedding.HostedTaskParser;
import com.sangluo.onestep.feature.navigation.NavigationDisplayFormatter;
import com.sangluo.onestep.feature.media.MediaSessionCoordinator;
import com.sangluo.onestep.model.LauncherApp;
import com.sangluo.onestep.model.PinnedTaskState;
import com.sangluo.onestep.model.VirtualDisplaySpec;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.system.input.RootInputBridgeClient;
import com.sangluo.onestep.system.ui.SystemUiController;
import com.sangluo.onestep.ui.settings.SettingsPanelController;
import com.sangluo.onestep.ui.topbar.TopComponentPage;
import com.sangluo.onestep.ui.topbar.TopComponentPagerAdapter;
import com.sangluo.onestep.ui.format.DurationFormatter;
import com.sangluo.onestep.ui.background.BlurredBackgroundView;
import com.sangluo.onestep.ui.widget.AppShortcutView;
import com.sangluo.onestep.ui.widget.PagingHorizontalScrollView;
import com.sangluo.onestep.ui.media.MediaPlaybackPanel;
import com.sangluo.onestep.ui.window.WindowLayoutCalculator;
import com.sangluo.onestep.ui.window.OneStepWindowView;

import java.lang.reflect.Method;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SIZE_DEFAULT_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SIZE_MAX_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SIZE_MIN_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.DEFAULT_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.DESKTOP_PAGE_COLUMNS;
import static com.sangluo.onestep.data.settings.OneStepSettings.DESKTOP_PAGE_ROWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.MAX_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.MIN_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SIZE_DEFAULT_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_HEIGHT_DEFAULT_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_HEIGHT_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_HEIGHT_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_HEIGHT_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.canUseSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.clamp;
import static com.sangluo.onestep.data.settings.OneStepSettings.isSupportedGridLayout;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeAllowedSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeCornerTriggerSensitivity;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppIconScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppStripSpacingScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppStripVerticalPaddingScale;

import com.sangluo.onestep.AmapNavigationClient;
import com.sangluo.onestep.MainActivity;
import com.sangluo.onestep.MediaNotificationListenerService;
import com.sangluo.onestep.R;

public final class TopPanelController implements AutoCloseable {
    public interface Callbacks {
        boolean deferWindowSwitchUiWork(int flags);
        boolean pipActive();
        Rect pipRestoreBounds();
        boolean topComponentsVisible();
        boolean activityDestroyed();
        void rebuildTopChromeContent();
        void setTopChromeVisible(boolean visible, boolean animate);
        void schedulePipDockBoundsUpdate();
        ShellCommandResult runPrivilegedCommand(
                String command, String description, boolean logOutput);
        String shellQuote(String value);
        GradientDrawable panelBackground(int fillColor, int strokeColor, float radius);
        int topMediaPlayerTopMargin();
        int topMediaAreaHeight();
        int pipDockTopInset();
        FrameLayout rootContainer();
        LauncherApp createLauncherApp(String packageName);
        void addOrFocusApp(LauncherApp app);
        int dp(float value);
    }

    private static final String TAG = "OneStep40";
    private static final int TOP_MEDIA_PLAYER_WIDTH_DP = 360;
    private static final int TOP_MEDIA_PLAYER_HEIGHT_DP = 76;
    private static final int TOP_COMPONENT_BACKGROUND_COLOR = 0xc0000000;
    private static final long TOP_COMPONENT_MEDIA_ID = 1L;
    private static final long TOP_COMPONENT_NAVIGATION_ID = 2L;
    private static final long TOP_COMPONENT_TIMER_ID = 3L;
    private static final long TOP_COMPONENT_RECORDING_ID = 4L;
    private static final long TOP_COMPONENT_STOPWATCH_ID = 5L;
    private static final long TIMER_MILLISECOND_REFRESH_INTERVAL_MS = 33L;
    private static final int DEFERRED_MEDIA_SESSION_REFRESH = 1;
    private static final int DEFERRED_MEDIA_UI_REFRESH = 1 << 1;
    private static final int DEFERRED_PLAYLIST_REFRESH = 1 << 2;
    private static final int DEFERRED_TOP_COMPONENT_REFRESH = 1 << 3;

    private final MainActivity activity;
    private final Callbacks callbacks;
    private final Handler mediaHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mediaRootExecutor;
    private final MediaPlaybackPanel mediaPlaybackPanel;

    private boolean mediaMonitoringActive;
    private ViewPager2 topComponentPager;
    private TopComponentPagerAdapter topComponentPagerAdapter;
    private LinearLayout topComponentIndexView;
    private FrameLayout topPipDockSlot;
    private View topMusicComponentView;
    private View topNavigationComponentView;
    private View topTimerComponentView;
    private View topStopwatchComponentView;
    private View topRecordingComponentView;
    private ImageView topNavigationDirectionView;
    private TextView topNavigationDistanceView;
    private TextView topNavigationRoadView;
    private TextView topNavigationSpeedView;
    private TextView topNavigationRemainTimeView;
    private ImageView topTimerAppIconView;
    private TextView topTimerTimeView;
    private TextView topTimerSubtitleView;
    private ImageView topTimerSecondaryActionView;
    private ImageView topTimerActionView;
    private ImageView topStopwatchAppIconView;
    private TextView topStopwatchTimeView;
    private TextView topStopwatchSubtitleView;
    private ImageView topStopwatchSecondaryActionView;
    private ImageView topStopwatchActionView;
    private ImageView topRecordingAppIconView;
    private TextView topRecordingTimeView;
    private ImageView topRecordingToggleView;
    private ImageView topRecordingStopView;
    private boolean topComponentAreaVisible;
    private AmapNavigationClient amapNavigationClient;
    private AmapNavigationClient.NavigationState activeNavigationState;
    private MediaNotificationListenerService.TopComponentNotificationSnapshot activeTimerComponent;
    private MediaNotificationListenerService.TopComponentNotificationSnapshot activeStopwatchComponent;
    private MediaNotificationListenerService.TopComponentNotificationSnapshot activeRecordingComponent;
    private final List<Long> topComponentPageOrder = new ArrayList<>();
    private long pendingTopComponentFocusId = RecyclerView.NO_ID;
    private int topComponentSelectedPosition;
    private final List<String> topTimerRecords = new ArrayList<>();
    private String topTimerRecordNotificationKey = "";

    private final Runnable mediaProgressTicker = new Runnable() {
        @Override public void run() {
            if (!mediaMonitoringActive) return;
            try {
                mediaPlaybackPanel.updateProgress();
                updateTopStatusComponentTimes();
            } catch (Throwable error) {
                Log.w(TAG, "Media ticker failed: " + error.getClass().getSimpleName());
            }
            if (mediaMonitoringActive) mediaHandler.postDelayed(this, 1000);
        }
    };
    private final Runnable timerMillisecondTicker = new Runnable() {
        @Override public void run() {
            if (!mediaMonitoringActive || activeTimerComponent == null) return;
            if (topTimerTimeView != null) {
                setTextIfChanged(topTimerTimeView, formatTopComponentTime(activeTimerComponent));
            }
            mediaHandler.postDelayed(this, TIMER_MILLISECOND_REFRESH_INTERVAL_MS);
        }
    };
    private final MediaNotificationListenerService.MediaUpdateListener mediaNotificationListener =
            () -> mediaHandler.post(() -> {
                if (deferWindowSwitchUiWork(DEFERRED_MEDIA_SESSION_REFRESH
                        | DEFERRED_TOP_COMPONENT_REFRESH)) return;
                refreshActiveMediaController();
                refreshTopStatusComponents();
            });
    private final AmapNavigationClient.Listener amapNavigationListener = state ->
            mediaHandler.post(() -> {
                if (activeNavigationState == null && state != null) {
                    pendingTopComponentFocusId = TOP_COMPONENT_NAVIGATION_ID;
                } else if (activeNavigationState != null && state == null) {
                    topComponentPageOrder.remove(Long.valueOf(TOP_COMPONENT_NAVIGATION_ID));
                }
                activeNavigationState = state;
                refreshTopStatusComponents();
            });

    public TopPanelController(
            MainActivity activity, ExecutorService mediaRootExecutor, Callbacks callbacks) {
        this.activity = activity;
        this.mediaRootExecutor = mediaRootExecutor;
        this.callbacks = callbacks;
        mediaPlaybackPanel = new MediaPlaybackPanel(
                activity, mediaHandler, mediaRootExecutor,
                new MediaPlaybackPanel.Callbacks() {
            @Override public boolean deferWindowSwitchUiWork(int flags) {
                return callbacks.deferWindowSwitchUiWork(flags);
            }
            @Override public ShellCommandResult runPrivilegedCommand(
                    String command, String description, boolean logOutput) {
                return callbacks.runPrivilegedCommand(command, description, logOutput);
            }
            @Override public String shellQuote(String value) {
                return callbacks.shellQuote(value);
            }
            @Override public void openComponentApp(String packageName) {
                TopPanelController.this.openComponentApp(packageName);
            }
            @Override public FrameLayout rootContainer() {
                return callbacks.rootContainer();
            }
            @Override public int topMediaAreaHeight() {
                return callbacks.topMediaAreaHeight();
            }
            @Override public int topMediaPlayerTopMargin() {
                return callbacks.topMediaPlayerTopMargin();
            }
            @Override public int dp(float value) { return callbacks.dp(value); }
        });
    }

    public View createView() {
        FrameLayout mediaArea = new FrameLayout(activity);
        mediaArea.setBackgroundColor(Color.TRANSPARENT);
        topComponentPager = null;
        topComponentPagerAdapter = null;
        topComponentIndexView = null;
        topPipDockSlot = null;
        topComponentSelectedPosition = 0;
        topMusicComponentView = null;
        topNavigationComponentView = null;
        topTimerComponentView = null;
        topStopwatchComponentView = null;
        topRecordingComponentView = null;
        topTimerAppIconView = null;
        topStopwatchAppIconView = null;
        topTimerSecondaryActionView = null;
        topStopwatchSecondaryActionView = null;
        topComponentAreaVisible = shouldShowTopComponentArea();
        if (!topComponentAreaVisible) {
            return mediaArea;
        }
        if (callbacks.pipActive()) {
            addPipDockSlot(mediaArea);
            return mediaArea;
        }

        topMusicComponentView = createTopComponentPage(createTopMusicPlayer());
        topNavigationComponentView = createTopComponentPage(createTopNavigationComponent());
        topTimerComponentView = createTopComponentPage(createTopTimerComponent(false));
        topStopwatchComponentView = createTopComponentPage(createTopTimerComponent(true));
        topRecordingComponentView = createTopComponentPage(createTopRecordingComponent());

        ViewPager2 pager = new ViewPager2(activity);
        topComponentPager = pager;
        pager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        pager.setOffscreenPageLimit(1);
        if (pager.getChildCount() > 0) {
            pager.getChildAt(0).setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        TopComponentPagerAdapter adapter = new TopComponentPagerAdapter();
        topComponentPagerAdapter = adapter;
        pager.setAdapter(adapter);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                topComponentSelectedPosition = position;
                updateTopComponentIndexIndicator();
            }
        });

        LinearLayout indexView = new LinearLayout(activity);
        topComponentIndexView = indexView;
        indexView.setOrientation(LinearLayout.HORIZONTAL);
        indexView.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams indexLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(12),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        indexLp.topMargin = getTopMediaPlayerTopMargin()
                + dp(TOP_MEDIA_PLAYER_HEIGHT_DP) + dp(2);
        mediaArea.addView(indexView, indexLp);

        FrameLayout.LayoutParams playerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(TOP_MEDIA_PLAYER_HEIGHT_DP),
                Gravity.TOP);
        playerLp.topMargin = getTopMediaPlayerTopMargin();
        mediaArea.addView(pager, playerLp);
        updateTopStatusComponentViews();
        return mediaArea;
    }

    private void addPipDockSlot(FrameLayout mediaArea) {
        FrameLayout slot = new FrameLayout(activity);
        topPipDockSlot = slot;
        slot.setBackgroundColor(Color.TRANSPARENT);
        slot.setClickable(false);
        slot.setFocusable(false);

        float aspectRatio = getPipAspectRatio();
        int slotHeight = callbacks.pipRestoreBounds().height() > 0
                ? callbacks.pipRestoreBounds().height() : dp(TOP_MEDIA_PLAYER_HEIGHT_DP);
        int maxWidth = Math.max(dp(48),
                activity.getResources().getDisplayMetrics().widthPixels - dp(32));
        int slotWidth = callbacks.pipRestoreBounds().width() > 0
                ? callbacks.pipRestoreBounds().width()
                : Math.max(dp(48), Math.round(slotHeight * aspectRatio));
        if (slotWidth > maxWidth) {
            float scale = maxWidth / (float) slotWidth;
            slotWidth = maxWidth;
            slotHeight = Math.max(dp(48), Math.round(slotHeight * scale));
        }
        FrameLayout.LayoutParams slotLp = new FrameLayout.LayoutParams(
                slotWidth, slotHeight, Gravity.TOP | Gravity.END);
        slotLp.topMargin = getPipDockTopInset();
        slotLp.rightMargin = dp(18);
        mediaArea.addView(slot, slotLp);
        slot.addOnLayoutChangeListener((view, left, top, right, bottom,
                                        oldLeft, oldTop, oldRight, oldBottom) ->
                schedulePipDockBoundsUpdate());
        slot.post(this::schedulePipDockBoundsUpdate);
    }

    public float getPipAspectRatio() {
        if (callbacks.pipRestoreBounds().width() <= 0 || callbacks.pipRestoreBounds().height() <= 0) {
            return 16f / 9f;
        }
        float ratio = callbacks.pipRestoreBounds().width() / (float) callbacks.pipRestoreBounds().height();
        return Math.max(1f / 2.39f, Math.min(2.39f, ratio));
    }

    private View createTopComponentPage(View card) {
        FrameLayout page = new FrameLayout(activity);
        card.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        cardParams.leftMargin = dp(16);
        cardParams.rightMargin = dp(16);
        page.addView(card, cardParams);
        return page;
    }

    public boolean shouldShowTopComponentArea() {
        return callbacks.topComponentsVisible();
    }

    private View createTopNavigationComponent() {
        LinearLayout component = createTopStatusComponentBase("导航");
        component.setOnClickListener(v -> openAmapNavigation());

        topNavigationDirectionView = new ImageView(activity);
        topNavigationDirectionView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topNavigationDirectionView.setBackgroundColor(Color.TRANSPARENT);
        topNavigationDirectionView.setPadding(dp(3), dp(3), dp(3), dp(3));
        topNavigationDirectionView.setImageResource(R.drawable.nav_maneuver_type_9);
        component.addView(topNavigationDirectionView, new LinearLayout.LayoutParams(
                dp(58), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout routeDetails = new LinearLayout(activity);
        routeDetails.setOrientation(LinearLayout.VERTICAL);
        routeDetails.setGravity(Gravity.CENTER_VERTICAL);

        topNavigationDistanceView = new TextView(activity);
        topNavigationDistanceView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topNavigationDistanceView.setSingleLine(true);
        topNavigationDistanceView.setTextColor(0xffffffff);
        topNavigationDistanceView.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(topNavigationDistanceView, 23);
        routeDetails.addView(topNavigationDistanceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        topNavigationRoadView = new TextView(activity);
        topNavigationRoadView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topNavigationRoadView.setSingleLine(true);
        topNavigationRoadView.setEllipsize(TextUtils.TruncateAt.END);
        topNavigationRoadView.setTextColor(0xbfffffff);
        setDpTextSize(topNavigationRoadView, 11);
        routeDetails.addView(topNavigationRoadView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams routeLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        routeLp.leftMargin = dp(6);
        routeLp.rightMargin = dp(6);
        component.addView(routeDetails, routeLp);

        LinearLayout metrics = new LinearLayout(activity);
        metrics.setOrientation(LinearLayout.VERTICAL);
        metrics.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);

        topNavigationSpeedView = createNavigationMetricView();
        setDpTextSize(topNavigationSpeedView, 23);
        metrics.addView(topNavigationSpeedView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        topNavigationRemainTimeView = createNavigationMetricView();
        metrics.addView(topNavigationRemainTimeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams metricsLp = new LinearLayout.LayoutParams(dp(110),
                ViewGroup.LayoutParams.MATCH_PARENT);
        metricsLp.rightMargin = dp(6);
        component.addView(metrics, metricsLp);
        component.setVisibility(View.GONE);
        return component;
    }

    private TextView createNavigationMetricView() {
        TextView view = new TextView(activity);
        view.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setTextColor(0xd9ffffff);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(view, 10);
        return view;
    }

    private View createTopTimerComponent(boolean stopwatch) {
        String label = stopwatch ? "正计时" : "倒计时";
        LinearLayout component = createTopStatusComponentBase(label);
        component.setOnClickListener(v -> openTopComponentApp(stopwatch
                ? activeStopwatchComponent : activeTimerComponent));

        ImageView clockIcon = new ImageView(activity);
        clockIcon.setImageResource(android.R.drawable.ic_lock_idle_alarm);
        clockIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        clockIcon.setBackgroundColor(Color.TRANSPARENT);
        clockIcon.setContentDescription(label);
        component.addView(clockIcon, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);

        TextView timeView = new TextView(activity);
        timeView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        timeView.setSingleLine(true);
        timeView.setEllipsize(TextUtils.TruncateAt.END);
        timeView.setTextColor(0xffffffff);
        timeView.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(timeView, 22);
        details.addView(timeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView subtitleView = new TextView(activity);
        subtitleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        subtitleView.setSingleLine(true);
        subtitleView.setText(label);
        subtitleView.setTextColor(0xb3ffffff);
        setDpTextSize(subtitleView, 12);
        details.addView(subtitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        detailsLp.leftMargin = dp(14);
        detailsLp.rightMargin = dp(6);
        component.addView(details, detailsLp);

        ImageView secondaryActionView = createCircularStatusActionButton(
                R.drawable.one_step_component_timing, 0x33ffffff);
        secondaryActionView.setContentDescription(stopwatch ? "计次" : "计时记录");
        secondaryActionView.setOnClickListener(v -> {
            if (stopwatch) {
                dispatchTopComponentSecondaryAction(activeStopwatchComponent);
            } else {
                addTopTimerRecord();
            }
        });
        component.addView(secondaryActionView, createCircularStatusActionLayoutParams());

        ImageView actionView = createCircularStatusActionButton(
                R.drawable.one_step_component_pause, 0x33ffffff);
        actionView.setOnClickListener(v -> dispatchTopComponentAction(stopwatch
                ? activeStopwatchComponent : activeTimerComponent));
        component.addView(actionView, createCircularStatusActionLayoutParams());

        if (stopwatch) {
            topStopwatchAppIconView = clockIcon;
            topStopwatchTimeView = timeView;
            topStopwatchSubtitleView = subtitleView;
            topStopwatchSecondaryActionView = secondaryActionView;
            topStopwatchActionView = actionView;
        } else {
            topTimerAppIconView = clockIcon;
            topTimerTimeView = timeView;
            topTimerSubtitleView = subtitleView;
            topTimerSecondaryActionView = secondaryActionView;
            topTimerActionView = actionView;
        }
        component.setVisibility(View.GONE);
        return component;
    }

    private View createTopRecordingComponent() {
        LinearLayout component = createTopStatusComponentBase("录音");
        component.setOnClickListener(v -> openTopComponentApp(activeRecordingComponent));

        topRecordingAppIconView = new ImageView(activity);
        topRecordingAppIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        topRecordingAppIconView.setBackgroundColor(Color.TRANSPARENT);
        component.addView(topRecordingAppIconView, new LinearLayout.LayoutParams(dp(58),
                dp(58)));

        LinearLayout recordingDetails = new LinearLayout(activity);
        recordingDetails.setOrientation(LinearLayout.VERTICAL);
        recordingDetails.setGravity(Gravity.CENTER_VERTICAL);

        topRecordingTimeView = new TextView(activity);
        topRecordingTimeView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topRecordingTimeView.setSingleLine(true);
        topRecordingTimeView.setEllipsize(TextUtils.TruncateAt.END);
        topRecordingTimeView.setTextColor(0xffffffff);
        topRecordingTimeView.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(topRecordingTimeView, 22);
        recordingDetails.addView(topRecordingTimeView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView recordingSubtitle = new TextView(activity);
        recordingSubtitle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        recordingSubtitle.setSingleLine(true);
        recordingSubtitle.setText("录音");
        recordingSubtitle.setTextColor(0xb3ffffff);
        setDpTextSize(recordingSubtitle, 12);
        recordingDetails.addView(recordingSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        detailsLp.leftMargin = dp(14);
        detailsLp.rightMargin = dp(6);
        component.addView(recordingDetails, detailsLp);

        topRecordingToggleView = createCircularStatusActionButton(
                R.drawable.one_step_component_pause, 0x33ffffff);
        topRecordingToggleView.setOnClickListener(v ->
                dispatchTopComponentAction(activeRecordingComponent));
        component.addView(topRecordingToggleView,
                createCircularStatusActionLayoutParams());

        topRecordingStopView = createCircularStatusActionButton(
                R.drawable.one_step_component_stop, 0xffff5252);
        topRecordingStopView.setContentDescription("停止录音");
        topRecordingStopView.setOnClickListener(v ->
                dispatchTopRecordingStopAction(activeRecordingComponent));
        component.addView(topRecordingStopView,
                createCircularStatusActionLayoutParams());
        component.setVisibility(View.GONE);
        return component;
    }

    private ImageView createCircularStatusActionButton(int iconResId, int backgroundColor) {
        ImageView button = new ImageView(activity);
        button.setImageResource(iconResId);
        button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(backgroundColor);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams createCircularStatusActionLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        return lp;
    }

    private LinearLayout createTopStatusComponentBase(String description) {
        LinearLayout component = new LinearLayout(activity);
        component.setOrientation(LinearLayout.HORIZONTAL);
        component.setGravity(Gravity.CENTER_VERTICAL);
        component.setPadding(dp(10), dp(8), dp(10), dp(8));
        component.setBackground(makeRoundedBackground(
                TOP_COMPONENT_BACKGROUND_COLOR, dp(12)));
        component.setContentDescription(description);
        return component;
    }


    private View createTopMusicPlayer() {
        return mediaPlaybackPanel.createView();
    }

    public void startMediaMonitoring() {
        mediaPlaybackPanel.start();
        MediaNotificationListenerService.addListener(mediaNotificationListener);
        refreshTopStatusComponents();
        resume();
    }

    public void startNavigationMonitoring() {
        amapNavigationClient = new AmapNavigationClient(activity, amapNavigationListener);
        amapNavigationClient.start();
    }

    public void resume() {
        mediaMonitoringActive = true;
        mediaPlaybackPanel.resume();
        mediaHandler.removeCallbacks(mediaProgressTicker);
        mediaHandler.post(mediaProgressTicker);
        syncTimerMillisecondTicker();
    }

    public void pause() {
        mediaMonitoringActive = false;
        mediaPlaybackPanel.pause();
        mediaHandler.removeCallbacks(mediaProgressTicker);
        mediaHandler.removeCallbacks(timerMillisecondTicker);
    }

    private void syncTimerMillisecondTicker() {
        mediaHandler.removeCallbacks(timerMillisecondTicker);
        if (mediaMonitoringActive && activeTimerComponent != null) {
            mediaHandler.post(timerMillisecondTicker);
        }
    }

    public void refreshTopStatusComponents() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mediaHandler.post(this::refreshTopStatusComponents);
            return;
        }
        if (deferWindowSwitchUiWork(DEFERRED_TOP_COMPONENT_REFRESH)) {
            return;
        }
        boolean wasVisible = topComponentAreaVisible;
        List<MediaNotificationListenerService.TopComponentNotificationSnapshot> snapshots =
                MediaNotificationListenerService.getTopComponentSnapshots();
        MediaNotificationListenerService.TopComponentNotificationSnapshot nextTimerComponent =
                findTopComponentSnapshot(snapshots,
                MediaNotificationListenerService.TopComponentNotificationSnapshot.TYPE_TIMER);
        MediaNotificationListenerService.TopComponentNotificationSnapshot nextStopwatchComponent =
                findTopComponentSnapshot(snapshots,
                MediaNotificationListenerService.TopComponentNotificationSnapshot
                        .TYPE_STOPWATCH);
        MediaNotificationListenerService.TopComponentNotificationSnapshot nextRecordingComponent =
                findTopComponentSnapshot(snapshots,
                MediaNotificationListenerService.TopComponentNotificationSnapshot.TYPE_RECORDING);
        if (activeTimerComponent == null && nextTimerComponent != null) {
            pendingTopComponentFocusId = TOP_COMPONENT_TIMER_ID;
        }
        if (activeStopwatchComponent == null && nextStopwatchComponent != null) {
            pendingTopComponentFocusId = TOP_COMPONENT_STOPWATCH_ID;
        }
        if (activeRecordingComponent == null && nextRecordingComponent != null) {
            pendingTopComponentFocusId = TOP_COMPONENT_RECORDING_ID;
        }
        if (activeTimerComponent != null && nextTimerComponent == null) {
            topComponentPageOrder.remove(Long.valueOf(TOP_COMPONENT_TIMER_ID));
        }
        if (activeStopwatchComponent != null && nextStopwatchComponent == null) {
            topComponentPageOrder.remove(Long.valueOf(TOP_COMPONENT_STOPWATCH_ID));
        }
        if (activeRecordingComponent != null && nextRecordingComponent == null) {
            topComponentPageOrder.remove(Long.valueOf(TOP_COMPONENT_RECORDING_ID));
        }
        syncTopTimerRecordState(activeTimerComponent, nextTimerComponent);
        activeTimerComponent = nextTimerComponent;
        activeStopwatchComponent = nextStopwatchComponent;
        activeRecordingComponent = nextRecordingComponent;
        syncTimerMillisecondTicker();
        boolean nowVisible = shouldShowTopComponentArea();
        boolean missingVisibleContent = nowVisible && (callbacks.pipActive()
                ? topPipDockSlot == null
                : topComponentPager == null);
        if (wasVisible != nowVisible || missingVisibleContent) {
            rebuildTopChromeContent();
            return;
        }
        updateTopStatusComponentViews();
    }

    public void refreshAppIcons() {
        mediaPlaybackPanel.refreshAppIcons();
        refreshTopStatusComponents();
    }

    private MediaNotificationListenerService.TopComponentNotificationSnapshot
    findTopComponentSnapshot(
            List<MediaNotificationListenerService.TopComponentNotificationSnapshot> snapshots,
            int type) {
        for (MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot
                : snapshots) {
            if (snapshot.type == type) {
                return snapshot;
            }
        }
        return null;
    }

    private void updateTopStatusComponentViews() {
        updateTopNavigationComponentView();
        updateTopTimerComponentView();
        updateTopStopwatchComponentView();
        updateTopRecordingComponentView();
        refreshTopComponentPagerPages();
        updateTopComponentIndexIndicator();
    }

    private void refreshTopComponentPagerPages() {
        ViewPager2 pager = topComponentPager;
        TopComponentPagerAdapter adapter = topComponentPagerAdapter;
        if (pager == null || adapter == null) {
            return;
        }
        long currentPageId = adapter.getPageId(pager.getCurrentItem());
        int previousPosition = pager.getCurrentItem();
        List<TopComponentPage> availablePages = new ArrayList<>();
        if (callbacks.topComponentsVisible() && topMusicComponentView != null) {
            availablePages.add(new TopComponentPage(
                    TOP_COMPONENT_MEDIA_ID, topMusicComponentView));
        }
        if (activeNavigationState != null && topNavigationComponentView != null) {
            availablePages.add(new TopComponentPage(TOP_COMPONENT_NAVIGATION_ID,
                    topNavigationComponentView));
        }
        if (activeTimerComponent != null && topTimerComponentView != null) {
            availablePages.add(new TopComponentPage(
                    TOP_COMPONENT_TIMER_ID, topTimerComponentView));
        }
        if (activeStopwatchComponent != null && topStopwatchComponentView != null) {
            availablePages.add(new TopComponentPage(TOP_COMPONENT_STOPWATCH_ID,
                    topStopwatchComponentView));
        }
        if (activeRecordingComponent != null && topRecordingComponentView != null) {
            availablePages.add(new TopComponentPage(TOP_COMPONENT_RECORDING_ID,
                    topRecordingComponentView));
        }
        for (int index = topComponentPageOrder.size() - 1; index >= 0; index--) {
            if (findTopComponentPage(availablePages,
                    topComponentPageOrder.get(index)) == null) {
                topComponentPageOrder.remove(index);
            }
        }
        for (TopComponentPage availablePage : availablePages) {
            if (!topComponentPageOrder.contains(availablePage.id)) {
                topComponentPageOrder.add(availablePage.id);
            }
        }
        List<TopComponentPage> pages = new ArrayList<>(availablePages.size());
        for (long pageId : topComponentPageOrder) {
            TopComponentPage page = findTopComponentPage(availablePages, pageId);
            if (page != null) {
                pages.add(page);
            }
        }
        boolean pagesChanged = adapter.setPages(pages);
        if (!pagesChanged && pendingTopComponentFocusId == RecyclerView.NO_ID) {
            return;
        }
        int targetPosition = adapter.indexOfPageId(pendingTopComponentFocusId);
        boolean focusNewComponent = targetPosition >= 0;
        pendingTopComponentFocusId = RecyclerView.NO_ID;
        if (!focusNewComponent) {
            targetPosition = adapter.indexOfPageId(currentPageId);
            if (targetPosition < 0) {
                targetPosition = Math.min(previousPosition,
                        Math.max(0, adapter.getItemCount() - 1));
            }
        }
        if (adapter.getItemCount() > 0) {
            topComponentSelectedPosition = targetPosition;
            pager.setCurrentItem(targetPosition, focusNewComponent);
        } else {
            topComponentSelectedPosition = 0;
        }
    }

    private TopComponentPage findTopComponentPage(List<TopComponentPage> pages, long pageId) {
        for (TopComponentPage page : pages) {
            if (page.id == pageId) {
                return page;
            }
        }
        return null;
    }

    private void updateTopComponentIndexIndicator() {
        LinearLayout indicator = topComponentIndexView;
        ViewPager2 pager = topComponentPager;
        TopComponentPagerAdapter adapter = topComponentPagerAdapter;
        if (indicator == null || pager == null || adapter == null) {
            return;
        }
        int pageCount = adapter.getItemCount();
        indicator.setVisibility(pageCount > 0 ? View.VISIBLE : View.GONE);
        if (pageCount == 0) {
            indicator.removeAllViews();
            return;
        }
        if (indicator.getChildCount() != pageCount) {
            indicator.removeAllViews();
            for (int i = 0; i < pageCount; i++) {
                View dot = new View(activity);
                LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(6), dp(6));
                dotLp.leftMargin = dp(3);
                dotLp.rightMargin = dp(3);
                indicator.addView(dot, dotLp);
            }
        }
        int currentPage = topComponentSelectedPosition;
        currentPage = Math.max(0, Math.min(pageCount - 1, currentPage));
        for (int i = 0; i < indicator.getChildCount(); i++) {
            GradientDrawable dotBackground = new GradientDrawable();
            dotBackground.setShape(GradientDrawable.OVAL);
            dotBackground.setColor(i == currentPage ? 0xffffffff : 0x66ffffff);
            indicator.getChildAt(i).setBackground(dotBackground);
        }
    }

    private void updateTopNavigationComponentView() {
        if (topNavigationComponentView == null) {
            return;
        }
        AmapNavigationClient.NavigationState state = activeNavigationState;
        if (state == null) {
            return;
        }
        topNavigationDirectionView.setImageResource(
                NavigationDisplayFormatter.getNavigationManeuverIconRes(state.maneuverId));
        topNavigationDirectionView.setContentDescription(
                NavigationDisplayFormatter.getNavigationManeuverDescription(state.maneuverId));
        topNavigationDistanceView.setText(
                NavigationDisplayFormatter.formatNavigationDistance(
                        state.segmentRemainDistanceMeters));
        topNavigationRoadView.setText(NavigationDisplayFormatter.formatNavigationRoads(
                state.currentRoadName, state.nextRoadName));
        topNavigationSpeedView.setText(state.speedKph + " km/h");
        topNavigationRemainTimeView.setText(
                NavigationDisplayFormatter.formatNavigationRemainingSummary(
                        state.routeRemainTimeSeconds,
                        state.routeRemainDistanceMeters));
        topNavigationComponentView.setContentDescription(
                NavigationDisplayFormatter.getNavigationManeuverDescription(state.maneuverId)
                        + "，" + NavigationDisplayFormatter.formatNavigationDistance(
                        state.segmentRemainDistanceMeters)
                        + "，" + NavigationDisplayFormatter.formatNavigationRoads(
                        state.currentRoadName, state.nextRoadName));
    }

    private void openAmapNavigation() {
        openComponentApp(AmapNavigationClient.PACKAGE_NAME, "找不到高德地图");
    }

    private void openTopComponentApp(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot != null) {
            openComponentApp(snapshot.packageName);
        }
    }

    private void openComponentApp(String packageName) {
        openComponentApp(packageName, "找不到对应应用");
    }

    private void openComponentApp(String packageName, String unavailableMessage) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        LauncherApp app = callbacks.createLauncherApp(packageName);
        if (app == null) {
            Toast.makeText(activity, unavailableMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        callbacks.addOrFocusApp(app);
    }

    private void updateTopTimerComponentView() {
        updateTopTimerCard(topTimerComponentView, topTimerAppIconView, topTimerTimeView,
                topTimerSubtitleView, topTimerSecondaryActionView, topTimerActionView,
                activeTimerComponent, "倒计时");
    }

    private void updateTopStopwatchComponentView() {
        updateTopTimerCard(topStopwatchComponentView, topStopwatchAppIconView,
                topStopwatchTimeView,
                topStopwatchSubtitleView, topStopwatchSecondaryActionView,
                topStopwatchActionView,
                activeStopwatchComponent, "正计时");
    }

    private void updateTopTimerCard(View componentView, ImageView appIconView, TextView timeView,
                                    TextView subtitleView, ImageView secondaryActionView,
                                    ImageView actionView,
                                    MediaNotificationListenerService
                                            .TopComponentNotificationSnapshot snapshot,
                                    String label) {
        if (componentView == null || timeView == null || subtitleView == null
                || secondaryActionView == null || actionView == null || snapshot == null) {
            return;
        }
        setTopComponentAppIcon(appIconView, snapshot.packageName,
                android.R.drawable.ic_lock_idle_alarm);
        timeView.setText(formatTopComponentTime(snapshot));
        boolean timer = snapshot.type == MediaNotificationListenerService
                .TopComponentNotificationSnapshot.TYPE_TIMER;
        subtitleView.setText(timer ? formatTopTimerSubtitle() : label);
        boolean secondaryActionEnabled = timer || snapshot.secondaryAction != null;
        secondaryActionView.setEnabled(secondaryActionEnabled);
        secondaryActionView.setAlpha(secondaryActionEnabled ? 1f : 0.45f);
        secondaryActionView.setContentDescription(timer ? "计时记录"
                : firstNonEmpty(snapshot.secondaryActionTitle, "计次"));
        updateTopStatusActionButton(actionView, snapshot);
    }

    private void syncTopTimerRecordState(
            MediaNotificationListenerService.TopComponentNotificationSnapshot previous,
            MediaNotificationListenerService.TopComponentNotificationSnapshot next) {
        if (next == null) {
            topTimerRecords.clear();
            topTimerRecordNotificationKey = "";
            return;
        }
        if (previous == null
                || !TextUtils.equals(topTimerRecordNotificationKey, next.notificationKey)) {
            topTimerRecords.clear();
            topTimerRecordNotificationKey = next.notificationKey;
        }
    }

    private void addTopTimerRecord() {
        MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot =
                activeTimerComponent;
        if (snapshot == null) {
            return;
        }
        if (!TextUtils.equals(topTimerRecordNotificationKey, snapshot.notificationKey)) {
            topTimerRecords.clear();
            topTimerRecordNotificationKey = snapshot.notificationKey;
        }
        topTimerRecords.add(formatTopComponentTime(snapshot));
        setTextIfChanged(topTimerSubtitleView, formatTopTimerSubtitle());
    }

    private String formatTopTimerSubtitle() {
        if (topTimerRecords.isEmpty()) {
            return "倒计时";
        }
        int lastIndex = topTimerRecords.size() - 1;
        return "倒计时 · #" + topTimerRecords.size() + " "
                + topTimerRecords.get(lastIndex);
    }

    private void updateTopRecordingComponentView() {
        if (topRecordingComponentView == null) {
            return;
        }
        MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot =
                activeRecordingComponent;
        if (snapshot == null) {
            return;
        }
        topRecordingTimeView.setText(formatRecordingDuration(snapshot));
        setTopComponentAppIcon(topRecordingAppIconView, snapshot.packageName,
                android.R.drawable.ic_btn_speak_now);
        updateTopStatusActionButton(topRecordingToggleView, snapshot);
        boolean stopEnabled = snapshot.stopAction != null;
        topRecordingStopView.setEnabled(stopEnabled);
        topRecordingStopView.setAlpha(stopEnabled ? 1f : 0.45f);
        topRecordingStopView.setContentDescription(firstNonEmpty(snapshot.stopActionTitle,
                "停止录音"));
    }

    private void updateTopStatusComponentTimes() {
        if (topTimerComponentView != null && topTimerComponentView.getVisibility() == View.VISIBLE
                && activeTimerComponent != null) {
            setTextIfChanged(topTimerTimeView, formatTopComponentTime(activeTimerComponent));
        }
        if (topStopwatchComponentView != null
                && topStopwatchComponentView.getVisibility() == View.VISIBLE
                && activeStopwatchComponent != null) {
            setTextIfChanged(topStopwatchTimeView,
                    formatTopComponentTime(activeStopwatchComponent));
        }
        if (topRecordingComponentView != null
                && topRecordingComponentView.getVisibility() == View.VISIBLE
                && activeRecordingComponent != null) {
            setTextIfChanged(topRecordingTimeView,
                    formatRecordingDuration(activeRecordingComponent));
        }
    }

    private void updateTopStatusActionButton(ImageView button,
                                             MediaNotificationListenerService
                                                     .TopComponentNotificationSnapshot snapshot) {
        if (button == null || snapshot == null) {
            return;
        }
        boolean enabled = snapshot.toggleAction != null;
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.45f);
        button.setImageResource(snapshot.running
                ? R.drawable.one_step_component_pause
                : R.drawable.one_step_component_play);
        button.setContentDescription(firstNonEmpty(snapshot.actionTitle,
                snapshot.running ? "暂停" : "继续"));
    }

    private void dispatchTopComponentAction(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot == null || snapshot.toggleAction == null) {
            return;
        }
        try {
            snapshot.toggleAction.send();
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 180);
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 700);
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            Log.w(TAG, "Top component action failed: package=" + snapshot.packageName
                    + ", error=" + e.getClass().getSimpleName());
            refreshTopStatusComponents();
        }
    }

    private void dispatchTopComponentSecondaryAction(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot == null || snapshot.secondaryAction == null) {
            return;
        }
        try {
            snapshot.secondaryAction.send();
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 180);
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 700);
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            Log.w(TAG, "Top component secondary action failed: package="
                    + snapshot.packageName + ", error=" + e.getClass().getSimpleName());
            refreshTopStatusComponents();
        }
    }

    private void dispatchTopRecordingStopAction(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot == null || snapshot.stopAction == null) {
            return;
        }
        try {
            snapshot.stopAction.send();
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 180);
            mediaHandler.postDelayed(this::refreshTopStatusComponents, 700);
        } catch (PendingIntent.CanceledException | RuntimeException e) {
            Log.w(TAG, "Stop recording action failed: package=" + snapshot.packageName
                    + ", error=" + e.getClass().getSimpleName());
            refreshTopStatusComponents();
        }
    }

    private void setTopComponentAppIcon(ImageView imageView, String packageName,
                                        int fallbackResId) {
        if (imageView == null) {
            return;
        }
        try {
            imageView.setImageDrawable(
                    activity.getPackageManager().getApplicationIcon(packageName));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            imageView.setImageResource(fallbackResId);
        }
    }

    private String formatTopComponentTime(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot == null) {
            return "";
        }
        if (snapshot.running && snapshot.chronometerBaseElapsedRealtime > 0L) {
            long delta = snapshot.chronometerCountDown
                    ? snapshot.chronometerBaseElapsedRealtime - SystemClock.elapsedRealtime()
                    : SystemClock.elapsedRealtime() - snapshot.chronometerBaseElapsedRealtime;
            return formatTopComponentDuration(snapshot, Math.max(0L, delta));
        }
        if (!TextUtils.isEmpty(snapshot.displayedTime)) {
            return snapshot.type == MediaNotificationListenerService
                    .TopComponentNotificationSnapshot.TYPE_TIMER
                    ? DurationFormatter.ensureMilliseconds(snapshot.displayedTime)
                    : snapshot.displayedTime;
        }
        if (snapshot.showChronometer && snapshot.when > 0L) {
            long delta = snapshot.chronometerCountDown
                    ? snapshot.when - System.currentTimeMillis()
                    : System.currentTimeMillis() - snapshot.when;
            return formatTopComponentDuration(snapshot, Math.max(0L, delta));
        }
        return firstNonEmpty(snapshot.text, snapshot.title, snapshot.subText, snapshot.infoText,
                getAppLabel(snapshot.packageName));
    }

    private String formatTopComponentDuration(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot,
            long millis) {
        if (snapshot.type == MediaNotificationListenerService
                .TopComponentNotificationSnapshot.TYPE_TIMER) {
            return DurationFormatter.formatDurationWithMilliseconds(millis);
        }
        return DurationFormatter.formatDuration(millis);
    }

    private String formatRecordingDuration(
            MediaNotificationListenerService.TopComponentNotificationSnapshot snapshot) {
        if (snapshot == null) {
            return "0:00";
        }
        if (snapshot.showChronometer && snapshot.when > 0L) {
            long delta = snapshot.chronometerCountDown
                    ? snapshot.when - System.currentTimeMillis()
                    : System.currentTimeMillis() - snapshot.when;
            return DurationFormatter.formatDuration(Math.max(0L, delta));
        }
        return DurationFormatter.findRecordingDuration(
                snapshot.text, snapshot.title, snapshot.subText, snapshot.infoText);
    }


    public void refreshActiveMediaController() {
        mediaPlaybackPanel.refreshActiveMediaController();
    }

    public void updateMediaUi() {
        mediaPlaybackPanel.updateMediaUi();
    }

    public boolean hidePlaylistPanel() {
        return mediaPlaybackPanel.hidePlaylistPanel();
    }

    public void updatePlaylistPanel() {
        mediaPlaybackPanel.updatePlaylistPanel();
    }

    public FrameLayout getPipDockSlot() {
        return topPipDockSlot;
    }

    @Override public void close() {
        pause();
        mediaHandler.removeCallbacks(mediaProgressTicker);
        MediaNotificationListenerService.removeListener(mediaNotificationListener);
        if (amapNavigationClient != null) {
            amapNavigationClient.close();
            amapNavigationClient = null;
        }
        mediaPlaybackPanel.close();
    }

    private boolean deferWindowSwitchUiWork(int flags) {
        return callbacks.deferWindowSwitchUiWork(flags);
    }
    private void rebuildTopChromeContent() { callbacks.rebuildTopChromeContent(); }
    private void setTopChromeVisible(boolean visible, boolean animate) {
        callbacks.setTopChromeVisible(visible, animate);
    }
    private void schedulePipDockBoundsUpdate() { callbacks.schedulePipDockBoundsUpdate(); }
    private ShellCommandResult runMainPrivilegedCommand(
            String command, String description, boolean logOutput) {
        return callbacks.runPrivilegedCommand(command, description, logOutput);
    }
    private String mainShellQuote(String value) { return callbacks.shellQuote(value); }
    private GradientDrawable makePanelBackground(int fill, int stroke, float radius) {
        return callbacks.panelBackground(fill, stroke, radius);
    }
    private int getTopMediaPlayerTopMargin() { return callbacks.topMediaPlayerTopMargin(); }
    private int getTopMediaAreaHeight() { return callbacks.topMediaAreaHeight(); }
    private int getPipDockTopInset() { return callbacks.pipDockTopInset(); }
    private int dp(float value) { return callbacks.dp(value); }
    private GradientDrawable makeRoundedBackground(int fillColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }
    private static FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }
    private static void setDpTextSize(TextView view, float value) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, value);
    }

    private static void setTextIfChanged(TextView view, String text) {
        if (view != null && !TextUtils.equals(view.getText(), text)) {
            view.setText(text);
        }
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (!TextUtils.isEmpty(value)) {
                return value;
            }
        }
        return "";
    }

    private String getAppLabel(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return "";
        }
        try {
            PackageManager packageManager = activity.getPackageManager();
            return String.valueOf(packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }
}
