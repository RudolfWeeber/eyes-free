LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := TextEnlarger

LOCAL_PROGUARD_ENABLED := full

include $(BUILD_PACKAGE)
