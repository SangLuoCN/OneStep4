package com.sangluo.onestep.ui.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.method.LinkMovementMethod;
import android.text.TextUtils;
import android.text.util.Linkify;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.sangluo.onestep.R;
import com.sangluo.onestep.system.root.ZygiskHookConfig;
import com.sangluo.onestep.ui.widget.AspectRatioImageView;
import com.sangluo.onestep.ui.widget.FixedViewportFrameLayout;
import com.sangluo.onestep.ui.window.OneStepWindowView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.CORNER_TRIGGER_SENSITIVITY_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.MIN_SIDE_WINDOWS;
import static com.sangluo.onestep.data.settings.OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_ICON_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_VERTICAL_MARGIN_SCALE_MAX;
import static com.sangluo.onestep.data.settings.OneStepSettings.TOP_NAV_VERTICAL_MARGIN_SCALE_MIN;
import static com.sangluo.onestep.data.settings.OneStepSettings.canUseSideWindowCount;
import static com.sangluo.onestep.data.settings.OneStepSettings.clamp;
import static com.sangluo.onestep.data.settings.OneStepSettings.sanitizeSideWindowCount;

public final class SettingsPanelController {
    private static final long SLIDER_LIVE_UPDATE_INTERVAL_MS = 100L;

    public interface Callbacks {
        OneStepWindowView activeMainWindowView();
        GradientDrawable panelBackground(int fillColor, int strokeColor, float radius);
        ImageView navigationIcon(int drawableResId, String description);
        void applyBackground(ImageView target);
        void pickBackground();
        void previewCornerTrigger();
        int desktopGridRows();
        int desktopGridColumns();
        int oneStepTriggerAreaScalePct();
        int cornerTriggerSensitivityPct();
        int topNavVerticalMarginScalePct();
        int topAppIconScalePct();
        int topAppStripSpacingScalePct();
        int topAppStripVerticalPaddingScalePct();
        boolean topComponentsVisible();
        boolean statusBarSpacingEnabled();
        boolean verticalWindowLayout();
        boolean logRecordingEnabled();
        int sideWindowCount();
        void saveGridLayout(int rows, int columns);
        void saveOneStepTriggerAreaScale(int value);
        void saveCornerTriggerSensitivity(int value);
        void saveTopNavVerticalMarginScale(int value);
        void saveTopAppIconScale(int value);
        void saveTopAppStripSpacingScale(int value);
        void saveTopAppStripVerticalPaddingScale(int value);
        void saveTopComponentsVisible(boolean visible);
        void saveStatusBarSpacingEnabled(boolean enabled);
        void saveVerticalWindowLayout(boolean enabled);
        void saveLogRecordingEnabled(boolean enabled);
        void saveSideWindowCount(int count);
        void exportSessionLog();
        boolean rootAuthorizationGranted();
        void requestRootAuthorization(RootAuthorizationResultCallback callback);
        boolean openKernelSuManager();
        void loadZygiskHookSettings(HookSettingsResultCallback callback);
        void saveZygiskHookSettings(boolean secureWindowEnabled,
                                    boolean statusBarOverlayEnabled,
                                    HookSettingsResultCallback callback);
        void rebootDevice();
    }

    public interface HookSettingsResultCallback {
        void onResult(ZygiskHookConfig.State state, String error);
    }

    public interface RootAuthorizationResultCallback {
        void onResult(RootAuthorizationResult result);
    }

    public enum RootAuthorizationResult {
        GRANTED,
        KERNEL_SU_ACTION_REQUIRED,
        FAILED
    }

    private static final String BILIBILI_PROFILE_URL = "https://space.bilibili.com/1037274194";
    private static final String DOUYIN_PROFILE_URL = "https://v.douyin.com/L4Kz8rINTrU";
    private static final String GITHUB_PROFILE_URL = "https://github.com/SangLuoCN";

    private final Activity activity;
    private final Callbacks callbacks;
    private FrameLayout internalSettingsPage;
    private FrameLayout legalNoticesPage;
    private LinearLayout rootAuthorizationItem;
    private ImageView settingsBackgroundPreview;
    private TextView gridLayoutValueView;
    private TextView topAppIconSizeValueView;
    private TextView topAppStripSpacingValueView;
    private TextView topAppStripVerticalPaddingValueView;
    private TextView topComponentsVisibleValueView;
    private TextView statusBarSpacingValueView;
    private TextView verticalWindowLayoutValueView;
    private TextView sideWindowCountValueView;
    private TextView topNavVerticalMarginValueView;
    private TextView oneStepTriggerAreaValueView;
    private TextView cornerTriggerSensitivityValueView;
    private TextView logRecordingEnabledValueView;
    private Switch topComponentsVisibleSwitch;
    private Switch statusBarSpacingSwitch;
    private Switch verticalWindowLayoutSwitch;
    private Switch logRecordingEnabledSwitch;
    private TextView rootAuthorizationValueView;
    private TextView zygiskHookStatusValueView;
    private TextView secureWindowHookValueView;
    private TextView statusBarOverlayHookValueView;
    private Switch secureWindowHookSwitch;
    private Switch statusBarOverlayHookSwitch;
    private LinearLayout applyHookSettingsItem;
    private LinearLayout exportLogItem;
    private int desktopGridRows;
    private int desktopGridColumns;
    private int oneStepTriggerAreaScalePct;
    private int cornerTriggerSensitivityPct;
    private int topNavVerticalMarginScalePct;
    private int topAppIconScalePct;
    private int topAppStripSpacingScalePct;
    private int topAppStripVerticalPaddingScalePct;
    private boolean topComponentsVisible;
    private boolean statusBarSpacingEnabled;
    private boolean verticalWindowLayout;
    private boolean logRecordingEnabled;
    private boolean secureWindowHookEnabled = true;
    private boolean statusBarOverlayHookEnabled = true;
    private boolean zygiskModuleInstalled;
    private boolean zygiskPayloadActive;
    private boolean lsposedInstalled;
    private boolean lsposedBackendActive;
    private boolean standaloneBackendActive;
    private boolean hookSettingsLoaded;
    private boolean hookSettingsSaving;
    private boolean updatingHookSwitches;
    private boolean hookSettingsReadFailed;
    private boolean hookSettingsNeedsRoot;
    private boolean rootAuthorizationGranted;
    private boolean rootAuthorizationRequestInFlight;
    private int hookSettingsRequestGeneration;
    private int sideWindowCount;

    public SettingsPanelController(Activity activity, Callbacks callbacks) {
        this.activity = activity;
        this.callbacks = callbacks;
    }

    public void show() {
        showInWindow(getActiveMainWindowView());
    }

    public void showInWindow(OneStepWindowView targetWindowView) {
        syncState();
        if (targetWindowView == null) {
            return;
        }
        hideOverlay(legalNoticesPage);
        if (internalSettingsPage == null) {
            internalSettingsPage = createInternalSettingsPage();
        }
        ViewParent currentParent = internalSettingsPage.getParent();
        if (currentParent != targetWindowView) {
            if (currentParent instanceof OneStepWindowView) {
                ((OneStepWindowView) currentParent).hideInternalOverlay(internalSettingsPage);
            } else if (currentParent instanceof ViewGroup) {
                ((ViewGroup) currentParent).removeView(internalSettingsPage);
            }
            targetWindowView.showInternalOverlay(internalSettingsPage);
        }
        refresh();
        prepareZygiskHookSettings();
        internalSettingsPage.bringToFront();
    }

    public void hide() {
        hideOverlay(internalSettingsPage);
        hideOverlay(legalNoticesPage);
    }

    public boolean isVisible() {
        return isOverlayAttached(internalSettingsPage) || isOverlayAttached(legalNoticesPage);
    }

    public boolean isShownInWindow(OneStepWindowView windowView) {
        return windowView != null && (isOverlayInWindow(internalSettingsPage, windowView)
                || isOverlayInWindow(legalNoticesPage, windowView));
    }

    private OneStepWindowView getActiveMainWindowView() {
        return callbacks.activeMainWindowView();
    }

    private FrameLayout createInternalSettingsPage() {
        FixedViewportFrameLayout page = new FixedViewportFrameLayout(activity);
        page.setBackgroundColor(0xffeeeeee);
        page.setClickable(true);
        FrameLayout viewport = page.getViewport();

        View background = new View(activity);
        background.setBackgroundColor(0xffeeeeee);
        viewport.addView(background, matchFrame());

        View wash = new View(activity);
        wash.setBackgroundColor(0x00ffffff);
        viewport.addView(wash, matchFrame());

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(24));
        viewport.addView(content, matchFrame());

        FrameLayout header = new FrameLayout(activity);
        header.setBackgroundColor(Color.WHITE);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ImageView close = createTopNavImageControl(
                R.drawable.top_nav_page_left, "关闭设置");
        close.setColorFilter(0xff333333);
        close.setOnClickListener(v -> hide());
        header.addView(close, new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.START | Gravity.CENTER_VERTICAL));

        TextView title = new TextView(activity);
        title.setText("一步设置");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        setDpTextSize(title, 16);
        header.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView settingsScroll = new ScrollView(activity);
        settingsScroll.setFillViewport(true);
        settingsScroll.setClipChildren(false);
        settingsScroll.setClipToPadding(false);
        content.addView(settingsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(14), dp(12), dp(14), dp(12));
        settingsScroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout backgroundItem = createBackgroundSettingsItem();
        settingsBackgroundPreview = new AspectRatioImageView(activity);
        settingsBackgroundPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        settingsBackgroundPreview.setBackground(makePanelBackground(Color.WHITE, 0x14000000, dp(4)));
        settingsBackgroundPreview.setOnClickListener(v -> pickBackgroundFromGallery());
        LinearLayout.LayoutParams previewLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewLp.topMargin = dp(12);
        backgroundItem.addView(settingsBackgroundPreview, previewLp);
        list.addView(backgroundItem, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout gridItem = createSettingsItem("图标布局设置", getGridLayoutLabel());
        gridLayoutValueView = (TextView) gridItem.getTag();
        gridItem.setOnClickListener(v -> showGridLayoutDialog());
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        gridLp.topMargin = dp(12);
        list.addView(gridItem, gridLp);

        LinearLayout sideWindowCountItem = createSettingsItem("小窗口数量",
                getSideWindowCountLabel());
        sideWindowCountValueView = (TextView) sideWindowCountItem.getTag();
        sideWindowCountItem.setOnClickListener(v -> showSideWindowCountDialog());
        LinearLayout.LayoutParams sideWindowCountLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        sideWindowCountLp.topMargin = dp(12);
        list.addView(sideWindowCountItem, sideWindowCountLp);

        LinearLayout cornerSizeItem = createSliderSettingsItem("一步触发区域",
                ONE_STEP_TRIGGER_AREA_SCALE_MIN, ONE_STEP_TRIGGER_AREA_SCALE_MAX,
                oneStepTriggerAreaScalePct,
                value -> saveOneStepTriggerAreaScale(value),
                this::formatPercentValue, this::showCornerTriggerPreview);
        oneStepTriggerAreaValueView = (TextView) cornerSizeItem.getTag();
        LinearLayout.LayoutParams cornerSizeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        cornerSizeLp.topMargin = dp(12);
        list.addView(cornerSizeItem, cornerSizeLp);

        LinearLayout cornerSensitivityItem = createSliderSettingsItem("角落触发灵敏度",
                CORNER_TRIGGER_SENSITIVITY_MIN, CORNER_TRIGGER_SENSITIVITY_MAX,
                cornerTriggerSensitivityPct,
                value -> saveCornerTriggerSensitivity(value));
        cornerTriggerSensitivityValueView = (TextView) cornerSensitivityItem.getTag();
        LinearLayout.LayoutParams cornerSensitivityLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        cornerSensitivityLp.topMargin = dp(12);
        list.addView(cornerSensitivityItem, cornerSensitivityLp);

        LinearLayout navVerticalMarginItem = createSliderSettingsItem("导航栏高度",
                TOP_NAV_VERTICAL_MARGIN_SCALE_MIN, TOP_NAV_VERTICAL_MARGIN_SCALE_MAX,
                topNavVerticalMarginScalePct,
                value -> saveTopNavVerticalMarginScale(value));
        topNavVerticalMarginValueView = (TextView) navVerticalMarginItem.getTag();
        LinearLayout.LayoutParams navVerticalMarginLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        navVerticalMarginLp.topMargin = dp(12);
        list.addView(navVerticalMarginItem, navVerticalMarginLp);

        LinearLayout topIconSizeItem = createSliderSettingsItem("图标栏图标大小",
                TOP_APP_ICON_SCALE_MIN, TOP_APP_ICON_SCALE_MAX, topAppIconScalePct,
                value -> saveTopAppIconScale(value));
        topAppIconSizeValueView = (TextView) topIconSizeItem.getTag();
        LinearLayout.LayoutParams topIconLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        topIconLp.topMargin = dp(12);
        list.addView(topIconSizeItem, topIconLp);

        LinearLayout topSpacingItem = createSliderSettingsItem("图标栏间距",
                TOP_APP_STRIP_SPACING_SCALE_MIN, TOP_APP_STRIP_SPACING_SCALE_MAX,
                topAppStripSpacingScalePct, value -> saveTopAppStripSpacingScale(value));
        topAppStripSpacingValueView = (TextView) topSpacingItem.getTag();
        LinearLayout.LayoutParams topSpacingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        topSpacingLp.topMargin = dp(12);
        list.addView(topSpacingItem, topSpacingLp);

        LinearLayout topVerticalPaddingItem = createSliderSettingsItem("图标栏高度",
                TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MIN,
                TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MAX,
                topAppStripVerticalPaddingScalePct,
                value -> saveTopAppStripVerticalPaddingScale(value));
        topAppStripVerticalPaddingValueView = (TextView) topVerticalPaddingItem.getTag();
        LinearLayout.LayoutParams topVerticalPaddingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        topVerticalPaddingLp.topMargin = dp(12);
        list.addView(topVerticalPaddingItem, topVerticalPaddingLp);

        LinearLayout topComponentsVisibleItem = createSwitchSettingsItem(
                "顶部组件显示", topComponentsVisible, this::saveTopComponentsVisible);
        topComponentsVisibleValueView = (TextView) topComponentsVisibleItem.getTag();
        topComponentsVisibleSwitch = findSwitchInItem(topComponentsVisibleItem);
        LinearLayout.LayoutParams topComponentsVisibleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        topComponentsVisibleLp.topMargin = dp(12);
        list.addView(topComponentsVisibleItem, topComponentsVisibleLp);

        LinearLayout statusBarSpacingItem = createSwitchSettingsItem(
                "状态栏间距", statusBarSpacingEnabled, this::saveStatusBarSpacingEnabled);
        statusBarSpacingValueView = (TextView) statusBarSpacingItem.getTag();
        statusBarSpacingSwitch = findSwitchInItem(statusBarSpacingItem);
        LinearLayout.LayoutParams statusBarSpacingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        statusBarSpacingLp.topMargin = dp(12);
        list.addView(statusBarSpacingItem, statusBarSpacingLp);

        rootAuthorizationItem = createSettingsItem(
                "ROOT 权限", "必需授权才可正常使用", "未授权");
        rootAuthorizationValueView = (TextView) rootAuthorizationItem.getTag();
        rootAuthorizationItem.setOnClickListener(v -> requestRootAuthorization());
        LinearLayout.LayoutParams rootAuthorizationLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        rootAuthorizationLp.topMargin = dp(12);
        list.addView(rootAuthorizationItem, rootAuthorizationLp);

        LinearLayout zygiskHookStatusItem = createSettingsItem(
                "Zygisk状态", "开启后才可使用增强功能", "读取中");
        zygiskHookStatusValueView = (TextView) zygiskHookStatusItem.getTag();
        LinearLayout.LayoutParams zygiskStatusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(88));
        zygiskStatusLp.topMargin = dp(12);
        list.addView(zygiskHookStatusItem, zygiskStatusLp);

        LinearLayout secureWindowHookItem = createSwitchSettingsItem(
                "隐私窗口显示",
                "开启可正常显示隐私窗口（必需开启Zygisk）",
                secureWindowHookEnabled,
                enabled -> updateZygiskHookSettings(
                        enabled, statusBarOverlayHookEnabled));
        secureWindowHookValueView = (TextView) secureWindowHookItem.getTag();
        secureWindowHookSwitch = findSwitchInItem(secureWindowHookItem);
        LinearLayout.LayoutParams secureWindowHookLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104));
        secureWindowHookLp.topMargin = dp(12);
        list.addView(secureWindowHookItem, secureWindowHookLp);

        LinearLayout statusBarOverlayHookItem = createSwitchSettingsItem(
                "顶部间距去除",
                "开启可去除应用界面顶部的间距（必需开启Zygisk），部分应用无效",
                statusBarOverlayHookEnabled,
                enabled -> updateZygiskHookSettings(
                        secureWindowHookEnabled, enabled));
        statusBarOverlayHookValueView = (TextView) statusBarOverlayHookItem.getTag();
        statusBarOverlayHookSwitch = findSwitchInItem(statusBarOverlayHookItem);
        LinearLayout.LayoutParams statusBarOverlayHookLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(104));
        statusBarOverlayHookLp.topMargin = dp(12);
        list.addView(statusBarOverlayHookItem, statusBarOverlayHookLp);

        applyHookSettingsItem = createSettingsItem("应用 Hook 设置", "重启");
        applyHookSettingsItem.setOnClickListener(v -> showHookSettingsRebootDialog());
        LinearLayout.LayoutParams applyHookSettingsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        applyHookSettingsLp.topMargin = dp(12);
        list.addView(applyHookSettingsItem, applyHookSettingsLp);
        refreshRootAuthorizationView();
        refreshZygiskHookSettingsViews();

        LinearLayout legalNoticesItem = createSettingsItem("开源许可与第三方声明", "查看");
        legalNoticesItem.setOnClickListener(v -> showLegalNoticesPage());
        LinearLayout.LayoutParams legalNoticesLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        legalNoticesLp.topMargin = dp(12);
        list.addView(legalNoticesItem, legalNoticesLp);

        LinearLayout logRecordingItem = createSwitchSettingsItem(
                "记录日志", logRecordingEnabled, this::saveLogRecordingEnabled);
        logRecordingEnabledValueView = (TextView) logRecordingItem.getTag();
        logRecordingEnabledSwitch = findSwitchInItem(logRecordingItem);
        LinearLayout.LayoutParams logRecordingLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        logRecordingLp.topMargin = dp(12);
        list.addView(logRecordingItem, logRecordingLp);

        exportLogItem = createSettingsItem("导出日志", "导出");
        exportLogItem.setOnClickListener(v -> callbacks.exportSessionLog());
        exportLogItem.setVisibility(logRecordingEnabled ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams exportLogLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68));
        exportLogLp.topMargin = dp(12);
        list.addView(exportLogItem, exportLogLp);

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER_HORIZONTAL);
        footer.setBackgroundColor(Color.TRANSPARENT);
        footer.setPadding(0, dp(16), 0, dp(12));

        LinearLayout socialRow = new LinearLayout(activity);
        socialRow.setOrientation(LinearLayout.HORIZONTAL);
        socialRow.setGravity(Gravity.CENTER);
        socialRow.setBackgroundColor(Color.TRANSPARENT);
        footer.addView(socialRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        addSettingsSocialIcon(socialRow, R.drawable.settings_social_bilibili, "哔哩哔哩主页",
                BILIBILI_PROFILE_URL);
        addSettingsSocialIcon(socialRow, R.drawable.settings_social_douyin, "抖音主页",
                DOUYIN_PROFILE_URL);
        addSettingsSocialIcon(socialRow, R.drawable.settings_social_github, "GitHub 主页",
                GITHUB_PROFILE_URL);

        TextView designer = createSettingsFooterText(
                "Designed By SangLuo", 12, 0xff555555, true);
        LinearLayout.LayoutParams designerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        designerLp.topMargin = dp(14);
        footer.addView(designer, designerLp);

        TextView version = createSettingsFooterText(
                getAppVersionLabel(), 10, 0xff777777, false);
        LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        versionLp.topMargin = dp(5);
        footer.addView(version, versionLp);

        TextView copyright = createSettingsFooterText(
                "© 2026 SangLuo · Apache-2.0", 10, 0xff888888, false);
        LinearLayout.LayoutParams copyrightLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        copyrightLp.topMargin = dp(5);
        footer.addView(copyright, copyrightLp);

        TextView attribution = createSettingsFooterText(
                "One Step resources © 2016 The Smartisan Open Source Project.",
                9, 0xffaaaaaa, false);
        LinearLayout.LayoutParams attributionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        attributionLp.topMargin = dp(3);
        footer.addView(attribution, attributionLp);

        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        footerLp.topMargin = dp(12);
        list.addView(footer, footerLp);
        return page;
    }

    private FrameLayout createLegalNoticesPage() {
        FixedViewportFrameLayout page = new FixedViewportFrameLayout(activity);
        page.setBackgroundColor(0xffeeeeee);
        page.setClickable(true);
        FrameLayout viewport = page.getViewport();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xffeeeeee);
        viewport.addView(content, matchFrame());

        FrameLayout header = new FrameLayout(activity);
        header.setBackgroundColor(Color.WHITE);
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        ImageView back = createTopNavImageControl(
                R.drawable.top_nav_page_left, "返回设置");
        back.setColorFilter(0xff333333);
        back.setOnClickListener(v -> showSettingsFromLegalNotices());
        header.addView(back, new FrameLayout.LayoutParams(dp(40), dp(40),
                Gravity.START | Gravity.CENTER_VERTICAL));

        TextView title = new TextView(activity);
        title.setText("开源许可与第三方声明");
        title.setGravity(Gravity.CENTER);
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        setDpTextSize(title, 15);
        header.addView(title, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        content.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout sections = new LinearLayout(activity);
        sections.setOrientation(LinearLayout.VERTICAL);
        sections.setPadding(dp(14), dp(12), dp(14), dp(24));
        scrollView.addView(sections, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addLegalSection(sections, "OneStep4.0",
                getAppVersionLabel() + "\nCopyright 2026 SangLuo\nApache License 2.0",
                false);
        addLegalSection(sections, "项目声明",
                readLegalAsset("legal/NOTICE"), false);
        addLegalSection(sections, "第三方软件及资源",
                formatLegalMarkdown(readLegalAsset("legal/THIRD_PARTY_NOTICES.md")), false);
        addLegalSection(sections, "Apache License 2.0",
                readLegalAsset("legal/LICENSE"), true);
        return page;
    }

    private void showLegalNoticesPage() {
        if (internalSettingsPage == null
                || !(internalSettingsPage.getParent() instanceof OneStepWindowView)) {
            return;
        }
        OneStepWindowView host = (OneStepWindowView) internalSettingsPage.getParent();
        if (legalNoticesPage == null) {
            legalNoticesPage = createLegalNoticesPage();
        }
        host.hideInternalOverlay(internalSettingsPage);
        host.showInternalOverlay(legalNoticesPage);
    }

    private void showSettingsFromLegalNotices() {
        if (legalNoticesPage == null
                || !(legalNoticesPage.getParent() instanceof OneStepWindowView)) {
            return;
        }
        OneStepWindowView host = (OneStepWindowView) legalNoticesPage.getParent();
        host.hideInternalOverlay(legalNoticesPage);
        showInWindow(host);
    }

    private void addLegalSection(LinearLayout parent, String titleText, String bodyText,
                                 boolean monospace) {
        LinearLayout section = new LinearLayout(activity);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(16), dp(14), dp(16), dp(16));
        section.setBackground(makePanelBackground(Color.WHITE, 0x10000000, dp(4)));

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        setDpTextSize(title, 15);
        section.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(activity);
        body.setText(bodyText);
        body.setTextColor(0xff555555);
        body.setLineSpacing(0f, 1.18f);
        body.setTextIsSelectable(true);
        body.setAutoLinkMask(Linkify.WEB_URLS);
        body.setLinkTextColor(0xff2f6fcb);
        body.setMovementMethod(LinkMovementMethod.getInstance());
        if (monospace) {
            body.setTypeface(Typeface.MONOSPACE);
            setDpTextSize(body, 9.5f);
        } else {
            setDpTextSize(body, 11);
        }
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(8);
        section.addView(body, bodyLp);

        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) {
            sectionLp.topMargin = dp(12);
        }
        parent.addView(section, sectionLp);
    }

    private String readLegalAsset(String path) {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                activity.getAssets().open(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(line);
            }
        } catch (IOException e) {
            return "许可文件未随应用打包，请查看项目源代码仓库。";
        }
        return text.toString();
    }

    private String formatLegalMarkdown(String markdown) {
        StringBuilder text = new StringBuilder();
        String[] lines = markdown.split("\\n", -1);
        for (String line : lines) {
            String formatted = line.trim();
            while (formatted.startsWith("#")) {
                formatted = formatted.substring(1).trim();
            }
            if (formatted.startsWith("- ")) {
                formatted = "• " + formatted.substring(2);
            }
            formatted = formatted.replace("`", "");
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(formatted);
        }
        return text.toString().trim();
    }

    private void hideOverlay(FrameLayout overlay) {
        if (overlay == null) {
            return;
        }
        ViewParent parent = overlay.getParent();
        if (parent instanceof OneStepWindowView) {
            ((OneStepWindowView) parent).hideInternalOverlay(overlay);
        } else if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(overlay);
        }
    }

    private boolean isOverlayAttached(FrameLayout overlay) {
        return overlay != null && overlay.getParent() != null;
    }

    private boolean isOverlayInWindow(FrameLayout overlay, OneStepWindowView windowView) {
        return overlay != null && overlay.getParent() == windowView;
    }

    private void addSettingsSocialIcon(LinearLayout row, int iconResId,
                                       String description, String url) {
        ImageView iconView = new ImageView(activity);
        iconView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable circleBackground = new GradientDrawable();
        circleBackground.setShape(GradientDrawable.OVAL);
        circleBackground.setColor(Color.WHITE);
        iconView.setBackground(circleBackground);
        iconView.setClipToOutline(true);
        iconView.setContentDescription(description);
        iconView.setClickable(true);
        iconView.setFocusable(true);
        iconView.setImageResource(iconResId);
        iconView.setOnClickListener(v -> openExternalLink(url));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        iconLp.leftMargin = dp(10);
        iconLp.rightMargin = dp(10);
        row.addView(iconView, iconLp);
    }

    private TextView createSettingsFooterText(String text, float sizeDp, int color,
                                              boolean bold) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(color);
        view.setSingleLine(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        setDpTextSize(view, sizeDp);
        return view;
    }

    private String getAppVersionLabel() {
        String versionName = "1.0.0";
        try {
            String configuredVersion = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0).versionName;
            if (!TextUtils.isEmpty(configuredVersion)) {
                versionName = configuredVersion;
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return "OneStep4 · V" + versionName;
    }

    private void openExternalLink(String url) {
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(activity, "链接暂未设置", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, "找不到可打开此链接的应用", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout createBackgroundSettingsItem() {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(16), dp(14), dp(16), dp(16));
        item.setBackground(makePanelBackground(Color.WHITE, 0x00000000, dp(6)));

        TextView title = new TextView(activity);
        title.setText("背景设置");
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        setDpTextSize(title, 15);
        item.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return item;
    }

    private LinearLayout createSettingsItem(String titleText, String valueText) {
        return createSettingsItem(titleText, null, valueText);
    }

    private LinearLayout createSettingsItem(String titleText, String subtitleText,
                                            String valueText) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), dp(12), dp(16), dp(12));
        item.setBackground(makePanelBackground(Color.WHITE, 0x00000000, dp(6)));

        item.addView(createSettingsLabel(titleText, subtitleText),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(activity);
        value.setText(valueText);
        value.setTextColor(0xff777777);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        setDpTextSize(value, 13);
        item.addView(value, new LinearLayout.LayoutParams(dp(84),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setTag(value);
        return item;
    }

    private LinearLayout createSliderSettingsItem(String titleText, int min, int max, int current,
                                                  SliderSettingChangeListener listener) {
        return createSliderSettingsItem(titleText, min, max, current, listener,
                this::formatPercentValue, null);
    }

    private LinearLayout createSliderSettingsItem(String titleText, int min, int max, int current,
                                                  SliderSettingChangeListener listener,
                                                  SliderSettingValueFormatter formatter,
                                                  Runnable interactionListener) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setClipChildren(false);
        item.setClipToPadding(false);
        item.setPadding(dp(18), dp(10), dp(18), dp(8));
        item.setBackground(makePanelBackground(Color.WHITE, 0x00000000, dp(6)));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false);
        item.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        setDpTextSize(title, 15);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(activity);
        value.setText(formatter.format(current));
        value.setTextColor(0xff777777);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        setDpTextSize(value, 13);
        header.addView(value, new LinearLayout.LayoutParams(dp(72),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        SeekBar seekBar = new SeekBar(activity);
        seekBar.setMax(Math.max(0, max - min));
        seekBar.setProgress(clamp(current, min, max) - min);
        seekBar.setPadding(dp(10), 0, dp(10), 0);
        applyBlueSeekBarTint(seekBar);
        SliderUpdateThrottler updateThrottler = new SliderUpdateThrottler(listener,
                interactionListener);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int valueDp = min + progress;
                value.setText(formatter.format(valueDp));
                if (fromUser) {
                    updateThrottler.schedule(seekBar, valueDp);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (interactionListener != null) {
                    interactionListener.run();
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateThrottler.flush(seekBar, min + seekBar.getProgress());
            }
        });
        item.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
        item.setTag(value);
        return item;
    }

    private LinearLayout createSwitchSettingsItem(String titleText, boolean checked,
                                                  SwitchSettingChangeListener listener) {
        return createSwitchSettingsItem(titleText, null, checked, listener);
    }

    private LinearLayout createSwitchSettingsItem(String titleText, String subtitleText,
                                                  boolean checked,
                                                  SwitchSettingChangeListener listener) {
        LinearLayout item = new LinearLayout(activity);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(16), dp(12), dp(16), dp(12));
        item.setBackground(makePanelBackground(Color.WHITE, 0x00000000, dp(6)));

        item.addView(createSettingsLabel(titleText, subtitleText),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(activity);
        value.setText(formatSwitchValue(checked));
        value.setTextColor(0xff777777);
        value.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        value.setSingleLine(true);
        setDpTextSize(value, 13);
        item.addView(value, new LinearLayout.LayoutParams(dp(64),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Switch toggle = new Switch(activity);
        toggle.setChecked(checked);
        applyBlueSwitchTint(toggle);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onChanged(isChecked));
        item.addView(toggle, new LinearLayout.LayoutParams(dp(58),
                ViewGroup.LayoutParams.WRAP_CONTENT));
        item.setTag(value);
        return item;
    }

    private LinearLayout createSettingsLabel(String titleText, String subtitleText) {
        LinearLayout label = new LinearLayout(activity);
        label.setOrientation(LinearLayout.VERTICAL);
        label.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(titleText);
        title.setTextColor(0xff222222);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        setDpTextSize(title, 15);
        label.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!TextUtils.isEmpty(subtitleText)) {
            TextView subtitle = new TextView(activity);
            subtitle.setText(subtitleText);
            subtitle.setTextColor(0xff888888);
            subtitle.setMaxLines(3);
            subtitle.setLineSpacing(0f, 1.08f);
            setDpTextSize(subtitle, 10.5f);
            LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            subtitleLp.topMargin = dp(3);
            label.addView(subtitle, subtitleLp);
        }
        return label;
    }

    private Switch findSwitchInItem(LinearLayout item) {
        for (int index = 0; index < item.getChildCount(); index++) {
            View child = item.getChildAt(index);
            if (child instanceof Switch) {
                return (Switch) child;
            }
        }
        return null;
    }

    private String formatPercentValue(int valuePct) {
        return valuePct + "%";
    }

    private String formatSwitchValue(boolean enabled) {
        return enabled ? "已开启" : "已关闭";
    }

    private void applyBlueSeekBarTint(SeekBar seekBar) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        seekBar.setProgressTintList(ColorStateList.valueOf(0xff2f80ff));
        seekBar.setThumbTintList(ColorStateList.valueOf(0xff2f80ff));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(0xffd6dde8));
        seekBar.setSecondaryProgressTintList(ColorStateList.valueOf(0xffd6dde8));
    }

    private void applyBlueSwitchTint(Switch toggle) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        toggle.setTrackTintList(new ColorStateList(states, new int[]{
                0xff6aa7ff,
                0xffcfd5df
        }));
        toggle.setThumbTintList(new ColorStateList(states, new int[]{
                Color.WHITE,
                Color.WHITE
        }));
    }

    private interface SliderSettingChangeListener {
        void onChanged(int value);
    }

    private interface SliderSettingValueFormatter {
        String format(int value);
    }

    /** Coalesces costly setting updates while keeping slider changes visible during a drag. */
    private static final class SliderUpdateThrottler {
        private final SliderSettingChangeListener listener;
        private final Runnable interactionListener;
        private final Runnable dispatchRunnable = this::dispatchPendingValue;
        private int pendingValue;
        private int lastDispatchedValue = Integer.MIN_VALUE;
        private long lastDispatchUptimeMillis;
        private boolean dispatchScheduled;

        SliderUpdateThrottler(SliderSettingChangeListener listener,
                              Runnable interactionListener) {
            this.listener = listener;
            this.interactionListener = interactionListener;
        }

        void schedule(SeekBar seekBar, int value) {
            pendingValue = value;
            if (dispatchScheduled) {
                return;
            }
            long elapsed = SystemClock.uptimeMillis() - lastDispatchUptimeMillis;
            long delay = Math.max(0L, SLIDER_LIVE_UPDATE_INTERVAL_MS - elapsed);
            dispatchScheduled = true;
            seekBar.postDelayed(dispatchRunnable, delay);
        }

        void flush(SeekBar seekBar, int value) {
            pendingValue = value;
            if (dispatchScheduled) {
                seekBar.removeCallbacks(dispatchRunnable);
                dispatchScheduled = false;
            }
            dispatchPendingValue();
        }

        private void dispatchPendingValue() {
            dispatchScheduled = false;
            if (pendingValue == lastDispatchedValue) {
                return;
            }
            lastDispatchedValue = pendingValue;
            lastDispatchUptimeMillis = SystemClock.uptimeMillis();
            listener.onChanged(pendingValue);
            if (interactionListener != null) {
                interactionListener.run();
            }
        }
    }

    private interface SwitchSettingChangeListener {
        void onChanged(boolean checked);
    }

    public void refresh() {
        syncState();
        if (settingsBackgroundPreview != null) {
            applyCurrentListBackground(settingsBackgroundPreview);
        }
        if (gridLayoutValueView != null) {
            gridLayoutValueView.setText(getGridLayoutLabel());
        }
        if (oneStepTriggerAreaValueView != null) {
            oneStepTriggerAreaValueView.setText(
                    formatPercentValue(oneStepTriggerAreaScalePct));
        }
        if (cornerTriggerSensitivityValueView != null) {
            cornerTriggerSensitivityValueView.setText(
                    formatPercentValue(cornerTriggerSensitivityPct));
        }
        if (topAppIconSizeValueView != null) {
            topAppIconSizeValueView.setText(formatPercentValue(topAppIconScalePct));
        }
        if (topAppStripSpacingValueView != null) {
            topAppStripSpacingValueView.setText(formatPercentValue(topAppStripSpacingScalePct));
        }
        if (topAppStripVerticalPaddingValueView != null) {
            topAppStripVerticalPaddingValueView.setText(
                    formatPercentValue(topAppStripVerticalPaddingScalePct));
        }
        if (topComponentsVisibleValueView != null) {
            topComponentsVisibleValueView.setText(formatSwitchValue(topComponentsVisible));
        }
        if (topComponentsVisibleSwitch != null
                && topComponentsVisibleSwitch.isChecked() != topComponentsVisible) {
            topComponentsVisibleSwitch.setChecked(topComponentsVisible);
        }
        if (statusBarSpacingValueView != null) {
            statusBarSpacingValueView.setText(formatSwitchValue(statusBarSpacingEnabled));
        }
        if (statusBarSpacingSwitch != null
                && statusBarSpacingSwitch.isChecked() != statusBarSpacingEnabled) {
            statusBarSpacingSwitch.setChecked(statusBarSpacingEnabled);
        }
        if (verticalWindowLayoutValueView != null) {
            verticalWindowLayoutValueView.setText(formatSwitchValue(verticalWindowLayout));
        }
        if (verticalWindowLayoutSwitch != null
                && verticalWindowLayoutSwitch.isChecked() != verticalWindowLayout) {
            verticalWindowLayoutSwitch.setChecked(verticalWindowLayout);
        }
        if (logRecordingEnabledValueView != null) {
            logRecordingEnabledValueView.setText(formatSwitchValue(logRecordingEnabled));
        }
        if (logRecordingEnabledSwitch != null
                && logRecordingEnabledSwitch.isChecked() != logRecordingEnabled) {
            logRecordingEnabledSwitch.setChecked(logRecordingEnabled);
        }
        if (exportLogItem != null) {
            exportLogItem.setVisibility(logRecordingEnabled ? View.VISIBLE : View.GONE);
        }
        if (sideWindowCountValueView != null) {
            sideWindowCountValueView.setText(getSideWindowCountLabel());
        }
        if (topNavVerticalMarginValueView != null) {
            topNavVerticalMarginValueView.setText(
                    formatPercentValue(topNavVerticalMarginScalePct));
        }
        refreshRootAuthorizationView();
        refreshZygiskHookSettingsViews();
    }

    private void prepareZygiskHookSettings() {
        rootAuthorizationGranted = callbacks.rootAuthorizationGranted();
        refreshRootAuthorizationView();
        if (rootAuthorizationGranted) {
            loadZygiskHookSettings();
            return;
        }
        ++hookSettingsRequestGeneration;
        hookSettingsLoaded = false;
        hookSettingsSaving = false;
        hookSettingsReadFailed = false;
        hookSettingsNeedsRoot = true;
        refreshZygiskHookSettingsViews();
    }

    private void requestRootAuthorization() {
        if (rootAuthorizationRequestInFlight) {
            return;
        }
        if (callbacks.rootAuthorizationGranted()) {
            rootAuthorizationGranted = true;
            refreshRootAuthorizationView();
            Toast.makeText(activity, "ROOT 权限已授权，无需重复授权",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        rootAuthorizationGranted = false;
        rootAuthorizationRequestInFlight = true;
        refreshRootAuthorizationView();
        callbacks.requestRootAuthorization(result -> activity.runOnUiThread(() -> {
            rootAuthorizationRequestInFlight = false;
            rootAuthorizationGranted = result == RootAuthorizationResult.GRANTED;
            refreshRootAuthorizationView();
            if (rootAuthorizationGranted) {
                Toast.makeText(activity, "ROOT 权限已授权", Toast.LENGTH_SHORT).show();
                loadZygiskHookSettings();
            } else if (result == RootAuthorizationResult.KERNEL_SU_ACTION_REQUIRED) {
                hookSettingsNeedsRoot = true;
                refreshZygiskHookSettingsViews();
                showKernelSuAuthorizationDialog();
            } else {
                hookSettingsNeedsRoot = true;
                refreshZygiskHookSettingsViews();
                Toast.makeText(activity, "ROOT 权限请求失败", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void showKernelSuAuthorizationDialog() {
        new AlertDialog.Builder(activity)
                .setTitle("在 KernelSU 中授权")
                .setMessage("KernelSU 不支持弹窗授权。请打开 KernelSU 后进入“超级用户”，"
                        + "搜索“One Step”并打开“超级用户”开关。"
                        + "授权后请清除后台并重新打开 OneStep。"
                        + "若仍失败，请确认 KernelSU 设置中的“传统 SU 命令支持”已启用。")
                .setNegativeButton("取消", null)
                .setPositiveButton("打开 KernelSU", (dialog, which) -> {
                    if (!callbacks.openKernelSuManager()) {
                        Toast.makeText(activity, "无法打开 KernelSU 管理器",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void refreshRootAuthorizationView() {
        if (rootAuthorizationValueView != null) {
            rootAuthorizationValueView.setText(rootAuthorizationRequestInFlight
                    ? "请求中" : rootAuthorizationGranted ? "已授权" : "未授权");
        }
        if (rootAuthorizationItem != null) {
            rootAuthorizationItem.setEnabled(!rootAuthorizationRequestInFlight);
            rootAuthorizationItem.setAlpha(rootAuthorizationRequestInFlight ? 0.5f : 1f);
        }
    }

    private void loadZygiskHookSettings() {
        int requestGeneration = ++hookSettingsRequestGeneration;
        hookSettingsLoaded = false;
        hookSettingsSaving = false;
        hookSettingsReadFailed = false;
        hookSettingsNeedsRoot = false;
        refreshZygiskHookSettingsViews();
        callbacks.loadZygiskHookSettings((state, error) -> activity.runOnUiThread(() -> {
            if (requestGeneration != hookSettingsRequestGeneration) {
                return;
            }
            if (state == null) {
                hookSettingsLoaded = false;
                hookSettingsSaving = false;
                hookSettingsReadFailed = true;
                rootAuthorizationGranted = callbacks.rootAuthorizationGranted();
                hookSettingsNeedsRoot = !rootAuthorizationGranted;
                refreshRootAuthorizationView();
                refreshZygiskHookSettingsViews();
                Toast.makeText(activity, TextUtils.isEmpty(error)
                                ? "读取 Hook 设置失败" : error,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            applyZygiskHookState(state);
            hookSettingsLoaded = true;
            hookSettingsSaving = false;
            hookSettingsReadFailed = false;
            hookSettingsNeedsRoot = false;
            refreshZygiskHookSettingsViews();
        }));
    }

    private void updateZygiskHookSettings(boolean secureWindowEnabled,
                                          boolean statusBarOverlayEnabled) {
        if (updatingHookSwitches || !hookSettingsLoaded || hookSettingsSaving) {
            return;
        }
        boolean previousSecureWindowEnabled = this.secureWindowHookEnabled;
        boolean previousStatusBarOverlayEnabled = this.statusBarOverlayHookEnabled;
        this.secureWindowHookEnabled = secureWindowEnabled;
        this.statusBarOverlayHookEnabled = statusBarOverlayEnabled;
        hookSettingsSaving = true;
        int requestGeneration = ++hookSettingsRequestGeneration;
        refreshZygiskHookSettingsViews();
        callbacks.saveZygiskHookSettings(
                secureWindowEnabled,
                statusBarOverlayEnabled,
                (state, error) -> activity.runOnUiThread(() -> {
                    if (requestGeneration != hookSettingsRequestGeneration) {
                        return;
                    }
                    hookSettingsSaving = false;
                    if (state == null) {
                        this.secureWindowHookEnabled = previousSecureWindowEnabled;
                        this.statusBarOverlayHookEnabled = previousStatusBarOverlayEnabled;
                        rootAuthorizationGranted = callbacks.rootAuthorizationGranted();
                        hookSettingsNeedsRoot = !rootAuthorizationGranted;
                        refreshRootAuthorizationView();
                        refreshZygiskHookSettingsViews();
                        Toast.makeText(activity, TextUtils.isEmpty(error)
                                        ? "保存 Hook 设置失败" : error,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    applyZygiskHookState(state);
                    hookSettingsLoaded = true;
                    hookSettingsReadFailed = false;
                    hookSettingsNeedsRoot = false;
                    refreshZygiskHookSettingsViews();
                    Toast.makeText(activity, "已保存，重启后生效",
                            Toast.LENGTH_SHORT).show();
                }));
    }

    private void applyZygiskHookState(ZygiskHookConfig.State state) {
        secureWindowHookEnabled = state.secureWindowEnabled;
        statusBarOverlayHookEnabled = state.statusBarOverlayEnabled;
        zygiskModuleInstalled = state.moduleInstalled;
        zygiskPayloadActive = state.zygiskPayloadActive;
        lsposedInstalled = state.lsposedInstalled;
        lsposedBackendActive = state.lsposedBackendActive;
        standaloneBackendActive = state.standaloneBackendActive;
    }

    private void refreshZygiskHookSettingsViews() {
        if (zygiskHookStatusValueView != null) {
            if (hookSettingsSaving) {
                zygiskHookStatusValueView.setText("保存中");
            } else if (hookSettingsNeedsRoot) {
                zygiskHookStatusValueView.setText("需要 ROOT");
            } else if (hookSettingsReadFailed) {
                zygiskHookStatusValueView.setText("读取失败");
            } else if (!hookSettingsLoaded) {
                zygiskHookStatusValueView.setText("读取中");
            } else if (!zygiskModuleInstalled) {
                zygiskHookStatusValueView.setText("模块不可用");
            } else if (lsposedBackendActive) {
                zygiskHookStatusValueView.setText("LSPosed/Vector 已启用");
            } else if (lsposedInstalled) {
                zygiskHookStatusValueView.setText("LSPosed/Vector 待配置");
            } else if (standaloneBackendActive) {
                zygiskHookStatusValueView.setText("已启用");
            } else {
                zygiskHookStatusValueView.setText(
                        zygiskPayloadActive ? "待生效" : "未启用");
            }
        }
        if (secureWindowHookValueView != null) {
            secureWindowHookValueView.setText(formatSwitchValue(secureWindowHookEnabled));
        }
        if (statusBarOverlayHookValueView != null) {
            statusBarOverlayHookValueView.setText(
                    formatSwitchValue(statusBarOverlayHookEnabled));
        }
        boolean controlsEnabled = hookSettingsLoaded
                && zygiskModuleInstalled && !hookSettingsSaving;
        updatingHookSwitches = true;
        try {
            updateHookSwitch(secureWindowHookSwitch, secureWindowHookEnabled, controlsEnabled);
            updateHookSwitch(statusBarOverlayHookSwitch,
                    statusBarOverlayHookEnabled, controlsEnabled);
        } finally {
            updatingHookSwitches = false;
        }
        if (applyHookSettingsItem != null) {
            applyHookSettingsItem.setEnabled(controlsEnabled);
            applyHookSettingsItem.setAlpha(controlsEnabled ? 1f : 0.5f);
        }
    }

    private void updateHookSwitch(Switch toggle, boolean checked, boolean enabled) {
        if (toggle == null) {
            return;
        }
        if (toggle.isChecked() != checked) {
            toggle.setChecked(checked);
        }
        toggle.setEnabled(enabled);
    }

    private void showHookSettingsRebootDialog() {
        if (!hookSettingsLoaded || hookSettingsSaving) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("重启设备")
                .setMessage("Hook 设置将在重启后生效。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("立即重启", (dialog, which) -> {
                    Toast.makeText(activity, "正在重启", Toast.LENGTH_SHORT).show();
                    callbacks.rebootDevice();
                })
                .show();
    }

    private String getGridLayoutLabel() {
        return desktopGridRows + "x" + desktopGridColumns;
    }

    private String getSideWindowCountLabel() {
        return sideWindowCount + "个";
    }

    private void showGridLayoutDialog() {
        String[] labels = {"4x3", "5x4", "6x5"};
        int checked = desktopGridRows == 5 ? 1 : desktopGridRows == 6 ? 2 : 0;
        new AlertDialog.Builder(activity)
                .setTitle("图标布局设置")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which == 1) {
                        saveGridLayout(5, 4);
                    } else if (which == 2) {
                        saveGridLayout(6, 5);
                    } else {
                        saveGridLayout(4, 3);
                    }
                    dialog.dismiss();
                })
                .show();
    }

    private void showSideWindowCountDialog() {
        String[] labels = {"3个", "4个", "5个", "6个"};
        int checked = sanitizeSideWindowCount(sideWindowCount) - MIN_SIDE_WINDOWS;
        new AlertDialog.Builder(activity)
                .setTitle("小窗口数量")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int count = MIN_SIDE_WINDOWS + which;
                    if (!canUseSideWindowCount(count)) {
                        Toast.makeText(activity, "不支持该小窗口数量",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveSideWindowCount(count);
                    dialog.dismiss();
                })
                .show();
    }


    private void syncState() {
        desktopGridRows = callbacks.desktopGridRows();
        desktopGridColumns = callbacks.desktopGridColumns();
        oneStepTriggerAreaScalePct = callbacks.oneStepTriggerAreaScalePct();
        cornerTriggerSensitivityPct = callbacks.cornerTriggerSensitivityPct();
        topNavVerticalMarginScalePct = callbacks.topNavVerticalMarginScalePct();
        topAppIconScalePct = callbacks.topAppIconScalePct();
        topAppStripSpacingScalePct = callbacks.topAppStripSpacingScalePct();
        topAppStripVerticalPaddingScalePct = callbacks.topAppStripVerticalPaddingScalePct();
        topComponentsVisible = callbacks.topComponentsVisible();
        statusBarSpacingEnabled = callbacks.statusBarSpacingEnabled();
        verticalWindowLayout = callbacks.verticalWindowLayout();
        logRecordingEnabled = callbacks.logRecordingEnabled();
        sideWindowCount = callbacks.sideWindowCount();
        rootAuthorizationGranted = callbacks.rootAuthorizationGranted();
    }
    private void saveGridLayout(int r,int c){callbacks.saveGridLayout(r,c);refresh();}
    private void saveOneStepTriggerAreaScale(int v){callbacks.saveOneStepTriggerAreaScale(v);}
    private void saveCornerTriggerSensitivity(int v){callbacks.saveCornerTriggerSensitivity(v);}
    private void saveTopNavVerticalMarginScale(int v){callbacks.saveTopNavVerticalMarginScale(v);}
    private void saveTopAppIconScale(int v){callbacks.saveTopAppIconScale(v);}
    private void saveTopAppStripSpacingScale(int v){callbacks.saveTopAppStripSpacingScale(v);}
    private void saveTopAppStripVerticalPaddingScale(int v){callbacks.saveTopAppStripVerticalPaddingScale(v);}
    private void saveTopComponentsVisible(boolean v){callbacks.saveTopComponentsVisible(v);}
    private void saveStatusBarSpacingEnabled(boolean v){callbacks.saveStatusBarSpacingEnabled(v);}
    private void saveVerticalWindowLayout(boolean v){callbacks.saveVerticalWindowLayout(v);}
    private void saveLogRecordingEnabled(boolean v){callbacks.saveLogRecordingEnabled(v);}
    private void saveSideWindowCount(int v){callbacks.saveSideWindowCount(v);refresh();}
    private void pickBackgroundFromGallery(){callbacks.pickBackground();}
    private void applyCurrentListBackground(ImageView target){callbacks.applyBackground(target);}
    private void showCornerTriggerPreview(){callbacks.previewCornerTrigger();}
    private ImageView createTopNavImageControl(int res,String description){return callbacks.navigationIcon(res,description);}
    private GradientDrawable makePanelBackground(int fill,int stroke,float radius){return callbacks.panelBackground(fill,stroke,radius);}
    private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private static FrameLayout.LayoutParams matchFrame(){return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);}
    private static void setDpTextSize(TextView view,float value){view.setTextSize(TypedValue.COMPLEX_UNIT_DIP,value);}
}
