#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/dist"
WORK_DIR="$(mktemp -d "$ROOT_DIR/build/magisk-privapp.XXXXXX")"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APP_GRADLE="$ROOT_DIR/app/build.gradle.kts"
APP_VERSION_NAME="$(awk -F'"' '/^[[:space:]]*versionName[[:space:]]*=/ { print $2; exit }' "$APP_GRADLE")"
APP_VERSION_CODE="$(awk -F'=' '/^[[:space:]]*versionCode[[:space:]]*=/ { gsub(/[[:space:]]/, "", $2); print $2; exit }' "$APP_GRADLE")"

if [[ -z "$APP_VERSION_NAME" || ! "$APP_VERSION_CODE" =~ ^[0-9]+$ ]]; then
    echo "无法从 $APP_GRADLE 读取应用版本" >&2
    exit 1
fi

OUT_ZIP="$OUT_DIR/OneStep4-$APP_VERSION_NAME-magisk-$(date +%Y%m%d-%H%M%S).zip"

mkdir -p "$OUT_DIR"

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:assembleDebug

mkdir -p "$WORK_DIR/META-INF/com/google/android"
mkdir -p "$WORK_DIR/system/priv-app/OneStep4"
mkdir -p "$WORK_DIR/system/etc/permissions"

cp "$ROOT_DIR/packaging/magisk/update-binary" \
    "$WORK_DIR/META-INF/com/google/android/update-binary"
cp "$ROOT_DIR/packaging/magisk/updater-script" \
    "$WORK_DIR/META-INF/com/google/android/updater-script"
cp "$ROOT_DIR/packaging/magisk/customize.sh" "$WORK_DIR/customize.sh"
cp "$ROOT_DIR/packaging/magisk/sepolicy.rule" "$WORK_DIR/sepolicy.rule"
cp "$APK_PATH" "$WORK_DIR/system/priv-app/OneStep4/OneStep4.apk"
cp "$ROOT_DIR/packaging/root/privapp-permissions-com.sangluo.onestep.xml" \
    "$WORK_DIR/system/etc/permissions/privapp-permissions-onestep.xml"
awk -v version="$APP_VERSION_NAME" -v version_code="$APP_VERSION_CODE" '
    /^version=/ { print "version=" version; next }
    /^versionCode=/ { print "versionCode=" version_code; next }
    { print }
' "$ROOT_DIR/packaging/magisk/module.prop" > "$WORK_DIR/module.prop"

chmod 0755 "$WORK_DIR/META-INF/com/google/android/update-binary"
chmod 0644 "$WORK_DIR/META-INF/com/google/android/updater-script"
chmod 0755 "$WORK_DIR/customize.sh"
chmod 0644 "$WORK_DIR/sepolicy.rule"
chmod 0644 "$WORK_DIR/system/priv-app/OneStep4/OneStep4.apk"
chmod 0644 "$WORK_DIR/system/etc/permissions/privapp-permissions-onestep.xml"
chmod 0644 "$WORK_DIR/module.prop"

(
    cd "$WORK_DIR"
    zip -qr "$OUT_ZIP" .
)

echo "$OUT_ZIP"
