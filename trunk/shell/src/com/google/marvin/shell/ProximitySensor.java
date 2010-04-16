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

    private SensorManager sManager;

    private Sensor pSensor;

    private float farValue;

    private ProximityChangeListener callback;

    private boolean close;

    SensorEventListener listener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Ignoring this for now
        }

        public void onSensorChanged(SensorEvent event) {
            if (event.values[0] < farValue) {
                close = true;
                callback.onProximityChanged(0);
            } else {
                close = false;
                callback.onProximityChanged(1);
            }
        }
    };

    /**
     * Constructor for ProximitySensor
     * 
     * @param ctx The Context of the app using the proximity sensor
     * @param proximityChangeListener Callback that will be invoked when a
     *            change is detected.
     */
    public ProximitySensor(Context ctx, ProximityChangeListener proximityChangeListener) {
        callback = proximityChangeListener;
        close = false;
        sManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        pSensor = sManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (pSensor != null) {
            farValue = pSensor.getMaximumRange();
            sManager.registerListener(listener, pSensor, SensorManager.SENSOR_DELAY_UI);
        }
    }

    /**
     * Checks if something is close to the proximity sensor
     * 
     * @return True if there is something close to the proximity sensor
     */
    public boolean isClose() {
        return close;
    }

    /**
     * The app using the ProximitySensor must shut it down when it is done using
     * it.
     */
    public void shutdown() {
        if (pSensor != null) {
            sManager.unregisterListener(listener);
            pSensor = null;
        }
    }

}
