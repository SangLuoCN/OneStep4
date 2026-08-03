package com.sangluo.onestep.hook;

/**
 * Binary compatibility for devices that still have the retired QQ target hook loaded in zygote.
 * New builds never select QQ as a hook target and sharing uses the standard ACTION_SEND route.
 */
@Deprecated
public final class OneStepImageDragTargetHook {
    private OneStepImageDragTargetHook() {
    }

    public static void install(String packageName, String processName) {
        // Intentionally empty. Remove after all deployed pre-v19 Zygisk payloads have aged out.
    }
}
