#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"

if pm path "$PACKAGE_NAME" >/dev/null 2>&1; then
  restore_role_qualification() {
    cmd role set-bypassing-role-qualification false >/dev/null 2>&1
  }

  trap restore_role_qualification EXIT INT TERM
  if cmd role get-role-holders --user 0 "$VIRTUAL_DISPLAY_ROLE" 2>/dev/null \
      | grep -qx "$PACKAGE_NAME"; then
    ui_print "- Trusted virtual display role already granted"
  elif cmd role set-bypassing-role-qualification true >/dev/null 2>&1 \
      && cmd role add-role-holder --user 0 "$VIRTUAL_DISPLAY_ROLE" \
      "$PACKAGE_NAME" 0 >/dev/null 2>&1; then
    ui_print "- Granted trusted virtual display role"
  else
    ui_print "- Role grant deferred to OneStep first launch"
  fi
  restore_role_qualification
  trap - EXIT INT TERM
else
  ui_print "- Package scan pending; role grant deferred to OneStep first launch"
fi

rm -f "$MODPATH/customize.sh"
