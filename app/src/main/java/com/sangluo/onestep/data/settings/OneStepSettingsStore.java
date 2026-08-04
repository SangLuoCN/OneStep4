package com.sangluo.onestep.data.settings;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Owns preference keys, legacy migrations, validation, and persistence. */
public final class OneStepSettingsStore {
    private static final String PREFS_NAME = "onestep_settings";
    private static final String DIRECT_BOOT_PREFS_NAME = "onestep_direct_boot";
    private static final String PREF_BACKGROUND_URI = "background_uri";
    private static final String PREF_GRID_ROWS = "grid_rows";
    private static final String PREF_GRID_COLUMNS = "grid_columns";
    private static final String PREF_TOP_APP_ICON_SCALE = "top_app_icon_scale";
    private static final String PREF_TOP_APP_ICON_SIZE_LEGACY = "top_app_icon_size";
    private static final String PREF_TOP_APP_STRIP_SPACING_SCALE =
            "top_app_strip_spacing_scale";
    private static final String PREF_TOP_APP_STRIP_VERTICAL_PADDING_SCALE =
            "top_app_strip_vertical_padding_scale";
    private static final String PREF_TOP_APP_LIST_CONFIG = "top_app_list_config";
    private static final String PREF_TOP_COMPONENTS_VISIBLE = "top_components_visible";
    private static final String PREF_STATUS_BAR_SPACING_ENABLED =
            "status_bar_spacing_enabled";
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
    private static final String PREF_BUILT_IN_DESKTOP_COMPONENT =
            "built_in_desktop_component";
    private static final String BUILT_IN_DESKTOP_ONE_STEP = "onestep";

    private final SharedPreferences preferences;
    private final SharedPreferences directBootPreferences;

    public OneStepSettingsStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        directBootPreferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(DIRECT_BOOT_PREFS_NAME, Context.MODE_PRIVATE);
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
                preferences.getBoolean(PREF_STATUS_BAR_SPACING_ENABLED, false),
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

    public TopAppListConfig loadTopAppListConfig() {
        String encoded = preferences.getString(PREF_TOP_APP_LIST_CONFIG, null);
        if (TextUtils.isEmpty(encoded)) {
            return TopAppListConfig.unconfigured();
        }
        try {
            JSONObject root = new JSONObject(encoded);
            return new TopAppListConfig(
                    true,
                    readStringArray(root.optJSONArray("order")),
                    new LinkedHashSet<>(readStringArray(root.optJSONArray("selected"))));
        } catch (JSONException | RuntimeException e) {
            return TopAppListConfig.unconfigured();
        }
    }

    public void saveTopAppListConfig(List<String> orderedKeys, Set<String> selectedKeys) {
        JSONArray order = new JSONArray();
        if (orderedKeys != null) {
            for (String key : orderedKeys) {
                if (!TextUtils.isEmpty(key)) {
                    order.put(key);
                }
            }
        }
        JSONArray selected = new JSONArray();
        if (selectedKeys != null) {
            for (String key : selectedKeys) {
                if (!TextUtils.isEmpty(key)) {
                    selected.put(key);
                }
            }
        }
        try {
            JSONObject root = new JSONObject()
                    .put("version", 1)
                    .put("order", order)
                    .put("selected", selected);
            preferences.edit().putString(PREF_TOP_APP_LIST_CONFIG, root.toString()).apply();
        } catch (JSONException e) {
            throw new IllegalStateException("Unable to encode top app list", e);
        }
    }

    public void saveTopComponentsVisible(boolean visible) {
        preferences.edit().putBoolean(PREF_TOP_COMPONENTS_VISIBLE, visible).apply();
    }

    public void saveStatusBarSpacingEnabled(boolean enabled) {
        preferences.edit().putBoolean(PREF_STATUS_BAR_SPACING_ENABLED, enabled).apply();
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

    public ComponentName getBuiltInDesktopComponent() {
        String value = preferences.getString(PREF_BUILT_IN_DESKTOP_COMPONENT, "");
        mirrorDirectBootDesktopSelection(value);
        return TextUtils.isEmpty(value) || BUILT_IN_DESKTOP_ONE_STEP.equals(value)
                ? null : ComponentName.unflattenFromString(value);
    }

    public boolean isOneStepDesktopSelected() {
        String value = preferences.getString(PREF_BUILT_IN_DESKTOP_COMPONENT, "");
        mirrorDirectBootDesktopSelection(value);
        return TextUtils.isEmpty(value) || BUILT_IN_DESKTOP_ONE_STEP.equals(value);
    }

    public void saveOneStepDesktop() {
        preferences.edit()
                .putString(PREF_BUILT_IN_DESKTOP_COMPONENT, BUILT_IN_DESKTOP_ONE_STEP)
                .apply();
        mirrorDirectBootDesktopSelection(BUILT_IN_DESKTOP_ONE_STEP);
    }

    public void saveBuiltInDesktopComponent(ComponentName componentName) {
        SharedPreferences.Editor editor = preferences.edit();
        if (componentName == null) {
            editor.remove(PREF_BUILT_IN_DESKTOP_COMPONENT);
        } else {
            editor.putString(PREF_BUILT_IN_DESKTOP_COMPONENT,
                    componentName.flattenToString());
        }
        editor.apply();
        mirrorDirectBootDesktopSelection(componentName == null
                ? "" : componentName.flattenToString());
    }

    public static ComponentName getDirectBootBuiltInDesktopComponent(Context context) {
        SharedPreferences directBootPreferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences(DIRECT_BOOT_PREFS_NAME, Context.MODE_PRIVATE);
        String value = directBootPreferences.getString(
                PREF_BUILT_IN_DESKTOP_COMPONENT, "");
        return TextUtils.isEmpty(value) || BUILT_IN_DESKTOP_ONE_STEP.equals(value)
                ? null : ComponentName.unflattenFromString(value);
    }

    private void mirrorDirectBootDesktopSelection(String value) {
        directBootPreferences.edit()
                .putString(PREF_BUILT_IN_DESKTOP_COMPONENT,
                        TextUtils.isEmpty(value) ? BUILT_IN_DESKTOP_ONE_STEP : value)
                .commit();
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

    private static List<String> readStringArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>(array.length());
        for (int index = 0; index < array.length(); index++) {
            String value = array.optString(index, "");
            if (!TextUtils.isEmpty(value)) {
                values.add(value);
            }
        }
        return values;
    }

    public static final class TopAppListConfig {
        public final boolean configured;
        public final List<String> orderedKeys;
        public final Set<String> selectedKeys;

        TopAppListConfig(boolean configured, List<String> orderedKeys,
                         Set<String> selectedKeys) {
            this.configured = configured;
            this.orderedKeys = Collections.unmodifiableList(
                    new ArrayList<>(orderedKeys));
            this.selectedKeys = Collections.unmodifiableSet(
                    new LinkedHashSet<>(selectedKeys));
        }

        private static TopAppListConfig unconfigured() {
            return new TopAppListConfig(
                    false, Collections.emptyList(), Collections.emptySet());
        }
    }
}
