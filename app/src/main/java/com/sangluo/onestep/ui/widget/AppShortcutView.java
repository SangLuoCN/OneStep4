package com.sangluo.onestep.ui.widget;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.sangluo.onestep.model.LauncherApp;

/** Reusable launcher icon used by the top strip, dock, and paged desktop grid. */
public final class AppShortcutView extends LinearLayout {
    private final FrameLayout iconFrame;
    private final ImageView icon;
    private final TextView label;
    private String packageName = "";

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
        icon.setImageDrawable(app.icon);
        label.setText(app.label);
        setContentDescription(app.label);
    }

    public String getPackageNameValue() {
        return packageName;
    }

    public void setActive(boolean active) {
        iconFrame.setBackgroundColor(Color.TRANSPARENT);
        label.setTextColor(active ? 0xffffffff : 0xddffffff);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
