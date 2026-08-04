package com.sangluo.onestep;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.Presentation;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Outline;
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
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewPropertyAnimator;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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
import com.sangluo.onestep.feature.embedding.DismissedAppClosePolicy;
import com.sangluo.onestep.feature.embedding.EmbeddedStartEpochStore;
import com.sangluo.onestep.feature.embedding.HiddenActivityViewHost;
import com.sangluo.onestep.feature.embedding.HostedBackDispatchPolicy;
import com.sangluo.onestep.feature.embedding.HostedBackExitPolicy;
import com.sangluo.onestep.feature.embedding.HostedDisplayRotationController;
import com.sangluo.onestep.feature.embedding.HostedInputFocusPolicy;
import com.sangluo.onestep.feature.embedding.HostedSurfaceReusePolicy;
import com.sangluo.onestep.feature.embedding.HostedTaskParser;
import com.sangluo.onestep.feature.drag.ImageShareTargetPolicy;
import com.sangluo.onestep.feature.embedding.HostedTouchFocusPolicy;
import com.sangluo.onestep.feature.embedding.VirtualDisplayHomeKeyPolicy;
import com.sangluo.onestep.feature.embedding.VirtualDisplayViewportPolicy;
import com.sangluo.onestep.feature.navigation.NavigationDisplayFormatter;
import com.sangluo.onestep.feature.media.MediaSessionCoordinator;
import com.sangluo.onestep.hook.OneStepPrimaryHomePolicy;
import com.sangluo.onestep.model.LauncherApp;
import com.sangluo.onestep.model.PinnedTaskState;
import com.sangluo.onestep.model.VirtualDisplaySpec;
import com.sangluo.onestep.system.display.RootVirtualDisplayBridgeClient;
import com.sangluo.onestep.system.root.PersistentRootShell;
import com.sangluo.onestep.system.root.RootBridgePolicyCompat;
import com.sangluo.onestep.system.root.ShellCommandResult;
import com.sangluo.onestep.system.input.RootInputBridgeClient;
import com.sangluo.onestep.system.ui.SystemUiController;
import com.sangluo.onestep.ui.topbar.TopComponentPage;
import com.sangluo.onestep.ui.topbar.TopComponentPagerAdapter;
import com.sangluo.onestep.ui.format.DurationFormatter;
import com.sangluo.onestep.ui.background.BlurredBackgroundView;
import com.sangluo.onestep.ui.widget.AspectRatioImageView;
import com.sangluo.onestep.ui.widget.AppShortcutView;
import com.sangluo.onestep.ui.widget.FixedViewportFrameLayout;
import com.sangluo.onestep.ui.widget.PagingHorizontalScrollView;
import com.sangluo.onestep.ui.window.OneStepWindowView;
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


public final class RootVirtualDisplayHost implements EmbeddedAppHost,
        SurfaceHolder.Callback, View.OnTouchListener {
    interface Callbacks {
        LauncherApp[] windowApps();
        OneStepWindowView[] windowViews();
        boolean[] embeddedSlotClosing();
        Handler mainHandler();
        ExecutorService displayImePolicyExecutor();
        ExecutorService sensorPolicyExecutor();
        PersistentRootShell persistentRootShell();
        EmbeddedStartEpochStore embeddedStartEpochStore();
        Object rootInputBridgeStartLock();
        int embeddedStartEpoch();
        boolean shouldRunEmbeddedStart(int startEpoch);
        boolean isMainDisplaySlot(int slot);
        boolean isMainPaneSlot(int slot);
        boolean isDualMainLayout();
        boolean isLargeScreenDevice();
        boolean activateMainSlot(int slot);
        boolean isActivityDestroyed();
        boolean isWindowFrameAnimationRunning();
        boolean isMultiWindowMode();
        boolean isWindowSlotEnabled(int slot);
        boolean suppressEmbeddedStarts();
        View workspace();
        int mainSlotSwitchPendingSlot();
        int activeMainSlot();
        boolean claimStaleSensorUidOverrideRecovery();
        int latestPhysicalLandscapeRotation();
        boolean hasGrantedSystemEmbeddingPermission();
        boolean isSystemAppInstall();
        void showEmbeddingHint(String reason);
        void swapWithMain(int slot);
        int dp(float value);
        Set<String> recordedSensorUidOverrides();
        void recordSensorUidOverride(String packageName);
        void clearSensorUidOverrideRecord(String packageName);
        long rootInputBridgeLastStartUptime();
        void setRootInputBridgeLastStartUptime(long uptime);
        boolean claimTrustedDisplayRoleSetup();
        Rect[] calculateWindowRects();
        Intent consumeRoutedLaunchIntent(int slot, String packageName);
        boolean onCrossAppLaunch(int sourceDisplayId, String sourcePackage,
                                 Intent intent, String targetPackage,
                                 String sharedImageMimeType,
                                 android.os.ParcelFileDescriptor sharedImageDescriptor);
        void onSystemTaskEvent(int event, int displayId, int taskId, String packageName,
                               String componentName);
        boolean onImageDragTouch(int sourceSlot, MotionEvent event);
        void onHostedAppExitedAfterBack(
                int slot, LauncherApp app, Runnable afterDesktopTakeover);
    }

    private static final String TAG = "OneStep40";
    private static volatile Boolean cachedSuCommandAvailable;
    private static final int ROOT_COMMAND_TIMEOUT_SECONDS = 8;
    private static final int LONG_PRESS_SWAP_MS = 450;
    private static final int DEFAULT_DISPLAY_ID = 0;
    private static final int MAX_WINDOWS = MAX_SIDE_WINDOWS + 2;
    private static final int WINDOW_SURFACE_RESTING_LAYER_BASE = -10_000;
    private static final int ROOT_INPUT_BRIDGE_CONNECT_LOG_THROTTLE_MS = 2000;
    private static final int ROOT_INPUT_BRIDGE_START_THROTTLE_MS = 800;
    private static final int ROOT_INPUT_BRIDGE_READY_TIMEOUT_MS = 2000;
    private static final int ROOT_INPUT_BRIDGE_READY_RETRY_MS = 50;
    private static final int ROOT_DISPLAY_REGISTRATION_TIMEOUT_MS = 800;
    private static final String TASK_STACK_LIST_COMMAND =
            "cmd activity stack list 2>/dev/null || am stack list";
    private static final int VIRTUAL_DISPLAY_RELEASE_RETRY_MS = 120;
    private static final int VIRTUAL_DISPLAY_RELEASE_MAX_ATTEMPTS = 4;
    private static final int VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX = 1080;
    private static final int VIRTUAL_DISPLAY_MIN_AREA_PX =
            VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX * VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX;
    private static final float VIRTUAL_DISPLAY_ASPECT_RATIO_TOLERANCE = 0.005f;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH_HIDDEN = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN = 1 << 7;
    private static final int DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN = 1 << 14;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED_HIDDEN = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_FOCUS_HIDDEN = 1 << 14;
    private static final int DISPLAY_IME_POLICY_LOCAL_HIDDEN = 0;
    private static final String ROOT_INPUT_BRIDGE_CLASS =
            "com.sangluo.onestep.RootInputBridge";
    private static final String ADD_TRUSTED_DISPLAY_PERMISSION =
            "android.permission.ADD_TRUSTED_DISPLAY";
    private static final String VIRTUAL_DISPLAY_ROLE =
            "android.app.role.COMPANION_DEVICE_APP_STREAMING";
    private static final String EXTRA_CLONE_RESOLVER_CONFIRMED =
            "called_from_doubleapp_resolver";
    private static final String EXTRA_CLONE_RESOLVER_CALLING_USER =
            "userId_called_from_doubleapp_resolver";
    private static final String EXTRA_CLONE_RESOLVER_CALLING_PACKAGE =
            "doubleapp_calling_package";
    private static final String EXTRA_XSPACE_AUTHORIZED =
            "android.intent.extra.auth_to_call_xspace";
    private static final String EXTRA_XSPACE_TARGET_USER =
            "android.intent.extra.xspace_cached_uid";
    private static final int[] HOSTED_TASK_RESOLUTION_DELAYS_MS = {80, 240, 700, 1500};
    private static final int[] IMAGE_SHARE_READINESS_DELAYS_MS = {
            80, 180, 360, 700, 1200, 2000, 3500, 5000
    };
    private static final long IMAGE_SHARE_VALIDATION_GUARD_MS = 6000L;
    private static final int[] ROUTED_IMAGE_SHARE_APP_READY_RETRY_MS = {
            300, 300, 300, 300
    };
    private static final int ROUTED_IMAGE_SHARE_STABLE_SCANS = 1;
    private static final int[] BACK_EXIT_CHECK_DELAYS_MS = {60, 180, 450};
    private static final long ROUTED_LAUNCH_AFTER_MAIN_DELAY_MS = 240L;
    private static final long HOSTED_SURFACE_REVEAL_AFTER_VISIBLE_MS = 96L;
    private final MainActivity owner;
    private final Callbacks callbacks;
    private final PackageManager packageManager;
    private final LauncherApps launcherApps;
    private final DisplayManager displayManager;
    private final LauncherApp[] windowApps;
    private final OneStepWindowView[] windowViews;
    private final boolean[] embeddedSlotClosing;
    private final Handler mainHandler;
    private final ExecutorService displayImePolicyExecutor;
    private final ExecutorService sensorPolicyExecutor;
    private final PersistentRootShell persistentRootShell;
    private EmbeddedStartEpochStore embeddedStartEpochStore;
    private final Object rootInputBridgeStartLock;
    private final FrameLayout hostView;
    private final SurfaceView surfaceView;
    private SurfaceControl windowAnimationLeash;
    private SurfaceControl windowAnimationSurface;
    private float hostedSurfaceAlpha = 1f;
    private final ExecutorService rootExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService inputDispatchExecutor =
            Executors.newSingleThreadExecutor();
    private final Object inputDispatchLock = new Object();
    private final ArrayDeque<PendingMotionEvent> pendingMotionEvents = new ArrayDeque<>();
    private final Matrix touchCoordinateTransform = new Matrix();
    private final int[] rootViewLocationOnScreen = new int[2];
    private final RootInputBridgeClient rootInputBridgeClient =
            new RootInputBridgeClient();
    private final RootVirtualDisplayBridgeClient rootVirtualDisplayBridgeClient =
            new RootVirtualDisplayBridgeClient();
    private final int slot;
    private final boolean rootAvailable;
    private final boolean systemLaunchAvailable;

    private volatile VirtualDisplay virtualDisplay;
    private volatile boolean rootManagedVirtualDisplay;
    private volatile boolean primaryHomeEnhancementActive;
    private LauncherApp pendingApp;
    private volatile int displayId = -1;
    private volatile int hostedTaskId = -1;
    private boolean hostedSurfaceRevealPending;
    private String hostedSurfaceRevealInstanceKey;
    private int hostedSurfaceRevealDisplayId = -1;
    private int hostedSurfaceRevealGeneration;
    private Runnable hostedSurfaceRevealedCallback;
    private String hostedSurfaceRevealCallbackInstanceKey;
    private int displayWidth;
    private int displayHeight;
    private int displayDensityDpi;
    private boolean displayUsesDualMainLayout;
    private int lastViewWidth;
    private int lastViewHeight;
    private float touchDownX;
    private float touchDownY;
    private float latestTouchX;
    private float latestTouchY;
    private float latestTouchRawX;
    private float latestTouchRawY;
    private long touchDownTime;
    private long touchDownWallTime;
    private int touchTargetDisplayId = -1;
    private int touchTargetDisplayWidth;
    private int touchTargetDisplayHeight;
    private int touchTargetViewWidth;
    private int touchTargetViewHeight;
    private int touchTargetDisplayRotation = Surface.ROTATION_0;
    private long touchTraceSequence;
    private long activeTouchTraceId;
    private boolean touchMoved;
    private boolean touchStartedOnMain;
    private boolean touchReservedForSystemNavigation;
    private boolean touchSequenceSuppressed;
    private int touchFocusRequestGeneration;
    private boolean skipActivityOptionsLaunch;
    private String launchRequestedPackage = "";
    private int launchRequestedUserId = -1;
    private int launchRequestedDisplayId = -1;
    private volatile int taskResolutionToken;
    private int hostedTaskValidationGeneration;
    private boolean hostedTaskValidationInFlight;
    private int routedLaunchGeneration;
    private int imageShareReadinessGeneration;
    private long imageShareValidationGuardUntilUptimeMs;
    private int backExitCheckGeneration;
    private int systemResumeExitCheckGeneration;
    private boolean surfaceDetached;
    private int displayReleaseGeneration;
    private boolean virtualDisplayCreationInProgress;
    private int virtualDisplayCreateGeneration;
    private volatile boolean taskResolutionInFlight;
    private volatile boolean rootInputBridgeRecoveryScheduled;
    private boolean inputDispatchRunning;
    private boolean inputDispatchClosed;
    private volatile String cachedRootInputBridgeToken;
    private Presentation demotedFocusWindow;
    private FrameLayout demotedFocusContent;
    private volatile int focusRequestGeneration;
    private volatile int sensorPolicyGeneration;
    private volatile int sensorLandscapeRotationGeneration;
    private volatile int imePolicyConfiguredDisplayId = -1;
    private int imePolicySetupGeneration;
    private int imePolicySetupDisplayId = -1;
    private long lastMotionUnavailableLogUptime;
    private String sensorServiceIdlePackage = "";
    private boolean sensorServiceUidOverrideConfirmed;
    private boolean retainSensorPolicyOnRelease;
    private int requestedSensorLandscapeRotation = -1;
    private boolean sensorLandscapeRotationApplied;
    private boolean bridgePrewarmOnly;
    private String unavailableReason = "";
    RootVirtualDisplayHost(MainActivity owner, Context context, int slot, Callbacks callbacks) {
        this(owner, context, slot, callbacks, false);
    }

    RootVirtualDisplayHost(MainActivity owner, Context context, int slot, Callbacks callbacks,
                           boolean bridgePrewarmOnly) {
        this.owner = owner;
        this.callbacks = callbacks;
        this.slot = slot;
        this.bridgePrewarmOnly = bridgePrewarmOnly;
        windowApps = callbacks.windowApps();
        windowViews = callbacks.windowViews();
        embeddedSlotClosing = callbacks.embeddedSlotClosing();
        mainHandler = callbacks.mainHandler();
        displayImePolicyExecutor = callbacks.displayImePolicyExecutor();
        sensorPolicyExecutor = callbacks.sensorPolicyExecutor();
        persistentRootShell = callbacks.persistentRootShell();
        embeddedStartEpochStore = callbacks.embeddedStartEpochStore();
        rootInputBridgeStartLock = callbacks.rootInputBridgeStartLock();
        packageManager = context.getPackageManager();
        launcherApps = context.getSystemService(LauncherApps.class);
        displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        hostView = new FrameLayout(context);
        hostView.setClipChildren(true);
        surfaceView = new SurfaceView(context);
        // Touch is injected into the hosted display directly.  The host SurfaceView must not
        // become the default-display IME target when the remote app focuses an editor.
        surfaceView.setFocusable(false);
        surfaceView.setFocusableInTouchMode(false);
        surfaceView.setKeepScreenOn(true);
        surfaceView.getHolder().setFormat(PixelFormat.OPAQUE);
        VirtualDisplaySpec initialSpec = makeVirtualDisplaySpec();
        surfaceView.getHolder().setFixedSize(initialSpec.width, initialSpec.height);
        surfaceView.setOnTouchListener(this);
        surfaceView.getHolder().addCallback(this);
        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        hostView.addView(surfaceView, matchParent);
        rootAvailable = hasSuCommand();
        systemLaunchAvailable = hasGrantedSystemEmbeddingPermission() || isSystemAppInstall();
        if (!bridgePrewarmOnly) {
            recoverStaleSensorServiceUidOverridesAsync();
        }
        if (!rootAvailable && !systemLaunchAvailable) {
            unavailableReason = "未检测到 su 或 system/priv-app 权限";
        }
    }

    PinnedTaskState queryPinnedTaskState() {
        if (!rootAvailable || !startRootInputBridgeIfNeeded(false)) {
            return null;
        }
        return rootInputBridgeClient.getPinnedTaskState(getRootInputBridgeToken());
    }

    boolean dockPinnedTask(int taskId, Rect targetBounds, Rect restoreBounds) {
        if (!rootAvailable || !startRootInputBridgeIfNeeded(false)) {
            return false;
        }
        return rootInputBridgeClient.dockPinnedTask(getRootInputBridgeToken(), taskId,
                targetBounds, restoreBounds);
    }

    boolean undockPinnedTask(int taskId, Rect restoreBounds) {
        if (!rootAvailable || !startRootInputBridgeIfNeeded(false)) {
            return false;
        }
        return rootInputBridgeClient.undockPinnedTask(getRootInputBridgeToken(), taskId,
                restoreBounds);
    }

    public boolean ensureWindowAnimationLeash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !surfaceView.isAttachedToWindow()) {
            return false;
        }
        SurfaceControl surfaceControl = surfaceView.getSurfaceControl();
        AttachedSurfaceControl rootSurfaceControl = surfaceView.getRootSurfaceControl();
        if (surfaceControl == null || !surfaceControl.isValid()
                || rootSurfaceControl == null) {
            return false;
        }
        if (windowAnimationLeash != null && windowAnimationLeash.isValid()
                && windowAnimationSurface == surfaceControl) {
            return true;
        }
        releaseStaleWindowAnimationLeash();
        SurfaceControl leash = null;
        try {
            leash = new SurfaceControl.Builder()
                    .setName("OneStep window animation leash " + slot)
                    .setHidden(true)
                    .build();
            SurfaceControl.Transaction transaction =
                    rootSurfaceControl.buildReparentTransaction(leash);
            if (transaction == null) {
                leash.release();
                return false;
            }
            transaction.reparent(surfaceControl, leash)
                    .setAlpha(surfaceControl, 1f)
                    .setLayer(surfaceControl, 0)
                    .setLayer(leash, WINDOW_SURFACE_RESTING_LAYER_BASE - slot)
                    .setAlpha(leash, hostedSurfaceAlpha)
                    .setVisibility(leash, true)
                    .apply();
            transaction.close();
            windowAnimationLeash = leash;
            windowAnimationSurface = surfaceControl;
            return true;
        } catch (RuntimeException e) {
            if (leash != null) {
                leash.release();
            }
            Log.w(TAG, "Create SurfaceControl animation leash failed: slot=" + slot
                    + ", error=" + e.getClass().getSimpleName());
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void prepareWindowSurfaceAnimation(SurfaceControl.Transaction transaction, int layer) {
        transaction.setPosition(windowAnimationLeash, 0f, 0f)
                .setScale(windowAnimationLeash, 1f, 1f)
                .setAlpha(windowAnimationLeash, hostedSurfaceAlpha)
                .setLayer(windowAnimationLeash, layer)
                .setLayer(windowAnimationSurface, 0)
                .setVisibility(windowAnimationLeash, true);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void applyWindowSurfaceAnimation(SurfaceControl.Transaction transaction,
                                     float translationX, float translationY,
                                     float scaleX, float scaleY) {
        transaction.setPosition(windowAnimationLeash, translationX, translationY)
                .setScale(windowAnimationLeash, scaleX, scaleY);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void resetWindowSurfaceAnimation(SurfaceControl.Transaction transaction) {
        if (windowAnimationLeash == null || !windowAnimationLeash.isValid()) {
            return;
        }
        transaction.setPosition(windowAnimationLeash, 0f, 0f)
                .setScale(windowAnimationLeash, 1f, 1f)
                .setAlpha(windowAnimationLeash, hostedSurfaceAlpha)
                .setLayer(windowAnimationLeash, WINDOW_SURFACE_RESTING_LAYER_BASE - slot);
    }

    public void setHostedSurfaceAlpha(float alpha) {
        applyHostedSurfaceAlpha(alpha, false);
    }

    private void applyHostedSurfaceAlpha(float alpha, boolean forceRestingTransform) {
        hostedSurfaceAlpha = Math.max(0f, Math.min(1f, alpha));
        boolean hasAnimationLeash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && windowAnimationLeash != null && windowAnimationLeash.isValid();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && (hasAnimationLeash
                || (forceRestingTransform && ensureWindowAnimationLeash()))) {
            surfaceView.setAlpha(1f);
            SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
            if (forceRestingTransform) {
                transaction.setPosition(windowAnimationLeash, 0f, 0f)
                        .setScale(windowAnimationLeash, 1f, 1f);
            }
            transaction.setAlpha(windowAnimationLeash, hostedSurfaceAlpha);
            transaction.apply();
            transaction.close();
            return;
        }
        surfaceView.setAlpha(hostedSurfaceAlpha);
    }

    private void releaseStaleWindowAnimationLeash() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        SurfaceControl leash = windowAnimationLeash;
        SurfaceControl surfaceControl = windowAnimationSurface;
        windowAnimationLeash = null;
        windowAnimationSurface = null;
        if (leash == null) {
            return;
        }
        try {
            AttachedSurfaceControl rootSurfaceControl = surfaceView.getRootSurfaceControl();
            SurfaceControl.Transaction transaction = surfaceControl != null
                    && surfaceControl.isValid() && rootSurfaceControl != null
                    ? rootSurfaceControl.buildReparentTransaction(surfaceControl) : null;
            if (transaction != null) {
                transaction.setAlpha(surfaceControl, hostedSurfaceAlpha)
                        .setLayer(surfaceControl,
                                WINDOW_SURFACE_RESTING_LAYER_BASE - slot)
                        .reparent(leash, null)
                        .apply();
                transaction.close();
            } else {
                return;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Release SurfaceControl animation leash failed: slot=" + slot
                    + ", error=" + e.getClass().getSimpleName());
        } finally {
            leash.release();
        }
    }

    @Override
    public boolean isAvailable() {
        return displayManager != null && (rootAvailable || systemLaunchAvailable);
    }

    @Override
    public View getView() {
        return hostView;
    }

    @Override
    public boolean canStartBeforeLayout() {
        return true;
    }

    void prepareDirectBootDisplay(LauncherApp desktopApp) {
        if (!bridgePrewarmOnly || desktopApp == null || !isAvailable()) {
            return;
        }
        windowApps[slot] = desktopApp;
        pendingApp = desktopApp;
        surfaceView.setVisibility(View.VISIBLE);
        hostView.post(() -> {
            if (!bridgePrewarmOnly || !isAvailable()) {
                return;
            }
            int width = surfaceView.getWidth();
            int height = surfaceView.getHeight();
            Surface surface = surfaceView.getHolder().getSurface();
            if (width > 0 && height > 0 && surface != null && surface.isValid()) {
                createVirtualDisplay(surfaceView.getHolder(), width, height);
                if (displayId > DEFAULT_DISPLAY_ID && pendingApp != null) {
                    LauncherApp pendingDesktop = pendingApp;
                    pendingApp = null;
                    start(pendingDesktop);
                }
            }
        });
    }

    void completeDirectBootPrewarm() {
        if (!bridgePrewarmOnly) {
            return;
        }
        bridgePrewarmOnly = false;
        embeddedStartEpochStore = callbacks.embeddedStartEpochStore();
        recoverStaleSensorServiceUidOverridesAsync();
    }

    @Override
    public boolean start(LauncherApp app) {
        return start(app, false);
    }

    @Override
    public boolean restart(LauncherApp app) {
        return start(app, true);
    }

    private boolean start(LauncherApp app, boolean forceLaunch) {
        if (!isAvailable() || embeddedSlotClosing[slot]) {
            return false;
        }
        final int startEpoch = callbacks.embeddedStartEpoch();
        if (!shouldRunEmbeddedStart(startEpoch)) {
            unavailableReason = "启动已取消";
            return false;
        }

        if (displayId < 0 || !hasVirtualDisplay()) {
            int viewWidth = surfaceView.getWidth();
            int viewHeight = surfaceView.getHeight();
            Surface surface = surfaceView.getHolder().getSurface();
            if (viewWidth > 0 && viewHeight > 0 && surface != null && surface.isValid()) {
                createVirtualDisplay(surfaceView.getHolder(), viewWidth, viewHeight);
            }
            if (displayId < 0) {
                pendingApp = app;
                hostedTaskId = -1;
                unavailableReason = "虚拟显示初始化中";
                surfaceView.setVisibility(View.VISIBLE);
                return true;
            }
        }

        boolean enhancedHomeLaunch = app.isHomeEntry()
                && primaryHomeEnhancementActive;
        Intent launcherIntent = app.createLaunchIntent(enhancedHomeLaunch);
        if (app.isHomeEntry()) {
            Log.i(TAG, "Start built-in desktop: slot=" + slot
                    + ", display=" + displayId
                    + ", mode=" + (enhancedHomeLaunch ? "enhanced-home" : "regular-app")
                    + ", component=" + app.componentName.flattenToShortString());
        }

        Intent routedLaunchIntent = callbacks.consumeRoutedLaunchIntent(
                slot, app.packageName);
        boolean reusingHostedApp = routedLaunchIntent == null
                && !forceLaunch
                && matchesLaunchRequest(app);
        boolean keepVisibleDuringValidation =
                shouldKeepHostedSurfaceVisibleDuringValidation(app);
        if (HostedSurfaceReusePolicy.shouldBeginReveal(
                reusingHostedApp, hostedTaskId > 0, hostedSurfaceRevealPending,
                keepVisibleDuringValidation)) {
            beginHostedSurfaceReveal(app);
        }
        if (!reusingHostedApp && callbacks.isMainPaneSlot(slot)) {
            ensureDisplayImeLocalPolicyAsync("main display app launch " + app.packageName);
        }
        return continueHostedAppStart(app, launcherIntent, routedLaunchIntent,
                startEpoch, reusingHostedApp);
    }

    private boolean continueHostedAppStart(LauncherApp app, Intent launcherIntent,
                                           Intent routedLaunchIntent,
                                           int startEpoch,
                                           boolean reusingHostedApp) {
        if (!shouldRunEmbeddedStart(startEpoch) || embeddedSlotClosing[slot]
                || displayId <= DEFAULT_DISPLAY_ID || !hasVirtualDisplay()) {
            unavailableReason = "启动已取消";
            return false;
        }
        if (app.isCurrentUser() && !bridgePrewarmOnly) {
            syncHostedSensorIsolationAsync(app.packageName);
        }
        if (reusingHostedApp) {
            pendingApp = null;
            unavailableReason = "";
            if (!HostedSurfaceReusePolicy.shouldValidateReusedTask(
                    isImageShareValidationGuardActive(app))) {
                Log.i(TAG, "Keep pending image share launch during duplicate host start: slot="
                        + slot + ", display=" + displayId
                        + ", package=" + app.packageName);
                return true;
            }
            validateReusedHostedApp(app, startEpoch);
            return true;
        }

        pendingApp = null;
        invalidateTaskResolution();

        syncLaunchRoutingSource();
        rootVirtualDisplayBridgeClient.allowNextLaunch(
                getRootInputBridgeToken(), app.packageName);

        boolean directImageShare = routedLaunchIntent != null
                && routedLaunchIntent.getBooleanExtra(
                MainActivity.EXTRA_IMAGE_SHARE_ROUTE, false)
                && !routedLaunchIntent.getBooleanExtra(
                MainActivity.EXTRA_IMAGE_SHARE_WAIT_FOR_APP_READY, false);
        if (directImageShare && systemLaunchAvailable
                && !skipActivityOptionsLaunch
                && startWithActivityOptions(
                routedLaunchIntent, app, startEpoch, false)) {
            launchRequestedPackage = app.packageName;
            launchRequestedUserId = app.userId();
            launchRequestedDisplayId = displayId;
            armImageShareValidationGuard();
            scheduleHostedTaskResolution("direct image share " + app.packageName);
            ComponentName initialComponent = routedLaunchIntent.getComponent();
            scheduleImageShareReadinessChecks(app,
                    initialComponent == null ? null : initialComponent.getClassName());
            unavailableReason = "";
            return true;
        }

        if (routedLaunchIntent != null && systemLaunchAvailable
                && !skipActivityOptionsLaunch
                && startWithActivityOptions(launcherIntent, app, startEpoch, true)) {
            launchRequestedPackage = app.packageName;
            launchRequestedUserId = app.userId();
            launchRequestedDisplayId = displayId;
            scheduleRoutedLaunch(app, routedLaunchIntent, startEpoch, displayId);
            unavailableReason = "";
            return true;
        }

        Intent requestedLaunchIntent = routedLaunchIntent != null
                ? routedLaunchIntent : launcherIntent;
        boolean preferDirectUserLaunch = rootAvailable
                && routedLaunchIntent == null
                && !app.isHomeEntry();
        if (!preferDirectUserLaunch
                && systemLaunchAvailable && !skipActivityOptionsLaunch
                && startWithActivityOptions(requestedLaunchIntent,
                app, startEpoch, routedLaunchIntent == null)) {
            launchRequestedPackage = app.packageName;
            launchRequestedUserId = app.userId();
            launchRequestedDisplayId = displayId;
            scheduleHostedTaskResolution("launch " + app.packageName);
            unavailableReason = "";
            return true;
        }

        ComponentName componentName = resolveLaunchComponent(requestedLaunchIntent);
        if (componentName != null) {
            String command = buildDisplayStartCommand(
                    requestedLaunchIntent, componentName, app);
            launchRequestedPackage = app.packageName;
            launchRequestedUserId = app.userId();
            launchRequestedDisplayId = displayId;
            runStartCommandAsync(command, startEpoch,
                    "start " + app.packageName + " on display " + displayId);
            scheduleHostedTaskResolution("launch " + app.packageName);
            unavailableReason = "";
            return true;
        }

        unavailableReason = "没有可用的 display 启动权限";
        launchRequestedPackage = "";
        launchRequestedUserId = -1;
        launchRequestedDisplayId = -1;
        return false;
    }

    private String buildDisplayStartCommand(Intent launchIntent,
                                            ComponentName componentName,
                                            LauncherApp app) {
        StringBuilder command = new StringBuilder("am start --display ")
                .append(displayId)
                .append(" --user ")
                .append(app.userId());
        if ((launchIntent.getFlags() & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0) {
            command.append(" --activity-no-animation");
        }
        if (!TextUtils.isEmpty(launchIntent.getAction())) {
            command.append(" -a ").append(shellQuote(launchIntent.getAction()));
        }
        Set<String> categories = launchIntent.getCategories();
        if (categories != null) {
            for (String category : categories) {
                command.append(" -c ").append(shellQuote(category));
            }
        }
        if (launchIntent.getBooleanExtra(
                OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME, false)) {
            command.append(" --ez ")
                    .append(shellQuote(
                            OneStepPrimaryHomePolicy.EXTRA_EMBEDDED_PRIMARY_HOME))
                    .append(" true");
        }
        if (!app.isHomeEntry() && usesOemCloneResolver()) {
            // The OEM launcher adds these after the user has selected an app instance.
            // Supplying them with an explicit --user makes each top-bar icon launch directly.
            command.append(" --ez ")
                    .append(shellQuote(EXTRA_XSPACE_AUTHORIZED))
                    .append(" true")
                    .append(" --ei ")
                    .append(shellQuote(EXTRA_XSPACE_TARGET_USER))
                    .append(' ')
                    .append(app.userId())
                    .append(" --ez ")
                    .append(shellQuote(EXTRA_CLONE_RESOLVER_CONFIRMED))
                    .append(" true")
                    .append(" --ei ")
                    .append(shellQuote(EXTRA_CLONE_RESOLVER_CALLING_USER))
                    .append(' ')
                    .append(android.os.Process.myUserHandle().hashCode())
                    .append(" --es ")
                    .append(shellQuote(EXTRA_CLONE_RESOLVER_CALLING_PACKAGE))
                    .append(' ')
                    .append(shellQuote(owner.getPackageName()));
        }
        return command.append(" -n ")
                .append(shellQuote(componentName.flattenToShortString()))
                .toString();
    }

    private boolean usesOemCloneResolver() {
        return "nubia".equalsIgnoreCase(Build.MANUFACTURER)
                || "zte".equalsIgnoreCase(Build.MANUFACTURER);
    }

    private void scheduleRoutedLaunch(LauncherApp app, Intent routedLaunchIntent,
                                      int startEpoch, int targetDisplayId) {
        final int generation = ++routedLaunchGeneration;
        final Intent routedIntent = new Intent(routedLaunchIntent);
        if (routedIntent.getBooleanExtra(
                MainActivity.EXTRA_IMAGE_SHARE_WAIT_FOR_APP_READY, false)) {
            armImageShareValidationGuard();
            scheduleRoutedImageShareAppReadyCheck(
                    generation, app, routedIntent, startEpoch, targetDisplayId,
                    0, "", 0);
            return;
        }
        mainHandler.postDelayed(() -> launchRoutedIntentIfCurrent(
                generation, app, routedIntent, startEpoch, targetDisplayId),
                ROUTED_LAUNCH_AFTER_MAIN_DELAY_MS);
    }

    private void scheduleRoutedImageShareAppReadyCheck(
            int generation, LauncherApp app, Intent routedIntent,
            int startEpoch, int targetDisplayId, int attempt,
            String previousTopActivity, int stableScans) {
        if (attempt < 0 || attempt >= ROUTED_IMAGE_SHARE_APP_READY_RETRY_MS.length) {
            return;
        }
        mainHandler.postDelayed(() -> checkRoutedImageShareAppReady(
                generation, app, routedIntent, startEpoch, targetDisplayId,
                attempt, previousTopActivity, stableScans),
                ROUTED_IMAGE_SHARE_APP_READY_RETRY_MS[attempt]);
    }

    private void checkRoutedImageShareAppReady(
            int generation, LauncherApp app, Intent routedIntent,
            int startEpoch, int targetDisplayId, int attempt,
            String previousTopActivity, int stableScans) {
        if (!isCurrentRoutedLaunch(
                generation, app, startEpoch, targetDisplayId)) {
            return;
        }
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND,
                        "wait for routed share app " + app.packageName, false);
                String topActivity = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output)
                        ? HostedTaskParser.findVisibleTopActivity(
                        stackList.output, targetDisplayId, app.packageName) : "";
                int nextStableScans = TextUtils.isEmpty(topActivity) ? 0
                        : TextUtils.equals(previousTopActivity, topActivity)
                        ? stableScans + 1 : 1;
                mainHandler.post(() -> {
                    if (!isCurrentRoutedLaunch(
                            generation, app, startEpoch, targetDisplayId)) {
                        return;
                    }
                    boolean finalAttempt = attempt + 1
                            >= ROUTED_IMAGE_SHARE_APP_READY_RETRY_MS.length;
                    if (nextStableScans >= ROUTED_IMAGE_SHARE_STABLE_SCANS
                            || finalAttempt) {
                        Log.i(TAG, "Routed share app initialization complete: slot=" + slot
                                + ", display=" + targetDisplayId
                                + ", package=" + app.packageName
                                + ", component=" + topActivity
                                + ", stableScans=" + nextStableScans
                                + ", fallback=" + (nextStableScans
                                < ROUTED_IMAGE_SHARE_STABLE_SCANS));
                        launchRoutedIntentIfCurrent(
                                generation, app, routedIntent,
                                startEpoch, targetDisplayId);
                        return;
                    }
                    scheduleRoutedImageShareAppReadyCheck(
                            generation, app, routedIntent, startEpoch,
                            targetDisplayId, attempt + 1,
                            topActivity, nextStableScans);
                });
            });
        } catch (RuntimeException e) {
            scheduleRoutedImageShareAppReadyCheck(
                    generation, app, routedIntent, startEpoch,
                    targetDisplayId, attempt + 1, "", 0);
        }
    }

    private void launchRoutedIntentIfCurrent(
            int generation, LauncherApp app, Intent routedIntent,
            int startEpoch, int targetDisplayId) {
        if (!isCurrentRoutedLaunch(
                generation, app, startEpoch, targetDisplayId)) {
            return;
        }
        rootVirtualDisplayBridgeClient.allowNextLaunch(
                getRootInputBridgeToken(), app.packageName);
        boolean routed = startWithActivityOptions(
                routedIntent, app, startEpoch, false);
        if (routed && routedIntent.getBooleanExtra(
                MainActivity.EXTRA_IMAGE_SHARE_ROUTE, false)) {
            armImageShareValidationGuard();
            ComponentName initialComponent = routedIntent.getComponent();
            scheduleImageShareReadinessChecks(app,
                    initialComponent == null
                            ? null : initialComponent.getClassName());
        }
        scheduleHostedTaskResolution((routed ? "routed launch "
                : "launcher fallback after routed launch failure ") + app.packageName);
        // The launcher task remains visible if an app-owned routing activity exits.
        unavailableReason = "";
        routedLaunchGeneration++;
    }

    private boolean isCurrentRoutedLaunch(
            int generation, LauncherApp app,
            int startEpoch, int targetDisplayId) {
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS
                ? windowApps[slot] : null;
        return generation == routedLaunchGeneration
                && targetDisplayId == displayId
                && shouldRunEmbeddedStart(startEpoch)
                && !embeddedSlotClosing[slot]
                && currentApp != null
                && currentApp.isSameInstance(app);
    }

    private void validateReusedHostedApp(LauncherApp app, int startEpoch) {
        if (!rootAvailable || hostedTaskValidationInFlight
                || displayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        final int generation = ++hostedTaskValidationGeneration;
        final int targetDisplayId = displayId;
        final String targetPackage = app.packageName;
        hostedTaskValidationInFlight = true;
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND, "validate reused hosted app", false);
                int resolvedTaskId = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output)
                        ? findVisibleHostedTaskId(
                        stackList.output, targetDisplayId, app) : -1;
                boolean scanSucceeded = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output);
                mainHandler.post(() -> {
                    if (generation != hostedTaskValidationGeneration) {
                        return;
                    }
                    hostedTaskValidationInFlight = false;
                    LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS
                            ? windowApps[slot] : null;
                    if (!scanSucceeded || targetDisplayId != displayId
                            || !shouldRunEmbeddedStart(startEpoch)
                            || embeddedSlotClosing[slot]
                            || currentApp == null
                            || !currentApp.isSameInstance(app)
                            || !TextUtils.equals(launchRequestedPackage, targetPackage)
                            || launchRequestedUserId != app.userId()
                            || launchRequestedDisplayId != targetDisplayId) {
                        return;
                    }
                    if (resolvedTaskId > 0) {
                        hostedTaskId = resolvedTaskId;
                        scheduleHostedSurfaceReveal(app, targetDisplayId);
                        return;
                    }
                    Log.w(TAG, "Restart hosted app after stale task reuse: slot=" + slot
                            + ", display=" + targetDisplayId
                            + ", package=" + targetPackage);
                    hostedTaskId = -1;
                    launchRequestedPackage = "";
                    launchRequestedUserId = -1;
                    launchRequestedDisplayId = -1;
                    boolean live = start(app);
                    windowViews[slot].setLiveAppVisible(
                            live && !isHostedSurfaceRevealPending(app));
                    if (!live) {
                        unavailableReason = "应用任务已结束，重新启动失败";
                    }
                });
            });
        } catch (RuntimeException e) {
            hostedTaskValidationInFlight = false;
        }
    }

    private void syncHostedSensorIsolation(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            resetSensorServiceUidOverride();
            return;
        }
        if (TextUtils.equals(sensorServiceIdlePackage, packageName)
                && sensorServiceUidOverrideConfirmed) {
            return;
        }
        if (!resetSensorServiceUidOverride()) {
            return;
        }
        if (!rootAvailable) {
            Log.w(TAG, "Hosted sensor isolation unavailable without root: slot=" + slot
                    + ", display=" + displayId + ", package=" + packageName);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.w(TAG, "SensorService UID isolation requires Android 9 or newer: package="
                    + packageName);
            return;
        }
        if (!isSafeSensorUidOverrideTarget(packageName)) {
            return;
        }
        String command = buildSensorUidIdleCommand(packageName);
        recordSensorUidOverride(packageName);
        sensorServiceIdlePackage = packageName;
        sensorServiceUidOverrideConfirmed = false;
        ShellCommandResult result = runPrivilegedCommand(command,
                "isolate hosted sensors for " + packageName, true);
        String output = result.output == null ? "" : result.output;
        if (result.exitCode == 0 && output.contains("__ONESTEP_SENSOR_UID_IDLE")) {
            sensorServiceUidOverrideConfirmed = true;
            Log.i(TAG, "SensorService UID isolation active: slot=" + slot
                    + ", display=" + displayId + ", package=" + packageName
                    + ", mode=uid");
        } else {
            Log.w(TAG, "SensorService UID isolation unavailable: slot=" + slot
                    + ", display=" + displayId + ", package=" + packageName);
            if (result.exitCode > 0) {
                sensorServiceIdlePackage = "";
                sensorServiceUidOverrideConfirmed = false;
                clearSensorUidOverrideRecord(packageName);
            } else {
                resetSensorServiceUidOverride();
            }
        }
    }

    private void syncHostedSensorIsolationAsync(String packageName) {
        final int generation = ++sensorPolicyGeneration;
        try {
            sensorPolicyExecutor.execute(() -> {
                if (generation != sensorPolicyGeneration || callbacks.isActivityDestroyed()) {
                    return;
                }
                syncHostedSensorIsolation(packageName);
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue hosted sensor isolation failed: slot=" + slot
                    + ", package=" + packageName + ", error="
                    + e.getClass().getSimpleName());
        }
    }

    private void resetSensorServiceUidOverrideAsync() {
        final int generation = ++sensorPolicyGeneration;
        try {
            sensorPolicyExecutor.execute(() -> {
                if (generation == sensorPolicyGeneration) {
                    resetSensorServiceUidOverride();
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue hosted sensor reset failed: slot=" + slot
                    + ", error=" + e.getClass().getSimpleName());
        }
    }

    private boolean isSafeSensorUidOverrideTarget(String packageName) {
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                    packageName, 0);
            int uid = applicationInfo.uid;
            int appId = Math.floorMod(uid, 100000);
            if (appId < 10000 || uid == android.os.Process.myUid()) {
                Log.w(TAG, "Refuse sensor UID isolation for system/self UID: package="
                        + packageName + ", uid=" + uid);
                return false;
            }
            String[] uidPackages = packageManager.getPackagesForUid(uid);
            if (uidPackages == null || uidPackages.length != 1
                    || !TextUtils.equals(uidPackages[0], packageName)) {
                Log.w(TAG, "Refuse sensor UID isolation for shared UID: package="
                        + packageName + ", uid=" + uid);
                return false;
            }
            return true;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.w(TAG, "Resolve sensor isolation UID failed for " + packageName + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean resetSensorServiceUidOverride() {
        String packageName = sensorServiceIdlePackage;
        if (TextUtils.isEmpty(packageName)) {
            return true;
        }
        if (!rootAvailable) {
            return false;
        }
        String command = buildSensorUidResetCommand(packageName);
        ShellCommandResult result = runPrivilegedCommand(command,
                "restore hosted sensors for " + packageName, true);
        String output = result.output == null ? "" : result.output;
        if (result.exitCode == 0 && output.contains("__ONESTEP_SENSOR_UID_RESET")) {
            sensorServiceIdlePackage = "";
            sensorServiceUidOverrideConfirmed = false;
            clearSensorUidOverrideRecord(packageName);
            return true;
        } else {
            Log.w(TAG, "Restore SensorService UID state failed: package=" + packageName);
            return false;
        }
    }

    private String buildSensorUidIdleCommand(String packageName) {
        int userId = getCurrentAndroidUserId();
        String quotedPackage = shellQuote(packageName);
        String command = "if cmd sensorservice set-uid-state " + quotedPackage
                + " idle --user " + userId + " >/dev/null 2>&1; then "
                + "echo __ONESTEP_SENSOR_UID_IDLE mode=user; ";
        if (userId == 0) {
            command += "elif cmd sensorservice set-uid-state " + quotedPackage
                    + " idle >/dev/null 2>&1; then "
                    + "echo __ONESTEP_SENSOR_UID_IDLE mode=legacy; ";
        }
        return command
                + "else echo __ONESTEP_SENSOR_UID_IDLE unavailable; exit 1; fi";
    }

    private String buildSensorUidResetCommand(String packageName) {
        int userId = getCurrentAndroidUserId();
        String quotedPackage = shellQuote(packageName);
        String command = "if cmd sensorservice reset-uid-state " + quotedPackage
                + " --user " + userId + " >/dev/null 2>&1; then "
                + "echo __ONESTEP_SENSOR_UID_RESET mode=user; ";
        if (userId == 0) {
            command += "elif cmd sensorservice reset-uid-state " + quotedPackage
                    + " >/dev/null 2>&1; then "
                    + "echo __ONESTEP_SENSOR_UID_RESET mode=legacy; ";
        }
        return command
                + "else echo __ONESTEP_SENSOR_UID_RESET unavailable; exit 1; fi";
    }

    private int getCurrentAndroidUserId() {
        return Math.max(0, android.os.Process.myUid() / 100000);
    }

    private void recoverStaleSensorServiceUidOverrides() {
        synchronized (owner) {
            if (!callbacks.claimStaleSensorUidOverrideRecovery()) {
                return;
            }
        }
        Set<String> packages = getRecordedSensorUidOverrides();
        if (packages.isEmpty() || !rootAvailable) {
            return;
        }
        for (String packageName : packages) {
            ShellCommandResult result = runPrivilegedCommand(
                    buildSensorUidResetCommand(packageName),
                    "recover stale sensor state for " + packageName, true);
            String output = result.output == null ? "" : result.output;
            if (result.exitCode == 0
                    && output.contains("__ONESTEP_SENSOR_UID_RESET")) {
                clearSensorUidOverrideRecord(packageName);
            } else {
                Log.w(TAG, "Stale SensorService UID state remains: package="
                        + packageName);
            }
        }
    }

    private void recoverStaleSensorServiceUidOverridesAsync() {
        try {
            sensorPolicyExecutor.execute(this::recoverStaleSensorServiceUidOverrides);
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue stale sensor state recovery failed: "
                    + e.getClass().getSimpleName());
        }
    }

    @Override
    public void refreshContainerSize() {
        if (embeddedSlotClosing[slot]) {
            return;
        }
        int viewWidth = surfaceView.getWidth();
        int viewHeight = surfaceView.getHeight();
        if (!hasVirtualDisplay() || displayId < 0 || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }
        if (callbacks.isWindowFrameAnimationRunning()) {
            return;
        }
        refreshVirtualDisplaySize(surfaceView.getHolder(), viewWidth, viewHeight);
    }

    @Override
    public void sendBack() {
        if (displayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        LauncherApp app = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        int targetDisplayId = displayId;
        int targetTaskId = hostedTaskId;
        int generation = ++backExitCheckGeneration;
        if (HostedBackDispatchPolicy.shouldDispatchBeforeExitCheck(
                app != null, app != null && app.isHomeEntry(), isMainDisplaySlot(slot))) {
            // A single Activity can still own a Fragment, WebView, or Compose back stack.
            // Always let the hosted app consume Back before deciding whether its task exited.
            injectBackAndScheduleExitCheck(
                    app, targetDisplayId, targetTaskId, generation);
            return;
        }
        injectKeyDirectAsync(KeyEvent.KEYCODE_BACK, "back display " + displayId);
    }

    private void injectBackAndScheduleExitCheck(LauncherApp app, int targetDisplayId,
                                                int targetTaskId, int generation) {
        injectKeyDirectAsync(KeyEvent.KEYCODE_BACK, "back display " + targetDisplayId);
        scheduleBackExitCheck(app, targetDisplayId, targetTaskId,
                generation, 0, 0);
    }

    private void scheduleBackExitCheck(LauncherApp app, int targetDisplayId,
                                       int targetTaskId, int generation, int attempt,
                                       int consecutiveMissingScans) {
        if (attempt < 0 || attempt >= BACK_EXIT_CHECK_DELAYS_MS.length) {
            return;
        }
        mainHandler.postDelayed(() -> runBackExitCheck(
                        app, targetDisplayId, targetTaskId, generation, attempt,
                        consecutiveMissingScans),
                BACK_EXIT_CHECK_DELAYS_MS[attempt]);
    }

    private void runBackExitCheck(LauncherApp app, int targetDisplayId,
                                  int targetTaskId, int generation, int attempt,
                                  int consecutiveMissingScans) {
        if (!isCurrentBackExitCheck(app, targetDisplayId, generation)) {
            return;
        }
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND, "check app after back", false);
                boolean scanSucceeded = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output);
                boolean taskPresent = scanSucceeded && (targetTaskId > 0
                        ? HostedTaskParser.containsTaskOnDisplay(
                        stackList.output, targetDisplayId, targetTaskId)
                        : HostedTaskParser.findHostedTaskId(
                        stackList.output, targetDisplayId, app.packageName) > 0);
                mainHandler.post(() -> finishBackExitCheck(
                        app, targetDisplayId, targetTaskId, generation, attempt,
                        consecutiveMissingScans, scanSucceeded, taskPresent));
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue app exit check after back failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
        }
    }

    private void finishBackExitCheck(LauncherApp app, int targetDisplayId,
                                     int targetTaskId, int generation, int attempt,
                                     int consecutiveMissingScans,
                                     boolean scanSucceeded, boolean taskPresent) {
        if (!isCurrentBackExitCheck(app, targetDisplayId, generation)) {
            return;
        }
        boolean canRetry = attempt + 1 < BACK_EXIT_CHECK_DELAYS_MS.length;
        HostedBackExitPolicy.Action action = HostedBackExitPolicy.afterScan(
                scanSucceeded, taskPresent, consecutiveMissingScans, canRetry);
        if (action == HostedBackExitPolicy.Action.KEEP_APP) {
            return;
        }
        if (action == HostedBackExitPolicy.Action.SHOW_DESKTOP) {
            hostedTaskId = -1;
            launchRequestedPackage = "";
            launchRequestedUserId = -1;
            launchRequestedDisplayId = -1;
            callbacks.onHostedAppExitedAfterBack(slot, app, null);
            return;
        }
        int nextMissingScans = scanSucceeded && !taskPresent
                ? consecutiveMissingScans + 1 : 0;
        scheduleBackExitCheck(app, targetDisplayId, targetTaskId, generation,
                attempt + 1, nextMissingScans);
    }

    private boolean isCurrentBackExitCheck(LauncherApp expectedApp,
                                           int targetDisplayId, int generation) {
        if (generation != backExitCheckGeneration || targetDisplayId != displayId
                || slot < 0 || slot >= MAX_WINDOWS || !isMainDisplaySlot(slot)
                || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp currentApp = windowApps[slot];
        return currentApp != null && expectedApp.isSameInstance(currentApp);
    }

    void checkHostedTaskAfterSystemTaskChange() {
        if (displayId <= DEFAULT_DISPLAY_ID || !isMainDisplaySlot(slot)
                || embeddedSlotClosing[slot]) {
            return;
        }
        LauncherApp app = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        if (app == null || app.isHomeEntry() || isHostedSurfaceRevealPending(app)) {
            return;
        }
        int targetDisplayId = displayId;
        int targetTaskId = hostedTaskId;
        int generation = ++systemResumeExitCheckGeneration;
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND,
                        "check hosted task on activity resume", false);
                boolean scanSucceeded = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output);
                boolean taskPresent = scanSucceeded && (targetTaskId > 0
                        ? HostedTaskParser.containsTaskOnDisplay(
                        stackList.output, targetDisplayId, targetTaskId)
                        : HostedTaskParser.findHostedTaskId(
                        stackList.output, targetDisplayId, app.packageName) > 0);
                mainHandler.post(() -> {
                    if (generation != systemResumeExitCheckGeneration
                            || !isCurrentSystemResumeExitCheck(
                            app, targetDisplayId)) {
                        return;
                    }
                    if (scanSucceeded && !taskPresent) {
                        Log.i(TAG, "Hosted task disappeared after system task change: slot="
                                + slot
                                + ", display=" + targetDisplayId
                                + ", app=" + app.packageName);
                        hostedTaskId = -1;
                        launchRequestedPackage = "";
                        launchRequestedUserId = -1;
                        launchRequestedDisplayId = -1;
                        callbacks.onHostedAppExitedAfterBack(slot, app, null);
                    }
                });
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue hosted task check on activity resume failed for slot " + slot);
        }
    }

    private boolean isCurrentSystemResumeExitCheck(
            LauncherApp expectedApp, int targetDisplayId) {
        if (targetDisplayId != displayId || !isMainDisplaySlot(slot)
                || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp currentApp = windowApps[slot];
        return currentApp != null && expectedApp.isSameInstance(currentApp);
    }

    boolean matchesHostedTask(LauncherApp app, int taskId) {
        if (app == null || taskId <= 0 || taskId != hostedTaskId
                || slot < 0 || slot >= MAX_WINDOWS) {
            return false;
        }
        LauncherApp currentApp = windowApps[slot];
        return currentApp != null && app.isSameInstance(currentApp);
    }

    boolean onHostedTaskMovedToFront(
            LauncherApp expectedApp, int taskId, String topPackageName) {
        if (expectedApp == null || taskId <= 0 || displayId <= DEFAULT_DISPLAY_ID
                || slot < 0 || slot >= MAX_WINDOWS || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp currentApp = windowApps[slot];
        if (currentApp == null || !expectedApp.isSameInstance(currentApp)) {
            return false;
        }
        boolean targetApp = TextUtils.equals(expectedApp.packageName, topPackageName);
        if (!targetApp && !isHostedSurfaceRevealPending(expectedApp)) {
            return false;
        }

        hostedTaskId = taskId;
        if (isHostedSurfaceRevealPending(expectedApp)) {
            Log.i(TAG, "Reveal hosted launch UI: slot=" + slot
                    + ", display=" + displayId
                    + ", taskId=" + taskId
                    + ", topPackage=" + topPackageName
                    + ", targetPackage=" + expectedApp.packageName);
            scheduleHostedSurfaceReveal(expectedApp, displayId);
        }
        return true;
    }

    @Override
    public void sendHome() {
        if (displayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        invalidateTaskResolution();
        invalidateDisplayImePolicySetup();
        pendingApp = null;
        launchRequestedPackage = "";
        launchRequestedUserId = -1;
        launchRequestedDisplayId = -1;
        if (!VirtualDisplayHomeKeyPolicy.shouldInjectHomeKey(Build.VERSION.SDK_INT)) {
            Log.i(TAG, "Skip virtual HOME key on Android 11 display " + displayId);
            return;
        }
        injectKeyDirectAsync(KeyEvent.KEYCODE_HOME, "home display " + displayId);
    }

    @Override
    public void invalidateTaskResolution() {
        backExitCheckGeneration++;
        taskResolutionToken++;
        taskResolutionInFlight = false;
        hostedTaskId = -1;
        hostedTaskValidationGeneration++;
        hostedTaskValidationInFlight = false;
        routedLaunchGeneration++;
        imageShareValidationGuardUntilUptimeMs = 0L;
    }

    boolean hasResolvedHostedTask(LauncherApp app) {
        return app != null && hostedTaskId > 0
                && displayId > DEFAULT_DISPLAY_ID && matchesLaunchRequest(app);
    }

    boolean shouldKeepHostedSurfaceVisibleDuringValidation(LauncherApp app) {
        return app != null && hasVirtualDisplay()
                && HostedSurfaceReusePolicy.shouldKeepVisibleDuringValidation(
                matchesLaunchRequest(app), isHostedSurfaceRevealPending(app),
                hostedSurfaceAlpha > 0f);
    }

    private boolean matchesLaunchRequest(LauncherApp app) {
        return app != null
                && displayId == launchRequestedDisplayId
                && TextUtils.equals(app.packageName, launchRequestedPackage)
                && launchRequestedUserId == app.userId();
    }

    void validateHostedTaskVisible(LauncherApp app,
                                   java.util.function.Consumer<Boolean> callback) {
        if (app == null || callback == null || displayId <= DEFAULT_DISPLAY_ID) {
            if (callback != null) {
                mainHandler.post(() -> callback.accept(false));
            }
            return;
        }
        final int targetDisplayId = displayId;
        final String targetInstanceKey = app.instanceKey();
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND, "validate hosted task visibility", false);
                int resolvedTaskId = stackList.exitCode == 0
                        && !TextUtils.isEmpty(stackList.output)
                        ? findVisibleHostedTaskId(stackList.output, targetDisplayId, app) : -1;
                mainHandler.post(() -> {
                    LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS
                            ? windowApps[slot] : null;
                    boolean current = targetDisplayId == displayId && currentApp != null
                            && targetInstanceKey.equals(currentApp.instanceKey())
                            && !embeddedSlotClosing[slot];
                    if (current && resolvedTaskId > 0) {
                        hostedTaskId = resolvedTaskId;
                        launchRequestedPackage = app.packageName;
                        launchRequestedUserId = app.userId();
                        launchRequestedDisplayId = targetDisplayId;
                    } else if (current) {
                        hostedTaskId = -1;
                    }
                    callback.accept(current && resolvedTaskId > 0);
                });
            });
        } catch (RuntimeException e) {
            mainHandler.post(() -> callback.accept(false));
        }
    }

    void runAfterHostedTaskVisible(LauncherApp app, Runnable callback) {
        if (callback == null) {
            return;
        }
        if (hasResolvedHostedTask(app) && !isHostedSurfaceRevealPending(app)) {
            callback.run();
            return;
        }
        if (isHostedSurfaceRevealPending(app)) {
            hostedSurfaceRevealedCallback = callback;
            hostedSurfaceRevealCallbackInstanceKey = app.instanceKey();
            return;
        }
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        if (currentApp != null && app != null && app.isSameInstance(currentApp)) {
            hostedSurfaceRevealedCallback = callback;
            hostedSurfaceRevealCallbackInstanceKey = app.instanceKey();
        }
    }

    boolean isHostedSurfaceRevealPending(LauncherApp app) {
        return app != null && hostedSurfaceRevealPending
                && displayId == hostedSurfaceRevealDisplayId
                && app.instanceKey().equals(hostedSurfaceRevealInstanceKey);
    }

    void concealHostedSurfaceForDesktopTakeover(LauncherApp app) {
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        if (app == null || currentApp == null || !app.isSameInstance(currentApp)) {
            return;
        }
        systemResumeExitCheckGeneration++;
        invalidateTaskResolution();
        pendingApp = null;
        launchRequestedPackage = "";
        launchRequestedUserId = -1;
        launchRequestedDisplayId = -1;
        cancelHostedSurfaceReveal();
        applyHostedSurfaceAlpha(0f, false);
        windowViews[slot].setLiveAppVisible(false);
    }

    @Override
    public void closeApp(LauncherApp app, Runnable onClosed) {
        final String targetPackage = app == null || TextUtils.isEmpty(app.packageName)
                ? getHostedPackageName() : app.packageName;
        final boolean forceStopPackage = DismissedAppClosePolicy.shouldForceStop(
                app != null && app.isHomeEntry());
        final int targetUserId = app == null
                ? android.os.Process.myUserHandle().hashCode()
                : app.userId();
        final boolean targetCurrentUser = app == null || app.isCurrentUser();
        final int targetTaskId = hostedTaskId;
        invalidateTaskResolution();
        invalidateDisplayImePolicySetup();
        pendingApp = null;
        launchRequestedPackage = "";
        launchRequestedUserId = -1;
        launchRequestedDisplayId = -1;
        cancelHostedSurfaceReveal();
        try {
            rootExecutor.execute(() -> {
                if (!TextUtils.isEmpty(targetPackage)) {
                    removeDismissedHostedTask(targetPackage, targetTaskId);
                    if (forceStopPackage) {
                        ShellCommandResult result = runPrivilegedCommand(
                                "am force-stop --user " + targetUserId + " "
                                        + shellQuote(targetPackage),
                                "force-stop dismissed app " + targetPackage, true);
                        if (result.exitCode != 0 && targetCurrentUser) {
                            result = runPrivilegedCommand(
                                    "am force-stop " + shellQuote(targetPackage),
                                    "fallback force-stop dismissed app " + targetPackage, true);
                        }
                        if (result.exitCode != 0) {
                            Log.e(TAG, "Force-stop dismissed app failed: " + targetPackage
                                    + " exit=" + result.exitCode);
                        }
                    } else {
                        Log.i(TAG, "Keep HOME package running after dismissal: "
                                + targetPackage);
                    }
                }
                resetSensorServiceUidOverrideAsync();
                mainHandler.post(() -> {
                    if (onClosed != null) {
                        onClosed.run();
                    }
                });
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue hosted app close failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            mainHandler.post(() -> {
                resetSensorServiceUidOverrideAsync();
                if (onClosed != null) {
                    onClosed.run();
                }
            });
        }
    }

    private void removeDismissedHostedTask(String packageName, int taskId) {
        if (taskId <= 0) {
            Log.w(TAG, "No resolved task ID while dismissing app: " + packageName);
            return;
        }
        boolean removed = startRootInputBridgeIfNeeded(false)
                && rootInputBridgeClient.removeTask(getRootInputBridgeToken(), taskId);
        if (!removed) {
            Log.e(TAG, "Remove dismissed app task failed: package=" + packageName
                    + ", taskId=" + taskId);
        }
    }

    @Override
    public void release() {
        shutdownInputDispatch();
        invalidateDisplayImePolicySetup();
        // The owning activity defers this call during HOME-driven instance replacement so the
        // framework cannot dispatch a pending HOME request to an already removed display.
        releaseVirtualDisplayWithRetry("host release", null);
        releaseStaleWindowAnimationLeash();
        rootInputBridgeClient.close();
        rootExecutor.shutdownNow();
    }

    void releaseForActivityReplacement() {
        retainSensorPolicyOnRelease = true;
        release();
    }

    @Override
    public String getUnavailableReason() {
        return unavailableReason;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceView.post(this::ensureWindowAnimationLeash);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (embeddedSlotClosing[slot]) {
            return;
        }
        int viewWidth = surfaceView.getWidth();
        int viewHeight = surfaceView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0 || !isAvailable()) {
            return;
        }
        LauncherApp appToStart = pendingApp;
        if (appToStart == null && slot >= 0 && slot < MAX_WINDOWS) {
            appToStart = windowApps[slot];
        }
        if (appToStart == null && !hasVirtualDisplay()) {
            return;
        }
        if (!hasVirtualDisplay()) {
            createVirtualDisplay(holder, viewWidth, viewHeight);
        } else {
            if (surfaceDetached) {
                attachVirtualDisplaySurface(holder);
            }
            if (callbacks.isWindowFrameAnimationRunning()) {
                return;
            }
            if (viewWidth != lastViewWidth || viewHeight != lastViewHeight) {
                refreshVirtualDisplaySize(holder, viewWidth, viewHeight);
            }
        }
        if (appToStart != null && displayId >= 0) {
            pendingApp = null;
            start(appToStart);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // SurfaceView may recreate its Surface during visibility and layout changes. Keep the
        // display/task and reattach in surfaceChanged instead of creating another SF output.
        detachVirtualDisplaySurface();
        releaseStaleWindowAnimationLeash();
    }

    private final class PendingMotionEvent {
        final int displayId;
        final int actionMasked;
        final MotionEvent event;
        final long traceId;
        final int focusRequestGeneration;

        PendingMotionEvent(int displayId, MotionEvent event, long traceId,
                           int focusRequestGeneration) {
            this.displayId = displayId;
            this.event = event;
            actionMasked = event.getActionMasked();
            this.traceId = traceId;
            this.focusRequestGeneration = focusRequestGeneration;
        }

        void recycle() {
            event.recycle();
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        latestTouchX = event.getX();
        latestTouchY = event.getY();
        latestTouchRawX = event.getRawX();
        latestTouchRawY = event.getRawY();
        if (callbacks.onImageDragTouch(slot, event)) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                clearTouchState();
            }
            return true;
        }
        if (displayId < 0 || callbacks.mainSlotSwitchPendingSlot() >= 0
                || callbacks.isWindowFrameAnimationRunning()) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                clearTouchState();
            }
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchDownTime = event.getEventTime();
                touchDownWallTime = toWallTimeMillis(touchDownTime);
                touchMoved = false;
                touchStartedOnMain = callbacks.isMainPaneSlot(slot)
                        && callbacks.activateMainSlot(slot);
                touchReservedForSystemNavigation = false;
                touchSequenceSuppressed = false;
                touchFocusRequestGeneration = 0;
                activeTouchTraceId = touchStartedOnMain ? ++touchTraceSequence : 0L;
                if (touchStartedOnMain) {
                    // Physical edge gestures must stay on display 0. Ordinary content taps keep
                    // the hosted display focused so its editor remains the active IME client.
                    touchReservedForSystemNavigation =
                            isTouchReservedForSystemNavigation(event);
                    if (touchReservedForSystemNavigation) {
                        touchSequenceSuppressed = true;
                        routeDefaultDisplaySystemNavigation(
                                "physical system gesture started");
                    } else {
                        touchFocusRequestGeneration = ++focusRequestGeneration;
                        syncLaunchRoutingSource();
                    }
                    touchTargetDisplayId = displayId;
                    touchTargetDisplayWidth = displayWidth;
                    touchTargetDisplayHeight = displayHeight;
                    touchTargetViewWidth = Math.max(1, surfaceView.getWidth());
                    touchTargetViewHeight = Math.max(1, surfaceView.getHeight());
                    touchTargetDisplayRotation = getTargetDisplayRotation();
                    configureTouchCoordinateTransform();
                } else {
                    touchTargetDisplayId = -1;
                }
                ViewParent parent = surfaceView.getParent();
                if (parent != null) {
                    parent.requestDisallowInterceptTouchEvent(true);
                }
                if (touchStartedOnMain && !touchSequenceSuppressed) {
                    injectMotionDirect(event);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                touchMoved |= movedPastTouchSlop(event.getX(), event.getY());
                if (touchStartedOnMain && !touchSequenceSuppressed) {
                    injectMotionDirect(event);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                if (touchStartedOnMain && !touchSequenceSuppressed) {
                    injectMotionDirect(event);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!touchStartedOnMain) {
                    if (!touchMoved) {
                        view.performClick();
                        if (!isMainDisplaySlot(slot)) {
                            swapWithMain(slot);
                        }
                    }
                    clearTouchState();
                    return true;
                }
                if (touchSequenceSuppressed) {
                    routeDefaultDisplaySystemNavigation(
                            touchReservedForSystemNavigation
                                    ? "physical system gesture completed"
                                    : "host touch dispatch unavailable");
                    clearTouchState();
                    return true;
                }
                injectMotionDirect(event);
                clearTouchState();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (touchStartedOnMain && !touchSequenceSuppressed) {
                    injectMotionDirect(event);
                }
                if (touchReservedForSystemNavigation) {
                    routeDefaultDisplaySystemNavigation(
                            "physical system gesture cancelled");
                }
                clearTouchState();
                return true;
            default:
                return true;
        }
    }

    private void clearTouchState() {
        touchStartedOnMain = false;
        touchReservedForSystemNavigation = false;
        touchSequenceSuppressed = false;
        touchFocusRequestGeneration = 0;
        activeTouchTraceId = 0L;
        touchTargetDisplayId = -1;
        touchTargetDisplayWidth = 0;
        touchTargetDisplayHeight = 0;
        touchTargetViewWidth = 0;
        touchTargetViewHeight = 0;
        touchTargetDisplayRotation = Surface.ROTATION_0;
    }

    boolean launchImageShareActivity(LauncherApp app, Intent shareIntent) {
        if (app == null || shareIntent == null
                || displayId <= DEFAULT_DISPLAY_ID || !hasVirtualDisplay()
                || embeddedSlotClosing[slot]) {
            return false;
        }
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS
                ? windowApps[slot] : null;
        if (currentApp == null || !currentApp.isSameInstance(app)) {
            return false;
        }
        int startEpoch = callbacks.embeddedStartEpoch();
        if (!shouldRunEmbeddedStart(startEpoch)) {
            return false;
        }
        syncLaunchRoutingSource();
        rootVirtualDisplayBridgeClient.allowNextLaunch(
                getRootInputBridgeToken(), app.packageName);
        boolean launched = startWithActivityOptions(
                shareIntent, app, startEpoch, false);
        if (!launched) {
            return false;
        }
        launchRequestedPackage = app.packageName;
        launchRequestedUserId = app.userId();
        launchRequestedDisplayId = displayId;
        armImageShareValidationGuard();
        scheduleHostedTaskResolution("image share " + app.packageName);
        ComponentName initialComponent = shareIntent.getComponent();
        scheduleImageShareReadinessChecks(app,
                initialComponent == null ? null : initialComponent.getClassName());
        return true;
    }

    private void scheduleImageShareReadinessChecks(
            LauncherApp app, String initialActivityName) {
        if (app == null || displayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        int generation = ++imageShareReadinessGeneration;
        int targetDisplayId = displayId;
        for (int delayMs : IMAGE_SHARE_READINESS_DELAYS_MS) {
            mainHandler.postDelayed(() -> checkImageShareReadiness(
                    generation, targetDisplayId, app, initialActivityName), delayMs);
        }
    }

    private void checkImageShareReadiness(
            int generation, int targetDisplayId, LauncherApp expectedApp,
            String initialActivityName) {
        if (generation != imageShareReadinessGeneration
                || targetDisplayId != displayId || embeddedSlotClosing[slot]
                || slot < 0 || slot >= MAX_WINDOWS
                || windowApps[slot] == null
                || !windowApps[slot].isSameInstance(expectedApp)) {
            return;
        }
        try {
            rootExecutor.execute(() -> {
                ShellCommandResult stackList = runPrivilegedCommand(
                        TASK_STACK_LIST_COMMAND,
                        "check image share activity " + expectedApp.packageName, false);
                if (generation != imageShareReadinessGeneration
                        || targetDisplayId != displayId
                        || stackList.exitCode != 0
                        || TextUtils.isEmpty(stackList.output)) {
                    return;
                }
                String topActivity = HostedTaskParser.findVisibleTopActivity(
                        stackList.output, targetDisplayId, expectedApp.packageName);
                if (TextUtils.isEmpty(topActivity)
                        || !ImageShareTargetPolicy.isShareUiReady(
                        expectedApp.packageName, topActivity, initialActivityName)) {
                    return;
                }
                mainHandler.post(() -> {
                    if (generation != imageShareReadinessGeneration
                            || targetDisplayId != displayId
                            || embeddedSlotClosing[slot]
                            || windowApps[slot] == null
                            || !windowApps[slot].isSameInstance(expectedApp)) {
                        return;
                    }
                    imageShareReadinessGeneration++;
                    imageShareValidationGuardUntilUptimeMs = 0L;
                    Log.i(TAG, "Image share activity ready: slot=" + slot
                            + ", display=" + targetDisplayId
                            + ", component=" + topActivity
                            + ", initial=" + initialActivityName);
                    callbacks.onSystemTaskEvent(
                            RootVirtualDisplayBridge.TASK_EVENT_MOVED_TO_FRONT,
                            targetDisplayId, hostedTaskId,
                            expectedApp.packageName, topActivity);
                });
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue image share readiness check failed for slot " + slot);
        }
    }

    private void armImageShareValidationGuard() {
        imageShareValidationGuardUntilUptimeMs =
                SystemClock.uptimeMillis() + IMAGE_SHARE_VALIDATION_GUARD_MS;
    }

    private boolean isImageShareValidationGuardActive(LauncherApp app) {
        return matchesLaunchRequest(app)
                && SystemClock.uptimeMillis() < imageShareValidationGuardUntilUptimeMs;
    }

    void cancelInjectedTouchForImageDrag() {
        if (!touchStartedOnMain || touchSequenceSuppressed
                || touchTargetDisplayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        MotionEvent cancel = MotionEvent.obtain(
                touchDownTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL,
                latestTouchX, latestTouchY, 0);
        try {
            injectMotionDirect(cancel);
            touchSequenceSuppressed = true;
        } finally {
            cancel.recycle();
        }
    }

    boolean hasActiveTouchForImageDrag() {
        return touchStartedOnMain && !touchReservedForSystemNavigation
                && touchTargetDisplayId == displayId;
    }

    float getLatestTouchRawX() {
        return latestTouchRawX;
    }

    float getLatestTouchRawY() {
        return latestTouchRawY;
    }

    private boolean movedPastTouchSlop(float x, float y) {
        float dx = x - touchDownX;
        float dy = y - touchDownY;
        float touchSlop = dp(8);
        return dx * dx + dy * dy > touchSlop * touchSlop;
    }

    private boolean isTouchReservedForSystemNavigation(MotionEvent event) {
        WindowInsets windowInsets = surfaceView.getRootWindowInsets();
        View rootView = surfaceView.getRootView();
        if (windowInsets == null || rootView == null
                || rootView.getWidth() <= 0 || rootView.getHeight() <= 0) {
            return false;
        }
        Insets gestureInsets = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? windowInsets.getInsets(WindowInsets.Type.systemGestures())
                : windowInsets.getSystemGestureInsets();
        rootView.getLocationOnScreen(rootViewLocationOnScreen);
        int windowLeft = rootViewLocationOnScreen[0];
        int windowTop = rootViewLocationOnScreen[1];
        return HostedTouchFocusPolicy.shouldReserveForSystemNavigation(
                event.getRawX(), event.getRawY(),
                windowLeft, windowTop,
                windowLeft + rootView.getWidth(), windowTop + rootView.getHeight(),
                gestureInsets.left, gestureInsets.top,
                gestureInsets.right, gestureInsets.bottom);
    }

    private void injectMotionDirect(MotionEvent event) {
        if (!touchStartedOnMain || touchTargetDisplayId <= DEFAULT_DISPLAY_ID) {
            return;
        }
        final int targetDisplayId = touchTargetDisplayId;
        final int actionMasked = event.getActionMasked();
        final long downTime = event.getDownTime();
        final long eventTime = event.getEventTime();
        MotionEvent transformedEvent = obtainTransformedMotionEvent(event);
        float x = transformedEvent.getX();
        float y = transformedEvent.getY();
        final long traceId = activeTouchTraceId;
        logTouchTraceHost(traceId, targetDisplayId, actionMasked, downTime,
                eventTime, x, y, transformedEvent.getPointerCount());
        boolean sent = sendMotionThroughRootBridge(
                targetDisplayId, transformedEvent, traceId,
                actionMasked == MotionEvent.ACTION_DOWN
                        ? touchFocusRequestGeneration : 0);
        if (!sent) {
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                touchSequenceSuppressed = true;
            }
            logMotionUnavailable(targetDisplayId);
        }
    }

    private MotionEvent obtainTransformedMotionEvent(MotionEvent sourceEvent) {
        MotionEvent transformedEvent = MotionEvent.obtain(sourceEvent);
        transformedEvent.transform(touchCoordinateTransform);
        return transformedEvent;
    }

    private void configureTouchCoordinateTransform() {
        float scaleX = touchTargetDisplayWidth / Math.max(1f, touchTargetViewWidth);
        float scaleY = touchTargetDisplayHeight / Math.max(1f, touchTargetViewHeight);
        if (touchTargetDisplayRotation == Surface.ROTATION_90) {
            touchCoordinateTransform.setValues(new float[] {
                    0f, scaleY, 0f,
                    -scaleX, 0f, Math.max(0f, touchTargetDisplayWidth - 0.001f),
                    0f, 0f, 1f
            });
        } else if (touchTargetDisplayRotation == Surface.ROTATION_270) {
            touchCoordinateTransform.setValues(new float[] {
                    0f, -scaleY, Math.max(0f, touchTargetDisplayHeight - 0.001f),
                    scaleX, 0f, 0f,
                    0f, 0f, 1f
            });
        } else {
            touchCoordinateTransform.setScale(scaleX, scaleY);
        }
    }

    private boolean sendMotionThroughRootBridge(int targetDisplayId,
                                                 MotionEvent transformedEvent,
                                                 long traceId,
                                                 int focusGeneration) {
        PendingMotionEvent motion = new PendingMotionEvent(
                targetDisplayId, transformedEvent, traceId, focusGeneration);
        synchronized (inputDispatchLock) {
            if (inputDispatchClosed) {
                motion.recycle();
                return false;
            }
            PendingMotionEvent last = pendingMotionEvents.peekLast();
            if (motion.actionMasked == MotionEvent.ACTION_MOVE
                    && last != null
                    && last.actionMasked == MotionEvent.ACTION_MOVE
                    && last.traceId == motion.traceId
                    && last.displayId == motion.displayId) {
                pendingMotionEvents.removeLast();
                last.recycle();
            }
            pendingMotionEvents.addLast(motion);
            if (inputDispatchRunning) {
                return true;
            }
            inputDispatchRunning = true;
            try {
                inputDispatchExecutor.execute(this::drainPendingMotionEvents);
                return true;
            } catch (RuntimeException e) {
                inputDispatchRunning = false;
                recyclePendingMotionEventsLocked();
                return false;
            }
        }
    }

    private void drainPendingMotionEvents() {
        while (true) {
            PendingMotionEvent motion;
            synchronized (inputDispatchLock) {
                if (inputDispatchClosed) {
                    recyclePendingMotionEventsLocked();
                    inputDispatchRunning = false;
                    return;
                }
                motion = pendingMotionEvents.pollFirst();
                if (motion == null) {
                    inputDispatchRunning = false;
                    return;
                }
            }
            boolean sent;
            try {
                if (motion.focusRequestGeneration > 0) {
                    // Run focus and DOWN on the same serial queue. A separate focus request can
                    // otherwise overtake asynchronous motion injection and strand the IME on 0.
                    boolean currentRequest = motion.focusRequestGeneration
                            == focusRequestGeneration
                            && motion.displayId == displayId
                            && slot == callbacks.activeMainSlot()
                            && !callbacks.isActivityDestroyed();
                    boolean focused = currentRequest && rootInputBridgeClient.focusHostedDisplay(
                            getRootInputBridgeToken(), motion.displayId);
                    if (currentRequest && !focused) {
                        Log.w(TAG, "Focus hosted display before touch failed: slot=" + slot
                                + ", display=" + motion.displayId);
                    }
                }
                sent = rootInputBridgeClient.sendMotion(
                        getRootInputBridgeToken(), motion.displayId,
                        motion.event, motion.traceId);
            } finally {
                motion.recycle();
            }
            if (!sent) {
                logMotionUnavailable(motion.displayId);
                scheduleRootInputBridgeRecovery();
            }
        }
    }

    private void recyclePendingMotionEventsLocked() {
        PendingMotionEvent pending;
        while ((pending = pendingMotionEvents.pollFirst()) != null) {
            pending.recycle();
        }
    }

    private void shutdownInputDispatch() {
        synchronized (inputDispatchLock) {
            inputDispatchClosed = true;
            recyclePendingMotionEventsLocked();
        }
        inputDispatchExecutor.shutdownNow();
    }

    private void injectKeyDirectAsync(int keyCode, String description) {
        final int targetDisplayId = displayId;
        if (!sendKeyThroughDirectBridge(targetDisplayId, keyCode)) {
            Log.w(TAG, "Direct input bridge unavailable: " + description);
        }
    }

    private void logTouchTraceHost(long traceId, int targetDisplayId, int actionMasked,
                                   long downTime, long eventTime, float x, float y,
                                   int pointerCount) {
        if (actionMasked != MotionEvent.ACTION_DOWN
                && actionMasked != MotionEvent.ACTION_POINTER_DOWN
                && actionMasked != MotionEvent.ACTION_POINTER_UP
                && actionMasked != MotionEvent.ACTION_UP
                && actionMasked != MotionEvent.ACTION_CANCEL) {
            return;
        }
        long eventWallTime = toWallTimeMillis(eventTime);
        long receiveWallTime = System.currentTimeMillis();
        String actionName = motionActionName(actionMasked);
        if (actionMasked == MotionEvent.ACTION_DOWN) {
            Log.i(TAG, "TouchTrace host-received action=" + actionName
                    + " traceId=" + traceId
                    + " slot=" + slot
                    + " display=" + targetDisplayId
                    + " downTimestamp=" + formatTimestamp(eventWallTime)
                    + " receiveTimestamp=" + formatTimestamp(receiveWallTime)
                    + " eventUptimeMs=" + eventTime
                    + " pointers=" + pointerCount
                    + " x=" + formatCoordinate(x)
                    + " y=" + formatCoordinate(y));
            return;
        }
        Log.i(TAG, "TouchTrace host-received action=" + actionName
                + " traceId=" + traceId
                + " slot=" + slot
                + " display=" + targetDisplayId
                + " downTimestamp=" + formatTimestamp(touchDownWallTime)
                + " upTimestamp=" + formatTimestamp(eventWallTime)
                + " receiveTimestamp=" + formatTimestamp(receiveWallTime)
                + " durationMs=" + Math.max(0L, eventTime - downTime)
                + " eventUptimeMs=" + eventTime
                + " pointers=" + pointerCount
                + " x=" + formatCoordinate(x)
                + " y=" + formatCoordinate(y));
    }

    private boolean sendKeyThroughDirectBridge(int targetDisplayId, int keyCode) {
        if (rootInputBridgeClient.sendKey(getRootInputBridgeToken(), targetDisplayId,
                keyCode)) {
            return true;
        }
        scheduleRootInputBridgeRecovery();
        return false;
    }

    void focusHostedDisplayAsync(String reason, Runnable onFinished) {
        final int targetDisplayId = displayId;
        final int requestGeneration = ++focusRequestGeneration;
        syncLaunchRoutingSource();
        try {
            displayImePolicyExecutor.execute(() -> {
                boolean currentRequest = requestGeneration == focusRequestGeneration
                        && targetDisplayId == displayId
                        && slot == callbacks.activeMainSlot()
                        && !callbacks.isActivityDestroyed();
                boolean focused = currentRequest && targetDisplayId > DEFAULT_DISPLAY_ID
                        && rootInputBridgeClient.focusHostedDisplay(
                        getRootInputBridgeToken(), targetDisplayId);
                if (currentRequest && !focused) {
                    Log.w(TAG, "Focus promoted display failed: slot=" + slot
                            + ", display=" + targetDisplayId + ", reason=" + reason);
                }
                if (onFinished != null) {
                    mainHandler.post(onFinished);
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue promoted display focus failed: slot=" + slot
                    + ", display=" + targetDisplayId + ", error="
                    + e.getClass().getSimpleName());
            if (onFinished != null) {
                mainHandler.post(onFinished);
            }
        }
    }

    boolean moveSystemTaskToHostedDisplay(int taskId, String componentName) {
        ComponentName component = ComponentName.unflattenFromString(componentName);
        return taskId > 0 && displayId > DEFAULT_DISPLAY_ID && component != null
                && rootInputBridgeClient.moveTaskToDisplay(
                getRootInputBridgeToken(), taskId, displayId, component);
    }

    void dismissHostedSystemRecents() {
        injectKeyDirectAsync(KeyEvent.KEYCODE_BACK, "dismiss recents display " + displayId);
    }

    boolean routeDefaultDisplaySystemNavigation(String reason) {
        focusRequestGeneration++;
        rootVirtualDisplayBridgeClient.updateLaunchSource(
                getRootInputBridgeToken(), DEFAULT_DISPLAY_ID, "", false);
        Log.i(TAG, "Route default-display system navigation without changing IME focus: "
                + reason);
        return rootAvailable;
    }

    private void logMotionUnavailable(int targetDisplayId) {
        long now = SystemClock.uptimeMillis();
        if (now - lastMotionUnavailableLogUptime
                < ROOT_INPUT_BRIDGE_CONNECT_LOG_THROTTLE_MS) {
            return;
        }
        lastMotionUnavailableLogUptime = now;
        Log.w(TAG, "Direct input unavailable: motion display " + targetDisplayId);
    }

    private void scheduleRootInputBridgeRecovery() {
        if (!rootAvailable || rootInputBridgeRecoveryScheduled) {
            return;
        }
        rootInputBridgeRecoveryScheduled = true;
        try {
            rootExecutor.execute(() -> {
                try {
                    ensureRootInputBridgeStarted(true, true);
                } finally {
                    rootInputBridgeRecoveryScheduled = false;
                }
            });
        } catch (RuntimeException e) {
            rootInputBridgeRecoveryScheduled = false;
        }
    }

    private int getTargetDisplayRotation() {
        try {
            Display currentDisplay = getHostedDisplay();
            if (currentDisplay != null) {
                return currentDisplay.getRotation();
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Read virtual display rotation failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
        }
        return Surface.ROTATION_0;
    }

    void onPhysicalLandscapeRotationChanged(int targetRotation) {
        if (!DeviceOrientationMapper.isLandscapeRotation(targetRotation)
                || displayId <= DEFAULT_DISPLAY_ID
                || callbacks.isWindowFrameAnimationRunning()
                || callbacks.mainSlotSwitchPendingSlot() >= 0) {
            return;
        }
        int currentRotation = getTargetDisplayRotation();
        if (!DeviceOrientationMapper.isLandscapeRotation(currentRotation)) {
            return;
        }
        requestSensorLandscapeRotationAsync(targetRotation,
                "physical device landscape changed");
    }

    void onHostedDisplayRotationChanged() {
        int currentRotation = getTargetDisplayRotation();
        if (DeviceOrientationMapper.isLandscapeRotation(currentRotation)) {
            int latestRotation = getLatestPhysicalLandscapeRotation();
            if (latestRotation >= 0
                    && !callbacks.isWindowFrameAnimationRunning()
                    && callbacks.mainSlotSwitchPendingSlot() < 0) {
                requestSensorLandscapeRotationAsync(latestRotation,
                        "hosted app entered landscape fullscreen");
            }
            return;
        }
        stopSensorLandscapeRotationAsync("hosted app left landscape fullscreen");
    }

    void onWindowSwitchSettledForSensorRotation() {
        int latestRotation = getLatestPhysicalLandscapeRotation();
        if (latestRotation < 0
                || !DeviceOrientationMapper.isLandscapeRotation(
                getTargetDisplayRotation())) {
            return;
        }
        requestSensorLandscapeRotationAsync(latestRotation,
                "window switch settled");
    }

    private void requestSensorLandscapeRotationAsync(int targetRotation, String reason) {
        if (!DeviceOrientationMapper.isLandscapeRotation(targetRotation)
                || displayId <= DEFAULT_DISPLAY_ID
                || !hasVirtualDisplay()) {
            return;
        }
        int currentRotation = getTargetDisplayRotation();
        if (!DeviceOrientationMapper.isLandscapeRotation(currentRotation)) {
            return;
        }
        if (currentRotation == targetRotation) {
            if (requestedSensorLandscapeRotation >= 0
                    && requestedSensorLandscapeRotation != targetRotation) {
                sensorLandscapeRotationGeneration++;
                requestedSensorLandscapeRotation = -1;
            }
            return;
        }
        if (requestedSensorLandscapeRotation == targetRotation) {
            return;
        }

        final int targetDisplayId = displayId;
        final int generation = ++sensorLandscapeRotationGeneration;
        requestedSensorLandscapeRotation = targetRotation;
        try {
            rootExecutor.execute(() -> {
                if (generation != sensorLandscapeRotationGeneration
                        || displayId != targetDisplayId
                        || !DeviceOrientationMapper.isLandscapeRotation(
                        getTargetDisplayRotation())) {
                    clearSensorLandscapeRotationRequest(generation, targetRotation);
                    return;
                }
                boolean applied = applySensorLandscapeDisplayRotation(
                        targetDisplayId, targetRotation,
                        reason + " on display " + targetDisplayId);
                mainHandler.post(() -> {
                    if (generation != sensorLandscapeRotationGeneration
                            || displayId != targetDisplayId) {
                        return;
                    }
                    if (applied) {
                        sensorLandscapeRotationApplied = true;
                        Log.i(TAG, "Forwarded physical landscape rotation: slot=" + slot
                                + ", display=" + targetDisplayId
                                + ", rotation=" + targetRotation
                                + ", reason=" + reason);
                    } else if (requestedSensorLandscapeRotation == targetRotation) {
                        requestedSensorLandscapeRotation = -1;
                    }
                });
            });
        } catch (RuntimeException e) {
            if (generation == sensorLandscapeRotationGeneration) {
                requestedSensorLandscapeRotation = -1;
            }
            Log.w(TAG, "Queue landscape sensor rotation failed: slot=" + slot
                    + ", display=" + targetDisplayId
                    + ", error=" + e.getClass().getSimpleName());
        }
    }

    private void clearSensorLandscapeRotationRequest(int generation, int targetRotation) {
        mainHandler.post(() -> {
            if (generation == sensorLandscapeRotationGeneration
                    && requestedSensorLandscapeRotation == targetRotation) {
                requestedSensorLandscapeRotation = -1;
            }
        });
    }

    void stopSensorLandscapeRotationAsync(String reason) {
        boolean rotationMayBeLocked = sensorLandscapeRotationApplied
                || requestedSensorLandscapeRotation >= 0;
        sensorLandscapeRotationGeneration++;
        requestedSensorLandscapeRotation = -1;
        sensorLandscapeRotationApplied = false;
        final int targetDisplayId = displayId;
        if (!rotationMayBeLocked || targetDisplayId <= DEFAULT_DISPLAY_ID
                || !hasVirtualDisplay()) {
            return;
        }
        try {
            rootExecutor.execute(() -> {
                if (displayId != targetDisplayId || !hasVirtualDisplay()) {
                    return;
                }
                if (!applyContentDrivenDisplayRotation(targetDisplayId,
                        reason + " on display " + targetDisplayId)) {
                    Log.w(TAG, "Restore content-driven rotation failed: slot=" + slot
                            + ", display=" + targetDisplayId + ", reason=" + reason);
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue content-driven rotation restore failed: slot=" + slot
                    + ", display=" + targetDisplayId
                    + ", error=" + e.getClass().getSimpleName());
        }
    }

    private int getDisplayFlagsForDiagnostics(Display display) {
        if (display == null) {
            return -1;
        }
        try {
            Method getFlags = Display.class.getDeclaredMethod("getFlags");
            getFlags.setAccessible(true);
            Object value = getFlags.invoke(display);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Read display flags failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            return -1;
        }
    }

    private boolean hasVirtualDisplay() {
        if (displayId <= DEFAULT_DISPLAY_ID
                || (!rootManagedVirtualDisplay && virtualDisplay == null)
                || displayManager == null) {
            return false;
        }
        try {
            return displayManager.getDisplay(displayId) != null;
        } catch (RuntimeException e) {
            Log.w(TAG, "Check virtual display liveness failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    private Display getHostedDisplay() {
        VirtualDisplay directDisplay = virtualDisplay;
        if (directDisplay != null) {
            try {
                return directDisplay.getDisplay();
            } catch (RuntimeException e) {
                Log.w(TAG, "Read direct virtual display failed for slot " + slot + ": "
                        + e.getClass().getSimpleName());
            }
        }
        return displayId > DEFAULT_DISPLAY_ID ? displayManager.getDisplay(displayId) : null;
    }

    private Display waitForHostedDisplay(int targetDisplayId) {
        if (targetDisplayId <= DEFAULT_DISPLAY_ID) {
            return null;
        }
        long deadline = SystemClock.uptimeMillis() + ROOT_DISPLAY_REGISTRATION_TIMEOUT_MS;
        do {
            Display display = displayManager.getDisplay(targetDisplayId);
            if (display != null) {
                return display;
            }
            long remainingMs = deadline - SystemClock.uptimeMillis();
            if (remainingMs <= 0L) {
                return null;
            }
            SystemClock.sleep(Math.min(16L, remainingMs));
        } while (true);
    }

    private boolean setHostedDisplaySurface(Surface surface) {
        if (rootManagedVirtualDisplay) {
            return rootVirtualDisplayBridgeClient.setSurface(
                    getRootInputBridgeToken(), slot, surface);
        }
        VirtualDisplay directDisplay = virtualDisplay;
        if (directDisplay == null) {
            return false;
        }
        directDisplay.setSurface(surface);
        return true;
    }

    void depriveHostedInputFocus() {
        focusRequestGeneration++;
        if (!HostedInputFocusPolicy.shouldUseDemotedFocusWindow(Build.VERSION.SDK_INT)) {
            destroyHostedInputFocusGuard();
            Log.i(TAG, "Keep hosted task visible without focus guard: slot=" + slot
                    + ", display=" + displayId + ", sdk=" + Build.VERSION.SDK_INT);
            return;
        }
        Presentation existingWindow = demotedFocusWindow;
        if (existingWindow != null && existingWindow.isShowing()) {
            return;
        }
        Display targetDisplay = getHostedDisplay();
        if (targetDisplay == null || targetDisplay.getDisplayId() != displayId
                || displayId <= DEFAULT_DISPLAY_ID) {
            return;
        }

        try {
            if (existingWindow != null) {
                existingWindow.show();
                Window existingPresentationWindow = existingWindow.getWindow();
                if (existingPresentationWindow != null) {
                    existingPresentationWindow.setLayout(1, 1);
                }
                if (demotedFocusContent != null) {
                    demotedFocusContent.requestFocus();
                }
                Log.i(TAG, "Reused hosted input focus guard: slot=" + slot
                        + ", display=" + displayId);
                return;
            }
            Presentation focusWindow = new Presentation(owner, targetDisplay);
            focusWindow.setCancelable(false);
            FrameLayout content = new FrameLayout(focusWindow.getContext());
            content.setBackgroundColor(Color.TRANSPARENT);
            content.setFocusable(true);
            content.setFocusableInTouchMode(true);
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            focusWindow.setContentView(content, new ViewGroup.LayoutParams(1, 1));

            Window window = focusWindow.getWindow();
            if (window == null) {
                throw new IllegalStateException("demoted focus window unavailable");
            }
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = 1;
            attributes.height = 1;
            attributes.gravity = Gravity.TOP | Gravity.START;
            attributes.dimAmount = 0f;
            attributes.windowAnimations = 0;
            window.setAttributes(attributes);

            demotedFocusWindow = focusWindow;
            demotedFocusContent = content;
            focusWindow.show();
            window.setLayout(1, 1);
            content.requestFocus();
            Log.i(TAG, "Deprived hosted input focus: slot=" + slot
                    + ", display=" + displayId);
        } catch (RuntimeException e) {
            destroyHostedInputFocusGuard();
            Log.w(TAG, "Deprive hosted input focus failed: slot=" + slot
                    + ", display=" + displayId + ", error="
                    + e.getClass().getSimpleName());
        }
    }

    void restoreHostedInputFocus() {
        Presentation focusWindow = demotedFocusWindow;
        if (focusWindow == null) {
            return;
        }
        try {
            if (focusWindow.isShowing()) {
                focusWindow.hide();
            }
            Log.i(TAG, "Restored hosted input focus: slot=" + slot
                    + ", display=" + displayId);
        } catch (RuntimeException e) {
            Log.w(TAG, "Restore hosted input focus failed: slot=" + slot
                    + ", error=" + e.getClass().getSimpleName());
        }
    }

    private void destroyHostedInputFocusGuard() {
        Presentation focusWindow = demotedFocusWindow;
        demotedFocusWindow = null;
        demotedFocusContent = null;
        if (focusWindow == null) {
            return;
        }
        try {
            focusWindow.dismiss();
        } catch (RuntimeException e) {
            Log.w(TAG, "Destroy hosted input focus guard failed: slot=" + slot
                    + ", error=" + e.getClass().getSimpleName());
        }
    }

    private void ensureDisplayImeLocalPolicyAsync(String reason) {
        final int targetDisplayId = displayId;
        if (targetDisplayId <= DEFAULT_DISPLAY_ID || !hasVirtualDisplay()
                || imePolicyConfiguredDisplayId == targetDisplayId
                || imePolicySetupDisplayId == targetDisplayId) {
            return;
        }
        final int generation = ++imePolicySetupGeneration;
        imePolicySetupDisplayId = targetDisplayId;
        checkDisplayImeLocalPolicy(reason, () -> {
            if (generation == imePolicySetupGeneration && targetDisplayId == displayId) {
                imePolicySetupDisplayId = -1;
            }
        }, () -> {
            if (generation == imePolicySetupGeneration && targetDisplayId == displayId) {
                imePolicySetupDisplayId = -1;
                Log.w(TAG, "Virtual display IME policy setup failed without blocking launch: "
                        + "slot=" + slot + ", display=" + targetDisplayId
                        + ", reason=" + reason);
            }
        });
    }

    void checkDisplayImeLocalPolicy(String reason, Runnable onConfirmed,
                                            Runnable onRejected) {
        final int targetDisplayId = displayId;
        if (targetDisplayId <= DEFAULT_DISPLAY_ID || !hasVirtualDisplay()) {
            mainHandler.post(onRejected);
            return;
        }
        if (imePolicyConfiguredDisplayId == targetDisplayId) {
            mainHandler.post(onConfirmed);
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Before Android 10 there is no per-display IME policy. The single system IME is
            // hosted by the default display while its InputConnection can target the focused
            // app on the virtual display.
            imePolicyConfiguredDisplayId = targetDisplayId;
            Log.i(TAG, "Virtual display IME policy: slot=" + slot
                    + ", display=" + targetDisplayId
                    + ", policy=PLATFORM_DEFAULT"
                    + ", imeDisplay=" + DEFAULT_DISPLAY_ID
                    + ", configured=true"
                    + ", reason=" + reason
                    + ", backend=pre-Q");
            mainHandler.post(onConfirmed);
            return;
        }
        try {
            displayImePolicyExecutor.execute(() -> {
                boolean configured = applyDisplayImeLocalPolicy(targetDisplayId, reason);
                mainHandler.post(() -> {
                    if (targetDisplayId != displayId || !hasVirtualDisplay()) {
                        onRejected.run();
                    } else if (configured) {
                        onConfirmed.run();
                    } else {
                        onRejected.run();
                    }
                });
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue virtual display IME policy failed: slot=" + slot
                    + ", display=" + targetDisplayId + ", error="
                    + e.getClass().getSimpleName());
            mainHandler.post(onRejected);
        }
    }

    private boolean applyDisplayImeLocalPolicy(int targetDisplayId, String reason) {
        if (targetDisplayId != displayId || !hasVirtualDisplay()) {
            return false;
        }
        Integer beforePolicy = rootAvailable
                ? null : getDisplayImePolicyThroughWindowManager(targetDisplayId);

        boolean windowManagerRequested = false;
        if (!rootAvailable) {
            windowManagerRequested = setDisplayImePolicyThroughWindowManager(targetDisplayId,
                    DISPLAY_IME_POLICY_LOCAL_HIDDEN);
        }
        Integer actualPolicy;
        boolean rootBridgeRequested = false;
        if (rootAvailable) {
            rootBridgeRequested = true;
            actualPolicy = rootInputBridgeClient.setDisplayImePolicy(
                    getRootInputBridgeToken(), targetDisplayId,
                    DISPLAY_IME_POLICY_LOCAL_HIDDEN);
            if (actualPolicy == null && targetDisplayId == displayId
                    && hasVirtualDisplay()) {
                // A prewarm may have been in flight when the first request failed. Retry the
                // shell bridge once before treating this as a capability failure.
                ensureRootInputBridgeStarted(false, true);
                actualPolicy = rootInputBridgeClient.setDisplayImePolicy(
                        getRootInputBridgeToken(), targetDisplayId,
                        DISPLAY_IME_POLICY_LOCAL_HIDDEN);
                if (actualPolicy == null && targetDisplayId == displayId
                        && hasVirtualDisplay()) {
                    ensureRootInputBridgeStarted(true, true);
                    actualPolicy = rootInputBridgeClient.setDisplayImePolicy(
                            getRootInputBridgeToken(), targetDisplayId,
                            DISPLAY_IME_POLICY_LOCAL_HIDDEN);
                }
            }
        } else {
            actualPolicy = getDisplayImePolicyThroughWindowManager(targetDisplayId);
        }
        boolean configured = actualPolicy != null
                ? actualPolicy == DISPLAY_IME_POLICY_LOCAL_HIDDEN
                : !rootAvailable && windowManagerRequested;
        if (configured && targetDisplayId == displayId && hasVirtualDisplay()) {
            imePolicyConfiguredDisplayId = targetDisplayId;
        }
        String rootVerification = !rootAvailable ? "unavailable"
                : actualPolicy == null ? "deferred"
                : configured ? "confirmed" : "failed";
        String policyBackend = rootAvailable && actualPolicy != null
                ? "WindowManager(root-bridge)"
                : rootAvailable ? "WindowManager(root-bridge)" : "WindowManager";
        int logPriority = configured ? Log.INFO : Log.WARN;
        Log.println(logPriority, TAG, "Virtual display IME policy: slot=" + slot
                + ", display=" + targetDisplayId
                + ", policy=LOCAL"
                + ", imeDisplay=" + targetDisplayId
                + ", before=" + formatDisplayImePolicy(beforePolicy)
                + ", actual=" + formatDisplayImePolicy(actualPolicy)
                + ", rootVerification=" + rootVerification
                + ", windowManagerRequested=" + windowManagerRequested
                + ", rootBridgeRequested=" + rootBridgeRequested
                + ", configured=" + configured
                + ", reason=" + reason
                + ", backend=" + policyBackend);
        return configured;
    }

    private String formatDisplayImePolicy(Integer policy) {
        if (policy == null) {
            return "unknown";
        }
        return policy == DISPLAY_IME_POLICY_LOCAL_HIDDEN
                ? "LOCAL" : String.valueOf(policy);
    }

    @SuppressLint("BlockedPrivateApi")
    private Integer getDisplayImePolicyThroughWindowManager(int targetDisplayId) {
        Object windowManager = owner.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return null;
        }
        try {
            Method method = WindowManager.class.getDeclaredMethod(
                    "getDisplayImePolicy", int.class);
            method.setAccessible(true);
            Object value = method.invoke(windowManager, targetDisplayId);
            return value instanceof Integer ? (Integer) value : null;
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Read virtual display IME policy failed: slot=" + slot
                    + ", display=" + targetDisplayId + ", error="
                    + e.getClass().getSimpleName());
            return null;
        }
    }

    @SuppressLint("BlockedPrivateApi")
    private boolean setDisplayImePolicyThroughWindowManager(int targetDisplayId,
                                                              int policy) {
        Object windowManager = owner.getSystemService(Context.WINDOW_SERVICE);
        if (windowManager == null) {
            return false;
        }
        try {
            Method method = WindowManager.class.getDeclaredMethod(
                    "setDisplayImePolicy", int.class, int.class);
            method.setAccessible(true);
            method.invoke(windowManager, targetDisplayId, policy);
            return true;
        } catch (NoSuchMethodException e) {
            Log.w(TAG, "Display IME policy API missing: slot=" + slot
                    + ", display=" + targetDisplayId
                    + ", sdk=" + Build.VERSION.SDK_INT);
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Set virtual display IME policy failed: slot=" + slot
                    + ", display=" + targetDisplayId
                    + ", policy=" + policy + ", error="
                    + e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean applyContentDrivenDisplayRotation(int targetDisplayId,
                                                      String description) {
        long startedAt = SystemClock.uptimeMillis();
        String bridgeToken = getRootInputBridgeToken();
        boolean applied = rootInputBridgeClient.setDisplayRotationAuto(
                bridgeToken, targetDisplayId);
        if (!applied) {
            ensureRootInputBridgeStarted(false, true);
            applied = rootInputBridgeClient.setDisplayRotationAuto(
                    bridgeToken, targetDisplayId);
        }
        if (applied) {
            Log.i(TAG, "Content-driven rotation Binder ok("
                    + (SystemClock.uptimeMillis() - startedAt) + "ms): " + description);
            return true;
        }
        String command = "wm set-ignore-orientation-request -d " + targetDisplayId
                + " false >/dev/null 2>&1; ignore_status=$?; "
                + "wm fixed-to-user-rotation -d " + targetDisplayId
                + " disabled >/dev/null 2>&1; fixed_status=$?; "
                + "if [ \"$fixed_status\" -ne 0 ]; then "
                + "wm set-fix-to-user-rotation -d " + targetDisplayId
                + " disabled >/dev/null 2>&1; fixed_status=$?; fi; "
                + "wm user-rotation -d " + targetDisplayId
                + " free >/dev/null 2>&1; rotation_status=$?; "
                + "if [ \"$rotation_status\" -ne 0 ]; then "
                + "wm set-user-rotation free -d " + targetDisplayId
                + " >/dev/null 2>&1; rotation_status=$?; fi; "
                + "echo __ONESTEP_ROTATION_AUTO ignore=$ignore_status"
                + " fixed=$fixed_status rotation=$rotation_status";
        ShellCommandResult result = runPrivilegedCommand(
                command, description + " shell fallback", true);
        String output = result.output == null ? "" : result.output;
        return result.exitCode == 0 && output.contains("ignore=0")
                && output.contains("fixed=0") && output.contains("rotation=0");
    }

    private boolean applySensorLandscapeDisplayRotation(int targetDisplayId,
                                                        int targetRotation,
                                                        String description) {
        long startedAt = SystemClock.uptimeMillis();
        String bridgeToken = getRootInputBridgeToken();
        boolean applied = rootInputBridgeClient.setDisplayLandscapeRotation(
                bridgeToken, targetDisplayId, targetRotation);
        if (!applied) {
            ensureRootInputBridgeStarted(false, true);
            applied = rootInputBridgeClient.setDisplayLandscapeRotation(
                    getRootInputBridgeToken(), targetDisplayId, targetRotation);
        }
        if (applied) {
            Log.i(TAG, "Landscape sensor rotation Binder ok("
                    + (SystemClock.uptimeMillis() - startedAt) + "ms): " + description);
            return true;
        }
        String command = "wm set-ignore-orientation-request -d " + targetDisplayId
                + " false >/dev/null 2>&1; ignore_status=$?; "
                + "wm fixed-to-user-rotation -d " + targetDisplayId
                + " disabled >/dev/null 2>&1; fixed_status=$?; "
                + "if [ \"$fixed_status\" -ne 0 ]; then "
                + "wm set-fix-to-user-rotation -d " + targetDisplayId
                + " disabled >/dev/null 2>&1; fixed_status=$?; fi; "
                + "wm user-rotation -d " + targetDisplayId
                + " lock " + targetRotation
                + " >/dev/null 2>&1; rotation_status=$?; "
                + "if [ \"$rotation_status\" -ne 0 ]; then "
                + "wm set-user-rotation lock " + targetRotation
                + " -d " + targetDisplayId
                + " >/dev/null 2>&1; rotation_status=$?; fi; "
                + "echo __ONESTEP_LANDSCAPE_ROTATION ignore=$ignore_status"
                + " fixed=$fixed_status rotation=$rotation_status";
        ShellCommandResult result = runPrivilegedCommand(
                command, description + " shell fallback", true);
        String output = result.output == null ? "" : result.output;
        return result.exitCode == 0 && output.contains("ignore=0")
                && output.contains("fixed=0") && output.contains("rotation=0");
    }

    private void configureNativeDisplayOrientationAsync(int targetDisplayId, String reason) {
        if (!rootAvailable || targetDisplayId <= DEFAULT_DISPLAY_ID) {
            Log.w(TAG, "Content-driven display rotation unavailable: display="
                    + targetDisplayId + ", root=" + rootAvailable);
            return;
        }
        try {
            rootExecutor.execute(() -> {
                if (displayId != targetDisplayId) {
                    return;
                }
                if (!applyContentDrivenDisplayRotation(targetDisplayId,
                        reason + " on display " + targetDisplayId)) {
                    Log.w(TAG, "Content-driven rotation unavailable for display "
                            + targetDisplayId);
                }
            });
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue content-driven display rotation failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private void scheduleHostedTaskResolution(String reason) {
        if (bridgePrewarmOnly || !canResolveHostedTask() || hostedTaskId > 0) {
            return;
        }
        int token = ++taskResolutionToken;
        for (int delayMs : HOSTED_TASK_RESOLUTION_DELAYS_MS) {
            mainHandler.postDelayed(() -> {
                if (token == taskResolutionToken && hostedTaskId <= 0) {
                    resolveHostedTask(reason, token);
                }
            }, delayMs);
        }
    }

    private boolean canResolveHostedTask() {
        return displayId > DEFAULT_DISPLAY_ID
                && slot >= 0
                && slot < MAX_WINDOWS
                && windowApps[slot] != null
                && callbacks.isWindowSlotEnabled(slot)
                && isAvailable()
                && !callbacks.suppressEmbeddedStarts();
    }

    private void resolveHostedTask(String reason, int token) {
        final int targetDisplayId = displayId;
        final LauncherApp targetApp = slot >= 0 && slot < MAX_WINDOWS
                ? windowApps[slot] : null;
        if (!canResolveHostedTask() || hostedTaskId > 0 || taskResolutionInFlight) {
            return;
        }
        taskResolutionInFlight = true;
        try {
            rootExecutor.execute(() -> {
                try {
                    if (token != taskResolutionToken || !canResolveHostedTask()) {
                        return;
                    }
                    ShellCommandResult stackList = runPrivilegedCommand(
                            TASK_STACK_LIST_COMMAND, "resolve task after " + reason, false);
                    if (token != taskResolutionToken || targetDisplayId != displayId) {
                        return;
                    }
                    if (stackList.exitCode != 0 || TextUtils.isEmpty(stackList.output)) {
                        Log.w(TAG, "Hosted task scan empty after " + reason);
                        return;
                    }
                    int resolvedTaskId = findVisibleHostedTaskId(
                            stackList.output, targetDisplayId, targetApp);
                    if (resolvedTaskId > 0) {
                        hostedTaskId = resolvedTaskId;
                        Log.i(TAG, "Resolved hosted task: slot=" + slot
                                + ", display=" + targetDisplayId
                                + ", taskId=" + resolvedTaskId);
                        mainHandler.post(() -> scheduleHostedSurfaceReveal(
                                targetApp, targetDisplayId));
                    }
                } finally {
                    taskResolutionInFlight = false;
                }
            });
        } catch (RuntimeException e) {
            taskResolutionInFlight = false;
        }
    }

    private int findHostedTaskId(String stackList, int targetDisplayId, LauncherApp app) {
        if (app == null) {
            return -1;
        }
        if (app.isHomeEntry()) {
            return HostedTaskParser.findHostedTaskIdForComponent(
                    stackList, targetDisplayId, app.packageName,
                    app.componentName.getClassName());
        }
        return HostedTaskParser.findHostedTaskId(
                stackList, targetDisplayId, app.packageName);
    }

    private int findVisibleHostedTaskId(
            String stackList, int targetDisplayId, LauncherApp app) {
        if (app == null) {
            return -1;
        }
        if (app.isHomeEntry()) {
            return HostedTaskParser.findVisibleHostedTaskIdForComponent(
                    stackList, targetDisplayId, app.packageName,
                    app.componentName.getClassName());
        }
        return HostedTaskParser.findVisibleHostedTaskId(
                stackList, targetDisplayId, app.packageName);
    }

    private void beginHostedSurfaceReveal(LauncherApp app) {
        if (hostedSurfaceRevealCallbackInstanceKey != null
                && !app.instanceKey().equals(hostedSurfaceRevealCallbackInstanceKey)) {
            hostedSurfaceRevealedCallback = null;
            hostedSurfaceRevealCallbackInstanceKey = null;
        }
        hostedSurfaceRevealPending = true;
        hostedSurfaceRevealInstanceKey = app.instanceKey();
        hostedSurfaceRevealDisplayId = displayId;
        hostedSurfaceRevealGeneration++;
        applyHostedSurfaceAlpha(0f, false);
        windowViews[slot].setLiveAppVisible(false);
    }

    private void scheduleHostedSurfaceReveal(LauncherApp app, int targetDisplayId) {
        final int generation = hostedSurfaceRevealGeneration;
        mainHandler.postDelayed(() -> {
            if (generation == hostedSurfaceRevealGeneration) {
                revealHostedSurfaceIfCurrent(app, targetDisplayId);
            }
        }, HOSTED_SURFACE_REVEAL_AFTER_VISIBLE_MS);
    }

    private void revealHostedSurfaceIfCurrent(LauncherApp app, int targetDisplayId) {
        if (app == null || !isHostedSurfaceRevealPending(app)
                || targetDisplayId != displayId || hostedTaskId <= 0
                || embeddedSlotClosing[slot]) {
            return;
        }
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        if (currentApp == null || !app.isSameInstance(currentApp)) {
            return;
        }
        Runnable onRevealed = app.instanceKey().equals(hostedSurfaceRevealCallbackInstanceKey)
                ? hostedSurfaceRevealedCallback : null;
        hostedSurfaceRevealPending = false;
        hostedSurfaceRevealInstanceKey = null;
        hostedSurfaceRevealDisplayId = -1;
        hostedSurfaceRevealedCallback = null;
        hostedSurfaceRevealCallbackInstanceKey = null;
        applyHostedSurfaceAlpha(1f, false);
        windowViews[slot].setLiveAppVisible(true);
        if (onRevealed != null) {
            onRevealed.run();
        }
    }

    private void cancelHostedSurfaceReveal() {
        hostedSurfaceRevealGeneration++;
        hostedSurfaceRevealPending = false;
        hostedSurfaceRevealInstanceKey = null;
        hostedSurfaceRevealDisplayId = -1;
        hostedSurfaceRevealedCallback = null;
        hostedSurfaceRevealCallbackInstanceKey = null;
    }

    private void createVirtualDisplay(SurfaceHolder holder, int viewWidth, int viewHeight) {
        if (virtualDisplayCreationInProgress) {
            Log.w(TAG, "Skip concurrent virtual display creation for slot " + slot);
            return;
        }
        if (hasVirtualDisplay() && displayId > DEFAULT_DISPLAY_ID) {
            Log.w(TAG, "Keep existing virtual display instead of recreating slot " + slot
                    + ": display=" + displayId);
            attachVirtualDisplaySurface(holder);
            return;
        }
        virtualDisplayCreationInProgress = true;
        try {
            createVirtualDisplayInternal(holder, viewWidth, viewHeight);
        } finally {
            virtualDisplayCreationInProgress = false;
        }
    }

    private void createVirtualDisplayInternal(SurfaceHolder holder, int viewWidth,
                                              int viewHeight) {
        VirtualDisplaySpec spec = makeVirtualDisplaySpec();
        if (!ensureVirtualDisplaySurfaceReady(holder, spec)) {
            return;
        }
        ensureTrustedDisplayRole();
        Log.i(TAG, "Virtual display permissions: trusted="
                + (owner.checkSelfPermission(ADD_TRUSTED_DISPLAY_PERMISSION)
                == PackageManager.PERMISSION_GRANTED));
        displayReleaseGeneration++;
        if (!releaseVirtualDisplay()) {
            unavailableReason = "旧虚拟显示释放失败，已阻止重复创建";
            Log.e(TAG, unavailableReason + " for slot " + slot);
            return;
        }
        int createGeneration = ++virtualDisplayCreateGeneration;
        String displayName = "OneStepSlot-" + slot
                + "/P" + android.os.Process.myPid()
                + "/G" + createGeneration;
        int trustedInteractiveFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH_HIDDEN
                | VIRTUAL_DISPLAY_FLAG_TRUSTED_HIDDEN
                | VIRTUAL_DISPLAY_FLAG_OWN_FOCUS_HIDDEN;
        int trustedPrivateFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH_HIDDEN
                | VIRTUAL_DISPLAY_FLAG_TRUSTED_HIDDEN
                | VIRTUAL_DISPLAY_FLAG_OWN_FOCUS_HIDDEN;
        int touchInteractiveFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH_HIDDEN;
        int touchPrivateFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH_HIDDEN;
        int publicOwnContentFlags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
        int[] flagCandidates = {
                trustedInteractiveFlags | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN,
                trustedPrivateFlags | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN,
                touchInteractiveFlags | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN,
                touchPrivateFlags | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN,
                publicOwnContentFlags | VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN,
                trustedInteractiveFlags,
                trustedPrivateFlags,
                touchInteractiveFlags,
                touchPrivateFlags,
                publicOwnContentFlags,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                0,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        };
        int selectedFlags = 0;
        boolean homeSupportRequested = false;
        String homeSupportFailure = "";
        String failures = "";
        if (rootAvailable) {
            if (!startRootInputBridgeIfNeeded(false, true)) {
                unavailableReason = "root 虚拟显示桥未就绪";
                Log.e(TAG, unavailableReason + " for slot " + slot);
                return;
            }
            RootVirtualDisplayBridgeClient.CreateResult result =
                    rootVirtualDisplayBridgeClient.create(
                            getRootInputBridgeToken(), slot, displayName,
                            spec.width, spec.height, spec.densityDpi,
                            holder.getSurface(), flagCandidates);
            if (result.isStaleSystemServiceFailure()) {
                Log.w(TAG, "Restart stale root display bridge before retrying slot " + slot);
                rootVirtualDisplayBridgeClient.close();
                if (startRootInputBridgeIfNeeded(true, true)) {
                    result = rootVirtualDisplayBridgeClient.create(
                            getRootInputBridgeToken(), slot, displayName,
                            spec.width, spec.height, spec.densityDpi,
                            holder.getSurface(), flagCandidates);
                }
            }
            if (result.isSuccess()) {
                rootManagedVirtualDisplay = true;
                displayId = result.displayId;
                selectedFlags = result.selectedFlags;
                homeSupportRequested = result.homeSupportRequested;
                homeSupportFailure = result.homeSupportFailure;
                primaryHomeEnhancementActive =
                        VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                                true, result.homeSupportRequested,
                                result.primaryHomeHookActive);
            } else {
                failures = result.failure;
            }
        } else {
            StringBuilder directFailures = new StringBuilder();
            for (int flags : flagCandidates) {
                VirtualDisplay candidate = null;
                try {
                    VirtualDisplayHomeSupport.CreationResult creation =
                            VirtualDisplayHomeSupport.create(
                                    displayManager, displayName,
                                    spec.width, spec.height, spec.densityDpi,
                                    holder.getSurface(), flags);
                    candidate = creation.display;
                    homeSupportRequested = creation.homeSupportRequested;
                    homeSupportFailure = creation.homeSupportFailure;
                    primaryHomeEnhancementActive =
                            VirtualDisplayHomeSupport.shouldUseEnhancedHomeLaunch(
                                    true, creation.homeSupportRequested,
                                    creation.primaryHomeHookActive);
                    Display candidateDisplay = candidate == null
                            ? null : candidate.getDisplay();
                    if (candidateDisplay != null) {
                        virtualDisplay = candidate;
                        displayId = candidateDisplay.getDisplayId();
                        selectedFlags = flags;
                        Log.i(TAG, "Create virtual display ok for slot " + slot
                                + " with flags=" + flags);
                        break;
                    }
                } catch (RuntimeException e) {
                    if (directFailures.length() > 0) {
                        directFailures.append("; ");
                    }
                    directFailures.append("flags=").append(flags).append(":")
                            .append(e.getClass().getSimpleName());
                } finally {
                    if (candidate != null && candidate != virtualDisplay) {
                        discardVirtualDisplayCandidate(candidate, flags);
                    }
                }
            }
            failures = directFailures.toString();
        }
        Display hostedDisplay = waitForHostedDisplay(displayId);
        if (!hasVirtualDisplay() || hostedDisplay == null) {
            if (rootManagedVirtualDisplay) {
                rootVirtualDisplayBridgeClient.release(getRootInputBridgeToken(), slot);
                rootManagedVirtualDisplay = false;
            }
            displayId = -1;
            unavailableReason = "创建虚拟显示失败 " + failures;
            Log.w(TAG, unavailableReason + " for slot " + slot);
            return;
        }
        lastViewWidth = viewWidth;
        lastViewHeight = viewHeight;
        displayWidth = spec.width;
        displayHeight = spec.height;
        displayDensityDpi = spec.densityDpi;
        displayUsesDualMainLayout = callbacks.isDualMainLayout();
        displayId = hostedDisplay.getDisplayId();
        surfaceDetached = false;
        unavailableReason = "";
        VirtualDisplaySystemDecorController.Result decorResult =
                VirtualDisplaySystemDecorController.disable(owner, displayId);
        int priority = decorResult.isConfirmedDisabled() ? Log.INFO : Log.WARN;
        Log.println(priority, TAG, "Virtual display system decorations: slot=" + slot
                + ", display=" + displayId
                + ", requested=" + decorResult.requested
                + ", actual=" + decorResult.actualValue()
                + (decorResult.failure.isEmpty()
                ? "" : ", failure=" + decorResult.failure)
                + ", backend=" + (rootManagedVirtualDisplay
                ? "app-fallback" : "app"));
        int actualDisplayFlags = getDisplayFlagsForDiagnostics(hostedDisplay);
        if (!RootVirtualDisplayFlags.hasRequiredTrustedDisplay(
                Build.VERSION.SDK_INT, rootManagedVirtualDisplay, actualDisplayFlags)) {
            unavailableReason = "root 虚拟显示未获得 trusted 标志";
            Log.e(TAG, unavailableReason + ": display=" + displayId
                    + ", flags=0x" + Integer.toHexString(actualDisplayFlags));
            releaseVirtualDisplay();
            return;
        }
        if ((selectedFlags & VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN) == 0
                || (actualDisplayFlags >= 0
                && (actualDisplayFlags & DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN) == 0)) {
            Log.w(TAG, "Virtual display cannot follow app orientation for slot " + slot
                    + ": requestedRotateFlag="
                    + ((selectedFlags & VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN)
                    != 0)
                    + ", actualDisplayFlags=0x" + Integer.toHexString(actualDisplayFlags));
        }
        Log.i(TAG, "Root virtual display ready for slot " + slot + ": displayId=" + displayId
                + ", view=" + viewWidth + "x" + viewHeight
                + ", virtual=" + spec.width + "x" + spec.height
                + ", densityDpi=" + spec.densityDpi
                + ", displayFlags=0x" + Integer.toHexString(actualDisplayFlags)
                + ", rotatesWithContent="
                + ((selectedFlags & VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN) != 0)
                + ", displayRotatesWithContent="
                + (actualDisplayFlags < 0 ? "unknown" : String.valueOf(
                (actualDisplayFlags & DISPLAY_FLAG_ROTATES_WITH_CONTENT_HIDDEN) != 0))
                + ", homeSupport=" + homeSupportRequested
                + (homeSupportFailure.isEmpty()
                ? "" : ", homeSupportFallback=" + homeSupportFailure));
        configureNativeDisplayOrientationAsync(displayId, "initialize virtual display");
        ensureRootInputBridgeStarted();
        if (callbacks.isMainPaneSlot(slot)) {
            ensureDisplayImeLocalPolicyAsync("initialize virtual display");
        }
    }

    private void ensureTrustedDisplayRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || !rootAvailable
                || owner.checkSelfPermission(ADD_TRUSTED_DISPLAY_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        synchronized (owner) {
            if (!callbacks.claimTrustedDisplayRoleSetup()) {
                return;
            }
        }

        String command = "cmd role add-role-holder --user 0 "
                + shellQuote(VIRTUAL_DISPLAY_ROLE) + " "
                + shellQuote(owner.getPackageName()) + " 0";
        ShellCommandResult result = runPrivilegedCommand(command,
                "grant trusted virtual display role", false);
        boolean granted = owner.checkSelfPermission(ADD_TRUSTED_DISPLAY_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
        Log.i(TAG, "Trusted virtual display role setup: granted=" + granted
                + ", exitCode=" + result.exitCode);
    }

    private void refreshVirtualDisplaySize(SurfaceHolder holder,
                                           int viewWidth, int viewHeight) {
        if (!hasVirtualDisplay() || displayId < 0) {
            createVirtualDisplay(holder, viewWidth, viewHeight);
            return;
        }
        boolean targetDualMainLayout = callbacks.isDualMainLayout();
        boolean leavingDualMainLayout = displayUsesDualMainLayout
                && !targetDualMainLayout;
        if (!VirtualDisplayViewportPolicy.shouldResizeForContainerLayout(
                callbacks.isLargeScreenDevice(), targetDualMainLayout, leavingDualMainLayout)) {
            keepVirtualDisplaySurfaceSize(holder, viewWidth, viewHeight);
            return;
        }
        Rect layoutReferenceRect = getReferenceRenderRect();
        if (!hasMatchingAspectRatio(viewWidth, viewHeight,
                layoutReferenceRect.width(), layoutReferenceRect.height())) {
            keepVirtualDisplaySurfaceSize(holder, viewWidth, viewHeight);
            return;
        }
        VirtualDisplaySpec targetSpec = leavingDualMainLayout
                && !callbacks.isMultiWindowMode()
                ? makeWorkspaceVirtualDisplaySpec() : makeTargetVirtualDisplaySpec();
        boolean targetAspectMatches = hasMatchingAspectRatio(
                displayWidth, displayHeight, targetSpec.width, targetSpec.height);
        boolean targetDensityMatches = displayDensityDpi == targetSpec.densityDpi;
        if ((!targetAspectMatches || !targetDensityMatches)
                && resizeVirtualDisplay(targetSpec)) {
            displayUsesDualMainLayout = targetDualMainLayout;
            holder.setFixedSize(displayWidth, displayHeight);
            lastViewWidth = viewWidth;
            lastViewHeight = viewHeight;
            return;
        }
        if (targetAspectMatches && targetDensityMatches) {
            displayUsesDualMainLayout = targetDualMainLayout;
        }
        keepVirtualDisplaySurfaceSize(holder, viewWidth, viewHeight);
    }

    private void keepVirtualDisplaySurfaceSize(SurfaceHolder holder,
                                               int viewWidth, int viewHeight) {
        Rect surfaceFrame = holder.getSurfaceFrame();
        if (surfaceFrame == null
                || surfaceFrame.width() != displayWidth
                || surfaceFrame.height() != displayHeight) {
            holder.setFixedSize(displayWidth, displayHeight);
        }
        lastViewWidth = viewWidth;
        lastViewHeight = viewHeight;
    }

    private VirtualDisplaySpec makeVirtualDisplaySpec() {
        if (displayWidth > 0 && displayHeight > 0 && displayDensityDpi > 0) {
            return new VirtualDisplaySpec(displayWidth, displayHeight, displayDensityDpi);
        }
        return makeTargetVirtualDisplaySpec();
    }

    private VirtualDisplaySpec makeTargetVirtualDisplaySpec() {
        return makeVirtualDisplaySpecForRect(getReferenceRenderRect());
    }

    private VirtualDisplaySpec makeWorkspaceVirtualDisplaySpec() {
        View workspace = callbacks.workspace();
        if (workspace == null || workspace.getWidth() <= 0 || workspace.getHeight() <= 0) {
            return makeTargetVirtualDisplaySpec();
        }
        return makeVirtualDisplaySpecForRect(
                new Rect(0, 0, workspace.getWidth(), workspace.getHeight()));
    }

    private VirtualDisplaySpec makeVirtualDisplaySpecForRect(Rect referenceRect) {
        // Every slot represents the same logical screen. Deriving the mode from each slot's
        // rounded view size made main/side swaps oscillate by a few pixels and relaunch apps.
        int referenceWidth = Math.max(1, referenceRect.width());
        int referenceHeight = Math.max(1, referenceRect.height());
        int virtualWidth = referenceWidth;
        int virtualHeight = referenceHeight;
        float qualityScale = calculateMinimumQualityScale(virtualWidth, virtualHeight);
        if (qualityScale > 1f) {
            virtualWidth = Math.max(1, Math.round(virtualWidth * qualityScale));
            virtualHeight = Math.max(1, Math.round(virtualHeight * qualityScale));
        }
        boolean largeScreenDevice = callbacks.isLargeScreenDevice();
        boolean useTabletDensity = largeScreenDevice && !callbacks.isDualMainLayout();
        int densityDpi = VirtualDisplayDensityPolicy.calculateDensityDpi(
                referenceWidth,
                referenceHeight,
                virtualWidth,
                virtualHeight,
                owner.getResources().getDisplayMetrics().densityDpi,
                largeScreenDevice,
                useTabletDensity);
        float tabletPixelScale = VirtualDisplayDensityPolicy.calculateTabletPixelScale(
                virtualWidth, virtualHeight, densityDpi, useTabletDensity);
        if (tabletPixelScale > 1f) {
            virtualWidth = Math.max(1, Math.round(virtualWidth * tabletPixelScale));
            virtualHeight = Math.max(1, Math.round(virtualHeight * tabletPixelScale));
        }
        return new VirtualDisplaySpec(virtualWidth, virtualHeight, densityDpi);
    }

    private boolean resizeVirtualDisplay(VirtualDisplaySpec spec) {
        if (spec == null || !hasVirtualDisplay() || displayId <= DEFAULT_DISPLAY_ID) {
            return false;
        }
        int oldWidth = displayWidth;
        int oldHeight = displayHeight;
        boolean resized;
        if (rootManagedVirtualDisplay) {
            resized = rootVirtualDisplayBridgeClient.resize(
                    getRootInputBridgeToken(), slot,
                    spec.width, spec.height, spec.densityDpi);
        } else {
            try {
                virtualDisplay.resize(spec.width, spec.height, spec.densityDpi);
                resized = true;
            } catch (RuntimeException e) {
                resized = false;
                Log.w(TAG, "Virtual display resize failed for slot " + slot
                        + ": " + e.getClass().getSimpleName());
            }
        }
        if (!resized) {
            Log.w(TAG, "Virtual display kept old aspect after resize rejection: slot=" + slot
                    + ", display=" + displayId
                    + ", current=" + oldWidth + "x" + oldHeight
                    + ", requested=" + spec.width + "x" + spec.height);
            return false;
        }
        displayWidth = spec.width;
        displayHeight = spec.height;
        displayDensityDpi = spec.densityDpi;
        Log.i(TAG, "Virtual display resized to match container aspect: slot=" + slot
                + ", display=" + displayId
                + ", old=" + oldWidth + "x" + oldHeight
                + ", new=" + displayWidth + "x" + displayHeight
                + ", densityDpi=" + displayDensityDpi);
        return true;
    }

    private static boolean hasMatchingAspectRatio(int firstWidth, int firstHeight,
                                                  int secondWidth, int secondHeight) {
        if (firstWidth <= 0 || firstHeight <= 0 || secondWidth <= 0 || secondHeight <= 0) {
            return false;
        }
        float firstAspect = firstWidth / (float) firstHeight;
        float secondAspect = secondWidth / (float) secondHeight;
        return Math.abs(firstAspect - secondAspect)
                / Math.max(0.0001f, secondAspect)
                <= VIRTUAL_DISPLAY_ASPECT_RATIO_TOLERANCE;
    }

    private boolean ensureVirtualDisplaySurfaceReady(SurfaceHolder holder,
                                                     VirtualDisplaySpec spec) {
        if (holder == null || spec == null) {
            return false;
        }
        Surface surface = holder.getSurface();
        if (surface == null || !surface.isValid()) {
            return false;
        }
        Rect frame = holder.getSurfaceFrame();
        if (frame != null && frame.width() == spec.width && frame.height() == spec.height) {
            return true;
        }
        holder.setFixedSize(spec.width, spec.height);
        Log.i(TAG, "Wait for full-size Surface before virtual display creation: slot=" + slot
                + ", current=" + (frame == null ? "unknown"
                : frame.width() + "x" + frame.height())
                + ", required=" + spec.width + "x" + spec.height);
        return false;
    }

    private Rect getReferenceRenderRect() {
        View workspace = callbacks.workspace();
        if (workspace == null || workspace.getWidth() <= 0 || workspace.getHeight() <= 0) {
            int width = Math.max(1, owner.getResources().getDisplayMetrics().widthPixels);
            int height = Math.max(1, owner.getResources().getDisplayMetrics().heightPixels);
            return new Rect(0, 0, width, height);
        }
        int activeMainSlot = callbacks.activeMainSlot();
        if (activeMainSlot < 0 || activeMainSlot >= MAX_WINDOWS) {
            return new Rect(0, 0, workspace.getWidth(), workspace.getHeight());
        }
        Rect[] rects = callbacks.calculateWindowRects();
        Rect mainRect = rects[activeMainSlot];
        if (mainRect == null || mainRect.width() <= 0 || mainRect.height() <= 0) {
            return new Rect(0, 0, workspace.getWidth(), workspace.getHeight());
        }
        return mainRect;
    }

    private float calculateMinimumQualityScale(int width, int height) {
        int shortEdge = Math.min(width, height);
        long area = (long) width * (long) height;
        float shortEdgeScale = VIRTUAL_DISPLAY_MIN_SHORT_EDGE_PX
                / (float) Math.max(1, shortEdge);
        float areaScale = (float) Math.sqrt(VIRTUAL_DISPLAY_MIN_AREA_PX
                / (double) Math.max(1L, area));
        return Math.max(1f, Math.max(shortEdgeScale, areaScale));
    }

    private boolean releaseVirtualDisplay() {
        invalidateTaskResolution();
        invalidateDisplayImePolicySetup();
        sensorLandscapeRotationGeneration++;
        requestedSensorLandscapeRotation = -1;
        sensorLandscapeRotationApplied = false;
        if (retainSensorPolicyOnRelease) {
            sensorPolicyGeneration++;
            sensorServiceIdlePackage = "";
            sensorServiceUidOverrideConfirmed = false;
        } else {
            resetSensorServiceUidOverrideAsync();
        }
        focusRequestGeneration++;
        destroyHostedInputFocusGuard();
        pendingApp = null;
        cancelHostedSurfaceReveal();
        launchRequestedPackage = "";
        launchRequestedUserId = -1;
        launchRequestedDisplayId = -1;
        rootInputBridgeClient.close();
        VirtualDisplay displayToRelease = virtualDisplay;
        boolean rootDisplayToRelease = rootManagedVirtualDisplay;
        int displayIdToRelease = displayId;
        if (rootDisplayToRelease) {
            rootVirtualDisplayBridgeClient.setSurface(
                    getRootInputBridgeToken(), slot, null);
            rootVirtualDisplayBridgeClient.release(getRootInputBridgeToken(), slot);
            rootManagedVirtualDisplay = false;
            Log.i(TAG, "Released root virtual display for slot " + slot
                    + ": displayId=" + displayIdToRelease);
        } else if (displayToRelease != null) {
            try {
                displayToRelease.setSurface(null);
                surfaceDetached = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "Detach virtual display before release failed for slot " + slot
                        + ", display=" + displayIdToRelease + ": "
                        + e.getClass().getSimpleName());
            }
            try {
                displayToRelease.release();
            } catch (RuntimeException e) {
                Log.e(TAG, "Release virtual display failed for slot " + slot
                        + ", display=" + displayIdToRelease + ": "
                        + e.getClass().getSimpleName());
                return false;
            }
            if (virtualDisplay == displayToRelease) {
                virtualDisplay = null;
            }
            Log.i(TAG, "Released virtual display for slot " + slot
                    + ": displayId=" + displayIdToRelease);
        }
        displayId = -1;
        primaryHomeEnhancementActive = false;
        hostedTaskId = -1;
        imePolicyConfiguredDisplayId = -1;
        displayWidth = 0;
        displayHeight = 0;
        displayDensityDpi = 0;
        displayUsesDualMainLayout = false;
        lastViewWidth = 0;
        lastViewHeight = 0;
        surfaceDetached = false;
        return true;
    }

    private void releaseVirtualDisplayWithRetry(String reason, Runnable onReleased) {
        int generation = ++displayReleaseGeneration;
        releaseVirtualDisplayWithRetry(reason, onReleased, generation, 1);
    }

    private void releaseVirtualDisplayWithRetry(String reason, Runnable onReleased,
                                                int generation, int attempt) {
        if (generation != displayReleaseGeneration) {
            return;
        }
        if (releaseVirtualDisplay()) {
            if (onReleased != null) {
                onReleased.run();
            }
            return;
        }
        if (attempt >= VIRTUAL_DISPLAY_RELEASE_MAX_ATTEMPTS) {
            Log.e(TAG, "Virtual display release exhausted retries for slot " + slot
                    + ", display=" + displayId + ", reason=" + reason);
            return;
        }
        mainHandler.postDelayed(() -> releaseVirtualDisplayWithRetry(
                        reason, onReleased, generation, attempt + 1),
                VIRTUAL_DISPLAY_RELEASE_RETRY_MS);
    }

    private void discardVirtualDisplayCandidate(VirtualDisplay candidate, int flags) {
        try {
            candidate.setSurface(null);
        } catch (RuntimeException e) {
            Log.w(TAG, "Detach rejected virtual display candidate failed for slot " + slot
                    + ", flags=" + flags + ": " + e.getClass().getSimpleName());
        }
        try {
            candidate.release();
        } catch (RuntimeException e) {
            Log.e(TAG, "Release rejected virtual display candidate failed for slot " + slot
                    + ", flags=" + flags + ": " + e.getClass().getSimpleName());
        }
    }

    private void invalidateDisplayImePolicySetup() {
        imePolicySetupGeneration++;
        imePolicySetupDisplayId = -1;
    }

    private void attachVirtualDisplaySurface(SurfaceHolder holder) {
        if (!hasVirtualDisplay() || holder == null || holder.getSurface() == null
                || !holder.getSurface().isValid()) {
            return;
        }
        try {
            displayReleaseGeneration++;
            if (setHostedDisplaySurface(holder.getSurface())) {
                surfaceDetached = false;
            } else {
                throw new IllegalStateException("virtual display surface rejected");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Attach virtual display surface failed for slot " + slot
                    + ": " + e.getClass().getSimpleName());
        }
    }

    private void detachVirtualDisplaySurface() {
        if (!hasVirtualDisplay() || surfaceDetached) {
            return;
        }
        try {
            if (setHostedDisplaySurface(null)) {
                surfaceDetached = true;
            } else {
                throw new IllegalStateException("virtual display detach rejected");
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Detach virtual display surface failed for slot " + slot + ": "
                    + e.getClass().getSimpleName());
        }
    }

    private String getHostedPackageName() {
        LauncherApp currentApp = slot >= 0 && slot < MAX_WINDOWS ? windowApps[slot] : null;
        if (currentApp != null) {
            return currentApp.packageName;
        }
        return pendingApp != null ? pendingApp.packageName : "";
    }

    private boolean startWithActivityOptions(Intent launchIntent, LauncherApp app,
                                             int startEpoch, boolean launcherMainActivity) {
        if (!shouldRunEmbeddedStart(startEpoch)) {
            unavailableReason = "启动已取消";
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            unavailableReason = "系统版本不支持 displayId 启动";
            return false;
        }

        Intent displayIntent = new Intent(launchIntent);
        displayIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean oemCloneDirectLaunch = !app.isHomeEntry() && usesOemCloneResolver();
        if (oemCloneDirectLaunch) {
            // HyperOS resolves both installed instances before ActivityTaskManager applies
            // the requested user. Its system-app authorization plus cached target user
            // bypasses that chooser while preserving the explicit user selection.
            displayIntent.putExtra(EXTRA_XSPACE_AUTHORIZED, true)
                    .putExtra(EXTRA_XSPACE_TARGET_USER, app.userId())
                    .putExtra(EXTRA_CLONE_RESOLVER_CONFIRMED, true)
                    .putExtra(EXTRA_CLONE_RESOLVER_CALLING_USER,
                            android.os.Process.myUserHandle().hashCode())
                    .putExtra(EXTRA_CLONE_RESOLVER_CALLING_PACKAGE,
                            owner.getPackageName());
        }
        ActivityOptions options = (displayIntent.getFlags()
                & Intent.FLAG_ACTIVITY_NO_ANIMATION) != 0
                ? ActivityOptions.makeCustomAnimation(owner, 0, 0)
                : ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);
        try {
            if (launcherMainActivity && !app.isHomeEntry() && launcherApps != null
                    && !oemCloneDirectLaunch) {
                launcherApps.startMainActivity(
                        app.componentName, app.userHandle, null, options.toBundle());
            } else if (app.isCurrentUser() || oemCloneDirectLaunch) {
                // XSpace consumes EXTRA_XSPACE_TARGET_USER while the outbound Binder
                // caller remains OneStep. This both selects the exact app instance and
                // lets Android validate/grant the source URI against OneStep's UID.
                owner.startActivity(displayIntent, options.toBundle());
            } else if (rootAvailable) {
                boolean rootStarted = rootVirtualDisplayBridgeClient.startActivityAsUser(
                        getRootInputBridgeToken(), displayIntent,
                        android.os.Process.myUserHandle().hashCode(),
                        app.userId(), displayId);
                if (!rootStarted) {
                    return false;
                }
            } else {
                return false;
            }
            Log.i(TAG, "ActivityOptions start ok: " + app.packageName
                    + " user=" + app.userId()
                    + " on display " + displayId);
            return true;
        } catch (RuntimeException e) {
            unavailableReason = e.getClass().getSimpleName();
            if (e instanceof SecurityException) {
                skipActivityOptionsLaunch = true;
            }
            Log.w(TAG, "ActivityOptions start failed: " + app.packageName
                    + " user=" + app.userId()
                    + " on display " + displayId + ": " + unavailableReason);
            return false;
        }
    }

    private ComponentName resolveLaunchComponent(Intent launchIntent) {
        ComponentName componentName = launchIntent.getComponent();
        if (componentName != null) {
            return componentName;
        }
        return launchIntent.resolveActivity(packageManager);
    }

    private boolean hasSuCommand() {
        Boolean cached = cachedSuCommandAvailable;
        if (cached != null) {
            return cached;
        }
        synchronized (RootVirtualDisplayHost.class) {
            cached = cachedSuCommandAvailable;
            if (cached != null) {
                return cached;
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
                    cachedSuCommandAvailable = true;
                    return true;
                }
            }

            Process process = null;
            try {
                process = new ProcessBuilder("sh", "-c", "command -v su")
                        .redirectErrorStream(true)
                        .start();
                boolean finished = waitForProcess(process, 600L);
                cachedSuCommandAvailable = finished && process.exitValue() == 0;
                return cachedSuCommandAvailable;
            } catch (IOException | InterruptedException | RuntimeException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                cachedSuCommandAvailable = false;
                return false;
            } finally {
                if (process != null) {
                    process.destroy();
                }
            }
        }
    }

    private void runPrivilegedCommandAsync(String command, String description) {
        if (rootAvailable) {
            runRootCommandAsync(command, description);
        } else {
            Log.w(TAG, "Skip privileged command without su: " + description);
        }
    }

    void ensureRootInputBridgeStarted() {
        ensureRootInputBridgeStarted(false, false);
    }

    private void ensureRootInputBridgeStarted(boolean force, boolean synchronous) {
        if (!rootAvailable) {
            Log.w(TAG, "Direct input bridge unavailable: root is not available");
            return;
        }
        if (synchronous) {
            startRootInputBridgeIfNeeded(force);
            return;
        }
        try {
            rootExecutor.execute(() -> startRootInputBridgeIfNeeded(force));
        } catch (RuntimeException e) {
            Log.w(TAG, "Queue direct input bridge start failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private boolean startRootInputBridgeIfNeeded(boolean force) {
        return startRootInputBridgeIfNeeded(force, true);
    }

    private boolean startRootInputBridgeIfNeeded(boolean force, boolean requireRoot) {
        synchronized (rootInputBridgeStartLock) {
            String bridgeToken = getRootInputBridgeToken();
            Integer currentBridgeUid =
                    rootInputBridgeClient.getCurrentBridgeUid(bridgeToken);
            Integer displayBridgeUid = currentBridgeUid != null
                    ? rootVirtualDisplayBridgeClient.getBridgeUid(bridgeToken) : null;
            boolean bridgeReady = currentBridgeUid != null
                    && displayBridgeUid != null
                    && (!requireRoot || (currentBridgeUid == 0 && displayBridgeUid == 0));
            if (bridgeReady) {
                if (!force) {
                    registerCrossAppLaunchRouting(bridgeToken);
                    return true;
                }
                Log.w(TAG, "Restart direct input bridge after operation failure: uid="
                        + currentBridgeUid);
                callbacks.setRootInputBridgeLastStartUptime(SystemClock.uptimeMillis());
                return restartRootInputBridge(bridgeToken);
            }
            if (currentBridgeUid != null) {
                Log.w(TAG, "Replace bridge without root display service: inputUid="
                        + currentBridgeUid + ", displayUid=" + displayBridgeUid);
            }
            long now = SystemClock.uptimeMillis();
            long lastStartUptime = callbacks.rootInputBridgeLastStartUptime();
            if (!force && lastStartUptime > 0L
                    && now - lastStartUptime
                    < ROOT_INPUT_BRIDGE_START_THROTTLE_MS) {
                return false;
            }
            callbacks.setRootInputBridgeLastStartUptime(now);
            return restartRootInputBridge(bridgeToken);
        }
    }

    private boolean restartRootInputBridge(String bridgeToken) {
        String apkPath = owner.getApplicationInfo().sourceDir;
        int uid = android.os.Process.myUid();
        String quotedBridgeToken = shellQuote(bridgeToken);
        rootInputBridgeClient.close();
        String cleanupCommand = "for p in $(pidof app_process64 app_process 2>/dev/null); do "
                + "cmdline=$(tr '\\0' ' ' < /proc/$p/cmdline 2>/dev/null); "
                + "case \"$cmdline\" in *" + ROOT_INPUT_BRIDGE_CLASS
                + "*) kill $p 2>/dev/null;; esac; done; ";
        String bridgeCommand = "runtime=/system/bin/app_process64; "
                + "[ -x \"$runtime\" ] || runtime=/system/bin/app_process; "
                + "CLASSPATH=" + shellQuote(apkPath)
                + " \"$runtime\" /system/bin "
                + ROOT_INPUT_BRIDGE_CLASS + " " + uid + " " + quotedBridgeToken
                + " </dev/null >/dev/null 2>&1 &";
        rootInputBridgeClient.close();
        rootVirtualDisplayBridgeClient.close();
        String policyCommand = "";
        if (RootBridgePolicyCompat.shouldApplyLivePolicy(Build.VERSION.SDK_INT)) {
            policyCommand = "if command -v magiskpolicy >/dev/null 2>&1; then "
                    + "root_context=$(id -Z 2>/dev/null); "
                    + "root_domain=${root_context#u:r:}; root_domain=${root_domain%%:*}; "
                    + "case \"$root_domain\" in ''|*[!a-zA-Z0-9_]*) ;; *) "
                    + "magiskpolicy --live "
                    + "\"allow $root_domain default_android_service service_manager add\" "
                    + shellQuote("allow priv_app default_android_service service_manager find")
                    + " "
                    + "\"allow priv_app $root_domain binder { call transfer }\" "
                    + "\"allow $root_domain priv_app binder { call transfer }\" "
                    + "\"allow priv_app $root_domain unix_stream_socket connectto\" "
                    + "\"allow $root_domain priv_app fd use\" "
                    + "\"allow $root_domain surfaceflinger_service service_manager find\" "
                    + "\"allow $root_domain surfaceflinger binder { call transfer }\" "
                    + "\"allow surfaceflinger $root_domain binder transfer\" "
                    + ">/dev/null 2>&1 || true; esac; fi; ";
        }
        ShellCommandResult rootResult = runRootCommand(
                cleanupCommand + policyCommand + bridgeCommand);
        if (waitForRootInputBridge(bridgeToken)) {
            registerCrossAppLaunchRouting(bridgeToken);
            Log.i(TAG, "Root display bridge ready: uid=0, launcherExit="
                    + rootResult.exitCode);
            return true;
        }
        Log.w(TAG, "Root display bridge did not become ready: rootExit="
                + rootResult.exitCode
                + ", rootOutput=" + rootResult.output);
        return false;
    }

    private void registerCrossAppLaunchRouting(String bridgeToken) {
        if (!rootVirtualDisplayBridgeClient.registerCrossAppLaunchCallback(
                bridgeToken, callbacks::onCrossAppLaunch,
                callbacks::onSystemTaskEvent)) {
            Log.w(TAG, "Cross-app launch routing callback unavailable for slot " + slot);
        }
    }

    void syncLaunchRoutingSource() {
        boolean active = slot == callbacks.activeMainSlot()
                && displayId > DEFAULT_DISPLAY_ID
                && slot >= 0 && slot < MAX_WINDOWS
                && windowApps[slot] != null;
        String packageName = active ? windowApps[slot].packageName : "";
        rootVirtualDisplayBridgeClient.updateLaunchSource(
                getRootInputBridgeToken(), active ? displayId : DEFAULT_DISPLAY_ID,
                packageName, active);
    }

    private boolean waitForRootInputBridge(String bridgeToken) {
        long deadline = SystemClock.uptimeMillis() + ROOT_INPUT_BRIDGE_READY_TIMEOUT_MS;
        do {
            Integer inputUid = rootInputBridgeClient.getCurrentBridgeUid(bridgeToken);
            Integer displayUid = rootVirtualDisplayBridgeClient.getBridgeUid(bridgeToken);
            if (inputUid != null && inputUid == 0
                    && displayUid != null && displayUid == 0) {
                return true;
            }
            long remainingMs = deadline - SystemClock.uptimeMillis();
            if (remainingMs <= 0L) {
                return false;
            }
            SystemClock.sleep(Math.min(ROOT_INPUT_BRIDGE_READY_RETRY_MS, remainingMs));
        } while (true);
    }

    private void runStartCommandAsync(String command, int startEpoch, String description) {
        if (rootAvailable) {
            runRootCommandAsync(buildGuardedStartCommand(command, startEpoch), description,
                    startEpoch);
        } else {
            Log.w(TAG, "Skip display start without su: " + description);
        }
    }

    private String buildGuardedStartCommand(String command, int startEpoch) {
        if (embeddedStartEpochStore == null) {
            return command;
        }
        return "epoch=$(cat " + shellQuote(embeddedStartEpochStore.getFilePath())
                + " 2>/dev/null); if [ \"$epoch\" != \"" + startEpoch
                + "\" ]; then echo stale embedded start; exit 73; fi; " + command;
    }

    private String buildRootInputBridgeToken() {
        try {
            PackageInfo packageInfo = packageManager.getPackageInfo(owner.getPackageName(), 0);
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
            ApplicationInfo applicationInfo = owner.getApplicationInfo();
            return versionCode + ":" + packageInfo.lastUpdateTime + ":"
                    + applicationInfo.sourceDir;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "unknown";
        }
    }

    private String getRootInputBridgeToken() {
        String token = cachedRootInputBridgeToken;
        if (token != null) {
            return token;
        }
        synchronized (rootInputBridgeStartLock) {
            if (cachedRootInputBridgeToken == null) {
                cachedRootInputBridgeToken = buildRootInputBridgeToken();
            }
            return cachedRootInputBridgeToken;
        }
    }

    private ShellCommandResult runPrivilegedCommand(String command, String description) {
        return runPrivilegedCommand(command, description, true);
    }

    private ShellCommandResult runPrivilegedCommand(String command, String description,
                                                   boolean logOutput) {
        if (rootAvailable) {
            long startedAt = System.currentTimeMillis();
            ShellCommandResult result = runRootCommand(command);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (result.exitCode == 0) {
                Log.i(TAG, "Root command ok(" + elapsedMs + "ms): "
                        + description + formatCommandLogOutput(result.output, logOutput));
            } else {
                Log.w(TAG, "Root command failed(" + result.exitCode + ", "
                        + elapsedMs + "ms): "
                        + description + formatCommandLogOutput(result.output, logOutput));
            }
            return result;
        }
        Log.w(TAG, "Privileged command unavailable without su: " + description);
        return new ShellCommandResult(-1, "su unavailable");
    }

    private void runRootCommandAsync(String command, String description) {
        runRootCommandAsync(rootExecutor, command, description);
    }

    private void runRootCommandAsync(ExecutorService executor, String command,
                                     String description) {
        executor.execute(() -> {
            long startedAt = System.currentTimeMillis();
            ShellCommandResult result = runRootCommand(command);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (result.exitCode == 0) {
                Log.i(TAG, "Root command ok(" + elapsedMs + "ms): "
                        + description + " " + result.output);
            } else {
                Log.w(TAG, "Root command failed(" + result.exitCode + ", "
                        + elapsedMs + "ms): "
                        + description + " " + result.output);
            }
        });
    }

    private void runRootCommandAsync(String command, String description, int startEpoch) {
        rootExecutor.execute(() -> {
            if (!shouldRunEmbeddedStart(startEpoch)) {
                Log.i(TAG, "Skip stale start command: " + description);
                return;
            }
            long startedAt = System.currentTimeMillis();
            ShellCommandResult result = runRootCommand(command);
            if (result.exitCode == 73) {
                Log.i(TAG, "Skip stale start command: " + description);
                return;
            }
            long elapsedMs = System.currentTimeMillis() - startedAt;
            if (result.exitCode == 0) {
                Log.i(TAG, "Root command ok(" + elapsedMs + "ms): "
                        + description + " " + result.output);
            } else {
                Log.w(TAG, "Root command failed(" + result.exitCode + ", "
                        + elapsedMs + "ms): " + description + " " + result.output);
            }
        });
    }

    private ShellCommandResult runRootCommand(String command) {
        return persistentRootShell.run(command, ROOT_COMMAND_TIMEOUT_SECONDS);
    }

    private String formatCommandLogOutput(String output, boolean includeOutput) {
        if (TextUtils.isEmpty(output)) {
            return "";
        }
        return includeOutput ? " " + output : " <" + output.length() + " chars>";
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private boolean hasGrantedSystemEmbeddingPermission() {
        return callbacks.hasGrantedSystemEmbeddingPermission();
    }

    private boolean isSystemAppInstall() {
        return callbacks.isSystemAppInstall();
    }

    private boolean shouldRunEmbeddedStart(int startEpoch) {
        return callbacks.shouldRunEmbeddedStart(startEpoch);
    }

    private boolean isMainDisplaySlot(int targetSlot) {
        return callbacks.isMainDisplaySlot(targetSlot);
    }

    private void showEmbeddingHintIfNeeded(String reason) {
        callbacks.showEmbeddingHint(reason);
    }

    private void swapWithMain(int targetSlot) {
        callbacks.swapWithMain(targetSlot);
    }

    private int dp(float value) {
        return callbacks.dp(value);
    }

    private int getLatestPhysicalLandscapeRotation() {
        return callbacks.latestPhysicalLandscapeRotation();
    }

    private Set<String> getRecordedSensorUidOverrides() {
        return callbacks.recordedSensorUidOverrides();
    }

    private void recordSensorUidOverride(String packageName) {
        callbacks.recordSensorUidOverride(packageName);
    }

    private void clearSensorUidOverrideRecord(String packageName) {
        callbacks.clearSensorUidOverrideRecord(packageName);
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

    int getDisplayId() {
        return displayId;
    }

    int getSlot() {
        return slot;
    }

    boolean hasRootAccess() {
        return rootAvailable;
    }
    private static long toWallTimeMillis(long uptimeMillis) {
        long nowWallTime = System.currentTimeMillis();
        long nowUptime = SystemClock.uptimeMillis();
        return nowWallTime - Math.max(0L, nowUptime - uptimeMillis);
    }

    private static String formatTimestamp(long wallTimeMillis) {
        return String.format(Locale.US, "%tF %<tT.%<tL", wallTimeMillis);
    }

    private static String formatCoordinate(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String motionActionName(int actionMasked) {
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                return "DOWN";
            case MotionEvent.ACTION_UP:
                return "UP";
            case MotionEvent.ACTION_MOVE:
                return "MOVE";
            case MotionEvent.ACTION_POINTER_DOWN:
                return "POINTER_DOWN";
            case MotionEvent.ACTION_POINTER_UP:
                return "POINTER_UP";
            case MotionEvent.ACTION_CANCEL:
                return "CANCEL";
            default:
                return String.valueOf(actionMasked);
        }
    }
}
