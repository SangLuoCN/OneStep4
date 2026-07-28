package com.sangluo.onestep;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import android.hardware.display.DisplayManager;

import org.junit.Test;

public class SecureVirtualDisplayFlagsTest {
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;

    @Test
    public void publicCandidateRemainsPublicSecureAndOwnContentOnly() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

        int actual = SecureVirtualDisplayFlags.forRootBridge(requested);

        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertEquals(0, actual & VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD);
        assertNotEquals(0, actual & VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    @Test
    public void privateCandidateRemainsPrivate() {
        int requested = DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;

        int actual = SecureVirtualDisplayFlags.forRootBridge(requested);

        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        assertNotEquals(0, actual & DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE);
        assertNotEquals(0, actual & VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }
}
