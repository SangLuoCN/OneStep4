#!/system/bin/sh

MODDIR="${0%/*}"
PACKAGE_NAME="com.sangluo.onestep"
LAUNCHER_COMPONENT="$PACKAGE_NAME/.HomeRedirectActivity"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
LOG_FILE="$MODDIR/service.log"

write_log() {
  message="$1"
  echo "$(date '+%Y-%m-%d %H:%M:%S') $message" >>"$LOG_FILE"
  log -t OneStepModule "$message" >/dev/null 2>&1 || true
}

package_known() {
  pm list packages -u 2>/dev/null | grep -qx "package:$PACKAGE_NAME"
}

restore_for_user() {
  user_id="$1"
  if cmd package install-existing --user "$user_id" "$PACKAGE_NAME" \
      >>"$LOG_FILE" 2>&1; then
    write_log "Restored package for user $user_id with cmd package"
  elif pm install-existing --user "$user_id" "$PACKAGE_NAME" \
      >>"$LOG_FILE" 2>&1; then
    write_log "Restored package for user $user_id with pm"
  elif pm list packages --user "$user_id" 2>/dev/null \
      | grep -qx "package:$PACKAGE_NAME"; then
    write_log "Package already installed for user $user_id"
  else
    write_log "Failed to restore package for user $user_id"
    return
  fi

  pm enable --user "$user_id" "$PACKAGE_NAME" >>"$LOG_FILE" 2>&1 || true
  pm enable --user "$user_id" "$LAUNCHER_COMPONENT" >>"$LOG_FILE" 2>&1 || true

  if cmd role get-role-holders --user "$user_id" "$VIRTUAL_DISPLAY_ROLE" \
      2>/dev/null | grep -qx "$PACKAGE_NAME"; then
    write_log "Trusted display role already granted for user $user_id"
  elif cmd role add-role-holder --user "$user_id" "$VIRTUAL_DISPLAY_ROLE" \
      "$PACKAGE_NAME" 0 >>"$LOG_FILE" 2>&1; then
    write_log "Granted trusted display role for user $user_id"
  else
    write_log "Trusted display role unavailable for user $user_id"
  fi
}

write_log "Waiting for Android boot completion"
boot_wait=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$boot_wait" -lt 180 ]; do
  sleep 2
  boot_wait=$((boot_wait + 2))
done

module_version_code="$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)"
expected_apk="/system/priv-app/OneStep4_v${module_version_code}/OneStep4.apk"
if [ ! -f "$expected_apk" ]; then
  write_log "Mounted APK is missing: $expected_apk"
  exit 1
fi

scan_wait=0
while ! package_known && [ "$scan_wait" -lt 180 ]; do
  sleep 2
  scan_wait=$((scan_wait + 2))
done

if ! package_known; then
  write_log "PackageManager did not scan $PACKAGE_NAME"
  exit 1
fi

restore_for_user 0

current_user="$(cmd activity get-current-user 2>/dev/null)"
case "$current_user" in
  ''|*[!0-9]*)
    current_user=0
    ;;
esac
if [ "$current_user" != "0" ]; then
  restore_for_user "$current_user"
fi

write_log "OneStep package recovery completed"
