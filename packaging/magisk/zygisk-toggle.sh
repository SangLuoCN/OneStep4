#!/system/bin/sh

MODULE_ID="onestep40_privapp"
MODULE_DIR="${ONESTEP_MODULE_DIR:-/data/adb/modules/$MODULE_ID}"
UPDATE_DIR="/data/adb/modules_update/$MODULE_ID"
GLOBAL_SCRIPT="/data/adb/post-fs-data.d/onestep40-zygisk-toggle.sh"
LSPOSED_ACTIVE_MARKER="/data/system/onestep-lsposed-backend-active"
STANDALONE_ACTIVE_MARKER="/data/system/onestep-standalone-backend-active"
PRIMARY_HOME_ACTIVE_MARKER="/data/system/onestep-primary-home-hook-active"
ROOT_DISPLAY_COMPAT_ACTIVE_MARKER="/data/system/onestep-root-display-compat-hook-active"

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

if [ -z "$ONESTEP_MODULE_DIR" ] && [ ! -d "$MODULE_DIR" ] \
    && [ -d "$UPDATE_DIR" ]; then
  MODULE_DIR="$UPDATE_DIR"
fi

if [ ! -d "$MODULE_DIR" ] || [ -f "$MODULE_DIR/remove" ]; then
  rm -rf "$MODULE_DIR/zygisk" 2>/dev/null
  rm -f "$LSPOSED_ACTIVE_MARKER" "$STANDALONE_ACTIVE_MARKER" \
    "$PRIMARY_HOME_ACTIVE_MARKER" "$ROOT_DISPLAY_COMPAT_ACTIVE_MARKER"
  rm -f "$GLOBAL_SCRIPT"
  exit 0
fi

PAYLOAD_DIR="$MODULE_DIR/zygisk-payload"
ARM64_PAYLOAD="$PAYLOAD_DIR/arm64-v8a.so"
ARM32_PAYLOAD="$PAYLOAD_DIR/armeabi-v7a.so"
ZYGISK_STATE="$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null)"
SDK_INT="$(getprop ro.build.version.sdk)"
case "$SDK_INT" in
  ''|*[!0-9]*) SDK_INT=0 ;;
esac

rm -f "$LSPOSED_ACTIVE_MARKER" "$STANDALONE_ACTIVE_MARKER" \
  "$PRIMARY_HOME_ACTIVE_MARKER" "$ROOT_DISPLAY_COMPAT_ACTIVE_MARKER"
resetprop -n onestep.hook.primaryhome_enhancement 0
if [ "$SDK_INT" -lt 29 ]; then
  rm -rf "$MODULE_DIR/zygisk"
  resetprop -n onestep.hook.backend unsupported
  exit 0
fi
if [ -e "$MODULE_DIR/hook-config/disable-secure-window" ]; then
  resetprop -n onestep.hook.secure 0
else
  resetprop -n onestep.hook.secure 1
fi
if [ -e "$MODULE_DIR/hook-config/disable-status-bar-overlay" ]; then
  resetprop -n onestep.hook.statusbar 0
else
  resetprop -n onestep.hook.statusbar 1
fi
if [ -e "$MODULE_DIR/hook-config/disable-primary-home-enhancement" ]; then
  primary_home_enhancement=0
else
  primary_home_enhancement=1
fi

if lsposed_active; then
  resetprop -n onestep.hook.backend lsposed
  resetprop -n onestep.hook.primaryhome_enhancement \
    "$primary_home_enhancement"
  rm -rf "$MODULE_DIR/zygisk"
  exit 0
fi

resetprop -n onestep.hook.backend standalone

if ! echo "$ZYGISK_STATE" | grep -q "value=1"; then
  # A top-level zygisk directory makes Magisk ignore the whole module when Zygisk is off.
  rm -rf "$MODULE_DIR/zygisk"
  exit 0
fi

if [ ! -f "$ARM64_PAYLOAD" ] || [ ! -f "$ARM32_PAYLOAD" ]; then
  rm -rf "$MODULE_DIR/zygisk"
  exit 0
fi

STAGING_DIR="$MODULE_DIR/.zygisk-staging"
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" || exit 0
cp -f "$ARM64_PAYLOAD" "$STAGING_DIR/arm64-v8a.so" || exit 0
cp -f "$ARM32_PAYLOAD" "$STAGING_DIR/armeabi-v7a.so" || exit 0
chown 0:0 "$STAGING_DIR" "$STAGING_DIR/arm64-v8a.so" \
  "$STAGING_DIR/armeabi-v7a.so" 2>/dev/null
chmod 0755 "$STAGING_DIR"
chmod 0644 "$STAGING_DIR/arm64-v8a.so" "$STAGING_DIR/armeabi-v7a.so"
rm -rf "$MODULE_DIR/zygisk"
mv "$STAGING_DIR" "$MODULE_DIR/zygisk"
resetprop -n onestep.hook.primaryhome_enhancement \
  "$primary_home_enhancement"
