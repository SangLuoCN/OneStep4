package com.sangluo.onestep.system.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.function.BooleanSupplier;

/** Applies system bars and owns the tiny default-display key-focus anchor. */
public final class SystemUiController implements AutoCloseable {
    private static final String TAG = "OneStep40";
    private final Activity activity;
    private final BooleanSupplier hideStatusBar;
    private final BooleanSupplier destroyed;
    private final BooleanSupplier systemAppInstall;
    private final Runnable backHandler;
    private Dialog defaultDisplayFocusWindow;
    private Dialog defaultDisplayBackCallbackOwner;
    private Object defaultDisplayBackCallback;
    private Boolean appliedHideStatusBar;
    private Boolean appliedSystemAppInstall;

    public SystemUiController(Activity activity,
                              BooleanSupplier hideStatusBar, BooleanSupplier destroyed,
                              BooleanSupplier systemAppInstall, Runnable backHandler) {
        this.activity = activity;
        this.hideStatusBar = hideStatusBar;
        this.destroyed = destroyed;
        this.systemAppInstall = systemAppInstall;
        this.backHandler = backHandler;
    }

    public void apply() {
        boolean shouldHideStatusBar = hideStatusBar.getAsBoolean();
        boolean isSystemAppInstall = systemAppInstall.getAsBoolean();
        if (appliedHideStatusBar != null
                && appliedHideStatusBar == shouldHideStatusBar
                && appliedSystemAppInstall != null
                && appliedSystemAppInstall == isSystemAppInstall) {
            if (shouldHideStatusBar
                    && (defaultDisplayFocusWindow == null
                    || !defaultDisplayFocusWindow.isShowing())) {
                activity.getWindow().getDecorView().post(this::ensureDefaultDisplayFocusWindow);
            }
            showDefaultDisplayNavigationBar(activity.getWindow());
            if (defaultDisplayFocusWindow != null && defaultDisplayFocusWindow.isShowing()) {
                showDefaultDisplayNavigationBar(defaultDisplayFocusWindow.getWindow());
            }
            return;
        }
        appliedHideStatusBar = shouldHideStatusBar;
        appliedSystemAppInstall = isSystemAppInstall;
        if (shouldHideStatusBar) {
            hideStatusBar();
        } else {
            showStatusBar();
        }
        applyHostWindowFocus();
        showDefaultDisplayNavigationBar(activity.getWindow());
    }

    public void invalidateAppliedState() {
        appliedHideStatusBar = null;
        appliedSystemAppInstall = null;
    }

    @Override
    public void close() {
        dismissDefaultDisplayFocusWindow();
    }

    private void applyHostWindowFocus() {
        Window window = activity.getWindow();
        if (hideStatusBar.getAsBoolean()) {
            if ((window.getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            }
            window.getDecorView().post(this::ensureDefaultDisplayFocusWindow);
        } else {
            if ((window.getAttributes().flags
                    & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            }
            dismissDefaultDisplayFocusWindow();
        }
    }

    @SuppressLint("GestureBackNavigation")
    private void ensureDefaultDisplayFocusWindow() {
        if (destroyed.getAsBoolean() || activity.isFinishing()
                || !hideStatusBar.getAsBoolean()) {
            return;
        }
        if (defaultDisplayFocusWindow != null && defaultDisplayFocusWindow.isShowing()) {
            return;
        }
        try {
            Dialog focusWindow = new Dialog(
                    activity, android.R.style.Theme_Translucent_NoTitleBar);
            focusWindow.setCancelable(false);
            focusWindow.setOnKeyListener((dialog, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        backHandler.run();
                    }
                    return true;
                }
                return false;
            });
            FrameLayout content = new FrameLayout(focusWindow.getContext());
            content.setBackgroundColor(Color.TRANSPARENT);
            content.setFocusable(true);
            content.setFocusableInTouchMode(true);
            content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            focusWindow.setContentView(content, new ViewGroup.LayoutParams(1, 1));

            Window window = focusWindow.getWindow();
            if (window == null) {
                throw new IllegalStateException("default display focus window unavailable");
            }
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.width = 1;
            attributes.height = 1;
            attributes.gravity = Gravity.TOP | Gravity.START;
            attributes.dimAmount = 0f;
            attributes.windowAnimations = 0;
            window.setAttributes(attributes);

            defaultDisplayFocusWindow = focusWindow;
            focusWindow.setOnDismissListener(dialog -> {
                unregisterDefaultDisplayBackCallback(focusWindow);
                if (defaultDisplayFocusWindow == focusWindow) {
                    defaultDisplayFocusWindow = null;
                }
            });
            focusWindow.show();
            registerDefaultDisplayBackCallback(focusWindow);
            window.setLayout(1, 1);
            showDefaultDisplayNavigationBar(window);
            content.requestFocus();
            Log.i(TAG, "Default display key focus anchor shown");
        } catch (RuntimeException e) {
            dismissDefaultDisplayFocusWindow();
            Log.w(TAG, "Default display key focus anchor failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private void dismissDefaultDisplayFocusWindow() {
        Dialog focusWindow = defaultDisplayFocusWindow;
        defaultDisplayFocusWindow = null;
        if (focusWindow == null) {
            return;
        }
        unregisterDefaultDisplayBackCallback(focusWindow);
        try {
            if (focusWindow.isShowing()) {
                focusWindow.dismiss();
            }
            Log.i(TAG, "Default display key focus anchor dismissed");
        } catch (RuntimeException e) {
            Log.w(TAG, "Dismiss default display key focus anchor failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private void registerDefaultDisplayBackCallback(Dialog focusWindow) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        unregisterDefaultDisplayBackCallback(defaultDisplayBackCallbackOwner);
        try {
            defaultDisplayBackCallback = Api33Impl.registerBackCallback(
                    focusWindow, backHandler);
            defaultDisplayBackCallbackOwner = focusWindow;
            Log.i(TAG, "Default display predictive-back callback registered");
        } catch (RuntimeException e) {
            defaultDisplayBackCallback = null;
            defaultDisplayBackCallbackOwner = null;
            Log.w(TAG, "Default display predictive-back registration failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private void unregisterDefaultDisplayBackCallback(Dialog focusWindow) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || focusWindow == null
                || defaultDisplayBackCallbackOwner != focusWindow
                || defaultDisplayBackCallback == null) {
            return;
        }
        Object callback = defaultDisplayBackCallback;
        defaultDisplayBackCallback = null;
        defaultDisplayBackCallbackOwner = null;
        try {
            Api33Impl.unregisterBackCallback(focusWindow, callback);
        } catch (RuntimeException e) {
            Log.w(TAG, "Default display predictive-back unregister failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private static final class Api33Impl {
        private Api33Impl() {
        }

        static Object registerBackCallback(Dialog dialog, Runnable backHandler) {
            android.window.OnBackInvokedCallback callback = backHandler::run;
            dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return callback;
        }

        static void unregisterBackCallback(Dialog dialog, Object callback) {
            dialog.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    (android.window.OnBackInvokedCallback) callback);
        }
    }

    private void showDefaultDisplayNavigationBar(Window window) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        if (window == null || (display != null
                && display.getDisplayId() != Display.DEFAULT_DISPLAY)) {
            return;
        }
        View decorView = window.getDecorView();
        boolean isSystemAppInstall = systemAppInstall.getAsBoolean();
        if (isSystemAppInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (isSystemAppInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.setNavigationBarDividerColor(Color.TRANSPARENT);
        }
        if (isSystemAppInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        int visibility = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        visibility &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        if (isSystemAppInstall && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            visibility &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decorView.setSystemUiVisibility(visibility);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller =
                    decorView.getWindowInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.navigationBars());
            } else {
                decorView.postDelayed(() -> showDefaultDisplayNavigationBar(window), 16);
            }
        }
    }

    private void showStatusBar() {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
        }
        applyDisplayCutoutMode(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller =
                    decorView.getWindowInsetsController();
            if (controller != null) {
                controller.show(android.view.WindowInsets.Type.statusBars());
            } else {
                decorView.postDelayed(this::apply, 16);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        decorView.setOnSystemUiVisibilityChangeListener(null);
    }

    private void hideStatusBar() {
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyDisplayCutoutMode(window);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller =
                    decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController
                                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                decorView.postDelayed(this::apply, 16);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            boolean statusVisible = (visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0;
            if (hideStatusBar.getAsBoolean() && statusVisible) {
                decorView.postDelayed(this::apply, 16);
            }
        });
    }

    private void applyDisplayCutoutMode(Window window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
    }

}
