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
import android.hardware.SensorListener;
import android.hardware.SensorManager;

/**
 * Uses the accelerometer to detect when the user is shaking the phone.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ShakeDetector {
    // Change this to true to enable shake to erase
    private static final boolean useShake = false;

    public interface ShakeListener {
        public void onShakeDetected();
    }

    private SensorListener mListener;

    private ShakeListener cb;

    private SensorManager sensorManager;

    public ShakeDetector(Context context, ShakeListener callback) {
        if (!useShake) {
            return;
        }
        cb = callback;
        mListener = new SensorListener() {
            private final double deletionForce = .8;

            private final int deletionCount = 2;

            int shakeCount = 0;

            boolean lastShakePositive = false;

            private int shakeCountTimeout = 500;

            public void onSensorChanged(int sensor, float[] values) {
                if ((values[1] > deletionForce) && !lastShakePositive) {
                    (new Thread(new resetShakeCount())).start();
                    shakeCount++;
                    lastShakePositive = true;
                } else if ((values[1] < -deletionForce) && lastShakePositive) {
                    (new Thread(new resetShakeCount())).start();
                    shakeCount++;
                    lastShakePositive = false;
                }
                if (shakeCount > deletionCount) {
                    shakeCount = 0;
                    cb.onShakeDetected();
                }
            }

            public void onAccuracyChanged(int arg0, int arg1) {
            }

            class resetShakeCount implements Runnable {
                public void run() {
                    try {
                        Thread.sleep(shakeCountTimeout);
                        shakeCount = 0;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(mListener, SensorManager.SENSOR_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void shutdown() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(mListener);
        }
    }

}
