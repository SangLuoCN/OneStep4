package com.sangluo.onestep;

import android.content.Intent;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrossAppLaunchRoutingPolicyTest {
    @Test
    public void preservesStorageAccessFrameworkContracts() {
        assertTrue(preserves(Intent.ACTION_GET_CONTENT));
        assertTrue(preserves(Intent.ACTION_OPEN_DOCUMENT));
        assertTrue(preserves(Intent.ACTION_CREATE_DOCUMENT));
        assertTrue(preserves(Intent.ACTION_OPEN_DOCUMENT_TREE));
    }

    @Test
    public void preservesPickAndCaptureContracts() {
        assertTrue(preserves(Intent.ACTION_PICK));
        assertTrue(preserves("android.provider.action.PICK_IMAGES"));
        assertTrue(preserves("android.media.action.IMAGE_CAPTURE"));
        assertTrue(preserves("android.media.action.VIDEO_CAPTURE"));
    }

    @Test
    public void preservesExplicitForwardResultLaunches() {
        assertTrue(CrossAppLaunchRoutingPolicy.shouldPreserveCallerTask(
                "com.example.CUSTOM", Intent.FLAG_ACTIVITY_FORWARD_RESULT));
    }

    @Test
    public void routesOrdinaryCrossAppLaunches() {
        assertFalse(preserves(Intent.ACTION_VIEW));
        assertFalse(preserves(Intent.ACTION_MAIN));
        assertFalse(preserves(null));
    }

    private static boolean preserves(String action) {
        return CrossAppLaunchRoutingPolicy.shouldPreserveCallerTask(action, 0);
    }
}
