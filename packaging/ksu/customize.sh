#!/system/bin/sh

if [ "$KSU" != "true" ]; then
  abort "! This package is only for KernelSU"
fi

if [ "$BOOTMODE" != "true" ]; then
  abort "! KernelSU modules must be installed from KernelSU Manager"
fi

ui_print "- OneStep 普通页面无需 ZygiskNext 即可使用"
ui_print "- 可选：安装并启用 ZygiskNext 后支持 FLAG_SECURE 页面正常显示"

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
ZYGISK_PAYLOAD_DIR="$MODPATH/zygisk-payload"
HOOK_CONFIG_DIR="$MODPATH/hook-config"
PREVIOUS_HOOK_CONFIG_DIR="/data/adb/modules/onestep4_ksu_privapp/hook-config"

mkdir -p "$HOOK_CONFIG_DIR"
for hook_marker in disable-secure-window disable-status-bar-overlay; do
  if [ -f "$PREVIOUS_HOOK_CONFIG_DIR/$hook_marker" ]; then
    : >"$HOOK_CONFIG_DIR/$hook_marker"
  fi
done
set_perm_recursive "$HOOK_CONFIG_DIR" 0 0 0700 0600

if [ ! -f "$APK_PATH" ]; then
  abort "! OneStep4 APK is missing from the module"
fi
if [ ! -f "$ZYGISK_PAYLOAD_DIR/arm64-v8a.so" ] \
    || [ ! -f "$ZYGISK_PAYLOAD_DIR/armeabi-v7a.so" ]; then
  abort "! OneStep4 optional Zygisk payload is incomplete"
fi

rm -rf "$MODPATH/zygisk"
touch "$APK_PATH"
touch "${APK_PATH%/*}"
set_perm "$APK_PATH" 0 0 0644
set_perm "$MODPATH/boot-completed.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/statusbar-post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/system/etc/onestep/OneStepStatusBarZeroOverlay.apk" 0 0 0644
set_perm_recursive "$ZYGISK_PAYLOAD_DIR" 0 0 0755 0644

if [ -d /data/adb/modules/zygisksu ] \
    && [ -f /data/adb/modules/zygisksu/module.prop ] \
    && grep -q '^id=zygisksu$' /data/adb/modules/zygisksu/module.prop \
    && [ ! -e /data/adb/modules/zygisksu/disable ] \
    && [ ! -e /data/adb/modules/zygisksu/remove ]; then
  mkdir -p "$MODPATH/zygisk"
  cp -f "$ZYGISK_PAYLOAD_DIR/arm64-v8a.so" "$MODPATH/zygisk/arm64-v8a.so"
  cp -f "$ZYGISK_PAYLOAD_DIR/armeabi-v7a.so" "$MODPATH/zygisk/armeabi-v7a.so"
  set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
  ui_print "- ZygiskNext 已检测到：重启后启用 FLAG_SECURE 显示增强"
else
  ui_print "- ZygiskNext 未启用：以普通页面兼容模式运行"
fi

ui_print "- KernelSU: ${KSU_VER:-unknown} (${KSU_VER_CODE:-unknown})"
ui_print "- OneStep4 will be restored for the active Android user after reboot"

rm -f "$MODPATH/customize.sh"
