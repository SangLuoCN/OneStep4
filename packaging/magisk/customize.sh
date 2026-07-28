#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
REPLACE="
/system/priv-app/OneStep4_v5
/system/priv-app/OneStep4_v6
"
APK_PATH="$MODPATH/system/priv-app/OneStep4/OneStep4.apk"

ZYGISK_STATE="$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null)"
if ! echo "$ZYGISK_STATE" | grep -q "value=1"; then
  ui_print "! 请在 Magisk 设置中启用 Zygisk，否则 FLAG_SECURE 页面仍会显示黑屏"
fi

if [ -f "$APK_PATH" ]; then
  touch "$APK_PATH"
  touch "${APK_PATH%/*}"
  set_perm "$APK_PATH" 0 0 0644
fi

if [ -f "$MODPATH/service.sh" ]; then
  set_perm "$MODPATH/service.sh" 0 0 0755
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
