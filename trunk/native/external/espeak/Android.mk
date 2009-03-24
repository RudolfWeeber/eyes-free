# Copyright 2008 Google Inc. All Rights Reserved.
#
# Android.mk for espeak
#

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	src/debug.cpp \
	src/compiledict.cpp \
	src/dictionary.cpp \
	src/event.cpp \
	src/fifo.cpp \
	src/intonation.cpp\
	src/numbers.cpp \
	src/phonemelist.cpp \
	src/setlengths.cpp \
	src/speak_lib.cpp \
	src/synth_mbrola.cpp \
	src/synthesize.cpp \
	src/tr_english.cpp \
	src/translate.cpp \
	src/voices.cpp \
	src/wavegen.cpp \
	src/espeak_command.cpp \
	src/readclause.cpp \
	src/synthdata.cpp \
	src/tr_languages.cpp \
	src/wave.cpp

LOCAL_PRELINK_MODULE:= false

LOCAL_MODULE:= libespeak

LOCAL_CFLAGS+= $(TOOL_CFLAGS) -DDEBUG_ENABLED=1 # -Dwchar_t=owchar_t

LOCAL_LDFLAGS:= $(TOOL_LDFLAGS) -lstdc++ -lc

include $(BUILD_STATIC_LIBRARY)
