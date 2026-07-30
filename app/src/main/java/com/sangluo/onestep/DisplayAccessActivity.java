package com.sangluo.onestep;

import android.app.Activity;
import android.os.Bundle;

/** One-shot, non-visual activity used only to grant this UID access to a new display. */
public final class DisplayAccessActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
        overridePendingTransition(0, 0);
    }
}
