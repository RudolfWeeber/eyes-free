# Copyright 2012 Google Inc.
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

WRAPPER_PATH := $(call my-dir)
LOCAL_PATH := $(WRAPPER_PATH)
LIBLOUIS_PATH := $(WRAPPER_PATH)/liblouis

#----------------------------------------------------------------
# liblouiswrap

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)
LOCAL_LDFLAGS := $(LIBLOUIS_LDFLAGS)
LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louiswrap
LOCAL_SRC_FILES := LibLouisWrapper.c
LOCAL_C_INCLUDES := $(WRAPPER_PATH)/.. $(LIBLOUIS_PATH)
LOCAL_WHOLE_STATIC_LIBRARIES := liblouis

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# liblouis

include $(CLEAR_VARS)

LOCAL_PATH := $(LIBLOUIS_PATH)
LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louis
LOCAL_SRC_FILES := \
	liblouis/compileTranslationTable.c \
	liblouis/lou_backTranslateString.c \
	liblouis/lou_translateString.c \
	liblouis/wrappers.c
LOCAL_C_INCLUDES := $(WRAPPER_PATH)/.. $(WRAPPER_PATH)

include $(BUILD_STATIC_LIBRARY)
