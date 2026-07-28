package com.sangluo.onestep.data.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Owns preference keys, legacy migrations, validation, and persistence. */
public final class OneStepSettingsStore {
    private static final String PREFS_NAME = "onestep_settings";
    private static final String PREF_BACKGROUND_URI = "background_uri";
    private static final String PREF_GRID_ROWS = "grid_rows";
    private static final String PREF_GRID_COLUMNS = "grid_columns";
    private static final String PREF_TOP_APP_ICON_SCALE = "top_app_icon_scale";
    private static final String PREF_TOP_APP_ICON_SIZE_LEGACY = "top_app_icon_size";
    private static final String PREF_TOP_APP_STRIP_SPACING_SCALE =
            "top_app_strip_spacing_scale";
    private static final String PREF_TOP_APP_STRIP_VERTICAL_PADDING_SCALE =
            "top_app_strip_vertical_padding_scale";
    private static final String PREF_TOP_COMPONENTS_VISIBLE = "top_components_visible";
    private static final String PREF_MEDIA_PLAYER_VISIBLE_LEGACY = "media_player_visible";
    private static final String PREF_SENSOR_UID_OVERRIDES = "sensor_uid_overrides";
    private static final String PREF_ONE_STEP_TRIGGER_AREA_SCALE =
            "one_step_trigger_area_scale";
    private static final String PREF_CORNER_TRIGGER_SIZE_DP = "corner_trigger_size_dp";
    private static final String PREF_CORNER_TRIGGER_SENSITIVITY =
            "corner_trigger_sensitivity";
    private static final String PREF_MEDIA_PLAYER_DEFAULT_APPLIED_LEGACY =
            "media_player_default_applied";
    private static final String PREF_VERTICAL_WINDOW_LAYOUT = "vertical_window_layout";
    private static final String PREF_SIDE_WINDOW_COUNT = "side_window_count";
    private static final String PREF_TOP_NAV_VERTICAL_MARGIN_SCALE =
            "top_nav_vertical_margin_scale";
    private static final String PREF_TOP_NAV_HEIGHT_SCALE = "top_nav_height_scale";
    private static final String PREF_TOP_NAV_HEIGHT_LEGACY = "top_nav_height";
    private static final String PREF_LOG_RECORDING_ENABLED = "log_recording_enabled";

    private final SharedPreferences preferences;

    public OneStepSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public OneStepSettings load() {
        int rows = OneStepSettings.sanitizeGridRows(preferences.getInt(
                PREF_GRID_ROWS, OneStepSettings.DESKTOP_PAGE_ROWS));
        int columns = OneStepSettings.sanitizeGridColumns(preferences.getInt(
                PREF_GRID_COLUMNS, OneStepSettings.DESKTOP_PAGE_COLUMNS));
        if (!OneStepSettings.isSupportedGridLayout(rows, columns)) {
            rows = OneStepSettings.DESKTOP_PAGE_ROWS;
            columns = OneStepSettings.DESKTOP_PAGE_COLUMNS;
        }

        boolean topComponentsVisible = loadTopComponentsVisible();
        boolean verticalWindowLayout = preferences.getBoolean(
                PREF_VERTICAL_WINDOW_LAYOUT, false);

        return new OneStepSettings(
                rows,
                columns,
                OneStepSettings.sanitizeTopAppIconScale(loadTopAppIconScale()),
                OneStepSettings.sanitizeTopAppStripSpacingScale(preferences.getInt(
                        PREF_TOP_APP_STRIP_SPACING_SCALE,
                        OneStepSettings.TOP_APP_STRIP_SPACING_SCALE_DEFAULT)),
                OneStepSettings.sanitizeTopAppStripVerticalPaddingScale(preferences.getInt(
                        PREF_TOP_APP_STRIP_VERTICAL_PADDING_SCALE,
                        OneStepSettings.TOP_APP_STRIP_VERTICAL_PADDING_SCALE_DEFAULT)),
                topComponentsVisible,
                verticalWindowLayout,
                OneStepSettings.sanitizeAllowedSideWindowCount(preferences.getInt(
                        PREF_SIDE_WINDOW_COUNT, OneStepSettings.DEFAULT_SIDE_WINDOWS)),
                OneStepSettings.sanitizeTopNavVerticalMarginScale(
                        loadTopNavVerticalMarginScale()),
                OneStepSettings.sanitizeOneStepTriggerAreaScale(
                        loadOneStepTriggerAreaScale()),
                OneStepSettings.sanitizeCornerTriggerSensitivity(preferences.getInt(
                        PREF_CORNER_TRIGGER_SENSITIVITY,
                        OneStepSettings.CORNER_TRIGGER_SENSITIVITY_DEFAULT)),
                preferences.getBoolean(PREF_LOG_RECORDING_ENABLED, false));
    }

    public Uri getBackgroundUri() {
        String value = preferences.getString(PREF_BACKGROUND_URI, "");
        return TextUtils.isEmpty(value) ? null : Uri.parse(value);
    }

    public void saveBackgroundUri(Uri uri) {
        preferences.edit().putString(PREF_BACKGROUND_URI, uri.toString()).apply();
    }

    public void saveGridLayout(int rows, int columns) {
        preferences.edit()
                .putInt(PREF_GRID_ROWS, rows)
                .putInt(PREF_GRID_COLUMNS, columns)
                .apply();
    }

    public void saveOneStepTriggerAreaScale(int scalePct) {
        preferences.edit()
                .putInt(PREF_ONE_STEP_TRIGGER_AREA_SCALE, scalePct)
                .remove(PREF_CORNER_TRIGGER_SIZE_DP)
                .apply();
    }

    public void saveCornerTriggerSensitivity(int sensitivityPct) {
        preferences.edit().putInt(PREF_CORNER_TRIGGER_SENSITIVITY, sensitivityPct).apply();
    }

    public void saveTopAppIconScale(int scalePct) {
        preferences.edit().putInt(PREF_TOP_APP_ICON_SCALE, scalePct)
                .remove(PREF_TOP_APP_ICON_SIZE_LEGACY)
                .apply();
    }

    public void saveTopAppStripSpacingScale(int scalePct) {
        preferences.edit().putInt(PREF_TOP_APP_STRIP_SPACING_SCALE, scalePct).apply();
    }

    public void saveTopAppStripVerticalPaddingScale(int scalePct) {
        preferences.edit().putInt(PREF_TOP_APP_STRIP_VERTICAL_PADDING_SCALE, scalePct).apply();
    }

    public void saveTopComponentsVisible(boolean visible) {
        preferences.edit().putBoolean(PREF_TOP_COMPONENTS_VISIBLE, visible).apply();
    }

    public void saveVerticalWindowLayout(boolean vertical) {
        preferences.edit().putBoolean(PREF_VERTICAL_WINDOW_LAYOUT, vertical).apply();
    }

    public void saveSideWindowCount(int count) {
        preferences.edit().putInt(PREF_SIDE_WINDOW_COUNT, count).apply();
    }

    public void saveTopNavVerticalMarginScale(int scalePct) {
        preferences.edit().putInt(PREF_TOP_NAV_VERTICAL_MARGIN_SCALE, scalePct)
                .remove(PREF_TOP_NAV_HEIGHT_SCALE)
                .remove(PREF_TOP_NAV_HEIGHT_LEGACY)
                .apply();
    }

    public void saveLogRecordingEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_LOG_RECORDING_ENABLED, enabled).apply();
    }

    public synchronized Set<String> getSensorUidOverrides() {
        Set<String> packages = preferences.getStringSet(
                PREF_SENSOR_UID_OVERRIDES, Collections.emptySet());
        return packages == null ? new HashSet<>() : new HashSet<>(packages);
    }

    public synchronized void recordSensorUidOverride(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        Set<String> packages = getSensorUidOverrides();
        if (packages.add(packageName)) {
            preferences.edit().putStringSet(PREF_SENSOR_UID_OVERRIDES, packages).commit();
        }
    }

    public synchronized void clearSensorUidOverride(String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return;
        }
        Set<String> packages = getSensorUidOverrides();
        if (!packages.remove(packageName)) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        if (packages.isEmpty()) {
            editor.remove(PREF_SENSOR_UID_OVERRIDES);
        } else {
            editor.putStringSet(PREF_SENSOR_UID_OVERRIDES, packages);
        }
        editor.commit();
    }

    private boolean loadTopComponentsVisible() {
        if (preferences.contains(PREF_TOP_COMPONENTS_VISIBLE)) {
            return preferences.getBoolean(PREF_TOP_COMPONENTS_VISIBLE, true);
        }
        boolean visible = preferences.getBoolean(PREF_MEDIA_PLAYER_VISIBLE_LEGACY, true);
        preferences.edit()
                .putBoolean(PREF_TOP_COMPONENTS_VISIBLE, visible)
                .remove(PREF_MEDIA_PLAYER_VISIBLE_LEGACY)
                .remove(PREF_MEDIA_PLAYER_DEFAULT_APPLIED_LEGACY)
                .apply();
        return visible;
    }

    private int loadTopAppIconScale() {
        int scale = preferences.getInt(PREF_TOP_APP_ICON_SCALE, Integer.MIN_VALUE);
        if (scale != Integer.MIN_VALUE) {
            return scale;
        }
        int legacyDp = preferences.getInt(PREF_TOP_APP_ICON_SIZE_LEGACY,
                OneStepSettings.TOP_APP_ICON_SIZE_DEFAULT_DP);
        return legacyDp <= 0 ? OneStepSettings.TOP_APP_ICON_SCALE_DEFAULT
                : Math.round(legacyDp * 100f / OneStepSettings.TOP_APP_ICON_SIZE_DEFAULT_DP);
    }

    private int loadTopNavVerticalMarginScale() {
        int scale = preferences.getInt(PREF_TOP_NAV_VERTICAL_MARGIN_SCALE,
                Integer.MIN_VALUE);
        if (scale != Integer.MIN_VALUE) {
            return scale;
        }
        scale = preferences.getInt(PREF_TOP_NAV_HEIGHT_SCALE, Integer.MIN_VALUE);
        if (scale != Integer.MIN_VALUE) {
            return scale;
        }
        int legacyDp = preferences.getInt(PREF_TOP_NAV_HEIGHT_LEGACY,
                OneStepSettings.TOP_NAV_HEIGHT_DEFAULT_DP);
        int combinedMarginDp = Math.max(0,
                legacyDp - OneStepSettings.TOP_NAV_CONTENT_HEIGHT_DP);
        return Math.round(combinedMarginDp * 100f
                / (OneStepSettings.TOP_NAV_VERTICAL_MARGIN_DEFAULT_DP * 2));
    }

    private int loadOneStepTriggerAreaScale() {
        int scale = preferences.getInt(PREF_ONE_STEP_TRIGGER_AREA_SCALE,
                Integer.MIN_VALUE);
        return scale != Integer.MIN_VALUE ? scale
                : OneStepSettings.oneStepTriggerAreaScaleForSizeDp(preferences.getInt(
                        PREF_CORNER_TRIGGER_SIZE_DP,
                        OneStepSettings.CORNER_TRIGGER_SIZE_DEFAULT_DP));
    }
}
