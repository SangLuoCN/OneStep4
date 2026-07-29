package com.sangluo.onestep;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VirtualDisplaySystemDecorControllerTest {
    @Test
    public void confirmedDisabledRequiresRequestAndFalseEffectivePolicy() {
        VirtualDisplaySystemDecorController.Result result =
                VirtualDisplaySystemDecorController.Result.verified(true, false);

        assertTrue(result.isConfirmedDisabled());
    }

    @Test
    public void forceDesktopOverrideIsNotReportedAsDisabled() {
        VirtualDisplaySystemDecorController.Result result =
                VirtualDisplaySystemDecorController.Result.verified(true, true);

        assertFalse(result.isConfirmedDisabled());
    }

    @Test
    public void unverifiedRequestIsNotReportedAsDisabled() {
        VirtualDisplaySystemDecorController.Result result =
                VirtualDisplaySystemDecorController.Result.unverified(true, "blocked");

        assertFalse(result.isConfirmedDisabled());
    }
}
