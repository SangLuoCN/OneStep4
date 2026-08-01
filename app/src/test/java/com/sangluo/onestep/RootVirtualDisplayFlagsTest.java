package com.sangluo.onestep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.hardware.display.DisplayManager;
import android.view.Display;

import org.junit.Test;

public class RootVirtualDisplayFlagsTest {
    private static final int ANDROID_10_SDK = 29;
    private static final int ANDROID_11_SDK = 30;
    private static final int DISPLAY_FLAG_TRUSTED = 1 << 7;
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;

    @Test
    public void publicCandidateRemainsPublicTrustedAndOwnContentOnly() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;

        int actual = RootVirtualDisplayFlags.forRootBridge(requested, ANDROID_11_SDK);

        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertEquals(0, actual & VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD);
        assertEquals(0, actual & VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS);
        assertNotEquals(0, actual & VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    @Test
    public void privateCandidateRemainsPrivateAndNonSecure() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

        int actual = RootVirtualDisplayFlags.forRootBridge(requested, ANDROID_11_SDK);

        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
        assertNotEquals(0, actual & VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    @Test
    public void privateCandidateCannotRequestSystemDecorations() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;

        int actual = RootVirtualDisplayFlags.forRootBridge(requested, ANDROID_11_SDK);

        assertEquals(0, actual & VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS);
    }

    @Test
    public void android10DropsUnsupportedTrustedFlag() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
                | VIRTUAL_DISPLAY_FLAG_TRUSTED;

        int actual = RootVirtualDisplayFlags.forRootBridge(requested, ANDROID_10_SDK);

        assertEquals(0, actual & VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    @Test
    public void android10AcceptsRootDisplayWithoutTrustedFlag() {
        assertTrue(RootVirtualDisplayFlags.hasRequiredTrustedDisplay(
                ANDROID_10_SDK, true, Display.FLAG_PRESENTATION));
    }

    @Test
    public void android11StillRequiresTrustedRootDisplay() {
        assertFalse(RootVirtualDisplayFlags.hasRequiredTrustedDisplay(
                ANDROID_11_SDK, true, Display.FLAG_PRESENTATION));
        assertTrue(RootVirtualDisplayFlags.hasRequiredTrustedDisplay(
                ANDROID_11_SDK, true, Display.FLAG_PRESENTATION | DISPLAY_FLAG_TRUSTED));
    }
}
