package com.sangluo.onestep;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Creates a virtual display that can host HOME and its wallpaper on supported Android builds. */
final class VirtualDisplayHomeSupport {
    private static final String ENHANCEMENT_PROPERTY =
            "onestep.hook.primaryhome_enhancement";
    private static final String HOOK_ACTIVE_MARKER =
            "/data/system/onestep-primary-home-hook-active";

    private VirtualDisplayHomeSupport() {
    }

    static CreationResult create(DisplayManager displayManager, String name,
                                 int width, int height, int densityDpi,
                                 Surface surface, int flags) {
        String homeSupportFailure = "";
        boolean hookActive = isPrimaryHomeHookActive();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && shouldRequestHomeSupport(
                        Build.VERSION.SDK_INT,
                        readBooleanProperty(ENHANCEMENT_PROPERTY),
                        hookActive)) {
            try {
                return createHomeSupportedDisplay(displayManager, name, width, height,
                        densityDpi, surface, flags, hookActive);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                homeSupportFailure = describeFailure(e);
            }
        }
        return new CreationResult(displayManager.createVirtualDisplay(
                name, width, height, densityDpi, surface, flags),
                false, homeSupportFailure, hookActive);
    }

    static boolean supportsVirtualDisplayConfig(int sdkInt) {
        return sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
    }

    static boolean shouldRequestHomeSupport(int sdkInt, boolean enhancementEnabled,
                                            boolean hookActive) {
        return supportsVirtualDisplayConfig(sdkInt) && enhancementEnabled && hookActive;
    }

    static boolean shouldUseEnhancedHomeLaunch(boolean homeEntry,
                                               boolean homeSupportRequested,
                                               boolean hookActive) {
        return homeEntry && homeSupportRequested && hookActive;
    }

    static boolean isPrimaryHomeHookActive() {
        return new File(HOOK_ACTIVE_MARKER).isFile();
    }

    private static boolean readBooleanProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getDeclaredMethod(
                    "get", String.class, String.class);
            get.setAccessible(true);
            return "1".equals(get.invoke(null, key, "0"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static CreationResult createHomeSupportedDisplay(
            DisplayManager displayManager, String name,
            int width, int height, int densityDpi,
            Surface surface, int flags, boolean hookActive)
            throws ReflectiveOperationException {
        VirtualDisplayConfig config = buildHomeSupportedConfig(
                name, width, height, densityDpi, surface, flags);
        return new CreationResult(
                displayManager.createVirtualDisplay(config), true, "", hookActive);
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private static VirtualDisplayConfig buildHomeSupportedConfig(
            String name, int width, int height, int densityDpi,
            Surface surface, int flags) throws ReflectiveOperationException {
        VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                name, width, height, densityDpi)
                .setSurface(surface)
                .setFlags(flags);
        Method setHomeSupported = builder.getClass().getMethod(
                "setHomeSupported", boolean.class);
        setHomeSupported.invoke(builder, true);
        return builder.build();
    }

    private static String describeFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getCause() != null) {
            current = ((InvocationTargetException) current).getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName()
                + (message == null || message.isEmpty() ? "" : ":" + message);
    }

    static final class CreationResult {
        final VirtualDisplay display;
        final boolean homeSupportRequested;
        final String homeSupportFailure;
        final boolean primaryHomeHookActive;

        CreationResult(VirtualDisplay display, boolean homeSupportRequested,
                       String homeSupportFailure, boolean primaryHomeHookActive) {
            this.display = display;
            this.homeSupportRequested = homeSupportRequested;
            this.homeSupportFailure = homeSupportFailure == null ? "" : homeSupportFailure;
            this.primaryHomeHookActive = primaryHomeHookActive;
        }
    }
}
