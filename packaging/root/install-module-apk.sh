#!/system/bin/sh

ONESTEP_PACKAGE_NAME="com.sangluo.onestep"
ONESTEP_HOME_COMPONENT="$ONESTEP_PACKAGE_NAME/.MainActivity"

onestep_install_print() {
  if command -v ui_print >/dev/null 2>&1; then
    ui_print "$1"
  else
    echo "$1"
  fi
}

onestep_package_version_code() {
  pm list packages --show-versioncode "$ONESTEP_PACKAGE_NAME" 2>/dev/null \
      | sed -n "s/^package:$ONESTEP_PACKAGE_NAME[[:space:]]*versionCode:\([0-9][0-9]*\).*$/\1/p" \
      | head -n 1
}

onestep_package_paths() {
  pm path "$ONESTEP_PACKAGE_NAME" 2>/dev/null | sed -n 's/^package://p'
}

onestep_resolve_home_component() {
  onestep_home_user="$1"
  onestep_resolved_home="$(cmd package resolve-activity --brief \
      --user "$onestep_home_user" \
      -a android.intent.action.MAIN \
      -c android.intent.category.HOME 2>/dev/null \
      | grep '^[^[:space:]][^[:space:]]*/[^[:space:]]*$' | tail -n 1)"
  if [ -z "$onestep_resolved_home" ]; then
    onestep_resolved_home="$(pm resolve-activity --brief \
        --user "$onestep_home_user" \
        -a android.intent.action.MAIN \
        -c android.intent.category.HOME 2>/dev/null \
        | grep '^[^[:space:]][^[:space:]]*/[^[:space:]]*$' | tail -n 1)"
  fi
  printf '%s\n' "$onestep_resolved_home"
}

onestep_is_selected_home() {
  onestep_home_user="$1"
  onestep_resolved_home="$(onestep_resolve_home_component "$onestep_home_user")"
  case "$onestep_resolved_home" in
    "$ONESTEP_PACKAGE_NAME"/*)
      return 0
      ;;
  esac
  cmd role get-role-holders --user "$onestep_home_user" android.app.role.HOME \
      2>/dev/null | grep -qx "$ONESTEP_PACKAGE_NAME"
}

onestep_restore_home_selection() {
  onestep_home_user="$1"
  onestep_home_attempt=0
  while [ "$onestep_home_attempt" -lt 5 ]; do
    if cmd package set-home-activity --user "$onestep_home_user" \
        "$ONESTEP_HOME_COMPONENT" >/dev/null 2>&1; then
      return 0
    fi
    onestep_home_attempt=$((onestep_home_attempt + 1))
    sleep 0.2
  done
  return 1
}

onestep_apk_sha256() {
  onestep_hash_path="$1"
  if [ ! -f "$onestep_hash_path" ]; then
    return 0
  fi
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$onestep_hash_path" 2>/dev/null | awk '{print $1}'
    return
  fi
  toybox sha256sum "$onestep_hash_path" 2>/dev/null | awk '{print $1}'
}

install_onestep_module_apk() {
  onestep_expected_apk="$1"
  onestep_expected_version="$2"
  onestep_module_dir="$3"

  if [ ! -f "$onestep_expected_apk" ]; then
    onestep_install_print "! 模块中缺少 OneStep4 APK"
    return 1
  fi
  case "$onestep_expected_version" in
    ''|*[!0-9]*)
      onestep_install_print "! 模块版本代码无效：$onestep_expected_version"
      return 1
      ;;
  esac

  onestep_current_version="$(onestep_package_version_code)"
  onestep_current_paths="$(onestep_package_paths)"
  onestep_current_apk="$(printf '%s\n' "$onestep_current_paths" | head -n 1)"
  onestep_expected_sha256="$(onestep_apk_sha256 "$onestep_expected_apk")"
  onestep_current_sha256="$(onestep_apk_sha256 "$onestep_current_apk")"
  onestep_install_reason=""

  case "$onestep_current_version" in
    ''|*[!0-9]*)
      onestep_install_reason="应用包未安装或无法读取版本"
      ;;
    *)
      if [ "$onestep_current_version" -lt "$onestep_expected_version" ]; then
        onestep_install_reason="已安装版本 $onestep_current_version 低于模块版本 $onestep_expected_version"
      elif [ -z "$onestep_current_paths" ]; then
        onestep_install_reason="软件包管理器未返回可读的 APK 路径"
      elif [ "$onestep_current_version" -eq "$onestep_expected_version" ] \
          && echo "$onestep_current_paths" | grep -q '/OneStep4_v[0-9][0-9]*/'; then
        onestep_install_reason="软件包管理器仍指向旧版系统路径"
      elif [ "$onestep_current_version" -eq "$onestep_expected_version" ] \
          && { [ -z "$onestep_expected_sha256" ] || [ -z "$onestep_current_sha256" ]; }; then
        onestep_install_reason="无法校验相同版本的 APK 内容"
      elif [ "$onestep_current_version" -eq "$onestep_expected_version" ] \
          && [ "$onestep_current_sha256" != "$onestep_expected_sha256" ]; then
        onestep_install_reason="相同版本的 APK 内容与模块不一致"
      fi
      ;;
  esac

  if [ -z "$onestep_install_reason" ]; then
    onestep_install_print "- OneStep4 已是当前版本，无需覆盖安装"
    return 0
  fi

  onestep_install_print "- 正在覆盖安装 OneStep4：$onestep_install_reason"
  onestep_current_user="$(cmd activity get-current-user 2>/dev/null)"
  case "$onestep_current_user" in
    ''|*[!0-9]*) onestep_current_user=0 ;;
  esac
  onestep_home_was_selected=0
  if onestep_is_selected_home "$onestep_current_user"; then
    onestep_home_was_selected=1
    if [ -d "$onestep_module_dir" ]; then
      : >"$onestep_module_dir/restore-home-selection"
    fi
  fi

  onestep_staged_apk="/data/local/tmp/onestep-module-install-$$.apk"
  rm -f "$onestep_staged_apk"
  if ! cp -f "$onestep_expected_apk" "$onestep_staged_apk"; then
    onestep_install_print "! 无法暂存模块 APK"
    return 1
  fi
  chown 2000:2000 "$onestep_staged_apk" 2>/dev/null || true
  chmod 0644 "$onestep_staged_apk" 2>/dev/null || true
  restorecon "$onestep_staged_apk" >/dev/null 2>&1 \
      || chcon u:object_r:shell_data_file:s0 "$onestep_staged_apk" \
          >/dev/null 2>&1 \
      || true

  onestep_install_output="$(pm install -r --user 0 "$onestep_staged_apk" 2>&1)"
  onestep_install_status=$?
  rm -f "$onestep_staged_apk"
  if [ "$onestep_install_status" -ne 0 ] \
      || ! echo "$onestep_install_output" | grep -q 'Success'; then
    onestep_install_print "! OneStep4 覆盖安装失败：$onestep_install_output"
    return 1
  fi

  onestep_updated_version="$(onestep_package_version_code)"
  onestep_updated_paths="$(onestep_package_paths)"
  onestep_updated_apk="$(printf '%s\n' "$onestep_updated_paths" | head -n 1)"
  onestep_updated_sha256="$(onestep_apk_sha256 "$onestep_updated_apk")"
  case "$onestep_updated_version" in
    ''|*[!0-9]*)
      onestep_install_print "! 覆盖安装后仍无法读取 OneStep4 版本"
      return 1
      ;;
    *)
      if [ "$onestep_updated_version" -lt "$onestep_expected_version" ] \
          || [ -z "$onestep_updated_paths" ]; then
        onestep_install_print "! OneStep4 覆盖安装后的版本或路径校验失败"
        return 1
      fi
      if [ "$onestep_updated_version" -eq "$onestep_expected_version" ] \
          && { [ -z "$onestep_expected_sha256" ] \
              || [ "$onestep_updated_sha256" != "$onestep_expected_sha256" ]; }; then
        onestep_install_print "! OneStep4 覆盖安装后的内容校验失败"
        return 1
      fi
      ;;
  esac

  if [ "$onestep_home_was_selected" = "1" ]; then
    if onestep_restore_home_selection "$onestep_current_user"; then
      onestep_install_print "- 已保留 OneStep 系统默认桌面选择"
    else
      onestep_install_print "! 暂未恢复系统默认桌面，将在重启后再尝试一次"
    fi
  fi
  onestep_install_print "- OneStep4 已在模块安装阶段完成覆盖安装"
  return 0
}
