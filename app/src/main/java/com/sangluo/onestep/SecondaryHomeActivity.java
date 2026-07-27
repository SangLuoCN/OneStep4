package com.sangluo.onestep;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;

/** HOME task kept behind applications hosted on a OneStep virtual display. */
public final class SecondaryHomeActivity extends Activity {
    public static final String EXTRA_BACKGROUND_ONLY =
            "com.sangluo.onestep.extra.SECONDARY_HOME_BACKGROUND_ONLY";
    private static final String TAG = "OneStep40";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View background = new View(this);
        background.setBackgroundColor(Color.BLACK);
        setContentView(background);
        handleHomeIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleHomeIntent(intent);
    }

    private void handleHomeIntent(Intent intent) {
        int displayId = getActivityDisplayId();
        if (displayId == Display.DEFAULT_DISPLAY) {
            Log.w(TAG, "Secondary HOME was launched on the default display");
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (intent != null && intent.getBooleanExtra(EXTRA_BACKGROUND_ONLY, false)) {
            Log.i(TAG, "Keep secondary HOME behind hosted app on display " + displayId);
            return;
        }
        if (!MainActivity.dispatchSecondaryHome(displayId)) {
            Log.w(TAG, "No active OneStep host for secondary HOME on display " + displayId);
        }
    }

    private int getActivityDisplayId() {
        Display display = getWindowManager().getDefaultDisplay();
        return display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
    }
}
