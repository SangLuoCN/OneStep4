#!/system/bin/sh

STATE_DIR="/data/system/onestep-module-state"
NAVIGATION_STATE="$STATE_DIR/force_fsg_nav_bar.state"
QQ_PACKAGE="com.tencent.mobileqq"

ensure_state_dir() {
  umask 077
  mkdir -p "$STATE_DIR" || return 1
  chown 0:0 "$STATE_DIR" 2>/dev/null || true
  chmod 0700 "$STATE_DIR" 2>/dev/null || true
  restorecon "$STATE_DIR" >/dev/null 2>&1 || true
}

write_state_file() {
  state_path="$1"
  shift
  state_tmp="$state_path.$$"
  rm -f "$state_tmp"
  if ! printf '%s\n' "$@" >"$state_tmp"; then
    rm -f "$state_tmp"
    return 1
  fi
  chown 0:0 "$state_tmp" 2>/dev/null || true
  chmod 0600 "$state_tmp" 2>/dev/null || true
  restorecon "$state_tmp" >/dev/null 2>&1 || true
  mv -f "$state_tmp" "$state_path"
}

snapshot_navigation() {
  [ ! -f "$NAVIGATION_STATE" ] || return 0
  ensure_state_dir || return 1
  navigation_value="$(settings get global force_fsg_nav_bar 2>/dev/null)"
  case "$navigation_value" in
    ''|null)
      write_state_file "$NAVIGATION_STATE" "absent=1"
      ;;
    *)
      write_state_file "$NAVIGATION_STATE" "value=$navigation_value"
      ;;
  esac
}

read_appop_mode() {
  appop_user="$1"
  appop_scope="$2"
  appop_name="$3"
  if [ "$appop_scope" = "uid" ]; then
    appop_output="$(cmd appops get --user "$appop_user" --uid \
        "$QQ_PACKAGE" "$appop_name" 2>/dev/null)"
  else
    appop_output="$(cmd appops get --user "$appop_user" \
        "$QQ_PACKAGE" "$appop_name" 2>/dev/null)"
  fi
  appop_mode="$(printf '%s\n' "$appop_output" | awk -v requested="$appop_name" '
    {
      line = $0
      sub(/^[[:space:]]*/, "", line)
      sub(/^Uid mode:[[:space:]]*/, "", line)
      prefix = requested ":"
      if (index(line, prefix) == 1) {
        line = substr(line, length(prefix) + 1)
        sub(/^[[:space:]]*/, "", line)
        sub(/[;[:space:]].*$/, "", line)
        print line
        exit
      }
    }
  ')"
  case "$appop_mode" in
    allow|ignore|deny|default|foreground|errored)
      printf '%s\n' "$appop_mode"
      ;;
    *)
      printf '%s\n' default
      ;;
  esac
}

snapshot_qq_appops() {
  appop_user="$1"
  case "$appop_user" in
    ''|*[!0-9]*) return 1 ;;
  esac
  if ! pm list packages --user "$appop_user" 2>/dev/null \
      | grep -qx "package:$QQ_PACKAGE"; then
    return 0
  fi
  appop_state="$STATE_DIR/qq-appops-$appop_user.state"
  [ ! -f "$appop_state" ] || return 0
  ensure_state_dir || return 1
  manage_mode="$(read_appop_mode "$appop_user" uid MANAGE_EXTERNAL_STORAGE)"
  write_mode="$(read_appop_mode "$appop_user" package WRITE_EXTERNAL_STORAGE)"
  write_state_file "$appop_state" \
      "user=$appop_user" \
      "manage_external_storage=$manage_mode" \
      "write_external_storage=$write_mode"
}

list_android_users() {
  cmd user list 2>/dev/null \
      | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p'
}

snapshot_installation() {
  snapshot_navigation || return 1
  installation_users="$(list_android_users)"
  [ -n "$installation_users" ] || installation_users=0
  for installation_user in $installation_users; do
    snapshot_qq_appops "$installation_user" || return 1
  done
}

restore_navigation() {
  [ -f "$NAVIGATION_STATE" ] || return 0
  navigation_record="$(head -n 1 "$NAVIGATION_STATE" 2>/dev/null)"
  case "$navigation_record" in
    absent=1)
      settings delete global force_fsg_nav_bar >/dev/null 2>&1
      ;;
    value=*)
      navigation_value="${navigation_record#value=}"
      settings put global force_fsg_nav_bar "$navigation_value" \
          >/dev/null 2>&1
      ;;
  esac
}

restore_qq_appops() {
  [ -d "$STATE_DIR" ] || return 0
  for appop_state in "$STATE_DIR"/qq-appops-*.state; do
    [ -f "$appop_state" ] || continue
    appop_user="$(sed -n 's/^user=//p' "$appop_state" | head -n 1)"
    manage_mode="$(sed -n 's/^manage_external_storage=//p' \
        "$appop_state" | head -n 1)"
    write_mode="$(sed -n 's/^write_external_storage=//p' \
        "$appop_state" | head -n 1)"
    case "$appop_user" in
      ''|*[!0-9]*) continue ;;
    esac
    if ! pm list packages --user "$appop_user" 2>/dev/null \
        | grep -qx "package:$QQ_PACKAGE"; then
      continue
    fi
    case "$manage_mode" in
      allow|ignore|deny|default|foreground|errored)
        cmd appops set --user "$appop_user" --uid "$QQ_PACKAGE" \
            MANAGE_EXTERNAL_STORAGE "$manage_mode" >/dev/null 2>&1 || true
        ;;
    esac
    case "$write_mode" in
      allow|ignore|deny|default|foreground|errored)
        cmd appops set --user "$appop_user" "$QQ_PACKAGE" \
            WRITE_EXTERNAL_STORAGE "$write_mode" >/dev/null 2>&1 || true
        ;;
    esac
  done
}

restore_all() {
  restore_navigation
  restore_qq_appops
}

case "$1" in
  snapshot-navigation)
    snapshot_navigation
    ;;
  snapshot-qq-appops)
    snapshot_qq_appops "$2"
    ;;
  snapshot-installation)
    snapshot_installation
    ;;
  restore-all)
    restore_all
    ;;
  *)
    echo "用法：$0 {snapshot-navigation|snapshot-qq-appops USER|snapshot-installation|restore-all}" >&2
    exit 2
    ;;
esac
