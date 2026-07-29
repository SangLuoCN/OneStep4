package com.sangluo.onestep.hook;

import java.util.ArrayList;
import java.util.List;

final class StatusBarOverlayPathPolicy {
    private StatusBarOverlayPathPolicy() {
    }

    static String[] update(String[] paths, String overlayPath, boolean enabled) {
        if (overlayPath == null || overlayPath.isEmpty()) {
            return paths;
        }
        List<String> updated = new ArrayList<>();
        if (paths != null) {
            for (String path : paths) {
                if (path != null && !overlayPath.equals(path)) {
                    updated.add(path);
                }
            }
        }
        if (enabled) {
            updated.add(overlayPath);
        }
        return updated.isEmpty() ? null : updated.toArray(new String[0]);
    }

    static boolean contains(String[] paths, String overlayPath) {
        if (paths == null || overlayPath == null) {
            return false;
        }
        for (String path : paths) {
            if (overlayPath.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
