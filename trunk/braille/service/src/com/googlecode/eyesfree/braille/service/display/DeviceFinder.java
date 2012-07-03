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

package com.googlecode.eyesfree.braille.service.display;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Finds supported devices among bonded devices.
 */
public class DeviceFinder {

    private static final String LOG_TAG = DeviceFinder.class.getSimpleName();
    private static final UUID SERIAL_BOARD_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String LAST_CONNECTED_DEVICE_KEY =
            "lastBluetoothDevice";

    private final SharedPreferences mSharedPreferences;

    /**
     * Information about a supported bonded bluetooth device.
     */
    public static class DeviceInfo {
        private final BluetoothDevice mBluetoothDevice;
        private final String mDriverCode;
        private final UUID mSdpUuid;
        private final boolean mConnectSecurely;

        public DeviceInfo(BluetoothDevice bluetoothDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely) {
            mBluetoothDevice = bluetoothDevice;
            mDriverCode = driverCode;
            mSdpUuid = sdpUuid;
            mConnectSecurely = connectSecurely;
        }

        /**
         * Returns the bluetooth device from the system.
         */
        public BluetoothDevice getBluetoothDevice() {
            return mBluetoothDevice;
        }

        /**
         * Returns the brltty driver code to use for this device.
         */
        public String getDriverCode() {
            return mDriverCode;
        }

        /**
         * Returns the service record uuid to use when connecting to
         * this device.
         */
        public UUID getSdpUuid() {
            return mSdpUuid;
        }

        /**
         * Returns whether to connect securely (preferred)
         * or not.
         * @see BluetoothDevice#createInsecureRfcommSocketToServiceRecord
         * @see BluetoothDevice#createRfcommSocketToServiceRecord
         */
        public boolean getConnectSecurely() {
            return mConnectSecurely;
        }
    }

    public DeviceFinder(Context context) {
        mSharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Returns a list of bonded and supported devices in the order they
     * should be tried.
     */
    public List<DeviceInfo> findDevices() {
        List<DeviceInfo> ret = new ArrayList<DeviceInfo>();
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
        for (BluetoothDevice dev : bondedDevices) {
            for (SupportedDevice matcher : SUPPORTED_DEVICES) {
                DeviceInfo matched = matcher.match(dev);
                if (matched != null) {
                    ret.add(matched);
                }
            }
        }
        String lastAddress = mSharedPreferences.getString(
            LAST_CONNECTED_DEVICE_KEY, null);
        if (lastAddress != null) {
            // If the last device that was successfully connected is
            // not already first in the list, put it there.
            // (Hence, the 1 below is intentional).
            for (int i = 1; i < ret.size(); ++i) {
                if (ret.get(i).getBluetoothDevice().getAddress().equals(
                        lastAddress)) {
                    Collections.swap(ret, 0, i);
                }
            }
        }
        return ret;
    }

    public void rememberSuccessfulConnection(DeviceInfo info) {
        BluetoothDevice device = info.getBluetoothDevice();
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        editor.putString(LAST_CONNECTED_DEVICE_KEY, device.getAddress());
        editor.apply();
    }

    private static interface SupportedDevice {
        DeviceInfo match(BluetoothDevice bluetoothDevice);
    }

    private static class NamePrefixSupportedDevice
            implements SupportedDevice {
        private final String mDriverCode;
        private final boolean mConnectSecurely;
        private final String[] mNamePrefixes;

        public NamePrefixSupportedDevice(String driverCode,
                boolean connectSecurely,
                String... namePrefixes) {
            mDriverCode = driverCode;
            mConnectSecurely = connectSecurely;
            mNamePrefixes = namePrefixes;
        }

        @Override
        public DeviceInfo match(BluetoothDevice bluetoothDevice) {
            String name = bluetoothDevice.getName();
            for (String prefix : mNamePrefixes) {
                if (name.startsWith(prefix)) {
                    return new DeviceInfo(bluetoothDevice, mDriverCode,
                            SERIAL_BOARD_UUID, mConnectSecurely);
                }
            }
            return null;
        }
    }

    private static final List<SupportedDevice> SUPPORTED_DEVICES;
    static {
        // TODO: Follow up on why secure connections can't be established
        // with some devices.
        ArrayList<SupportedDevice> l = new ArrayList<SupportedDevice>();
        l.add(new NamePrefixSupportedDevice("vo", true, "EL12-"));
        l.add(new NamePrefixSupportedDevice("eu", true, "Esys-"));
        l.add(new NamePrefixSupportedDevice("fs", true, "Focus 40 BT"));
        // Secure connections currently fail on Android devices for the
        // Brailliants.
        l.add(new NamePrefixSupportedDevice("hw", false, "Brailliant BI"));
        // Secure connections get prematurely closed 50% of the time
        // by the Refreshabraille.
        l.add(new NamePrefixSupportedDevice("bm", false, "Refreshabraille"));
        l.add(new NamePrefixSupportedDevice("pm", true, "braillex trio"));
        SUPPORTED_DEVICES = Collections.unmodifiableList(l);
    }
}
