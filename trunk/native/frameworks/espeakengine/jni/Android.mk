LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	com_google_espeakengine.cpp

LOCAL_C_INCLUDES += \
	$(JNI_H_INCLUDE) \
	$(LOCAL_PATH)/android/graphics \
	$(call include-path-for, corecg graphics) \
	$(call include-path-for, libhardware)/hardware \
	$(LOCAL_PATH)/../../include/ui \
	$(LOCAL_PATH)/../../include/utils \
	external/espeak/src \

LOCAL_STATIC_LIBRARIES := \
	libespeak

LOCAL_SHARED_LIBRARIES := \
        libandroid_runtime \
	libnativehelper \
	libmedia \
	libutils \
	libcutils

XLOCAL_SHARED_LIBRARIES := \
	libexpat \
	libnetutils \
	libui \
	libsgl \
	libcorecg \
	libsqlite \
	libdvm \
	libGLES_CM \
	libhardware \
	libsonivox \
	libcrypto \
	libssl \
	libicuuc \
	libicui18n \
	libicudata \
	libwpa_client

LOCAL_MODULE:= libespeakengine

LOCAL_ARM_MODE := arm

LOCAL_PRELINK_MODULE := false

include $(BUILD_SHARED_LIBRARY)

include $(call all-makefiles-under,$(LOCAL_PATH))
