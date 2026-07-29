#!/system/bin/sh

MODDIR="${0%/*}"
ZYGISK_NEXT_DIR="/data/adb/modules/zygisksu"
PAYLOAD_DIR="$MODDIR/zygisk-payload"
ARM64_PAYLOAD="$PAYLOAD_DIR/arm64-v8a.so"
ARM32_PAYLOAD="$PAYLOAD_DIR/armeabi-v7a.so"

zygisk_next_active() {
  [ -d "$ZYGISK_NEXT_DIR" ] \
    && [ -f "$ZYGISK_NEXT_DIR/module.prop" ] \
    && grep -q '^id=zygisksu$' "$ZYGISK_NEXT_DIR/module.prop" \
    && [ ! -e "$ZYGISK_NEXT_DIR/disable" ] \
    && [ ! -e "$ZYGISK_NEXT_DIR/remove" ]
}

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
