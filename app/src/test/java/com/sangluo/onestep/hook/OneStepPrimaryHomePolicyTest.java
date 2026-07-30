package com.sangluo.onestep.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;

import org.junit.Test;

public class OneStepPrimaryHomePolicyTest {
    @Test
    public void acceptsOnlyMarkedOneStepHomeLaunchOnOwnedDisplay() {
        assertTrue(OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                Intent.ACTION_MAIN,
                true,
                "com.sangluo.onestep",
                "com.example.launcher",
                "OneStepSlot-0/P100/G1"));
    }

    @Test
    public void rejectsUnmarkedOrForeignLaunches() {
        assertFalse(OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                false,
                Intent.ACTION_MAIN,
                true,
                "com.sangluo.onestep",
                "com.example.launcher",
                "OneStepSlot-0/P100/G1"));
        assertFalse(OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                Intent.ACTION_MAIN,
                true,
                "com.example.caller",
                "com.example.launcher",
                "OneStepSlot-0/P100/G1"));
    }

    @Test
    public void rejectsNonHomeOrNonOneStepDisplay() {
        assertFalse(OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                Intent.ACTION_MAIN,
                false,
                "com.sangluo.onestep",
                "com.example.launcher",
                "OneStepSlot-0/P100/G1"));
        assertFalse(OneStepPrimaryHomePolicy.shouldCreateWorkspace(
                true,
                Intent.ACTION_MAIN,
                true,
                "com.sangluo.onestep",
                "com.example.launcher",
                "Built-in Screen"));
    }
}
