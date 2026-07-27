package com.sangluo.onestep.data.settings;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OneStepSettingsTest {
    @Test
    public void validatesSupportedGridLayouts() {
        assertTrue(OneStepSettings.isSupportedGridLayout(4, 3));
        assertTrue(OneStepSettings.isSupportedGridLayout(5, 4));
        assertTrue(OneStepSettings.isSupportedGridLayout(6, 5));
        assertFalse(OneStepSettings.isSupportedGridLayout(4, 5));
    }

    @Test
    public void clampsUserControlledScales() {
        assertEquals(OneStepSettings.TOP_APP_ICON_SCALE_MIN,
                OneStepSettings.sanitizeTopAppIconScale(1));
        assertEquals(OneStepSettings.TOP_APP_ICON_SCALE_MAX,
                OneStepSettings.sanitizeTopAppIconScale(999));
        assertEquals(100, OneStepSettings.sanitizeTopAppIconScale(100));
        assertEquals(OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_MIN,
                OneStepSettings.sanitizeOneStepTriggerAreaScale(1));
        assertEquals(OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_MAX,
                OneStepSettings.sanitizeOneStepTriggerAreaScale(999));
        assertEquals(OneStepSettings.TOP_NAV_VERTICAL_MARGIN_SCALE_MIN,
                OneStepSettings.sanitizeTopNavVerticalMarginScale(1));
    }

    @Test
    public void convertsLegacyTriggerAreaDpToPercentScale() {
        assertEquals(100, OneStepSettings.oneStepTriggerAreaScaleForSizeDp(156));
        assertEquals(OneStepSettings.ONE_STEP_TRIGGER_AREA_SCALE_MIN,
                OneStepSettings.oneStepTriggerAreaScaleForSizeDp(1));
        assertEquals(156, OneStepSettings.oneStepTriggerAreaSizeDp(100));
    }

    @Test
    public void restoresDefaultForInvalidSideWindowCount() {
        assertEquals(OneStepSettings.DEFAULT_SIDE_WINDOWS,
                OneStepSettings.sanitizeAllowedSideWindowCount(-1));
        assertEquals(6, OneStepSettings.sanitizeAllowedSideWindowCount(6));
    }
}
