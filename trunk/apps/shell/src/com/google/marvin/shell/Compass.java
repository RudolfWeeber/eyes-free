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

package com.google.marvin.shell;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Compass uses the magnetic compass to track the current heading.
 * 
 * @author clchen@google.com (Charles L. Chen), credo@google.com (Tim Credo)
 */

public class Compass {
    private Context ctx;

    public Compass(Context context) {
        ctx = context;
        sensorOk = true;
        sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        Sensor compassSensor = sensorManager.getDefaultSensor(SensorManager.SENSOR_ORIENTATION);
        sensorManager.registerListener(mListener, compassSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private static final String[] DIRECTION_NAMES = {
    "north", "north north east", "north east",
            "east north east", "east", "east south east", "south east", "south south east", "south",
            "south south west", "south west", "west south west", "west", "west north west",
            "north west", "north north west", "north" };

    private SensorManager sensorManager;

    private float currentHeading = -1;

    private boolean sensorOk;

    /**
     * Handles the sensor events for changes to readings and accuracy
     */
    private final SensorEventListener mListener = new SensorEventListener() {
            @Override
        public void onSensorChanged(SensorEvent event) {
            // Values are yaw (heading), pitch, and roll.
            currentHeading = event.values[0];
        }

            @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {
            sensorOk = (arg1 == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        }
    };

    public String getCurrentHeading() {
        if (currentHeading == -1) {
            return "";
        }
        if (!sensorOk) {
            return "Please calibrate the compass by shaking your handset.";
        }
        int index = (int) ((currentHeading * 100 + 1125) / 2250);
        return DIRECTION_NAMES[index];
    }

    public double getCurrentHeadingValue() {
        if (currentHeading == -1) {
            return -1;
        }
        if (!sensorOk) {
            return -1;
        }
        return currentHeading;
    }

    public void shutdown() {
        sensorManager.unregisterListener(mListener);
    }

}
