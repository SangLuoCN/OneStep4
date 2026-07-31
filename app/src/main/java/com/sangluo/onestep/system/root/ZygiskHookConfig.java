package com.sangluo.onestep.system.root;

import java.util.HashMap;
import java.util.Map;

/** Builds and parses root commands for the boot-time Zygisk hook switches. */
public final class ZygiskHookConfig {
    private static final String CONFIG_DIR_NAME = "hook-config";
    private static final String DISABLE_SECURE_WINDOW = "disable-secure-window";
    private static final String DISABLE_STATUS_BAR_OVERLAY = "disable-status-bar-overlay";
    private static final String DISABLE_PRIMARY_HOME_ENHANCEMENT =
            "disable-primary-home-enhancement";
    private static final String MAGISK_MODULE_DIR =
            "/data/adb/modules/onestep40_privapp";
    private static final String KSU_MODULE_DIR =
            "/data/adb/modules/onestep4_ksu_privapp";
    private static final String LSPOSED_ACTIVE_MARKER =
            "/data/system/onestep-lsposed-backend-active";
    private static final String STANDALONE_ACTIVE_MARKER =
            "/data/system/onestep-standalone-backend-active";

    private ZygiskHookConfig() {
    }

    public static String readCommand() {
        return moduleDiscoveryCommand()
                + "if [ -n \"$onestep_module\" ]; then echo module=1; "
                + "else echo module=0; fi; "
                + "if [ -n \"$onestep_module\" ] && [ -e \"$onestep_module/"
                + CONFIG_DIR_NAME + "/" + DISABLE_SECURE_WINDOW
                + "\" ]; then echo secure=0; else echo secure=1; fi; "
                + "if [ -n \"$onestep_module\" ] && [ ! -e \"$onestep_module/"
                + CONFIG_DIR_NAME + "/" + DISABLE_STATUS_BAR_OVERLAY
                + "\" ]; then echo statusbar=1; else echo statusbar=0; fi; "
                + "if [ -n \"$onestep_module\" ] && [ -e \"$onestep_module/"
                + CONFIG_DIR_NAME + "/" + DISABLE_PRIMARY_HOME_ENHANCEMENT
                + "\" ]; then echo primaryhome_enhancement=0; "
                + "else echo primaryhome_enhancement=1; fi; "
                + "if [ -n \"$onestep_module\" ] "
                + "&& [ -f \"$onestep_module/zygisk/arm64-v8a.so\" ] "
                + "&& [ -f \"$onestep_module/zygisk/armeabi-v7a.so\" ]; then "
                + "echo zygisk=1; else echo zygisk=0; fi; "
                + lsposedDiscoveryCommand()
                + "echo lsposed=$onestep_lsposed; "
                + "if [ -f " + quote(LSPOSED_ACTIVE_MARKER) + " ]; then "
                + "echo lsposed_backend=1; else echo lsposed_backend=0; fi; "
                + "if [ -f " + quote(STANDALONE_ACTIVE_MARKER) + " ]; then "
                + "echo standalone_backend=1; else echo standalone_backend=0; fi";
    }

    public static String writeCommand(boolean secureWindowEnabled,
                                      boolean statusBarOverlayEnabled,
                                      boolean primaryHomeEnhancementEnabled) {
        return moduleDiscoveryCommand()
                + "[ -n \"$onestep_module\" ] || exit 2; "
                + "onestep_config=\"$onestep_module/" + CONFIG_DIR_NAME + "\"; "
                + "umask 077; mkdir -p \"$onestep_config\" || exit 1; "
                + "chmod 0700 \"$onestep_config\" || exit 1; "
                + "chown 0:0 \"$onestep_config\" >/dev/null 2>&1 || true; "
                + updateMarkerCommand(DISABLE_SECURE_WINDOW, secureWindowEnabled)
                + updateMarkerCommand(DISABLE_STATUS_BAR_OVERLAY, statusBarOverlayEnabled)
                + updateMarkerCommand(DISABLE_PRIMARY_HOME_ENHANCEMENT,
                primaryHomeEnhancementEnabled)
                + readCommand();
    }

    public static State parse(String output) {
        Map<String, String> values = new HashMap<>();
        if (output != null) {
            for (String line : output.split("\\n")) {
                int separator = line.indexOf('=');
                if (separator > 0) {
                    values.put(line.substring(0, separator).trim(),
                            line.substring(separator + 1).trim());
                }
            }
        }
        if (!isBinary(values.get("secure"))
                || !isBinary(values.get("statusbar"))
                || !isBinary(values.get("primaryhome_enhancement"))
                || !isBinary(values.get("module"))
                || !isBinary(values.get("zygisk"))
                || !isBinary(values.get("lsposed"))
                || !isBinary(values.get("lsposed_backend"))
                || !isBinary(values.get("standalone_backend"))) {
            return null;
        }
        return new State(
                "1".equals(values.get("secure")),
                "1".equals(values.get("statusbar")),
                "1".equals(values.get("primaryhome_enhancement")),
                "1".equals(values.get("module")),
                "1".equals(values.get("zygisk")),
                "1".equals(values.get("lsposed")),
                "1".equals(values.get("lsposed_backend")),
                "1".equals(values.get("standalone_backend")));
    }

    private static String updateMarkerCommand(String marker, boolean enabled) {
        if (enabled) {
            return "rm -f \"$onestep_config/" + marker + "\" || exit 1; ";
        }
        return ": > \"$onestep_config/" + marker + "\" || exit 1; "
                + "chmod 0600 \"$onestep_config/" + marker + "\" || exit 1; ";
    }

    private static String moduleDiscoveryCommand() {
        return "onestep_module=; "
                + "for onestep_candidate in " + quote(MAGISK_MODULE_DIR) + " "
                + quote(KSU_MODULE_DIR) + "; do "
                + "if [ -d \"$onestep_candidate\" ] "
                + "&& [ ! -e \"$onestep_candidate/disable\" ] "
                + "&& [ ! -e \"$onestep_candidate/remove\" ]; then "
                + "onestep_module=\"$onestep_candidate\"; break; fi; done; ";
    }

    private static String lsposedDiscoveryCommand() {
        return "onestep_lsposed=0; "
                + "for onestep_lsp_prop in /data/adb/modules/*/module.prop; do "
                + "[ -f \"$onestep_lsp_prop\" ] || continue; "
                + "onestep_lsp_dir=\"${onestep_lsp_prop%/*}\"; "
                + "[ ! -e \"$onestep_lsp_dir/disable\" ] || continue; "
                + "[ ! -e \"$onestep_lsp_dir/remove\" ] || continue; "
                + "if grep -Eiq '^(id|name)=.*(lsposed|lspd|vector)' "
                + "\"$onestep_lsp_prop\"; then onestep_lsposed=1; break; fi; done; ";
    }

    private static boolean isBinary(String value) {
        return "0".equals(value) || "1".equals(value);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    public static final class State {
        public final boolean secureWindowEnabled;
        public final boolean statusBarOverlayEnabled;
        public final boolean primaryHomeEnhancementEnabled;
        public final boolean moduleInstalled;
        public final boolean zygiskPayloadActive;
        public final boolean lsposedInstalled;
        public final boolean lsposedBackendActive;
        public final boolean standaloneBackendActive;

        State(boolean secureWindowEnabled, boolean statusBarOverlayEnabled,
              boolean primaryHomeEnhancementEnabled,
              boolean moduleInstalled, boolean zygiskPayloadActive,
              boolean lsposedInstalled, boolean lsposedBackendActive,
              boolean standaloneBackendActive) {
            this.secureWindowEnabled = secureWindowEnabled;
            this.statusBarOverlayEnabled = statusBarOverlayEnabled;
            this.primaryHomeEnhancementEnabled = primaryHomeEnhancementEnabled;
            this.moduleInstalled = moduleInstalled;
            this.zygiskPayloadActive = zygiskPayloadActive;
            this.lsposedInstalled = lsposedInstalled;
            this.lsposedBackendActive = lsposedBackendActive;
            this.standaloneBackendActive = standaloneBackendActive;
        }
    }
}
