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

/**
 * Convenience class for working with the ProximitySensor
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ProximitySensor {
    private final SensorManager mSensorManager;
    private final Sensor mSensor;
    private final boolean mIgnoreCallbackOnRegistration;
    private final float mFarValue;

    private ProximityChangeListener mCallback;

    private boolean mIgnoreNextCallback;
    private boolean mActive;
    private boolean mClose;

    /**
     * Constructor for ProximitySensor
     * 
     * @param context The parent context.
     * @param ignoreCallbackOnRegistration Flag indicating whether or not to
     *            drop the first callback event after registering the listener.
     */
    public ProximitySensor(Context context, boolean ignoreCallbackOnRegistration) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mIgnoreCallbackOnRegistration = ignoreCallbackOnRegistration;

        if (mSensor != null) {
            mFarValue = mSensor.getMaximumRange();
        } else {
            mFarValue = 0;
        }
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
        return mClose;
    }

    /**
     * @return the current state of the proximity sensor
     */
    public boolean isActive() {
        return mActive;
    }

    /**
     * Stops listening for sensor events.
     */
    public void stop() {
        if (mSensor != null) {
            mSensorManager.unregisterListener(mSensorEventListener);
            mActive = false;
        }
    }

    /**
     * Starts listening for sensor events.
     */
    public void start() {
        if (mSensor != null) {
            mSensorManager.registerListener(mSensorEventListener, mSensor,
                    SensorManager.SENSOR_DELAY_UI);
            mActive = true;

            if (mIgnoreCallbackOnRegistration) {
                mIgnoreNextCallback = true;
            }
        }
    }

    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Do nothing.
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mIgnoreNextCallback) {
                mIgnoreNextCallback = false;
                return;
            }
            if (event.values[0] < mFarValue) {
                mClose = true;
                mCallback.onProximityChanged(mClose);
            } else {
                mClose = false;
                mCallback.onProximityChanged(mClose);
            }
        }
    };

    /**
     * Callback for when the proximity sensor detects a change
     */
    public interface ProximityChangeListener {
        public void onProximityChanged(boolean close);
    }
}
