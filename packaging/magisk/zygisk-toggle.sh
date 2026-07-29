#!/system/bin/sh

MODULE_ID="onestep40_privapp"
MODULE_DIR="/data/adb/modules/$MODULE_ID"
UPDATE_DIR="/data/adb/modules_update/$MODULE_ID"
GLOBAL_SCRIPT="/data/adb/post-fs-data.d/onestep40-zygisk-toggle.sh"

if [ ! -d "$MODULE_DIR" ] && [ -d "$UPDATE_DIR" ]; then
  MODULE_DIR="$UPDATE_DIR"
fi

if [ ! -d "$MODULE_DIR" ] || [ -f "$MODULE_DIR/remove" ]; then
  rm -rf "$MODULE_DIR/zygisk" 2>/dev/null
  rm -f "$GLOBAL_SCRIPT"
  exit 0
fi

PAYLOAD_DIR="$MODULE_DIR/zygisk-payload"
ARM64_PAYLOAD="$PAYLOAD_DIR/arm64-v8a.so"
ARM32_PAYLOAD="$PAYLOAD_DIR/armeabi-v7a.so"
ZYGISK_STATE="$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk';" 2>/dev/null)"

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
