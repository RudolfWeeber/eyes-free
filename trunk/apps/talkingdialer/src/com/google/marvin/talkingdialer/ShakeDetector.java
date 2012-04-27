
package com.google.marvin.talkingdialer;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Message;

public class ShakeDetector {
    private final ShakeHandler mHandler = new ShakeHandler();

    private final ShakeListener mShakeListener;
    private final SensorManager mSensorManager;

    private int mShakeCount = 0;
    private boolean mLastShakePositive = false;

    public ShakeDetector(Context context, ShakeListener shakeListener) {
        mShakeListener = shakeListener;

        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorManager.registerListener(mSensorListener,
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void shutdown() {
        mSensorManager.unregisterListener(mSensorListener);
    }

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        private static final double DELETION_FORCE = 0.8;
        private static final int DELETION_COUNT = 2;

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if ((sensorEvent.values[1] > DELETION_FORCE) && !mLastShakePositive) {
                mHandler.startResetTimeout();
                mShakeCount++;
                mLastShakePositive = true;
            } else if ((sensorEvent.values[1] < -DELETION_FORCE) && mLastShakePositive) {
                mHandler.startResetTimeout();
                mShakeCount++;
                mLastShakePositive = false;
            }

            if (mShakeCount > DELETION_COUNT) {
                mShakeCount = 0;
                mShakeListener.onShakeDetected();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int arg1) {
            // Do nothing.
        }
    };

    private class ShakeHandler extends Handler {
        private final int RESET_SHAKE_COUNT = 1;
        private final int DELAY_RESET_SHAKE_COUNT = 500;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case RESET_SHAKE_COUNT:
                    mShakeCount = 0;
                    break;
            }
        }

        public void startResetTimeout() {
            sendEmptyMessageDelayed(RESET_SHAKE_COUNT, DELAY_RESET_SHAKE_COUNT);
        }
    }

    public interface ShakeListener {
        public void onShakeDetected();
    }
}
