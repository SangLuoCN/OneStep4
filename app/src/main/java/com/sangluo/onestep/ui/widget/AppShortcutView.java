package com.sangluo.onestep.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sangluo.onestep.model.LauncherApp;

/** Reusable launcher icon used by the top strip, dock, and paged desktop grid. */
public final class AppShortcutView extends LinearLayout {
    public enum AppStatus {
        NONE,
        BACKGROUND,
        FOREGROUND
    }

    private static final int BACKGROUND_STATUS_COLOR = 0xff0a84ff;
    private static final int FOREGROUND_STATUS_COLOR = 0xff34c759;

    private final FrameLayout iconFrame;
    private final ImageView icon;
    private final View statusIndicator;
    private final TextView label;
    private String packageName = "";
    private String appLabel = "";
    private boolean statusIndicatorEnabled;
    private AppStatus appStatus = AppStatus.NONE;

    public AppShortcutView(Context context, boolean showLabel, int iconSizeDp, float textSizeDp) {
        super(context);
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(2), dp(showLabel ? 3 : 2), dp(2), dp(showLabel ? 3 : 2));
        setBackgroundColor(Color.TRANSPARENT);

        iconFrame = new FrameLayout(context);
        iconFrame.setBackgroundColor(Color.TRANSPARENT);
        int iconFrameSizeDp = iconSizeDp + (showLabel ? 8 : 6);
        addView(iconFrame, new LinearLayout.LayoutParams(
                dp(iconFrameSizeDp), dp(iconFrameSizeDp)));

        icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iconFrame.addView(icon, new FrameLayout.LayoutParams(
                dp(iconSizeDp), dp(iconSizeDp), Gravity.CENTER));

        statusIndicator = new View(context);
        statusIndicator.setElevation(dp(2));
        statusIndicator.setVisibility(GONE);
        int statusSize = dp(10);
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                statusSize, statusSize, Gravity.TOP | Gravity.END);
        statusParams.topMargin = dp(1);
        statusParams.setMarginEnd(dp(1));
        iconFrame.addView(statusIndicator, statusParams);

        label = new TextView(context);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(0xddffffff);
        label.setShadowLayer(dp(1), 0, dp(1), 0x66000000);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, textSizeDp);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(5);
        addView(label, labelParams);
        label.setVisibility(showLabel ? VISIBLE : GONE);
    }

    public void bind(LauncherApp app) {
        packageName = app.packageName;
        appLabel = app.label;
        icon.setImageDrawable(app.icon);
        label.setText(app.label);
        updateContentDescription();
    }

    public String getPackageNameValue() {
        return packageName;
    }

    public void setActive(boolean active) {
        iconFrame.setBackgroundColor(Color.TRANSPARENT);
        label.setTextColor(active ? 0xffffffff : 0xddffffff);
    }

    public void setStatusIndicatorEnabled(boolean enabled) {
        statusIndicatorEnabled = enabled;
        updateStatusIndicator();
    }

    public void setAppStatus(AppStatus status) {
        appStatus = status == null ? AppStatus.NONE : status;
        updateStatusIndicator();
        updateContentDescription();
    }

    private void updateStatusIndicator() {
        if (!statusIndicatorEnabled || appStatus == AppStatus.NONE) {
            statusIndicator.setVisibility(GONE);
            return;
        }
        GradientDrawable marker = new GradientDrawable();
        marker.setShape(GradientDrawable.OVAL);
        marker.setColor(appStatus == AppStatus.FOREGROUND
                ? FOREGROUND_STATUS_COLOR : BACKGROUND_STATUS_COLOR);
        marker.setStroke(dp(1), 0xe6ffffff);
        statusIndicator.setBackground(marker);
        statusIndicator.setVisibility(VISIBLE);
    }

    private void updateContentDescription() {
        if (!statusIndicatorEnabled || appStatus == AppStatus.NONE) {
            setContentDescription(appLabel);
            return;
        }
        setContentDescription(appLabel + (appStatus == AppStatus.FOREGROUND
                ? "，已在窗口中打开" : "，正在后台运行"));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
