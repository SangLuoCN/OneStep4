package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
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
import com.sangluo.onestep.data.apps.LauncherAppRepository;
import com.sangluo.onestep.feature.embedding.EmbeddedAppHost;
import com.sangluo.onestep.feature.embedding.EmbeddedStartEpochStore;
import com.sangluo.onestep.feature.embedding.HiddenActivityViewHost;
import com.sangluo.onestep.feature.embedding.HostedDisplayRotationController;
import com.sangluo.onestep.feature.logging.SessionLogRecorder;
import com.sangluo.onestep.model.LauncherApp;
import com.sangluo.onestep.model.PinnedTaskState;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.system.ui.SystemUiController;
import com.sangluo.onestep.ui.background.BlurredBackgroundView;
import com.sangluo.onestep.ui.settings.SettingsPanelController;
import com.sangluo.onestep.ui.topbar.TopPanelController;
import com.sangluo.onestep.ui.widget.AppShortcutView;
import com.sangluo.onestep.ui.widget.FixedViewportFrameLayout;
import com.sangluo.onestep.ui.widget.PagingHorizontalScrollView;
import com.sangluo.onestep.ui.window.AppLaunchPlacement;
import com.sangluo.onestep.ui.window.OneStepWindowView;
import com.sangluo.onestep.ui.window.WindowAnimationController;
import com.sangluo.onestep.ui.window.WindowLayoutCalculator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final long SUPERSEDED_DISPLAY_RELEASE_GRACE_MS = 5000L;
    private static final int TOP_BAR_HEIGHT_DEFAULT_DP = 74;
    private static final int MEDIA_ROOT_COMMAND_TIMEOUT_SECONDS = 8;
    private static final int EMBEDDED_START_RETRY_MS = 25;
    private static final int EMBEDDED_START_MAX_RETRIES = 120;
    private static final int WINDOW_FRAME_SWITCH_ANIMATION_MS = 200;
    private static final long WINDOW_SWITCH_IDLE_WARMUP_THRESHOLD_MS = 3000L;
    private static final int SIDE_DISMISS_DISTANCE_DP = 48;
    private static final int SIDE_DISMISS_SETTLE_MS = 180;
    private static final int WINDOW_SCALE_APPEAR_MS = 240;
    private static final float WINDOW_SCALE_APPEAR_START = 0.82f;
    private static final int MAIN_APP_REPLACE_FADE_OUT_MS = 160;
    private static final long MAIN_APP_REPLACE_REVEAL_SETTLE_MS = 48L;
    private static final long MAIN_APP_REPLACE_REVEAL_TIMEOUT_MS = 2100L;
    private static final long MAIN_APP_REPLACE_FALLBACK_REVEAL_MS = 280L;
    private static final int CORNER_TRIGGER_DISTANCE_DEFAULT_DP = 36;
    private static final int CORNER_TRIGGER_PREVIEW_HIDE_DELAY_MS = 2000;
    private static final int EXIT_BACKGROUND_DELAY_MS = 180;
    private static final int EMBEDDED_LAYOUT_REFRESH_DELAY_MS = 320;
    private static final int VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX = 1080;
    private static final long POST_ANIMATION_NON_CRITICAL_WORK_DELAY_MS = 64L;
    private static final long CROSS_APP_ROUTE_RETRY_MS = 60L;
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
            "android.permission.CAPTURE_VIDEO_OUTPUT",
            "android.permission.CAPTURE_SECURE_VIDEO_OUTPUT",
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
    private final Set<String> backgroundAppPackages = new HashSet<>();
    private final List<Integer> sideSlotOrder = new ArrayList<>();
    private final ArrayDeque<RoutedAppLaunch> pendingCrossAppRoutes = new ArrayDeque<>();
    private final Map<String, Intent> routedLaunchIntents = new HashMap<>();

    private List<LauncherApp> launcherApps = Collections.emptyList();
    private LauncherAppRepository launcherAppRepository;
    private Boolean suCommandAvailable;
    private final PersistentRootShell persistentRootShell = new PersistentRootShell();
    private boolean embeddingHintShown;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService mediaRootExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService displayImePolicyExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService sensorPolicyExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService visualEffectExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService wallpaperExecutor =
            Executors.newSingleThreadExecutor();
    private final ExecutorService pipDockExecutor = Executors.newSingleThreadExecutor();
    private final Object rootInputBridgeStartLock = new Object();
    private final Runnable refreshAllEmbeddedSlotLayoutsRunnable =
            this::refreshAllEmbeddedSlotLayouts;
    private final Runnable cornerTriggerPreviewHideRunnable = this::hideCornerTriggerPreview;
    private final Runnable drainCrossAppRoutesRunnable = this::drainCrossAppRoutes;
    private final OneStepWindowView.Callbacks windowViewCallbacks =
            new OneStepWindowView.Callbacks() {
                @Override
                public View createDesktopHome() {
                    return MainActivity.this.createDesktopHome();
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
                @Override public boolean shouldDeferHostedAppReveal(
                        int slot, String packageName) {
                    return isMainAppReplacementRevealPending(slot, packageName);
                }
                @Override public void onHostedAppReady(int slot, String packageName) {
                    MainActivity.this.onHostedAppReady(slot, packageName);
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
    private int mainAppReplacementRevealGeneration;
    private int mainAppReplacementRevealSlot = -1;
    private String mainAppReplacementRevealPackage = "";
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
    private boolean mediaPlayerVisible = true;
    private boolean verticalWindowLayout;
    private int sideWindowCount = DEFAULT_SIDE_WINDOWS;
    private int topNavVerticalMarginScalePct = TOP_NAV_VERTICAL_MARGIN_SCALE_DEFAULT;
    private int oneStepTriggerAreaScalePct = ONE_STEP_TRIGGER_AREA_SCALE_DEFAULT;
    private int cornerTriggerSensitivityPct = CORNER_TRIGGER_SENSITIVITY_DEFAULT;
    private SystemUiController systemUiController;
    private SessionLogRecorder sessionLogRecorder;
    private SettingsPanelController settingsPanelController;
    private TopPanelController topPanelController;
    private android.window.OnBackInvokedCallback systemBackCallback;
    private boolean pipMonitoringActive;
    private boolean pipQueryInFlight;
    private boolean pipDockInFlight;
    private boolean pipActive;
    private boolean pipDockApplied;
    private int pipTaskId = -1;
    private int pipMonitorGeneration;
    private final Rect pipRestoreBounds = new Rect();
    private final Runnable flushDeferredWindowSwitchWorkRunnable =
            this::flushDeferredWindowSwitchWork;
    private final Runnable showDesktopHomeRunnable = this::showDesktopHomeInMain;
    private final Runnable pipMonitorRunnable = this::queryPipStateAsync;
    private final Runnable pipDockBoundsUpdateRunnable = this::requestPipDockFromSlot;

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
        sessionLogRecorder = new SessionLogRecorder(getApplicationContext());
        sessionLogRecorder.start();
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
        settingsPanelController = createSettingsPanelController();
        topPanelController = createTopPanelController();
        windowAnimationController = createWindowAnimationController();
        initializeEmbeddedBridgeState();
        launcherAppRepository = new LauncherAppRepository(this);
        launcherApps = launcherAppRepository.loadLauncherApps();
        setContentView(createDesktop());
        // setContentView installs the decor and may refresh its default pixel format.
        // Keep the HOME task opaque so a system HOME transition cannot expose wallpaper.
        hostWindow.setFormat(PixelFormat.OPAQUE);
        hostWindow.getDecorView().setBackgroundColor(Color.BLACK);
        systemUiController = new SystemUiController(
                this, mainHandler, this::shouldHideStatusBarForOneStep,
                () -> activityDestroyed, this::isSystemAppInstall,
                this::handleSystemBack, this::focusActiveHostedDisplay);
        getWindow().getDecorView().post(() -> Log.i(TAG, "Hardware acceleration: decor="
                + getWindow().getDecorView().isHardwareAccelerated()));
        applyStatusBarForCurrentMode();
        registerSystemBackCallback();
        logSystemPermissionState();
        createEmbeddedHosts();
        initializeHostedLandscapeOrientationForwarding();
        prewarmRootInputBridge();
        renderWindows();
        initMediaMonitoring();
        initAmapNavigationMonitoring();
        if (isDesktopHomeRequestIntent(getIntent())) {
            mainHandler.post(this::requestDesktopHomeInMain);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nonDefaultDisplayHomeRelay) {
            return;
        }
        boolean returningToForeground = !activityResumed;
        activityResumed = true;
        suppressEmbeddedStarts = false;
        if (returningToForeground && systemUiController != null) {
            systemUiController.invalidateAppliedState();
        }
        applyStatusBarForCurrentMode();
        hostedDisplayRotationController.enable();
        resumeMediaMonitoring();
        startPipMonitoring();
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
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyStatusBarForCurrentMode();
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

    private void focusActiveHostedDisplay() {
        if (activityDestroyed || !shouldHideStatusBarForOneStep()
                || activeMainSlot < 0 || activeMainSlot >= embeddedHosts.length) {
            return;
        }
        EmbeddedAppHost activeHost = embeddedHosts[activeMainSlot];
        if (activeHost instanceof RootVirtualDisplayHost) {
            ((RootVirtualDisplayHost) activeHost).focusHostedDisplay();
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

    static boolean dispatchSecondaryHome(int displayId) {
        MainActivity activity = defaultDisplayInstance.get();
        if (displayId <= Display.DEFAULT_DISPLAY || activity == null
                || activity.activityDestroyed || activity.nonDefaultDisplayHomeRelay) {
            return false;
        }
        activity.mainHandler.post(() -> activity.handleSecondaryHome(displayId));
        return true;
    }

    private void handleSecondaryHome(int displayId) {
        if (activityDestroyed) {
            return;
        }
        RootVirtualDisplayHost sourceHost = findRootVirtualDisplayHost(displayId);
        if (sourceHost == null) {
            Log.w(TAG, "Ignore secondary HOME from unknown display " + displayId);
            return;
        }
        int sourceSlot = sourceHost.getSlot();
        if (sourceSlot != activeMainSlot) {
            Log.w(TAG, "Ignore secondary HOME from non-main slot: display=" + displayId
                    + ", slot=" + sourceSlot + ", mainSlot=" + activeMainSlot);
            return;
        }
        Log.i(TAG, "Show built-in app list for secondary HOME: display=" + displayId
                + ", slot=" + sourceSlot);
        suppressEmbeddedStarts = false;
        requestDesktopHomeInMain();
    }

    private boolean shouldHideStatusBarForOneStep() {
        return multiWindowMode && !exitOneStepPending;
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
            mediaRootExecutor.shutdownNow();
            visualEffectExecutor.shutdownNow();
            wallpaperExecutor.shutdownNow();
            pipDockExecutor.shutdownNow();
            releaseEmbeddedResources();
            super.onDestroy();
            return;
        }
        restoreDefaultDisplayFocus("OneStep destroyed");
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
        mainHandler.removeCallbacks(pipMonitorRunnable);
        mainHandler.removeCallbacks(pipDockBoundsUpdateRunnable);
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
        visualEffectExecutor.shutdownNow();
        wallpaperExecutor.shutdownNow();
        pipDockExecutor.shutdown();
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
        updateScreenContainerBackground();
        ensureWindowChildren();

        for (int slot = 0; slot < MAX_WINDOWS; slot++) {
            boolean visible = isWindowSlotEnabled(slot) && !embeddedSlotClosing[slot];
            windowViews[slot].setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            windowViews[slot].setMainWindowMode(slot == activeMainSlot);
        }

        Runnable layoutFinished = () -> {
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
        return getTopMediaAreaHeight() + getTopNavHeight() + getTopAppStripHeight();
    }

    private void setTopChromeVisible(boolean visible, boolean animate) {
        if (topChromeContainer == null) {
            return;
        }
        int chromeHeight = Math.max(1, getTopChromeHeight());
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
            if (multiWindowMode || isInternalSettingsVisible()) {
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
                    boolean matched = left
                            ? dx > triggerDistance && dy > triggerDistance
                            : dx < -triggerDistance && dy > triggerDistance;
                    if (!triggered[0] && matched && Math.abs(dx) > Math.abs(dy) * 0.42f) {
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
        if (statusGestureShield != null) {
            statusGestureShield.setVisibility(View.GONE);
        }
        if (leftCornerTrigger != null) {
            leftCornerTrigger.setVisibility(visibility);
        }
        if (rightCornerTrigger != null) {
            rightCornerTrigger.setVisibility(visibility);
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
        backgroundOpenedApps();
        workspace.postDelayed(this::completeExitOneStepMode, EXIT_BACKGROUND_DELAY_MS);
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
        int contentHeight = dp(TOP_NAV_CONTENT_HEIGHT_DP);

        topNavLeftControls = new LinearLayout(this);
        topNavLeftControls.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topNavLeftControls.setPadding(dp(16), 0, 0, 0);
        FrameLayout.LayoutParams leftLp = new FrameLayout.LayoutParams(
                dp(122), contentHeight, Gravity.START | Gravity.CENTER_VERTICAL);
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
        setDpTextSize(title, 25);
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                dp(172), contentHeight, Gravity.CENTER);
        navRoot.addView(title, titleLp);

        topNavRightControls = new LinearLayout(this);
        topNavRightControls.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        topNavRightControls.setPadding(0, 0, dp(16), 0);
        FrameLayout.LayoutParams rightLp = new FrameLayout.LayoutParams(
                dp(122), contentHeight, Gravity.END | Gravity.CENTER_VERTICAL);
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
        control.setImageResource(drawableResId);
        control.setScaleType(ImageView.ScaleType.FIT_CENTER);
        control.setPadding(dp(12), dp(12), dp(12), dp(12));
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
        container.addView(control, new LinearLayout.LayoutParams(
                dp(42), ViewGroup.LayoutParams.MATCH_PARENT));
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

        for (LauncherApp app : launcherApps) {
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
        mediaPlayerVisible = settings.mediaPlayerVisible;
        verticalWindowLayout = settings.verticalWindowLayout;
        sideWindowCount = settings.sideWindowCount;
        topNavVerticalMarginScalePct = settings.topNavVerticalMarginScalePct;
        oneStepTriggerAreaScalePct = settings.oneStepTriggerAreaScalePct;
        cornerTriggerSensitivityPct = settings.cornerTriggerSensitivityPct;
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
            @Override public int desktopGridRows() { return desktopGridRows; }
            @Override public int desktopGridColumns() { return desktopGridColumns; }
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
            @Override public boolean mediaPlayerVisible() { return mediaPlayerVisible; }
            @Override public boolean verticalWindowLayout() { return verticalWindowLayout; }
            @Override public int sideWindowCount() { return sideWindowCount; }
            @Override public void saveGridLayout(int rows, int columns) {
                MainActivity.this.saveGridLayout(rows, columns);
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
            @Override public void saveMediaPlayerVisible(boolean visible) {
                MainActivity.this.saveMediaPlayerVisible(visible);
            }
            @Override public void saveVerticalWindowLayout(boolean enabled) {
                MainActivity.this.saveVerticalWindowLayout(enabled);
            }
            @Override public void saveSideWindowCount(int count) {
                MainActivity.this.saveSideWindowCount(count);
            }
            @Override public void exportSessionLog() {
                MainActivity.this.exportSessionLog();
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
            @Override public boolean mediaPlayerVisible() { return mediaPlayerVisible; }
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
        int desktopSlot = findDesktopHomeSlot();
        if (desktopSlot == activeMainSlot) {
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
                        markAppBackgrounded(previousApp);
                        windowApps[slot] = null;
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

    private void saveMediaPlayerVisible(boolean visible) {
        if (visible && verticalWindowLayout) {
            Toast.makeText(this, "竖向布局下不能开启播放组件", Toast.LENGTH_SHORT).show();
            updateSettingsPageViews();
            return;
        }
        if (visible == mediaPlayerVisible) {
            return;
        }
        mediaPlayerVisible = visible;
        if (!visible) {
            topPanelController.removeMediaPage();
        }
        settingsStore.saveMediaPlayerVisible(visible);
        enforceSideWindowCountLimit();
        updateSettingsPageViews();
        rebuildTopChromeContent();
    }

    private void saveVerticalWindowLayout(boolean enabled) {
        if (enabled == verticalWindowLayout) {
            return;
        }
        verticalWindowLayout = enabled;
        if (enabled && mediaPlayerVisible) {
            mediaPlayerVisible = false;
            topPanelController.removeMediaPage();
        }
        settingsStore.saveWindowLayout(enabled, mediaPlayerVisible);
        enforceSideWindowCountLimit();
        updateSettingsPageViews();
        rebuildTopChromeContent();
        scheduleEmbeddedSlotRefresh();
    }

    private void saveSideWindowCount(int count) {
        int sanitized = sanitizeSideWindowCount(count);
        if (!canUseSideWindowCount(sanitized)) {
            Toast.makeText(this, "关闭播放组件或开启竖向布局后可选择更多小窗口",
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

    private void closeHiddenActivityViewApp(String packageName, Runnable onClosed) {
        Runnable finish = () -> {
            if (onClosed != null) {
                onClosed.run();
            }
        };
        if (TextUtils.isEmpty(packageName)) {
            mainHandler.post(finish);
            return;
        }
        try {
            mediaRootExecutor.execute(() -> {
                ShellCommandResult result = runMainPrivilegedCommand(
                        "am force-stop --user current " + mainShellQuote(packageName),
                        "force-stop dismissed ActivityView app " + packageName, true);
                if (!result.isSuccess()) {
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
        int existingSlot = findSlot(launch.targetApp.packageName);
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
        int existingSlot = findSlot(app.packageName);
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
        int desktopHomeSlot = findDesktopHomeSlot();
        boolean mainOccupied = windowApps[activeMainSlot] != null;
        if (desktopHomeSlot >= 0 && desktopHomeSlot != activeMainSlot
                && mainOccupied && emptySideSlot < 0) {
            stageAppForDesktopHomePromotion(desktopHomeSlot, app);
            return;
        }
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                activeMainSlot, mainOccupied, emptySideSlot);
        switch (placement.action) {
            case START_IN_MAIN:
                startAppInSlot(placement.targetSlot, app);
                break;
            case START_IN_SIDE_AND_PROMOTE:
                stageAppForMainPromotion(placement.targetSlot, app);
                break;
            case REPLACE_MAIN:
                replaceAppInSlot(placement.targetSlot, app);
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
        startAppInSlot(slot, app);
    }

    private void stageAppForDesktopHomePromotion(int slot, LauncherApp app) {
        if (activityDestroyed || slot < 0 || slot >= MAX_WINDOWS || app == null
                || slot == activeMainSlot || !isDesktopHomeSlot(slot)
                || windowApps[slot] != null || embeddedSlotClosing[slot]
                || !sideSlotOrder.contains(slot) || isWindowAnimationRunning()
                || mainSlotSwitchPendingSlot >= 0 || mainContentReplacementPendingSlot >= 0
                || pendingMainAppStartSlot >= 0 || pendingInternalSettingsSlot >= 0
                || pendingDesktopHomeSlot >= 0) {
            return;
        }
        pendingMainAppStartSlot = slot;
        pendingMainAppStart = app;
        embeddedSyncGenerations[slot]++;
        windowViews[slot].setLiveAppVisible(false);
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
        backgroundAppPackages.remove(app.packageName);
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
        if (TextUtils.equals(previousApp.packageName, replacementApp.packageName)) {
            startAppInSlot(slot, replacementApp);
            return;
        }

        if (slot == activeMainSlot) {
            previousHost.invalidateTaskResolution();
            mainSlotSwitchGeneration++;
            clearPendingMainSlotSwitch();
            animateMainAppReplacement(slot, previousApp, previousHost, replacementApp);
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
            backgroundAppPackages.remove(previousApp.packageName);
            startAppInSlot(slot, replacementApp);
        });
        try {
            previousHost.closeApp(previousApp.packageName, onClosed);
        } catch (RuntimeException e) {
            Log.w(TAG, "Replace app close failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            onClosed.run();
        }
    }

    private void animateMainAppReplacement(int slot, LauncherApp previousApp,
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
                    if (activityDestroyed || slot != activeMainSlot
                            || windowApps[slot] != previousApp) {
                        mainContentReplacementPendingSlot = -1;
                        windowView.setAlpha(1f);
                        return;
                    }
                    Log.i(TAG, "Launch replacement and let Android background previous main app: "
                            + "previous=" + previousApp.packageName + ", replacement="
                            + replacementApp.packageName + ", slot=" + slot);
                    embeddedSyncGenerations[slot]++;
                    windowView.setLiveAppVisible(false);
                    previousHost.sendHome();
                    markAppBackgrounded(previousApp);
                    mainAppReplacementRevealGeneration = replacementGeneration;
                    mainAppReplacementRevealSlot = slot;
                    mainAppReplacementRevealPackage = replacementApp.packageName;
                    startAppInSlot(slot, replacementApp, false, false);
                    holdMainAppReplacementUntilReady(
                            slot, replacementApp, replacementGeneration);
                })
                .start();
    }

    private void holdMainAppReplacementUntilReady(int slot, LauncherApp replacementApp,
                                                  int replacementGeneration) {
        OneStepWindowView windowView = windowViews[slot];
        if (windowView == null) {
            revealMainAppReplacement(
                    slot, replacementApp.packageName, replacementGeneration, "no window");
            return;
        }
        windowView.animate().cancel();
        windowView.setPivotX(windowView.getWidth() / 2f);
        windowView.setPivotY(windowView.getHeight() / 2f);
        windowView.setTranslationX(0f);
        windowView.setTranslationY(0f);
        windowView.setScaleX(WINDOW_SCALE_APPEAR_START);
        windowView.setScaleY(WINDOW_SCALE_APPEAR_START);
        windowView.setAlpha(0f);
        long timeoutMs = embeddedHosts[slot] instanceof RootVirtualDisplayHost
                ? MAIN_APP_REPLACE_REVEAL_TIMEOUT_MS
                : MAIN_APP_REPLACE_FALLBACK_REVEAL_MS;
        mainHandler.postDelayed(() -> revealMainAppReplacement(
                slot, replacementApp.packageName, replacementGeneration, "timeout"), timeoutMs);
    }

    private boolean isMainAppReplacementRevealPending(int slot, String packageName) {
        return mainAppReplacementRevealSlot == slot
                && mainAppReplacementRevealGeneration == mainContentReplacementGeneration
                && TextUtils.equals(mainAppReplacementRevealPackage, packageName);
    }

    private void onHostedAppReady(int slot, String packageName) {
        if (!isMainAppReplacementRevealPending(slot, packageName)) {
            return;
        }
        int replacementGeneration = mainAppReplacementRevealGeneration;
        mainHandler.postDelayed(() -> revealMainAppReplacement(
                slot, packageName, replacementGeneration, "task ready"),
                MAIN_APP_REPLACE_REVEAL_SETTLE_MS);
    }

    private void revealMainAppReplacement(int slot, String packageName,
                                          int replacementGeneration, String reason) {
        if (replacementGeneration != mainContentReplacementGeneration
                || !isMainAppReplacementRevealPending(slot, packageName)) {
            return;
        }
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        OneStepWindowView windowView = slot >= 0 && slot < MAX_WINDOWS
                ? windowViews[slot] : null;
        if (activityDestroyed || slot != activeMainSlot || currentApp == null
                || windowView == null
                || !TextUtils.equals(currentApp.packageName, packageName)) {
            mainContentReplacementPendingSlot = -1;
            mainAppReplacementRevealSlot = -1;
            mainAppReplacementRevealPackage = "";
            if (windowView != null) {
                windowView.setAlpha(1f);
                windowView.setScaleX(1f);
                windowView.setScaleY(1f);
            }
            return;
        }
        mainContentReplacementPendingSlot = -1;
        mainAppReplacementRevealSlot = -1;
        mainAppReplacementRevealPackage = "";
        windowView.setLiveAppVisible(true);
        Log.i(TAG, "Reveal replacement app after old content is hidden: package="
                + packageName + ", slot=" + slot + ", reason=" + reason);
        animateWindowAppAppear(slot, currentApp, true);
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
                    markAppBackgrounded(previousApp);
                    windowApps[slot] = null;
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
            return currentApp != null
                    && TextUtils.equals(currentApp.packageName, expectedApp.packageName);
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

    private void backgroundOpenedApps() {
        if (suppressEmbeddedStarts) {
            Log.i(TAG, "Skip duplicate backgroundOpenedApps during exit");
            return;
        }
        mainSlotSwitchGeneration++;
        clearPendingMainSlotSwitch();
        suppressEmbeddedStarts = true;
        embeddedStartEpoch++;
        embeddedStartEpochStore.persist(embeddedStartEpoch);
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
            if (getPackageManager().getLaunchIntentForPackage(packageName) == null) {
                return null;
            }
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return new LauncherApp(
                    String.valueOf(getPackageManager().getApplicationLabel(info)),
                    packageName,
                    getPackageManager().getApplicationIcon(info));
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return null;
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
        if (dismissedHost != null) {
            dismissedHost.invalidateTaskResolution();
        }
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
            dismissedHost.closeApp(dismissedApp.packageName, onClosed);
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
            backgroundAppPackages.remove(dismissedApp.packageName);
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
            newRootHost.focusHostedDisplayAsync(() -> {
                if (switchGeneration == mainSlotSwitchGeneration
                        && activeMainSlot == newMainSlot) {
                    newRootHost.restoreHostedInputFocus();
                    refreshAllHostedSensorLandscapeRotations();
                }
                if (mainSlotSwitchPendingSlot == newMainSlot
                        && mainSlotSwitchPendingOldSlot == oldMainSlot) {
                    clearPendingMainSlotSwitch();
                }
            });
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
            windowViews[slot].setLiveAppVisible(false);
            return;
        }
        windowViews[slot].setLiveAppVisible(true);
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
        if (currentApp == null || !TextUtils.equals(currentApp.packageName,
                expectedApp.packageName)) {
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
        windowView.setLiveAppVisible(live
                && !isMainAppReplacementRevealPending(slot, currentApp.packageName));
        if (!live) {
            String reason = ready ? host.getUnavailableReason() : "嵌入容器未完成布局";
            Log.w(TAG, "Cannot embed " + currentApp.packageName + " in slot " + slot
                    + ": " + reason);
            showEmbeddingHintIfNeeded(reason);
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

    private void renderWindows() {
        for (int i = 0; i < MAX_WINDOWS; i++) {
            windowViews[i].bind(windowApps[i], i);
        }
        for (AppShortcutView shortcutView : shortcutViews) {
            String packageName = shortcutView.getPackageNameValue();
            boolean foreground = findSlot(packageName) >= 0;
            shortcutView.setActive(foreground);
            shortcutView.setAppStatus(foreground
                    ? AppShortcutView.AppStatus.FOREGROUND
                    : backgroundAppPackages.contains(packageName)
                    ? AppShortcutView.AppStatus.BACKGROUND
                    : AppShortcutView.AppStatus.NONE);
        }
    }

    private void markAppBackgrounded(LauncherApp app) {
        if (app != null && !TextUtils.isEmpty(app.packageName)) {
            backgroundAppPackages.add(app.packageName);
        }
    }

    private int findSlot(String packageName) {
        for (int i = 0; i < MAX_WINDOWS; i++) {
            LauncherApp app = windowApps[i];
            if (app != null && TextUtils.equals(app.packageName, packageName)) {
                return i;
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
        return getStatusBarHeight() + dp(16);
    }

    private int getTopNavHeight() {
        return dp(getTopNavHeightDp());
    }

    private int getTopNavHeightDp() {
        return getTopBarHeightDp(topNavVerticalMarginScalePct);
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
        int minHeightDp = contentBlockDp + getTopBarVerticalPaddingDp(scalePct) * 2 + 6;
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
        if (mainHasSuCommand()) {
            long startedAt = System.currentTimeMillis();
            ShellCommandResult result = runMainRootCommand(command);
            logMainShellResult(result, description, startedAt, logOutput);
            return result;
        }
        ShellCommandResult result = new ShellCommandResult(-1, "su unavailable");
        logMainShellResult(result, description, System.currentTimeMillis(), logOutput);
        return result;
    }

    private boolean mainHasSuCommand() {
        if (suCommandAvailable != null) {
            return suCommandAvailable;
        }
        String[] knownPaths = {
                "/system/bin/su",
                "/system/xbin/su",
                "/product/bin/su",
                "/sbin/su",
                "/su/bin/su",
                "/debug_ramdisk/su"
        };
        for (String path : knownPaths) {
            if (new File(path).exists()) {
                suCommandAvailable = true;
                return true;
            }
        }

        Process process = null;
        try {
            process = new ProcessBuilder("sh", "-c", "command -v su")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = waitForProcess(process, 600L);
            suCommandAvailable = finished && process.exitValue() == 0;
            return suCommandAvailable;
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            suCommandAvailable = false;
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private ShellCommandResult runMainRootCommand(String command) {
        return persistentRootShell.run(command, MEDIA_ROOT_COMMAND_TIMEOUT_SECONDS);
    }

    private static boolean waitForProcess(Process process, long timeoutMs)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + Math.max(0L, timeoutMs);
        while (SystemClock.uptimeMillis() <= deadline) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException running) {
                long remaining = deadline - SystemClock.uptimeMillis();
                if (remaining <= 0L) {
                    return false;
                }
                Thread.sleep(Math.min(20L, remaining));
            }
        }
        return false;
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
