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

import com.googlecode.eyesfree.braille.service.R;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
        private final Map<String, Integer> mFriendlyKeyNames;

        public DeviceInfo(BluetoothDevice bluetoothDevice,
                String driverCode, UUID sdpUuid,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames) {
            mBluetoothDevice = bluetoothDevice;
            mDriverCode = driverCode;
            mSdpUuid = sdpUuid;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
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

        /**
         */
        public Map<String, Integer> getFriendlyKeyNames() {
            return mFriendlyKeyNames;
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
        private final Map<String, Integer> mFriendlyKeyNames;
        private final String[] mNamePrefixes;

        public NamePrefixSupportedDevice(String driverCode,
                boolean connectSecurely,
                Map<String, Integer> friendlyKeyNames,
                String... namePrefixes) {
            mDriverCode = driverCode;
            mConnectSecurely = connectSecurely;
            mFriendlyKeyNames = friendlyKeyNames;
            mNamePrefixes = namePrefixes;
        }

        @Override
        public DeviceInfo match(BluetoothDevice bluetoothDevice) {
            String name = bluetoothDevice.getName();
            for (String prefix : mNamePrefixes) {
                if (name.startsWith(prefix)) {
                    return new DeviceInfo(bluetoothDevice, mDriverCode,
                            SERIAL_BOARD_UUID, mConnectSecurely,
                            mFriendlyKeyNames);
                }
            }
            return null;
        }
    }

    private static class KeyNameMapBuilder {
        private final Map<String, Integer> mNameMap =
                new HashMap<String, Integer>();

        /**
         * Adds a mapping from the internal {@code name} to a friendly name
         * with resource id {@code resId}.
         */
        public KeyNameMapBuilder add(String name, int resId) {
            mNameMap.put(name, resId);
            return this;
        }

        public KeyNameMapBuilder dots6() {
            add("Dot1", R.string.key_Dot1);
            add("Dot2", R.string.key_Dot2);
            add("Dot3", R.string.key_Dot3);
            add("Dot4", R.string.key_Dot4);
            add("Dot5", R.string.key_Dot5);
            add("Dot6", R.string.key_Dot6);
            return this;
        }

        public KeyNameMapBuilder dots8() {
            dots6();
            add("Dot7", R.string.key_Dot7);
            add("Dot8", R.string.key_Dot8);
            return this;
        }

        public KeyNameMapBuilder routing() {
            return add("RoutingKey", R.string.key_Routing);
        }

        public Map<String, Integer> build() {
            return Collections.unmodifiableMap(mNameMap);
        }
    }

    private static final List<SupportedDevice> SUPPORTED_DEVICES;
    static {
        // TODO: Follow up on why secure connections can't be established
        // with some devices.
        ArrayList<SupportedDevice> l = new ArrayList<SupportedDevice>();

        // BraillePen
        l.add(new NamePrefixSupportedDevice("vo", true,
                new KeyNameMapBuilder()
                        .dots6()
                        .add("Shift", R.string.key_BP_Shift)
                        .add("Space", R.string.key_Space)
                        .add("Control", R.string.key_BP_Control)
                        .add("JoystickLeft", R.string.key_JoystickLeft)
                        .add("JoystickRight", R.string.key_JoystickRight)
                        .add("JoystickUp", R.string.key_JoystickUp)
                        .add("JoystickDown", R.string.key_JoystickDown)
                        .add("JoystickEnter", R.string.key_JoystickCenter)
                        .add("ScrollLeft", R.string.key_BP_ScrollLeft)
                        .add("ScrollRight", R.string.key_BP_ScrollRight)
                        .build(),
                        "EL12-"));

        // Esys
        l.add(new NamePrefixSupportedDevice("eu", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Switch1Left", R.string.key_esys_SwitchLeft)
                        .add("Switch1Right", R.string.key_esys_SwitchRight)
                        .add("LeftJoystickLeft",
                                R.string.key_esys_LeftJoystickLeft)
                        .add("LeftJoystickRight",
                                R.string.key_esys_LeftJoystickRight)
                        .add("LeftJoystickUp",
                                R.string.key_esys_LeftJoystickUp)
                        .add("LeftJoystickDown",
                                R.string.key_esys_LeftJoystickDown)
                        .add("LeftJoystickPress",
                                R.string.key_esys_LeftJoystickCenter)
                        .add("RightJoystickLeft",
                                R.string.key_esys_RightJoystickLeft)
                        .add("RightJoystickRight",
                                R.string.key_esys_RightJoystickRight)
                        .add("RightJoystickUp",
                                R.string.key_esys_RightJoystickUp)
                        .add("RightJoystickDown",
                                R.string.key_esys_RightJoystickDown)
                        .add("RightJoystickPress",
                                R.string.key_esys_RightJoystickCenter)
                        .add("Backspace", R.string.key_Backspace)
                        .add("Space", R.string.key_Space)
                        .add("RoutingKey1", R.string.key_Routing)
                        .build(),
                        "Esys-"));

        // Focus 40
        l.add(new NamePrefixSupportedDevice("fs", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Space", R.string.key_Space)
                        .add("LeftAdvance", R.string.key_focus_LeftAdvance)
                        .add("RightAdvance", R.string.key_focus_RightAdvance)
                        .add("LeftWheelPress",
                                R.string.key_focus_LeftWheelPress)
                        .add("LeftWheelDown",
                                R.string.key_focus_LeftWheelDown)
                        .add("LeftWheelUp",
                                R.string.key_focus_LeftWheelUp)
                        .add("RightWheelPress",
                                R.string.key_focus_RightWheelPress)
                        .add("RightWheelDown",
                                R.string.key_focus_RightWheelDown)
                        .add("RightWheelUp",
                                R.string.key_focus_RightWheelUp)
                        .routing()
                        .add("LeftShift", R.string.key_focus_LeftShift)
                        .add("RightShift", R.string.key_focus_RightShift)
                        .add("LeftGdf", R.string.key_focus_LeftGdf)
                        .add("RightGdf", R.string.key_focus_RightGdf)
                        .add("LeftRockerUp", R.string.key_focus_LeftRockerUp)
                        .add("LeftRockerDown",
                                R.string.key_focus_LeftRockerDown)
                        .add("RightRockerUp", R.string.key_focus_RightRockerUp)
                        .add("RightRockerDown",
                                R.string.key_focus_RightRockerDown)
                        .build(),
                        "Focus 40 BT"));

        // Brailliant
        // Secure connections currently fail on Android devices for the
        // Brailliant.
        l.add(new NamePrefixSupportedDevice("hw", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .routing()
                        .add("Space", R.string.key_Space)
                        .add("Power", R.string.key_brailliant_Power)
                        .add("Display1", R.string.key_brailliant_Display1)
                        .add("Display2", R.string.key_brailliant_Display2)
                        .add("Display3", R.string.key_brailliant_Display3)
                        .add("Display4", R.string.key_brailliant_Display4)
                        .add("Display5", R.string.key_brailliant_Display5)
                        .add("Display6", R.string.key_brailliant_Display6)
                        .add("Thumb1", R.string.key_brailliant_Thumb1)
                        .add("Thumb2", R.string.key_brailliant_Thumb2)
                        .add("Thumb3", R.string.key_brailliant_Thumb3)
                        .add("Thumb4", R.string.key_brailliant_Thumb4)
                        .build(),
                        "Brailliant BI"));

        // APH Refreshabraille.
        // Secure connections get prematurely closed 50% of the time
        // by the Refreshabraille.
        l.add(new NamePrefixSupportedDevice("bm", false,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("Left", R.string.key_JoystickLeft)
                        .add("Right", R.string.key_JoystickRight)
                        .add("Up", R.string.key_JoystickUp)
                        .add("Down", R.string.key_JoystickDown)
                        .add("Press", R.string.key_JoystickCenter)
                        .routing()
                        .add("Display2", R.string.key_APH_AdvanceLeft)
                        .add("Display5", R.string.key_APH_AdvanceRight)
                        .add("B9", R.string.key_Space)
                        .add("B10", R.string.key_Space)
                        .build(),
                        "Refreshabraille"));

        // Braillex Trio
        l.add(new NamePrefixSupportedDevice("pm", true,
                new KeyNameMapBuilder()
                        .dots8()
                        .add("LeftSpace", R.string.key_Space)
                        .add("RightSpace", R.string.key_Space)
                        .add("Space", R.string.key_Space)
                        .add("LeftThumb", R.string.key_braillex_LeftThumb)
                        .add("RightThumb", R.string.key_braillex_RightThumb)
                        .add("RoutingKey1", R.string.key_Routing)
                        .add("BarLeft1", R.string.key_braillex_BarLeft1)
                        .add("BarLeft2", R.string.key_braillex_BarLeft2)
                        .add("BarRight1", R.string.key_braillex_BarRight1)
                        .add("BarRight2", R.string.key_braillex_BarRight2)
                        .add("BarUp1", R.string.key_braillex_BarUp1)
                        .add("BarUp2", R.string.key_braillex_BarUp2)
                        .add("BarRight1", R.string.key_braillex_BarRight1)
                        .add("BarRight2", R.string.key_braillex_BarRight2)
                        .add("LeftKeyRear", R.string.key_braillex_LeftKeyRear)
                        .add("LeftKeyFront", R.string.key_braillex_LeftKeyFront)
                        .add("RightKeyRear", R.string.key_braillex_RightKeyRear)
                        .add("RightKeyFront",
                                R.string.key_braillex_RightKeyFront)
                        .build(),
                        "braillex trio"));

        SUPPORTED_DEVICES = Collections.unmodifiableList(l);
    }
}
