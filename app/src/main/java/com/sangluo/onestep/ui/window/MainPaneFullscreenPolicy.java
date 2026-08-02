package com.sangluo.onestep.ui.window;

/** Selects the main pane nearest either outer edge of the current workspace. */
public final class MainPaneFullscreenPolicy {
    private MainPaneFullscreenPolicy() {
    }

    public static int selectEdgeMainSlot(
            int workspaceWidth,
            int firstSlot, int firstLeft, int firstRight,
            int secondSlot, int secondLeft, int secondRight,
            int activeSlot) {
        int firstDistance = distanceToOuterEdge(workspaceWidth, firstLeft, firstRight);
        int secondDistance = distanceToOuterEdge(workspaceWidth, secondLeft, secondRight);
        if (firstDistance < secondDistance) {
            return firstSlot;
        }
        if (secondDistance < firstDistance) {
            return secondSlot;
        }
        if (activeSlot == firstSlot || activeSlot == secondSlot) {
            return activeSlot;
        }
        return firstSlot;
    }

    private static int distanceToOuterEdge(int workspaceWidth, int left, int right) {
        return Math.min(Math.abs(left), Math.abs(workspaceWidth - right));
    }
}
