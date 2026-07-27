package com.sangluo.onestep;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/** Opens the OneStep host directly from its launcher icon. */
public final class HomeRedirectActivity extends Activity {
    private static final String TAG = "OneStep40";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent appIntent = new Intent(this, MainActivity.class)
                .setAction(Intent.ACTION_MAIN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (getIntent().getBooleanExtra(MainActivity.EXTRA_SHOW_DESKTOP_HOME, false)) {
            appIntent.putExtra(MainActivity.EXTRA_SHOW_DESKTOP_HOME, true);
        }
        if (getIntent().getBooleanExtra(
                MainActivity.EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, false)) {
            appIntent.putExtra(MainActivity.EXTRA_DEFAULT_DISPLAY_RELAY_ATTEMPTED, true);
        }
        try {
            startActivity(appIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Unable to open OneStep main activity", e);
        } finally {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
