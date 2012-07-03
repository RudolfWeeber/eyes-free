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
 * Bluetooth functionality that works on the Android NDK.
 */

#ifndef BRLTTY_INCLUDED_BLUETOOTH_ANDROID_H_
#define BRLTTY_INCLUDED_BLUETOOTH_ANDROID_H_

#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct BluetoothAndroidConnectionStruct BluetoothAndroidConnection;

struct BluetoothAndroidConnectionStruct {
  /* This should be a file descriptor in non-blocking mode that can be read
   * to read more data from the bluetooth connection.
   */
  int read_fd;
  /* Arbitrary client-owned data. */
  void *data;
  /* Function that is used to write data to the bluetooth connection
   * with the usual posix semanics.
   */
  ssize_t (*writeData)(BluetoothAndroidConnection *conn,
                       const void* buffer,
                       size_t size);
};

/*
 * Store a connection struct that will be used when a bluetooth
 * connection is 'opened' by the brltty driver.  This is global
 * state: there can be only one connection at a time.
 */
void bluetoothAndroidSetConnection(
    BluetoothAndroidConnection* conn);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif /* BRLTTY_INCLUDED_BLUETOOTH_ANDROID_H_ */
