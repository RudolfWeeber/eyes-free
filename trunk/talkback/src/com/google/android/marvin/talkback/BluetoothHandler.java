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

package com.google.android.marvin.talkback;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.List;

/**
 * A helper class for handling the Bluetooth headset.
 */
class BluetoothHandler {
    private static final String TAG = BluetoothHandler.class.getSimpleName();

    private static final boolean DBG = true;

    private static final int MSG_START_BLUETOOTH = 1;
    private static final int MSG_STOP_BLUETOOTH = 2;
    private static final int MSG_CANCEL_BLUETOOTH = 3;

    private final Listener mListener;
    private final int mStartupTimeoutMillis;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private final BluetoothHeadset.ServiceListener mBluetoothHeadsetServiceListener;

    private BluetoothDevice mBluetoothDevice;
    private BluetoothHeadset mBluetoothProxy;
    private BluetoothHeadsetCompatWrapper mBluetoothProxyCompat;

    private boolean mBluetoothConnectionCanceled = false;

    /**
     * Creates the Bluetooth handler. No connection to a device is made in the
     * constructor.
     *
     * @param listener the listener that will be notified about the bluetooth
     *            connection state changes
     */
    public BluetoothHandler(int startupTimeoutMillis, Listener listener) {
        mListener = listener;
        mStartupTimeoutMillis = startupTimeoutMillis;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case MSG_START_BLUETOOTH:
                        startBluetooth((BluetoothHeadset) msg.obj);
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
        };
        mBluetoothHeadsetServiceListener = new BluetoothHeadset.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (DBG)
                    Log.d(TAG, "BluetoothHeadset.ServiceListener.onServiceConnected");
                Message.obtain(mHandler, MSG_START_BLUETOOTH, proxy).sendToTarget();
            }

            @Override
            public void onServiceDisconnected(int profile) {
                if (DBG)
                    Log.d(TAG, "BluetoothHeadset.ServiceListener.onServiceDisconnected");
                Message.obtain(mHandler, MSG_STOP_BLUETOOTH).sendToTarget();
            }
        };
    }

    private void startWatchdog() {
        stopWatchdog();
        mBluetoothConnectionCanceled = false;
        mHandler.sendMessageDelayed(Message.obtain(mHandler, MSG_CANCEL_BLUETOOTH),
                mStartupTimeoutMillis);
    }

    private void stopWatchdog() {
        mHandler.removeMessages(MSG_CANCEL_BLUETOOTH);
    }

    private void startBluetooth(BluetoothHeadset proxy) {
        stopWatchdog();
        if (mBluetoothConnectionCanceled) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);
            return;
        }
        List<BluetoothDevice> devices = proxy.getConnectedDevices();
        if (devices.size() > 0) {
            mBluetoothDevice = devices.get(0);
            mBluetoothProxy = proxy;
            mBluetoothProxyCompat = new BluetoothHeadsetCompatWrapper(proxy);
            Log.i(TAG, "Connected to a bluetooth device.");
        } else {
            if (DBG)
                Log.d(TAG, "No device is connected, closing bluetooth proxy.");
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy);
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
        if (DBG)
            Log.d(TAG, "Closing bluetooth proxy:" + mBluetoothProxy);
        mBluetoothConnectionCanceled = true;
        stopWatchdog();
        if (mBluetoothProxy != null) {
            mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothProxy);
            mBluetoothProxy = null;
            mBluetoothProxyCompat = null;
            mBluetoothDevice = null;
        }
    }

    /**
     * @return {@code false} if no devices are available or the
     *         {@link BluetoothAdapter} is turned off.
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
            if (DBG)
                Log.d(TAG, "BluetoothHandler.start() was called without closing the proxy.");
            mListener.onConnectionComplete();
            return;
        }
        startWatchdog();
        mBluetoothAdapter.getProfileProxy(context, mBluetoothHeadsetServiceListener,
                BluetoothProfile.HEADSET);
    }

    public boolean startSco() {
        if (mBluetoothProxy == null || mBluetoothDevice == null)
            return false;

        return mBluetoothProxyCompat.startScoUsingVirtualVoiceCall(mBluetoothDevice);
    }

    public boolean stopSco() {
        if (mBluetoothProxy == null || mBluetoothDevice == null)
            return false;

        return mBluetoothProxyCompat.stopScoUsingVirtualVoiceCall(mBluetoothDevice);
    }

    public boolean isAudioConnected() {
        if (mBluetoothProxy == null || mBluetoothDevice == null)
            return false;

        return mBluetoothProxy.isAudioConnected(mBluetoothDevice);
    }

    /**
     * An interface for the bluetooth callbacks.
     */
    public interface Listener {

        /**
         * Called when a bluetooth headset connection establishment is complete.
         * If the connection to a bluetooth headset was successful,
         * {@link BluetoothHeadset#startVoiceRecognition} is called before this
         * callback to redirect the input audio to the headset, otherwise the
         * audio input will be received from the mic. This call is done on the
         * main thread.
         */
        void onConnectionComplete();
    }
}
