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

package com.google.android.marvin.talkback;

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

    /**
     * Callback for when the proximity sensor detects a change
     */
    public interface ProximityChangeListener {
        public void onProximityChanged(float proximity);
    }
    
    /**
     * State where the proximity sensor is inactive and uninitialized
     */
    public static final int STATE_STOPPED = 0;
    
    /**
     * State where the proximity sensor is initialized but not detecting
     */
    public static final int STATE_STANDBY = 1;
    
    /**
     * State where the proximity sensor is active and detecting
     */
    public static final int STATE_RUNNING = 2;

    private SensorManager mSensorManager;

    private Sensor mSensor;

    private float mFarValue;
    
    private boolean mIgnoreCallbackOnRegistration;
    
    private boolean mIgnoreNextCallback;
    
    private int mCurrentState = STATE_STOPPED;

    private ProximityChangeListener mCallback;

    private boolean mClose;

    SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Ignoring this for now
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mIgnoreNextCallback) {
                mIgnoreNextCallback = false;
                return;
            }
            if (event.values[0] < mFarValue) {
                mClose = true;
                mCallback.onProximityChanged(0);
            } else {
                mClose = false;
                mCallback.onProximityChanged(1);
            }
        }
    };

    /**
     * Constructor for ProximitySensor
     *
     * @param ctx The Context of the app using the proximity sensor
     * @param ignoreCallbackOnRegistration Flag indicating whether or not to
     *            drop the first callback event after registering the listener.
     * @param proximityChangeListener Callback that will be invoked when a
     *            change is detected.
     */
    public ProximitySensor(Context ctx, boolean ignoreCallbackOnRegistration,
            ProximityChangeListener proximityChangeListener) {
        mCallback = proximityChangeListener;
        mIgnoreCallbackOnRegistration = ignoreCallbackOnRegistration;
        mClose = false;
        mSensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (mSensor != null) {
            mFarValue = mSensor.getMaximumRange();
            mCurrentState = STATE_STANDBY;
        }
    }

    /**
     * Checks if something is close to the proximity sensor
     *
     * @return True if there is something close to the proximity sensor
     */
    public boolean isClose() {
        return mClose;
    }
    
    /**
     * @return the current state of the proximity sensor
     */
    public int getState() {
        return mCurrentState;
    }
                          
    /**
     * Shuts down the sensor, but keeps the instance around for easy resume
     */
    public void standby() {
        if (mCurrentState == STATE_RUNNING) {
            if (mSensor != null) {
                mSensorManager.unregisterListener(listener);
                mCurrentState = STATE_STANDBY;
            }
        }
    }

    /**
     * Re-registers the listener on the sensor, resuming from standby state.
     */
    public void resume() {
        if (mCurrentState == STATE_STANDBY) {
            if (mSensor != null && mSensor != null) {
                mSensorManager.registerListener(listener, mSensor, SensorManager.SENSOR_DELAY_UI);
                mCurrentState = STATE_RUNNING;
                if (mIgnoreCallbackOnRegistration) {
                    mIgnoreNextCallback = true;
                }
            }
        }
    }

    /**
     * The app using the ProximitySensor must shut it down when it is done using it.
     */
    public void shutdown() {
        if (mSensor != null) {
            mSensorManager.unregisterListener(listener);
            mSensor = null;
            mCurrentState = STATE_STOPPED;
        }
    }
}
