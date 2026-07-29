#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
REPLACE="
/system/priv-app/OneStep4_v5
/system/priv-app/OneStep4_v6
"
APK_PATH="$MODPATH/system/priv-app/OneStep4/OneStep4.apk"
ZYGISK_PAYLOAD_DIR="$MODPATH/zygisk-payload"
GLOBAL_ZYGISK_TOGGLE="/data/adb/post-fs-data.d/onestep40-zygisk-toggle.sh"

ZYGISK_STATE="$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null)"
if ! echo "$ZYGISK_STATE" | grep -q "value=1"; then
  rm -rf "$MODPATH/zygisk"
  ui_print "- Zygisk 未启用：OneStep 普通页面仍可正常使用"
  ui_print "! FLAG_SECURE 页面将由系统显示为黑屏；启用 Zygisk 后可正常显示"
else
  mkdir -p "$MODPATH/zygisk"
  cp -f "$ZYGISK_PAYLOAD_DIR/arm64-v8a.so" "$MODPATH/zygisk/arm64-v8a.so"
  cp -f "$ZYGISK_PAYLOAD_DIR/armeabi-v7a.so" "$MODPATH/zygisk/armeabi-v7a.so"
  set_perm_recursive "$MODPATH/zygisk" 0 0 0755 0644
  ui_print "- Zygisk 已启用：FLAG_SECURE 虚拟屏显示增强可用"
fi

mkdir -p /data/adb/post-fs-data.d
cp -f "$MODPATH/zygisk-toggle.sh" "$GLOBAL_ZYGISK_TOGGLE"
chown 0:0 "$GLOBAL_ZYGISK_TOGGLE"
chmod 0755 "$GLOBAL_ZYGISK_TOGGLE"
set_perm_recursive "$ZYGISK_PAYLOAD_DIR" 0 0 0755 0644
set_perm "$MODPATH/zygisk-toggle.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755

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
    ui_print "- Trusted virtual display role already granted"
  elif cmd role add-role-holder --user 0 "$VIRTUAL_DISPLAY_ROLE" \
      "$PACKAGE_NAME" 0 >/dev/null 2>&1; then
    ui_print "- Granted trusted virtual display role"
  else
    ui_print "- Role grant deferred to OneStep first launch"
  fi
else
  ui_print "- Package scan pending; role grant deferred to OneStep first launch"
fi

rm -f "$MODPATH/customize.sh"
