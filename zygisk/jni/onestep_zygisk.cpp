#include <android/log.h>
#include <ctype.h>
#include <dirent.h>
#include <fcntl.h>
#include <jni.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <unistd.h>

#include "zygisk.hpp"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "OneStepZygisk", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "OneStepZygisk", __VA_ARGS__)

namespace {

int statusHookDiagnosticFd = -1;

void writeStatusHookDiagnostic(const char *message) {
    if (statusHookDiagnosticFd >= 0) {
        dprintf(statusHookDiagnosticFd, "%s\n", message);
        fsync(statusHookDiagnosticFd);
    }
}

constexpr const char *kAliuHookDex = "zygisk-runtime/aliuhook.dex";
constexpr const char *kOneStepPackage = "com.sangluo.onestep";
constexpr const char *kOneStepApk = "/system/priv-app/OneStep4/OneStep4.apk";
constexpr const char *kHookClass =
        "com.sangluo.onestep.hook.OneStepSecureWindowHook";
constexpr const char *kStatusBarOverlayHookClass =
        "com.sangluo.onestep.hook.OneStepStatusBarOverlayHook";
constexpr const char *kPrimaryHomeHookClass =
        "com.sangluo.onestep.hook.OneStepPrimaryHomeHook";
constexpr const char *kRootVirtualDisplayCompatHookClass =
        "com.sangluo.onestep.hook.OneStepRootVirtualDisplayCompatHook";
constexpr const char *kHyperOsGestureNavigationHookClass =
        "com.sangluo.onestep.hook.HyperOsGestureNavigationBypassHook";
constexpr const char *kHyperOsSystemUiGestureNavigationHookClass =
        "com.sangluo.onestep.hook.HyperOsSystemUiGestureNavigationBypassHook";
constexpr const char *kGooglePhotosDragHookClass =
        "com.sangluo.onestep.hook.OneStepGooglePhotosDragHook";
constexpr const char *kUniversalImageDragHookClass =
        "com.sangluo.onestep.hook.OneStepUniversalImageDragHook";
constexpr const char *kMiuiHomeProcess = "com.miui.home";
constexpr const char *kSystemUiProcess = "com.android.systemui";
constexpr const char *kGooglePhotosProcess = "com.google.android.apps.photos";
constexpr const char *kQqProcess = "com.tencent.mobileqq";
constexpr const char *kWeChatProcess = "com.tencent.mm";
constexpr const char *kHyperOsVersionProperty = "ro.mi.os.version.name";
constexpr const char *kImageDragSharingProperty = "onestep.hook.image_drag";
constexpr const char *kDisableSecureWindowHook =
        "hook-config/disable-secure-window";
constexpr const char *kDisableStatusBarOverlayHook =
        "hook-config/disable-status-bar-overlay";
constexpr const char *kDisablePrimaryHomeEnhancement =
        "hook-config/disable-primary-home-enhancement";
constexpr const char *kModulesDirectory = "/data/adb/modules";
constexpr const char *kStandaloneBackendMarker =
        "/data/system/onestep-standalone-backend-active";

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
    if (statusHookDiagnosticFd >= 0) {
        dprintf(statusHookDiagnosticFd, "JNI failure at %s\n", stage);
        fsync(statusHookDiagnosticFd);
    }
    return true;
}

template <typename T>
bool jniResultUnavailable(JNIEnv *env, T result, const char *stage) {
    bool hadException = clearException(env, stage);
    return result == nullptr || hadException;
}

class JniExceptionGuard {
public:
    JniExceptionGuard(JNIEnv *environment, const char *callbackStage)
            : env(environment), stage(callbackStage) {
    }

    ~JniExceptionGuard() {
        clearException(env, stage);
    }

private:
    JNIEnv *env;
    const char *stage;
};

bool containsIgnoreCase(const char *text, const char *needle) {
    if (text == nullptr || needle == nullptr || *needle == '\0') {
        return false;
    }
    size_t needleLength = strlen(needle);
    for (const char *start = text; *start != '\0'; ++start) {
        size_t index = 0;
        while (index < needleLength && start[index] != '\0'
                && tolower(static_cast<unsigned char>(start[index]))
                == tolower(static_cast<unsigned char>(needle[index]))) {
            ++index;
        }
        if (index == needleLength) {
            return true;
        }
    }
    return false;
}

bool isLsposedModuleProperty(const char *line) {
    if (line == nullptr
            || (strncmp(line, "id=", 3) != 0 && strncmp(line, "name=", 5) != 0)) {
        return false;
    }
    return containsIgnoreCase(line, "lsposed")
            || containsIgnoreCase(line, "lspd")
            || containsIgnoreCase(line, "vector");
}

bool activeLsposedModuleInstalled() {
    char selectedBackend[PROP_VALUE_MAX]{};
    if (__system_property_get("onestep.hook.backend", selectedBackend) > 0
            && strcmp(selectedBackend, "lsposed") == 0) {
        return true;
    }
    DIR *modules = opendir(kModulesDirectory);
    if (modules == nullptr) {
        return false;
    }
    bool found = false;
    dirent *entry = nullptr;
    while (!found && (entry = readdir(modules)) != nullptr) {
        if (entry->d_name[0] == '.') {
            continue;
        }
        char moduleDirectory[PATH_MAX];
        snprintf(moduleDirectory, sizeof(moduleDirectory), "%s/%s",
                 kModulesDirectory, entry->d_name);
        char markerPath[PATH_MAX];
        snprintf(markerPath, sizeof(markerPath), "%s/disable", moduleDirectory);
        if (access(markerPath, F_OK) == 0) {
            continue;
        }
        snprintf(markerPath, sizeof(markerPath), "%s/remove", moduleDirectory);
        if (access(markerPath, F_OK) == 0) {
            continue;
        }
        char propertyPath[PATH_MAX];
        snprintf(propertyPath, sizeof(propertyPath), "%s/module.prop", moduleDirectory);
        FILE *properties = fopen(propertyPath, "r");
        if (properties == nullptr) {
            continue;
        }
        char line[512];
        while (fgets(line, sizeof(line), properties) != nullptr) {
            if (isLsposedModuleProperty(line)) {
                found = true;
                break;
            }
        }
        fclose(properties);
    }
    closedir(modules);
    return found;
}

void markStandaloneBackendActive() {
    int fd = open(kStandaloneBackendMarker,
                  O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) {
        LOGE("could not create standalone backend marker");
        return;
    }
    dprintf(fd, "standalone\n");
    close(fd);
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
    if (jniResultUnavailable(env, classLoaderClass, "find ClassLoader")) {
        return nullptr;
    }
    jmethodID method = env->GetStaticMethodID(
            classLoaderClass, "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
    if (jniResultUnavailable(env, method, "getSystemClassLoader method")) {
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
    if (jniResultUnavailable(env, buffer, "create AliuHook dex buffer")) {
        free(dexData);
        return nullptr;
    }

    jclass byteBufferClass = env->FindClass("java/nio/ByteBuffer");
    if (jniResultUnavailable(env, byteBufferClass, "find ByteBuffer")) {
        free(dexData);
        return nullptr;
    }
    jobjectArray buffers = env->NewObjectArray(1, byteBufferClass, buffer);
    if (jniResultUnavailable(env, buffers, "create AliuHook dex buffer array")) {
        free(dexData);
        return nullptr;
    }

    char libraryPath[256];
    snprintf(libraryPath, sizeof(libraryPath),
             "/proc/self/fd/%d/zygisk-runtime/%s", moduleFd, kAbi);
    jstring libraryPathString = env->NewStringUTF(libraryPath);
    jclass loaderClass = env->FindClass("dalvik/system/InMemoryDexClassLoader");
    if (jniResultUnavailable(env, loaderClass, "find InMemoryDexClassLoader")) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(
            loaderClass, "<init>",
            "([Ljava/nio/ByteBuffer;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    if (jniResultUnavailable(env, constructor, "InMemoryDexClassLoader constructor")) {
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
    if (jniResultUnavailable(env, forName, "Class.forName method")) {
        return false;
    }
    jstring name = env->NewStringUTF("de.robv.android.xposed.XposedBridge");
    jobject result = env->CallStaticObjectMethod(classClass, forName, name, JNI_TRUE, loader);
    if (jniResultUnavailable(env, result, "initialize AliuHook")) {
        return false;
    }
    return true;
}

jobject makeAppClassLoader(JNIEnv *env, jobject parent) {
    jclass loaderClass = env->FindClass("dalvik/system/DexClassLoader");
    if (jniResultUnavailable(env, loaderClass, "find DexClassLoader")) {
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(
            loaderClass, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/ClassLoader;)V");
    if (jniResultUnavailable(env, constructor, "DexClassLoader constructor")) {
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
    if (jniResultUnavailable(env, loadClass, "ClassLoader.loadClass method")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kHookClass);
    auto hookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, hookClass, "load OneStep hook class")) {
        return nullptr;
    }
    return hookClass;
}

jclass loadStatusBarOverlayHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass,
            "ClassLoader.loadClass method for status-bar overlay")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kStatusBarOverlayHookClass);
    auto overlayHookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, overlayHookClass,
            "load OneStep status-bar overlay hook class")) {
        return nullptr;
    }
    return overlayHookClass;
}

jclass loadPrimaryHomeHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass,
            "ClassLoader.loadClass method for primary HOME")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kPrimaryHomeHookClass);
    auto primaryHomeClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, primaryHomeClass,
            "load OneStep primary HOME hook class")) {
        return nullptr;
    }
    return primaryHomeClass;
}

jclass loadRootVirtualDisplayCompatHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass,
            "ClassLoader.loadClass method for root display compat")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kRootVirtualDisplayCompatHookClass);
    auto compatHookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, compatHookClass,
            "load OneStep root display compat hook class")) {
        return nullptr;
    }
    return compatHookClass;
}

jclass loadHyperOsGestureNavigationHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass,
            "ClassLoader.loadClass method for HyperOS gesture")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kHyperOsGestureNavigationHookClass);
    auto gestureHookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, gestureHookClass,
            "load HyperOS gesture hook class")) {
        return nullptr;
    }
    return gestureHookClass;
}

jclass loadHyperOsSystemUiGestureNavigationHookClass(JNIEnv *env, jobject appLoader) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass,
            "ClassLoader.loadClass method for HyperOS SystemUI gesture")) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(kHyperOsSystemUiGestureNavigationHookClass);
    auto gestureHookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, gestureHookClass,
            "load HyperOS SystemUI gesture hook class")) {
        return nullptr;
    }
    return gestureHookClass;
}

jclass loadNamedHookClass(JNIEnv *env, jobject appLoader, const char *name,
                          const char *stage) {
    jclass classLoaderClass = env->FindClass("java/lang/ClassLoader");
    jmethodID loadClass = classLoaderClass == nullptr ? nullptr : env->GetMethodID(
            classLoaderClass, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
    if (jniResultUnavailable(env, loadClass, stage)) {
        return nullptr;
    }
    jstring className = env->NewStringUTF(name);
    auto hookClass = static_cast<jclass>(
            env->CallObjectMethod(appLoader, loadClass, className));
    if (jniResultUnavailable(env, hookClass, stage)) {
        return nullptr;
    }
    return hookClass;
}

bool isProcess(JNIEnv *env, jstring niceName, const char *expected) {
    if (niceName == nullptr || expected == nullptr) {
        return false;
    }
    const char *name = env->GetStringUTFChars(niceName, nullptr);
    if (jniResultUnavailable(env, name, "read app process name")) {
        return false;
    }
    bool matches = strcmp(name, expected) == 0;
    env->ReleaseStringUTFChars(niceName, name);
    return matches;
}

bool isUserAppMainProcess(JNIEnv *env, jint uid, jstring niceName) {
    if (uid < 10000 || niceName == nullptr) {
        return false;
    }
    const char *name = env->GetStringUTFChars(niceName, nullptr);
    if (jniResultUnavailable(env, name, "read universal image source process name")) {
        return false;
    }
    bool mainProcess = strchr(name, ':') == nullptr
            && strcmp(name, kOneStepPackage) != 0
            && strcmp(name, kSystemUiProcess) != 0
            && strcmp(name, kMiuiHomeProcess) != 0;
    env->ReleaseStringUTFChars(niceName, name);
    return mainProcess;
}

bool isPackageDataDirectory(JNIEnv *env, jstring appDataDir, const char *expected) {
    if (appDataDir == nullptr || expected == nullptr) {
        return false;
    }
    const char *path = env->GetStringUTFChars(appDataDir, nullptr);
    if (jniResultUnavailable(env, path, "read app data directory")) {
        return false;
    }
    const char *lastSlash = strrchr(path, '/');
    const char *directoryName = lastSlash == nullptr ? path : lastSlash + 1;
    bool matches = strcmp(directoryName, expected) == 0;
    env->ReleaseStringUTFChars(appDataDir, path);
    return matches;
}

bool isHyperOs() {
    char version[PROP_VALUE_MAX]{};
    return __system_property_get(kHyperOsVersionProperty, version) > 0
            && strncmp(version, "OS", 2) == 0;
}

bool isPropertyEnabled(const char *name) {
    char value[PROP_VALUE_MAX]{};
    return __system_property_get(name, value) > 0 && strcmp(value, "1") == 0;
}

class OneStepZygiskModule : public zygisk::ModuleBase {
public:
    void onLoad(zygisk::Api *loadedApi, JNIEnv *loadedEnv) override {
        api = loadedApi;
        env = loadedEnv;
    }

    void preAppSpecialize(zygisk::AppSpecializeArgs *args) override {
        JniExceptionGuard exceptionGuard(env, "finish app pre-specialization");
        bool isMiuiHome = args != nullptr
                && isProcess(env, args->nice_name, kMiuiHomeProcess);
        bool isSystemUi = args != nullptr
                && isProcess(env, args->nice_name, kSystemUiProcess);
        bool wantsHyperOsHook = (isMiuiHome || isSystemUi) && isHyperOs();
        bool imageDragSharingEnabled = isPropertyEnabled(kImageDragSharingProperty);
        bool googlePhotosNameMatched = args != nullptr
                && isProcess(env, args->nice_name, kGooglePhotosProcess);
        bool googlePhotosDataDirMatched = args != nullptr
                && isPackageDataDirectory(
                        env, args->app_data_dir, kGooglePhotosProcess);
        bool isGooglePhotos = imageDragSharingEnabled
                && (googlePhotosNameMatched || googlePhotosDataDirMatched);
        bool universalProcessNameMatched = args != nullptr
                && (isProcess(env, args->nice_name, kQqProcess)
                || isProcess(env, args->nice_name, kWeChatProcess));
        bool universalDataDirMatched = args != nullptr
                && (isPackageDataDirectory(env, args->app_data_dir, kQqProcess)
                || isPackageDataDirectory(env, args->app_data_dir, kWeChatProcess));
        bool isUniversalImageSource = imageDragSharingEnabled && args != nullptr
                && isUserAppMainProcess(env, args->uid, args->nice_name)
                && (universalProcessNameMatched || universalDataDirMatched);
        if (!wantsHyperOsHook && !isGooglePhotos && !isUniversalImageSource) {
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        // LSPosed owns the system/framework hooks, but it is often not scoped to
        // QQ and WeChat. Keep those app-side media hooks available even when the
        // LSPosed scope does not include both packages.
        if (activeLsposedModuleInstalled() && !isUniversalImageSource && !isGooglePhotos) {
            LOGI("LSPosed backend selected; skip standalone non-app hook");
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        if (isGooglePhotos) {
            LOGI("Google Photos source process selected via %s",
                 googlePhotosNameMatched ? "process name" : "app data directory");
        }
        if (isUniversalImageSource) {
            LOGI("QQ/WeChat universal image source process selected");
        }
        int moduleFd = api->getModuleDir();
        if (moduleFd < 0) {
            LOGE("module directory unavailable for HyperOS app hook");
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
            return;
        }
        jobject systemLoader = systemClassLoader(env);
        jobject aliuhookLoader = systemLoader == nullptr
                ? nullptr : makeAliuHookClassLoader(env, moduleFd, systemLoader);
        if (aliuhookLoader != nullptr && initializeAliuHook(env, aliuhookLoader)) {
            jobject appLoader = makeAppClassLoader(env, aliuhookLoader);
            jclass localGestureHookClass = nullptr;
            if (appLoader != nullptr) {
                if (wantsHyperOsHook) {
                    localGestureHookClass = isMiuiHome
                            ? loadHyperOsGestureNavigationHookClass(env, appLoader)
                            : loadHyperOsSystemUiGestureNavigationHookClass(env, appLoader);
                }
                if (isGooglePhotos) {
                    jclass localSource = loadNamedHookClass(
                            env, appLoader, kGooglePhotosDragHookClass,
                            "load Google Photos drag hook class");
                    if (localSource != nullptr) {
                        googlePhotosDragHookClass = static_cast<jclass>(
                            env->NewGlobalRef(localSource));
                    }
                }
                if (isUniversalImageSource) {
                    jclass localUniversal = loadNamedHookClass(
                            env, appLoader, kUniversalImageDragHookClass,
                            "load universal image drag hook class");
                    if (localUniversal != nullptr) {
                        universalImageDragHookClass = static_cast<jclass>(
                                env->NewGlobalRef(localUniversal));
                    }
                }
            }
            if (localGestureHookClass != nullptr) {
                hyperOsAppHookClass = static_cast<jclass>(
                        env->NewGlobalRef(localGestureHookClass));
                LOGI("HyperOS gesture hook runtime prepared for %s in %s",
                     kAbi, isMiuiHome ? kMiuiHomeProcess : kSystemUiProcess);
            }
            if (hyperOsAppHookClass != nullptr
                    || googlePhotosDragHookClass != nullptr
                    || universalImageDragHookClass != nullptr) {
                appProcessSystemClassLoaderRef = env->NewGlobalRef(systemLoader);
            }
        }
        close(moduleFd);
        if (appProcessSystemClassLoaderRef == nullptr) {
            api->setOption(zygisk::DLCLOSE_MODULE_LIBRARY);
        }
    }

    void postAppSpecialize(const zygisk::AppSpecializeArgs *args) override {
        JniExceptionGuard exceptionGuard(env, "finish app post-specialization");
        if (appProcessSystemClassLoaderRef == nullptr) {
            return;
        }
        if (hyperOsAppHookClass != nullptr) {
            jmethodID bootstrap = env->GetStaticMethodID(
                    hyperOsAppHookClass, "bootstrap", "(Ljava/lang/ClassLoader;)V");
            if (jniResultUnavailable(
                    env, bootstrap, "find HyperOS gesture hook bootstrap")) {
                LOGE("HyperOS gesture hook bootstrap was unavailable");
            } else {
                env->CallStaticVoidMethod(
                        hyperOsAppHookClass, bootstrap, appProcessSystemClassLoaderRef);
                if (!clearException(env, "run HyperOS gesture hook bootstrap")) {
                    LOGI("HyperOS gesture hook bootstrap started");
                }
            }
        }
        if (googlePhotosDragHookClass != nullptr) {
            invokeAppHookInstall(googlePhotosDragHookClass, "Google Photos source",
                    kGooglePhotosProcess, kGooglePhotosProcess);
        }
        if (universalImageDragHookClass != nullptr) {
            invokeUniversalImageHookInstall(universalImageDragHookClass, args);
        }
    }

    void preServerSpecialize(zygisk::ServerSpecializeArgs *) override {
        JniExceptionGuard exceptionGuard(env, "finish server pre-specialization");
        int moduleFd = api->getModuleDir();
        if (moduleFd < 0) {
            LOGE("module directory unavailable");
            return;
        }
        secureWindowHookEnabled =
                faccessat(moduleFd, kDisableSecureWindowHook, F_OK, 0) != 0;
        statusBarOverlayHookEnabled =
                faccessat(moduleFd, kDisableStatusBarOverlayHook, F_OK, 0) != 0;
        primaryHomeEnhancementEnabled =
                faccessat(moduleFd, kDisablePrimaryHomeEnhancement, F_OK, 0) != 0;
        LOGI("Hook settings: secure=%d, statusbar=%d, primaryHome=%d",
             secureWindowHookEnabled, statusBarOverlayHookEnabled,
             primaryHomeEnhancementEnabled);
        if (activeLsposedModuleInstalled()) {
            lsposedBackendSelected = true;
            LOGI("Active LSPosed module detected; skip standalone Aliuhook/LSPlant");
            close(moduleFd);
            return;
        }
        jobject systemLoader = systemClassLoader(env);
        jobject aliuhookLoader = systemLoader == nullptr
                ? nullptr : makeAliuHookClassLoader(env, moduleFd, systemLoader);
        if (aliuhookLoader != nullptr && initializeAliuHook(env, aliuhookLoader)) {
            jobject appLoader = makeAppClassLoader(env, aliuhookLoader);
            jclass localHookClass = appLoader == nullptr || !secureWindowHookEnabled
                    ? nullptr : loadHookClass(env, appLoader);
            if (localHookClass != nullptr) {
                hookClass = static_cast<jclass>(env->NewGlobalRef(localHookClass));
                LOGI("Hook runtime prepared for %s", kAbi);
            }
            jclass localPrimaryHomeClass = appLoader == nullptr
                    || !primaryHomeEnhancementEnabled
                    ? nullptr : loadPrimaryHomeHookClass(env, appLoader);
            if (localPrimaryHomeClass != nullptr) {
                primaryHomeHookClass = static_cast<jclass>(
                        env->NewGlobalRef(localPrimaryHomeClass));
                LOGI("Primary HOME hook runtime prepared for %s", kAbi);
            }
            jclass localRootDisplayCompatClass = appLoader == nullptr
                    ? nullptr : loadRootVirtualDisplayCompatHookClass(env, appLoader);
            if (localRootDisplayCompatClass != nullptr) {
                rootDisplayCompatHookClass = static_cast<jclass>(
                        env->NewGlobalRef(localRootDisplayCompatClass));
                LOGI("Root display compat hook runtime prepared for %s", kAbi);
            }
            if (appLoader != nullptr && statusBarOverlayHookEnabled) {
                appClassLoaderRef = env->NewGlobalRef(appLoader);
            }
            if (hookClass != nullptr || appClassLoaderRef != nullptr
                    || primaryHomeHookClass != nullptr
                    || rootDisplayCompatHookClass != nullptr) {
                systemClassLoaderRef = env->NewGlobalRef(systemLoader);
            }
        }
        close(moduleFd);
    }

    void postServerSpecialize(const zygisk::ServerSpecializeArgs *) override {
        JniExceptionGuard exceptionGuard(env, "finish server post-specialization");
        if (lsposedBackendSelected) {
            LOGI("LSPosed backend selected for system_server");
            return;
        }
        markStandaloneBackendActive();
        statusHookDiagnosticFd = open("/data/system/onestep-status-hook.log",
                                      O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
        writeStatusHookDiagnostic("postServerSpecialize started");
        if (systemClassLoaderRef == nullptr) {
            LOGE("Hook runtime was not prepared");
            writeStatusHookDiagnostic("system class loader unavailable");
            closeStatusHookDiagnostic();
            return;
        }
        if (rootDisplayCompatHookClass == nullptr) {
            LOGE("Root display compat hook runtime was not prepared");
        } else {
            jmethodID compatBootstrap = env->GetStaticMethodID(
                    rootDisplayCompatHookClass, "bootstrap", "(Ljava/lang/ClassLoader;)V");
            if (jniResultUnavailable(
                    env, compatBootstrap, "find root display compat hook bootstrap")) {
                LOGE("Root display compat hook bootstrap was unavailable");
            } else {
                env->CallStaticVoidMethod(
                        rootDisplayCompatHookClass, compatBootstrap, systemClassLoaderRef);
                if (!clearException(env, "run root display compat hook bootstrap")) {
                    LOGI("Root display compat hook bootstrap completed");
                }
            }
        }
        if (!secureWindowHookEnabled) {
            LOGI("Secure-window hook disabled by user");
        } else if (hookClass == nullptr) {
            LOGE("Secure-window hook runtime was not prepared");
        } else {
            jmethodID bootstrap = env->GetStaticMethodID(
                    hookClass, "bootstrap", "(Ljava/lang/ClassLoader;)V");
            if (jniResultUnavailable(env, bootstrap, "find hook bootstrap")) {
                LOGE("Secure-window hook bootstrap was unavailable");
            } else {
                env->CallStaticVoidMethod(hookClass, bootstrap, systemClassLoaderRef);
                if (!clearException(env, "run hook bootstrap")) {
                    LOGI("Hook bootstrap started");
                }
            }
        }
        jclass statusBarOverlayHookClass = !statusBarOverlayHookEnabled
                || appClassLoaderRef == nullptr
                ? nullptr : loadStatusBarOverlayHookClass(env, appClassLoaderRef);
        if (!statusBarOverlayHookEnabled) {
            LOGI("Status-bar overlay hook disabled by user");
            writeStatusHookDiagnostic("status-bar hook disabled by user");
        } else if (statusBarOverlayHookClass == nullptr) {
            LOGE("Status-bar overlay hook runtime was not prepared");
            writeStatusHookDiagnostic("status-bar hook class unavailable");
        } else {
            writeStatusHookDiagnostic("status-bar hook class loaded");
            jmethodID overlayBootstrap = env->GetStaticMethodID(
                    statusBarOverlayHookClass,
                    "bootstrap",
                    "(Ljava/lang/ClassLoader;)V");
            if (jniResultUnavailable(
                    env, overlayBootstrap, "find status-bar overlay hook bootstrap")) {
                LOGE("Status-bar overlay hook bootstrap was unavailable");
                writeStatusHookDiagnostic("status-bar bootstrap method unavailable");
            } else {
                writeStatusHookDiagnostic("calling status-bar bootstrap");
                env->CallStaticVoidMethod(
                        statusBarOverlayHookClass, overlayBootstrap, systemClassLoaderRef);
                if (!clearException(env, "run status-bar overlay hook bootstrap")) {
                    LOGI("Status-bar overlay hook bootstrap started");
                    writeStatusHookDiagnostic("status-bar bootstrap returned");
                }
            }
        }
        if (!primaryHomeEnhancementEnabled) {
            LOGI("Primary HOME enhancement disabled by user");
        } else if (primaryHomeHookClass == nullptr) {
            LOGE("Primary HOME hook runtime was not prepared");
        } else {
            jmethodID primaryHomeBootstrap = env->GetStaticMethodID(
                    primaryHomeHookClass, "bootstrap", "(Ljava/lang/ClassLoader;)V");
            if (jniResultUnavailable(
                    env, primaryHomeBootstrap, "find primary HOME hook bootstrap")) {
                LOGE("Primary HOME hook bootstrap was unavailable");
            } else {
                env->CallStaticVoidMethod(
                        primaryHomeHookClass, primaryHomeBootstrap, systemClassLoaderRef);
                if (!clearException(env, "run primary HOME hook bootstrap")) {
                    LOGI("Primary HOME hook bootstrap started");
                }
            }
        }
        closeStatusHookDiagnostic();
    }

private:
    void invokeAppHookInstall(jclass targetClass, const char *label,
                              const char *packageName, const char *processName) {
        jmethodID install = env->GetStaticMethodID(
                targetClass, "install", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (jniResultUnavailable(env, install, "find image drag hook install")) {
            LOGE("Image drag %s hook install method unavailable", label);
            return;
        }
        jstring packageValue = env->NewStringUTF(packageName);
        jstring processValue = env->NewStringUTF(processName);
        env->CallStaticVoidMethod(targetClass, install, packageValue, processValue);
        if (!clearException(env, "run image drag hook install")) {
            LOGI("Image drag %s hook installed in %s", label, processName);
        }
    }

    void invokeUniversalImageHookInstall(
            jclass targetClass, const zygisk::AppSpecializeArgs *args) {
        if (targetClass == nullptr || args == nullptr || args->nice_name == nullptr) {
            return;
        }
        jmethodID install = env->GetStaticMethodID(
                targetClass, "install", "(Ljava/lang/String;Ljava/lang/String;)V");
        if (jniResultUnavailable(env, install, "find universal image hook install")) {
            LOGE("Image drag universal hook install method unavailable");
            return;
        }
        env->CallStaticVoidMethod(
                targetClass, install, args->nice_name, args->nice_name);
        if (!clearException(env, "run universal image hook install")) {
            LOGI("Image drag universal hook installed in app process");
        }
    }

    static void closeStatusHookDiagnostic() {
        if (statusHookDiagnosticFd >= 0) {
            close(statusHookDiagnosticFd);
            statusHookDiagnosticFd = -1;
        }
    }

    zygisk::Api *api = nullptr;
    JNIEnv *env = nullptr;
    jclass hookClass = nullptr;
    jclass primaryHomeHookClass = nullptr;
    jclass rootDisplayCompatHookClass = nullptr;
    jclass hyperOsAppHookClass = nullptr;
    jclass googlePhotosDragHookClass = nullptr;
    jclass universalImageDragHookClass = nullptr;
    jobject appClassLoaderRef = nullptr;
    jobject systemClassLoaderRef = nullptr;
    jobject appProcessSystemClassLoaderRef = nullptr;
    bool secureWindowHookEnabled = true;
    bool statusBarOverlayHookEnabled = true;
    bool primaryHomeEnhancementEnabled = true;
    bool lsposedBackendSelected = false;
};

} // namespace

REGISTER_ZYGISK_MODULE(OneStepZygiskModule)
