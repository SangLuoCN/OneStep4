package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class VirtualNavigationBarPolicyTest {
    @Test
    public void hidesOnlyOneStepVirtualNavigationWindows() {
        assertTrue(VirtualNavigationBarPolicy.shouldHide(
                "OneStepSlot-2", VirtualNavigationBarPolicy.TYPE_NAVIGATION_BAR));
        assertTrue(VirtualNavigationBarPolicy.shouldHide(
                "OneStepSlot-2", VirtualNavigationBarPolicy.TYPE_NAVIGATION_BAR_PANEL));
        assertFalse(VirtualNavigationBarPolicy.shouldHide(
                "Built-in Screen", VirtualNavigationBarPolicy.TYPE_NAVIGATION_BAR));
        assertFalse(VirtualNavigationBarPolicy.shouldHide("OneStepSlot-2", 2000));
        assertFalse(VirtualNavigationBarPolicy.shouldHide(null,
                VirtualNavigationBarPolicy.TYPE_NAVIGATION_BAR));
    }

    @Test
    public void hidesOnlyOneStepVirtualMiuiBottomCaptions() {
        assertTrue(VirtualNavigationBarPolicy.shouldHideBottomCaption(
                "OneStepSlot-2", "Miui Bottom Caption of Task=3397"));
        assertFalse(VirtualNavigationBarPolicy.shouldHideBottomCaption(
                "Built-in Screen", "Miui Bottom Caption of Task=3397"));
        assertFalse(VirtualNavigationBarPolicy.shouldHideBottomCaption(
                "OneStepSlot-2", "NavigationBar2"));
        assertFalse(VirtualNavigationBarPolicy.shouldHideBottomCaption(
                null, "Miui Bottom Caption of Task=3397"));
    }
}
