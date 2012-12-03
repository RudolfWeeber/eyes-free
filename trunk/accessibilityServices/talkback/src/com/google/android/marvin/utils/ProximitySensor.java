/*
 * Copyright (C) 2010 Google Inc.
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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

/**
 * Convenience class for working with the ProximitySensor. Also uses the ambient
 * light sensor in its place, when available.
 *
 * @author clchen@google.com (Charles L. Chen)
 */
public class ProximitySensor {
    private final SensorManager mSensorManager;
    private final Sensor mProxSensor;
    private final float mFarValue;

    private ProximityChangeListener mCallback;

    /**
     * Whether the next proximity sensor event should be dropped. Used on API <
     * 14 to ignore the first sensor event that occurs after registering a
     * listener.
     */
    private boolean mIgnoreNextEvent;

    /** Whether the user is close to the proximity sensor. */
    private boolean mIsClose;

    /** Whether the sensor is currently active. */
    private boolean mIsActive;

    /**
     * Constructor for ProximitySensor
     *
     * @param context The parent context.
     */
    public ProximitySensor(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mFarValue = (mProxSensor != null) ? mProxSensor.getMaximumRange() : 0;
    }

    public void setProximityChangeListener(ProximityChangeListener listener) {
        mCallback = listener;
    }

    /**
     * Checks if something is close to the proximity sensor
     *
     * @return {@code true} if there is something close to the proximity sensor
     */
    public boolean isClose() {
        return mIsClose;
    }

    /**
     * Stops listening for sensor events.
     */
    public void stop() {
        if ((mProxSensor == null) || !mIsActive) {
            return;
        }

        mIsActive = false;
        mSensorManager.unregisterListener(mListener);
    }

    /**
     * Starts listening for sensor events.
     */
    public void start() {
        if ((mProxSensor == null) || mIsActive) {
            return;
        }

        // On API < 14, the proximity sensor sends an event immediately after
        // registering a listener. We should ignore this event.
        if (Build.VERSION.SDK_INT < 14) {
            mIgnoreNextEvent = true;
        }

        mIsActive = true;
        mSensorManager.registerListener(mListener, mProxSensor, SensorManager.SENSOR_DELAY_UI);
    }

    private final SensorEventListener mListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do nothing.
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mIgnoreNextEvent) {
                mIgnoreNextEvent = false;
                return;
            }

            mIsClose = (event.values[0] < mFarValue);
            mCallback.onProximityChanged(mIsClose);
        }
    };

    /**
     * Callback for when the proximity sensor detects a change
     */
    public interface ProximityChangeListener {
        public void onProximityChanged(boolean isClose);
    }
}
