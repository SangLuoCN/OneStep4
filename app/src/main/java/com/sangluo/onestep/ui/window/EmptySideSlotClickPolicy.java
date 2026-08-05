package com.sangluo.onestep.ui.window;

/** Decides how an empty side slot responds to a click. */
public final class EmptySideSlotClickPolicy {
    public enum Action {
        IGNORE,
        SHOW_DESKTOP_ALREADY_DISPLAYED,
        SHOW_DESKTOP_AND_PROMOTE
    }

    private EmptySideSlotClickPolicy() {
    }

    public static Action decide(boolean emptySideSlot, boolean desktopDisplayed,
                                boolean mainAppDisplayed, boolean interactionBlocked) {
        if (!emptySideSlot || interactionBlocked) {
            return Action.IGNORE;
        }
        if (desktopDisplayed) {
            return Action.SHOW_DESKTOP_ALREADY_DISPLAYED;
        }
        if (mainAppDisplayed) {
            return Action.SHOW_DESKTOP_AND_PROMOTE;
        }
        return Action.IGNORE;
    }
}
