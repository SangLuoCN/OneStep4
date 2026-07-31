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
                "secure=0\nstatusbar=1\nprimaryhome_enhancement=0\n"
                        + "module=1\nzygisk=0\n"
                        + "lsposed=1\nlsposed_backend=1\nstandalone_backend=0\n");

        assertNotNull(state);
        assertFalse(state.secureWindowEnabled);
        assertTrue(state.statusBarOverlayEnabled);
        assertFalse(state.primaryHomeEnhancementEnabled);
        assertTrue(state.moduleInstalled);
        assertFalse(state.zygiskPayloadActive);
        assertTrue(state.lsposedInstalled);
        assertTrue(state.lsposedBackendActive);
        assertFalse(state.standaloneBackendActive);
    }

    @Test
    public void parseAcceptsReorderedOutputAndIgnoresUnrelatedLines() {
        ZygiskHookConfig.State state = ZygiskHookConfig.parse(
                "diagnostic=value\nzygisk=1\nmodule=1\nstatusbar=0\nsecure=1\n"
                        + "standalone_backend=1\nprimaryhome_enhancement=1\n"
                        + "lsposed_backend=0\nlsposed=0\n");

        assertNotNull(state);
        assertTrue(state.secureWindowEnabled);
        assertFalse(state.statusBarOverlayEnabled);
        assertTrue(state.primaryHomeEnhancementEnabled);
        assertTrue(state.moduleInstalled);
        assertTrue(state.zygiskPayloadActive);
        assertFalse(state.lsposedInstalled);
        assertFalse(state.lsposedBackendActive);
        assertTrue(state.standaloneBackendActive);
    }

    @Test
    public void parseRejectsMissingOrInvalidFields() {
        assertNull(ZygiskHookConfig.parse(
                "secure=1\nstatusbar=1\nprimaryhome_enhancement=1\nmodule=1\n"));
        assertNull(ZygiskHookConfig.parse(
                "secure=1\nstatusbar=1\nmodule=1\nzygisk=1\n"
                        + "lsposed=0\nlsposed_backend=0\nstandalone_backend=1\n"));
        assertNull(ZygiskHookConfig.parse(
                "secure=enabled\nstatusbar=1\nprimaryhome_enhancement=1\n"
                        + "module=1\nzygisk=1\n"
                        + "lsposed=0\nlsposed_backend=0\nstandalone_backend=1\n"));
    }

    @Test
    public void readCommandChecksBothModuleTypesAndBothPayloads() {
        String command = ZygiskHookConfig.readCommand();

        assertTrue(command.contains("/data/adb/modules/onestep40_privapp"));
        assertTrue(command.contains("/data/adb/modules/onestep4_ksu_privapp"));
        assertTrue(command.contains("zygisk/arm64-v8a.so"));
        assertTrue(command.contains("zygisk/armeabi-v7a.so"));
        assertTrue(command.contains("onestep-lsposed-backend-active"));
        assertTrue(command.contains("onestep-standalone-backend-active"));
        assertTrue(command.contains("(lsposed|lspd|vector)"));
        assertTrue(command.contains("disable-primary-home-enhancement"));
        assertTrue(command.contains(
                "disable-status-bar-overlay\" ]; then echo statusbar=1; "
                        + "else echo statusbar=0"));
    }

    @Test
    public void writeCommandUsesIndependentDisableMarkers() {
        String secureOnly = ZygiskHookConfig.writeCommand(true, false, false);
        String statusBarOnly = ZygiskHookConfig.writeCommand(false, true, false);
        String primaryHomeEnhancementOnly =
                ZygiskHookConfig.writeCommand(false, false, true);

        assertTrue(secureOnly.contains(
                "rm -f \"$onestep_config/disable-secure-window\""));
        assertTrue(secureOnly.contains(
                ": > \"$onestep_config/disable-status-bar-overlay\""));
        assertTrue(secureOnly.contains(
                ": > \"$onestep_config/disable-primary-home-enhancement\""));
        assertTrue(statusBarOnly.contains(
                ": > \"$onestep_config/disable-secure-window\""));
        assertTrue(statusBarOnly.contains(
                "rm -f \"$onestep_config/disable-status-bar-overlay\""));
        assertTrue(statusBarOnly.contains(
                ": > \"$onestep_config/disable-primary-home-enhancement\""));
        assertTrue(primaryHomeEnhancementOnly.contains(
                ": > \"$onestep_config/disable-secure-window\""));
        assertTrue(primaryHomeEnhancementOnly.contains(
                ": > \"$onestep_config/disable-status-bar-overlay\""));
        assertTrue(primaryHomeEnhancementOnly.contains(
                "rm -f \"$onestep_config/disable-primary-home-enhancement\""));
    }
}
