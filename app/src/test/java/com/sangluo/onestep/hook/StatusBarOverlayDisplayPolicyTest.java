package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StatusBarOverlayDisplayPolicyTest {
    private static final int DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 6;

    @Test
    public void appliesZeroHeightOverlayWithoutSystemDecorations() {
        assertTrue(StatusBarOverlayDisplayPolicy.shouldApply(0));
    }

    @Test
    public void preservesStatusBarHeightWithSystemDecorations() {
        assertFalse(StatusBarOverlayDisplayPolicy.shouldApply(
                DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS));
    }
}
