LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := client

LOCAL_SRC_FILES := Main.cpp

# Only current folder include
LOCAL_C_INCLUDES := $(LOCAL_PATH)

# Compiler Flags
LOCAL_CFLAGS := -Wno-error=format-security -fvisibility=hidden -ffunction-sections -fdata-sections -w
LOCAL_CFLAGS += -fno-rtti -fno-exceptions -fpermissive

LOCAL_CPPFLAGS := -std=c++17 -Wno-error=format-security -fvisibility=hidden -ffunction-sections -fdata-sections -w
LOCAL_CPPFLAGS += -fno-rtti -fno-exceptions -fpermissive

LOCAL_LDFLAGS := -Wl,--gc-sections,--strip-all

LOCAL_LDLIBS := -llog -landroid

include $(BUILD_SHARED_LIBRARY)