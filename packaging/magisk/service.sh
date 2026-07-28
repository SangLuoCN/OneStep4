#!/system/bin/sh

MODDIR="${0%/*}"
PACKAGE_NAME="com.sangluo.onestep"
LAUNCHER_COMPONENT="$PACKAGE_NAME/.HomeRedirectActivity"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
LOG_FILE="$MODDIR/service.log"
PACKAGE_SCAN_WAIT_SECONDS=30

write_log() {
  message="$1"
  echo "$(date '+%Y-%m-%d %H:%M:%S') $message" >>"$LOG_FILE"
  log -t OneStepModule "$message" >/dev/null 2>&1 || true
}

package_known() {
  pm list packages -u 2>/dev/null | grep -qx "package:$PACKAGE_NAME"
}

package_version_code() {
  pm list packages --show-versioncode "$PACKAGE_NAME" 2>/dev/null \
      | sed -n "s/^package:$PACKAGE_NAME[[:space:]]*versionCode:\([0-9][0-9]*\).*$/\1/p" \
      | head -n 1
}

package_paths() {
  pm path "$PACKAGE_NAME" 2>/dev/null | sed -n 's/^package://p'
}

apk_sha256() {
  apk_path="$1"
  if [ ! -f "$apk_path" ]; then
    return 1
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$apk_path" 2>/dev/null | awk '{print $1}'
    return
  fi
  toybox sha256sum "$apk_path" 2>/dev/null | awk '{print $1}'
}

install_module_apk() {
  user_id="$1"
  write_log "Installing module APK for user $user_id from $expected_apk"
  install_output="$(pm install -r --user "$user_id" "$expected_apk" 2>&1)"
  install_status=$?
  if [ -n "$install_output" ]; then
    echo "$install_output" >>"$LOG_FILE"
  fi
  if [ "$install_status" -eq 0 ] && echo "$install_output" | grep -q 'Success'; then
    write_log "Installed module APK for user $user_id"
    return 0
  fi
  write_log "Failed to install module APK for user $user_id: status=$install_status"
  return 1
}

ensure_current_package() {
  user_id="$1"
  expected_version="$2"
  current_version="$(package_version_code)"
  current_paths="$(package_paths)"
  current_apk_path="$(printf '%s\n' "$current_paths" | head -n 1)"
  expected_sha256="$(apk_sha256 "$expected_apk")"
  current_sha256="$(apk_sha256 "$current_apk_path")"
  install_reason=""

  case "$expected_version" in
    ''|*[!0-9]*)
      write_log "Invalid module versionCode: $expected_version"
      return 1
      ;;
  esac

  case "$current_version" in
    ''|*[!0-9]*)
      install_reason="package is not registered with a readable version"
      ;;
    *)
      if [ "$current_version" -lt "$expected_version" ]; then
        install_reason="installed version $current_version is older than $expected_version"
      elif [ -z "$current_paths" ]; then
        install_reason="PackageManager returned no readable APK path"
      elif [ "$current_version" -eq "$expected_version" ] \
          && echo "$current_paths" | grep -q '/OneStep4_v[0-9][0-9]*/'; then
        install_reason="PackageManager still points to a versioned system path"
      elif [ "$current_version" -eq "$expected_version" ] \
          && { [ -z "$expected_sha256" ] || [ -z "$current_sha256" ]; }; then
        install_reason="same-version APK content could not be verified"
      elif [ "$current_version" -eq "$expected_version" ] \
          && [ "$current_sha256" != "$expected_sha256" ]; then
        install_reason="same-version APK content differs from the module"
      fi
      ;;
  esac

  if [ -z "$install_reason" ]; then
    write_log "Package version/path accepted: version=${current_version:-unknown}, paths=${current_paths:-unknown}"
    return 0
  fi

  write_log "Package update required: $install_reason; paths=${current_paths:-unknown}"
  if ! install_module_apk "$user_id"; then
    return 1
  fi

  updated_version="$(package_version_code)"
  updated_paths="$(package_paths)"
  updated_apk_path="$(printf '%s\n' "$updated_paths" | head -n 1)"
  updated_sha256="$(apk_sha256 "$updated_apk_path")"
  write_log "Package after update: version=${updated_version:-unknown}, paths=${updated_paths:-unknown}"
  case "$updated_version" in
    ''|*[!0-9]*)
      write_log "Package version is still unreadable after update"
      return 1
      ;;
    *)
      if [ "$updated_version" -lt "$expected_version" ] || [ -z "$updated_paths" ]; then
        write_log "Package update verification failed"
        return 1
      fi
      if [ "$updated_version" -eq "$expected_version" ] \
          && { [ -z "$expected_sha256" ] || [ "$updated_sha256" != "$expected_sha256" ]; }; then
        write_log "Package content verification failed after update"
        return 1
      fi
      ;;
  esac
  return 0
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
expected_apk="/system/priv-app/OneStep4/OneStep4.apk"
if [ ! -f "$expected_apk" ]; then
  write_log "Mounted APK is missing: $expected_apk"
  exit 1
fi

scan_wait=0
while ! package_known && [ "$scan_wait" -lt "$PACKAGE_SCAN_WAIT_SECONDS" ]; do
  sleep 2
  scan_wait=$((scan_wait + 2))
done

if ! ensure_current_package 0 "$module_version_code"; then
  write_log "PackageManager update failed for $PACKAGE_NAME"
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
