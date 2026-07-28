#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/dist"
WORK_DIR="$(mktemp -d "$ROOT_DIR/build/magisk-privapp.XXXXXX")"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
ZYGISK_LIB_DIR="$ROOT_DIR/zygisk/build/libs"
ZYGISK_RUNTIME_DIR="$ROOT_DIR/app/build/zygisk-hook-runtime"
APP_GRADLE="$ROOT_DIR/app/build.gradle.kts"
APP_VERSION_NAME="$(awk -F'"' '/^[[:space:]]*versionName[[:space:]]*=/ { print $2; exit }' "$APP_GRADLE")"
APP_VERSION_CODE="$(awk -F'=' '/^[[:space:]]*versionCode[[:space:]]*=/ { gsub(/[[:space:]]/, "", $2); print $2; exit }' "$APP_GRADLE")"

sha256_file() {
    local file_path="$1"
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$file_path" | awk '{ print $1 }'
        return
    fi
    if command -v shasum >/dev/null 2>&1; then
        shasum -a 256 "$file_path" | awk '{ print $1 }'
        return
    fi
    echo "找不到 SHA-256 校验工具" >&2
    exit 1
}

if [[ -z "$APP_VERSION_NAME" || ! "$APP_VERSION_CODE" =~ ^[0-9]+$ ]]; then
    echo "无法从 $APP_GRADLE 读取应用版本" >&2
    exit 1
fi

SYSTEM_APP_DIR="OneStep4"
APK_ENTRY="system/priv-app/$SYSTEM_APP_DIR/OneStep4.apk"

mkdir -p "$OUT_DIR"

echo "清理旧 APK 和应用构建产物..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" :app:clean

echo "强制重新编译最新 APK..."
"$ROOT_DIR/gradlew" -p "$ROOT_DIR" --no-build-cache --rerun-tasks \
    :app:assembleDebug :app:prepareZygiskHookRuntime

"$ROOT_DIR/scripts/build-zygisk-hook.sh"

if [[ ! -f "$APK_PATH" ]]; then
    echo "APK 构建完成后不存在：$APK_PATH" >&2
    exit 1
fi
for required_file in \
    "$ZYGISK_LIB_DIR/arm64-v8a/libonestep_zygisk.so" \
    "$ZYGISK_LIB_DIR/armeabi-v7a/libonestep_zygisk.so" \
    "$ZYGISK_RUNTIME_DIR/classes.dex" \
    "$ZYGISK_RUNTIME_DIR/jni/arm64-v8a/libaliuhook.so" \
    "$ZYGISK_RUNTIME_DIR/jni/arm64-v8a/liblsplant.so" \
    "$ZYGISK_RUNTIME_DIR/jni/arm64-v8a/libc++_shared.so" \
    "$ZYGISK_RUNTIME_DIR/jni/armeabi-v7a/libaliuhook.so" \
    "$ZYGISK_RUNTIME_DIR/jni/armeabi-v7a/liblsplant.so" \
    "$ZYGISK_RUNTIME_DIR/jni/armeabi-v7a/libc++_shared.so"; do
    if [[ ! -f "$required_file" ]]; then
        echo "Zygisk Hook 构建产物不存在：$required_file" >&2
        exit 1
    fi
done

APK_SHA256="$(sha256_file "$APK_PATH")"
OUT_ZIP="$OUT_DIR/OneStep4-$APP_VERSION_NAME-magisk-$(date +%Y%m%d-%H%M%S).zip"

mkdir -p "$WORK_DIR/META-INF/com/google/android"
mkdir -p "$WORK_DIR/system/priv-app/$SYSTEM_APP_DIR"
mkdir -p "$WORK_DIR/system/etc/permissions"
mkdir -p "$WORK_DIR/zygisk"
mkdir -p "$WORK_DIR/zygisk-runtime/arm64-v8a"
mkdir -p "$WORK_DIR/zygisk-runtime/armeabi-v7a"

cp "$ROOT_DIR/packaging/magisk/update-binary" \
    "$WORK_DIR/META-INF/com/google/android/update-binary"
cp "$ROOT_DIR/packaging/magisk/updater-script" \
    "$WORK_DIR/META-INF/com/google/android/updater-script"
cp "$ROOT_DIR/packaging/magisk/customize.sh" "$WORK_DIR/customize.sh"
cp "$ROOT_DIR/packaging/magisk/service.sh" "$WORK_DIR/service.sh"
cp "$ROOT_DIR/packaging/magisk/sepolicy.rule" "$WORK_DIR/sepolicy.rule"
cp "$APK_PATH" "$WORK_DIR/system/priv-app/$SYSTEM_APP_DIR/OneStep4.apk"
cp "$ZYGISK_LIB_DIR/arm64-v8a/libonestep_zygisk.so" \
    "$WORK_DIR/zygisk/arm64-v8a.so"
cp "$ZYGISK_LIB_DIR/armeabi-v7a/libonestep_zygisk.so" \
    "$WORK_DIR/zygisk/armeabi-v7a.so"
cp "$ZYGISK_RUNTIME_DIR/classes.dex" "$WORK_DIR/zygisk-runtime/aliuhook.dex"
for abi in arm64-v8a armeabi-v7a; do
    cp "$ZYGISK_RUNTIME_DIR/jni/$abi/libaliuhook.so" \
        "$WORK_DIR/zygisk-runtime/$abi/libaliuhook.so"
    cp "$ZYGISK_RUNTIME_DIR/jni/$abi/liblsplant.so" \
        "$WORK_DIR/zygisk-runtime/$abi/liblsplant.so"
    cp "$ZYGISK_RUNTIME_DIR/jni/$abi/libc++_shared.so" \
        "$WORK_DIR/zygisk-runtime/$abi/libc++_shared.so"
done

COPIED_APK_SHA256="$(sha256_file "$WORK_DIR/$APK_ENTRY")"
if [[ "$COPIED_APK_SHA256" != "$APK_SHA256" ]]; then
    echo "复制到 Magisk 工作目录的 APK 校验失败" >&2
    exit 1
fi

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
chmod 0755 "$WORK_DIR/service.sh"
chmod 0644 "$WORK_DIR/sepolicy.rule"
chmod 0644 "$WORK_DIR/system/priv-app/$SYSTEM_APP_DIR/OneStep4.apk"
chmod 0644 "$WORK_DIR/system/etc/permissions/privapp-permissions-onestep.xml"
chmod 0644 "$WORK_DIR/module.prop"
chmod 0644 "$WORK_DIR/zygisk/arm64-v8a.so" "$WORK_DIR/zygisk/armeabi-v7a.so"
chmod 0755 "$WORK_DIR/zygisk-runtime"
chmod 0755 "$WORK_DIR/zygisk-runtime/arm64-v8a"
chmod 0755 "$WORK_DIR/zygisk-runtime/armeabi-v7a"
chmod 0644 "$WORK_DIR/zygisk-runtime/aliuhook.dex"
chmod 0644 "$WORK_DIR/zygisk-runtime/arm64-v8a/"*.so
chmod 0644 "$WORK_DIR/zygisk-runtime/armeabi-v7a/"*.so

(
    cd "$WORK_DIR"
    zip -qr "$OUT_ZIP" .
)

VERIFY_DIR="$WORK_DIR/verify"
mkdir -p "$VERIFY_DIR"
unzip -qq "$OUT_ZIP" \
    "$APK_ENTRY" \
    "zygisk/arm64-v8a.so" \
    "zygisk/armeabi-v7a.so" \
    "zygisk-runtime/aliuhook.dex" \
    -d "$VERIFY_DIR"
PACKAGED_APK_SHA256="$(sha256_file "$VERIFY_DIR/$APK_ENTRY")"
if [[ "$PACKAGED_APK_SHA256" != "$APK_SHA256" ]]; then
    echo "Magisk ZIP 内 APK 与最新构建 APK 不一致" >&2
    exit 1
fi
for required_entry in \
    "zygisk/arm64-v8a.so" \
    "zygisk/armeabi-v7a.so" \
    "zygisk-runtime/aliuhook.dex"; do
    if [[ ! -s "$VERIFY_DIR/$required_entry" ]]; then
        echo "Magisk ZIP 内缺少 Zygisk Hook：$required_entry" >&2
        exit 1
    fi
done

echo "APK SHA-256: $APK_SHA256"
echo "$OUT_ZIP"
