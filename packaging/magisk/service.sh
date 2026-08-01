#!/system/bin/sh

MODDIR="${0%/*}"
PACKAGE_NAME="com.sangluo.onestep"
HOME_COMPONENT="$PACKAGE_NAME/.MainActivity"
VIRTUAL_DISPLAY_ROLE="android.app.role.COMPANION_DEVICE_APP_STREAMING"
LOG_FILE="$MODDIR/service.log"
PACKAGE_SCAN_WAIT_SECONDS=30
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
  write_log "正在为用户 $user_id 安装模块 APK，来源路径：$expected_apk"
  install_output="$(pm install -r --user "$user_id" "$expected_apk" 2>&1)"
  install_status=$?
  if [ -n "$install_output" ]; then
    echo "$install_output" >>"$LOG_FILE"
  fi
  if [ "$install_status" -eq 0 ] && echo "$install_output" | grep -q 'Success'; then
    write_log "已为用户 $user_id 安装模块 APK"
    return 0
  fi
  write_log "为用户 $user_id 安装模块 APK 失败：状态码=$install_status"
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
      write_log "模块版本代码无效：$expected_version"
      return 1
      ;;
  esac

  case "$current_version" in
    ''|*[!0-9]*)
      install_reason="应用包未注册或无法读取版本"
      ;;
    *)
      if [ "$current_version" -lt "$expected_version" ]; then
        install_reason="已安装版本 $current_version 低于模块版本 $expected_version"
      elif [ -z "$current_paths" ]; then
        install_reason="软件包管理器未返回可读的 APK 路径"
      elif [ "$current_version" -eq "$expected_version" ] \
          && echo "$current_paths" | grep -q '/OneStep4_v[0-9][0-9]*/'; then
        install_reason="软件包管理器仍指向带版本号的系统路径"
      elif [ "$current_version" -eq "$expected_version" ] \
          && { [ -z "$expected_sha256" ] || [ -z "$current_sha256" ]; }; then
        install_reason="无法校验相同版本的 APK 内容"
      elif [ "$current_version" -eq "$expected_version" ] \
          && [ "$current_sha256" != "$expected_sha256" ]; then
        install_reason="相同版本的 APK 内容与模块不一致"
      fi
      ;;
  esac

  if [ -z "$install_reason" ]; then
    write_log "应用版本和路径校验通过：版本=${current_version:-未知}，路径=${current_paths:-未知}"
    return 0
  fi

  write_log "需要更新应用：$install_reason；当前路径=${current_paths:-未知}"
  if ! install_module_apk "$user_id"; then
    return 1
  fi

  updated_version="$(package_version_code)"
  updated_paths="$(package_paths)"
  updated_apk_path="$(printf '%s\n' "$updated_paths" | head -n 1)"
  updated_sha256="$(apk_sha256 "$updated_apk_path")"
  write_log "更新后的应用信息：版本=${updated_version:-未知}，路径=${updated_paths:-未知}"
  case "$updated_version" in
    ''|*[!0-9]*)
      write_log "更新后仍无法读取应用版本"
      return 1
      ;;
    *)
      if [ "$updated_version" -lt "$expected_version" ] || [ -z "$updated_paths" ]; then
        write_log "应用更新校验失败"
        return 1
      fi
      if [ "$updated_version" -eq "$expected_version" ] \
          && { [ -z "$expected_sha256" ] || [ "$updated_sha256" != "$expected_sha256" ]; }; then
        write_log "更新后的应用内容校验失败"
        return 1
      fi
      ;;
  esac
  return 0
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

  settings put global force_fsg_nav_bar 1 >/dev/null 2>&1
  fsg_mode="$(settings get global force_fsg_nav_bar 2>/dev/null)"
  if [ "$fsg_mode" = "1" ]; then
    write_log "已为用户 $user_id 恢复 HyperOS 手势导航"
    return 0
  fi
  write_log "HyperOS 手势导航恢复失败：force=$fsg_mode"
  return 1
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

module_version_code="$(sed -n 's/^versionCode=//p' "$MODDIR/module.prop" | head -n 1)"
expected_apk="/system/priv-app/OneStep4/OneStep4.apk"
if [ ! -f "$expected_apk" ]; then
  write_log "找不到已挂载的 APK：$expected_apk"
  exit 1
fi

scan_wait=0
while ! package_known && [ "$scan_wait" -lt "$PACKAGE_SCAN_WAIT_SECONDS" ]; do
  sleep 2
  scan_wait=$((scan_wait + 2))
done

current_user="$(cmd activity get-current-user 2>/dev/null)"
case "$current_user" in
  ''|*[!0-9]*)
    current_user=0
    ;;
esac
onestep_home_was_selected=0
if onestep_is_selected_home "$current_user"; then
  onestep_home_was_selected=1
fi

if ! ensure_current_package 0 "$module_version_code"; then
  write_log "软件包管理器更新 $PACKAGE_NAME 失败"
  exit 1
fi

restore_for_user 0

if [ "$current_user" != "0" ]; then
  restore_for_user "$current_user"
fi

write_log "OneStep 应用恢复完成"
restore_selected_home_when_unlocked "$current_user" "$onestep_home_was_selected"
restore_hyperos_gesture_navigation "$current_user"
