package com.sangluo.onestep.hook;

import android.view.Display;

final class DefaultDisplayHomeBottomCaptionPolicy {
    private static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";

    private DefaultDisplayHomeBottomCaptionPolicy() {
    }

    static boolean shouldSuppress(int displayId, String defaultHomePackage) {
        return displayId == Display.DEFAULT_DISPLAY
                && ONE_STEP_PACKAGE.equals(defaultHomePackage);
    }
}
