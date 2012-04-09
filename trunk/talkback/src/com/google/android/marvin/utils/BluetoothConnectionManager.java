/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.marvin.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.googlecode.eyesfree.compat.bluetooth.BluetoothAdapterCompat;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothDeviceCompat;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothHeadsetCompat;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothProfileCompat;
import com.googlecode.eyesfree.compat.bluetooth.BluetoothProfileCompat.ServiceListenerCompat;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.List;

/**
 * A helper class for handling the Bluetooth headset.
 */
public class BluetoothConnectionManager {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 11;

    private final Listener mListener;
    private final int mStartupTimeoutMillis;
    private final BluetoothAdapterCompat mBluetoothAdapter;
    private final BluetoothHandler mBluetoothHandler;

    private BluetoothDeviceCompat mBluetoothDevice;
    private BluetoothHeadsetCompat mBluetoothProxy;

    private boolean mBluetoothConnectionCanceled = false;

    /**
     * Creates the Bluetooth handler. No connection to a device is made in the
     * constructor.
     * 
     * @param listener the listener that will be notified about the bluetooth
     *            connection state changes
     */
    public BluetoothConnectionManager(int startupTimeoutMillis, Listener listener) {
        mListener = listener;
        mStartupTimeoutMillis = startupTimeoutMillis;
        mBluetoothAdapter = BluetoothAdapterCompat.getDefaultAdapter();
        mBluetoothHandler = new BluetoothHandler();
    }

    private void startWatchdog() {
        stopWatchdog();

        mBluetoothConnectionCanceled = false;

        mBluetoothHandler.cancelBluetooth(mStartupTimeoutMillis);
    }

    private void stopWatchdog() {
        mBluetoothHandler.cancelBluetooth(0);
    }

    private void startBluetoothInternal(BluetoothHeadsetCompat proxy) {
        stopWatchdog();

        if (mBluetoothConnectionCanceled) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfileCompat.HEADSET, proxy);
            return;
        }

        final List<BluetoothDeviceCompat> devices = BluetoothProfileCompat.getConnectedDevices(proxy);

        if (devices.size() > 0) {
            mBluetoothDevice = devices.get(0);
            mBluetoothProxy = proxy;

            LogUtils.log(BluetoothConnectionManager.class, Log.INFO,
                    "Connected to a bluetooth device.");
        } else {
            LogUtils.log(BluetoothConnectionManager.class, Log.DEBUG,
                    "No device is connected, closing Bluetooth proxy.");

            mBluetoothAdapter.closeProfileProxy(BluetoothProfileCompat.HEADSET, proxy);
        }

        mListener.onConnectionComplete();
    }

    private void cancel() {
        mListener.onConnectionComplete();
        stop();
    }

    /**
     * Closes the connection to the Bluetooth proxy. This method must be called
     * to prevent resource leakage.
     */
    public void stop() {
        LogUtils.log(BluetoothConnectionManager.class, Log.DEBUG, "Closing bluetooth proxy: %s",
                mBluetoothProxy);

        mBluetoothConnectionCanceled = true;

        stopWatchdog();

        if (mBluetoothProxy != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfileCompat.HEADSET, mBluetoothProxy);
            mBluetoothProxy = null;
            mBluetoothDevice = null;
        }
    }

    /**
     * @return {@code false} if no devices are available or the
     *         {@link BluetoothAdapterCompat} is turned off.
     */
    public boolean isBluetoothAvailable() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()
                && mBluetoothAdapter.getBondedDevices() != null
                && mBluetoothAdapter.getBondedDevices().size() > 0;
    }

    /**
     * Initiates an asynchronous connection to a bluetooth device. This function
     * should be called on the main thread.
     * {@link Listener#onConnectionComplete} will be called on the main thread
     * after invoking this method.
     * 
     * @param context the {@link Context} in which the handler is created
     */
    public void start(Context context) {
        if (mBluetoothProxy != null) {
            LogUtils.log(BluetoothConnectionManager.class, Log.DEBUG,
                    "BluetoothHandler.start() was called without closing the proxy.");
            mListener.onConnectionComplete();
            return;
        }

        startWatchdog();

        mBluetoothAdapter.getProfileProxy(context,
                mBluetoothHeadsetServiceListener, BluetoothProfileCompat.HEADSET);
    }

    public boolean startSco() {
        if (mBluetoothProxy == null || mBluetoothDevice == null) {
            return false;
        }

        return mBluetoothProxy.startScoUsingVirtualVoiceCall(mBluetoothDevice);
    }

    public boolean stopSco() {
        if (mBluetoothProxy == null || mBluetoothDevice == null) {
            return false;
        }

        return mBluetoothProxy.stopScoUsingVirtualVoiceCall(mBluetoothDevice);
    }

    public boolean isAudioConnected() {
        if (mBluetoothProxy == null || mBluetoothDevice == null) {
            return false;
        }

        return mBluetoothProxy.isAudioConnected(mBluetoothDevice);
    }

    private final ServiceListenerCompat
            mBluetoothHeadsetServiceListener = new ServiceListenerCompat() {
                    @Override
                public void onServiceConnected(int profile, Object proxy) {
                    LogUtils.log(BluetoothConnectionManager.class, Log.DEBUG,
                            "Bluetooth service connected");

                    final BluetoothHeadsetCompat headset = new BluetoothHeadsetCompat(proxy);

                    mBluetoothHandler.startBluetooth(headset);
                }

                    @Override
                public void onServiceDisconnected(int profile) {
                    LogUtils.log(BluetoothConnectionManager.class, Log.DEBUG,
                            "Bluetooth service disconnected");

                    mBluetoothHandler.stopBluetooth();
                }
            };

    /**
     * An interface for the bluetooth callbacks.
     */
    public interface Listener {

        /**
         * Called when a bluetooth headset connection establishment is complete.
         * If the connection to a bluetooth headset was successful,
         * BluetoothHeadsetCompat.startVoiceRecognition() is called before this
         * callback to redirect the input audio to the headset, otherwise the
         * audio input will be received from the mic. This call is done on the
         * main thread.
         */
        void onConnectionComplete();
    }

    private class BluetoothHandler extends Handler {
        private static final int MSG_START_BLUETOOTH = 1;
        private static final int MSG_STOP_BLUETOOTH = 2;
        private static final int MSG_CANCEL_BLUETOOTH = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_START_BLUETOOTH:
                    startBluetoothInternal((BluetoothHeadsetCompat) msg.obj);
                    break;
            case MSG_STOP_BLUETOOTH:
                    stop();
                    break;
            case MSG_CANCEL_BLUETOOTH:
                    cancel();
                    break;
            }

            super.handleMessage(msg);
        }

        public void startBluetooth(BluetoothHeadsetCompat proxy) {
            obtainMessage(MSG_START_BLUETOOTH, proxy).sendToTarget();
        }

        public void stopBluetooth() {
            sendEmptyMessage(MSG_STOP_BLUETOOTH);
        }

        public void cancelBluetooth(long timeout) {
            sendEmptyMessageDelayed(MSG_STOP_BLUETOOTH, timeout);
        }
    }
}
