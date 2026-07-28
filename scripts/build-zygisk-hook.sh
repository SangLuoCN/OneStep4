#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_PROPERTIES="$ROOT_DIR/local.properties"

find_ndk_build() {
    if [[ -n "${ANDROID_NDK_HOME:-}" && -x "${ANDROID_NDK_HOME}/ndk-build" ]]; then
        echo "${ANDROID_NDK_HOME}/ndk-build"
        return
    fi
    if [[ -n "${ANDROID_NDK_ROOT:-}" && -x "${ANDROID_NDK_ROOT}/ndk-build" ]]; then
        echo "${ANDROID_NDK_ROOT}/ndk-build"
        return
    fi

    local sdk_dir="${ANDROID_SDK_ROOT:-}"
    if [[ -z "$sdk_dir" && -f "$LOCAL_PROPERTIES" ]]; then
        sdk_dir="$(awk -F'=' '/^sdk\.dir=/ { sub(/^sdk\.dir=/, ""); print; exit }' \
            "$LOCAL_PROPERTIES" | sed 's/\\:/:/g; s/\\\\/\\/g')"
    fi
    if [[ -n "$sdk_dir" && -d "$sdk_dir/ndk" ]]; then
        local candidate
        candidate="$(find "$sdk_dir/ndk" -maxdepth 2 -type f -name ndk-build \
            | sort -V | tail -n 1)"
        if [[ -n "$candidate" && -x "$candidate" ]]; then
            echo "$candidate"
            return
        fi
    fi
    return 1
}

NDK_BUILD="$(find_ndk_build || true)"
if [[ -z "$NDK_BUILD" ]]; then
    echo "未找到 Android NDK。请安装 NDK r29，或设置 ANDROID_NDK_HOME。" >&2
    exit 1
fi

echo "编译 OneStep Zygisk Hook：$NDK_BUILD"
"$NDK_BUILD" \
    -C "$ROOT_DIR/zygisk" \
    -B \
    NDK_PROJECT_PATH=. \
    NDK_APPLICATION_MK=jni/Application.mk \
    APP_BUILD_SCRIPT=jni/Android.mk \
    NDK_OUT=build/obj \
    NDK_LIBS_OUT=build/libs

for abi in arm64-v8a armeabi-v7a; do
    output="$ROOT_DIR/zygisk/build/libs/$abi/libonestep_zygisk.so"
    if [[ ! -f "$output" ]]; then
        echo "Zygisk 输出不存在：$output" >&2
        exit 1
    fi
done
