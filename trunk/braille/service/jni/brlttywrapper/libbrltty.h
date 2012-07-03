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
 * This is a library to expose a brlapi-like interface that can be linked
 * into another binary.  The intended use is on Android, compiled under
 * the NDK, meaning that some system and I/O abstractons must be
 * provided by the user of the library.
 *
 * Usage:
 *
 * All this must be called from one and only one thread from initialization to
 * destruction.  There is global state maintianed by this library internally,
 * meaning there can only be one driver active at a time.  This is why there
 * is no 'handle' object for the driver.  Each initialization call should be
 * followed at some point by a matching destroy call.
 */

#ifndef BRLTTY_INCLUDED_LIBBRLTTYH
#define BRLTTY_INCLUDED_LIBBRLTTYH

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 * Initializes a given braille driver, trying to connect to a given
 * device.  Returns non-zero on success.
 */
int
brltty_initialize(const char* driverCode, const char* brailleDevice,
                  const char* tablesDir);

/*
 * Closes the connection and deallocates resources for a braille
 * driver.
 */
int
brltty_destroy(void);

/*
 * Polls the driver for a single key command.  This call is non-blocking.
 * If no command is available, EOF is returned.
 */
int
brltty_readCommand(void);

/*
 * Updates the display with a dot pattern.  dotPattern should contain
 * at least size bytes, one for each braille cell.
 * Further, size should match the size of the display.
 * If it doesn't, the patterns will be silently truncated or padded
 * with blank cells.
 */
int
brltty_writeWindow(unsigned char *dotPattern, size_t size);

/*
 * Returns the number of cells that are present on the display.
 * This does not include any status cells that are separate from the
 * main display.
 */
int
brltty_getTextCells(void);


/*
 * Returns the total number of dedicated status cells, that is cells that are
 * separate from the main display.  This is 0 if the display lacks status
 * cells.
 */
int
brltty_getStatusCells(void);

/*
 * Callback used with brltty_listKeyMap.
 */
typedef int (*KeyMapEntryCallback)(int command, int keyCount,
                                   const char *keys[],
                                   void *data);

/*
 * List the keyboard bindings loaded for the currently connected
 * display.  Invokes the callback for each key binding.
 * data is part of the closure for the callback.
int
brltty_listKeyMap(KeyMapEntryCallback callback, void* data);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_LIBBRLTTYH */
