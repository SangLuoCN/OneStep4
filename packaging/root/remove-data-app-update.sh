#!/system/bin/sh

PACKAGE_NAME="com.sangluo.onestep"
SYSTEM_APK_PATH="/system/priv-app/OneStep4/OneStep4.apk"

print_message() {
  if command -v ui_print >/dev/null 2>&1; then
    ui_print "$1"
  else
    echo "$1"
  fi
}

active_data_app_paths() {
  pm path "$PACKAGE_NAME" 2>/dev/null | sed -n \
      -e 's|^package:\(/data/app/.*\)$|\1|p' \
      -e 's|^package:\(/mnt/expand/.*/app/.*\)$|\1|p'
}

system_package_is_ready() {
  [ -f "$SYSTEM_APK_PATH" ] || return 1
  pm list packages -s 2>/dev/null | grep -qx "package:$PACKAGE_NAME"
}

wait_for_data_app_removal() {
  removal_attempt=0
  while [ "$removal_attempt" -lt 20 ]; do
    if [ -z "$(active_data_app_paths)" ]; then
      return 0
    fi
    cmd package wait-for-handler --timeout 1000 >/dev/null 2>&1 || true
    sleep 0.2
    removal_attempt=$((removal_attempt + 1))
  done
  [ -z "$(active_data_app_paths)" ]
}

if [ -z "$(active_data_app_paths)" ]; then
  print_message "- 未发现 OneStep4 的 /data/app 更新包"
  exit 0
fi

if ! system_package_is_ready; then
  print_message "! 系统版 OneStep4 尚未挂载或未被 PackageManager 识别，跳过清理"
  exit 1
fi

print_message "- 正在移除 OneStep4 的旧 /data/app 更新包"
uninstall_output="$(cmd package uninstall-system-updates "$PACKAGE_NAME" 2>&1)"
uninstall_status=$?
if [ -n "$uninstall_output" ]; then
  printf '%s\n' "$uninstall_output"
fi
if [ "$uninstall_status" -ne 0 ]; then
  print_message "! PackageManager 无法回退 OneStep4 系统应用更新，保留待处理标记"
  exit 1
fi
if wait_for_data_app_removal; then
  print_message "- 已移除 OneStep4 系统应用更新包，应用数据保持不变"
  exit 0
fi

print_message "! PackageManager 仍指向 OneStep4 的旧 /data/app 包，未执行全局卸载"
exit 1
