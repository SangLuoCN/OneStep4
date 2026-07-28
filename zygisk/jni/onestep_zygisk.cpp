#include <android/log.h>
#include <fcntl.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <unistd.h>

#include "zygisk.hpp"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "OneStepZygisk", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "OneStepZygisk", __VA_ARGS__)

namespace {

constexpr const char *kAliuHookDex = "zygisk-runtime/aliuhook.dex";
constexpr const char *kOneStepApk = "/system/priv-app/OneStep4/OneStep4.apk";
constexpr const char *kHookClass =
        "com.sangluo.onestep.hook.OneStepSecureWindowHook";

#if defined(__aarch64__)
constexpr const char *kAbi = "arm64-v8a";
#elif defined(__arm__)
constexpr const char *kAbi = "armeabi-v7a";
#else
#error OneStep Zygisk supports only arm64-v8a and armeabi-v7a
#endif

bool clearException(JNIEnv *env, const char *stage) {
    if (!env->ExceptionCheck()) {
        return false;
    }
    env->ExceptionDescribe();
    env->ExceptionClear();
    LOGE("JNI failure at %s", stage);
    return true;
}

void *readModuleFile(int moduleFd, const char *path, size_t *sizeOut) {
    int fd = openat(moduleFd, path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        LOGE("openat failed for %s", path);
        return nullptr;
    }
    struct stat fileStat{};
    if (fstat(fd, &fileStat) != 0 || fileStat.st_size <= 0) {
        LOGE("invalid runtime file %s", path);
        close(fd);
        return nullptr;
    }
    auto *data = static_cast<unsigned char *>(malloc(static_cast<size_t>(fileStat.st_size)));
    if (data == nullptr) {
        close(fd);
        return nullptr;
    }
    size_t offset = 0;
    while (offset < static_cast<size_t>(fileStat.st_size)) {
        ssize_t count = read(fd, data + offset,
                             static_cast<size_t>(fileStat.st_size) - offset);
        if (count <= 0) {
            LOGE("read failed for %s", path);
            free(data);
            close(fd);
            return nullptr;
        }
        offset += static_cast<size_t>(count);
    }
    close(fd);
    *sizeOut = offset;
    return data;
}

jobject systemClassLoader(JNIEnv *env) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    if (classLoaderClass == nullptr || clearException(env, "find ClassLoader")) {
        return nullptr;
    }
    jmethodID method = env->GetStaticMethodID(
            classLoaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    if (method == nullptr || clearException(env, "getSystemClassLoader method")) {
        return nullptr;
    }
    jobject loader = env->CallStaticObjectMethod(classLoaderClass, method);
    if (clearException(env, "getSystemClassLoader")) {
        return nullptr;
    }
    return loader;
}

jobject makeAliuHookClassLoader(JNIEnv *env, int moduleFd, jobject parent) {
    size_t dexSize = 0;
    void *dexData = readModuleFile(moduleFd, kAliuHookDex, &dexSize);
    if (dexData == nullptr) {
        return nullptr;
    }
    jobject buffer = env->NewDirectByteBuffer(dexData, static_cast<jlong>(dexSize));
    if (buffer == nullptr || clearException(env, "create AliuHook dex buffer")) {
        free(dexData);
        return nullptr;
    }

    jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
    if (byteBufferClass == nullptr || clearException(env, "find ByteBuffer")) {
        free(dexData);
        return nullptr;
    }
    jobjectArray buffers = env->NewObjectArray(1, byteBufferClass, buffer);
    if (buffers == nullptr || clearException(env, "create AliuHook dex buffer array")) {
        free(dexData);
        return nullptr;
    }

    char libraryPath[256];
    snprintf(libraryPath, sizeof(libraryPath),
             "/proc/self/fd/%d/zygisk-runtime/%s", moduleFd, kAbi);
    jstring libraryPathString = env->NewStringUTF(libraryPath);
    jclass loaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (loaderClass == nullptr || clearException(env, "find InMemoryDexClassLoader")) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(
            loaderClass, "<init>",
            "([Ljava/nio/ByteBuffer;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    if (constructor == nullptr || clearException(env, "InMemoryDexClassLoader constructor")) {
        return nullptr;
    }
    jobject loader = env->NewObject(loaderClass, constructor, buffers,
                                    libraryPathString, parent);
    if (clearException(env, "create AliuHook class loader")) {
        return nullptr;
    }
    return loader;
}

bool initializeAliuHook(JNIEnv *env, jobject loader) {
    jclass classClass = env->FindClass("java/lang/Class");
    jmethodID forName = classClass == nullptr ? nullptr : env->GetStaticMethodID(
            classClass, "forName",
            "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;");
    if (forName == nullptr || clearException(env, "Class.forName method")) {
        return false;
    }
    jstring name = env->NewStringUTF("de.robv.android.xposed.XposedBridge");
    jobject result = env->CallStaticObjectMethod(classClass, forName, name, JNI_TRUE, loader);
    if (result == nullptr || clearException(env, "initialize AliuHook")) {
        return false;
    }
    return true;
}

jobject makeAppClassLoader(JNIEnv *env, jobject parent) {
    jclass loaderClass = env->FindClass("dalvik/system/DexClassLoader");
    if (loaderClass == nullptr || clearException(env, "find DexClassLoader")) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(
            loaderClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    if (constructor == nullptr || clearException(env, "DexClassLoader constructor")) {
        return nullptr;
    }
    jstring apkPath = env->NewStringUTF(kOneStepApk);
    jobject loader = env->NewObject(loaderClass, constructor, apkPath,
                                    nullptr, nullptr, parent);
    if (clearException(env, "create OneStep class loader")) {
        return nullptr;
    }
    return loader;
}

jclass loadHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (loadClass == nullptr || clearException(env, "ClassLoader.loadClass method")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kHookClass);
    auto hookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (hookClass == nullptr || clearException(env, "load OneStep hook class")) {
        return nullptr;
    }
    return hookClass;
}

class OneStepZygiskModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *loadedApi, JNIEnv *loadedEnv) override {
        api = loadedApi;
        env = loadedEnv;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *) override {
        api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs *) override {
        int moduleFd = api->getModuleDir();
        if (moduleFd < 0) {
            LOGE("module directory unavailable");
            return;
        }
        jobject systemLoader = systemClassLoader(env);
        jobject aliuhookLoader = systemLoader == nullptr
                ? nullptr : makeAliuHookClassLoader(env, moduleFd, systemLoader);
        if (aliuhookLoader != nullptr && initializeAliuHook(env, aliuhookLoader)) {
            jobject appLoader = makeAppClassLoader(env, aliuhookLoader);
            jclass localHookClass = appLoader == nullptr ? nullptr : loadHookClass(env, appLoader);
            if (localHookClass != nullptr) {
                hookClass = static_cast<jclass>(env->NewGlobalRef(localHookClass));
                systemClassLoaderRef = env->NewGlobalRef(systemLoader);
                LOGI("Hook runtime prepared for %s", kAbi);
            }
        }
        close(moduleFd);
    }

    void postServerSpecialize(const zygisk::ServerSpecializeArgs *) override {
        if (hookClass == nullptr || systemClassLoaderRef == nullptr) {
            LOGE("Hook runtime was not prepared");
            return;
        }
        jmethodID bootstrap = env->GetStaticMethodID(
                hookClass, "bootstrap", "(Ljava/lang/ClassLoader;)V");
        if (bootstrap == nullptr || clearException(env, "find hook bootstrap")) {
            return;
        }
        env->CallStaticVoidMethod(hookClass, bootstrap, systemClassLoaderRef);
        if (!clearException(env, "run hook bootstrap")) {
            LOGI("Hook bootstrap started");
        }
    }

private:
    zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    jclass hookClass = nullptr;
    jobject systemClassLoaderRef = nullptr;
};

} // namespace

REGISTER_ZYGISK_MODULE(OneStepZygiskModule)
