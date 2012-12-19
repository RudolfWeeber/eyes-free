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
BRLTTY_PATH := $(LOCAL_PATH)/brltty

include $(LOCAL_PATH)/build/driver.mk

# Uncomment the second line below and comment out the first one
# to get a smaller binary with less symbols.
VISIBILITY=
#VISIBILITY=-fvisibility=hidden

#----------------------------------------------------------------
# List of brltty drivers that are included.  If adding a new driver,
# include the directory name of the driver in the below list.

$(call build-braille-drivers,\
	Voyager \
	EuroBraille \
	FreedomScientific \
	HumanWare \
	Baum \
	Papenmeier \
	HIMS \
	)

#----------------------------------------------------------------
# brlttywrap

include $(CLEAR_VARS)

LOCAL_MODULE    := brlttywrap
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := $(LOCAL_PATH)/.. $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := BrlttyWrapper.c
LOCAL_WHOLE_STATIC_LIBRARIES := libbrltty

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# libbrltty
include $(CLEAR_VARS)

LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty \
	$(LOCAL_PATH)/brltty/Programs \
	$(LOCAL_PATH)

LOCAL_CFLAGS+=-DHAVE_CONFIG_H $(VISIBILITY)
LOCAL_CFLAGS+=-D__ANDROID__

LOCAL_SRC_FILES:= \
	libbrltty.c \
	bluetooth_android.c \
	brltty/Programs/cmd.c \
	brltty/Programs/charset.c \
	brltty/Programs/charset_none.c \
	brltty/Programs/lock.c \
	brltty/Programs/drivers.c \
	brltty/Programs/driver.c \
	brltty/Programs/ttb_translate.c \
	brltty/Programs/ttb_compile.c \
	brltty/Programs/ttb_native.c

# Base objects
LOCAL_SRC_FILES+= \
	brltty/Programs/log.c \
	brltty/Programs/file.c \
	brltty/Programs/device.c \
	brltty/Programs/parse.c \
	brltty/Programs/timing.c \
	brltty/Programs/io_misc.c

# Braille objects
LOCAL_SRC_FILES+= \
	brltty/Programs/brl.c

# IO objects
LOCAL_SRC_FILES+= \
	brltty/Programs/io_generic.c

# Bluetooth objects
LOCAL_SRC_FILES+= \
	brltty/Programs/bluetooth.c \

# System objects
LOCAL_SRC_FILES+= sys_android.c

# Other, not sure where they come from.
LOCAL_SRC_FILES+= \
	brltty/Programs/unicode.c \
	brltty/Programs/queue.c \
	brltty/Programs/serial.c \
	brltty/Programs/serial_none.c \
	brltty/Programs/usb.c \
	brltty/Programs/usb_none.c \
	brltty/Programs/usb_hid.c \
	brltty/Programs/usb_serial.c \
	brltty/Programs/ktb_translate.c \
	brltty/Programs/ktb_compile.c \
	brltty/Programs/async.c \
	brltty/Programs/datafile.c \
	brltty/Programs/dataarea.c

LOCAL_MODULE := brltty
LOCAL_WHOLE_STATIC_LIBRARIES := $(DRIVER_MODULES)
include $(BUILD_STATIC_LIBRARY)
