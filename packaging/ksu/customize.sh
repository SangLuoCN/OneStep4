#!/system/bin/sh

if [ "$KSU" != "true" ]; then
  abort "! 此安装包仅适用于 KernelSU"
fi

if [ "$BOOTMODE" != "true" ]; then
  abort "! KernelSU 模块必须通过 KernelSU 管理器安装"
fi

ui_print "- OneStep 普通页面无需 ZygiskNext 即可使用"
ui_print "- 可选：安装并启用 ZygiskNext 后支持 FLAG_SECURE 页面正常显示"

ksu_version_code="${KSU_VER_CODE:-0}"
case "$ksu_version_code" in
  ''|*[!0-9]*)
    ksu_version_code=0
    ;;
esac

SDK_INT="$(getprop ro.build.version.sdk)"
case "$SDK_INT" in
  ''|*[!0-9]*) SDK_INT=0 ;;
esac

if [ "$SDK_INT" -ge 37 ]; then
  if ! printf '%s\n' \
      'allow priv_app_36 default_android_service service_manager find' \
      'allow priv_app_36 ksu binder { call transfer }' \
      'allow ksu priv_app_36 binder { call transfer }' \
      'allow priv_app_36 ksu unix_stream_socket connectto' \
      'allow ksu priv_app_36 fd use' \
      'allow priv_app_36 su binder { call transfer }' \
      'allow su priv_app_36 binder { call transfer }' \
      'allow priv_app_36 su unix_stream_socket connectto' \
      'allow su priv_app_36 fd use' \
      >>"$MODPATH/sepolicy.rule"; then
    abort "! 无法写入 Android 17 ROOT 兼容策略"
  fi
  ui_print "- 已启用 Android 17 特权应用 ROOT 兼容策略"
fi

if [ "$ksu_version_code" -ge 30000 ] && [ ! -d /data/adb/metamodule ]; then
  ui_print "! KernelSU 3.x 不会自行挂载模块中的系统文件"
  ui_print "! 请安装并启用 meta-overlayfs（或兼容的元模块），然后重启"
  abort "! 重启后请重新安装 OneStep4 KernelSU 模块"
fi

for replace_path in \
    /system/priv-app/OneStep4_v5 \
    /system/priv-app/OneStep4_v6; do
  if ! mkdir -p "$MODPATH$replace_path" 2>/dev/null \
      || ! touch "$MODPATH$replace_path/.replace" 2>/dev/null; then
    abort "! 无法创建旧版本目录替换标记：$replace_path"
  fi
done
REPLACE=""

APK_PATH="$MODPATH/system/priv-app/OneStep4/OneStep4.apk"
ZYGISK_PAYLOAD_DIR="$MODPATH/zygisk-payload"
HOOK_CONFIG_DIR="$MODPATH/hook-config"
PREVIOUS_HOOK_CONFIG_DIR="/data/adb/modules/onestep4_ksu_privapp/hook-config"
PREVIOUS_HOOK_CONFIG_EXISTS=false
if [ -d "$PREVIOUS_HOOK_CONFIG_DIR" ]; then
  PREVIOUS_HOOK_CONFIG_EXISTS=true
fi

lsposed_active() {
  for module_prop in /data/adb/modules/*/module.prop; do
    [ -f "$module_prop" ] || continue
    module_dir="${module_prop%/*}"
    [ ! -e "$module_dir/disable" ] || continue
    [ ! -e "$module_dir/remove" ] || continue
    if grep -Eiq '^(id|name)=.*(lsposed|lspd|vector)' "$module_prop"; then
      return 0
    fi
  done
  return 1
}

mkdir -p "$HOOK_CONFIG_DIR"
for hook_marker in disable-secure-window disable-status-bar-overlay \
    disable-primary-home-enhancement enable-hyperos-third-party-gesture; do
  if [ -f "$PREVIOUS_HOOK_CONFIG_DIR/$hook_marker" ]; then
    : >"$HOOK_CONFIG_DIR/$hook_marker"
  fi
done
if [ "$PREVIOUS_HOOK_CONFIG_EXISTS" != "true" ]; then
  : >"$HOOK_CONFIG_DIR/disable-status-bar-overlay"
fi
if [ -f "$PREVIOUS_HOOK_CONFIG_DIR/disable-primary-home" ]; then
  : >"$HOOK_CONFIG_DIR/disable-primary-home-enhancement"
fi
set_perm_recursive "$HOOK_CONFIG_DIR" 0 0 0700 0600

if [ ! -f "$APK_PATH" ]; then
  abort "! 模块中缺少 OneStep4 APK"
fi
if [ ! -f "$ZYGISK_PAYLOAD_DIR/arm64-v8a.so" ] \
    || [ ! -f "$ZYGISK_PAYLOAD_DIR/armeabi-v7a.so" ]; then
  abort "! 模块中的可选 Zygisk 组件不完整"
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

if lsposed_active; then
  ui_print "- LSPosed/Vector 已检测：使用框架 Hook 后端"
  ui_print "! 请在 LSPosed 中启用 OneStep 并勾选“系统框架”、“设置”和“系统桌面”作用域"
elif [ -d /data/adb/modules/zygisksu ] \
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

ui_print "- KernelSU 版本：${KSU_VER:-未知}（版本代码：${KSU_VER_CODE:-未知}）"
ui_print "- 重启后将为当前 Android 用户恢复 OneStep4"

rm -f "$MODPATH/customize.sh"
