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

package com.googlecode.eyesfree.walkytalky;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class Compass {
    public interface HeadingListener {
        public void onHeadingChanged(String heading);
    }

    private static final String[] DIRECTION_NAMES = {
            "north", "north north east", "north east", "east north east", "east",
            "east south east", "south east", "south south east", "south", "south south west",
            "south west", "west south west", "west", "west north west", "north west",
            "north north west", "north"
    };

    private float mCurrentHeading = 0;

    private boolean mSensorOk = false;
 
    float mLastStableHeading = 0;

    int mStableCount = 0;

    String mLastHeadingName = "";

    int MIN_STABLECOUNT = 50;

    float STABLE_TOLERANCE = 20;

    private HeadingListener mHeadingListener;

    private SensorManager mSensorManager;

    /**
     * Handles the sensor events for changes to readings and accuracy
     */
    private SensorEventListener mListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            mSensorOk = (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
            if (!mSensorOk) {
                mLastHeadingName = "";
                mStableCount = 0;
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Values are yaw (heading), pitch, and roll.
            mCurrentHeading = event.values[0];
            processDirection();
        }
    };

    public Compass(Context ctx, HeadingListener callback) {
        mHeadingListener = callback;
        mSensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);

        mSensorManager.registerListener(mListener, mSensorManager
                .getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_GAME);
    }

    public void shutdown() {
        if (mSensorManager != null) {
            mSensorManager.unregisterListener(mListener);
        }
    }

    public String getCurrentHeading() {
        return mLastHeadingName;
    }

    protected void processDirection() {
        // Do not try bother determining if a new cardinal direction
        // was reached if the sensors are not functioning correctly.
        if (!mSensorOk) {
            mLastHeadingName = "";
            mStableCount = 0;
            return;
        }

        // Do not update immediately - wait until the sensor readings have been
        // stable for some time.
        if (Math.abs(mLastStableHeading - mCurrentHeading) < STABLE_TOLERANCE) {
            mStableCount++;
        } else {
            mLastStableHeading = mCurrentHeading;
            mStableCount = 0;
        }

        if (mStableCount > MIN_STABLECOUNT) {
            String headingName = directionToString(mCurrentHeading);
            if (!headingName.equals(mLastHeadingName)) {
                mLastHeadingName = headingName;
                mHeadingListener.onHeadingChanged(mLastHeadingName);
            }
        }
    }

    public static String directionToString(float heading) {
        int index = (int) ((heading * 100 + 1125) / 2250);
        return DIRECTION_NAMES[index];
    }
}
