package com.sangluo.onestep.system.display;

/** Prevents a stale host from mutating a replacement host's display slot. */
public final class DisplayOwnerPolicy {
    private DisplayOwnerPolicy() {
    }

    public static boolean matches(Object currentOwner, Object requestingOwner) {
        return currentOwner != null && currentOwner == requestingOwner;
    }
}
