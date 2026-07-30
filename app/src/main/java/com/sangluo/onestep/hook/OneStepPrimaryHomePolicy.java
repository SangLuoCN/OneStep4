package com.sangluo.onestep.hook;

import android.content.Intent;

/** Eligibility rules for creating a primary launcher workspace on a OneStep display. */
public final class OneStepPrimaryHomePolicy {
    public static final String EXTRA_EMBEDDED_PRIMARY_HOME =
            "com.sangluo.onestep.extra.EMBEDDED_PRIMARY_HOME";
    static final String ONE_STEP_PACKAGE = "com.sangluo.onestep";
    static final String DISPLAY_NAME_PREFIX = "OneStepSlot-";

    private OneStepPrimaryHomePolicy() {
    }

    static boolean shouldCreateWorkspace(boolean requested, String action,
                                         boolean hasHomeCategory,
                                         String launchedFromPackage,
                                         String targetPackage,
                                         String displayName) {
        return requested
                && Intent.ACTION_MAIN.equals(action)
                && hasHomeCategory
                && ONE_STEP_PACKAGE.equals(launchedFromPackage)
                && targetPackage != null
                && !ONE_STEP_PACKAGE.equals(targetPackage)
                && displayName != null
                && displayName.startsWith(DISPLAY_NAME_PREFIX);
    }
}
