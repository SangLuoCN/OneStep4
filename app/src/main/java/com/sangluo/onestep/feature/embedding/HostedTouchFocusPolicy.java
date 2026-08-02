package com.sangluo.onestep.feature.embedding;

/** Separates physical system gestures from touches intended for the hosted display. */
public final class HostedTouchFocusPolicy {
    private HostedTouchFocusPolicy() {
    }

    public static boolean shouldReserveForSystemNavigation(
            float rawX, float rawY,
            int windowLeft, int windowTop, int windowRight, int windowBottom,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (windowRight <= windowLeft || windowBottom <= windowTop
                || rawX < windowLeft || rawX > windowRight
                || rawY < windowTop || rawY > windowBottom) {
            return false;
        }
        return (insetLeft > 0 && rawX < windowLeft + insetLeft)
                || (insetTop > 0 && rawY < windowTop + insetTop)
                || (insetRight > 0 && rawX > windowRight - insetRight)
                || (insetBottom > 0 && rawY > windowBottom - insetBottom);
    }
}
