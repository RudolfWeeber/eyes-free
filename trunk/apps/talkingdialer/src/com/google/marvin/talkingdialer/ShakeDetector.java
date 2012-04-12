
package com.google.marvin.talkingdialer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector {

    public interface ShakeListener {
        public void onShakeDetected();
    }

    private SensorEventListener mListener;

    private ShakeListener cb;

    private SensorManager sensorManager;

    public ShakeDetector(Context context, ShakeListener callback) {
        cb = callback;
        mListener = new SensorEventListener() {
            private final double deletionForce = .8;

            private final int deletionCount = 2;

            int shakeCount = 0;

            boolean lastShakePositive = false;

            private int shakeCountTimeout = 500;

            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                if ((sensorEvent.values[1] > deletionForce) && !lastShakePositive) {
                    (new Thread(new resetShakeCount())).start();
                    shakeCount++;
                    lastShakePositive = true;
                } else if ((sensorEvent.values[1] < -deletionForce) && lastShakePositive) {
                    (new Thread(new resetShakeCount())).start();
                    shakeCount++;
                    lastShakePositive = false;
                }
                if (shakeCount > deletionCount) {
                    shakeCount = 0;
                    cb.onShakeDetected();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int arg1) {
            }

            class resetShakeCount implements Runnable {
                @Override
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
        sensorManager.registerListener(mListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void shutdown() {
        sensorManager.unregisterListener(mListener);
    }

}
