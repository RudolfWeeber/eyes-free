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

# This file defines the make function build-braille-drivers to  build a
# set of modules for a list of brltty braille drivers.

DRIVER_BASE_PATH := brltty/Drivers/Braille

# This gets appended the name of each driver module.
DRIVER_MODULES := $(empty)

# Used by the included driver make files to locate the braille.mk file.
# The slash at the end is required.
SRC_TOP := $(LOCAL_PATH)/build/

# Internal template to generate the code to build a driver module.
define ev-build-braille-driver
include $$(CLEAR_VARS)
DRIVER_PATH := $$(DRIVER_BASE_PATH)/$1
LOCAL_C_INCLUDES := $$(BRLTTY_PATH) $$(BRLTTY_PATH)/Programs
# Set by the included make file if the sources are not the standard one.
SRC_FILES := $$(empty)
# Clear variables that should be set by the included make file.
DRIVER_NAME := $$(empty)
DRIVER_CODE := $$(empty)
DRIVER_COMMENT := $$(empty)
DRIVER_VERSION := $$(empty)
DRIVER_DEVELOPERS := $$(empty)
include $$(LOCAL_PATH)/$$(DRIVER_PATH)/Makefile.in
ifeq ($$(SRC_FILES),$$(empty))
LOCAL_SRC_FILES := $$(DRIVER_PATH)/braille.c
else
LOCAL_SRC_FILES := $$(patsubst %,$$(DRIVER_PATH)/%,$$(SRC_FILES))
endif
LOCAL_MODULE := libbr$$(DRIVER_CODE)
LOCAL_CFLAGS := $$(VISIBILITY) \
	'-DDRIVER_NAME=$$(DRIVER_NAME)' '-DDRIVER_CODE=$$(DRIVER_CODE)' \
	'-DDRIVER_COMMENT="$$(DRIVER_COMMENT)"' \
	'-DDRIVER_VERSION="$$(DRIVER_VERSION)"' \
	'-DDRIVER_DEVELOPERS="$$(DRIVER_DEVELOPERS)"' \
	-DHAVE_CONFIG_H
include $$(BUILD_STATIC_LIBRARY)
DRIVER_MODULES += $$(LOCAL_MODULE)
endef

# Builds a braille driver given the directory (relative to
# brltty/Drivers/Braille) of the driver code.
build-braille-driver = $(eval $(call ev-build-braille-driver,$1))

# Builds the specified list of braille drivers.
build-braille-drivers = $(foreach dir,$1,$(call build-braille-driver,$(dir)))
