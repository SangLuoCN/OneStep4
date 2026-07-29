#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"
SOURCE_DIR="$ROOT_DIR/packaging/statusbar-overlay"
OUTPUT_DIR="$ROOT_DIR/app/build/statusbar-overlay"
OUTPUT_APK="$OUTPUT_DIR/OneStepStatusBarZeroOverlay.apk"

find_sdk_dir() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
        echo "$ANDROID_SDK_ROOT"
        return
    fi
    if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
        echo "$ANDROID_HOME"
        return
    fi
    if [[ -f "$LOCAL_PROPERTIES" ]]; then
        awk -F'=' '/^sdk\.dir=/ { sub(/^sdk\.dir=/, ""); print; exit }' \
            "$LOCAL_PROPERTIES" | sed 's/\\:/:/g; s/\\\\/\\/g'
    fi
}

SDK_DIR="$(find_sdk_dir)"
if [[ -z "$SDK_DIR" || ! -d "$SDK_DIR" ]]; then
    echo "Android SDK 不可用，无法构建状态栏资源 overlay" >&2
    exit 1
fi

AAPT2="$(find "$SDK_DIR/build-tools" -maxdepth 2 -type f -name aapt2 \
    | sort -V | tail -n 1)"
ANDROID_JAR="$(find "$SDK_DIR/platforms" -maxdepth 2 -type f -name android.jar \
    | sort -V | tail -n 1)"
if [[ -z "$AAPT2" || ! -x "$AAPT2" || -z "$ANDROID_JAR" || ! -f "$ANDROID_JAR" ]]; then
    echo "Android aapt2 或 platform android.jar 不可用" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR/compiled"
rm -f "$OUTPUT_DIR/compiled/resources.zip" "$OUTPUT_APK"

"$AAPT2" compile \
    --dir "$SOURCE_DIR/res" \
    -o "$OUTPUT_DIR/compiled/resources.zip"
"$AAPT2" link \
    --auto-add-overlay \
    -I "$ANDROID_JAR" \
    --manifest "$SOURCE_DIR/AndroidManifest.xml" \
    -o "$OUTPUT_APK" \
    "$OUTPUT_DIR/compiled/resources.zip"

if [[ ! -s "$OUTPUT_APK" ]]; then
    echo "状态栏资源 overlay 构建失败" >&2
    exit 1
fi

echo "$OUTPUT_APK"
