package com.sangluo.onestep.ui.window;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AppLaunchPlacementTest {
    @Test
    public void emptyMainAlwaysReceivesNewApp() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(2, false, 0);

        assertEquals(AppLaunchPlacement.Action.START_IN_MAIN, placement.action);
        assertEquals(2, placement.targetSlot);
    }

    @Test
    public void occupiedMainMovesToSideWhenSideSlotIsEmpty() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(0, true, 3);

        assertEquals(AppLaunchPlacement.Action.START_IN_SIDE_AND_PROMOTE, placement.action);
        assertEquals(3, placement.targetSlot);
    }

    @Test
    public void emptySecondMainReceivesNewAppBeforeSidePromotion() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(0, true, 1, 3, -1);

        assertEquals(AppLaunchPlacement.Action.START_IN_EMPTY_MAIN, placement.action);
        assertEquals(1, placement.targetSlot);
    }

    @Test
    public void fullLayoutReplacesCurrentMain() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(1, true, -1);

        assertEquals(AppLaunchPlacement.Action.REPLACE_MAIN, placement.action);
        assertEquals(1, placement.targetSlot);
    }

    @Test
    public void occupiedEdgeMainUsesEmptyMiddleMainBeforeSideSlot() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                0, true, 1, 3, -1, 1);

        assertEquals(AppLaunchPlacement.Action.START_IN_EMPTY_MAIN, placement.action);
        assertEquals(1, placement.targetSlot);
    }

    @Test
    public void activeEmptyMiddleMainReceivesNewAppDirectly() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                1, false, -1, 3, -1, 1);

        assertEquals(AppLaunchPlacement.Action.START_IN_MAIN, placement.action);
        assertEquals(1, placement.targetSlot);
    }

    @Test
    public void occupiedMiddleMainMovesToEmptySideBeforeShowingNewApp() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                0, true, -1, 3, -1, 1);

        assertEquals(AppLaunchPlacement.Action.START_IN_SIDE_AND_PROMOTE, placement.action);
        assertEquals(3, placement.targetSlot);
    }

    @Test
    public void occupiedMiddleMainIsReplacedOnlyWhenSideScreensAreFull() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(
                0, true, -1, -1, -1, 1);

        assertEquals(AppLaunchPlacement.Action.REPLACE_MAIN, placement.action);
        assertEquals(1, placement.targetSlot);
    }

    @Test
    public void displayedMainDesktopReplacesEmptySideSlotPriority() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(0, true, 3, 2);

        assertEquals(
                AppLaunchPlacement.Action.REPLACE_SIDE_AND_PROMOTE, placement.action);
        assertEquals(2, placement.targetSlot);
    }

    @Test
    public void displayedMainDesktopInMainIsReplacedDirectly() {
        AppLaunchPlacement placement = AppLaunchPlacement.decide(2, true, 0, 2);

        assertEquals(AppLaunchPlacement.Action.REPLACE_MAIN, placement.action);
        assertEquals(2, placement.targetSlot);
    }
}
