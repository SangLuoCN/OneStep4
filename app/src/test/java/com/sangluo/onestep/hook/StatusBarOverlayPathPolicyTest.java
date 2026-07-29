package com.sangluo.onestep.hook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusBarOverlayPathPolicyTest {
    private static final String OVERLAY = "/system/etc/onestep/statusbar.apk";

    @Test
    public void addsOverlayToEmptyPaths() {
        assertArrayEquals(new String[]{OVERLAY},
                StatusBarOverlayPathPolicy.update(null, OVERLAY, true));
    }

    @Test
    public void addsOverlayOnceAndPreservesOrder() {
        assertArrayEquals(new String[]{"/first.apk", "/second.apk", OVERLAY},
                StatusBarOverlayPathPolicy.update(
                        new String[]{"/first.apk", OVERLAY, "/second.apk", OVERLAY},
                        OVERLAY,
                        true));
    }

    @Test
    public void removesOnlyOneStepOverlay() {
        assertArrayEquals(new String[]{"/first.apk", "/second.apk"},
                StatusBarOverlayPathPolicy.update(
                        new String[]{"/first.apk", OVERLAY, "/second.apk"},
                        OVERLAY,
                        false));
        assertNull(StatusBarOverlayPathPolicy.update(
                new String[]{OVERLAY}, OVERLAY, false));
    }

    @Test
    public void detectsOverlayPresence() {
        assertTrue(StatusBarOverlayPathPolicy.contains(
                new String[]{"/first.apk", OVERLAY}, OVERLAY));
        assertFalse(StatusBarOverlayPathPolicy.contains(
                new String[]{"/first.apk"}, OVERLAY));
        assertFalse(StatusBarOverlayPathPolicy.contains(null, OVERLAY));
    }
}
