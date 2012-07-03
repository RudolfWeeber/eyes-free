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

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louis
LOCAL_SRC_FILES := \
	liblouis/liblouis/compileTranslationTable.c \
	liblouis/liblouis/lou_backTranslateString.c \
	liblouis/liblouis/lou_translateString.c \
	liblouis/liblouis/wrappers.c \
	LibLouisWrapper.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)/..

include $(BUILD_SHARED_LIBRARY)
