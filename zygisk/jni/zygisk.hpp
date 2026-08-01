/* Copyright 2022-2023 John "topjohnwu" Wu
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS.
 */

#pragma once

#include <jni.h>
#include <stdint.h>
#include <sys/types.h>

#define ZYGISK_API_VERSION 4

namespace zygisk {

struct Api;
struct AppSpecializeArgs;
struct ServerSpecializeArgs;

class ModuleBase {
public:
    virtual void onLoad(Api *, JNIEnv *) {}
    virtual void preAppSpecialize(AppSpecializeArgs *) {}
    virtual void postAppSpecialize(const AppSpecializeArgs *) {}
    virtual void preServerSpecialize(ServerSpecializeArgs *) {}
    virtual void postServerSpecialize(const ServerSpecializeArgs *) {}
};

struct AppSpecializeArgs {
    jint &uid;
    jint &gid;
    jintArray &gids;
    jint &runtime_flags;
    jobjectArray &rlimits;
    jint &mount_external;
    jstring &se_info;
    jstring &nice_name;
    jstring &instruction_set;
    jstring &app_data_dir;
    jintArray *const fds_to_ignore;
    jboolean *const is_child_zygote;
    jboolean *const is_top_app;
    jobjectArray *const pkg_data_info_list;
    jobjectArray *const whitelisted_data_info_list;
    jboolean *const mount_data_dirs;
    jboolean *const mount_storage_dirs;

    AppSpecializeArgs() = delete;
};

struct ServerSpecializeArgs {
    jint &uid;
    jint &gid;
    jintArray &gids;
    jint &runtime_flags;
    jlong &permitted_capabilities;
    jlong &effective_capabilities;

    ServerSpecializeArgs() = delete;
};

namespace internal {
struct api_table;
template <class T> void entry_impl(api_table *, JNIEnv *);
}

enum Option : int {
    FORCE_DENYLIST_UNMOUNT = 0,
    DLCLOSE_MODULE_LIBRARY = 1,
};

struct Api {
    int connectCompanion();
    int getModuleDir();
    void setOption(Option opt);
    uint32_t getFlags();
    bool exemptFd(int fd);
    void hookJniNativeMethods(JNIEnv *env, const char *className,
                              JNINativeMethod *methods, int numMethods);
    void pltHookRegister(dev_t dev, ino_t inode, const char *symbol,
                         void *newFunc, void **oldFunc);
    bool pltHookCommit();

private:
    internal::api_table *tbl;
    template <class T> friend void internal::entry_impl(internal::api_table *, JNIEnv *);
};

#define REGISTER_ZYGISK_MODULE(clazz)                                      \
void zygisk_module_entry(zygisk::internal::api_table *table, JNIEnv *env) { \
    zygisk::internal::entry_impl<clazz>(table, env);                        \
}

namespace internal {

struct module_abi {
    long api_version;
    ModuleBase *impl;
    void (*preAppSpecialize)(ModuleBase *, AppSpecializeArgs *);
    void (*postAppSpecialize)(ModuleBase *, const AppSpecializeArgs *);
    void (*preServerSpecialize)(ModuleBase *, ServerSpecializeArgs *);
    void (*postServerSpecialize)(ModuleBase *, const ServerSpecializeArgs *);

    explicit module_abi(ModuleBase *module) : api_version(ZYGISK_API_VERSION), impl(module) {
        preAppSpecialize = [](auto m, auto args) { m->preAppSpecialize(args); };
        postAppSpecialize = [](auto m, auto args) { m->postAppSpecialize(args); };
        preServerSpecialize = [](auto m, auto args) { m->preServerSpecialize(args); };
        postServerSpecialize = [](auto m, auto args) { m->postServerSpecialize(args); };
    }
};

struct api_table {
    void *impl;
    bool (*registerModule)(api_table *, module_abi *);
    void (*hookJniNativeMethods)(JNIEnv *, const char *, JNINativeMethod *, int);
    void (*pltHookRegister)(dev_t, ino_t, const char *, void *, void **);
    bool (*exemptFd)(int);
    bool (*pltHookCommit)();
    int (*connectCompanion)(void *);
    void (*setOption)(void *, Option);
    int (*getModuleDir)(void *);
    uint32_t (*getFlags)(void *);
};

template <class T>
void entry_impl(api_table *table, JNIEnv *env) {
    static Api api;
    api.tbl = table;
    static T module;
    static module_abi abi(&module);
    if (table->registerModule(table, &abi)) {
        module.onLoad(&api, env);
    }
}

} // namespace internal

inline int Api::connectCompanion() {
    return tbl->connectCompanion ? tbl->connectCompanion(tbl->impl) : -1;
}

inline int Api::getModuleDir() {
    return tbl->getModuleDir ? tbl->getModuleDir(tbl->impl) : -1;
}

inline void Api::setOption(Option opt) {
    if (tbl->setOption) tbl->setOption(tbl->impl, opt);
}

inline uint32_t Api::getFlags() {
    return tbl->getFlags ? tbl->getFlags(tbl->impl) : 0;
}

inline bool Api::exemptFd(int fd) {
    return tbl->exemptFd && tbl->exemptFd(fd);
}

inline void Api::hookJniNativeMethods(JNIEnv *env, const char *className,
                                      JNINativeMethod *methods, int numMethods) {
    if (tbl->hookJniNativeMethods) {
        tbl->hookJniNativeMethods(env, className, methods, numMethods);
    }
}

inline void Api::pltHookRegister(dev_t dev, ino_t inode, const char *symbol,
                                 void *newFunc, void **oldFunc) {
    if (tbl->pltHookRegister) {
        tbl->pltHookRegister(dev, inode, symbol, newFunc, oldFunc);
    }
}

inline bool Api::pltHookCommit() {
    return tbl->pltHookCommit && tbl->pltHookCommit();
}

} // namespace zygisk

extern "C" {
[[gnu::visibility("default"), maybe_unused]]
void zygisk_module_entry(zygisk::internal::api_table *, JNIEnv *);
}
