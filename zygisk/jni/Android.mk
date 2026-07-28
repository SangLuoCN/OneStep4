LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := onestep_zygisk
LOCAL_SRC_FILES := onestep_zygisk.cpp
LOCAL_LDLIBS := -llog -lstdc++
include $(BUILD_SHARED_LIBRARY)
