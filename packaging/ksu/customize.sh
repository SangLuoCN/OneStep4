#!/system/bin/sh

if [ "$KSU" != "true" ]; then
  abort "! This package is only for KernelSU"
fi

if [ "$BOOTMODE" != "true" ]; then
  abort "! KernelSU modules must be installed from KernelSU Manager"
fi

ksu_version_code="${KSU_VER_CODE:-0}"
case "$ksu_version_code" in
  ''|*[!0-9]*)
    ksu_version_code=0
    ;;
esac

if [ "$ksu_version_code" -ge 30000 ] && [ ! -d /data/adb/metamodule ]; then
  ui_print "! KernelSU 3.x does not mount module system files by itself"
  ui_print "! Install and enable meta-overlayfs (or a compatible metamodule), reboot,"
  abort "! then install the OneStep4 KernelSU module again"
fi

REPLACE="
/system/priv-app/OneStep4_v5
/system/priv-app/OneStep4_v6
"

APK_PATH="$MODPATH/system/priv-app/OneStep4/OneStep4.apk"

if [ ! -f "$APK_PATH" ]; then
  abort "! OneStep4 APK is missing from the module"
fi

touch "$APK_PATH"
touch "${APK_PATH%/*}"
set_perm "$APK_PATH" 0 0 0644
set_perm "$MODPATH/boot-completed.sh" 0 0 0755

ui_print "- KernelSU: ${KSU_VER:-unknown} (${KSU_VER_CODE:-unknown})"
ui_print "- OneStep4 will be restored for the active Android user after reboot"

rm -f "$MODPATH/customize.sh"
