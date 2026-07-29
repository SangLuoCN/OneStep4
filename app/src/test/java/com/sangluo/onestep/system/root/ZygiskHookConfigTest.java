package com.sangluo.onestep.system.root;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ZygiskHookConfigTest {
    @Test
    public void parseReadsIndependentHookAndRuntimeStates() {
        ZygiskHookConfig.State state = ZygiskHookConfig.parse(
                "secure=0\nstatusbar=1\nmodule=1\nzygisk=0\n");

        assertNotNull(state);
        assertFalse(state.secureWindowEnabled);
        assertTrue(state.statusBarOverlayEnabled);
        assertTrue(state.moduleInstalled);
        assertFalse(state.zygiskPayloadActive);
    }

    @Test
    public void parseAcceptsReorderedOutputAndIgnoresUnrelatedLines() {
        ZygiskHookConfig.State state = ZygiskHookConfig.parse(
                "diagnostic=value\nzygisk=1\nmodule=1\nstatusbar=0\nsecure=1\n");

        assertNotNull(state);
        assertTrue(state.secureWindowEnabled);
        assertFalse(state.statusBarOverlayEnabled);
        assertTrue(state.moduleInstalled);
        assertTrue(state.zygiskPayloadActive);
    }

    @Test
    public void parseRejectsMissingOrInvalidFields() {
        assertNull(ZygiskHookConfig.parse("secure=1\nstatusbar=1\nmodule=1\n"));
        assertNull(ZygiskHookConfig.parse(
                "secure=enabled\nstatusbar=1\nmodule=1\nzygisk=1\n"));
    }

    @Test
    public void readCommandChecksBothModuleTypesAndBothPayloads() {
        String command = ZygiskHookConfig.readCommand();

        assertTrue(command.contains("/data/adb/modules/onestep40_privapp"));
        assertTrue(command.contains("/data/adb/modules/onestep4_ksu_privapp"));
        assertTrue(command.contains("zygisk/arm64-v8a.so"));
        assertTrue(command.contains("zygisk/armeabi-v7a.so"));
    }

    @Test
    public void writeCommandUsesIndependentDisableMarkers() {
        String secureOnly = ZygiskHookConfig.writeCommand(true, false);
        String statusBarOnly = ZygiskHookConfig.writeCommand(false, true);

        assertTrue(secureOnly.contains(
                "rm -f \"$onestep_config/disable-secure-window\""));
        assertTrue(secureOnly.contains(
                ": > \"$onestep_config/disable-status-bar-overlay\""));
        assertTrue(statusBarOnly.contains(
                ": > \"$onestep_config/disable-secure-window\""));
        assertTrue(statusBarOnly.contains(
                "rm -f \"$onestep_config/disable-status-bar-overlay\""));
    }
}
