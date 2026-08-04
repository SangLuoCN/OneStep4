package com.sangluo.onestep.feature.drag;

import java.lang.reflect.Method;

/** Reads the boot-time master switch shared by OneStep and hooked app processes. */
public final class ImageDragFeatureGate {
    public static final String PROPERTY = "onestep.hook.image_drag";

    private ImageDragFeatureGate() {
    }

    public static boolean isEnabled() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Method get = properties.getDeclaredMethod(
                    "get", String.class, String.class);
            get.setAccessible(true);
            return "1".equals(get.invoke(null, PROPERTY, "0"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
