package com.sangluo.onestep.data.settings;

/** Validated snapshot of user-configurable OneStep layout settings. */
public final class OneStepSettings {
    public static final int MIN_SIDE_WINDOWS = 3;
    public static final int DEFAULT_SIDE_WINDOWS = 3;
    public static final int MAX_SIDE_WINDOWS = 6;
    public static final int DESKTOP_PAGE_ROWS = 4;
    public static final int DESKTOP_PAGE_COLUMNS = 3;
    public static final int TOP_NAV_HEIGHT_DEFAULT_DP = 66;
    public static final int TOP_NAV_CONTENT_HEIGHT_DP = 42;
    public static final int TOP_NAV_VERTICAL_MARGIN_DEFAULT_DP = 12;
    public static final int TOP_APP_ICON_SIZE_DEFAULT_DP = 46;
    public static final int TOP_APP_ICON_SCALE_DEFAULT = 100;
    public static final int TOP_APP_ICON_SCALE_MIN = 60;
    public static final int TOP_APP_ICON_SCALE_MAX = 120;
    public static final int TOP_APP_STRIP_SPACING_SCALE_DEFAULT = 100;
    public static final int TOP_APP_STRIP_SPACING_SCALE_MIN = 50;
    public static final int TOP_APP_STRIP_SPACING_SCALE_MAX = 120;
    public static final int TOP_APP_STRIP_VERTICAL_PADDING_SCALE_DEFAULT = 100;
    public static final int TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MIN = 40;
    public static final int TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MAX = 120;
    public static final int TOP_NAV_VERTICAL_MARGIN_SCALE_DEFAULT = 100;
    public static final int TOP_NAV_VERTICAL_MARGIN_SCALE_MIN = 40;
    public static final int TOP_NAV_VERTICAL_MARGIN_SCALE_MAX = 120;
    // Kept for source compatibility with the legacy host implementations.
    public static final int TOP_NAV_HEIGHT_SCALE_DEFAULT = 100;
    public static final int TOP_NAV_HEIGHT_SCALE_MIN = 40;
    public static final int TOP_NAV_HEIGHT_SCALE_MAX = 120;
    public static final int CORNER_TRIGGER_SIZE_DEFAULT_DP = 156;
    public static final int CORNER_TRIGGER_SIZE_MIN_DP = 72;
    public static final int CORNER_TRIGGER_SIZE_MAX_DP = 180;
    public static final int ONE_STEP_TRIGGER_AREA_SCALE_DEFAULT = 100;
    public static final int ONE_STEP_TRIGGER_AREA_SCALE_MIN = 50;
    public static final int ONE_STEP_TRIGGER_AREA_SCALE_MAX = 120;
    public static final int CORNER_TRIGGER_SENSITIVITY_DEFAULT = 100;
    public static final int CORNER_TRIGGER_SENSITIVITY_MIN = 60;
    public static final int CORNER_TRIGGER_SENSITIVITY_MAX = 160;

    public final int desktopGridRows;
    public final int desktopGridColumns;
    public final int topAppIconScalePct;
    public final int topAppStripSpacingScalePct;
    public final int topAppStripVerticalPaddingScalePct;
    public final boolean topComponentsVisible;
    public final boolean verticalWindowLayout;
    public final int sideWindowCount;
    public final int topNavVerticalMarginScalePct;
    public final int oneStepTriggerAreaScalePct;
    public final int cornerTriggerSensitivityPct;
    public final boolean logRecordingEnabled;

    public OneStepSettings(int desktopGridRows, int desktopGridColumns,
                           int topAppIconScalePct, int topAppStripSpacingScalePct,
                           int topAppStripVerticalPaddingScalePct, boolean topComponentsVisible,
                           boolean verticalWindowLayout, int sideWindowCount,
                           int topNavVerticalMarginScalePct, int oneStepTriggerAreaScalePct,
                           int cornerTriggerSensitivityPct, boolean logRecordingEnabled) {
        this.desktopGridRows = desktopGridRows;
        this.desktopGridColumns = desktopGridColumns;
        this.topAppIconScalePct = topAppIconScalePct;
        this.topAppStripSpacingScalePct = topAppStripSpacingScalePct;
        this.topAppStripVerticalPaddingScalePct = topAppStripVerticalPaddingScalePct;
        this.topComponentsVisible = topComponentsVisible;
        this.verticalWindowLayout = verticalWindowLayout;
        this.sideWindowCount = sideWindowCount;
        this.topNavVerticalMarginScalePct = topNavVerticalMarginScalePct;
        this.oneStepTriggerAreaScalePct = oneStepTriggerAreaScalePct;
        this.cornerTriggerSensitivityPct = cornerTriggerSensitivityPct;
        this.logRecordingEnabled = logRecordingEnabled;
    }

    public static int sanitizeGridRows(int rows) {
        return rows == 5 || rows == 6 ? rows : DESKTOP_PAGE_ROWS;
    }

    public static int sanitizeGridColumns(int columns) {
        return columns == 4 || columns == 5 ? columns : DESKTOP_PAGE_COLUMNS;
    }

    public static int sanitizeTopAppIconScale(int scale) {
        return clamp(scale, TOP_APP_ICON_SCALE_MIN, TOP_APP_ICON_SCALE_MAX);
    }

    public static int sanitizeTopAppStripSpacingScale(int scale) {
        return clamp(scale, TOP_APP_STRIP_SPACING_SCALE_MIN,
                TOP_APP_STRIP_SPACING_SCALE_MAX);
    }

    public static int sanitizeTopAppStripVerticalPaddingScale(int scale) {
        return clamp(scale, TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MIN,
                TOP_APP_STRIP_VERTICAL_PADDING_SCALE_MAX);
    }

    public static int sanitizeTopNavVerticalMarginScale(int scale) {
        return clamp(scale, TOP_NAV_VERTICAL_MARGIN_SCALE_MIN,
                TOP_NAV_VERTICAL_MARGIN_SCALE_MAX);
    }

    public static int sanitizeOneStepTriggerAreaScale(int scale) {
        return clamp(scale, ONE_STEP_TRIGGER_AREA_SCALE_MIN,
                ONE_STEP_TRIGGER_AREA_SCALE_MAX);
    }

    public static int oneStepTriggerAreaSizeDp(int scalePct) {
        return Math.max(1, Math.round(CORNER_TRIGGER_SIZE_DEFAULT_DP
                * sanitizeOneStepTriggerAreaScale(scalePct) / 100f));
    }

    public static int oneStepTriggerAreaScaleForSizeDp(int sizeDp) {
        return sanitizeOneStepTriggerAreaScale(Math.round(
                Math.max(1, sizeDp) * 100f / CORNER_TRIGGER_SIZE_DEFAULT_DP));
    }

    public static int sanitizeCornerTriggerSensitivity(int sensitivityPct) {
        return clamp(sensitivityPct, CORNER_TRIGGER_SENSITIVITY_MIN,
                CORNER_TRIGGER_SENSITIVITY_MAX);
    }

    public static int sanitizeSideWindowCount(int count) {
        return clamp(count, MIN_SIDE_WINDOWS, MAX_SIDE_WINDOWS);
    }

    public static int sanitizeAllowedSideWindowCount(int count) {
        int sanitized = sanitizeSideWindowCount(count);
        return canUseSideWindowCount(sanitized) ? sanitized : DEFAULT_SIDE_WINDOWS;
    }

    public static boolean canUseSideWindowCount(int count) {
        return count >= MIN_SIDE_WINDOWS && count <= MAX_SIDE_WINDOWS;
    }

    public static boolean isSupportedGridLayout(int rows, int columns) {
        return (rows == 4 && columns == 3)
                || (rows == 5 && columns == 4)
                || (rows == 6 && columns == 5);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
