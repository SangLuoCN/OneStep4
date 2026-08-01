#!/system/bin/sh

MODDIR="${0%/*}"
OVERLAY_PATH="/system/etc/onestep/OneStepStatusBarZeroOverlay.apk"
IDMAP_PATH="/data/resource-cache/system@etc@onestep@OneStepStatusBarZeroOverlay.apk@idmap"
TARGET_PATH="/system/framework/framework-res.apk"
LOG_FILE="$MODDIR/statusbar-overlay.log"
ZYGISK_DIR="$MODDIR/zygisk"
DISABLE_STATUS_BAR_HOOK="$MODDIR/hook-config/disable-status-bar-overlay"

lsposed_active() {
  for module_prop in /data/adb/modules/*/module.prop; do
    [ -f "$module_prop" ] || continue
    module_dir="${module_prop%/*}"
    [ ! -e "$module_dir/disable" ] || continue
    [ ! -e "$module_dir/remove" ] || continue
    if grep -Eiq '^(id|name)=.*(lsposed|lspd|vector)' "$module_prop"; then
      return 0
    fi
  done
  return 1
}

write_log() {
  message="$1"
  echo "$(date '+%Y-%m-%d %H:%M:%S') $message" >>"$LOG_FILE"
  log -t OneStepStatusBarOverlay "$message" >/dev/null 2>&1 || true
}

zygisk_payload_active() {
  if lsposed_active; then
    return 0
  fi
  [ -f "$ZYGISK_DIR/arm64-v8a.so" ] \
    && [ -f "$ZYGISK_DIR/armeabi-v7a.so" ]
}

if [ -e "$DISABLE_STATUS_BAR_HOOK" ]; then
  rm -f "$IDMAP_PATH"
  write_log "用户已禁用状态栏 Hook，覆盖层同步停用"
  exit 0
fi

if ! zygisk_payload_active; then
  rm -f "$IDMAP_PATH"
  write_log "Zygisk 组件未激活，状态栏覆盖层已停用"
  exit 0
fi

if [ ! -f "$OVERLAY_PATH" ] || [ ! -f "$TARGET_PATH" ]; then
  write_log "缺少覆盖层或系统框架资源，状态栏覆盖层已停用"
  exit 0
fi

mkdir -p /data/resource-cache
rm -f "$IDMAP_PATH"

IDMAP_TOOL=
if [ -x /system/bin/idmap2 ]; then
  IDMAP_TOOL=/system/bin/idmap2
elif command -v idmap2 >/dev/null 2>&1; then
  IDMAP_TOOL="$(command -v idmap2)"
fi

if [ -n "$IDMAP_TOOL" ] && "$IDMAP_TOOL" create \
      --target-apk-path "$TARGET_PATH" \
      --overlay-apk-path "$OVERLAY_PATH" \
      --idmap-path "$IDMAP_PATH" \
      --ignore-overlayable >>"$LOG_FILE" 2>&1; then
  write_log "已使用 idmap2 生成限定显示范围的状态栏资源映射"
elif [ -n "$IDMAP_TOOL" ] && "$IDMAP_TOOL" create \
      --target-apk-path "$TARGET_PATH" \
      --overlay-apk-path "$OVERLAY_PATH" \
      --idmap-path "$IDMAP_PATH" \
      --policy public \
      --policy system >>"$LOG_FILE" 2>&1; then
  write_log "已使用 public/system 策略生成状态栏资源映射"
elif [ -x /system/bin/idmap ] && /system/bin/idmap --path \
      "$TARGET_PATH" "$OVERLAY_PATH" "$IDMAP_PATH" >>"$LOG_FILE" 2>&1; then
  IDMAP_TOOL=/system/bin/idmap
  write_log "已使用旧版 idmap 生成限定显示范围的状态栏资源映射"
else
  rm -f "$IDMAP_PATH"
  write_log "没有可用的兼容 idmap 生成方式，状态栏覆盖层已停用"
  exit 0
fi

chown root:root "$IDMAP_PATH" >/dev/null 2>&1 || true
chmod 0644 "$IDMAP_PATH" >/dev/null 2>&1 || true
restorecon "$IDMAP_PATH" >/dev/null 2>&1 || true

if [ "${IDMAP_TOOL##*/}" = idmap2 ] && "$IDMAP_TOOL" dump \
      --idmap-path "$IDMAP_PATH" 2>/dev/null | grep -q 'dimen/status_bar_height'; then
  write_log "状态栏资源映射校验通过"
elif [ "${IDMAP_TOOL##*/}" = idmap ] && "$IDMAP_TOOL" --inspect \
      "$IDMAP_PATH" >/dev/null 2>&1; then
  write_log "旧版状态栏 idmap 结构校验通过"
else
  rm -f "$IDMAP_PATH"
  write_log "idmap 校验失败，状态栏覆盖层已停用"
fi
