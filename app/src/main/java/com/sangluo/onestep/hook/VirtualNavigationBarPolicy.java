package com.sangluo.onestep.hook;

final class VirtualNavigationBarPolicy {
    static final int TYPE_NAVIGATION_BAR = 2019;
    static final int TYPE_NAVIGATION_BAR_PANEL = 2024;
    private static final String DISPLAY_NAME_PREFIX = "OneStepSlot-";
    private static final String MIUI_BOTTOM_CAPTION_TITLE_PREFIX =
            "Miui Bottom Caption of Task=";

    private VirtualNavigationBarPolicy() {
    }

    static boolean shouldHide(String displayName, int windowType) {
        return (windowType == TYPE_NAVIGATION_BAR
                || windowType == TYPE_NAVIGATION_BAR_PANEL)
                && isOneStepVirtualDisplay(displayName);
    }

    static boolean shouldHideBottomCaption(String displayName, CharSequence windowTitle) {
        return isOneStepVirtualDisplay(displayName)
                && windowTitle != null
                && windowTitle.toString().startsWith(MIUI_BOTTOM_CAPTION_TITLE_PREFIX);
    }

    private static boolean isOneStepVirtualDisplay(String displayName) {
        return displayName != null && displayName.startsWith(DISPLAY_NAME_PREFIX);
    }
}
