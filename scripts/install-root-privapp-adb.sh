#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PERMISSIONS_XML="$ROOT_DIR/packaging/root/privapp-permissions-com.sangluo.onestep.xml"
TMP_APK="/data/local/tmp/OneStep4.apk"
TMP_XML="/data/local/tmp/privapp-permissions-onestep.xml"
SYSTEM_APK_DIR="/system/priv-app/OneStep4"
SYSTEM_APK="$SYSTEM_APK_DIR/OneStep4.apk"
LEGACY_SYSTEM_APK="/system/priv-app/OneStep40/OneStep40.apk"
LEGACY_SYSTEM_APK_DIR="/system/priv-app/OneStep40"
SYSTEM_XML="/system/etc/permissions/privapp-permissions-onestep.xml"
DEVICE_SERIAL="${1:-${ANDROID_SERIAL:-}}"

adb_cmd() {
    if [[ -n "$DEVICE_SERIAL" ]]; then
        adb -s "$DEVICE_SERIAL" "$@"
    else
        adb "$@"
    fi
}

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug

adb_cmd wait-for-device
adb_cmd root >/dev/null 2>&1 || true
adb_cmd wait-for-device

ADB_UID="$(adb_cmd shell id -u 2>/dev/null | tr -d '\r' || true)"
if [[ "$ADB_UID" == "0" ]]; then
    ROOT_MODE="adb-root"
else
    ROOT_MODE="su"
fi

run_root() {
    local command="$1"
    if [[ "$ROOT_MODE" == "adb-root" ]]; then
        adb_cmd shell "$command"
    else
        adb_cmd shell su -c "$command"
    fi
}

adb_cmd remount >/dev/null 2>&1 || true
run_root "mount -o rw,remount /system >/dev/null 2>&1 || mount -o rw,remount / >/dev/null 2>&1 || true"

PACKAGE_PATHS="$(adb_cmd shell pm path com.sangluo.onestep 2>/dev/null | tr -d '\r' || true)"
if [[ "$PACKAGE_PATHS" == *"package:/data/app"* ]]; then
    adb_cmd uninstall com.sangluo.onestep >/dev/null 2>&1 || true
fi

adb_cmd push "$APK_PATH" "$TMP_APK"
adb_cmd push "$PERMISSIONS_XML" "$TMP_XML"

run_root "mkdir -p '$SYSTEM_APK_DIR' /system/etc/permissions"
run_root "cp '$TMP_APK' '$SYSTEM_APK'"
run_root "cp '$TMP_XML' '$SYSTEM_XML'"
run_root "chmod 0755 '$SYSTEM_APK_DIR'"
run_root "chmod 0644 '$SYSTEM_APK' '$SYSTEM_XML'"
run_root "rm -f '$LEGACY_SYSTEM_APK' >/dev/null 2>&1 || true"
run_root "rmdir '$LEGACY_SYSTEM_APK_DIR' >/dev/null 2>&1 || true"
run_root "rm -f /system/bin/onestep-root-helper.sh /system/etc/init/onestep-root-helper.rc >/dev/null 2>&1 || true"
run_root "chown root:root '$SYSTEM_APK_DIR' '$SYSTEM_APK' '$SYSTEM_XML' >/dev/null 2>&1 || true"
run_root "settings put global hidden_api_policy_pre_p_apps 1 >/dev/null 2>&1 || true"
run_root "settings put global hidden_api_policy_p_apps 1 >/dev/null 2>&1 || true"
run_root "settings put global hidden_api_policy 1 >/dev/null 2>&1 || true"

adb_cmd reboot

echo "Installed OneStep4 as priv-app through $ROOT_MODE. Device is rebooting."
