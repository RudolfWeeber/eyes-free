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

#include "prologue.h"

#include "bluetooth_android.h"

#include <errno.h>

#include "io_bluetooth.h"
#include "bluetooth_internal.h"
#include "log.h"

static BluetoothAndroidConnection* globalConnection = NULL;

struct BluetoothConnectionExtensionStruct {
  BluetoothAndroidConnection* conn;
};

void
bluetoothAndroidSetConnection(BluetoothAndroidConnection* conn) {
  globalConnection = conn;
}


//////////////////////////////////////////////////////////////////////
// Implementation of system-specific bluetooth functions required
// by brltty I/O functions.
//////////////////////////////////////////////////////////////////////
BluetoothConnectionExtension *
bthConnect (uint64_t bda, uint8_t channel) {
  BluetoothConnectionExtension* bcx = NULL;
  if (!globalConnection) {
    logMessage(LOG_ERR, "Opening bluetooth without an andorid bluetooth "
               "conection");
    goto out;
  }
  if ((bcx = malloc(sizeof(*bcx))) == NULL) {
    logMessage(LOG_ERR, "Can't allocate android bluetooth extension struct");
    goto out;
  }
  bcx->conn = globalConnection;
out:
  return bcx;
}

void
bthDisconnect (BluetoothConnectionExtension *bcx) {
  if (bcx->conn != globalConnection) {
    logMessage(LOG_ERR, "Android bluetooth closed after a new connection "
               "was stablished");
  }
  free(bcx);
}

int
bthAwaitInput (BluetoothConnection *connection, int milliseconds) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return awaitInput(conn->read_fd, milliseconds);
}

ssize_t
bthReadData (
  BluetoothConnection *connection, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return readData(conn->read_fd, buffer, size, initialTimeout,
                  subsequentTimeout);
}

ssize_t
bthWriteData (BluetoothConnection *connection, const void *buffer, size_t size) {
  BluetoothAndroidConnection *conn = connection->extension->conn;
  return (*conn->writeData)(conn, buffer, size);
}
