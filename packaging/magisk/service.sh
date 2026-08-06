#!/system/bin/sh

MODDIR="${0%/*}"
PACKAGE_NAME="com.sangluo.onestep"
HOME_COMPONENT="$PACKAGE_NAME/.MainActivity"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
LOG_FILE="$MODDIR/service.log"
STATE_HELPER="$MODDIR/module-state.sh"
DATA_APP_CLEANUP_HELPER="$MODDIR/remove-data-app-update.sh"
DATA_APP_CLEANUP_MARKER="$MODDIR/remove-data-app-update-pending"
USER_UNLOCK_POLL_INTERVAL_SECONDS=0.2
USER_UNLOCK_WAIT_ATTEMPTS=3000

write_log() {
  message="$1"
  echo "$(date '+%Y-%m-%d %H:%M:%S') $message" >>"$LOG_FILE"
  log -t OneStepModule "$message" >/dev/null 2>&1 || true
}

run_and_log() {
  logged_output="$("$@" 2>&1)"
  logged_status=$?
  if [ -n "$logged_output" ]; then
    echo "$logged_output" >>"$LOG_FILE"
  fi
  return "$logged_status"
}

cleanup_data_app_update_if_pending() {
  if [ ! -e "$DATA_APP_CLEANUP_MARKER" ]; then
    return 0
  fi
  if [ ! -x "$DATA_APP_CLEANUP_HELPER" ]; then
    write_log "无法清理旧 /data/app 更新包：缺少清理脚本"
    return 1
  fi

  write_log "模块已挂载且系统已启动，正在检查旧 /data/app 更新包"
  if run_and_log "$DATA_APP_CLEANUP_HELPER"; then
    rm -f "$DATA_APP_CLEANUP_MARKER"
    write_log "旧 /data/app 更新包检查完成"
    return 0
  fi
  write_log "旧 /data/app 更新包暂未清理，保留标记供下次启动重试"
  return 1
}

restore_for_user() {
  user_id="$1"
  if pm list packages --user "$user_id" 2>/dev/null \
      | grep -qx "package:$PACKAGE_NAME"; then
    write_log "用户 $user_id 已安装应用"
  elif run_and_log cmd package install-existing --user "$user_id" \
      "$PACKAGE_NAME"; then
    write_log "已通过 cmd package 为用户 $user_id 恢复应用"
  elif run_and_log pm install-existing --user "$user_id" "$PACKAGE_NAME"; then
    write_log "已通过 pm 为用户 $user_id 恢复应用"
  else
    write_log "无法为用户 $user_id 恢复应用"
    return
  fi

  if cmd role get-role-holders --user "$user_id" "$VIRTUAL_DISPLAY_ROLE" \
      2>/dev/null | grep -qx "$PACKAGE_NAME"; then
    write_log "用户 $user_id 已获得可信虚拟显示角色"
  elif run_and_log cmd role add-role-holder --user "$user_id" \
      "$VIRTUAL_DISPLAY_ROLE" "$PACKAGE_NAME" 0; then
    write_log "已为用户 $user_id 授予可信虚拟显示角色"
  else
    write_log "无法为用户 $user_id 授予可信虚拟显示角色"
  fi
}

user_unlocked() {
  user_id="$1"
  ce_available="$(getprop "sys.user.$user_id.ce_available")"
  if [ "$ce_available" = "true" ]; then
    return 0
  fi
  if [ -n "$ce_available" ]; then
    return 1
  fi
  dumpsys user 2>/dev/null \
      | sed -n "/UserInfo{$user_id:/,/State:/p" \
      | grep -q 'State: RUNNING_UNLOCKED'
}

resolve_home_component() {
  user_id="$1"
  home_component="$(cmd package resolve-activity --brief --user "$user_id" \
      -a android.intent.action.MAIN \
      -c android.intent.category.HOME 2>/dev/null \
      | grep '^[^[:space:]][^[:space:]]*/[^[:space:]]*$' | tail -n 1)"
  if [ -z "$home_component" ]; then
    home_component="$(pm resolve-activity --brief --user "$user_id" \
        -a android.intent.action.MAIN \
        -c android.intent.category.HOME 2>/dev/null \
        | grep '^[^[:space:]][^[:space:]]*/[^[:space:]]*$' | tail -n 1)"
  fi
  printf '%s\n' "$home_component"
}

onestep_is_selected_home() {
  user_id="$1"
  resolved_home="$(resolve_home_component "$user_id")"
  case "$resolved_home" in
    "$PACKAGE_NAME"/*)
      return 0
      ;;
  esac

  cmd role get-role-holders --user "$user_id" android.app.role.HOME \
      2>/dev/null | grep -qx "$PACKAGE_NAME"
}

restore_onestep_home_selection() {
  user_id="$1"
  selection_attempt=0
  while [ "$selection_attempt" -lt 5 ]; do
    if run_and_log cmd package set-home-activity --user "$user_id" \
        "$HOME_COMPONENT"; then
      write_log "已为用户 $user_id 恢复 OneStep 桌面选择"
      return 0
    fi
    selection_attempt=$((selection_attempt + 1))
    sleep "$USER_UNLOCK_POLL_INTERVAL_SECONDS"
  done
  write_log "无法为用户 $user_id 恢复 OneStep 桌面选择"
  return 1
}

restore_selected_home_when_unlocked() {
  user_id="$1"
  was_selected_before_recovery="$2"
  if [ "$was_selected_before_recovery" != "1" ] \
      && ! onestep_is_selected_home "$user_id"; then
    write_log "已跳过用户 $user_id 的桌面恢复：未选择 OneStep"
    return
  fi

  restore_onestep_home_selection "$user_id"
  unlock_attempt=0
  while ! user_unlocked "$user_id" \
      && [ "$unlock_attempt" -lt "$USER_UNLOCK_WAIT_ATTEMPTS" ]; do
    sleep "$USER_UNLOCK_POLL_INTERVAL_SECONDS"
    unlock_attempt=$((unlock_attempt + 1))
  done
  if [ "$unlock_attempt" -ge "$USER_UNLOCK_WAIT_ATTEMPTS" ] \
      && ! user_unlocked "$user_id"; then
    write_log "等待用户 $user_id 解锁超时，无法恢复桌面"
    return
  fi

  write_log "正在为用户 $user_id 恢复已选择的 OneStep 桌面"
  start_output="$(cmd activity start-activity --user "$user_id" \
      -a android.intent.action.MAIN \
      -c android.intent.category.HOME \
      -n "$HOME_COMPONENT" 2>&1)"
  start_status=$?
  if [ "$start_status" -ne 0 ]; then
    start_output="$(am start --user "$user_id" \
        -a android.intent.action.MAIN \
        -c android.intent.category.HOME \
        -n "$HOME_COMPONENT" 2>&1)"
    start_status=$?
  fi
  if [ -n "$start_output" ]; then
    echo "$start_output" >>"$LOG_FILE"
  fi
  if [ "$start_status" -eq 0 ] \
      && ! echo "$start_output" | grep -Eqi '(^|[[:space:]])(Error|Exception):'; then
    write_log "已为用户 $user_id 恢复已选择的 OneStep 桌面"
  else
    write_log "为用户 $user_id 恢复已选择的 OneStep 桌面失败：状态码=$start_status"
  fi
}

hyperos_third_party_home_selected() {
  user_id="$1"
  hyperos_version="$(getprop ro.mi.os.version.name 2>/dev/null)"
  case "$hyperos_version" in
    OS*) ;;
    *) return 1 ;;
  esac

  resolved_home="$(resolve_home_component "$user_id")"
  case "$resolved_home" in
    com.miui.home/*|com.mi.android.globallauncher/*)
      return 1
      ;;
  esac
  onestep_is_selected_home "$user_id"
}

restore_hyperos_gesture_navigation() {
  user_id="$1"
  marker="$MODDIR/hook-config/enable-hyperos-third-party-gesture"
  if [ ! -e "$marker" ]; then
    write_log "已跳过 HyperOS 手势恢复：功能标记不存在"
    return 0
  fi
  if ! hyperos_third_party_home_selected "$user_id"; then
    write_log "已跳过 HyperOS 手势恢复：OneStep 不是已选择的第三方桌面"
    return 0
  fi

  if [ -x "$STATE_HELPER" ]; then
    "$STATE_HELPER" snapshot-navigation >/dev/null 2>&1 || true
  fi
  settings put global force_fsg_nav_bar 1 >/dev/null 2>&1
  fsg_mode="$(settings get global force_fsg_nav_bar 2>/dev/null)"
  if [ "$fsg_mode" = "1" ]; then
    write_log "已为用户 $user_id 恢复 HyperOS 手势导航"
    return 0
  fi
  write_log "HyperOS 手势导航恢复失败：force=$fsg_mode"
  return 1
}

prepare_qq_original_media_storage() {
  user_id="$1"
  qq_package="com.tencent.mobileqq"
  if ! pm list packages --user "$user_id" 2>/dev/null \
      | grep -qx "package:$qq_package"; then
    write_log "已跳过 QQ 原图目录初始化：用户 $user_id 未安装 QQ"
    return 0
  fi

  if [ -x "$STATE_HELPER" ]; then
    "$STATE_HELPER" snapshot-qq-appops "$user_id" >/dev/null 2>&1 || true
  fi
  run_and_log cmd appops set --user "$user_id" --uid "$qq_package" \
      MANAGE_EXTERNAL_STORAGE allow || true
  run_and_log cmd appops set --user "$user_id" "$qq_package" \
      WRITE_EXTERNAL_STORAGE allow || true
  qq_media_dir="/storage/emulated/$user_id/Tencent/MobileQQ/chatpic/Temp"
  if mkdir -p "$qq_media_dir" 2>/dev/null; then
    write_log "已初始化 QQ 原图媒体目录：$qq_media_dir"
  else
    write_log "无法初始化 QQ 原图媒体目录：$qq_media_dir"
  fi
}

write_log "正在等待 Android 启动完成"
if [ -x "$MODDIR/statusbar-post-fs-data.sh" ]; then
  write_log "正在检查可选的 Zygisk 状态栏覆盖层"
  "$MODDIR/statusbar-post-fs-data.sh"
fi

boot_wait=0
while [ "$(getprop sys.boot_completed)" != "1" ] && [ "$boot_wait" -lt 180 ]; do
  sleep 2
  boot_wait=$((boot_wait + 2))
done

current_user="$(cmd activity get-current-user 2>/dev/null)"
case "$current_user" in
  ''|*[!0-9]*)
    current_user=0
    ;;
esac
onestep_home_was_selected=0
if [ -e "$MODDIR/restore-home-selection" ] \
    || onestep_is_selected_home "$current_user"; then
  onestep_home_was_selected=1
fi

cleanup_data_app_update_if_pending || true
restore_for_user 0
prepare_qq_original_media_storage 0

if [ "$current_user" != "0" ]; then
  restore_for_user "$current_user"
  prepare_qq_original_media_storage "$current_user"
fi

write_log "OneStep 应用恢复完成"
restore_selected_home_when_unlocked "$current_user" "$onestep_home_was_selected"
rm -f "$MODDIR/restore-home-selection"
restore_hyperos_gesture_navigation "$current_user"
