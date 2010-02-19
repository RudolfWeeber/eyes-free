# Copyright 2010 Google Inc. All Rights Reserved.
#
# Android.mk for PlaceHolderTTS
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= src/PlaceHolderTTS.cpp

LOCAL_PRELINK_MODULE:= false

LOCAL_MODULE:= libPlaceHolderTTS

LOCAL_CFLAGS+= $(TOOL_CFLAGS)

LOCAL_LDFLAGS:= $(TOOL_LDFLAGS) -lstdc++ -lc

include $(BUILD_STATIC_LIBRARY)
