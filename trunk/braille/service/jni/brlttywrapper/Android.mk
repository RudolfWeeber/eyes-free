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

# Uncomment the second line below and comment out the first one
# to get a smaller binary with less symbols.
VISIBILITY=
#VISIBILITY=-fvisibility=hidden

LOCAL_PATH := $(call my-dir)

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
LOCAL_WHOLE_STATIC_LIBRARIES := libbrvo libbreu libbrfs libbrhw libbrbm libbrpm
include $(BUILD_STATIC_LIBRARY)


#----------------------------------------------------------------
# Voyager driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbrvo
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := brltty/Drivers/Braille/Voyager/braille.c
LOCAL_CFLAGS += $(VISIBILITY)
LOCAL_CFLAGS += \
	-DDRIVER_CODE=vo \
	-DDRIVER_NAME=Voyager \
	-DDRIVER_COMMENT="\"44/70, Part232, BraillePen/EasyLink\"" \
	-DDRIVER_VERSION="\"0.3 (June 2009)\"" \
	-DDRIVER_DEVELOPERS="\"Stéphane Doyon <s.doyon@videotron.ca>\"" \
	-DHAVE_CONFIG_H

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# EuroBraille driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbreu
LOCAL_C_INCLUDES := $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := \
	brltty/Drivers/Braille/EuroBraille/eu_braille.c \
	brltty/Drivers/Braille/EuroBraille/eu_clio.c \
	brltty/Drivers/Braille/EuroBraille/eu_esysiris.c
LOCAL_CFLAGS += \
	-DDRIVER_CODE=eu \
	-DDRIVER_NAME=EuroBraille \
	-DDRIVER_COMMENT="\"AzerBraille, Clio, Iris, NoteBraille, Scriba, Esys 12/40\"" \
	-DDRIVER_VERSION="\"2.0\"" \
	-DDRIVER_DEVELOPERS="\"Yannick PLASSIARD <yan@mistigri.org>, Olivier BERT <obert01@mistigri.org>, Nicolas PITRE <nico@fluxnic.net>\"" \
	-DHAVE_CONFIG_H
LOCAL_CFLAGS += $(VISIBILITY)

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# Freedom Scientific driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbrfs
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := brltty/Drivers/Braille/FreedomScientific/braille.c
LOCAL_CFLAGS += $(VISIBILITY)
LOCAL_CFLAGS += \
	-DDRIVER_CODE=fs \
	-DDRIVER_NAME=FreedomScientific \
	-DDRIVER_COMMENT="\"Focus 1 44/70/84, Focus 2 40/80, Focus Blue 40, PAC Mate 20/40\"" \
	-DDRIVER_VERSION="\"\"" \
	-DDRIVER_DEVELOPERS="\"Dave Mielke <dave@mielke.cc>\"" \
	-DHAVE_CONFIG_H

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# HumanWare driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbrhw
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := brltty/Drivers/Braille/HumanWare/braille.c
LOCAL_CFLAGS += $(VISIBILITY)
LOCAL_CFLAGS += \
	-DDRIVER_CODE=hw \
	-DDRIVER_NAME=HumanWare \
	-DDRIVER_COMMENT="\"Brailliant 32/40/80 bi\"" \
	-DDRIVER_VERSION="\"\"" \
	-DDRIVER_DEVELOPERS="\"Dave Mielke <dave@mielke.cc>\"" \
	-DHAVE_CONFIG_H

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# Baum driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbrbm
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := brltty/Drivers/Braille/Baum/braille.c
LOCAL_CFLAGS += $(VISIBILITY)
LOCAL_CFLAGS += \
	-DDRIVER_CODE=bm \
	-DDRIVER_NAME=Baum \
	-DDRIVER_COMMENT="\"Inka, Vario/RBT, SuperVario/Brailliant, PocketVario, VarioPro, EcoVario, VarioConnect/BrailleConnect, Refreshabraille\"" \
	-DDRIVER_VERSION="\"\"" \
	-DDRIVER_DEVELOPERS="\"Dave Mielke <dave@mielke.cc>\"" \
	-DHAVE_CONFIG_H

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# Papenmeier driver

include $(CLEAR_VARS)
LOCAL_MODULE := libbrpm
LOCAL_C_INCLUDES:= $(LOCAL_PATH)/brltty $(LOCAL_PATH)/brltty/Programs
LOCAL_SRC_FILES := brltty/Drivers/Braille/Papenmeier/braille.c
LOCAL_CFLAGS += $(VISIBILITY)
LOCAL_CFLAGS += \
	-DDRIVER_CODE=pm \
	-DDRIVER_NAME=Papenmeier \
	-DDRIVER_COMMENT="\"Compact 486, Compact/Tiny, IB 80 CR Soft, 2D Lite (plus), 2D Screen Soft, EL 80, EL 2D 40/66/80, EL 40/66/70/80 S, EL 2D 80 S, EL 40 P, EL 80 II, Elba 20/32, Trio 40/Elba20/Elba32\"" \
	-DDRIVER_VERSION="\"\"" \
	-DDRIVER_DEVELOPERS="\"August Hörandl <august.hoerandl@gmx.at>, Heimo Schön <heimo.schoen@gmx.at>\"" \
	-DHAVE_CONFIG_H

LOCAL_MODULE_TAGS := optional
include $(BUILD_STATIC_LIBRARY)
