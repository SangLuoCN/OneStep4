#!/system/bin/sh

MODDIR="${0%/*}"
ZYGISK_NEXT_DIR="/data/adb/modules/zygisksu"
PAYLOAD_DIR="$MODDIR/zygisk-payload"
ARM64_PAYLOAD="$PAYLOAD_DIR/arm64-v8a.so"
ARM32_PAYLOAD="$PAYLOAD_DIR/armeabi-v7a.so"
LSPOSED_ACTIVE_MARKER="/data/system/onestep-lsposed-backend-active"
STANDALONE_ACTIVE_MARKER="/data/system/onestep-standalone-backend-active"
PRIMARY_HOME_ACTIVE_MARKER="/data/system/onestep-primary-home-hook-active"

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

set_hook_property() {
  property_name="$1"
  property_value="$2"
  if command -v resetprop >/dev/null 2>&1; then
    resetprop -n "$property_name" "$property_value"
  elif [ -x /data/adb/ksu/bin/resetprop ]; then
    /data/adb/ksu/bin/resetprop -n "$property_name" "$property_value"
  fi
}

rm -f "$LSPOSED_ACTIVE_MARKER" "$STANDALONE_ACTIVE_MARKER" \
  "$PRIMARY_HOME_ACTIVE_MARKER"
set_hook_property onestep.hook.primaryhome_enhancement 0
if [ -e "$MODDIR/hook-config/disable-secure-window" ]; then
  set_hook_property onestep.hook.secure 0
else
  set_hook_property onestep.hook.secure 1
fi
if [ -e "$MODDIR/hook-config/disable-status-bar-overlay" ]; then
  set_hook_property onestep.hook.statusbar 0
else
  set_hook_property onestep.hook.statusbar 1
fi
if [ -e "$MODDIR/hook-config/disable-primary-home-enhancement" ]; then
  primary_home_enhancement=0
else
  primary_home_enhancement=1
fi

zygisk_next_active() {
  [ -d "$ZYGISK_NEXT_DIR" ] \
    && [ -f "$ZYGISK_NEXT_DIR/module.prop" ] \
    && grep -q '^id=zygisksu$' "$ZYGISK_NEXT_DIR/module.prop" \
    && [ ! -e "$ZYGISK_NEXT_DIR/disable" ] \
    && [ ! -e "$ZYGISK_NEXT_DIR/remove" ]
}

if lsposed_active; then
  set_hook_property onestep.hook.backend lsposed
  set_hook_property onestep.hook.primaryhome_enhancement \
    "$primary_home_enhancement"
  rm -rf "$MODDIR/zygisk"
  exit 0
fi

set_hook_property onestep.hook.backend standalone

if ! zygisk_next_active \
    || [ ! -f "$ARM64_PAYLOAD" ] \
    || [ ! -f "$ARM32_PAYLOAD" ]; then
  rm -rf "$MODDIR/zygisk"
  exit 0
fi

STAGING_DIR="$MODDIR/.zygisk-staging"
rm -rf "$STAGING_DIR"
mkdir -p "$STAGING_DIR" || exit 0
cp -f "$ARM64_PAYLOAD" "$STAGING_DIR/arm64-v8a.so" || exit 0
cp -f "$ARM32_PAYLOAD" "$STAGING_DIR/armeabi-v7a.so" || exit 0
chown 0:0 "$STAGING_DIR" "$STAGING_DIR/arm64-v8a.so" \
  "$STAGING_DIR/armeabi-v7a.so" 2>/dev/null
chmod 0755 "$STAGING_DIR"
chmod 0644 "$STAGING_DIR/arm64-v8a.so" "$STAGING_DIR/armeabi-v7a.so"
rm -rf "$MODDIR/zygisk"
mv "$STAGING_DIR" "$MODDIR/zygisk"
set_hook_property onestep.hook.primaryhome_enhancement \
  "$primary_home_enhancement"
