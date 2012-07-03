/*
 * Copyright 2012 Google Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/*
 * Stubs for various system specific functionality that we don't need or
 * have on Android.
 */

#include "prologue.h"

#include "system.h"

#include "sys_prog_none.h"

#include "sys_boot_none.h"

#include "sys_exec_none.h"

#include "sys_mount_none.h"

#ifdef ENABLE_SHARED_OBJECTS
#include "sys_shlib_none.h"
#endif /* ENABLE_SHARED_OBJECTS */

#include "sys_beep_none.h"

#ifdef ENABLE_PCM_SUPPORT
#include "sys_pcm_none.h"
#endif /* ENABLE_PCM_SUPPORT */

#ifdef ENABLE_MIDI_SUPPORT
#include "sys_midi_none.h"
#endif /* ENABLE_MIDI_SUPPORT */

#include "sys_ports_none.h"
