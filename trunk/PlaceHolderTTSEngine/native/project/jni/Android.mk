LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	com_google_placeHolderTTSEngine.cpp

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	apps/PlaceHolderTTSEngine/project/jni/libPlaceHolderTTS/src

LOCAL_STATIC_LIBRARIES := \
	libPlaceHolderTTS

LOCAL_MODULE:= PlaceHolderTTSEngine

LOCAL_ARM_MODE := arm

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
