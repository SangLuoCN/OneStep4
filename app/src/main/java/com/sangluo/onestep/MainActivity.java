package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import com.sangluo.onestep.data.settings.OneStepSettings;
import com.sangluo.onestep.data.settings.OneStepSettingsStore;
import com.sangluo.onestep.data.settings.TopAppListPolicy;
import com.sangluo.onestep.data.apps.LauncherAppRepository;
import com.sangluo.onestep.feature.embedding.EmbeddedAppHost;
import com.sangluo.onestep.feature.embedding.DismissedAppClosePolicy;
import com.sangluo.onestep.feature.embedding.DefaultHomeRoutingPolicy;
import com.sangluo.onestep.feature.embedding.EmbeddedStartEpochStore;
import com.sangluo.onestep.feature.embedding.HiddenActivityViewHost;
import com.sangluo.onestep.feature.embedding.HostedDisplayRotationController;
import com.sangluo.onestep.feature.logging.SessionLogRecorder;
import com.sangluo.onestep.feature.tasks.RunningTaskAppResolver;
import com.sangluo.onestep.model.LauncherApp;
import com.sangluo.onestep.model.PinnedTaskState;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.system.root.ZygiskHookConfig;
import com.sangluo.onestep.system.ui.SystemUiController;
import com.sangluo.onestep.ui.background.BlurredBackgroundView;
import com.sangluo.onestep.ui.gesture.CornerTriggerGesturePolicy;
import com.sangluo.onestep.ui.settings.SettingsPanelController;
import com.sangluo.onestep.ui.topbar.TopPanelController;
import com.sangluo.onestep.ui.widget.AppShortcutView;
import com.sangluo.onestep.ui.widget.FixedViewportFrameLayout;
import com.sangluo.onestep.ui.widget.PagingHorizontalScrollView;
import com.sangluo.onestep.ui.window.AppLaunchPlacement;
import com.sangluo.onestep.ui.window.OneStepWindowView;
import com.sangluo.onestep.ui.window.SideWindowInputShieldController;
import com.sangluo.onestep.ui.window.WindowAnimationController;
import com.sangluo.onestep.ui.window.WindowLayoutCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.DEFAULT_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.DESKTOP_PAGE_COLUMNS;
import static com.sangluo.onestep.data.settings.OneStepSettings.DESKTOP_PAGE_ROWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.MAX_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SIZE_DEFAULT_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_CONTENT_HEIGHT_DP;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_VERTICAL_MARGIN_SCALE_DEFAULT;
import static com.sangluo.onestep.data.settings.OneStepSettings.canUseSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.isSupportedGridLayout;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeAllowedSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeCornerTriggerSensitivity;
import static com.sangluo.onestep.data.settings.OneStepSettings.oneStepTriggerAreaSizeDp;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppIconScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppStripSpacingScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopAppStripVerticalPaddingScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeOneStepTriggerAreaScale;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeTopNavVerticalMarginScale;

public class MainActivity extends Activity {
    private static final String TAG = "OneStep40";
    private static final String ACTION_OPLUS_SKIN_CHANGED =
            "oplus.intent.action.SKIN_CHANGED";
    private static final String ACTION_SMARTISAN_ICONS_CHANGED =
            "com.smartisanos.launcher.update_icon";
    private static final String ACTION_OVERLAY_CHANGED =
            "android.intent.action.OVERLAY_CHANGED";
    static final String EXTRA_SHOW_DESKTOP_HOME =
            "com.sangluo.onestep.extra.SHOW_DESKTOP_HOME";
    static final String EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED =
            "com.sangluo.onestep.extra.DEFAULT_DISPLAY_RELAY_ATTEMPTED";
    private static WeakReference<MainActivity> defaultDisplayInstance =
            new WeakReference<>(null);
    private static final int MAX_WINDOWS = MAX_SIDE_WINDOWS + 1;
    private static final int REQUEST_PICK_BACKGROUND = 42;
    private static final int REQUEST_EXPORT_LOG_STORAGE = 43;
    private static final int TOP_MEDIA_AREA_MIN_HEIGHT_DP = 116;
    private static final int TOP_MEDIA_PLAYER_HEIGHT_DP = 76;
    private static final long PIP_MONITOR_INTERVAL_MS = 450L;
    private static final long PIP_MONITOR_RETRY_INTERVAL_MS = 1200L;
    private static final long RUNNING_TASK_MONITOR_INTERVAL_MS = 500L;
    private static final long RUNNING_TASK_MONITOR_RETRY_INTERVAL_MS = 2000L;
    private static final long RUNNING_TASK_EVENT_REFRESH_DELAY_MS = 32L;
    private static final int RUNNING_TASK_QUERY_LIMIT = 256;
    private static final long SUPERSEDED_DISPLAY_RELEASE_GRACE_MS = 5000L;
    private static final int TOP_NAV_VERTICAL_SPACING_DEFAULT_DP = 20;
    private static final int TOP_BAR_HEIGHT_DEFAULT_DP = 74;
    private static final int TOP_NAV_BUTTON_SIZE_DP = 26;
    private static final int TOP_NAV_BUTTON_SPACING_DP = 8;
    private static final int TOP_NAV_ICON_SIZE_DP = 20;
    private static final int MEDIA_ROOT_COMMAND_TIMEOUT_SECONDS = 8;
    private static final String[] KERNEL_SU_MANAGER_PACKAGES = {
            "me.weishu.kernelsu",
            "com.rifsxd.ksunext"
    };
    private static final int EMBEDDED_START_RETRY_MS = 25;
    private static final int EMBEDDED_START_MAX_RETRIES = 120;
    private static final int WINDOW_FRAME_SWITCH_ANIMATION_MS = 200;
    private static final long WINDOW_SWITCH_IDLE_WARMUP_THRESHOLD_MS = 3000L;
    private static final int SIDE_DISMISS_DISTANCE_DP = 48;
    private static final int SIDE_DISMISS_SETTLE_MS = 180;
    private static final int WINDOW_SCALE_APPEAR_MS = 240;
    private static final float WINDOW_SCALE_APPEAR_START = 0.82f;
    private static final int MAIN_APP_REPLACE_FADE_OUT_MS = 160;
    private static final int CORNER_TRIGGER_DISTANCE_DEFAULT_DP = 36;
    private static final int CORNER_TRIGGER_PREVIEW_HIDE_DELAY_MS = 2000;
    private static final int EXIT_FULLSCREEN_LAYOUT_DELAY_MS = 180;
    private static final int EMBEDDED_LAYOUT_REFRESH_DELAY_MS = 320;
    private static final int VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX = 1080;
    private static final long POST_ANIMATION_NON_CRITICAL_WORK_DELAY_MS = 64L;
    private static final long CROSS_APP_ROUTE_RETRY_MS = 60L;
    private static final long DEFAULT_HOME_RESTORE_DELAY_MS = 80L;
    private static final long DEFAULT_NAVIGATION_FOCUS_RESTORE_DELAY_MS = 80L;
    private static final long BLOCKED_RECENTS_RESTORE_TIMEOUT_MS = 1000L;
    private static final int MAX_PENDING_CROSS_APP_ROUTES = 8;
    private static final int DEFERRED_MEDIA_SESSION_REFRESH = 1;
    private static final int DEFERRED_MEDIA_UI_REFRESH = 1 << 1;
    private static final int DEFERRED_PLAYLIST_REFRESH = 1 << 2;
    private static final int DEFERRED_TOP_COMPONENT_REFRESH = 1 << 3;
    // Honors an app's fullscreen orientation request without waiting for device rotation.
    // WindowManager.DisplayImePolicy is hidden on older SDKs; keep the stable value here.
    private static final String[] REQUIRED_EMBEDDING_PERMISSIONS = {
            "android.permission.ACTIVITY_EMBEDDING",
            "android.permission.MANAGE_ACTIVITY_TASKS",
            "android.permission.MANAGE_ACTIVITY_STACKS",
            "android.permission.INTERNAL_SYSTEM_WINDOW",
            "android.permission.REAL_GET_TASKS",
            "android.permission.START_ACTIVITIES_FROM_BACKGROUND",
            "android.permission.ADD_TRUSTED_DISPLAY",
            "android.permission.MEDIA_CONTENT_CONTROL",
            "android.permission.STATUS_BAR"
    };

    private final LauncherApp[] windowApps = new LauncherApp[MAX_WINDOWS];
    private final OneStepWindowView[] windowViews = new OneStepWindowView[MAX_WINDOWS];
    private final EmbeddedAppHost[] embeddedHosts = new EmbeddedAppHost[MAX_WINDOWS];
    private final int[] embeddedSyncGenerations = new int[MAX_WINDOWS];
    private final boolean[] embeddedSlotClosing = new boolean[MAX_WINDOWS];
    private final List<AppShortcutView> shortcutViews = new ArrayList<>();
    private final Set<String> taskBackedAppInstances = new HashSet<>();
    private final List<Integer> sideSlotOrder = new ArrayList<>();
    private final ArrayDeque<RoutedAppLaunch> pendingCrossAppRoutes = new ArrayDeque<>();
    private final Map<String, Intent> routedLaunchIntents = new HashMap<>();

    private List<LauncherApp> launcherApps = Collections.emptyList();
    private List<LauncherApp> orderedTopAppCandidates = Collections.emptyList();
    private List<LauncherApp> topAppStripApps = Collections.emptyList();
    private Set<String> selectedTopAppInstanceKeys = Collections.emptySet();
    private List<LauncherApp> builtInDesktopApps = Collections.emptyList();
    private LauncherApp builtInDesktopApp;
    private LauncherAppRepository launcherAppRepository;
    private final PersistentRootShell persistentRootShell = new PersistentRootShell();
    private boolean embeddingHintShown;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mediaRootExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService hookSettingsExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService displayImePolicyExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService sensorPolicyExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService visualEffectExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService wallpaperExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService pipDockExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService runningTaskExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService launcherIconExecutor = Executors.newSingleThreadExecutor();
    private final Object rootInputBridgeStartLock = new Object();
    private boolean launcherIconReceiverRegistered;
    private boolean launcherIconRefreshInFlight;
    private boolean launcherIconRefreshPending;
    private boolean completedFirstResume;
    private final BroadcastReceiver launcherIconChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            requestLauncherIconRefresh(intent == null ? "theme broadcast" : intent.getAction());
        }
    };
    private final Runnable refreshAllEmbeddedSlotLayoutsRunnable =
            this::refreshAllEmbeddedSlotLayouts;
    private final Runnable syncSideInputProtectionRunnable =
            this::syncSideInputProtection;
    private final Runnable cornerTriggerPreviewHideRunnable = this::hideCornerTriggerPreview;
    private final Runnable drainCrossAppRoutesRunnable = this::drainCrossAppRoutes;
    private final OneStepWindowView.Callbacks windowViewCallbacks =
            new OneStepWindowView.Callbacks() {
                @Override
                public View createDesktopHome() {
                    return builtInDesktopApp == null
                            ? MainActivity.this.createDesktopHome() : null;
                }

                @Override
                public void configureDesktopHomeViewport(OneStepWindowView windowView) {
                    MainActivity.this.configureDesktopHomeViewport(windowView);
                }

                @Override
                public void drawSharedBackground(Canvas canvas, View target) {
                    if (oneStepBackgroundView != null) {
                        oneStepBackgroundView.drawSharedBackground(canvas, target);
                    }
                }

                @Override
                public boolean isVerticalWindowLayout() {
                    return verticalWindowLayout;
                }

                @Override
                public int getSideDismissDirection() {
                    return MainActivity.this.getSideDismissDirection();
                }

                @Override
                public int getSideDismissDistancePx() {
                    return dp(SIDE_DISMISS_DISTANCE_DP);
                }

                @Override
                public boolean canDismissSlot(int slot) {
                    return slot >= 0 && slot < MAX_WINDOWS
                            && (windowApps[slot] != null || isInternalSettingsSlot(slot)
                            || isDesktopHomeSlot(slot));
                }

                @Override
                public boolean movedPastSideDismissThreshold(float dx, float dy) {
                    return MainActivity.this.movedPastSideDismissThreshold(dx, dy);
                }

                @Override
                public void dismissSideWindow(int slot) {
                    MainActivity.this.dismissSideWindow(slot);
                }

                @Override
                public void settleSideWindowBack(OneStepWindowView windowView) {
                    MainActivity.this.settleSideWindowBack(windowView);
                }

                @Override
                public boolean shouldRetainEmbeddedSurface(int slot) {
                    return slot >= 0 && slot < MAX_WINDOWS
                            && windowApps[slot] != null
                            && embeddedHosts[slot] instanceof RootVirtualDisplayHost;
                }
            };
    private final RootVirtualDisplayHost.Callbacks rootVirtualDisplayCallbacks =
            new RootVirtualDisplayHost.Callbacks() {
                @Override public LauncherApp[] windowApps() { return windowApps; }
                @Override public OneStepWindowView[] windowViews() { return windowViews; }
                @Override public boolean[] embeddedSlotClosing() { return embeddedSlotClosing; }
                @Override public Handler mainHandler() { return mainHandler; }
                @Override public ExecutorService displayImePolicyExecutor() {
                    return displayImePolicyExecutor;
                }
                @Override public ExecutorService sensorPolicyExecutor() {
                    return sensorPolicyExecutor;
                }
                @Override public PersistentRootShell persistentRootShell() {
                    return persistentRootShell;
                }
                @Override public EmbeddedStartEpochStore embeddedStartEpochStore() {
                    return embeddedStartEpochStore;
                }
                @Override public Object rootInputBridgeStartLock() {
                    return rootInputBridgeStartLock;
                }
                @Override public int embeddedStartEpoch() { return embeddedStartEpoch; }
                @Override public boolean shouldRunEmbeddedStart(int startEpoch) {
                    return MainActivity.this.shouldRunEmbeddedStart(startEpoch);
                }
                @Override public boolean isMainDisplaySlot(int slot) {
                    return MainActivity.this.isMainDisplaySlot(slot);
                }
                @Override public boolean isActivityDestroyed() { return activityDestroyed; }
                @Override public boolean isWindowFrameAnimationRunning() {
                    return isWindowAnimationRunning();
                }
                @Override public boolean isMultiWindowMode() { return multiWindowMode; }
                @Override public boolean isWindowSlotEnabled(int slot) {
                    return MainActivity.this.isWindowSlotEnabled(slot);
                }
                @Override public boolean suppressEmbeddedStarts() {
                    return suppressEmbeddedStarts;
                }
                @Override public View workspace() { return workspace; }
                @Override public int mainSlotSwitchPendingSlot() {
                    return mainSlotSwitchPendingSlot;
                }
                @Override public int activeMainSlot() { return activeMainSlot; }
                @Override public boolean claimStaleSensorUidOverrideRecovery() {
                    if (staleSensorUidOverridesRecoveryAttempted) {
                        return false;
                    }
                    staleSensorUidOverridesRecoveryAttempted = true;
                    return true;
                }
                @Override public int latestPhysicalLandscapeRotation() {
                    return getLatestPhysicalLandscapeRotation();
                }
                @Override public boolean hasGrantedSystemEmbeddingPermission() {
                    return MainActivity.this.hasGrantedSystemEmbeddingPermission();
                }
                @Override public boolean isSystemAppInstall() {
                    return MainActivity.this.isSystemAppInstall();
                }
                @Override public void showEmbeddingHint(String reason) {
                    showEmbeddingHintIfNeeded(reason);
                }
                @Override public void swapWithMain(int slot) {
                    MainActivity.this.swapWithMain(slot);
                }
                @Override public int dp(float value) { return MainActivity.this.dp(value); }
                @Override public Set<String> recordedSensorUidOverrides() {
                    return getRecordedSensorUidOverrides();
                }
                @Override public void recordSensorUidOverride(String packageName) {
                    MainActivity.this.recordSensorUidOverride(packageName);
                }
                @Override public void clearSensorUidOverrideRecord(String packageName) {
                    MainActivity.this.clearSensorUidOverrideRecord(packageName);
                }
                @Override public long rootInputBridgeLastStartUptime() {
                    return rootInputBridgeLastStartUptime;
                }
                @Override public void setRootInputBridgeLastStartUptime(long uptime) {
                    rootInputBridgeLastStartUptime = uptime;
                }
                @Override public boolean claimTrustedDisplayRoleSetup() {
                    if (trustedDisplayRoleSetupAttempted) {
                        return false;
                    }
                    trustedDisplayRoleSetupAttempted = true;
                    return true;
                }
                @Override public Rect[] calculateWindowRects() {
                    return MainActivity.this.calculateWindowRects();
                }
                @Override public Intent consumeRoutedLaunchIntent(
                        int slot, String packageName) {
                    return MainActivity.this.consumeRoutedLaunchIntent(slot, packageName);
                }
                @Override public boolean onCrossAppLaunch(
                        int sourceDisplayId, String sourcePackage,
                        Intent intent, String targetPackage) {
                    return MainActivity.this.onCrossAppLaunch(
                            sourceDisplayId, sourcePackage, intent, targetPackage);
                }
                @Override public void onSystemTaskEvent(
                        int event, int displayId, int taskId, String packageName,
                        String componentName) {
                    MainActivity.this.onSystemTaskEvent(
                            event, displayId, taskId, packageName, componentName);
                }
                @Override public void onHostedAppExitedAfterBack(
                        int slot, LauncherApp app, Runnable afterDesktopTakeover) {
                    MainActivity.this.onHostedAppExitedAfterBack(
                            slot, app, afterDesktopTakeover);
                }
            };
    private OneStepSettingsStore settingsStore;
    private EmbeddedStartEpochStore embeddedStartEpochStore;
    private FrameLayout workspace;
    private View screenContainerBackground;
    private FrameLayout rootContainer;
    private FrameLayout topChromeContainer;
    private LinearLayout topChromeContent;
    private LinearLayout topNavLeftControls;
    private LinearLayout topNavRightControls;
    private ImageView topNavPageLeftControl;
    private ImageView topNavPageRightControl;
    private ImageView topNavSettingsControl;
    private ImageView topNavExpandLeftControl;
    private ImageView topNavExpandRightControl;
    private BlurredBackgroundView oneStepBackgroundView;
    private HorizontalScrollView topAppStripScrollView;
    private View statusGestureShield;
    private View leftCornerTrigger;
    private View rightCornerTrigger;
    private FrameLayout cornerTriggerPreviewLayer;
    private View leftCornerTriggerPreview;
    private View rightCornerTriggerPreview;
    private volatile int activeMainSlot;
    private HostedDisplayRotationController hostedDisplayRotationController;
    private boolean mainOnLeft = true;
    private boolean multiWindowMode;
    private boolean exitOneStepPending;
    private boolean activityDestroyed;
    private boolean activityResumed;
    private boolean nonDefaultDisplayHomeRelay;
    private boolean embeddedResourcesReleased;
    private WindowAnimationController windowAnimationController;
    private boolean windowSwitchAnimationCritical;
    private int deferredWindowSwitchUiWork;
    private boolean staleSensorUidOverridesRecoveryAttempted;
    private int mainSlotSwitchGeneration;
    private int mainSlotSwitchPendingSlot = -1;
    private int mainSlotSwitchPendingOldSlot = -1;
    private int mainContentReplacementGeneration;
    private int mainContentReplacementPendingSlot = -1;
    private int desktopTakeoverGeneration;
    private int pendingMainAppStartSlot = -1;
    private LauncherApp pendingMainAppStart;
    private int pendingInternalSettingsSlot = -1;
    private int pendingDesktopHomeSlot = -1;
    private boolean desktopHomeRequestPending;
    private long rootInputBridgeLastStartUptime;
    private boolean trustedDisplayRoleSetupAttempted;
    private volatile boolean suppressEmbeddedStarts;
    private volatile int embeddedStartEpoch;
    private int desktopGridRows = DESKTOP_PAGE_ROWS;
    private int desktopGridColumns = DESKTOP_PAGE_COLUMNS;
    private int topAppIconScalePct = TOP_APP_ICON_SCALE_DEFAULT;
    private int topAppStripSpacingScalePct = TOP_APP_STRIP_SPACING_SCALE_DEFAULT;
    private int topAppStripVerticalPaddingScalePct =
            TOP_APP_STRIP_VERTICAL_PADDING_SCALE_DEFAULT;
    private boolean topComponentsVisible = true;
    private boolean statusBarSpacingEnabled;
    private boolean verticalWindowLayout;
    private int sideWindowCount = DEFAULT_SIDE_WINDOWS;
    private int topNavVerticalMarginScalePct = TOP_NAV_VERTICAL_MARGIN_SCALE_DEFAULT;
    private int oneStepTriggerAreaScalePct = ONE_STEP_TRIGGER_AREA_SCALE_DEFAULT;
    private int cornerTriggerSensitivityPct = CORNER_TRIGGER_SENSITIVITY_DEFAULT;
    private boolean logRecordingEnabled;
    private SystemUiController systemUiController;
    private SessionLogRecorder sessionLogRecorder;
    private SettingsPanelController settingsPanelController;
    private TopPanelController topPanelController;
    private SideWindowInputShieldController sideInputShieldController;
    private android.window.OnBackInvokedCallback systemBackCallback;
    private boolean pipMonitoringActive;
    private boolean pipQueryInFlight;
    private boolean pipDockInFlight;
    private boolean pipActive;
    private boolean pipDockApplied;
    private int pipTaskId = -1;
    private int pipMonitorGeneration;
    private boolean runningTaskMonitoringActive;
    private boolean runningTaskQueryInFlight;
    private boolean runningTaskRefreshPending;
    private boolean runningTaskQueryFailureLogged;
    private int runningTaskMonitorGeneration;
    private boolean defaultHomeRestorePending;
    private boolean blockedDefaultRecentsRestorePending;
    private boolean systemRecentsComponentResolved;
    private ComponentName systemRecentsComponent;
    private final Rect pipRestoreBounds = new Rect();
    private final Runnable flushDeferredWindowSwitchWorkRunnable =
            this::flushDeferredWindowSwitchWork;
    private final Runnable showDesktopHomeRunnable = this::showDesktopHomeInMain;
    private final Runnable restoreOneStepHomeRunnable = this::restoreOneStepHomeNow;
    private final Runnable clearBlockedDefaultRecentsRestoreRunnable =
            () -> blockedDefaultRecentsRestorePending = false;
    private final Runnable pipMonitorRunnable = this::queryPipStateAsync;
    private final Runnable pipDockBoundsUpdateRunnable = this::requestPipDockFromSlot;
    private final Runnable runningTaskMonitorRunnable = this::queryRunningTaskStatusesAsync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (isSystemAppInstall()) {
            setTheme(R.style.Theme_OneStep40_TransparentNavigation);
        }
        super.onCreate(savedInstanceState);
        int activityDisplayId = getActivityDisplayId();
        if (activityDisplayId != Display.DEFAULT_DISPLAY) {
            nonDefaultDisplayHomeRelay = true;
            Log.w(TAG, "Redirect HOME activity from virtual display " + activityDisplayId
                    + " to default display");
            redirectHomeToDefaultDisplay();
            return;
        }
        defaultDisplayInstance = new WeakReference<>(this);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window hostWindow = getWindow();
        hostWindow.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        hostWindow.setFormat(PixelFormat.OPAQUE);
        hostWindow.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            hostWindow.setStatusBarColor(Color.TRANSPARENT);
            hostWindow.setNavigationBarColor(
                    isSystemAppInstall() ? Color.TRANSPARENT : Color.BLACK);
        }

        loadOneStepSettings();
        if (logRecordingEnabled) {
            startSessionLogRecording();
        }
        settingsPanelController = createSettingsPanelController();
        topPanelController = createTopPanelController();
        windowAnimationController = createWindowAnimationController();
        initializeEmbeddedBridgeState();
        launcherAppRepository = new LauncherAppRepository(this);
        launcherApps = launcherAppRepository.loadLauncherApps();
        reconcileTopAppListConfiguration();
        loadBuiltInDesktopApps();
        setContentView(createDesktop());
        registerLauncherIconChangeReceiver();
        sideInputShieldController = new SideWindowInputShieldController(
                this, MAX_WINDOWS, new SideWindowInputShieldController.Callbacks() {
            @Override
            public boolean shouldShieldSlot(int slot) {
                return shouldShieldSideInput(slot);
            }

            @Override
            public OneStepWindowView windowView(int slot) {
                return slot >= 0 && slot < MAX_WINDOWS ? windowViews[slot] : null;
            }
        });
        // setContentView installs the decor and may refresh its default pixel format.
        // Keep the HOME task opaque so a system HOME transition cannot expose wallpaper.
        hostWindow.setFormat(PixelFormat.OPAQUE);
        hostWindow.getDecorView().setBackgroundColor(Color.BLACK);
        systemUiController = new SystemUiController(
                this, this::shouldHideStatusBarForOneStep,
                () -> activityDestroyed, this::isSystemAppInstall, this::handleSystemBack);
        getWindow().getDecorView().post(() -> Log.i(TAG, "Hardware acceleration: decor="
                + getWindow().getDecorView().isHardwareAccelerated()));
        applyStatusBarForCurrentMode();
        registerSystemBackCallback();
        logSystemPermissionState();
        createEmbeddedHosts();
        initializeHostedLandscapeOrientationForwarding();
        prewarmRootInputBridge();
        renderWindows();
        mainHandler.post(this::requestDesktopHomeInMain);
        initMediaMonitoring();
        initAmapNavigationMonitoring();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nonDefaultDisplayHomeRelay) {
            return;
        }
        boolean returningToForeground = !activityResumed;
        activityResumed = true;
        startRunningTaskMonitoring();
        if (returningToForeground && completedFirstResume) {
            requestLauncherIconRefresh("returned to foreground");
        }
        completedFirstResume = true;
        suppressEmbeddedStarts = false;
        if (returningToForeground && systemUiController != null) {
            systemUiController.invalidateAppliedState();
        }
        applyStatusBarForCurrentMode();
        hostedDisplayRotationController.enable();
        resumeMediaMonitoring();
        startPipMonitoring();
        scheduleSideInputProtectionSync();
        scheduleDefaultNavigationFocusRestore("OneStep resumed");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!nonDefaultDisplayHomeRelay) {
            requestLauncherIconRefresh("configuration changed");
        }
    }

    private void registerLauncherIconChangeReceiver() {
        if (launcherIconReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(ACTION_OVERLAY_CHANGED);
        filter.addAction(ACTION_OPLUS_SKIN_CHANGED);
        filter.addAction(ACTION_SMARTISAN_ICONS_CHANGED);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(launcherIconChangeReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(launcherIconChangeReceiver, filter);
            }
            launcherIconReceiverRegistered = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to register theme icon receiver", e);
        }
    }

    private void unregisterLauncherIconChangeReceiver() {
        if (!launcherIconReceiverRegistered) {
            return;
        }
        try {
            unregisterReceiver(launcherIconChangeReceiver);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to unregister theme icon receiver", e);
        }
        launcherIconReceiverRegistered = false;
    }

    private void requestLauncherIconRefresh(String reason) {
        if (activityDestroyed || nonDefaultDisplayHomeRelay || launcherAppRepository == null) {
            return;
        }
        if (launcherIconRefreshInFlight) {
            launcherIconRefreshPending = true;
            return;
        }
        launcherIconRefreshInFlight = true;
        try {
            launcherIconExecutor.execute(() -> {
                List<LauncherApp> refreshedApps = null;
                List<LauncherApp> refreshedDesktopApps = null;
                RuntimeException loadError = null;
                try {
                    refreshedApps = launcherAppRepository.refreshLauncherApps();
                    refreshedDesktopApps = launcherAppRepository.loadHomeApps();
                } catch (RuntimeException e) {
                    loadError = e;
                }
                List<LauncherApp> result = refreshedApps;
                List<LauncherApp> desktopResult = refreshedDesktopApps;
                RuntimeException error = loadError;
                mainHandler.post(() -> finishLauncherIconRefresh(
                        reason, result, desktopResult, error));
            });
        } catch (RuntimeException e) {
            launcherIconRefreshInFlight = false;
            Log.w(TAG, "Unable to schedule themed icon refresh", e);
        }
    }

    private void finishLauncherIconRefresh(
            String reason, List<LauncherApp> refreshedApps,
            List<LauncherApp> refreshedDesktopApps, RuntimeException error) {
        launcherIconRefreshInFlight = false;
        if (!activityDestroyed && error == null && refreshedApps != null) {
            applyRefreshedLauncherApps(refreshedApps);
            applyRefreshedBuiltInDesktopApps(refreshedDesktopApps);
            Log.i(TAG, "Reloaded system themed icons: reason=" + reason
                    + ", count=" + refreshedApps.size());
        } else if (error != null) {
            Log.w(TAG, "Reloading system themed icons failed: reason=" + reason, error);
        }
        if (launcherIconRefreshPending && !activityDestroyed) {
            launcherIconRefreshPending = false;
            requestLauncherIconRefresh("coalesced theme change");
        }
    }

    private void applyRefreshedLauncherApps(List<LauncherApp> refreshedApps) {
        boolean structureChanged = launcherApps.size() != refreshedApps.size();
        if (!structureChanged) {
            for (int i = 0; i < launcherApps.size(); i++) {
                LauncherApp oldApp = launcherApps.get(i);
                LauncherApp newApp = refreshedApps.get(i);
                if (!oldApp.isSameInstance(newApp)
                        || !TextUtils.equals(oldApp.label, newApp.label)) {
                    structureChanged = true;
                    break;
                }
            }
        }

        launcherApps = refreshedApps;
        reconcileTopAppListConfiguration();
        Map<String, LauncherApp> refreshedByInstance = new HashMap<>();
        Map<String, LauncherApp> refreshedByPackage = new HashMap<>();
        for (LauncherApp app : refreshedApps) {
            refreshedByInstance.put(app.instanceKey(), app);
            if (app.isCurrentUser()) {
                refreshedByPackage.putIfAbsent(app.packageName, app);
            }
        }
        for (int slot = 0; slot < windowApps.length; slot++) {
            LauncherApp current = windowApps[slot];
            if (current == null) {
                continue;
            }
            if (builtInDesktopApp != null
                    && builtInDesktopApp.componentName.equals(current.componentName)) {
                continue;
            }
            LauncherApp refreshed = refreshedByInstance.get(current.instanceKey());
            if (refreshed == null && current.isCurrentUser()) {
                refreshed = refreshedByPackage.get(current.packageName);
            }
            if (refreshed != null) {
                windowApps[slot] = refreshed;
            }
        }

        if (structureChanged) {
            rebuildTopChromeContent();
        } else {
            for (AppShortcutView shortcut : shortcutViews) {
                LauncherApp app = refreshedByInstance.get(shortcut.getInstanceKeyValue());
                if (app != null) {
                    shortcut.bind(app);
                }
            }
        }
        if (topPanelController != null) {
            topPanelController.refreshAppIcons();
        }
    }

    private void applyRefreshedBuiltInDesktopApps(List<LauncherApp> refreshedApps) {
        if (refreshedApps == null) {
            return;
        }
        builtInDesktopApps = refreshedApps;
        ComponentName selectedComponent = settingsStore.getBuiltInDesktopComponent();
        LauncherApp selected = findAppByComponent(refreshedApps, selectedComponent);
        if (selected == null && !refreshedApps.isEmpty()) {
            selected = refreshedApps.get(0);
            settingsStore.saveBuiltInDesktopComponent(selected.componentName);
        }
        builtInDesktopApp = selected;
        if (selected != null) {
            for (int slot = 0; slot < windowApps.length; slot++) {
                LauncherApp current = windowApps[slot];
                if (current != null && selected.componentName.equals(current.componentName)) {
                    windowApps[slot] = selected;
                }
            }
        }
        updateSettingsPageViews();
        renderWindows();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (nonDefaultDisplayHomeRelay) {
            redirectHomeToDefaultDisplay();
            return;
        }
        boolean desktopHomeRequest = isDesktopHomeRequestIntent(intent);
        if (desktopHomeRequest && activityResumed) {
            suppressEmbeddedStarts = false;
            requestDesktopHomeInMain();
            return;
        }
        if (desktopHomeRequest) {
            overridePendingTransition(0, 0);
        }
        suppressEmbeddedStarts = false;
        applyStatusBarForCurrentMode();
        hostedDisplayRotationController.enable();
        resumeMediaMonitoring();
        startPipMonitoring();
        if (desktopHomeRequest) {
            mainHandler.post(this::requestDesktopHomeInMain);
        }
    }

    private boolean isDesktopHomeRequestIntent(Intent intent) {
        return isSystemHomeIntent(intent)
                || (intent != null && intent.getBooleanExtra(EXTRA_SHOW_DESKTOP_HOME, false));
    }

    private boolean isSystemHomeIntent(Intent intent) {
        return intent != null && TextUtils.equals(Intent.ACTION_MAIN, intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_HOME);
    }

    @Override
    protected void onPause() {
        if (nonDefaultDisplayHomeRelay) {
            super.onPause();
            return;
        }
        restoreDefaultDisplayFocus("OneStep paused");
        activityResumed = false;
        stopRunningTaskMonitoring();
        pauseMediaMonitoring();
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (nonDefaultDisplayHomeRelay) {
            super.onStop();
            return;
        }
        hostedDisplayRotationController.disable();
        stopAllHostedSensorLandscapeRotations("OneStep stopped");
        hostedDisplayRotationController.clearLatestRotation();
        pauseMediaMonitoring();
        stopPipMonitoring(true);
        suspendWindowInputRouting();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyStatusBarForCurrentMode();
            scheduleSideInputProtectionSync();
        }
    }

    private void initializeHostedLandscapeOrientationForwarding() {
        hostedDisplayRotationController = new HostedDisplayRotationController(
                this, mainHandler, new HostedDisplayRotationController.Listener() {
            @Override
            public void onHostedDisplayChanged(int displayId) {
                RootVirtualDisplayHost host = findRootVirtualDisplayHost(displayId);
                if (host != null) {
                    host.onHostedDisplayRotationChanged();
                }
            }

            @Override
            public void onPhysicalLandscapeRotationChanged(int rotation) {
                dispatchPhysicalLandscapeRotationToHostedDisplays(rotation);
            }
        });
        hostedDisplayRotationController.register();
    }

    private void dispatchPhysicalLandscapeRotationToHostedDisplays(int targetRotation) {
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost) {
                ((RootVirtualDisplayHost) host).onPhysicalLandscapeRotationChanged(
                        targetRotation);
            }
        }
    }

    private void refreshAllHostedSensorLandscapeRotations() {
        if (getLatestPhysicalLandscapeRotation() < 0) {
            return;
        }
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost) {
                ((RootVirtualDisplayHost) host).onWindowSwitchSettledForSensorRotation();
            }
        }
    }

    private int getLatestPhysicalLandscapeRotation() {
        return hostedDisplayRotationController == null
                ? -1 : hostedDisplayRotationController.getLatestLandscapeRotation();
    }

    private void stopAllHostedSensorLandscapeRotations(String reason) {
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost) {
                ((RootVirtualDisplayHost) host).stopSensorLandscapeRotationAsync(reason);
            }
        }
    }

    private RootVirtualDisplayHost findRootVirtualDisplayHost(int targetDisplayId) {
        if (targetDisplayId <= Display.DEFAULT_DISPLAY) {
            return null;
        }
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost
                    && ((RootVirtualDisplayHost) host).getDisplayId() == targetDisplayId) {
                return (RootVirtualDisplayHost) host;
            }
        }
        return null;
    }

    private void applyStatusBarForCurrentMode() {
        if (systemUiController != null) {
            systemUiController.apply();
        }
    }

    private void restoreDefaultDisplayFocus(String reason) {
        RootVirtualDisplayHost activeHost = activeMainSlot >= 0
                && activeMainSlot < embeddedHosts.length
                && embeddedHosts[activeMainSlot] instanceof RootVirtualDisplayHost
                ? (RootVirtualDisplayHost) embeddedHosts[activeMainSlot] : null;
        if (activeHost != null && activeHost.focusDefaultDisplayForSystemNavigation(reason)) {
            return;
        }
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost && host != activeHost
                    && ((RootVirtualDisplayHost) host)
                    .focusDefaultDisplayForSystemNavigation(reason)) {
                return;
            }
        }
    }

    private int getActivityDisplayId() {
        Display display = getWindowManager().getDefaultDisplay();
        return display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
    }

    private void redirectHomeToDefaultDisplay() {
        MainActivity defaultActivity = defaultDisplayInstance.get();
        if (defaultActivity != null && !defaultActivity.activityDestroyed
                && defaultActivity != this) {
            defaultActivity.restoreDefaultDisplayFocus("virtual HOME redirected");
            defaultActivity.suppressEmbeddedStarts = false;
            defaultActivity.requestDesktopHomeInMain();
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, false)) {
            Log.e(TAG, "Stop repeated HOME redirect because no default-display instance exists");
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        Intent redirectIntent = new Intent(this, HomeRedirectActivity.class)
                .putExtra(EXTRA_SHOW_DESKTOP_HOME, true)
                .putExtra(EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                startActivity(redirectIntent, options.toBundle());
            } else {
                startActivity(redirectIntent);
            }
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Unable to redirect HOME to default display", e);
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private void onSystemTaskEvent(
            int event, int displayId, int taskId, String packageName, String componentName) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            handleSystemTaskEvent(event, displayId, taskId, packageName, componentName);
            return;
        }
        CountDownLatch handled = new CountDownLatch(1);
        mainHandler.post(() -> {
            try {
                handleSystemTaskEvent(event, displayId, taskId, packageName, componentName);
            } finally {
                handled.countDown();
            }
        });
        try {
            handled.await(120L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void handleSystemTaskEvent(
            int event, int displayId, int taskId, String packageName, String componentName) {
        requestRunningTaskStatusRefresh();
        if (activityDestroyed || activeMainSlot < 0 || activeMainSlot >= MAX_WINDOWS) {
            return;
        }
        LauncherApp currentMainApp = windowApps[activeMainSlot];
        EmbeddedAppHost activeHost = embeddedHosts[activeMainSlot];
        RootVirtualDisplayHost rootHost = activeHost instanceof RootVirtualDisplayHost
                ? (RootVirtualDisplayHost) activeHost : null;
        boolean systemRecentsMovedToFront = event
                == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && isSystemRecentsTask(packageName, componentName);
        if (systemRecentsMovedToFront) {
            if (displayId == Display.DEFAULT_DISPLAY && !isOneStepDefaultHome()) {
                cancelPendingOneStepHomeRestore();
                Log.i(TAG, "Leave default-display recents to the configured system HOME");
                return;
            }
            boolean mainShowsHostedDesktop = rootHost != null
                    && currentMainApp != null && currentMainApp.isHomeEntry();
            if (displayId == Display.DEFAULT_DISPLAY) {
                handleDefaultDisplaySystemRecents(
                        rootHost, taskId, componentName, mainShowsHostedDesktop);
                return;
            }
            RootVirtualDisplayHost recentsHost = findRootVirtualDisplayHost(displayId);
            if (mainShowsHostedDesktop && displayId == rootHost.getDisplayId()) {
                return;
            }
            if (mainShowsHostedDesktop
                    && rootHost.moveSystemTaskToHostedDisplay(taskId, componentName)) {
                Log.i(TAG, "Moved system recents from display " + displayId
                        + " into main desktop display " + rootHost.getDisplayId());
                return;
            }
            Log.i(TAG, "Dismiss system recents outside main desktop: display=" + displayId
                    + ", mainDesktop=" + mainShowsHostedDesktop);
            if (recentsHost != null) {
                recentsHost.dismissHostedSystemRecents();
            }
            scheduleDefaultNavigationFocusRestore("hosted recents dismissed");
            return;
        }
        if (event == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && displayId > Display.DEFAULT_DISPLAY
                && findRootVirtualDisplayHost(displayId) != null) {
            scheduleDefaultNavigationFocusRestore("hosted task moved to front");
        }
        boolean oneStepHomeMovedToFront = event
                == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && displayId == Display.DEFAULT_DISPLAY
                && TextUtils.equals(packageName, getPackageName());
        if (oneStepHomeMovedToFront) {
            if (blockedDefaultRecentsRestorePending) {
                blockedDefaultRecentsRestorePending = false;
                mainHandler.removeCallbacks(clearBlockedDefaultRecentsRestoreRunnable);
                Log.i(TAG, "Keep current main content after blocking default-display recents");
                scheduleDefaultNavigationFocusRestore("default-display recents blocked");
                return;
            }
            Log.i(TAG, "Route default-display HOME through OneStep containers");
            requestDesktopHomeInMain();
            return;
        }
        boolean systemDesktopMovedToDefaultDisplay = event
                == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && displayId == Display.DEFAULT_DISPLAY
                && isBuiltInDesktopHomeTask(packageName, componentName);
        if (systemDesktopMovedToDefaultDisplay) {
            if (!isOneStepDefaultHome()) {
                cancelPendingOneStepHomeRestore();
                Log.i(TAG, "Keep configured system HOME in front: " + componentName);
                return;
            }
            Log.i(TAG, "Restore OneStep after system HOME moved desktop to default display: "
                    + componentName);
            bringOneStepHomeToFront();
            return;
        }
        if (rootHost == null) {
            return;
        }
        if (displayId > Display.DEFAULT_DISPLAY
                && rootHost.getDisplayId() != displayId) {
            return;
        }
        LauncherApp currentApp = currentMainApp;
        if (currentApp == null || currentApp.isHomeEntry()) {
            return;
        }
        if (event == RootVirtualDisplayBridge.TASK_EVENT_STACK_CHANGED) {
            rootHost.checkHostedTaskAfterSystemTaskChange();
            return;
        }
        boolean hostedTaskRemovalStarted = event
                == RootVirtualDisplayBridge.TASK_EVENT_REMOVAL_STARTED
                && (TextUtils.equals(currentApp.packageName, packageName)
                || (TextUtils.isEmpty(packageName)
                && rootHost.matchesHostedTask(currentApp, taskId)));
        boolean systemDesktopMovedToFront = event
                == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && displayId == rootHost.getDisplayId()
                && isBuiltInDesktopHomeTask(packageName, componentName);
        if (systemDesktopMovedToFront && rootHost.isHostedSurfaceRevealPending(currentApp)) {
            Log.i(TAG, "Keep secondary desktop concealed while hosted app is launching: slot="
                    + activeMainSlot + ", app=" + currentApp.packageName);
            return;
        }
        boolean hostedTaskMovedToFront = event
                == RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT
                && displayId == rootHost.getDisplayId()
                && !TextUtils.equals(packageName, getPackageName())
                && !systemDesktopMovedToFront;
        if (hostedTaskMovedToFront
                && rootHost.onHostedTaskMovedToFront(
                currentApp, taskId, packageName)) {
            return;
        }
        if (hostedTaskRemovalStarted) {
            Log.i(TAG, "Take over desktop when hosted task removal starts: slot="
                    + activeMainSlot + ", task=" + taskId
                    + ", app=" + currentApp.packageName);
            onHostedAppExitedAfterBack(activeMainSlot, currentApp, null);
            return;
        }
        if (systemDesktopMovedToFront) {
            handleHostedHomeRequest(activeMainSlot, currentApp, rootHost);
            return;
        }
        if (displayId <= Display.DEFAULT_DISPLAY) {
            rootHost.checkHostedTaskAfterSystemTaskChange();
        }
    }

    private void handleHostedHomeRequest(
            int sourceSlot, LauncherApp currentApp, RootVirtualDisplayHost sourceHost) {
        LauncherApp selectedDesktop = resolveBuiltInDesktopApp();
        int existingDesktopSlot = selectedDesktop == null
                ? -1 : findSlotByComponent(selectedDesktop.componentName);
        boolean canKeepCurrentApp = findEmptySideSlot() >= 0
                || (existingDesktopSlot >= 0 && existingDesktopSlot != sourceSlot
                && !embeddedSlotClosing[existingDesktopSlot]);

        Log.i(TAG, "Route hosted HOME through OneStep containers: sourceSlot="
                + sourceSlot + ", app=" + currentApp.packageName
                + ", keepCurrent=" + canKeepCurrentApp
                + ", existingDesktopSlot=" + existingDesktopSlot);
        if (canKeepCurrentApp) {
            // HOME has already put the launcher task above this app on its display.
            // Bring the app back before that display becomes a side container.
            if (!sourceHost.restart(currentApp)) {
                sourceHost.concealHostedSurfaceForDesktopTakeover(currentApp);
            }
        } else {
            sourceHost.concealHostedSurfaceForDesktopTakeover(currentApp);
        }
        requestDesktopHomeInMain();
    }

    private void bringOneStepHomeToFront() {
        if (!isOneStepDefaultHome()) {
            cancelPendingOneStepHomeRestore();
            return;
        }
        if (defaultHomeRestorePending) {
            return;
        }
        defaultHomeRestorePending = true;
        Log.i(TAG, "Restore OneStep after system HOME moved desktop to default display");
        mainHandler.postDelayed(restoreOneStepHomeRunnable, DEFAULT_HOME_RESTORE_DELAY_MS);
    }

    private void handleDefaultDisplaySystemRecents(
            RootVirtualDisplayHost rootHost, int taskId, String componentName,
            boolean moveIntoHostedDesktop) {
        if (!isOneStepDefaultHome()) {
            cancelPendingOneStepHomeRestore();
            Log.i(TAG, "Do not intercept default-display recents while OneStep is not HOME");
            return;
        }
        if (blockedDefaultRecentsRestorePending) {
            return;
        }
        blockedDefaultRecentsRestorePending = true;
        mainHandler.removeCallbacks(clearBlockedDefaultRecentsRestoreRunnable);
        mainHandler.postDelayed(clearBlockedDefaultRecentsRestoreRunnable,
                BLOCKED_RECENTS_RESTORE_TIMEOUT_MS);
        if (moveIntoHostedDesktop && rootHost != null
                && rootHost.moveSystemTaskToHostedDisplay(taskId, componentName)) {
            Log.i(TAG, "Moved system recents into hosted desktop: task=" + taskId
                    + ", display=" + rootHost.getDisplayId());
            return;
        }
        Log.i(TAG, moveIntoHostedDesktop
                ? "Block default-display recents because task migration failed"
                : "Block default-display recents because main content is not desktop");
        Intent restoreIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                startActivity(restoreIntent, options.toBundle());
            } else {
                startActivity(restoreIntent);
            }
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException | SecurityException e) {
            blockedDefaultRecentsRestorePending = false;
            mainHandler.removeCallbacks(clearBlockedDefaultRecentsRestoreRunnable);
            Log.e(TAG, "Unable to restore OneStep after blocking system recents", e);
        }
    }

    private void scheduleDefaultNavigationFocusRestore(String reason) {
        mainHandler.postDelayed(() -> {
            if (!activityDestroyed) {
                restoreDefaultDisplayFocus(reason);
            }
        }, DEFAULT_NAVIGATION_FOCUS_RESTORE_DELAY_MS);
    }

    private void restoreOneStepHomeNow() {
        defaultHomeRestorePending = false;
        if (activityDestroyed) {
            return;
        }
        if (!isOneStepDefaultHome()) {
            Log.i(TAG, "Cancel OneStep HOME restore because the default HOME changed");
            return;
        }
        Intent homeIntent = new Intent(this, MainActivity.class)
                .putExtra(EXTRA_SHOW_DESKTOP_HOME, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY);
                startActivity(homeIntent, options.toBundle());
            } else {
                startActivity(homeIntent);
            }
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "Unable to restore OneStep after system HOME", e);
            requestDesktopHomeInMain();
        }
    }

    private void cancelPendingOneStepHomeRestore() {
        if (!defaultHomeRestorePending) {
            return;
        }
        defaultHomeRestorePending = false;
        mainHandler.removeCallbacks(restoreOneStepHomeRunnable);
    }

    private boolean isOneStepDefaultHome() {
        try {
            ComponentName defaultHome = new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .resolveActivity(getPackageManager());
            String defaultHomePackage = defaultHome == null
                    ? null : defaultHome.getPackageName();
            return DefaultHomeRoutingPolicy.shouldInterceptSystemHome(
                    getPackageName(), defaultHomePackage);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean isBuiltInDesktopPackage(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return false;
        }
        if (builtInDesktopApp != null
                && TextUtils.equals(builtInDesktopApp.packageName, packageName)) {
            return true;
        }
        for (LauncherApp desktopApp : builtInDesktopApps) {
            if (desktopApp != null
                    && TextUtils.equals(desktopApp.packageName, packageName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBuiltInDesktopHomeTask(String packageName, String componentName) {
        if (!isBuiltInDesktopPackage(packageName) || TextUtils.isEmpty(componentName)) {
            return false;
        }
        ComponentName taskComponent = ComponentName.unflattenFromString(componentName);
        if (taskComponent == null) {
            return false;
        }
        if (builtInDesktopApp != null
                && taskComponent.equals(builtInDesktopApp.componentName)) {
            return true;
        }
        for (LauncherApp desktopApp : builtInDesktopApps) {
            if (desktopApp != null && taskComponent.equals(desktopApp.componentName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemRecentsTask(String packageName, String componentName) {
        if (TextUtils.isEmpty(componentName)) {
            return false;
        }
        ComponentName taskComponent = ComponentName.unflattenFromString(componentName);
        if (taskComponent == null) {
            return false;
        }
        ComponentName configuredRecents = resolveSystemRecentsComponent();
        if (configuredRecents != null) {
            return configuredRecents.equals(taskComponent);
        }
        return isBuiltInDesktopPackage(packageName)
                && taskComponent.getClassName().endsWith("RecentsActivity");
    }

    private ComponentName resolveSystemRecentsComponent() {
        if (systemRecentsComponentResolved) {
            return systemRecentsComponent;
        }
        systemRecentsComponentResolved = true;
        try {
            Resources systemResources = Resources.getSystem();
            int resourceId = systemResources.getIdentifier(
                    "config_recentsComponentName", "string", "android");
            if (resourceId != 0) {
                systemRecentsComponent = ComponentName.unflattenFromString(
                        systemResources.getString(resourceId));
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to resolve system recents component: "
                    + e.getClass().getSimpleName());
        }
        return systemRecentsComponent;
    }

    private boolean shouldHideStatusBarForOneStep() {
        return multiWindowMode && !exitOneStepPending && !statusBarSpacingEnabled;
    }

    private synchronized Set<String> getRecordedSensorUidOverrides() {
        return settingsStore.getSensorUidOverrides();
    }

    private synchronized void recordSensorUidOverride(String packageName) {
        settingsStore.recordSensorUidOverride(packageName);
    }

    private synchronized void clearSensorUidOverrideRecord(String packageName) {
        settingsStore.clearSensorUidOverride(packageName);
    }

    @Override
    protected void onDestroy() {
        activityDestroyed = true;
        if (sessionLogRecorder != null) {
            sessionLogRecorder.close();
            sessionLogRecorder = null;
        }
        MainActivity replacementActivity = defaultDisplayInstance.get();
        boolean supersededOnDefaultDisplay = replacementActivity != null
                && replacementActivity != this
                && !replacementActivity.activityDestroyed;
        if (replacementActivity == this) {
            defaultDisplayInstance.clear();
        }
        if (nonDefaultDisplayHomeRelay) {
            launcherIconExecutor.shutdownNow();
            mediaRootExecutor.shutdownNow();
            hookSettingsExecutor.shutdownNow();
            visualEffectExecutor.shutdownNow();
            wallpaperExecutor.shutdownNow();
            pipDockExecutor.shutdownNow();
            runningTaskExecutor.shutdownNow();
            releaseEmbeddedResources();
            super.onDestroy();
            return;
        }
        restoreDefaultDisplayFocus("OneStep destroyed");
        unregisterLauncherIconChangeReceiver();
        cancelWindowSurfaceAnimation();
        if (hostedDisplayRotationController != null) {
            hostedDisplayRotationController.close();
        }
        if (systemUiController != null) {
            systemUiController.close();
            systemUiController = null;
        }
        mainHandler.removeCallbacks(cornerTriggerPreviewHideRunnable);
        mainHandler.removeCallbacks(drainCrossAppRoutesRunnable);
        mainHandler.removeCallbacks(flushDeferredWindowSwitchWorkRunnable);
        mainHandler.removeCallbacks(showDesktopHomeRunnable);
        mainHandler.removeCallbacks(restoreOneStepHomeRunnable);
        mainHandler.removeCallbacks(clearBlockedDefaultRecentsRestoreRunnable);
        mainHandler.removeCallbacks(pipMonitorRunnable);
        mainHandler.removeCallbacks(pipDockBoundsUpdateRunnable);
        mainHandler.removeCallbacks(runningTaskMonitorRunnable);
        mainHandler.removeCallbacks(syncSideInputProtectionRunnable);
        if (sideInputShieldController != null) {
            sideInputShieldController.release();
            sideInputShieldController = null;
        }
        windowSwitchAnimationCritical = false;
        deferredWindowSwitchUiWork = 0;
        pendingCrossAppRoutes.clear();
        routedLaunchIntents.clear();
        pauseMediaMonitoring();
        if (!supersededOnDefaultDisplay && (isFinishing() || exitOneStepPending)) {
            backgroundOpenedApps();
        }
        if (topPanelController != null) {
            topPanelController.close();
            topPanelController = null;
        }
        mediaRootExecutor.shutdownNow();
        hookSettingsExecutor.shutdownNow();
        launcherIconExecutor.shutdownNow();
        visualEffectExecutor.shutdownNow();
        wallpaperExecutor.shutdownNow();
        pipDockExecutor.shutdown();
        runningTaskExecutor.shutdownNow();
        if (supersededOnDefaultDisplay) {
            Log.w(TAG, "Keep superseded virtual displays for "
                    + SUPERSEDED_DISPLAY_RELEASE_GRACE_MS
                    + "ms while Android completes the pending HOME dispatch");
            mainHandler.postDelayed(() -> releaseEmbeddedResources(true),
                    SUPERSEDED_DISPLAY_RELEASE_GRACE_MS);
        } else {
            releaseEmbeddedResources();
        }
        unregisterSystemBackCallback();
        super.onDestroy();
    }

    private void releaseEmbeddedResources() {
        releaseEmbeddedResources(false);
    }

    private void releaseEmbeddedResources(boolean supersededByReplacement) {
        if (embeddedResourcesReleased) {
            return;
        }
        embeddedResourcesReleased = true;
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host != null) {
                if (supersededByReplacement && host instanceof RootVirtualDisplayHost) {
                    ((RootVirtualDisplayHost) host).releaseForActivityReplacement();
                } else {
                    host.release();
                }
            }
        }
        displayImePolicyExecutor.shutdownNow();
        try {
            sensorPolicyExecutor.execute(persistentRootShell::close);
            sensorPolicyExecutor.shutdown();
        } catch (RuntimeException e) {
            sensorPolicyExecutor.shutdownNow();
            persistentRootShell.close();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_BACKGROUND && resultCode == RESULT_OK && data != null) {
            saveSelectedBackground(data.getData(), data.getFlags());
        }
    }

    private void exportSessionLog() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_EXPORT_LOG_STORAGE);
            return;
        }
        performSessionLogExport();
    }

    private void performSessionLogExport() {
        SessionLogRecorder recorder = sessionLogRecorder;
        if (recorder == null) {
            Toast.makeText(this, "日志记录器未启动", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean accepted = recorder.export(result -> mainHandler.post(() -> {
            if (activityDestroyed) {
                return;
            }
            String message = result.isSuccessful()
                    ? "日志已导出：Download/" + result.getFileName()
                    : "日志导出失败：" + result.getErrorMessage();
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }));
        Toast.makeText(this, accepted ? "正在导出日志…" : "日志正在导出",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_EXPORT_LOG_STORAGE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            performSessionLogExport();
        } else {
            Toast.makeText(this, "未获得存储权限，无法导出日志", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("GestureBackNavigation")
    @Override
    public void onBackPressed() {
        handleSystemBack();
    }

    private void registerSystemBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || systemBackCallback != null) {
            return;
        }
        systemBackCallback = this::handleSystemBack;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, systemBackCallback);
    }

    private void unregisterSystemBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || systemBackCallback == null) {
            return;
        }
        try {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(systemBackCallback);
        } catch (IllegalArgumentException ignored) {
        }
        systemBackCallback = null;
    }

    @SuppressLint("MissingPermission")
    private View createDesktop() {
        FrameLayout root = new FrameLayout(this);
        rootContainer = root;
        root.setBackground(makeWallpaperFallback());
        try {
            root.setBackground(WallpaperManager.getInstance(this).getDrawable());
        } catch (RuntimeException ignored) {
            root.setBackground(makeWallpaperFallback());
        }

        oneStepBackgroundView = new BlurredBackgroundView(
                this,
                visualEffectExecutor,
                this::loadCurrentListBackgroundDrawable,
                () -> windowSwitchAnimationCritical,
                () -> activityDestroyed,
                this::invalidateWindowPlaceholderBackgrounds);
        root.addView(oneStepBackgroundView, matchFrame());

        workspace = new FrameLayout(this);
        workspace.setBackgroundColor(Color.TRANSPARENT);
        workspace.setClipChildren(true);
        workspace.setClipToPadding(true);
        root.addView(workspace, matchFrame());

        screenContainerBackground = new View(this);
        screenContainerBackground.setBackgroundColor(Color.BLACK);
        workspace.addView(screenContainerBackground, matchFrame());

        topChromeContainer = new FrameLayout(this);
        topChromeContent = new LinearLayout(this);
        topChromeContent.setOrientation(LinearLayout.VERTICAL);
        topChromeContent.setPadding(0, getStatusBarSpacingHeight(), 0, 0);
        topChromeContainer.addView(topChromeContent, matchFrame());
        topChromeContent.addView(createTopMediaArea(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopMediaAreaHeight()));
        topChromeContent.addView(createTopNavigationBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopNavHeight()));
        topChromeContent.addView(createTopAppStrip(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopAppStripHeight()));
        FrameLayout.LayoutParams topChromeLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopChromeHeight(), Gravity.TOP);
        root.addView(topChromeContainer, topChromeLp);

        activeMainSlot = 0;
        multiWindowMode = false;
        initializeSideSlotOrder();

        for (int i = 0; i < MAX_WINDOWS; i++) {
            final int slot = i;
            windowViews[i] = new OneStepWindowView(
                    this, i == activeMainSlot, makeWindowPlaceholderBorder(), windowViewCallbacks);
            windowViews[i].setOnClickListener(v -> {
                if (!isMainDisplaySlot(slot)) {
                    swapWithMain(slot);
                }
            });
            windowViews[i].setOnLongClickListener(v -> {
                if (isMainDisplaySlot(slot)) {
                    syncEmbeddedSlot(slot);
                } else {
                    swapWithMain(slot);
                }
                return true;
            });
        }

        setTopChromeVisible(false, false);
        applyWindowLayout(false);
        addCornerTriggers(root);
        return root;
    }

    private void initializeSideSlotOrder() {
        sideSlotOrder.clear();
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (slot != activeMainSlot) {
                sideSlotOrder.add(slot);
            }
        }
    }

    private void applyWindowLayout(boolean animate) {
        applyWindowLayout(animate, null);
    }

    private void applyWindowLayout(boolean animate, Runnable onAnimationFinished) {
        if (workspace == null) {
            return;
        }
        if (workspace.getWidth() == 0 || workspace.getHeight() == 0) {
            workspace.post(() -> applyWindowLayout(false, onAnimationFinished));
            return;
        }

        Rect[] targetRects = calculateWindowRects();
        suspendWindowInputRouting();
        updateScreenContainerBackground();
        ensureWindowChildren();

        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            boolean visible = isWindowSlotEnabled(slot) && !embeddedSlotClosing[slot];
            windowViews[slot].setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            windowViews[slot].setMainWindowMode(slot == activeMainSlot);
        }

        Runnable layoutFinished = () -> {
            restoreWindowInputRoutingAfterLayout();
            configureDesktopHomeViewport(windowViews[activeMainSlot]);
            if (onAnimationFinished != null) {
                onAnimationFinished.run();
            } else {
                refreshAllEmbeddedSlotLayouts();
            }
        };

        if (!animate || !hasLaidOutWindowFrames()) {
            cancelWindowSurfaceAnimation();
            for (int slot = 0; slot < MAX_WINDOWS; slot++) {
                setWindowFrame(windowViews[slot], targetRects[slot]);
                resetWindowTransform(windowViews[slot]);
            }
            applyWindowZOrder();
            layoutFinished.run();
            return;
        }

        animateWindowFrames(targetRects, layoutFinished);
    }

    private void configureDesktopHomeViewport(OneStepWindowView windowView) {
        if (windowView == null || workspace == null) {
            return;
        }
        int viewportWidth = workspace.getWidth();
        int viewportHeight = workspace.getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }
        windowView.updateDesktopHomeViewport(viewportWidth, viewportHeight);
    }

    private void ensureWindowChildren() {
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            OneStepWindowView windowView = windowViews[slot];
            if (windowView.getParent() != workspace) {
                ViewParent parent = windowView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(windowView);
                }
                workspace.addView(windowView);
            }
        }
    }

    private void updateScreenContainerBackground() {
        if (screenContainerBackground == null) {
            return;
        }
        int topMargin = multiWindowMode ? getTopChromeHeight() : 0;
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) screenContainerBackground.getLayoutParams();
        if (layoutParams.topMargin == topMargin) {
            return;
        }
        layoutParams.topMargin = topMargin;
        screenContainerBackground.setLayoutParams(layoutParams);
    }

    private Rect[] calculateWindowRects() {
        return WindowLayoutCalculator.calculate(
                MAX_WINDOWS,
                workspace.getWidth(),
                workspace.getHeight(),
                dp(2),
                getTopChromeHeight(),
                multiWindowMode,
                verticalWindowLayout,
                activeMainSlot,
                sideSlotOrder,
                getVisibleSideWindowCount(),
                mainOnLeft,
                dp(3));
    }

    private boolean hasLaidOutWindowFrames() {
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (!isWindowSlotEnabled(slot)) {
                continue;
            }
            OneStepWindowView windowView = windowViews[slot];
            if (windowView.getWidth() <= 0 || windowView.getHeight() <= 0) {
                return false;
            }
        }
        return true;
    }

    private void animateWindowFrames(Rect[] targetRects, Runnable onAnimationFinished) {
        windowAnimationController.animate(targetRects, onAnimationFinished);
    }

    private void cancelWindowSurfaceAnimation() {
        if (windowAnimationController != null) {
            windowAnimationController.cancelAndReset();
        }
    }

    private boolean isWindowAnimationRunning() {
        return windowAnimationController != null && windowAnimationController.isRunning();
    }

    private void beginWindowSwitchAnimationCriticalSection() {
        mainHandler.removeCallbacks(flushDeferredWindowSwitchWorkRunnable);
        windowSwitchAnimationCritical = true;
    }

    private void scheduleDeferredWindowSwitchWorkFlush() {
        mainHandler.removeCallbacks(flushDeferredWindowSwitchWorkRunnable);
        mainHandler.postDelayed(flushDeferredWindowSwitchWorkRunnable,
                POST_ANIMATION_NON_CRITICAL_WORK_DELAY_MS);
    }

    private boolean deferWindowSwitchUiWork(int workFlags) {
        if (!windowSwitchAnimationCritical) {
            return false;
        }
        deferredWindowSwitchUiWork |= workFlags;
        return true;
    }

    private void flushDeferredWindowSwitchWork() {
        if (activityDestroyed) {
            windowSwitchAnimationCritical = false;
            deferredWindowSwitchUiWork = 0;
            return;
        }
        if (isWindowAnimationRunning()) {
            scheduleDeferredWindowSwitchWorkFlush();
            return;
        }
        windowSwitchAnimationCritical = false;
        int workFlags = deferredWindowSwitchUiWork;
        deferredWindowSwitchUiWork = 0;

        if ((workFlags & DEFERRED_MEDIA_SESSION_REFRESH) != 0) {
            refreshActiveMediaController();
        } else if ((workFlags & DEFERRED_MEDIA_UI_REFRESH) != 0) {
            updateMediaUi();
        } else if ((workFlags & DEFERRED_PLAYLIST_REFRESH) != 0) {
            updatePlaylistPanel();
        }
        if ((workFlags & DEFERRED_TOP_COMPONENT_REFRESH) != 0) {
            refreshTopStatusComponents();
        }
    }

    private void applyWindowZOrder() {
        for (int slot : sideSlotOrder) {
            if (!isWindowSlotEnabled(slot)) {
                continue;
            }
            OneStepWindowView sideView = windowViews[slot];
            sideView.setElevation(0f);
            sideView.setTranslationZ(0f);
            sideView.setZ(0f);
            sideView.bringToFront();
        }
        OneStepWindowView mainView = windowViews[activeMainSlot];
        mainView.setElevation(dp(8));
        mainView.setTranslationZ(dp(8));
        mainView.setZ(dp(16));
        mainView.bringToFront();
        workspace.invalidate();
    }

    private int getVisibleSideWindowCount() {
        return Math.min(sideWindowCount, sideSlotOrder.size());
    }

    private int getEnabledWindowCount() {
        return 1 + getVisibleSideWindowCount();
    }

    private boolean isWindowSlotEnabled(int slot) {
        if (slot == activeMainSlot) {
            return true;
        }
        int sideIndex = sideSlotOrder.indexOf(slot);
        return sideIndex >= 0 && sideIndex < getVisibleSideWindowCount();
    }

    private void configureWindowPivot(View view, Rect currentRect, Rect targetRect) {
        if (targetRect.left == 0 && currentRect.left == 0) {
            view.setPivotX(0);
        } else if (targetRect.right == workspace.getWidth()
                && currentRect.right == workspace.getWidth()) {
            view.setPivotX(currentRect.width());
        } else {
            view.setPivotX(currentRect.width() / 2f);
        }

        if (targetRect.top == 0 && currentRect.top == 0) {
            view.setPivotY(0);
        } else if (targetRect.bottom == workspace.getHeight()
                && currentRect.bottom == workspace.getHeight()) {
            view.setPivotY(currentRect.height());
        } else {
            view.setPivotY(currentRect.height() / 2f);
        }
    }

    private float calculateTranslationX(View view, Rect currentRect, Rect targetRect,
                                        float targetScaleX) {
        return targetRect.left - currentRect.left
                - view.getPivotX() * (1f - targetScaleX);
    }

    private float calculateTranslationY(View view, Rect currentRect, Rect targetRect,
                                        float targetScaleY) {
        return targetRect.top - currentRect.top
                - view.getPivotY() * (1f - targetScaleY);
    }

    private Rect getWindowFrame(View view) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        return new Rect(lp.leftMargin, lp.topMargin,
                lp.leftMargin + Math.max(1, lp.width),
                lp.topMargin + Math.max(1, lp.height));
    }

    private void setWindowFrame(View view, Rect rect) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(rect.width(), rect.height());
        }
        if (lp.width == rect.width() && lp.height == rect.height()
                && lp.leftMargin == rect.left && lp.topMargin == rect.top) {
            return;
        }
        lp.width = rect.width();
        lp.height = rect.height();
        lp.leftMargin = rect.left;
        lp.topMargin = rect.top;
        view.setLayoutParams(lp);
    }

    private void resetWindowTransform(View view) {
        view.animate().cancel();
        view.setTranslationX(0);
        view.setTranslationY(0);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private int getTopChromeHeight() {
        return getStatusBarSpacingHeight() + getTopMediaAreaHeight()
                + getTopNavHeight() + getTopAppStripHeight();
    }

    private void setTopChromeVisible(boolean visible, boolean animate) {
        if (topChromeContainer == null) {
            return;
        }
        int chromeHeight = Math.max(1, getTopChromeHeight());
        topChromeContent.setPadding(0, getStatusBarSpacingHeight(), 0, 0);
        ViewGroup.LayoutParams layoutParams = topChromeContainer.getLayoutParams();
        if (layoutParams != null && layoutParams.height != chromeHeight) {
            layoutParams.height = chromeHeight;
            topChromeContainer.setLayoutParams(layoutParams);
        }
        topChromeContainer.animate().cancel();
        if (visible) {
            topChromeContainer.setVisibility(View.VISIBLE);
            if (!animate) {
                topChromeContainer.setTranslationY(0f);
                schedulePipDockBoundsUpdate();
                return;
            }
            topChromeContainer.setTranslationY(-chromeHeight);
            topChromeContainer.animate()
                    .translationY(0f)
                    .setDuration(WINDOW_FRAME_SWITCH_ANIMATION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .withEndAction(this::schedulePipDockBoundsUpdate)
                    .start();
            return;
        }
        if (!animate) {
            topChromeContainer.setTranslationY(-chromeHeight);
            topChromeContainer.setVisibility(View.GONE);
            return;
        }
        topChromeContainer.animate()
                .translationY(-chromeHeight)
                .setDuration(WINDOW_FRAME_SWITCH_ANIMATION_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> topChromeContainer.setVisibility(View.GONE))
                .start();
    }

    private void rebuildTopChromeContent() {
        if (topChromeContainer == null || topChromeContent == null) {
            return;
        }
        shortcutViews.clear();
        topChromeContent.removeAllViews();
        topChromeContent.setPadding(0, getStatusBarSpacingHeight(), 0, 0);
        topChromeContent.addView(createTopMediaArea(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopMediaAreaHeight()));
        topChromeContent.addView(createTopNavigationBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopNavHeight()));
        topChromeContent.addView(createTopAppStrip(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, getTopAppStripHeight()));

        ViewGroup.LayoutParams rawLp = topChromeContainer.getLayoutParams();
        if (rawLp != null && rawLp.height != getTopChromeHeight()) {
            rawLp.height = getTopChromeHeight();
            topChromeContainer.setLayoutParams(rawLp);
        }
        topChromeContainer.setTranslationY(multiWindowMode ? 0f : -getTopChromeHeight());
        rebuildDesktopHomeViews();
        updateShortcutAppStatuses();
        requestRunningTaskStatusRefresh();
        applyWindowLayout(false);
    }

    private void addCornerTriggers(FrameLayout root) {
        int gestureShieldHeight = getTopGestureShieldHeight();
        statusGestureShield = new View(this);
        statusGestureShield.setBackgroundColor(Color.TRANSPARENT);
        statusGestureShield.setVisibility(View.GONE);
        root.addView(statusGestureShield, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gestureShieldHeight,
                Gravity.TOP));

        cornerTriggerPreviewLayer = new FrameLayout(this);
        cornerTriggerPreviewLayer.setClipChildren(false);
        cornerTriggerPreviewLayer.setClipToPadding(false);
        cornerTriggerPreviewLayer.setClickable(false);
        cornerTriggerPreviewLayer.setFocusable(false);
        cornerTriggerPreviewLayer.setImportantForAccessibility(
                View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        cornerTriggerPreviewLayer.setVisibility(View.GONE);
        leftCornerTriggerPreview = createCornerTriggerPreviewMask();
        rightCornerTriggerPreview = createCornerTriggerPreviewMask();
        cornerTriggerPreviewLayer.addView(leftCornerTriggerPreview);
        cornerTriggerPreviewLayer.addView(rightCornerTriggerPreview);
        root.addView(cornerTriggerPreviewLayer, matchFrame());

        leftCornerTrigger = createCornerTrigger(true);
        rightCornerTrigger = createCornerTrigger(false);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(getCornerTriggerSizePx(),
                getCornerTriggerSizePx(),
                Gravity.START | Gravity.TOP);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(getCornerTriggerSizePx(),
                getCornerTriggerSizePx(),
                Gravity.END | Gravity.TOP);
        root.addView(leftCornerTrigger, leftLp);
        root.addView(rightCornerTrigger, rightLp);
        updateCornerTriggerBounds();
        updateCornerTriggers();
    }

    private int getTopGestureShieldHeight() {
        return Math.max(dp(28), getStatusBarHeight() + dp(8));
    }

    private View createCornerTrigger(boolean left) {
        View trigger = new View(this);
        trigger.setBackgroundColor(Color.TRANSPARENT);
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final boolean[] triggered = new boolean[1];
        trigger.setOnTouchListener((view, event) -> {
            if (multiWindowMode) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    triggered[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX[0];
                    float dy = event.getRawY() - downY[0];
                    int triggerDistance = getCornerTriggerDistancePx();
                    if (!triggered[0] && CornerTriggerGesturePolicy.matches(
                            left, dx, dy, triggerDistance)) {
                        triggered[0] = true;
                        enterOneStepMode(left);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    return true;
                default:
                    return true;
            }
        });
        return trigger;
    }

    private View createCornerTriggerPreviewMask() {
        View preview = new View(this);
        preview.setBackground(makePanelBackground(0x553f8cff, 0xff2f80ff, dp(14)));
        preview.setClipToOutline(true);
        preview.setClickable(false);
        preview.setFocusable(false);
        preview.setLongClickable(false);
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return preview;
    }

    private void updateCornerTriggerBounds() {
        int size = getCornerTriggerSizePx();
        updateCornerTriggerBounds(leftCornerTrigger, size, Gravity.START | Gravity.TOP);
        updateCornerTriggerBounds(rightCornerTrigger, size, Gravity.END | Gravity.TOP);
        updateCornerTriggerBounds(leftCornerTriggerPreview, size, Gravity.START | Gravity.TOP);
        updateCornerTriggerBounds(rightCornerTriggerPreview, size, Gravity.END | Gravity.TOP);
    }

    private void updateCornerTriggerBounds(View view, int size, int gravity) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams rawParams = view.getLayoutParams();
        FrameLayout.LayoutParams params = rawParams instanceof FrameLayout.LayoutParams
                ? (FrameLayout.LayoutParams) rawParams
                : new FrameLayout.LayoutParams(size, size, gravity);
        params.width = size;
        params.height = size;
        params.gravity = gravity;
        params.topMargin = multiWindowMode ? 0 : getStatusBarHeight();
        view.setLayoutParams(params);
    }

    private void showCornerTriggerPreview() {
        if (cornerTriggerPreviewLayer == null) {
            return;
        }
        updateCornerTriggerBounds();
        mainHandler.removeCallbacks(cornerTriggerPreviewHideRunnable);
        cornerTriggerPreviewLayer.setAlpha(1f);
        cornerTriggerPreviewLayer.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(cornerTriggerPreviewHideRunnable,
                CORNER_TRIGGER_PREVIEW_HIDE_DELAY_MS);
    }

    private void hideCornerTriggerPreview() {
        if (cornerTriggerPreviewLayer == null) {
            return;
        }
        cornerTriggerPreviewLayer.setAlpha(1f);
        cornerTriggerPreviewLayer.setVisibility(View.GONE);
    }

    private void updateCornerTriggers() {
        int visibility = multiWindowMode ? View.GONE : View.VISIBLE;
        updateCornerTriggerBounds();
        if (statusGestureShield != null) {
            statusGestureShield.setVisibility(View.GONE);
        }
        if (leftCornerTrigger != null) {
            leftCornerTrigger.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                leftCornerTrigger.bringToFront();
            }
        }
        if (rightCornerTrigger != null) {
            rightCornerTrigger.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                rightCornerTrigger.bringToFront();
            }
        }
        if (rootContainer != null) {
            rootContainer.invalidate();
        }
    }

    private void enterOneStepMode(boolean sideFromLeft) {
        suppressEmbeddedStarts = false;
        mainOnLeft = !sideFromLeft;
        updateTopNavigationControls();
        multiWindowMode = false;
        applyWindowLayout(false);
        multiWindowMode = true;
        applyStatusBarForCurrentMode();
        setTopChromeVisible(true, true);
        updateCornerTriggers();
        applyWindowLayout(true);
    }

    private void exitOneStepMode() {
        if (!multiWindowMode || exitOneStepPending) {
            return;
        }
        exitOneStepPending = true;
        requestPipUndock();
        applyStatusBarForCurrentMode();
        suspendEmbeddedStartsForFullscreen();
        workspace.postDelayed(this::completeExitOneStepMode,
                EXIT_FULLSCREEN_LAYOUT_DELAY_MS);
    }

    private void completeExitOneStepMode() {
        exitOneStepPending = false;
        if (!multiWindowMode) {
            return;
        }
        multiWindowMode = false;
        applyStatusBarForCurrentMode();
        setTopChromeVisible(false, true);
        updateCornerTriggers();
        applyWindowLayout(true);
    }

    private View createTopMediaArea() {
        return topPanelController.createView();
    }

    private float getPipAspectRatio() {
        return topPanelController.getPipAspectRatio();
    }

    private boolean shouldShowTopComponentArea() {
        return topPanelController.shouldShowTopComponentArea();
    }

    private View createTopNavigationBar() {
        FrameLayout navRoot = new FrameLayout(this);
        navRoot.setBackgroundColor(Color.TRANSPARENT);

        topNavLeftControls = new LinearLayout(this);
        topNavLeftControls.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topNavLeftControls.setPadding(dp(16), 0, 0, 0);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                dp(122), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.CENTER_VERTICAL);
        navRoot.addView(topNavLeftControls, leftLp);

        topNavPageLeftControl = createTopNavImageControl(
                R.drawable.top_nav_page_left, "应用列表向左");
        topNavPageLeftControl.setOnClickListener(v -> scrollTopAppStripPage(1));
        topNavPageRightControl = createTopNavImageControl(
                R.drawable.top_nav_page_right, "应用列表向右");
        topNavPageRightControl.setOnClickListener(v -> scrollTopAppStripPage(-1));
        topNavSettingsControl = createTopNavImageControl(
                R.drawable.top_nav_settings, "设置");
        topNavSettingsControl.setOnClickListener(v -> showInternalSettingsPage());
        topNavExpandLeftControl = createTopNavImageControl(
                R.drawable.top_nav_expand_left, "展开");
        topNavExpandLeftControl.setOnClickListener(v -> exitOneStepMode());
        topNavExpandRightControl = createTopNavImageControl(
                R.drawable.top_nav_expand_right, "展开");
        topNavExpandRightControl.setOnClickListener(v -> exitOneStepMode());

        TextView title = new TextView(this);
        title.setText("One Step");
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(0x86ffffff);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setShadowLayer(dp(1), 0, dp(1), 0x26000000);
        setDpTextSize(title, 20);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                dp(172), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        navRoot.addView(title, titleLp);

        topNavRightControls = new LinearLayout(this);
        topNavRightControls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topNavRightControls.setPadding(0, 0, dp(16), 0);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(
                dp(122), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        navRoot.addView(topNavRightControls, rightLp);
        updateTopNavigationControls();

        View topLine = new View(this);
        topLine.setBackgroundColor(0x18715f53);
        FrameLayout.LayoutParams topLineLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1), Gravity.TOP);
        navRoot.addView(topLine, topLineLp);

        View bottomLine = new View(this);
        bottomLine.setBackgroundColor(0x18715f53);
        FrameLayout.LayoutParams bottomLineLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1), Gravity.BOTTOM);
        navRoot.addView(bottomLine, bottomLineLp);
        return navRoot;
    }

    private ImageView createTopNavImageControl(int drawableResId, String description) {
        ImageView control = new ImageView(this);
        LayerDrawable icon = new LayerDrawable(new Drawable[]{getDrawable(drawableResId)});
        icon.setLayerSize(0, dp(TOP_NAV_ICON_SIZE_DP), dp(TOP_NAV_ICON_SIZE_DP));
        icon.setLayerGravity(0, Gravity.CENTER);
        control.setImageDrawable(icon);
        control.setScaleType(ImageView.ScaleType.CENTER);
        control.setPadding(0, 0, 0, 0);
        control.setImageAlpha(217);
        control.setContentDescription(description);
        control.setClickable(true);
        control.setFocusable(true);
        return control;
    }

    private void updateTopNavigationControls() {
        if (topNavLeftControls == null || topNavRightControls == null
                || topNavPageLeftControl == null || topNavPageRightControl == null
                || topNavSettingsControl == null || topNavExpandLeftControl == null
                || topNavExpandRightControl == null) {
            return;
        }
        topNavLeftControls.removeAllViews();
        topNavRightControls.removeAllViews();
        if (verticalWindowLayout || mainOnLeft) {
            addTopNavControl(topNavLeftControls, topNavPageLeftControl);
            addTopNavControl(topNavLeftControls, topNavPageRightControl);
            addTopNavControl(topNavRightControls, topNavSettingsControl);
            addTopNavControl(topNavRightControls, topNavExpandRightControl);
            return;
        }
        addTopNavControl(topNavLeftControls, topNavExpandLeftControl);
        addTopNavControl(topNavLeftControls, topNavSettingsControl);
        addTopNavControl(topNavRightControls, topNavPageLeftControl);
        addTopNavControl(topNavRightControls, topNavPageRightControl);
    }

    private void addTopNavControl(LinearLayout container, View control) {
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                dp(TOP_NAV_BUTTON_SIZE_DP), dp(TOP_NAV_BUTTON_SIZE_DP));
        if (container.getChildCount() > 0) {
            layoutParams.leftMargin = dp(TOP_NAV_BUTTON_SPACING_DP);
        }
        container.addView(control, layoutParams);
    }

    private TextView createTopNavControl(String symbol, float sizeDp, String description) {
        TextView control = new TextView(this);
        control.setText(symbol);
        control.setGravity(Gravity.CENTER);
        control.setTextColor(0xd9ffffff);
        control.setTypeface(Typeface.DEFAULT_BOLD);
        control.setContentDescription(description);
        control.setShadowLayer(dp(1), 0, dp(1), 0x33000000);
        setDpTextSize(control, sizeDp);
        return control;
    }

    private void scrollTopAppStripPage(int visualDirection) {
        HorizontalScrollView scrollView = topAppStripScrollView;
        if (scrollView == null || scrollView.getChildCount() == 0 || scrollView.getWidth() <= 0) {
            return;
        }
        int pageWidth = Math.max(1, scrollView.getWidth());
        int childWidth = scrollView.getChildAt(0).getWidth();
        int maxScrollX = Math.max(0, childWidth - scrollView.getWidth());
        int targetScrollX = scrollView.getScrollX() + visualDirection * pageWidth;
        targetScrollX = Math.max(0, Math.min(maxScrollX, targetScrollX));
        scrollView.smoothScrollTo(targetScrollX, 0);
    }

    private void initMediaMonitoring() {
        topPanelController.startMediaMonitoring();
    }

    private void initAmapNavigationMonitoring() {
        topPanelController.startNavigationMonitoring();
    }

    private void resumeMediaMonitoring() {
        topPanelController.resume();
    }

    private void pauseMediaMonitoring() {
        if (topPanelController != null) {
            topPanelController.pause();
        }
    }

    private void refreshTopStatusComponents() {
        topPanelController.refreshTopStatusComponents();
    }

    private void initializeEmbeddedBridgeState() {
        embeddedStartEpochStore = new EmbeddedStartEpochStore(this);
        embeddedStartEpoch = embeddedStartEpochStore.beginSession();
    }

    private void refreshActiveMediaController() {
        topPanelController.refreshActiveMediaController();
    }

    private void updateMediaUi() {
        topPanelController.updateMediaUi();
    }

    private boolean hidePlaylistPanel() {
        return topPanelController.hidePlaylistPanel();
    }

    private void updatePlaylistPanel() {
        topPanelController.updatePlaylistPanel();
    }

    private View createTopAppStrip() {
        FrameLayout stripRoot = new FrameLayout(this);
        stripRoot.setBackgroundColor(Color.TRANSPARENT);

        HorizontalScrollView scrollView = new HorizontalScrollView(this);
        topAppStripScrollView = scrollView;
        scrollView.setHorizontalScrollBarEnabled(false);
        scrollView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        stripRoot.addView(scrollView, matchFrame());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(getTopAppStripSidePaddingDp()), dp(getTopAppStripVerticalPaddingDp()),
                dp(getTopAppStripSidePaddingDp()), dp(getTopAppStripVerticalPaddingDp()));
        scrollView.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (LauncherApp app : topAppStripApps) {
            int iconSizeDp = getTopAppIconSizeDp();
            AppShortcutView shortcut = new AppShortcutView(this, false, iconSizeDp, 0);
            shortcut.setStatusIndicatorEnabled(true);
            shortcut.bind(app);
            shortcut.setOnClickListener(v -> addOrFocusApp(app));
            int cellWidthDp = getTopAppStripCellWidthDp(iconSizeDp);
            LinearLayout.LayoutParams shortcutLp = new LinearLayout.LayoutParams(dp(cellWidthDp),
                    ViewGroup.LayoutParams.MATCH_PARENT);
            row.addView(shortcut, shortcutLp);
            shortcutViews.add(shortcut);
        }

        View bottomLine = new View(this);
        bottomLine.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2), Gravity.BOTTOM);
        stripRoot.addView(bottomLine, lineLp);
        return stripRoot;
    }

    private View createDesktopHome() {
        FixedViewportFrameLayout page = new FixedViewportFrameLayout(this);
        page.setCropToFill(true);
        FrameLayout homeRoot = page.getViewport();
        ImageView background = new ImageView(this);
        applyCurrentListBackground(background);
        homeRoot.addView(background, matchFrame());

        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(0, dp(10), 0, 0);
        home.setBackgroundColor(0x00000000);
        homeRoot.addView(home, matchFrame());

        home.addView(createPagedDesktopAppGrid(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        View dockLine = new View(this);
        dockLine.setBackgroundColor(0x1a315c3e);
        home.addView(dockLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        FrameLayout dock = new FrameLayout(this);
        dock.setPadding(0, dp(4), 0, dp(26));
        dock.setBackgroundColor(0x1275aa72);
        int dockCount = getDockAppCount();
        int dockStart = Math.max(0, launcherApps.size() - dockCount);
        dock.addView(createAppRows(dockStart, dockCount, desktopGridColumns,
                94, getDesktopGridIconSizeDp(), getDesktopGridTextSizeDp(), true),
                matchFrame());
        home.addView(dock, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(128)));
        return page;
    }

    private View createPagedDesktopAppGrid() {
        FrameLayout panel = new FrameLayout(this);
        panel.setPadding(0, 0, 0, 0);
        panel.setBackgroundColor(Color.TRANSPARENT);

        PagingHorizontalScrollView pager = new PagingHorizontalScrollView(this);
        pager.setFillViewport(true);
        pager.setHorizontalScrollBarEnabled(false);
        pager.setVerticalScrollBarEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panel.addView(pager, matchFrame());

        LinearLayout pages = new LinearLayout(this);
        pages.setOrientation(LinearLayout.HORIZONTAL);
        pages.setGravity(Gravity.CENTER_VERTICAL);
        pager.addView(pages, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int appCount = Math.max(0, launcherApps.size() - getDockAppCount());
        int pageSize = desktopGridRows * desktopGridColumns;
        int pageCount = Math.max(1, (appCount + pageSize - 1) / pageSize);
        for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            LinearLayout page = createFixedAppGridPage(pageIndex * pageSize,
                    Math.min(pageSize, Math.max(0, appCount - pageIndex * pageSize)));
            pages.addView(page, new LinearLayout.LayoutParams(dp(320),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        pager.addOnLayoutChangeListener((view, left, top, right, bottom,
                                         oldLeft, oldTop, oldRight, oldBottom) ->
                updatePagedGridPageWidths(pager, pages));
        pager.post(() -> updatePagedGridPageWidths(pager, pages));
        return panel;
    }

    private int getDockAppCount() {
        return Math.min(desktopGridColumns, Math.max(0, launcherApps.size()));
    }

    private void updatePagedGridPageWidths(HorizontalScrollView pager, LinearLayout pages) {
        int pageWidth = Math.max(1, pager.getWidth());
        for (int i = 0; i < pages.getChildCount(); i++) {
            View child = pages.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp.width != pageWidth) {
                lp.width = pageWidth;
                child.setLayoutParams(lp);
            }
        }
        if (pager instanceof PagingHorizontalScrollView) {
            ((PagingHorizontalScrollView) pager).snapToNearestPage();
        }
    }

    private LinearLayout createFixedAppGridPage(int startIndex, int count) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, 0, 0, 0);

        for (int rowIndex = 0; rowIndex < desktopGridRows; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            page.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            for (int column = 0; column < desktopGridColumns; column++) {
                int localIndex = rowIndex * desktopGridColumns + column;
                int appIndex = startIndex + localIndex;
                FrameLayout cell = new FrameLayout(this);
                row.addView(cell, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                if (localIndex >= count || appIndex >= launcherApps.size()) {
                    continue;
                }

                LauncherApp app = launcherApps.get(appIndex);
                AppShortcutView shortcut = new AppShortcutView(this, true,
                        getDesktopGridIconSizeDp(), getDesktopGridTextSizeDp());
                shortcut.bind(app);
                shortcut.setOnClickListener(v -> addOrFocusApp(app));
                cell.addView(shortcut, matchFrame());
                shortcutViews.add(shortcut);
            }
        }
        return page;
    }

    private int getDesktopGridIconSizeDp() {
        if (desktopGridRows >= 6 || desktopGridColumns >= 5) {
            return 38;
        }
        if (desktopGridRows >= 5 || desktopGridColumns >= 4) {
            return 44;
        }
        return 52;
    }

    private float getDesktopGridTextSizeDp() {
        if (desktopGridRows >= 6 || desktopGridColumns >= 5) {
            return 9f;
        }
        if (desktopGridRows >= 5 || desktopGridColumns >= 4) {
            return 10f;
        }
        return 11f;
    }

    private void loadOneStepSettings() {
        settingsStore = new OneStepSettingsStore(this);
        OneStepSettings settings = settingsStore.load();
        desktopGridRows = settings.desktopGridRows;
        desktopGridColumns = settings.desktopGridColumns;
        topAppIconScalePct = settings.topAppIconScalePct;
        topAppStripSpacingScalePct = settings.topAppStripSpacingScalePct;
        topAppStripVerticalPaddingScalePct = settings.topAppStripVerticalPaddingScalePct;
        topComponentsVisible = settings.topComponentsVisible;
        statusBarSpacingEnabled = settings.statusBarSpacingEnabled;
        verticalWindowLayout = settings.verticalWindowLayout;
        sideWindowCount = settings.sideWindowCount;
        topNavVerticalMarginScalePct = settings.topNavVerticalMarginScalePct;
        oneStepTriggerAreaScalePct = settings.oneStepTriggerAreaScalePct;
        cornerTriggerSensitivityPct = settings.cornerTriggerSensitivityPct;
        logRecordingEnabled = settings.logRecordingEnabled;
    }

    private void reconcileTopAppListConfiguration() {
        OneStepSettingsStore.TopAppListConfig config = settingsStore.loadTopAppListConfig();
        List<String> availableKeys = new ArrayList<>(launcherApps.size());
        Map<String, LauncherApp> appsByKey = new HashMap<>();
        for (LauncherApp app : launcherApps) {
            String key = app.instanceKey();
            availableKeys.add(key);
            appsByKey.put(key, app);
        }
        TopAppListPolicy.State state = TopAppListPolicy.reconcile(
                availableKeys, config.configured, config.orderedKeys, config.selectedKeys);
        List<LauncherApp> orderedCandidates = new ArrayList<>(state.orderedKeys.size());
        List<LauncherApp> selectedApps = new ArrayList<>(state.selectedKeys.size());
        for (String key : state.orderedKeys) {
            LauncherApp app = appsByKey.get(key);
            if (app == null) {
                continue;
            }
            orderedCandidates.add(app);
            if (state.selectedKeys.contains(key)) {
                selectedApps.add(app);
            }
        }
        orderedTopAppCandidates = orderedCandidates;
        topAppStripApps = selectedApps;
        selectedTopAppInstanceKeys = new LinkedHashSet<>(state.selectedKeys);
    }

    private void saveTopAppListConfiguration(
            List<String> orderedKeys, Set<String> selectedKeys) {
        settingsStore.saveTopAppListConfig(orderedKeys, selectedKeys);
        reconcileTopAppListConfiguration();
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void loadBuiltInDesktopApps() {
        try {
            builtInDesktopApps = launcherAppRepository.loadHomeApps();
        } catch (RuntimeException e) {
            builtInDesktopApps = Collections.emptyList();
            Log.w(TAG, "Loading system desktop applications failed", e);
        }
        ComponentName selectedComponent = settingsStore.getBuiltInDesktopComponent();
        builtInDesktopApp = findAppByComponent(builtInDesktopApps, selectedComponent);
        if (builtInDesktopApp == null && !builtInDesktopApps.isEmpty()) {
            builtInDesktopApp = builtInDesktopApps.get(0);
            settingsStore.saveBuiltInDesktopComponent(builtInDesktopApp.componentName);
        }
    }

    private LauncherApp findAppByComponent(
            List<LauncherApp> apps, ComponentName componentName) {
        if (componentName == null || apps == null) {
            return null;
        }
        for (LauncherApp app : apps) {
            if (componentName.equals(app.componentName)) {
                return app;
            }
        }
        return null;
    }

    private void saveBuiltInDesktop(LauncherApp app) {
        if (app == null || launcherAppRepository == null) {
            return;
        }
        LauncherApp resolved;
        try {
            resolved = launcherAppRepository.loadHomeApp(app.componentName);
        } catch (RuntimeException e) {
            resolved = null;
        }
        if (resolved == null) {
            Toast.makeText(this, "该桌面当前不可用", Toast.LENGTH_SHORT).show();
            loadBuiltInDesktopApps();
            updateSettingsPageViews();
            return;
        }
        builtInDesktopApp = resolved;
        settingsStore.saveBuiltInDesktopComponent(resolved.componentName);
        boolean replaced = false;
        List<LauncherApp> updatedApps = new ArrayList<>(builtInDesktopApps);
        for (int index = 0; index < updatedApps.size(); index++) {
            if (resolved.componentName.equals(updatedApps.get(index).componentName)) {
                updatedApps.set(index, resolved);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            updatedApps.add(resolved);
        }
        builtInDesktopApps = updatedApps;
        updateSettingsPageViews();
        Toast.makeText(this, "已将“" + resolved.label + "”设为内置桌面",
                Toast.LENGTH_SHORT).show();
        mainHandler.post(this::requestDesktopHomeInMain);
    }

    private List<LauncherApp> loadDefaultHomeCandidates() {
        if (launcherAppRepository == null) {
            return Collections.emptyList();
        }
        try {
            return launcherAppRepository.loadDefaultHomeApps();
        } catch (RuntimeException e) {
            Log.w(TAG, "Loading default HOME candidates failed", e);
            return Collections.emptyList();
        }
    }

    private void setDefaultHomeWithRoot(
            LauncherApp app, SettingsPanelController.DefaultHomeResultCallback callback) {
        if (app == null || callback == null) {
            return;
        }
        hookSettingsExecutor.execute(() -> {
            String targetPackage = mainShellQuote(app.packageName);
            String targetComponent = mainShellQuote(app.componentKey());
            int userId = app.userId();
            String command = "target_package=" + targetPackage + "\n"
                    + "target_component=" + targetComponent + "\n"
                    + "user_id=" + userId + "\n"
                    + "set_output=\"$(cmd package set-home-activity --user \"$user_id\" "
                    + "\"$target_component\" 2>&1)\"\n"
                    + "set_status=$?\n"
                    + "if [ \"$set_status\" -ne 0 ]; then\n"
                    + "  cmd role add-role-holder --user \"$user_id\" "
                    + "android.app.role.HOME \"$target_package\" 0 >/dev/null 2>&1\n"
                    + "fi\n"
                    + "attempt=0\n"
                    + "while [ \"$attempt\" -lt 10 ]; do\n"
                    + "  resolved=\"$(cmd package resolve-activity --brief --user "
                    + "\"$user_id\" -a android.intent.action.MAIN "
                    + "-c android.intent.category.HOME 2>&1)\"\n"
                    + "  case \"$resolved\" in\n"
                    + "    *\"$target_package/\"*) printf '%s\\n' \"$resolved\"; exit 0 ;;\n"
                    + "  esac\n"
                    + "  attempt=$((attempt + 1))\n"
                    + "  sleep 0.1\n"
                    + "done\n"
                    + "printf '%s\\n%s\\n' \"$set_output\" \"$resolved\"\n"
                    + "exit 1";
            ShellCommandResult result = runMainPrivilegedCommand(
                    command, "set default HOME to " + app.componentKey(), true);
            String message = result.isSuccess()
                    ? "已将“" + app.label + "”设为默认桌面"
                    : "设置“" + app.label + "”为默认桌面失败";
            mainHandler.post(() -> {
                if (!activityDestroyed) {
                    callback.onResult(result.isSuccess(), message);
                }
            });
        });
    }

    private void enableHyperOsGestureNavigation(
            SettingsPanelController.DefaultHomeResultCallback callback) {
        if (callback == null) {
            return;
        }
        hookSettingsExecutor.execute(() -> {
            String command = "marker_written=0\n"
                    + "for module_dir in /data/adb/modules/onestep40_privapp "
                    + "/data/adb/modules/onestep4_ksu_privapp; do\n"
                    + "  if [ -d \"$module_dir\" ] && [ ! -e \"$module_dir/disable\" ] "
                    + "&& [ ! -e \"$module_dir/remove\" ]; then\n"
                    + "    mkdir -p \"$module_dir/hook-config\"\n"
                    + "    : > \"$module_dir/hook-config/enable-hyperos-third-party-gesture\"\n"
                    + "    chmod 0600 \"$module_dir/hook-config/enable-hyperos-third-party-gesture\"\n"
                    + "    marker_written=1\n"
                    + "    break\n"
                    + "  fi\n"
                    + "done\n"
                    + "settings put global force_fsg_nav_bar 1\n"
                    + "fsg_mode=\"$(settings get global force_fsg_nav_bar 2>/dev/null)\"\n"
                    + "if [ \"$fsg_mode\" = \"1\" ]; then\n"
                    + "  printf 'force_fsg_nav_bar=%s\\nmarker=%s\\n' "
                    + "\"$fsg_mode\" \"$marker_written\"\n"
                    + "  exit 0\n"
                    + "fi\n"
                    + "printf 'force_fsg_nav_bar=%s\\nmarker=%s\\n' "
                    + "\"$fsg_mode\" \"$marker_written\"\n"
                    + "exit 1";
            ShellCommandResult result = runMainPrivilegedCommand(
                    command, "enable HyperOS gesture navigation", true);
            String message = result.isSuccess()
                    ? "已启用 HyperOS 全面屏手势"
                    : "HyperOS 全面屏手势开关写入失败";
            mainHandler.post(() -> {
                if (!activityDestroyed) {
                    callback.onResult(result.isSuccess(), message);
                }
            });
        });
    }

    private SettingsPanelController createSettingsPanelController() {
        return new SettingsPanelController(this, new SettingsPanelController.Callbacks() {
            @Override public OneStepWindowView activeMainWindowView() {
                return activeMainSlot >= 0 && activeMainSlot < windowViews.length
                        ? windowViews[activeMainSlot] : null;
            }
            @Override public GradientDrawable panelBackground(
                    int fillColor, int strokeColor, float radius) {
                return makePanelBackground(fillColor, strokeColor, radius);
            }
            @Override public ImageView navigationIcon(int drawableResId, String description) {
                return createTopNavImageControl(drawableResId, description);
            }
            @Override public void applyBackground(ImageView target) {
                applyCurrentListBackground(target);
            }
            @Override public void pickBackground() { pickBackgroundFromGallery(); }
            @Override public void previewCornerTrigger() { showCornerTriggerPreview(); }
            @Override public int oneStepTriggerAreaScalePct() {
                return oneStepTriggerAreaScalePct;
            }
            @Override public int cornerTriggerSensitivityPct() {
                return cornerTriggerSensitivityPct;
            }
            @Override public int topNavVerticalMarginScalePct() {
                return topNavVerticalMarginScalePct;
            }
            @Override public int topAppIconScalePct() { return topAppIconScalePct; }
            @Override public int topAppStripSpacingScalePct() {
                return topAppStripSpacingScalePct;
            }
            @Override public int topAppStripVerticalPaddingScalePct() {
                return topAppStripVerticalPaddingScalePct;
            }
            @Override public boolean topComponentsVisible() { return topComponentsVisible; }
            @Override public boolean statusBarSpacingEnabled() {
                return statusBarSpacingEnabled;
            }
            @Override public boolean verticalWindowLayout() { return verticalWindowLayout; }
            @Override public boolean logRecordingEnabled() { return logRecordingEnabled; }
            @Override public int sideWindowCount() { return sideWindowCount; }
            @Override public List<LauncherApp> topAppCandidates() {
                return new ArrayList<>(orderedTopAppCandidates);
            }
            @Override public Set<String> selectedTopAppInstanceKeys() {
                return new LinkedHashSet<>(selectedTopAppInstanceKeys);
            }
            @Override public void saveTopAppList(
                    List<String> orderedKeys, Set<String> selectedKeys) {
                MainActivity.this.saveTopAppListConfiguration(orderedKeys, selectedKeys);
            }
            @Override public List<LauncherApp> builtInDesktopApps() {
                return new ArrayList<>(builtInDesktopApps);
            }
            @Override public String builtInDesktopComponentKey() {
                return builtInDesktopApp == null ? "" : builtInDesktopApp.componentKey();
            }
            @Override public void refreshBuiltInDesktopApps() {
                MainActivity.this.loadBuiltInDesktopApps();
            }
            @Override public void saveBuiltInDesktop(LauncherApp app) {
                MainActivity.this.saveBuiltInDesktop(app);
            }
            @Override public List<LauncherApp> defaultHomeCandidates() {
                return MainActivity.this.loadDefaultHomeCandidates();
            }
            @Override public void setDefaultHome(
                    LauncherApp app,
                    SettingsPanelController.DefaultHomeResultCallback callback) {
                MainActivity.this.setDefaultHomeWithRoot(app, callback);
            }
            @Override public void enableHyperOsGestureNavigation(
                    SettingsPanelController.DefaultHomeResultCallback callback) {
                MainActivity.this.enableHyperOsGestureNavigation(callback);
            }
            @Override public void saveOneStepTriggerAreaScale(int value) {
                MainActivity.this.saveOneStepTriggerAreaScale(value);
            }
            @Override public void saveCornerTriggerSensitivity(int value) {
                MainActivity.this.saveCornerTriggerSensitivity(value);
            }
            @Override public void saveTopNavVerticalMarginScale(int value) {
                MainActivity.this.saveTopNavVerticalMarginScale(value);
            }
            @Override public void saveTopAppIconScale(int value) {
                MainActivity.this.saveTopAppIconScale(value);
            }
            @Override public void saveTopAppStripSpacingScale(int value) {
                MainActivity.this.saveTopAppStripSpacingScale(value);
            }
            @Override public void saveTopAppStripVerticalPaddingScale(int value) {
                MainActivity.this.saveTopAppStripVerticalPaddingScale(value);
            }
            @Override public void saveTopComponentsVisible(boolean visible) {
                MainActivity.this.saveTopComponentsVisible(visible);
            }
            @Override public void saveStatusBarSpacingEnabled(boolean enabled) {
                MainActivity.this.saveStatusBarSpacingEnabled(enabled);
            }
            @Override public void saveVerticalWindowLayout(boolean enabled) {
                MainActivity.this.saveVerticalWindowLayout(enabled);
            }
            @Override public void saveLogRecordingEnabled(boolean enabled) {
                MainActivity.this.saveLogRecordingEnabled(enabled);
            }
            @Override public void saveSideWindowCount(int count) {
                MainActivity.this.saveSideWindowCount(count);
            }
            @Override public void exportSessionLog() {
                MainActivity.this.exportSessionLog();
            }
            @Override public boolean rootAuthorizationGranted() {
                return persistentRootShell.hasConfirmedRootAccess();
            }
            @Override public void requestRootAuthorization(
                    SettingsPanelController.RootAuthorizationResultCallback callback) {
                MainActivity.this.requestRootAuthorization(callback);
            }
            @Override public boolean openKernelSuManager() {
                return MainActivity.this.openKernelSuManager();
            }
            @Override public void loadZygiskHookSettings(
                    SettingsPanelController.HookSettingsResultCallback callback) {
                MainActivity.this.loadZygiskHookSettings(callback);
            }
            @Override public void saveZygiskHookSettings(
                    boolean secureWindowEnabled,
                    boolean statusBarOverlayEnabled,
                    boolean primaryHomeEnhancementEnabled,
                    SettingsPanelController.HookSettingsResultCallback callback) {
                MainActivity.this.saveZygiskHookSettings(
                        secureWindowEnabled, statusBarOverlayEnabled,
                        primaryHomeEnhancementEnabled, callback);
            }
            @Override public void rebootDevice() {
                MainActivity.this.rebootDeviceForHookSettings();
            }
        });
    }

    private TopPanelController createTopPanelController() {
        return new TopPanelController(this, mediaRootExecutor,
                new TopPanelController.Callbacks() {
            @Override public boolean deferWindowSwitchUiWork(int flags) {
                return MainActivity.this.deferWindowSwitchUiWork(flags);
            }
            @Override public boolean pipActive() { return pipActive; }
            @Override public Rect pipRestoreBounds() { return new Rect(pipRestoreBounds); }
            @Override public boolean topComponentsVisible() { return topComponentsVisible; }
            @Override public boolean activityDestroyed() { return activityDestroyed; }
            @Override public void rebuildTopChromeContent() {
                MainActivity.this.rebuildTopChromeContent();
            }
            @Override public void setTopChromeVisible(boolean visible, boolean animate) {
                MainActivity.this.setTopChromeVisible(visible, animate);
            }
            @Override public void schedulePipDockBoundsUpdate() {
                MainActivity.this.schedulePipDockBoundsUpdate();
            }
            @Override public ShellCommandResult runPrivilegedCommand(
                    String command, String description, boolean logOutput) {
                return runMainPrivilegedCommand(command, description, logOutput);
            }
            @Override public String shellQuote(String value) {
                return mainShellQuote(value);
            }
            @Override public GradientDrawable panelBackground(
                    int fillColor, int strokeColor, float radius) {
                return makePanelBackground(fillColor, strokeColor, radius);
            }
            @Override public int topMediaPlayerTopMargin() {
                return getTopMediaPlayerTopMargin();
            }
            @Override public int topMediaAreaHeight() { return getTopMediaAreaHeight(); }
            @Override public int pipDockTopInset() { return getPipDockTopInset(); }
            @Override public FrameLayout rootContainer() { return rootContainer; }
            @Override public LauncherApp createLauncherApp(String packageName) {
                return createLauncherAppForPackage(packageName);
            }
            @Override public void addOrFocusApp(LauncherApp app) {
                MainActivity.this.addOrFocusApp(app);
            }
            @Override public int dp(float value) { return MainActivity.this.dp(value); }
        });
    }

    private WindowAnimationController createWindowAnimationController() {
        return new WindowAnimationController(new WindowAnimationController.Callbacks() {
            @Override public OneStepWindowView[] windowViews() { return windowViews; }
            @Override public EmbeddedAppHost[] embeddedHosts() { return embeddedHosts; }
            @Override public LauncherApp[] windowApps() { return windowApps; }
            @Override public ViewGroup workspace() { return workspace; }
            @Override public Handler mainHandler() { return mainHandler; }
            @Override public int activeMainSlot() { return activeMainSlot; }
            @Override public void beginCriticalSection() {
                beginWindowSwitchAnimationCriticalSection();
            }
            @Override public void applyWindowZOrder() {
                MainActivity.this.applyWindowZOrder();
            }
            @Override public void refreshAllEmbeddedSlotLayouts() {
                MainActivity.this.refreshAllEmbeddedSlotLayouts();
            }
            @Override public void scheduleDeferredWorkFlush() {
                scheduleDeferredWindowSwitchWorkFlush();
            }
        });
    }

    private int getCornerTriggerSizePx() {
        return dp(oneStepTriggerAreaSizeDp(oneStepTriggerAreaScalePct));
    }

    private int getCornerTriggerDistancePx() {
        return Math.max(1, Math.round(dp(CORNER_TRIGGER_DISTANCE_DEFAULT_DP)
                * 100f / Math.max(1, cornerTriggerSensitivityPct)));
    }

    private Uri getSelectedBackgroundUri() {
        return settingsStore == null ? null : settingsStore.getBackgroundUri();
    }

    @SuppressLint("MissingPermission")
    private Drawable loadCurrentListBackgroundDrawable() {
        Uri selectedUri = getSelectedBackgroundUri();
        if (selectedUri != null) {
            try (InputStream inputStream = getContentResolver().openInputStream(selectedUri)) {
                Drawable drawable = Drawable.createFromStream(inputStream, selectedUri.toString());
                if (drawable != null) {
                    return drawable;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        try {
            return WallpaperManager.getInstance(this).getDrawable();
        } catch (RuntimeException e) {
            return makeWallpaperFallback();
        }
    }

    private void applyCurrentListBackground(ImageView target) {
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        target.setImageDrawable(loadCurrentListBackgroundDrawable());
    }

    private void refreshTopChromeBackground() {
        if (oneStepBackgroundView != null) {
            oneStepBackgroundView.refreshBackground();
        }
    }

    private void invalidateWindowPlaceholderBackgrounds() {
        for (OneStepWindowView windowView : windowViews) {
            if (windowView != null) {
                windowView.invalidatePlaceholderBackground();
            }
        }
    }

    private void showDesktopHomeInMain() {
        if (!desktopHomeRequestPending) {
            return;
        }
        if (activityDestroyed || activeMainSlot < 0 || activeMainSlot >= MAX_WINDOWS) {
            desktopHomeRequestPending = false;
            return;
        }
        if (isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0 || pendingMainAppStartSlot >= 0
                || pendingInternalSettingsSlot >= 0 || pendingDesktopHomeSlot >= 0) {
            mainHandler.removeCallbacks(showDesktopHomeRunnable);
            mainHandler.postDelayed(showDesktopHomeRunnable, 80L);
            return;
        }
        LauncherApp selectedDesktop = resolveBuiltInDesktopApp();
        if (selectedDesktop != null) {
            desktopHomeRequestPending = false;
            showBuiltInDesktopInMain(selectedDesktop);
            return;
        }
        int desktopSlot = findDesktopHomeSlot();
        if (desktopSlot == activeMainSlot) {
            desktopHomeRequestPending = false;
            return;
        }
        desktopHomeRequestPending = false;
        if (desktopSlot >= 0) {
            if (!embeddedSlotClosing[desktopSlot]) {
                switchMainSlot(desktopSlot, true);
            }
            return;
        }

        int emptySideSlot = findEmptySideSlot();
        boolean mainOccupied = windowApps[activeMainSlot] != null
                || isInternalSettingsSlot(activeMainSlot);
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                activeMainSlot, mainOccupied, emptySideSlot);
        switch (placement.action) {
            case START_IN_MAIN:
                windowViews[activeMainSlot].showDesktopHome();
                renderWindows();
                break;
            case START_IN_SIDE_AND_PROMOTE:
                stageDesktopHomeForMainPromotion(placement.targetSlot);
                break;
            case REPLACE_MAIN:
                replaceMainWithDesktopHome();
                break;
        }
    }

    private LauncherApp resolveBuiltInDesktopApp() {
        if (launcherAppRepository == null) {
            return null;
        }
        if (builtInDesktopApp == null) {
            loadBuiltInDesktopApps();
            updateSettingsPageViews();
            return builtInDesktopApp;
        }
        try {
            LauncherApp resolved = launcherAppRepository.loadHomeApp(
                    builtInDesktopApp.componentName);
            if (resolved != null) {
                builtInDesktopApp = resolved;
                return resolved;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Resolving selected built-in desktop failed", e);
        }
        loadBuiltInDesktopApps();
        updateSettingsPageViews();
        return builtInDesktopApp;
    }

    private void showBuiltInDesktopInMain(LauncherApp desktopApp) {
        suppressEmbeddedStarts = false;
        int desktopSlot = findSlotByComponent(desktopApp.componentName);
        if (desktopSlot < 0) {
            addOrFocusApp(desktopApp);
            return;
        }

        windowApps[desktopSlot] = desktopApp;
        windowViews[desktopSlot].hideDesktopHome();
        boolean moveExistingDesktop = desktopSlot != activeMainSlot
                && !embeddedSlotClosing[desktopSlot];
        if (moveExistingDesktop) {
            renderWindows();
            switchMainSlot(desktopSlot, true);
            return;
        }

        EmbeddedAppHost host = embeddedHosts[desktopSlot];
        // OEM task callbacks can repeat during a recents animation. Reuse the existing desktop
        // task so its live Surface is never replaced by the blurred placeholder.
        boolean live = host != null && host.start(desktopApp);
        boolean revealPending = host instanceof RootVirtualDisplayHost
                && ((RootVirtualDisplayHost) host).isHostedSurfaceRevealPending(desktopApp);
        windowViews[desktopSlot].setLiveAppVisible(live && !revealPending);
        renderWindows();
        if (!live && host != null) {
            showEmbeddingHintIfNeeded(host.getUnavailableReason());
        }
    }

    private void requestDesktopHomeInMain() {
        desktopHomeRequestPending = true;
        showDesktopHomeInMain();
    }

    private void stageDesktopHomeForMainPromotion(int slot) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || slot == activeMainSlot
                || windowApps[slot] != null || isInternalSettingsSlot(slot)
                || isDesktopHomeSlot(slot) || embeddedSlotClosing[slot]
                || !sideSlotOrder.contains(slot) || isWindowAnimationRunning()
                || mainSlotSwitchPendingSlot >= 0 || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        pendingDesktopHomeSlot = slot;
        embeddedSyncGenerations[slot]++;
        windowViews[slot].setLiveAppVisible(false);
        switchMainSlot(slot, true);
    }

    private boolean isPendingDesktopHomeSlot(int slot) {
        return slot >= 0 && slot == pendingDesktopHomeSlot;
    }

    private void showPendingDesktopHomeAfterPromotion(int slot) {
        if (!isPendingDesktopHomeSlot(slot) || slot != activeMainSlot) {
            return;
        }
        pendingDesktopHomeSlot = -1;
        windowViews[slot].showDesktopHome();
        animateDesktopHomeAppear(slot);
    }

    private void replaceMainWithDesktopHome() {
        int slot = activeMainSlot;
        LauncherApp previousApp = windowApps[slot];
        EmbeddedAppHost previousHost = embeddedHosts[slot];
        boolean previousSettings = isInternalSettingsSlot(slot);
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null) {
            return;
        }
        if (previousApp == null && !previousSettings) {
            windowView.showDesktopHome();
            renderWindows();
            return;
        }

        if (previousApp != null && previousHost != null) {
            previousHost.invalidateTaskResolution();
        }
        mainSlotSwitchGeneration++;
        clearPendingMainSlotSwitch();
        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.animate()
                .alpha(0f)
                .setDuration(MAIN_APP_REPLACE_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (replacementGeneration != mainContentReplacementGeneration) {
                        return;
                    }
                    mainContentReplacementPendingSlot = -1;
                    boolean stillCurrent = previousApp != null
                            ? windowApps[slot] == previousApp
                            : previousSettings && isInternalSettingsSlot(slot);
                    if (activityDestroyed || slot != activeMainSlot || !stillCurrent) {
                        windowView.setAlpha(1f);
                        return;
                    }
                    if (previousApp != null) {
                        embeddedSyncGenerations[slot]++;
                        if (previousHost != null) {
                            previousHost.sendHome();
                        }
                        windowApps[slot] = null;
                        clearHostedAppRevealState(slot);
                    }
                    if (previousSettings) {
                        hideInternalSettingsPage();
                    }
                    renderWindows();
                    windowView.showDesktopHome();
                    Log.i(TAG, "Show desktop HOME in main slot " + slot);
                    animateDesktopHomeAppear(slot);
                })
                .start();
    }

    private boolean isDesktopHomeSlot(int slot) {
        return slot >= 0 && slot < MAX_WINDOWS && windowViews[slot] != null
                && windowViews[slot].isDesktopHomeShown();
    }

    private int findDesktopHomeSlot() {
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (isDesktopHomeSlot(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isDisplayedMainDesktopSlot(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp app = windowApps[slot];
        return isDesktopHomeSlot(slot) || (app != null && app.isHomeEntry());
    }

    private int findDisplayedMainDesktopSlot() {
        if (isDisplayedMainDesktopSlot(activeMainSlot)) {
            return activeMainSlot;
        }
        for (int slot : sideSlotOrder) {
            if (isWindowSlotEnabled(slot) && isDisplayedMainDesktopSlot(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private void showInternalSettingsPage() {
        if (settingsPanelController == null || activeMainSlot < 0
                || activeMainSlot >= MAX_WINDOWS || isWindowAnimationRunning()
                || mainSlotSwitchPendingSlot >= 0 || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        if (isInternalSettingsVisible()) {
            int settingsSlot = findInternalSettingsSlot();
            if (settingsSlot >= 0 && settingsSlot != activeMainSlot) {
                switchMainSlot(settingsSlot, true);
            } else {
                settingsPanelController.showInWindow(windowViews[activeMainSlot]);
            }
            return;
        }
        int emptySideSlot = findEmptySideSlot();
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                activeMainSlot, windowApps[activeMainSlot] != null
                        || isDesktopHomeSlot(activeMainSlot), emptySideSlot);
        if (placement.action == AppLaunchPlacement.Action.START_IN_SIDE_AND_PROMOTE) {
            stageInternalSettingsForMainPromotion(placement.targetSlot);
            return;
        }
        if (placement.action == AppLaunchPlacement.Action.REPLACE_MAIN) {
            replaceMainWithInternalSettings();
            return;
        }
        settingsPanelController.showInWindow(windowViews[activeMainSlot]);
        animateInternalSettingsAppear(activeMainSlot);
    }

    private void hideInternalSettingsPage() {
        pendingInternalSettingsSlot = -1;
        settingsPanelController.hide();
    }

    private boolean isInternalSettingsVisible() {
        return settingsPanelController != null && settingsPanelController.isVisible();
    }

    private boolean isInternalSettingsSlot(int slot) {
        return settingsPanelController != null && slot >= 0 && slot < MAX_WINDOWS
                && settingsPanelController.isShownInWindow(windowViews[slot]);
    }

    private int findInternalSettingsSlot() {
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (isInternalSettingsSlot(slot)) {
                return slot;
            }
        }
        return -1;
    }

    private void updateSettingsPageViews() {
        if (settingsPanelController != null) {
            settingsPanelController.refresh();
        }
    }

    private void saveGridLayout(int rows, int columns) {
        if (!isSupportedGridLayout(rows, columns)) {
            return;
        }
        desktopGridRows = rows;
        desktopGridColumns = columns;
        settingsStore.saveGridLayout(rows, columns);
        updateSettingsPageViews();
        rebuildDesktopHomeViews();
    }

    private void saveOneStepTriggerAreaScale(int scalePct) {
        int sanitized = sanitizeOneStepTriggerAreaScale(scalePct);
        if (sanitized != oneStepTriggerAreaScalePct) {
            oneStepTriggerAreaScalePct = sanitized;
            settingsStore.saveOneStepTriggerAreaScale(sanitized);
            updateCornerTriggerBounds();
            updateSettingsPageViews();
        }
    }

    private void saveCornerTriggerSensitivity(int sensitivityPct) {
        int sanitized = sanitizeCornerTriggerSensitivity(sensitivityPct);
        if (sanitized == cornerTriggerSensitivityPct) {
            return;
        }
        cornerTriggerSensitivityPct = sanitized;
        settingsStore.saveCornerTriggerSensitivity(sanitized);
        updateSettingsPageViews();
    }

    private void saveTopAppIconScale(int scalePct) {
        int sanitized = sanitizeTopAppIconScale(scalePct);
        if (sanitized == topAppIconScalePct) {
            return;
        }
        topAppIconScalePct = sanitized;
        settingsStore.saveTopAppIconScale(sanitized);
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void saveTopAppStripSpacingScale(int scalePct) {
        int sanitized = sanitizeTopAppStripSpacingScale(scalePct);
        if (sanitized == topAppStripSpacingScalePct) {
            return;
        }
        topAppStripSpacingScalePct = sanitized;
        settingsStore.saveTopAppStripSpacingScale(sanitized);
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void saveTopAppStripVerticalPaddingScale(int scalePct) {
        int sanitized = sanitizeTopAppStripVerticalPaddingScale(scalePct);
        if (sanitized == topAppStripVerticalPaddingScalePct) {
            return;
        }
        topAppStripVerticalPaddingScalePct = sanitized;
        settingsStore.saveTopAppStripVerticalPaddingScale(sanitized);
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void saveTopComponentsVisible(boolean visible) {
        if (visible == topComponentsVisible) {
            return;
        }
        topComponentsVisible = visible;
        settingsStore.saveTopComponentsVisible(visible);
        if (!visible) {
            requestPipUndock();
        }
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void saveStatusBarSpacingEnabled(boolean enabled) {
        if (enabled == statusBarSpacingEnabled) {
            return;
        }
        statusBarSpacingEnabled = enabled;
        settingsStore.saveStatusBarSpacingEnabled(enabled);
        applyStatusBarForCurrentMode();
        updateSettingsPageViews();
        rebuildTopChromeContent();
        scheduleEmbeddedSlotRefresh();
    }

    private void saveVerticalWindowLayout(boolean enabled) {
        if (enabled == verticalWindowLayout) {
            return;
        }
        verticalWindowLayout = enabled;
        settingsStore.saveVerticalWindowLayout(enabled);
        enforceSideWindowCountLimit();
        updateSettingsPageViews();
        rebuildTopChromeContent();
        scheduleEmbeddedSlotRefresh();
    }

    private void saveLogRecordingEnabled(boolean enabled) {
        if (enabled == logRecordingEnabled) {
            return;
        }
        logRecordingEnabled = enabled;
        settingsStore.saveLogRecordingEnabled(enabled);
        if (enabled) {
            startSessionLogRecording();
        } else {
            stopSessionLogRecording();
        }
        updateSettingsPageViews();
    }

    private void startSessionLogRecording() {
        if (sessionLogRecorder != null) {
            return;
        }
        sessionLogRecorder = new SessionLogRecorder(getApplicationContext());
        sessionLogRecorder.start();
    }

    private void stopSessionLogRecording() {
        SessionLogRecorder recorder = sessionLogRecorder;
        sessionLogRecorder = null;
        if (recorder != null) {
            recorder.close();
        }
    }

    private void saveSideWindowCount(int count) {
        int sanitized = sanitizeSideWindowCount(count);
        if (!canUseSideWindowCount(sanitized)) {
            Toast.makeText(this, "不支持该小窗口数量",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (sanitized == sideWindowCount) {
            return;
        }
        sideWindowCount = sanitized;
        settingsStore.saveSideWindowCount(sanitized);
        updateSettingsPageViews();
        applyWindowLayout(true);
        scheduleEmbeddedSlotRefresh();
    }

    private void enforceSideWindowCountLimit() {
        int sanitized = sanitizeAllowedSideWindowCount(sideWindowCount);
        if (sanitized == sideWindowCount) {
            return;
        }
        sideWindowCount = sanitized;
        settingsStore.saveSideWindowCount(sanitized);
    }

    private void saveTopNavVerticalMarginScale(int scalePct) {
        int sanitized = sanitizeTopNavVerticalMarginScale(scalePct);
        if (sanitized == topNavVerticalMarginScalePct) {
            return;
        }
        topNavVerticalMarginScalePct = sanitized;
        settingsStore.saveTopNavVerticalMarginScale(sanitized);
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void pickBackgroundFromGallery() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_BACKGROUND);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(fallback, REQUEST_PICK_BACKGROUND);
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "找不到相册应用", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveSelectedBackground(Uri uri, int flags) {
        if (uri == null) {
            return;
        }
        int readFlag = flags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (readFlag != 0) {
            try {
                getContentResolver().takePersistableUriPermission(uri, readFlag);
            } catch (RuntimeException ignored) {
            }
        }
        settingsStore.saveBackgroundUri(uri);
        updateSettingsPageViews();
        refreshTopChromeBackground();
        rebuildDesktopHomeViews();
        syncSelectedBackgroundToSystem(uri);
    }

    private void syncSelectedBackgroundToSystem(Uri uri) {
        try {
            wallpaperExecutor.execute(() -> {
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                    if (inputStream == null) {
                        throw new IOException("Background image stream unavailable");
                    }
                    WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                    if (!wallpaperManager.isWallpaperSupported()
                            || !wallpaperManager.isSetWallpaperAllowed()) {
                        throw new SecurityException("Setting the system wallpaper is disabled");
                    }
                    wallpaperManager.setStream(inputStream, null, true,
                            WallpaperManager.FLAG_SYSTEM);
                    Log.i(TAG, "System wallpaper synchronized with OneStep background");
                } catch (IOException | RuntimeException e) {
                    Log.w(TAG, "Synchronize system wallpaper failed: "
                            + e.getClass().getSimpleName(), e);
                    mainHandler.post(() -> {
                        if (!activityDestroyed) {
                            Toast.makeText(this, "系统壁纸同步失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Schedule system wallpaper synchronization failed: "
                    + e.getClass().getSimpleName(), e);
        }
    }

    private void rebuildDesktopHomeViews() {
        for (OneStepWindowView windowView : windowViews) {
            if (windowView != null) {
                windowView.rebuildDesktopHomeIfNeeded();
            }
        }
        renderWindows();
    }

    private LinearLayout createAppRows(int startIndex, int count, int columns, int cellHeightDp,
                                      int iconSizeDp, float textSizeDp, boolean showLabel) {
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);

        int available = Math.min(count, Math.max(0, launcherApps.size() - startIndex));
        int rowCount = Math.max(1, (available + columns - 1) / columns);
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER);
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(cellHeightDp)));

            for (int column = 0; column < columns; column++) {
                int appIndex = startIndex + rowIndex * columns + column;
                FrameLayout cell = new FrameLayout(this);
                row.addView(cell, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1f));

                if (appIndex >= launcherApps.size() || appIndex >= startIndex + count) {
                    continue;
                }

                LauncherApp app = launcherApps.get(appIndex);
                AppShortcutView shortcut = new AppShortcutView(this, showLabel, iconSizeDp, textSizeDp);
                shortcut.bind(app);
                shortcut.setOnClickListener(v -> addOrFocusApp(app));
                cell.addView(shortcut, matchFrame());
                shortcutViews.add(shortcut);
            }
        }
        return rows;
    }

    private void createEmbeddedHosts() {
        for (int i = 0; i < MAX_WINDOWS; i++) {
            installFreshEmbeddedHost(i);
        }
    }

    private void prewarmRootInputBridge() {
        for (EmbeddedAppHost host : embeddedHosts) {
            if (!(host instanceof RootVirtualDisplayHost)) {
                continue;
            }
            RootVirtualDisplayHost rootHost = (RootVirtualDisplayHost) host;
            if (!rootHost.hasRootAccess()) {
                return;
            }
            rootHost.ensureRootInputBridgeStarted();
            return;
        }
    }

    private void startPipMonitoring() {
        if (activityDestroyed || pipMonitoringActive) {
            return;
        }
        pipMonitoringActive = true;
        pipMonitorGeneration++;
        mainHandler.removeCallbacks(pipMonitorRunnable);
        mainHandler.post(pipMonitorRunnable);
    }

    private void stopPipMonitoring(boolean restorePosition) {
        pipMonitoringActive = false;
        pipMonitorGeneration++;
        pipQueryInFlight = false;
        mainHandler.removeCallbacks(pipMonitorRunnable);
        mainHandler.removeCallbacks(pipDockBoundsUpdateRunnable);
        if (restorePosition) {
            requestPipUndock();
        }
    }

    private void queryPipStateAsync() {
        if (!pipMonitoringActive || activityDestroyed || pipQueryInFlight) {
            return;
        }
        RootVirtualDisplayHost host = findPipBridgeHost();
        if (host == null) {
            scheduleNextPipQuery(PIP_MONITOR_RETRY_INTERVAL_MS);
            return;
        }
        final int generation = pipMonitorGeneration;
        pipQueryInFlight = true;
        try {
            pipDockExecutor.execute(() -> {
                PinnedTaskState state = host.queryPinnedTaskState();
                mainHandler.post(() -> {
                    if (!pipMonitoringActive || activityDestroyed
                            || generation != pipMonitorGeneration) {
                        return;
                    }
                    pipQueryInFlight = false;
                    if (state != null) {
                        applyPinnedTaskState(state);
                    }
                    scheduleNextPipQuery(state == null || !state.active
                            ? PIP_MONITOR_RETRY_INTERVAL_MS : PIP_MONITOR_INTERVAL_MS);
                });
            });
        } catch (RuntimeException e) {
            pipQueryInFlight = false;
            scheduleNextPipQuery(PIP_MONITOR_RETRY_INTERVAL_MS);
        }
    }

    private void scheduleNextPipQuery(long delayMs) {
        if (!pipMonitoringActive || activityDestroyed) {
            return;
        }
        mainHandler.removeCallbacks(pipMonitorRunnable);
        mainHandler.postDelayed(pipMonitorRunnable, delayMs);
    }

    private void applyPinnedTaskState(PinnedTaskState state) {
        if (!state.active) {
            boolean wasActive = pipActive;
            pipActive = false;
            pipDockApplied = false;
            pipTaskId = -1;
            pipRestoreBounds.setEmpty();
            if (wasActive) {
                rebuildTopChromeContent();
            }
            return;
        }

        boolean taskChanged = pipTaskId != state.taskId;
        float previousAspectRatio = getPipAspectRatio();
        if (taskChanged || (!pipDockApplied && !multiWindowMode)) {
            pipRestoreBounds.set(state.bounds);
        }
        boolean aspectRatioChanged = Math.abs(previousAspectRatio - getPipAspectRatio()) > 0.01f;
        boolean needsRebuild = !pipActive || taskChanged || aspectRatioChanged;
        pipActive = true;
        pipTaskId = state.taskId;
        if (taskChanged) {
            pipDockApplied = false;
        }
        if (needsRebuild) {
            rebuildTopChromeContent();
        }
        schedulePipDockBoundsUpdate();
    }

    private RootVirtualDisplayHost findPipBridgeHost() {
        for (EmbeddedAppHost host : embeddedHosts) {
            if (host instanceof RootVirtualDisplayHost
                    && ((RootVirtualDisplayHost) host).hasRootAccess()) {
                return (RootVirtualDisplayHost) host;
            }
        }
        return null;
    }

    private void schedulePipDockBoundsUpdate() {
        mainHandler.removeCallbacks(pipDockBoundsUpdateRunnable);
        if (!shouldDockPip()) {
            return;
        }
        mainHandler.post(pipDockBoundsUpdateRunnable);
    }

    private boolean shouldDockPip() {
        FrameLayout pipDockSlot = topPanelController == null
                ? null : topPanelController.getPipDockSlot();
        return pipMonitoringActive && pipActive && pipTaskId > 0
                && multiWindowMode && !exitOneStepPending && pipDockSlot != null;
    }

    private void requestPipDockFromSlot() {
        if (!shouldDockPip() || pipDockInFlight || topChromeContainer == null
                || Math.abs(topChromeContainer.getTranslationY()) > 0.5f) {
            return;
        }
        Rect targetBounds = new Rect();
        FrameLayout pipDockSlot = topPanelController.getPipDockSlot();
        if (pipDockSlot == null || !pipDockSlot.getGlobalVisibleRect(targetBounds)
                || targetBounds.width() < dp(48) || targetBounds.height() < dp(48)
                || pipRestoreBounds.isEmpty()) {
            return;
        }
        RootVirtualDisplayHost host = findPipBridgeHost();
        if (host == null) {
            return;
        }
        final int taskId = pipTaskId;
        final Rect restoreBounds = new Rect(pipRestoreBounds);
        final Rect requestedBounds = new Rect(targetBounds);
        pipDockInFlight = true;
        try {
            pipDockExecutor.execute(() -> {
                boolean success = host.dockPinnedTask(taskId, requestedBounds, restoreBounds);
                mainHandler.post(() -> {
                    pipDockInFlight = false;
                    if (activityDestroyed || taskId != pipTaskId) {
                        return;
                    }
                    if (success && shouldDockPip()) {
                        pipDockApplied = true;
                    }
                });
            });
        } catch (RuntimeException e) {
            pipDockInFlight = false;
        }
    }

    private void requestPipUndock() {
        if (pipTaskId <= 0 || pipRestoreBounds.isEmpty()) {
            pipDockApplied = false;
            return;
        }
        RootVirtualDisplayHost host = findPipBridgeHost();
        if (host == null) {
            return;
        }
        final int taskId = pipTaskId;
        final Rect restoreBounds = new Rect(pipRestoreBounds);
        pipDockApplied = false;
        try {
            pipDockExecutor.execute(() -> host.undockPinnedTask(taskId, restoreBounds));
        } catch (RuntimeException ignored) {
        }
    }

    private void installFreshEmbeddedHost(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || windowViews[slot] == null) {
            return;
        }
        EmbeddedAppHost host = createEmbeddedHost(this, slot);
        embeddedHosts[slot] = host;
        if (host.isAvailable()) {
            windowViews[slot].attachEmbeddedHost(host.getView());
            Log.i(TAG, "Embedded host ready for slot " + slot);
        } else {
            Log.w(TAG, "Embedded host unavailable for slot " + slot + ": "
                    + host.getUnavailableReason());
        }
    }

    private EmbeddedAppHost createEmbeddedHost(Context context, int slot) {
        EmbeddedAppHost rootHost = new RootVirtualDisplayHost(
                this, context, slot, rootVirtualDisplayCallbacks);
        if (rootHost.isAvailable()) {
            Log.i(TAG, "Using high-resolution virtual display host for slot " + slot);
            return rootHost;
        }
        Log.i(TAG, "Root virtual display host unavailable for slot " + slot + ": "
                + rootHost.getUnavailableReason());

        if (hasGrantedSystemEmbeddingPermission()) {
            EmbeddedAppHost systemHost = createHiddenActivityViewHost(context);
            if (systemHost.isAvailable()) {
                Log.i(TAG, "Using system ActivityView host for slot " + slot);
                return systemHost;
            }
            Log.i(TAG, "System ActivityView host unavailable for slot " + slot + ": "
                    + systemHost.getUnavailableReason());
        }

        return createHiddenActivityViewHost(context);
    }

    private HiddenActivityViewHost createHiddenActivityViewHost(Context context) {
        return new HiddenActivityViewHost(
                context, () -> !suppressEmbeddedStarts, this::closeHiddenActivityViewApp);
    }

    private void closeHiddenActivityViewApp(LauncherApp app, Runnable onClosed) {
        Runnable finish = () -> {
            if (onClosed != null) {
                onClosed.run();
            }
        };
        String packageName = app == null ? "" : app.packageName;
        if (TextUtils.isEmpty(packageName)) {
            mainHandler.post(finish);
            return;
        }
        if (!DismissedAppClosePolicy.shouldForceStop(app.isHomeEntry())) {
            Log.i(TAG, "Keep HOME package running after ActivityView dismissal: "
                    + packageName);
            mainHandler.post(finish);
            return;
        }
        try {
            mediaRootExecutor.execute(() -> {
                int userId = app.userId();
                ShellCommandResult result = runMainPrivilegedCommand(
                        "am force-stop --user " + userId + " " + mainShellQuote(packageName),
                        "force-stop dismissed ActivityView app " + packageName, true);
                if (!result.isSuccess() && app.isCurrentUser()) {
                    result = runMainPrivilegedCommand(
                            "am force-stop " + mainShellQuote(packageName),
                            "fallback force-stop dismissed ActivityView app " + packageName, true);
                }
                if (!result.isSuccess()) {
                    Log.e(TAG, "Force-stop dismissed ActivityView app failed: "
                            + packageName + " exit=" + result.exitCode);
                }
                mainHandler.post(finish);
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue dismissed ActivityView close failed: "
                    + e.getClass().getSimpleName());
            mainHandler.post(finish);
        }
    }

    private boolean onCrossAppLaunch(int sourceDisplayId, String sourcePackage,
                                     Intent intent, String targetPackage) {
        if (activityDestroyed || intent == null || TextUtils.isEmpty(sourcePackage)
                || TextUtils.isEmpty(targetPackage)
                || TextUtils.equals(sourcePackage, targetPackage)
                || TextUtils.equals(getPackageName(), targetPackage)) {
            return false;
        }
        RootVirtualDisplayHost sourceHost = findRootVirtualDisplayHost(sourceDisplayId);
        if (sourceHost == null) {
            return false;
        }
        int sourceSlot = sourceHost.getSlot();
        LauncherApp sourceApp = sourceSlot >= 0 && sourceSlot < MAX_WINDOWS
                ? windowApps[sourceSlot] : null;
        if (sourceApp == null || !TextUtils.equals(sourceApp.packageName, sourcePackage)) {
            return false;
        }
        LauncherApp targetApp = createLauncherAppForPackage(targetPackage);
        if (targetApp == null) {
            return false;
        }
        ComponentName component = intent.getComponent();
        if (component != null && !TextUtils.equals(component.getPackageName(), targetPackage)) {
            return false;
        }
        RoutedAppLaunch routedLaunch = new RoutedAppLaunch(
                sourceSlot, sourcePackage, targetApp, new Intent(intent));
        return mainHandler.post(() -> enqueueCrossAppRoute(routedLaunch));
    }

    private void enqueueCrossAppRoute(RoutedAppLaunch routedLaunch) {
        if (activityDestroyed || routedLaunch == null) {
            return;
        }
        while (pendingCrossAppRoutes.size() >= MAX_PENDING_CROSS_APP_ROUTES) {
            pendingCrossAppRoutes.removeFirst();
        }
        pendingCrossAppRoutes.addLast(routedLaunch);
        mainHandler.removeCallbacks(drainCrossAppRoutesRunnable);
        mainHandler.post(drainCrossAppRoutesRunnable);
    }

    private void drainCrossAppRoutes() {
        if (activityDestroyed || pendingCrossAppRoutes.isEmpty()) {
            return;
        }
        if (isCrossAppRouteUiBusy()) {
            mainHandler.postDelayed(drainCrossAppRoutesRunnable, CROSS_APP_ROUTE_RETRY_MS);
            return;
        }
        RoutedAppLaunch launch = pendingCrossAppRoutes.peekFirst();
        LauncherApp currentSource = launch.sourceSlot >= 0 && launch.sourceSlot < MAX_WINDOWS
                ? windowApps[launch.sourceSlot] : null;
        if (currentSource != null
                && TextUtils.equals(currentSource.packageName, launch.sourcePackage)
                && launch.sourceSlot != activeMainSlot) {
            switchMainSlot(launch.sourceSlot, true);
            mainHandler.postDelayed(drainCrossAppRoutesRunnable, CROSS_APP_ROUTE_RETRY_MS);
            return;
        }

        pendingCrossAppRoutes.removeFirst();
        routedLaunchIntents.put(launch.targetApp.packageName, launch.intent);
        int existingSlot = findSlot(launch.targetApp);
        if (existingSlot >= 0 && existingSlot != activeMainSlot
                && !embeddedSlotClosing[existingSlot]) {
            switchMainSlot(existingSlot, true);
        } else {
            addOrFocusApp(launch.targetApp);
        }
        Log.i(TAG, "Route cross-app launch inside OneStep: source=" + launch.sourcePackage
                + " sourceSlot=" + launch.sourceSlot
                + " target=" + launch.targetApp.packageName
                + " existingSlot=" + existingSlot);
        if (!pendingCrossAppRoutes.isEmpty()) {
            mainHandler.postDelayed(drainCrossAppRoutesRunnable, CROSS_APP_ROUTE_RETRY_MS);
        }
    }

    private boolean isCrossAppRouteUiBusy() {
        return isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0 || pendingMainAppStartSlot >= 0
                || pendingInternalSettingsSlot >= 0 || pendingDesktopHomeSlot >= 0;
    }

    private Intent consumeRoutedLaunchIntent(int slot, String packageName) {
        if (slot < 0 || slot >= MAX_WINDOWS || TextUtils.isEmpty(packageName)) {
            return null;
        }
        LauncherApp app = windowApps[slot];
        if (app == null || !TextUtils.equals(app.packageName, packageName)) {
            return null;
        }
        return routedLaunchIntents.remove(packageName);
    }

    private void addOrFocusApp(LauncherApp app) {
        if (app == null || isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0 || pendingMainAppStartSlot >= 0
                || pendingInternalSettingsSlot >= 0 || pendingDesktopHomeSlot >= 0) {
            return;
        }
        boolean replaceMainDesktopHome = isDesktopHomeSlot(activeMainSlot);
        boolean replaceMainSettings = isInternalSettingsSlot(activeMainSlot);
        int existingSlot = findSlot(app);
        if (replaceMainDesktopHome) {
            if (existingSlot >= 0 && embeddedSlotClosing[existingSlot]) {
                return;
            }
            if (existingSlot >= 0 && existingSlot != activeMainSlot) {
                switchMainSlot(existingSlot, true);
                return;
            }
            suppressEmbeddedStarts = false;
            replaceDesktopHomeWithApp(app);
            return;
        }
        if (replaceMainSettings) {
            if (existingSlot >= 0 && embeddedSlotClosing[existingSlot]) {
                return;
            }
            if (existingSlot >= 0 && existingSlot != activeMainSlot) {
                switchMainSlot(existingSlot, true);
                return;
            }
            int mainDesktopSlot = findDisplayedMainDesktopSlot();
            if (mainDesktopSlot >= 0 && mainDesktopSlot != activeMainSlot) {
                suppressEmbeddedStarts = false;
                stageAppForMainDesktopReplacement(mainDesktopSlot, app);
                return;
            }
            int emptySideSlot = findEmptySideSlot();
            if (emptySideSlot >= 0) {
                stageAppForMainPromotion(emptySideSlot, app);
                return;
            }
            replaceInternalSettingsWithApp(app, existingSlot);
            return;
        }
        if (existingSlot >= 0) {
            if (embeddedSlotClosing[existingSlot]) {
                return;
            }
            if (existingSlot != activeMainSlot) {
                switchMainSlot(existingSlot, true);
                return;
            }
            renderWindows();
            if (embeddedHosts[existingSlot] instanceof RootVirtualDisplayHost) {
                syncEmbeddedSlot(existingSlot);
            }
            return;
        }

        suppressEmbeddedStarts = false;
        int emptySideSlot = findEmptySideSlot();
        boolean mainOccupied = windowApps[activeMainSlot] != null;
        int mainDesktopSlot = findDisplayedMainDesktopSlot();
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                activeMainSlot, mainOccupied, emptySideSlot, mainDesktopSlot);
        switch (placement.action) {
            case START_IN_MAIN:
                startAppInSlot(placement.targetSlot, app);
                break;
            case START_IN_SIDE_AND_PROMOTE:
                stageAppForMainPromotion(placement.targetSlot, app);
                break;
            case REPLACE_SIDE_AND_PROMOTE:
                stageAppForMainDesktopReplacement(placement.targetSlot, app);
                break;
            case REPLACE_MAIN:
                if (isDesktopHomeSlot(placement.targetSlot)) {
                    replaceDesktopHomeWithApp(app);
                } else {
                    replaceAppInSlot(placement.targetSlot, app);
                }
                break;
        }
    }

    private void startAppInSlot(int slot, LauncherApp app) {
        startAppInSlot(slot, app, true, false);
    }

    private void stageAppForMainPromotion(int slot, LauncherApp app) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || app == null
                || slot == activeMainSlot || windowApps[slot] != null
                || embeddedSlotClosing[slot] || !sideSlotOrder.contains(slot)
                || isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        pendingMainAppStartSlot = slot;
        pendingMainAppStart = app;
        embeddedSyncGenerations[slot]++;
        windowViews[slot].setLiveAppVisible(false);
        switchMainSlot(slot, true);
        if (isPendingMainAppStartSlot(slot)) {
            clearPendingMainAppStart(slot);
            startAppInSlot(slot, app, false, false);
        }
    }

    private boolean isPendingMainAppStartSlot(int slot) {
        return slot >= 0 && slot == pendingMainAppStartSlot && pendingMainAppStart != null;
    }

    private void startPendingMainAppAfterPromotion(int slot) {
        if (!isPendingMainAppStartSlot(slot) || slot != activeMainSlot) {
            return;
        }
        LauncherApp app = pendingMainAppStart;
        clearPendingMainAppStart(slot);
        if (isDesktopHomeSlot(slot)) {
            replaceDesktopHomeWithApp(app);
            return;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp != null && currentApp.isHomeEntry()) {
            replaceAppInSlot(slot, app);
            return;
        }
        startAppInSlot(slot, app, false, false);
    }

    private void stageAppForMainDesktopReplacement(int slot, LauncherApp app) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || app == null
                || slot == activeMainSlot || !isDisplayedMainDesktopSlot(slot)
                || embeddedSlotClosing[slot]
                || !sideSlotOrder.contains(slot) || isWindowAnimationRunning()
                || mainSlotSwitchPendingSlot >= 0 || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        pendingMainAppStartSlot = slot;
        pendingMainAppStart = app;
        embeddedSyncGenerations[slot]++;
        if (isDesktopHomeSlot(slot)) {
            windowViews[slot].setLiveAppVisible(false);
        }
        switchMainSlot(slot, true);
    }

    private void clearPendingMainAppStart(int slot) {
        if (slot != pendingMainAppStartSlot) {
            return;
        }
        pendingMainAppStartSlot = -1;
        pendingMainAppStart = null;
    }

    private void stageInternalSettingsForMainPromotion(int slot) {
        if (activityDestroyed || settingsPanelController == null || slot < 0
                || slot >= MAX_WINDOWS || slot == activeMainSlot || windowApps[slot] != null
                || embeddedSlotClosing[slot] || !sideSlotOrder.contains(slot)
                || isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        pendingInternalSettingsSlot = slot;
        embeddedSyncGenerations[slot]++;
        windowViews[slot].setLiveAppVisible(false);
        switchMainSlot(slot, true);
    }

    private boolean isPendingInternalSettingsSlot(int slot) {
        return slot >= 0 && slot == pendingInternalSettingsSlot;
    }

    private void showPendingInternalSettingsAfterPromotion(int slot) {
        if (!isPendingInternalSettingsSlot(slot) || slot != activeMainSlot
                || settingsPanelController == null) {
            return;
        }
        pendingInternalSettingsSlot = -1;
        settingsPanelController.showInWindow(windowViews[slot]);
        animateInternalSettingsAppear(slot);
    }

    private void clearPendingMainPromotionContent(int slot) {
        clearPendingMainAppStart(slot);
        if (slot == pendingInternalSettingsSlot) {
            pendingInternalSettingsSlot = -1;
        }
        if (slot == pendingDesktopHomeSlot) {
            pendingDesktopHomeSlot = -1;
        }
    }

    private void startAppInSlot(int slot, LauncherApp app, boolean animateAppearance,
                                boolean fadeIn) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || app == null) {
            return;
        }
        windowViews[slot].hideDesktopHome();
        windowApps[slot] = app;
        renderWindows();
        syncEmbeddedSlot(slot);
        if (animateAppearance) {
            animateWindowAppAppear(slot, app, fadeIn);
        }
    }

    private void replaceAppInSlot(int slot, LauncherApp replacementApp) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || replacementApp == null
                || embeddedSlotClosing[slot]) {
            return;
        }
        LauncherApp previousApp = windowApps[slot];
        EmbeddedAppHost previousHost = embeddedHosts[slot];
        if (previousApp == null || previousHost == null) {
            startAppInSlot(slot, replacementApp);
            return;
        }
        if (previousApp.isSameInstance(replacementApp)) {
            startAppInSlot(slot, replacementApp);
            return;
        }

        if (slot == activeMainSlot) {
            previousHost.invalidateTaskResolution();
            mainSlotSwitchGeneration++;
            clearPendingMainSlotSwitch();
            launchMainAppReplacement(slot, previousApp, previousHost, replacementApp);
            return;
        }

        embeddedSlotClosing[slot] = true;
        embeddedSyncGenerations[slot]++;
        previousHost.invalidateTaskResolution();
        windowViews[slot].setLiveAppVisible(false);

        Runnable onClosed = () -> mainHandler.post(() -> {
            if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS
                    || embeddedHosts[slot] != previousHost
                    || windowApps[slot] != previousApp) {
                return;
            }
            embeddedSlotClosing[slot] = false;
            startAppInSlot(slot, replacementApp);
        });
        try {
            previousHost.closeApp(previousApp, onClosed);
        } catch (RuntimeException e) {
            Log.w(TAG, "Replace app close failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            onClosed.run();
        }
    }

    private void launchMainAppReplacement(int slot, LauncherApp previousApp,
                                          EmbeddedAppHost previousHost,
                                          LauncherApp replacementApp) {
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null) {
            startAppInSlot(slot, replacementApp);
            return;
        }
        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setAlpha(1f);
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        beginMainAppReplacementLaunch(slot, previousApp, previousHost,
                replacementApp, windowView, replacementGeneration);
    }

    private void beginMainAppReplacementLaunch(
            int slot, LauncherApp previousApp, EmbeddedAppHost previousHost,
            LauncherApp replacementApp, OneStepWindowView windowView,
            int replacementGeneration) {
        if (replacementGeneration != mainContentReplacementGeneration) {
            return;
        }
        if (activityDestroyed || slot != activeMainSlot
                || embeddedHosts[slot] != previousHost
                || windowApps[slot] != previousApp) {
            mainContentReplacementPendingSlot = -1;
            return;
        }
        embeddedSyncGenerations[slot]++;
        windowApps[slot] = replacementApp;
        // Do not cover or hide this SurfaceView. WindowManager owns the immediate target
        // StartingWindow/task surface and swaps it with the app's first real buffer.
        renderWindows();
        setHostedSurfaceAlpha(slot, 1f);
        windowView.setLiveAppVisible(true);
        boolean launchStarted = previousHost.start(replacementApp);
        if (!launchStarted) {
            windowApps[slot] = previousApp;
            mainContentReplacementPendingSlot = -1;
            windowView.setLiveAppVisible(true);
            setHostedSurfaceAlpha(slot, 1f);
            renderWindows();
            showEmbeddingHintIfNeeded(previousHost.getUnavailableReason());
            return;
        }
        mainContentReplacementPendingSlot = -1;
        Log.i(TAG, "Launch replacement with WindowManager starting surface: previous="
                + previousApp.packageName + ", replacement="
                + replacementApp.packageName + ", slot=" + slot);
    }

    private void replaceDesktopHomeWithApp(LauncherApp app) {
        int slot = activeMainSlot;
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null || !isDesktopHomeSlot(slot)) {
            startAppInSlot(slot, app);
            return;
        }

        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.animate()
                .alpha(0f)
                .setDuration(MAIN_APP_REPLACE_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (replacementGeneration != mainContentReplacementGeneration) {
                        return;
                    }
                    mainContentReplacementPendingSlot = -1;
                    if (activityDestroyed || slot != activeMainSlot
                            || !isDesktopHomeSlot(slot)) {
                        windowView.setAlpha(1f);
                        return;
                    }
                    windowView.hideDesktopHome();
                    startAppInSlot(slot, app, true, true);
                })
                .start();
    }

    private void replaceMainWithInternalSettings() {
        int slot = activeMainSlot;
        if (isDesktopHomeSlot(slot)) {
            replaceDesktopHomeWithInternalSettings();
            return;
        }
        LauncherApp previousApp = windowApps[slot];
        EmbeddedAppHost previousHost = embeddedHosts[slot];
        OneStepWindowView windowView = windowViews[slot];
        if (previousApp == null || previousHost == null || windowView == null) {
            windowApps[slot] = null;
            clearHostedAppRevealState(slot);
            renderWindows();
            settingsPanelController.showInWindow(windowView);
            animateInternalSettingsAppear(slot);
            return;
        }

        previousHost.invalidateTaskResolution();
        mainSlotSwitchGeneration++;
        clearPendingMainSlotSwitch();
        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.animate()
                .alpha(0f)
                .setDuration(MAIN_APP_REPLACE_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (replacementGeneration != mainContentReplacementGeneration) {
                        return;
                    }
                    mainContentReplacementPendingSlot = -1;
                    if (activityDestroyed || slot != activeMainSlot
                            || windowApps[slot] != previousApp) {
                        windowView.setAlpha(1f);
                        return;
                    }
                    embeddedSyncGenerations[slot]++;
                    previousHost.sendHome();
                    windowApps[slot] = null;
                    clearHostedAppRevealState(slot);
                    renderWindows();
                    settingsPanelController.showInWindow(windowView);
                    Log.i(TAG, "Show internal settings and let Android background previous "
                            + "main app: previous=" + previousApp.packageName + ", slot=" + slot);
                    animateInternalSettingsAppear(slot);
                })
                .start();
    }

    private void replaceDesktopHomeWithInternalSettings() {
        int slot = activeMainSlot;
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null || !isDesktopHomeSlot(slot)) {
            return;
        }

        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.animate()
                .alpha(0f)
                .setDuration(MAIN_APP_REPLACE_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (replacementGeneration != mainContentReplacementGeneration) {
                        return;
                    }
                    mainContentReplacementPendingSlot = -1;
                    if (activityDestroyed || slot != activeMainSlot
                            || !isDesktopHomeSlot(slot)) {
                        windowView.setAlpha(1f);
                        return;
                    }
                    windowView.hideDesktopHome();
                    settingsPanelController.showInWindow(windowView);
                    animateInternalSettingsAppear(slot);
                })
                .start();
    }

    private void replaceInternalSettingsWithApp(LauncherApp app, int existingSlot) {
        int slot = activeMainSlot;
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null || !isInternalSettingsSlot(slot)) {
            hideInternalSettingsPage();
            startAppInSlot(slot, app);
            return;
        }

        final int replacementGeneration = ++mainContentReplacementGeneration;
        mainContentReplacementPendingSlot = slot;
        windowView.animate().cancel();
        windowView.setScaleX(1f);
        windowView.setScaleY(1f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.animate()
                .alpha(0f)
                .setDuration(MAIN_APP_REPLACE_FADE_OUT_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (replacementGeneration != mainContentReplacementGeneration) {
                        return;
                    }
                    mainContentReplacementPendingSlot = -1;
                    if (activityDestroyed || slot != activeMainSlot
                            || !isInternalSettingsSlot(slot)) {
                        windowView.setAlpha(1f);
                        return;
                    }
                    hideInternalSettingsPage();
                    windowView.setAlpha(1f);
                    if (existingSlot >= 0 && existingSlot != slot
                            && existingSlot < MAX_WINDOWS
                            && windowApps[existingSlot] != null
                            && !embeddedSlotClosing[existingSlot]) {
                        switchMainSlot(existingSlot, true);
                        return;
                    }
                    startAppInSlot(slot, app, true, true);
                })
                .start();
    }

    private void animateWindowAppAppear(int slot, LauncherApp expectedApp, boolean fadeIn) {
        animateWindowContentAppear(slot, fadeIn, () -> {
            LauncherApp currentApp = windowApps[slot];
            return currentApp != null && currentApp.isSameInstance(expectedApp);
        });
    }

    private void animateInternalSettingsAppear(int slot) {
        animateWindowContentAppear(slot, true, () -> isInternalSettingsSlot(slot));
    }

    private void animateDesktopHomeAppear(int slot) {
        animateWindowContentAppear(slot, true, () -> isDesktopHomeSlot(slot));
    }

    private void animateWindowContentAppear(int slot, boolean fadeIn,
                                            BooleanSupplier stillCurrent) {
        if (slot < 0 || slot >= MAX_WINDOWS || !isWindowSlotEnabled(slot)
                || !stillCurrent.getAsBoolean()) {
            return;
        }
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null) {
            return;
        }
        windowView.animate().cancel();
        windowView.setPivotX(windowView.getWidth() / 2f);
        windowView.setPivotY(windowView.getHeight() / 2f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.setScaleX(WINDOW_SCALE_APPEAR_START);
        windowView.setScaleY(WINDOW_SCALE_APPEAR_START);
        windowView.setAlpha(fadeIn ? 0f : 1f);
        windowView.post(() -> {
            if (isWindowAnimationRunning() || !stillCurrent.getAsBoolean()) {
                return;
            }
            windowView.animate().cancel();
            windowView.setPivotX(windowView.getWidth() / 2f);
            windowView.setPivotY(windowView.getHeight() / 2f);
            windowView.setTranslationX(0f);
            windowView.setTranslationY(0f);
            windowView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(WINDOW_SCALE_APPEAR_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator())
                    .start();
        });
    }

    private void refreshAllEmbeddedSlotLayouts() {
        if (isWindowAnimationRunning()) {
            scheduleEmbeddedSlotRefresh();
            return;
        }
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (windowApps[slot] == null || windowViews[slot] == null
                    || embeddedSlotClosing[slot]) {
                continue;
            }
            EmbeddedAppHost host = embeddedHosts[slot];
            windowViews[slot].requestLayout();
            windowViews[slot].invalidate();
            if (host != null) {
                View hostView = host.getView();
                if (hostView != null) {
                    hostView.requestLayout();
                    hostView.invalidate();
                }
                host.refreshContainerSize();
            }
        }
    }

    private void scheduleEmbeddedSlotRefresh() {
        if (workspace == null) {
            return;
        }
        workspace.removeCallbacks(refreshAllEmbeddedSlotLayoutsRunnable);
        workspace.postDelayed(refreshAllEmbeddedSlotLayoutsRunnable,
                EMBEDDED_LAYOUT_REFRESH_DELAY_MS);
    }

    private void dispatchMainBack() {
        if (handleOverlayBack()) {
            return;
        }
        sendBackToActiveMainHost();
    }

    private void handleSystemBack() {
        if (handleOverlayBack()) {
            return;
        }
        if (sendBackToActiveMainHost()) {
            return;
        }
        Log.i(TAG, "Consume root launcher back");
    }

    private boolean sendBackToActiveMainHost() {
        if (activeMainSlot < 0 || activeMainSlot >= MAX_WINDOWS
                || windowApps[activeMainSlot] == null) {
            return false;
        }
        EmbeddedAppHost host = embeddedHosts[activeMainSlot];
        if (host == null) {
            return false;
        }
        host.sendBack();
        return true;
    }

    private void onHostedAppExitedAfterBack(
            int slot, LauncherApp exitedApp, Runnable afterDesktopTakeover) {
        if (activityDestroyed || exitedApp == null || exitedApp.isHomeEntry()
                || slot < 0 || slot >= MAX_WINDOWS || slot != activeMainSlot
                || embeddedSlotClosing[slot]) {
            return;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp == null || !exitedApp.isSameInstance(currentApp)) {
            return;
        }
        LauncherApp primaryDesktop = resolveBuiltInDesktopApp();
        int existingPrimaryDesktopSlot = primaryDesktop == null
                ? -1 : findSlotByComponent(primaryDesktop.componentName);
        final int takeoverGeneration = ++desktopTakeoverGeneration;
        desktopHomeRequestPending = false;
        suppressEmbeddedStarts = false;

        if (existingPrimaryDesktopSlot >= 0
                && existingPrimaryDesktopSlot != slot
                && !embeddedSlotClosing[existingPrimaryDesktopSlot]) {
            EmbeddedAppHost desktopHost = embeddedHosts[existingPrimaryDesktopSlot];
            if (desktopHost instanceof RootVirtualDisplayHost) {
                RootVirtualDisplayHost rootDesktopHost =
                        (RootVirtualDisplayHost) desktopHost;
                if (rootDesktopHost.hasResolvedHostedTask(primaryDesktop)) {
                    promoteExistingPrimaryDesktopAfterExit(
                            takeoverGeneration, slot, exitedApp, primaryDesktop,
                            existingPrimaryDesktopSlot, afterDesktopTakeover);
                } else {
                    final int desktopSlot = existingPrimaryDesktopSlot;
                    rootDesktopHost.validateHostedTaskVisible(primaryDesktop,
                            visible -> finishPrimaryDesktopValidationAfterExit(
                                    takeoverGeneration, slot, exitedApp, primaryDesktop,
                                    desktopSlot, afterDesktopTakeover, visible));
                }
            } else {
                promoteExistingPrimaryDesktopAfterExit(
                        takeoverGeneration, slot, exitedApp, primaryDesktop,
                        existingPrimaryDesktopSlot, afterDesktopTakeover);
            }
            return;
        }
        startPrimaryDesktopAfterExit(
                takeoverGeneration, slot, exitedApp, primaryDesktop,
                afterDesktopTakeover);
    }

    private void finishPrimaryDesktopValidationAfterExit(
            int generation, int exitedSlot, LauncherApp exitedApp,
            LauncherApp primaryDesktop, int desktopSlot,
            Runnable afterDesktopTakeover, boolean visible) {
        if (!isCurrentDesktopTakeover(generation, exitedSlot, exitedApp)) {
            return;
        }
        if (visible) {
            promoteExistingPrimaryDesktopAfterExit(
                    generation, exitedSlot, exitedApp, primaryDesktop,
                    desktopSlot, afterDesktopTakeover);
            return;
        }
        Log.w(TAG, "Discard stale built-in desktop slot before app exit takeover: slot="
                + desktopSlot + ", component="
                + primaryDesktop.componentName.flattenToShortString());
        clearStalePrimaryDesktopSlot(desktopSlot, primaryDesktop);
        startPrimaryDesktopAfterExit(
                generation, exitedSlot, exitedApp, primaryDesktop,
                afterDesktopTakeover);
    }

    private void promoteExistingPrimaryDesktopAfterExit(
            int generation, int exitedSlot, LauncherApp exitedApp,
            LauncherApp primaryDesktop, int desktopSlot,
            Runnable afterDesktopTakeover) {
        if (!isCurrentDesktopTakeover(generation, exitedSlot, exitedApp)
                || desktopSlot < 0 || desktopSlot >= MAX_WINDOWS
                || embeddedSlotClosing[desktopSlot]) {
            return;
        }
        LauncherApp desktopApp = windowApps[desktopSlot];
        if (desktopApp == null
                || !primaryDesktop.componentName.equals(desktopApp.componentName)) {
            startPrimaryDesktopAfterExit(
                    generation, exitedSlot, exitedApp, primaryDesktop,
                    afterDesktopTakeover);
            return;
        }
        Log.i(TAG, "Move verified built-in desktop main interface to main slot after app "
                + "exited: desktopSlot=" + desktopSlot
                + ", exitedSlot=" + exitedSlot + ", app=" + exitedApp.packageName);
        switchMainSlot(desktopSlot, true);
        mainHandler.post(() -> finishExitedSlotCleanupAfterDesktopPromotion(
                generation, exitedSlot, exitedApp, primaryDesktop, desktopSlot,
                afterDesktopTakeover, 0));
    }

    private void startPrimaryDesktopAfterExit(
            int generation, int slot, LauncherApp exitedApp,
            LauncherApp primaryDesktop, Runnable afterDesktopTakeover) {
        if (!isCurrentDesktopTakeover(generation, slot, exitedApp)) {
            return;
        }
        clearExitedHostedAppSlot(slot, exitedApp);
        if (primaryDesktop != null) {
            Log.i(TAG, "Start built-in desktop main interface in exited main slot: slot=" + slot
                    + ", app=" + exitedApp.packageName
                    + ", desktop=" + primaryDesktop.componentName.flattenToShortString());
            startAppInSlot(slot, primaryDesktop, false, true);
            if (afterDesktopTakeover != null) {
                EmbeddedAppHost host = embeddedHosts[slot];
                if (host instanceof RootVirtualDisplayHost) {
                    ((RootVirtualDisplayHost) host).runAfterHostedTaskVisible(
                            primaryDesktop, afterDesktopTakeover);
                } else {
                    mainHandler.post(afterDesktopTakeover);
                }
            }
            return;
        }

        Log.w(TAG, "No system desktop main activity is available after app exited; use OneStep "
                + "desktop fallback");
        requestDesktopHomeInMain();
        if (afterDesktopTakeover != null) {
            mainHandler.post(afterDesktopTakeover);
        }
    }

    private boolean isCurrentDesktopTakeover(
            int generation, int slot, LauncherApp exitedApp) {
        if (generation != desktopTakeoverGeneration || activityDestroyed
                || slot < 0 || slot >= MAX_WINDOWS || slot != activeMainSlot
                || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp currentApp = windowApps[slot];
        return currentApp != null && exitedApp != null
                && exitedApp.isSameInstance(currentApp);
    }

    private void clearStalePrimaryDesktopSlot(int slot, LauncherApp primaryDesktop) {
        if (slot < 0 || slot >= MAX_WINDOWS || primaryDesktop == null) {
            return;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp == null
                || !primaryDesktop.componentName.equals(currentApp.componentName)) {
            return;
        }
        EmbeddedAppHost host = embeddedHosts[slot];
        if (host instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) host).concealHostedSurfaceForDesktopTakeover(currentApp);
        }
        embeddedSyncGenerations[slot]++;
        windowApps[slot] = null;
        clearHostedAppRevealState(slot);
        windowViews[slot].setLiveAppVisible(false);
        renderWindows();
    }

    private void finishExitedSlotCleanupAfterDesktopPromotion(
            int generation, int exitedSlot, LauncherApp exitedApp, LauncherApp primaryDesktop,
            int desktopSlot, Runnable afterDesktopTakeover, int attempt) {
        if (generation != desktopTakeoverGeneration || activityDestroyed
                || exitedSlot < 0 || exitedSlot >= MAX_WINDOWS
                || desktopSlot < 0 || desktopSlot >= MAX_WINDOWS) {
            return;
        }
        LauncherApp currentExitedSlotApp = windowApps[exitedSlot];
        if (currentExitedSlotApp == null
                || !exitedApp.isSameInstance(currentExitedSlotApp)) {
            return;
        }
        if (activeMainSlot == desktopSlot && !isWindowAnimationRunning()) {
            clearExitedHostedAppSlot(exitedSlot, exitedApp);
            if (afterDesktopTakeover != null) {
                afterDesktopTakeover.run();
            }
            return;
        }
        LauncherApp desktopApp = windowApps[desktopSlot];
        if (primaryDesktop == null || desktopApp == null
                || !primaryDesktop.componentName.equals(desktopApp.componentName)
                || embeddedSlotClosing[desktopSlot]) {
            Log.w(TAG, "Verified primary desktop slot disappeared during promotion: slot="
                    + desktopSlot);
            startPrimaryDesktopAfterExit(
                    generation, exitedSlot, exitedApp,
                    primaryDesktop, afterDesktopTakeover);
            return;
        }

        boolean animationRunning = isWindowAnimationRunning();
        if (!animationRunning && activeMainSlot != desktopSlot) {
            boolean staleCompletedSwitchPending = mainSlotSwitchPendingSlot == activeMainSlot
                    && mainSlotSwitchPendingOldSlot != activeMainSlot;
            if (staleCompletedSwitchPending) {
                Log.w(TAG, "Clear completed main-slot focus pending before desktop promotion: "
                        + "pending=" + mainSlotSwitchPendingSlot
                        + ", old=" + mainSlotSwitchPendingOldSlot
                        + ", desktop=" + desktopSlot);
                clearPendingMainSlotSwitch();
            }
            if (mainSlotSwitchPendingSlot < 0 && mainContentReplacementPendingSlot < 0) {
                Log.i(TAG, "Retry verified primary desktop promotion: desktopSlot="
                        + desktopSlot + ", exitedSlot=" + exitedSlot
                        + ", attempt=" + attempt);
                switchMainSlot(desktopSlot, true);
            }
        }
        mainHandler.postDelayed(() -> finishExitedSlotCleanupAfterDesktopPromotion(
                generation, exitedSlot, exitedApp, primaryDesktop, desktopSlot,
                afterDesktopTakeover, attempt + 1), 32L);
    }

    private void clearExitedHostedAppSlot(int slot, LauncherApp exitedApp) {
        if (slot < 0 || slot >= MAX_WINDOWS || exitedApp == null) {
            return;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp == null || !exitedApp.isSameInstance(currentApp)) {
            return;
        }
        embeddedSyncGenerations[slot]++;
        EmbeddedAppHost host = embeddedHosts[slot];
        if (host instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) host).concealHostedSurfaceForDesktopTakeover(exitedApp);
        }
        windowApps[slot] = null;
        clearHostedAppRevealState(slot);
        windowViews[slot].setLiveAppVisible(false);
        renderWindows();
    }

    private void backgroundOpenedApps() {
        if (!suppressEmbeddedStarts) {
            suppressEmbeddedStarts = true;
            embeddedStartEpoch++;
            embeddedStartEpochStore.persist(embeddedStartEpoch);
        }
        mainSlotSwitchGeneration++;
        clearPendingMainSlotSwitch();
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (windowApps[slot] == null || embeddedHosts[slot] == null) {
                continue;
            }
            embeddedHosts[slot].invalidateTaskResolution();
        }
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (windowApps[slot] == null || embeddedHosts[slot] == null) {
                continue;
            }
            embeddedSyncGenerations[slot]++;
            embeddedHosts[slot].sendHome();
        }
    }

    private void suspendEmbeddedStartsForFullscreen() {
        if (suppressEmbeddedStarts) {
            return;
        }
        suppressEmbeddedStarts = true;
        embeddedStartEpoch++;
        embeddedStartEpochStore.persist(embeddedStartEpoch);
    }

    private boolean shouldRunEmbeddedStart(int startEpoch) {
        return !suppressEmbeddedStarts && startEpoch == embeddedStartEpoch;
    }

    private boolean handleOverlayBack() {
        if (isInternalSettingsVisible()) {
            hideInternalSettingsPage();
            return true;
        }
        if (hidePlaylistPanel()) {
            return true;
        }
        return false;
    }

    private void openSettingsInMain() {
        LauncherApp settingsApp = createLauncherAppForPackage("com.android.settings");
        if (settingsApp == null) {
            Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
            ComponentName componentName = settingsIntent.resolveActivity(getPackageManager());
            if (componentName != null) {
                settingsApp = createLauncherAppForPackage(componentName.getPackageName());
            }
        }
        if (settingsApp == null) {
            Toast.makeText(this, "找不到系统设置", Toast.LENGTH_SHORT).show();
            return;
        }
        hideInternalSettingsPage();
        addOrFocusApp(settingsApp);
    }

    private LauncherApp createLauncherAppForPackage(String packageName) {
        try {
            return launcherAppRepository == null
                    ? null : launcherAppRepository.loadLauncherApp(packageName);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void addOrFocusLatestApp(String packageName) {
        for (LauncherApp app : launcherApps) {
            if (TextUtils.equals(app.packageName, packageName)) {
                addOrFocusApp(app);
                return;
            }
        }
        LauncherApp app = createLauncherAppForPackage(packageName);
        if (app != null) {
            addOrFocusApp(app);
        }
    }

    private void swapWithMain(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || slot == activeMainSlot
                || (windowApps[slot] == null && !isInternalSettingsSlot(slot)
                && !isDesktopHomeSlot(slot))
                || embeddedSlotClosing[slot]) {
            return;
        }
        switchMainSlot(slot, true);
    }

    private void dismissSideWindow(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || slot == activeMainSlot
                || (windowApps[slot] == null && !isInternalSettingsSlot(slot)
                && !isDesktopHomeSlot(slot))
                || embeddedSlotClosing[slot]) {
            return;
        }
        if (isDesktopHomeSlot(slot)) {
            dismissDesktopHomeSideWindow(slot);
            return;
        }
        if (isInternalSettingsSlot(slot)) {
            dismissInternalSettingsSideWindow(slot);
            return;
        }
        embeddedSlotClosing[slot] = true;
        embeddedSyncGenerations[slot]++;
        LauncherApp dismissedApp = windowApps[slot];
        EmbeddedAppHost dismissedHost = embeddedHosts[slot];
        long dismissStartedUptime = SystemClock.uptimeMillis();
        OneStepWindowView windowView = windowViews[slot];
        int direction = getSideDismissDirection();
        windowView.animate().cancel();
        windowView.setElevation(0f);
        windowView.setTranslationZ(0f);
        windowView.setZ(0f);
        windowViews[activeMainSlot].bringToFront();
        ViewPropertyAnimator animator = windowView.animate()
                .alpha(0.18f)
                .setDuration(SIDE_DISMISS_SETTLE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        if (verticalWindowLayout) {
            animator.translationY(windowView.getHeight() + dp(24));
        } else {
            animator.translationX(direction * (windowView.getWidth() + dp(24)));
        }
        animator.start();
        closeDismissedSlot(slot, dismissedApp, dismissedHost, windowView,
                dismissStartedUptime);
    }

    private void dismissInternalSettingsSideWindow(int slot) {
        OneStepWindowView windowView = windowViews[slot];
        int direction = getSideDismissDirection();
        windowView.animate().cancel();
        ViewPropertyAnimator animator = windowView.animate()
                .alpha(0.18f)
                .setDuration(SIDE_DISMISS_SETTLE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        if (verticalWindowLayout) {
            animator.translationY(windowView.getHeight() + dp(24));
        } else {
            animator.translationX(direction * (windowView.getWidth() + dp(24)));
        }
        animator.withEndAction(() -> {
            if (activityDestroyed || slot == activeMainSlot || !isInternalSettingsSlot(slot)) {
                return;
            }
            hideInternalSettingsPage();
            windowView.setTranslationX(0f);
            windowView.setTranslationY(0f);
            windowView.setScaleX(1f);
            windowView.setScaleY(1f);
            windowView.setAlpha(1f);
            renderWindows();
        }).start();
    }

    private void dismissDesktopHomeSideWindow(int slot) {
        OneStepWindowView windowView = windowViews[slot];
        int direction = getSideDismissDirection();
        windowView.animate().cancel();
        ViewPropertyAnimator animator = windowView.animate()
                .alpha(0.18f)
                .setDuration(SIDE_DISMISS_SETTLE_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator());
        if (verticalWindowLayout) {
            animator.translationY(windowView.getHeight() + dp(24));
        } else {
            animator.translationX(direction * (windowView.getWidth() + dp(24)));
        }
        animator.withEndAction(() -> {
            if (activityDestroyed || slot == activeMainSlot || !isDesktopHomeSlot(slot)) {
                return;
            }
            windowView.hideDesktopHome();
            windowView.setTranslationX(0f);
            windowView.setTranslationY(0f);
            windowView.setScaleX(1f);
            windowView.setScaleY(1f);
            windowView.setAlpha(1f);
            renderWindows();
        }).start();
    }

    private void closeDismissedSlot(int slot, LauncherApp dismissedApp,
                                    EmbeddedAppHost dismissedHost, OneStepWindowView windowView,
                                    long dismissStartedUptime) {
        Runnable onClosed = () -> finishDismissedSlotAfterAnimation(slot, dismissedApp,
                dismissedHost, windowView, dismissStartedUptime);
        if (dismissedHost == null) {
            onClosed.run();
            return;
        }
        try {
            dismissedHost.closeApp(dismissedApp, onClosed);
        } catch (RuntimeException e) {
            Log.w(TAG, "Close dismissed app failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            onClosed.run();
        }
    }

    private void finishDismissedSlotAfterAnimation(int slot, LauncherApp dismissedApp,
                                                   EmbeddedAppHost dismissedHost,
                                                   OneStepWindowView windowView,
                                                   long dismissStartedUptime) {
        long animationEndUptime = dismissStartedUptime + SIDE_DISMISS_SETTLE_MS + 16L;
        long remainingMs = Math.max(0L, animationEndUptime - SystemClock.uptimeMillis());
        windowView.postDelayed(() -> {
            if (slot >= 0 && slot < MAX_WINDOWS && embeddedSlotClosing[slot]
                    && windowApps[slot] == dismissedApp
                    && embeddedHosts[slot] == dismissedHost) {
                windowView.setLiveAppVisible(false);
                finishDismissedSlot(slot, dismissedApp, dismissedHost, windowView);
            }
        }, remainingMs);
    }

    private void finishDismissedSlot(int slot, LauncherApp dismissedApp,
                                     EmbeddedAppHost dismissedHost, OneStepWindowView windowView) {
        mainHandler.post(() -> {
            if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS
                    || !embeddedSlotClosing[slot]
                    || slot == activeMainSlot || embeddedHosts[slot] != dismissedHost
                    || windowApps[slot] != dismissedApp) {
                return;
            }
            windowApps[slot] = null;
            clearHostedAppRevealState(slot);
            embeddedSlotClosing[slot] = false;
            windowView.setTranslationX(0f);
            windowView.setTranslationY(0f);
            windowView.setScaleX(1f);
            windowView.setScaleY(1f);
            windowView.setAlpha(1f);
            renderWindows();
            applyWindowLayout(false);
        });
    }

    private void settleSideWindowBack(OneStepWindowView windowView) {
        windowView.animate().cancel();
        windowView.animate()
                .translationX(0f)
                .translationY(0f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private boolean isSideRailOnLeft() {
        return !mainOnLeft;
    }

    private int getSideDismissDirection() {
        return isSideRailOnLeft() ? -1 : 1;
    }

    private boolean movedPastSideDismissThreshold(float dx, float dy) {
        if (verticalWindowLayout) {
            return dy > 0
                    && Math.abs(dy) >= dp(SIDE_DISMISS_DISTANCE_DP)
                    && Math.abs(dy) > Math.abs(dx) * 0.85f;
        }
        int direction = getSideDismissDirection();
        return dx * direction > 0
                && Math.abs(dx) >= dp(SIDE_DISMISS_DISTANCE_DP)
                && Math.abs(dx) > Math.abs(dy) * 0.85f;
    }

    private void switchMainSlot(int newMainSlot, boolean animate) {
        if (newMainSlot < 0 || newMainSlot >= MAX_WINDOWS
                || newMainSlot == activeMainSlot || embeddedSlotClosing[newMainSlot]
                || isWindowAnimationRunning() || mainSlotSwitchPendingSlot >= 0
                || mainContentReplacementPendingSlot >= 0) {
            return;
        }
        int oldMainSlot = activeMainSlot;
        int sideIndex = sideSlotOrder.indexOf(newMainSlot);
        if (sideIndex < 0) {
            return;
        }
        if (mainSlotSwitchPendingSlot == newMainSlot
                && mainSlotSwitchPendingOldSlot == oldMainSlot) {
            return;
        }
        beginWindowSwitchAnimationCriticalSection();
        final int switchGeneration = ++mainSlotSwitchGeneration;
        EmbeddedAppHost promotedHost = embeddedHosts[newMainSlot];
        if (windowApps[newMainSlot] != null
                && promotedHost instanceof RootVirtualDisplayHost) {
            mainSlotSwitchPendingSlot = newMainSlot;
            mainSlotSwitchPendingOldSlot = oldMainSlot;
            ((RootVirtualDisplayHost) promotedHost).checkDisplayImeLocalPolicy(
                    "slot promoted to main",
                    () -> {
                        if (switchGeneration != mainSlotSwitchGeneration) {
                            clearPendingMainPromotionContent(newMainSlot);
                            clearPendingMainSlotSwitch();
                            scheduleDeferredWindowSwitchWorkFlush();
                            return;
                        }
                        finishMainSlotSwitchAfterPolicy(switchGeneration, oldMainSlot,
                                newMainSlot, animate);
                    },
                    () -> rejectMainSlotSwitch(switchGeneration, oldMainSlot, newMainSlot,
                            "IME policy was not confirmed", "输入法显示策略设置失败，请重试"));
            return;
        }
        finishMainSlotSwitchAfterPolicy(switchGeneration, oldMainSlot,
                newMainSlot, animate);
    }

    private void clearPendingMainSlotSwitch() {
        mainSlotSwitchPendingSlot = -1;
        mainSlotSwitchPendingOldSlot = -1;
    }

    private void finishMainSlotSwitchAfterPolicy(int switchGeneration, int oldMainSlot,
                                                 int newMainSlot, boolean animate) {
        if (switchGeneration != mainSlotSwitchGeneration
                || activeMainSlot != oldMainSlot) {
            clearPendingMainPromotionContent(newMainSlot);
            clearPendingMainSlotSwitch();
            scheduleDeferredWindowSwitchWorkFlush();
            return;
        }
        Runnable transferFocus = () -> {
            if (switchGeneration == mainSlotSwitchGeneration
                    && activeMainSlot == newMainSlot) {
                transferMainSlotFocus(switchGeneration, oldMainSlot, newMainSlot);
            }
        };
        Runnable performSwitch = () -> {
            if (switchGeneration != mainSlotSwitchGeneration
                    || activeMainSlot != oldMainSlot) {
                clearPendingMainPromotionContent(newMainSlot);
                clearPendingMainSlotSwitch();
                scheduleDeferredWindowSwitchWorkFlush();
                return;
            }
            if (!completeMainSlotSwitch(oldMainSlot, newMainSlot, animate, transferFocus)) {
                clearPendingMainPromotionContent(newMainSlot);
                clearPendingMainSlotSwitch();
                scheduleDeferredWindowSwitchWorkFlush();
            }
        };
        if (animate && shouldWarmUpIdleWindowSwitch()
                && !canUseSurfaceWindowAnimationForCurrentLayout()) {
            warmUpIdleWindowSwitch(switchGeneration, oldMainSlot, newMainSlot, performSwitch);
        } else {
            performSwitch.run();
        }
    }

    private boolean canUseSurfaceWindowAnimationForCurrentLayout() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false;
        }
        int liveSurfaceCount = 0;
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            if (!isWindowSlotEnabled(slot)) {
                continue;
            }
            if (windowApps[slot] == null
                    || !(embeddedHosts[slot] instanceof RootVirtualDisplayHost)) {
                return false;
            }
            liveSurfaceCount++;
        }
        return liveSurfaceCount >= 2;
    }

    private boolean shouldWarmUpIdleWindowSwitch() {
        return workspace != null && workspace.isAttachedToWindow()
                && SystemClock.uptimeMillis() - windowAnimationController.getLastAnimationEndUptime()
                >= WINDOW_SWITCH_IDLE_WARMUP_THRESHOLD_MS;
    }

    private void warmUpIdleWindowSwitch(int switchGeneration, int oldMainSlot,
                                        int newMainSlot, Runnable performSwitch) {
        OneStepWindowView oldMainView = windowViews[oldMainSlot];
        OneStepWindowView newMainView = windowViews[newMainSlot];
        if (oldMainView != null) {
            oldMainView.invalidate();
        }
        if (newMainView != null) {
            newMainView.invalidate();
        }
        workspace.invalidate();
        workspace.postOnAnimation(() -> {
            if (switchGeneration != mainSlotSwitchGeneration
                    || activeMainSlot != oldMainSlot
                    || newMainSlot < 0 || newMainSlot >= MAX_WINDOWS
                    || embeddedSlotClosing[newMainSlot]) {
                clearPendingMainPromotionContent(newMainSlot);
                clearPendingMainSlotSwitch();
                scheduleDeferredWindowSwitchWorkFlush();
                return;
            }
            workspace.postOnAnimation(performSwitch);
        });
    }

    private void transferMainSlotFocus(int switchGeneration, int oldMainSlot, int newMainSlot) {
        EmbeddedAppHost oldHost = embeddedHosts[oldMainSlot];
        if (oldHost instanceof RootVirtualDisplayHost) {
            RootVirtualDisplayHost oldRootHost = (RootVirtualDisplayHost) oldHost;
            oldRootHost.depriveHostedInputFocus();
        }
        if (windowApps[newMainSlot] == null) {
            clearPendingMainSlotSwitch();
            return;
        }
        EmbeddedAppHost newHost = embeddedHosts[newMainSlot];
        if (newHost instanceof RootVirtualDisplayHost) {
            RootVirtualDisplayHost newRootHost = (RootVirtualDisplayHost) newHost;
            if (switchGeneration == mainSlotSwitchGeneration
                    && activeMainSlot == newMainSlot) {
                newRootHost.restoreHostedInputFocus();
                scheduleDefaultNavigationFocusRestore("main slot switched");
                refreshAllHostedSensorLandscapeRotations();
            }
            if (mainSlotSwitchPendingSlot == newMainSlot
                    && mainSlotSwitchPendingOldSlot == oldMainSlot) {
                clearPendingMainSlotSwitch();
            }
            return;
        }
        clearPendingMainSlotSwitch();
    }

    private void rejectMainSlotSwitch(int switchGeneration, int oldMainSlot, int newMainSlot,
                                      String logReason, String userMessage) {
        if (switchGeneration != mainSlotSwitchGeneration) {
            clearPendingMainPromotionContent(newMainSlot);
            clearPendingMainSlotSwitch();
            scheduleDeferredWindowSwitchWorkFlush();
            return;
        }
        clearPendingMainPromotionContent(newMainSlot);
        clearPendingMainSlotSwitch();
        scheduleDeferredWindowSwitchWorkFlush();
        if (activityDestroyed || suppressEmbeddedStarts || exitOneStepPending
                || activeMainSlot != oldMainSlot) {
            return;
        }
        if (embeddedSlotClosing[newMainSlot]) {
            return;
        }
        Log.w(TAG, "Keep current main slot because " + logReason + ": slot=" + newMainSlot);
        Toast.makeText(this, userMessage, Toast.LENGTH_SHORT).show();
    }

    private boolean completeMainSlotSwitch(int oldMainSlot, int newMainSlot, boolean animate,
                                           Runnable onLayoutSettled) {
        if (activityDestroyed || suppressEmbeddedStarts || exitOneStepPending
                || activeMainSlot != oldMainSlot
                || newMainSlot < 0 || newMainSlot >= MAX_WINDOWS
                || newMainSlot == oldMainSlot || embeddedSlotClosing[newMainSlot]
                || (windowApps[newMainSlot] == null
                && !isInternalSettingsSlot(newMainSlot)
                && !isDesktopHomeSlot(newMainSlot)
                && !isPendingMainAppStartSlot(newMainSlot)
                && !isPendingInternalSettingsSlot(newMainSlot)
                && !isPendingDesktopHomeSlot(newMainSlot))) {
            return false;
        }
        int sideIndex = sideSlotOrder.indexOf(newMainSlot);
        if (sideIndex < 0) {
            return false;
        }
        sideSlotOrder.set(sideIndex, oldMainSlot);
        activeMainSlot = newMainSlot;
        EmbeddedAppHost oldHost = embeddedHosts[oldMainSlot];
        if (oldHost instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) oldHost).syncLaunchRoutingSource();
        }
        EmbeddedAppHost newHost = embeddedHosts[newMainSlot];
        if (newHost instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) newHost).syncLaunchRoutingSource();
        }
        mainOnLeft = !mainOnLeft;
        updateTopNavigationControls();
        applyWindowLayout(animate, () -> {
            startPendingMainAppAfterPromotion(newMainSlot);
            showPendingInternalSettingsAfterPromotion(newMainSlot);
            showPendingDesktopHomeAfterPromotion(newMainSlot);
            refreshEmbeddedSlotsAfterRoleChange(oldMainSlot, newMainSlot);
            LauncherApp newMainApp = windowApps[newMainSlot];
            if (newMainApp != null
                    && routedLaunchIntents.containsKey(newMainApp.packageName)) {
                syncEmbeddedSlot(newMainSlot);
            }
            if (onLayoutSettled != null) {
                onLayoutSettled.run();
            }
        });
        if (!isWindowAnimationRunning() && windowSwitchAnimationCritical) {
            scheduleDeferredWindowSwitchWorkFlush();
        }
        return true;
    }

    private void refreshEmbeddedSlotsAfterRoleChange(int oldMainSlot, int newMainSlot) {
        refreshEmbeddedSlotAfterRoleChange(oldMainSlot);
        if (newMainSlot != oldMainSlot) {
            refreshEmbeddedSlotAfterRoleChange(newMainSlot);
        }
    }

    private void refreshEmbeddedSlotAfterRoleChange(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || embeddedSlotClosing[slot]
                || windowApps[slot] == null) {
            return;
        }
        EmbeddedAppHost host = embeddedHosts[slot];
        if (host != null) {
            host.refreshContainerSize();
        }
    }

    private boolean isMainDisplaySlot(int slot) {
        return slot == activeMainSlot;
    }

    private void syncEmbeddedSlot(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS || windowViews[slot] == null) {
            return;
        }
        LauncherApp app = windowApps[slot];
        int generation = ++embeddedSyncGenerations[slot];
        if (app == null || embeddedSlotClosing[slot]) {
            clearHostedAppRevealState(slot);
            windowViews[slot].setLiveAppVisible(false);
            return;
        }
        EmbeddedAppHost host = embeddedHosts[slot];
        RootVirtualDisplayHost rootHost = host instanceof RootVirtualDisplayHost
                ? (RootVirtualDisplayHost) host : null;
        boolean resolvedRootTask = rootHost != null && rootHost.hasResolvedHostedTask(app);
        boolean keepHostedSurfaceVisible = rootHost != null
                && rootHost.shouldKeepHostedSurfaceVisibleDuringValidation(app);
        if (rootHost != null && !resolvedRootTask && !keepHostedSurfaceVisible) {
            clearHostedAppRevealState(slot);
            windowViews[slot].setLiveAppVisible(false);
        } else {
            setHostedSurfaceAlpha(slot, 1f);
            windowViews[slot].setLiveAppVisible(true);
        }
        windowViews[slot].requestLayout();
        int startEpoch = embeddedStartEpoch;
        windowViews[slot].post(() -> startEmbeddedSlotWhenReady(slot, generation, startEpoch, app,
                EMBEDDED_START_MAX_RETRIES));
    }

    private void startEmbeddedSlotWhenReady(int slot, int generation, int startEpoch,
                                            LauncherApp expectedApp, int retriesLeft) {
        if (slot < 0 || slot >= MAX_WINDOWS || generation != embeddedSyncGenerations[slot]
                || embeddedSlotClosing[slot]) {
            return;
        }
        if (!shouldRunEmbeddedStart(startEpoch)) {
            return;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp == null || !currentApp.isSameInstance(expectedApp)) {
            return;
        }

        OneStepWindowView windowView = windowViews[slot];
        EmbeddedAppHost host = embeddedHosts[slot];
        if (windowView == null || host == null) {
            String reason = host == null ? "未创建嵌入宿主" : host.getUnavailableReason();
            Log.w(TAG, "Cannot embed " + currentApp.packageName + " in slot " + slot
                    + ": " + reason);
            showEmbeddingHintIfNeeded(reason);
            if (windowView != null) {
                windowView.setLiveAppVisible(false);
            }
            return;
        }

        View hostView = host.getView();
        boolean ready = windowView.getEmbeddedContentWidth() > 0
                && windowView.getEmbeddedContentHeight() > 0
                && hostView != null
                && hostView.getWidth() > 0
                && hostView.getHeight() > 0;
        boolean canStartBeforeLayout = host.canStartBeforeLayout();
        if (!ready && !canStartBeforeLayout && retriesLeft > 0) {
            windowView.postDelayed(() -> startEmbeddedSlotWhenReady(slot, generation,
                    startEpoch, expectedApp, retriesLeft - 1), EMBEDDED_START_RETRY_MS);
            return;
        }

        Rect frame = getWindowFrame(windowView);
        String hostSize = hostView == null ? "0x0"
                : hostView.getWidth() + "x" + hostView.getHeight();
        Log.i(TAG, "Start embedded slot " + slot
                + " main=" + isMainDisplaySlot(slot)
                + " app=" + currentApp.packageName
                + " frame=" + frame.width() + "x" + frame.height()
                + "@" + frame.left + "," + frame.top
                + " content=" + windowView.getEmbeddedContentWidth()
                + "x" + windowView.getEmbeddedContentHeight()
                + " host=" + hostSize
                + " type=" + host.getClass().getSimpleName());

        boolean live = (ready || canStartBeforeLayout) && host.start(currentApp);
        boolean revealPending = host instanceof RootVirtualDisplayHost
                && ((RootVirtualDisplayHost) host).isHostedSurfaceRevealPending(currentApp);
        windowView.setLiveAppVisible(live && !revealPending);
        if (!live) {
            String reason = ready ? host.getUnavailableReason() : "嵌入容器未完成布局";
            Log.w(TAG, "Cannot embed " + currentApp.packageName + " in slot " + slot
                    + ": " + reason);
            showEmbeddingHintIfNeeded(reason);
        }
    }

    private void clearHostedAppRevealState(int slot) {
        if (slot < 0 || slot >= MAX_WINDOWS) {
            return;
        }
        setHostedSurfaceAlpha(slot, 0f);
    }

    private void setHostedSurfaceAlpha(int slot, float alpha) {
        if (slot < 0 || slot >= MAX_WINDOWS) {
            return;
        }
        EmbeddedAppHost host = embeddedHosts[slot];
        if (host instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) host).setHostedSurfaceAlpha(alpha);
        }
    }

    private void logSystemPermissionState() {
        for (String permission : REQUIRED_EMBEDDING_PERMISSIONS) {
            int state = checkSelfPermission(permission);
            Log.i(TAG, permission + "="
                    + (state == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
        }
        Log.i(TAG, "installMode=" + (isSystemAppInstall() ? "system" : "data"));
    }

    private boolean hasGrantedSystemEmbeddingPermission() {
        for (String permission : REQUIRED_EMBEDDING_PERMISSIONS) {
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemAppInstall() {
        int flags = getApplicationInfo().flags;
        return (flags & ApplicationInfo.FLAG_SYSTEM) != 0
                || (flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    private void showEmbeddingHintIfNeeded(String reason) {
        if (embeddingHintShown) {
            return;
        }
        embeddingHintShown = true;
        String detail = TextUtils.isEmpty(reason) ? "" : "：" + reason;
        Toast.makeText(this, "当前环境未开放固定容器内活 App 嵌入" + detail, Toast.LENGTH_LONG).show();
    }

    private void startRunningTaskMonitoring() {
        if (activityDestroyed || runningTaskMonitoringActive) {
            return;
        }
        runningTaskMonitoringActive = true;
        runningTaskRefreshPending = false;
        runningTaskMonitorGeneration++;
        mainHandler.removeCallbacks(runningTaskMonitorRunnable);
        mainHandler.post(runningTaskMonitorRunnable);
    }

    private void stopRunningTaskMonitoring() {
        runningTaskMonitoringActive = false;
        runningTaskQueryInFlight = false;
        runningTaskRefreshPending = false;
        runningTaskMonitorGeneration++;
        mainHandler.removeCallbacks(runningTaskMonitorRunnable);
    }

    private void requestRunningTaskStatusRefresh() {
        if (!runningTaskMonitoringActive || activityDestroyed) {
            return;
        }
        if (runningTaskQueryInFlight) {
            runningTaskRefreshPending = true;
            return;
        }
        mainHandler.removeCallbacks(runningTaskMonitorRunnable);
        mainHandler.postDelayed(
                runningTaskMonitorRunnable, RUNNING_TASK_EVENT_REFRESH_DELAY_MS);
    }

    private void queryRunningTaskStatusesAsync() {
        if (!runningTaskMonitoringActive || activityDestroyed || runningTaskQueryInFlight) {
            return;
        }
        final int generation = runningTaskMonitorGeneration;
        final List<LauncherApp> apps = new ArrayList<>(launcherApps);
        runningTaskQueryInFlight = true;
        try {
            runningTaskExecutor.execute(() -> {
                Set<String> snapshot = queryTaskBackedAppInstances(apps);
                mainHandler.post(() -> finishRunningTaskStatusQuery(generation, snapshot));
            });
        } catch (RuntimeException e) {
            runningTaskQueryInFlight = false;
            scheduleNextRunningTaskStatusQuery(RUNNING_TASK_MONITOR_RETRY_INTERVAL_MS);
        }
    }

    private void finishRunningTaskStatusQuery(int generation, Set<String> snapshot) {
        if (!runningTaskMonitoringActive || activityDestroyed
                || generation != runningTaskMonitorGeneration) {
            return;
        }
        runningTaskQueryInFlight = false;
        if (snapshot != null && !taskBackedAppInstances.equals(snapshot)) {
            taskBackedAppInstances.clear();
            taskBackedAppInstances.addAll(snapshot);
            updateShortcutAppStatuses();
        }
        boolean refreshAgain = runningTaskRefreshPending;
        runningTaskRefreshPending = false;
        scheduleNextRunningTaskStatusQuery(refreshAgain
                ? RUNNING_TASK_EVENT_REFRESH_DELAY_MS
                : snapshot == null
                ? RUNNING_TASK_MONITOR_RETRY_INTERVAL_MS
                : RUNNING_TASK_MONITOR_INTERVAL_MS);
    }

    private void scheduleNextRunningTaskStatusQuery(long delayMs) {
        if (!runningTaskMonitoringActive || activityDestroyed) {
            return;
        }
        mainHandler.removeCallbacks(runningTaskMonitorRunnable);
        mainHandler.postDelayed(runningTaskMonitorRunnable, delayMs);
    }

    @SuppressWarnings("deprecation")
    private Set<String> queryTaskBackedAppInstances(List<LauncherApp> apps) {
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (activityManager == null) {
            return null;
        }
        List<RunningTaskAppResolver.TaskIdentity> tasks = new ArrayList<>();
        Set<Integer> recentTaskIds = new HashSet<>();
        boolean querySucceeded = false;
        RuntimeException lastFailure = null;
        try {
            List<ActivityManager.RecentTaskInfo> recentTasks = activityManager.getRecentTasks(
                    RUNNING_TASK_QUERY_LIMIT, ActivityManager.RECENT_WITH_EXCLUDED);
            if (recentTasks != null) {
                for (ActivityManager.RecentTaskInfo task : recentTasks) {
                    if (task != null) {
                        tasks.add(toTaskIdentity(task));
                        int taskId = readTaskId(task);
                        if (taskId > 0) {
                            recentTaskIds.add(taskId);
                        }
                    }
                }
            }
            querySucceeded = true;
        } catch (RuntimeException e) {
            lastFailure = e;
        }
        try {
            List<ActivityManager.RunningTaskInfo> runningTasks =
                    activityManager.getRunningTasks(RUNNING_TASK_QUERY_LIMIT);
            if (runningTasks != null) {
                for (ActivityManager.RunningTaskInfo task : runningTasks) {
                    if (task != null && !recentTaskIds.contains(readTaskId(task))) {
                        tasks.add(toTaskIdentity(task));
                    }
                }
            }
            querySucceeded = true;
        } catch (RuntimeException e) {
            lastFailure = e;
        }
        if (!querySucceeded) {
            if (!runningTaskQueryFailureLogged) {
                runningTaskQueryFailureLogged = true;
                Log.w(TAG, "Unable to query system tasks for app status", lastFailure);
            }
            return null;
        }
        runningTaskQueryFailureLogged = false;
        List<RunningTaskAppResolver.AppIdentity> appIdentities =
                new ArrayList<>(apps.size());
        for (LauncherApp app : apps) {
            appIdentities.add(new RunningTaskAppResolver.AppIdentity(
                    app.instanceKey(), app.componentKey(), app.packageName, app.userId()));
        }
        return RunningTaskAppResolver.resolve(appIdentities, tasks);
    }

    private RunningTaskAppResolver.TaskIdentity toTaskIdentity(
            ActivityManager.RecentTaskInfo task) {
        return new RunningTaskAppResolver.TaskIdentity(
                readTaskUserId(task),
                componentKey(task.baseIntent == null ? null : task.baseIntent.getComponent()),
                componentKey(task.origActivity),
                componentKey(task.baseActivity),
                componentKey(task.topActivity));
    }

    private RunningTaskAppResolver.TaskIdentity toTaskIdentity(
            ActivityManager.RunningTaskInfo task) {
        return new RunningTaskAppResolver.TaskIdentity(
                readTaskUserId(task),
                componentKey(task.baseIntent == null ? null : task.baseIntent.getComponent()),
                "",
                componentKey(task.baseActivity),
                componentKey(task.topActivity));
    }

    private int readTaskUserId(Object taskInfo) {
        try {
            return taskInfo.getClass().getField("userId").getInt(taskInfo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return android.os.Process.myUserHandle().hashCode();
        }
    }

    private int readTaskId(Object taskInfo) {
        for (String fieldName : new String[]{"taskId", "id"}) {
            try {
                int taskId = taskInfo.getClass().getField(fieldName).getInt(taskInfo);
                if (taskId > 0) {
                    return taskId;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Android releases renamed this field; try the next compatible name.
            }
        }
        return -1;
    }

    private String componentKey(ComponentName componentName) {
        return componentName == null ? "" : componentName.flattenToString();
    }

    private void renderWindows() {
        for (int i = 0; i < MAX_WINDOWS; i++) {
            windowViews[i].bind(windowApps[i], i);
        }
        updateShortcutAppStatuses();
        requestRunningTaskStatusRefresh();
        scheduleSideInputProtectionSync();
    }

    private void updateShortcutAppStatuses() {
        for (AppShortcutView shortcutView : shortcutViews) {
            String instanceKey = shortcutView.getInstanceKeyValue();
            boolean taskPresent = taskBackedAppInstances.contains(instanceKey);
            boolean foreground = taskPresent && findSlot(instanceKey) >= 0;
            shortcutView.setActive(foreground);
            shortcutView.setAppStatus(foreground
                    ? AppShortcutView.AppStatus.FOREGROUND
                    : taskPresent
                    ? AppShortcutView.AppStatus.BACKGROUND
                    : AppShortcutView.AppStatus.NONE);
        }
    }

    private boolean shouldShieldSideInput(int slot) {
        return activityResumed && !activityDestroyed
                && slot >= 0 && slot < MAX_WINDOWS
                && slot != activeMainSlot
                && isWindowSlotEnabled(slot)
                && !embeddedSlotClosing[slot]
                && (windowApps[slot] != null
                || isInternalSettingsSlot(slot)
                || isDesktopHomeSlot(slot));
    }

    private void suspendWindowInputRouting() {
        mainHandler.removeCallbacks(syncSideInputProtectionRunnable);
        if (sideInputShieldController != null) {
            sideInputShieldController.hideAll();
        }
    }

    private void restoreWindowInputRoutingAfterLayout() {
        scheduleSideInputProtectionSync();
    }

    private void scheduleSideInputProtectionSync() {
        if (workspace == null || activityDestroyed) {
            return;
        }
        mainHandler.removeCallbacks(syncSideInputProtectionRunnable);
        workspace.postOnAnimation(syncSideInputProtectionRunnable);
    }

    private void syncSideInputProtection() {
        if (activityDestroyed || isWindowAnimationRunning()) {
            return;
        }
        if (sideInputShieldController != null) {
            sideInputShieldController.update();
        }
    }

    private int findSlot(LauncherApp target) {
        if (target == null) {
            return -1;
        }
        return findSlot(target.instanceKey());
    }

    private int findSlot(String instanceKey) {
        for (int i = 0; i < MAX_WINDOWS; i++) {
            LauncherApp app = windowApps[i];
            if (app != null && TextUtils.equals(app.instanceKey(), instanceKey)) {
                return i;
            }
        }
        return -1;
    }

    private int findSlotByComponent(ComponentName componentName) {
        if (componentName == null) {
            return -1;
        }
        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            LauncherApp app = windowApps[slot];
            if (app != null && componentName.equals(app.componentName)) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptySideSlot() {
        for (int slot : sideSlotOrder) {
            if (isWindowSlotEnabled(slot) && windowApps[slot] == null
                    && !isInternalSettingsSlot(slot)
                    && !isDesktopHomeSlot(slot)
                    && !isPendingInternalSettingsSlot(slot)
                    && !isPendingDesktopHomeSlot(slot)
                    && !embeddedSlotClosing[slot]) {
                return slot;
            }
        }
        return -1;
    }

    private FrameLayout.LayoutParams matchFrame() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private GradientDrawable makePanelBackground(int fillColor, int strokeColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable makeRoundedBackground(int fillColor, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable makeWindowPlaceholderBorder() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.TRANSPARENT);
        drawable.setCornerRadius(0f);
        drawable.setStroke(Math.max(1, dp(0.5f)), 0x40ffffff);
        return drawable;
    }

    private GradientDrawable makeWallpaperFallback() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{
                0xff9bb68a,
                0xff78b089,
                0xff49a6a1
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int getTopMediaAreaHeight() {
        if (!shouldShowTopComponentArea()) {
            return 0;
        }
        if (!pipActive) {
            return dp(TOP_MEDIA_AREA_MIN_HEIGHT_DP);
        }
        int pipHeight = pipRestoreBounds.height() > 0
                ? pipRestoreBounds.height() : dp(TOP_MEDIA_PLAYER_HEIGHT_DP);
        return Math.max(dp(TOP_MEDIA_AREA_MIN_HEIGHT_DP),
                getPipDockTopInset() + pipHeight + dp(8));
    }

    private int getPipDockTopInset() {
        return (statusBarSpacingEnabled ? 0 : getStatusBarHeight()) + dp(16);
    }

    private int getStatusBarSpacingHeight() {
        return statusBarSpacingEnabled ? getStatusBarSafeInsetHeight() : 0;
    }

    private int getStatusBarSafeInsetHeight() {
        int safeInsetHeight = Math.max(dp(24), getStatusBarHeight());
        WindowInsets windowInsets = getWindow().getDecorView().getRootWindowInsets();
        if (windowInsets == null) {
            return safeInsetHeight;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.graphics.Insets topInsets = windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout());
            return Math.max(safeInsetHeight, topInsets.top);
        }
        safeInsetHeight = Math.max(safeInsetHeight, windowInsets.getStableInsetTop());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && windowInsets.getDisplayCutout() != null) {
            safeInsetHeight = Math.max(safeInsetHeight,
                    windowInsets.getDisplayCutout().getSafeInsetTop());
        }
        return safeInsetHeight;
    }

    private int getTopNavHeight() {
        return dp(getTopNavHeightDp());
    }

    private int getTopNavHeightDp() {
        int verticalSpacingDp = Math.round(TOP_NAV_VERTICAL_SPACING_DEFAULT_DP
                * topNavVerticalMarginScalePct / 100f);
        return TOP_NAV_BUTTON_SIZE_DP + verticalSpacingDp;
    }

    private int getTopAppStripHeight() {
        return dp(getTopAppStripHeightDp());
    }

    private int getTopAppStripHeightDp() {
        return getTopBarHeightDp(topAppStripVerticalPaddingScalePct);
    }

    private int getTopBarHeightDp(int scalePct) {
        int scaledHeightDp = Math.round(TOP_BAR_HEIGHT_DEFAULT_DP * scalePct / 100f);
        int contentBlockDp = Math.max(TOP_NAV_CONTENT_HEIGHT_DP, getTopAppIconSizeDp() + 6);
        int minHeightDp = contentBlockDp + getTopBarVerticalPaddingDp(scalePct) * 2;
        return Math.max(minHeightDp, scaledHeightDp);
    }

    private int getTopAppIconSizeDp() {
        return Math.max(1, Math.round(TOP_APP_ICON_SIZE_DEFAULT_DP * topAppIconScalePct / 100f));
    }

    private int getTopAppStripSidePaddingDp() {
        return Math.max(8, Math.round(16 * topAppStripSpacingScalePct / 100f));
    }

    private int getTopAppStripVerticalPaddingDp() {
        return getTopBarVerticalPaddingDp(topAppStripVerticalPaddingScalePct);
    }

    private int getTopBarVerticalPaddingDp(int scalePct) {
        return Math.max(0, Math.round(4 * scalePct / 100f));
    }

    private int getTopAppStripCellWidthDp(int iconSizeDp) {
        int baseWidthDp = iconSizeDp + 28;
        return Math.max(iconSizeDp + 12,
                Math.round(baseWidthDp * topAppStripSpacingScalePct / 100f));
    }

    private int getTopMediaPlayerTopMargin() {
        int centeredTop = (getTopMediaAreaHeight() - dp(TOP_MEDIA_PLAYER_HEIGHT_DP)) / 2;
        return centeredTop;
    }

    private int getStatusBarHeight() {
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId <= 0) {
            return dp(24);
        }
        return getResources().getDimensionPixelSize(resourceId);
    }

    private ShellCommandResult runMainPrivilegedCommand(String command, String description,
                                                       boolean logOutput) {
        long startedAt = System.currentTimeMillis();
        ShellCommandResult result = runMainRootCommand(command);
        logMainShellResult(result, description, startedAt, logOutput);
        return result;
    }

    private void loadZygiskHookSettings(
            SettingsPanelController.HookSettingsResultCallback callback) {
        hookSettingsExecutor.execute(() -> {
            ShellCommandResult result = runMainPrivilegedCommand(
                    ZygiskHookConfig.readCommand(), "read Zygisk hook settings", false);
            ZygiskHookConfig.State state = result.isSuccess()
                    ? ZygiskHookConfig.parse(result.output) : null;
            String error = result.isSuccess() ? "Hook 设置数据无效" : "无法读取 Hook 设置";
            mainHandler.post(() -> {
                if (!activityDestroyed) {
                    callback.onResult(state, state == null ? error : null);
                }
            });
        });
    }

    private void requestRootAuthorization(
            SettingsPanelController.RootAuthorizationResultCallback callback) {
        hookSettingsExecutor.execute(() -> {
            ShellCommandResult result = runMainPrivilegedCommand(
                    "id -u", "request ROOT authorization", false);
            boolean granted = result.isSuccess()
                    && outputContainsLine(result.output, "0");
            SettingsPanelController.RootAuthorizationResult authorizationResult = granted
                    ? SettingsPanelController.RootAuthorizationResult.GRANTED
                    : findKernelSuManagerLaunchIntent() != null
                    ? SettingsPanelController.RootAuthorizationResult.KERNEL_SU_ACTION_REQUIRED
                    : SettingsPanelController.RootAuthorizationResult.FAILED;
            mainHandler.post(() -> {
                if (!activityDestroyed) {
                    callback.onResult(authorizationResult);
                }
            });
        });
    }

    private static boolean outputContainsLine(String output, String expected) {
        if (output == null) {
            return false;
        }
        for (String line : output.split("\\n")) {
            if (expected.equals(line.trim())) {
                return true;
            }
        }
        return false;
    }

    private void saveZygiskHookSettings(
            boolean secureWindowEnabled,
            boolean statusBarOverlayEnabled,
            boolean primaryHomeEnhancementEnabled,
            SettingsPanelController.HookSettingsResultCallback callback) {
        hookSettingsExecutor.execute(() -> {
            ShellCommandResult result = runMainPrivilegedCommand(
                    ZygiskHookConfig.writeCommand(
                            secureWindowEnabled, statusBarOverlayEnabled,
                            primaryHomeEnhancementEnabled),
                    "save Zygisk hook settings", false);
            ZygiskHookConfig.State state = result.isSuccess()
                    ? ZygiskHookConfig.parse(result.output) : null;
            String error = result.isSuccess() ? "Hook 设置数据无效" : "无法保存 Hook 设置";
            mainHandler.post(() -> {
                if (!activityDestroyed) {
                    callback.onResult(state, state == null ? error : null);
                }
            });
        });
    }

    private void rebootDeviceForHookSettings() {
        hookSettingsExecutor.execute(() -> runMainPrivilegedCommand(
                "svc power reboot || reboot", "reboot for Zygisk hook settings", false));
    }

    private ShellCommandResult runMainRootCommand(String command) {
        return persistentRootShell.run(command, MEDIA_ROOT_COMMAND_TIMEOUT_SECONDS);
    }

    private Intent findKernelSuManagerLaunchIntent() {
        PackageManager packageManager = getPackageManager();
        for (String packageName : KERNEL_SU_MANAGER_PACKAGES) {
            Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                return launchIntent;
            }
        }
        return null;
    }

    private boolean openKernelSuManager() {
        Intent launchIntent = findKernelSuManagerLaunchIntent();
        if (launchIntent == null) {
            return false;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(launchIntent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.w(TAG, "Unable to open KernelSU manager", e);
            return false;
        }
    }

    private void logMainShellResult(ShellCommandResult result, String description, long startedAt,
                                    boolean logOutput) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        String output = TextUtils.isEmpty(result.output) ? ""
                : logOutput ? " " + result.output : " <" + result.output.length() + " chars>";
        if (result.exitCode == 0) {
            Log.i(TAG, "Media root command ok(" + elapsedMs + "ms): " + description + output);
        } else {
            Log.w(TAG, "Media root command failed(" + result.exitCode + ", "
                    + elapsedMs + "ms): " + description + output);
        }
    }

    private String mainShellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void setDpTextSize(TextView view, float value) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, value);
    }

    private static final class RoutedAppLaunch {
        final int sourceSlot;
        final String sourcePackage;
        final LauncherApp targetApp;
        final Intent intent;

        RoutedAppLaunch(int sourceSlot, String sourcePackage,
                        LauncherApp targetApp, Intent intent) {
            this.sourceSlot = sourceSlot;
            this.sourcePackage = sourcePackage;
            this.targetApp = targetApp;
            this.intent = intent;
        }
    }

}
