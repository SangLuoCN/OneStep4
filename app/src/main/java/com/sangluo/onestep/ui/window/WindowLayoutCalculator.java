package com.sangluo.onestep.ui.window;

import android.graphics.Rect;

import java.util.List;

/** Calculates main and side-window frames without owning or mutating any views. */
public final class WindowLayoutCalculator {
    private WindowLayoutCalculator() {
    }

    public static Rect[] calculate(int windowCount, int workspaceWidth, int workspaceHeight,
                                   int divider, int topChromeHeight, boolean multiWindowMode,
                                   boolean verticalLayout, int activeMainSlot,
                                   List<Integer> sideSlotOrder, int visibleSideCount,
                                   boolean mainOnLeft, int hiddenMargin) {
        return calculate(windowCount, workspaceWidth, workspaceHeight, divider, topChromeHeight,
                multiWindowMode, verticalLayout, activeMainSlot, activeMainSlot, -1,
                sideSlotOrder, visibleSideCount, mainOnLeft, hiddenMargin);
    }

    public static Rect[] calculate(int windowCount, int workspaceWidth, int workspaceHeight,
                                   int divider, int topChromeHeight, boolean multiWindowMode,
                                   boolean verticalLayout, int activeMainSlot,
                                   int firstMainSlot, int secondMainSlot,
                                   List<Integer> sideSlotOrder, int visibleSideCount,
                                   boolean mainOnLeft, int hiddenMargin) {
        Rect[] rects = new Rect[windowCount];
        int contentTop = multiWindowMode
                ? Math.min(topChromeHeight, Math.max(0, workspaceHeight - 1)) : 0;
        int contentHeight = Math.max(1, workspaceHeight - contentTop);
        if (secondMainSlot >= 0 && secondMainSlot != firstMainSlot) {
            calculateDualMain(rects, workspaceWidth, workspaceHeight, contentTop, contentHeight,
                    divider, multiWindowMode, verticalLayout, activeMainSlot,
                    firstMainSlot, secondMainSlot, sideSlotOrder, visibleSideCount,
                    mainOnLeft, hiddenMargin);
            fillMissing(rects, workspaceWidth, workspaceHeight, hiddenMargin);
            return rects;
        }
        if (verticalLayout) {
            calculateVertical(rects, workspaceWidth, workspaceHeight, contentTop, contentHeight,
                    divider, multiWindowMode, activeMainSlot, sideSlotOrder, visibleSideCount,
                    hiddenMargin);
        } else {
            calculateHorizontal(rects, workspaceWidth, workspaceHeight, contentTop, contentHeight,
                    divider, multiWindowMode, activeMainSlot, sideSlotOrder, visibleSideCount,
                    mainOnLeft, hiddenMargin);
        }
        fillMissing(rects, workspaceWidth, workspaceHeight, hiddenMargin);
        return rects;
    }

    private static void calculateDualMain(Rect[] rects, int workspaceWidth,
                                          int workspaceHeight, int contentTop,
                                          int contentHeight, int divider,
                                          boolean multiWindowMode, boolean verticalLayout,
                                          int activeMainSlot, int firstMainSlot,
                                          int secondMainSlot, List<Integer> sideSlotOrder,
                                          int visibleSideCount, boolean mainOnLeft,
                                          int hiddenMargin) {
        if (!multiWindowMode) {
            rects[activeMainSlot] = new Rect(0, 0, workspaceWidth, workspaceHeight);
            int inactiveMainSlot = activeMainSlot == firstMainSlot
                    ? secondMainSlot : firstMainSlot;
            rects[inactiveMainSlot] = hiddenRect(
                    workspaceWidth, workspaceHeight, hiddenMargin);
            for (int slot : sideSlotOrder) {
                rects[slot] = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
            }
            return;
        }
        // A trailing grid is the only arrangement that can keep two portrait main panes while
        // consuming the full width of a near-square tablet or unfolded display.
        calculateDualMainHorizontal(rects, workspaceWidth, workspaceHeight,
                contentTop, contentHeight, divider, firstMainSlot, secondMainSlot,
                sideSlotOrder, visibleSideCount, mainOnLeft);
    }

    private static void calculateDualMainHorizontal(
            Rect[] rects, int workspaceWidth, int workspaceHeight,
            int contentTop, int contentHeight, int divider,
            int firstMainSlot, int secondMainSlot, List<Integer> sideSlotOrder,
            int visibleSideCount, boolean mainOnLeft) {
        int shownSideCount = Math.min(visibleSideCount, sideSlotOrder.size());
        int sideRows = Math.max(1, Math.min(3, shownSideCount));
        int sideColumns = shownSideCount == 0
                ? 0 : (shownSideCount + sideRows - 1) / sideRows;
        int horizontalGapCount = sideColumns == 0 ? 1 : sideColumns + 1;
        int availableTileWidth = Math.max(2 + sideColumns,
                workspaceWidth - divider * horizontalGapCount);
        int averageSideHeight = sideColumns == 0 ? 0 : Math.max(1,
                (contentHeight - divider * Math.max(0, sideRows - 1)) / sideRows);
        int tileHeightSum = contentHeight * 2 + sideColumns * averageSideHeight;
        int mainWidth = Math.max(1, Math.round(
                availableTileWidth * contentHeight / (float) Math.max(1, tileHeightSum)));
        mainWidth = Math.min(mainWidth,
                Math.max(1, (availableTileWidth - sideColumns) / 2));
        int sideTilesWidth = Math.max(sideColumns,
                availableTileWidth - mainWidth * 2);
        int sideGridWidth = sideColumns == 0 ? 0
                : sideTilesWidth + divider * Math.max(0, sideColumns - 1);
        int groupTop = contentTop;
        int mainLeft = mainOnLeft || sideColumns == 0
                ? 0 : sideGridWidth + divider;
        int sideLeft = mainOnLeft
                ? mainLeft + mainWidth * 2 + divider * 2 : 0;

        int leftMainSlot = mainOnLeft ? firstMainSlot : secondMainSlot;
        int rightMainSlot = mainOnLeft ? secondMainSlot : firstMainSlot;
        rects[leftMainSlot] = new Rect(
                mainLeft, groupTop, mainLeft + mainWidth, groupTop + contentHeight);
        int secondMainLeft = mainLeft + mainWidth + divider;
        rects[rightMainSlot] = new Rect(secondMainLeft, groupTop,
                secondMainLeft + mainWidth, groupTop + contentHeight);
        for (int index = 0; index < shownSideCount; index++) {
            int column = index / sideRows;
            int row = index % sideRows;
            int left = sideLeft + distributedOffset(
                    sideTilesWidth, sideColumns, column, divider);
            int top = groupTop + distributedOffset(
                    contentHeight - divider * Math.max(0, sideRows - 1),
                    sideRows, row, divider);
            int sideWidth = distributedSize(sideTilesWidth, sideColumns, column);
            int sideHeight = distributedSize(
                    contentHeight - divider * Math.max(0, sideRows - 1), sideRows, row);
            rects[sideSlotOrder.get(index)] = new Rect(
                    left, top, left + sideWidth, top + sideHeight);
        }
    }

    private static int distributedOffset(int totalSize, int count, int index, int gap) {
        if (count <= 0 || index <= 0) {
            return 0;
        }
        int baseSize = totalSize / count;
        int remainder = totalSize % count;
        return index * baseSize + Math.min(index, remainder) + index * gap;
    }

    private static int distributedSize(int totalSize, int count, int index) {
        if (count <= 0) {
            return 0;
        }
        int baseSize = totalSize / count;
        int remainder = totalSize % count;
        return Math.max(1, baseSize + (index < remainder ? 1 : 0));
    }

    private static void calculateHorizontal(Rect[] rects, int workspaceWidth,
                                            int workspaceHeight, int contentTop,
                                            int contentHeight, int divider,
                                            boolean multiWindowMode, int activeMainSlot,
                                            List<Integer> sideSlotOrder, int visibleSideCount,
                                            boolean mainOnLeft, int hiddenMargin) {
        int availableWidth = Math.max(2, workspaceWidth - divider);
        int sideHeight = Math.max(1,
                (contentHeight - divider * Math.max(0, visibleSideCount - 1))
                        / Math.max(1, visibleSideCount));
        int sideWidth = Math.round(sideHeight * availableWidth
                / (float) Math.max(1, contentHeight + sideHeight));
        sideWidth = Math.max(1, Math.min(availableWidth - 1, sideWidth));
        int mainWidth = availableWidth - sideWidth;
        int sideLeft = mainOnLeft ? mainWidth + divider : 0;
        int mainLeft = mainOnLeft ? 0 : sideWidth + divider;

        if (!multiWindowMode) {
            rects[activeMainSlot] = new Rect(0, 0, workspaceWidth, workspaceHeight);
            int offscreenSideLeft = mainOnLeft
                    ? workspaceWidth + divider : -sideWidth - divider;
            for (int index = 0; index < sideSlotOrder.size(); index++) {
                int slot = sideSlotOrder.get(index);
                if (index >= visibleSideCount) {
                    rects[slot] = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
                    continue;
                }
                int top = index * (sideHeight + divider);
                rects[slot] = new Rect(offscreenSideLeft, top,
                        offscreenSideLeft + sideWidth, top + sideHeight);
            }
            return;
        }

        rects[activeMainSlot] = new Rect(
                mainLeft, contentTop, mainLeft + mainWidth, workspaceHeight);
        for (int index = 0; index < sideSlotOrder.size(); index++) {
            int slot = sideSlotOrder.get(index);
            if (index >= visibleSideCount) {
                rects[slot] = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
                continue;
            }
            int top = contentTop + index * (sideHeight + divider);
            rects[slot] = new Rect(sideLeft, top, sideLeft + sideWidth, top + sideHeight);
        }
    }

    private static void calculateVertical(Rect[] rects, int workspaceWidth,
                                          int workspaceHeight, int contentTop,
                                          int contentHeight, int divider,
                                          boolean multiWindowMode, int activeMainSlot,
                                          List<Integer> sideSlotOrder, int visibleSideCount,
                                          int hiddenMargin) {
        int totalDividerWidth = divider * Math.max(0, visibleSideCount - 1);
        int sideWidth = Math.max(1,
                (workspaceWidth - totalDividerWidth) / Math.max(1, visibleSideCount));
        float widthRatio = sideWidth / (float) Math.max(1, workspaceWidth);
        int sideHeight = Math.round(widthRatio * Math.max(1, contentHeight - divider)
                / (1f + widthRatio));
        sideHeight = Math.max(1,
                Math.min(Math.max(1, contentHeight - divider - 1), sideHeight));
        int railWidth = sideWidth * visibleSideCount + totalDividerWidth;
        int railLeft = Math.max(0, (workspaceWidth - railWidth) / 2);
        int mainBottom = Math.max(contentTop + 1, workspaceHeight - sideHeight - divider);

        if (!multiWindowMode) {
            rects[activeMainSlot] = new Rect(0, 0, workspaceWidth, workspaceHeight);
            int offscreenTop = workspaceHeight + divider;
            for (int index = 0; index < sideSlotOrder.size(); index++) {
                int slot = sideSlotOrder.get(index);
                if (index >= visibleSideCount) {
                    rects[slot] = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
                    continue;
                }
                int left = railLeft + index * (sideWidth + divider);
                rects[slot] = new Rect(
                        left, offscreenTop, left + sideWidth, offscreenTop + sideHeight);
            }
            return;
        }

        int sideTop = workspaceHeight - sideHeight;
        rects[activeMainSlot] = new Rect(0, contentTop, workspaceWidth, mainBottom);
        for (int index = 0; index < sideSlotOrder.size(); index++) {
            int slot = sideSlotOrder.get(index);
            if (index >= visibleSideCount) {
                rects[slot] = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
                continue;
            }
            int left = railLeft + index * (sideWidth + divider);
            rects[slot] = new Rect(left, sideTop, left + sideWidth, sideTop + sideHeight);
        }
    }

    private static void fillMissing(Rect[] rects, int workspaceWidth, int workspaceHeight,
                                    int hiddenMargin) {
        Rect hiddenRect = hiddenRect(workspaceWidth, workspaceHeight, hiddenMargin);
        for (int slot = 0; slot < rects.length; slot++) {
            if (rects[slot] == null) {
                rects[slot] = new Rect(hiddenRect);
            }
        }
    }

    private static Rect hiddenRect(int workspaceWidth, int workspaceHeight, int hiddenMargin) {
        int left = workspaceWidth + hiddenMargin;
        int top = workspaceHeight + hiddenMargin;
        return new Rect(left, top, left + 1, top + 1);
    }
}
