package com.sangluo.onestep.feature.drag;

/** Pure hit-testing used to select an image-share target on pointer release. */
public final class ImageDragHoverPolicy {
    private ImageDragHoverPolicy() {
    }

    public static int findTarget(float x, float y, int[][] frames,
                                 boolean[] eligibleSlots) {
        if (frames == null || eligibleSlots == null) {
            return -1;
        }
        int count = Math.min(frames.length, eligibleSlots.length);
        for (int slot = 0; slot < count; slot++) {
            int[] frame = frames[slot];
            if (eligibleSlots[slot] && frame != null
                    && frame.length >= 4
                    && x >= frame[0] && x < frame[2]
                    && y >= frame[1] && y < frame[3]) {
                return slot;
            }
        }
        return -1;
    }

}
