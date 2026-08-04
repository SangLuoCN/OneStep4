#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
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
GLOBAL_ZYGISK_TOGGLE="/data/adb/post-fs-data.d/onestep40-zygisk-toggle.sh"
HOOK_CONFIG_DIR="$MODPATH/hook-config"
PREVIOUS_HOOK_CONFIG_DIR="/data/adb/modules/onestep40_privapp/hook-config"
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

ZYGISK_STATE="$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null)"

zygisk_enabled() {
  if [ "$ZYGISK_ENABLED" = "1" ] \
      || echo "$ZYGISK_STATE" | grep -q "value=1"; then
    return 0
  fi
  for zygote_pid in $(pidof zygote64 zygote 2>/dev/null); do
    if grep -q '/libzygisk\.so' "/proc/$zygote_pid/maps" 2>/dev/null; then
      return 0
    fi
  done
  return 1
}

SDK_INT="$(getprop ro.build.version.sdk)"
case "$SDK_INT" in
  ''|*[!0-9]*) SDK_INT=0 ;;
esac
if [ "$SDK_INT" -ge 37 ]; then
  if ! printf '%s\n' \
      'allow priv_app_36 default_android_service service_manager find' \
      'allow priv_app_36 magisk binder { call transfer }' \
      'allow magisk priv_app_36 binder { call transfer }' \
      'allow priv_app_36 magisk unix_stream_socket connectto' \
      'allow magisk priv_app_36 fd use' \
      >>"$MODPATH/sepolicy.rule"; then
    abort "! 无法写入 Android 17 ROOT 兼容策略"
  fi
  ui_print "- 已启用 Android 17 特权应用 ROOT 兼容策略"
fi
if [ "$SDK_INT" -lt 29 ]; then
  rm -rf "$MODPATH/zygisk"
  ui_print "- 当前 Android API $SDK_INT：Zygisk Hook 仅支持 Android 10 及以上"
elif ! zygisk_enabled; then
  rm -rf "$MODPATH/zygisk"
  if lsposed_active; then
    ui_print "- LSPosed/Vector 已检测：使用框架 Hook 后端"
  else
    ui_print "- Zygisk 未启用：OneStep 普通页面仍可正常使用"
  fi
  ui_print "! FLAG_SECURE 页面将由系统显示为黑屏；启用 Zygisk 后可正常显示"
else
  mkdir -p "$MODPATH/zygisk"
  cp -f "$ZYGISK_PAYLOAD_DIR/arm64-v8a.so" "$MODPATH/zygisk/arm64-v8a.so"
  cp -f "$ZYGISK_PAYLOAD_DIR/armeabi-v7a.so" "$MODPATH/zygisk/armeabi-v7a.so"
  set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
  if lsposed_active; then
    ui_print "- LSPosed/Vector + Zygisk 已检测：系统 Hook 使用 LSPosed，通用应用图片 Hook 使用 Zygisk"
  else
    ui_print "- Zygisk 已启用：FLAG_SECURE 虚拟屏显示增强可用"
  fi
fi

mkdir -p /data/adb/post-fs-data.d
cp -f "$MODPATH/zygisk-toggle.sh" "$GLOBAL_ZYGISK_TOGGLE"
chown 0:0 "$GLOBAL_ZYGISK_TOGGLE"
chmod 0755 "$GLOBAL_ZYGISK_TOGGLE"
set_perm_recursive "$ZYGISK_PAYLOAD_DIR" 0 0 0755 0644
set_perm "$MODPATH/zygisk-toggle.sh" 0 0 0755
set_perm "$MODPATH/post-fs-data.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755

if [ -f "$APK_PATH" ]; then
  touch "$APK_PATH"
  touch "${APK_PATH%/*}"
  set_perm "$APK_PATH" 0 0 0644
fi

if [ -f "$MODPATH/service.sh" ]; then
  set_perm "$MODPATH/service.sh" 0 0 0755
fi
if [ -f "$MODPATH/statusbar-post-fs-data.sh" ]; then
  set_perm "$MODPATH/statusbar-post-fs-data.sh" 0 0 0755
fi
if [ -f "$MODPATH/system/etc/onestep/OneStepStatusBarZeroOverlay.apk" ]; then
  set_perm "$MODPATH/system/etc/onestep/OneStepStatusBarZeroOverlay.apk" 0 0 0644
fi

if pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  if cmd role get-role-holders --user 0 "$VIRTUAL_DISPLAY_ROLE" 2>/dev/null \
      | grep -qx "$PACKAGE_NAME"; then
    ui_print "- 可信虚拟显示角色已授权"
  elif cmd role add-role-holder --user 0 "$VIRTUAL_DISPLAY_ROLE" \
      "$PACKAGE_NAME" 0 >/dev/null 2>&1; then
    ui_print "- 已授予可信虚拟显示角色"
  else
    ui_print "- 暂未授予可信虚拟显示角色，将在首次启动 OneStep 时重试"
  fi
else
  ui_print "- 系统尚未扫描到 OneStep4，将在首次启动时授予可信虚拟显示角色"
fi

rm -f "$MODPATH/customize.sh"
