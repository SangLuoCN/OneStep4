package com.sangluo.onestep.ui.window;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EmptySideSlotClickPolicyTest {
    @Test
    public void displayedDesktopOnlyShowsCurrentDesktopHint() {
        assertEquals(
                EmptySideSlotClickPolicy.Action.SHOW_DESKTOP_ALREADY_DISPLAYED,
                EmptySideSlotClickPolicy.decide(true, true, true, false));
    }

    @Test
    public void appMainWithoutDesktopStartsDesktopAndPromotesClickedSlot() {
        assertEquals(
                EmptySideSlotClickPolicy.Action.SHOW_DESKTOP_AND_PROMOTE,
                EmptySideSlotClickPolicy.decide(true, false, true, false));
    }

    @Test
    public void emptyMainDoesNotStartDesktopFromSideSlot() {
        assertEquals(
                EmptySideSlotClickPolicy.Action.IGNORE,
                EmptySideSlotClickPolicy.decide(true, false, false, false));
    }

    @Test
    public void occupiedOrBusySlotIsIgnored() {
        assertEquals(
                EmptySideSlotClickPolicy.Action.IGNORE,
                EmptySideSlotClickPolicy.decide(false, false, true, false));
        assertEquals(
                EmptySideSlotClickPolicy.Action.IGNORE,
                EmptySideSlotClickPolicy.decide(true, false, true, true));
    }
}
