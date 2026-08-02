package com.sangluo.onestep.ui.window;

/** Decides where a newly selected app enters the current window layout. */
public final class AppLaunchPlacement {
    public enum Action {
        START_IN_MAIN,
        START_IN_EMPTY_MAIN,
        START_IN_SIDE_AND_PROMOTE,
        REPLACE_SIDE_AND_PROMOTE,
        REPLACE_MAIN
    }

    public final Action action;
    public final int targetSlot;

    private AppLaunchPlacement(Action action, int targetSlot) {
        this.action = action;
        this.targetSlot = targetSlot;
    }

    public static AppLaunchPlacement decide(int activeMainSlot, boolean mainOccupied,
                                            int emptySideSlot) {
        return decide(activeMainSlot, mainOccupied, emptySideSlot, -1);
    }

    public static AppLaunchPlacement decide(int activeMainSlot, boolean mainOccupied,
                                            int emptySideSlot, int mainDesktopSlot) {
        return decide(activeMainSlot, mainOccupied, -1, emptySideSlot, mainDesktopSlot);
    }

    public static AppLaunchPlacement decide(int activeMainSlot, boolean mainOccupied,
                                            int emptyMainSlot, int emptySideSlot,
                                            int mainDesktopSlot) {
        if (mainDesktopSlot >= 0) {
            return new AppLaunchPlacement(
                    mainDesktopSlot == activeMainSlot
                            ? Action.REPLACE_MAIN : Action.REPLACE_SIDE_AND_PROMOTE,
                    mainDesktopSlot);
        }
        if (!mainOccupied) {
            return new AppLaunchPlacement(Action.START_IN_MAIN, activeMainSlot);
        }
        if (emptyMainSlot >= 0) {
            return new AppLaunchPlacement(Action.START_IN_EMPTY_MAIN, emptyMainSlot);
        }
        if (emptySideSlot >= 0) {
            return new AppLaunchPlacement(
                    Action.START_IN_SIDE_AND_PROMOTE, emptySideSlot);
        }
        return new AppLaunchPlacement(Action.REPLACE_MAIN, activeMainSlot);
    }
}
