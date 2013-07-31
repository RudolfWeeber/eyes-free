package com.google.android.marvin.talkback;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

/**
 * Detector for shake events used to trigger continuous reading.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class ShakeDetector implements SensorEventListener {

    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    private static final float MOVEMENT_WINDOW = 200;

    private final TalkBackService mContext;

    private final SharedPreferences mPrefs;

    private final FullScreenReadController mFullScreenReadController;

    private final SensorManager mSensorManager;

    private final Sensor mAccelerometer;

    private boolean mIsFeatureEnabled;

    private boolean mIsActive;

    private long mLastSensorUpdate;

    private float[] mLastEventValues;

    public ShakeDetector(TalkBackService context) {
        mContext = context;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mFullScreenReadController = context.getFullScreenReadController();
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * Sets whether or not to enable the shake detection feature.
     *
     * @param enable {@code true} to enable, {@code false} otherwise
     */
    public void setEnabled(boolean enable) {
        mIsFeatureEnabled = enable;
        if (enable) {
            final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            if (pm.isScreenOn()) {
                resumePolling();
            }
        } else {
            pausePolling();
        }
    }

    /**
     * Starts polling the accelerometer for shake detection. If the feature has
     * not be enabled by calling {@link #setEnabled(boolean)}, calling this
     * method is a no-op.
     */
    public void resumePolling() {
        if (!mIsFeatureEnabled || mIsActive) {
            return;
        }

        mLastSensorUpdate = 0;
        mLastEventValues = new float[3];
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mIsActive = true;
    }

    /**
     * Stops polling the accelerometer for shake detection.
     */
    public void pausePolling() {
        if (!mIsActive) {
            return;
        }

        mSensorManager.unregisterListener(this);
        mIsActive = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final long time = System.currentTimeMillis();
        final long deltaT = (time - mLastSensorUpdate);

        if (deltaT > MOVEMENT_WINDOW) {
            final float movement = Math.abs(event.values[0] + event.values[1] + event.values[2]
                    - mLastEventValues[0] - mLastEventValues[1] - mLastEventValues[2]);
            final float speed = (movement / deltaT) * 10000;
            mLastSensorUpdate = time;
            mLastEventValues = event.values.clone();

            final int threshold = SharedPreferencesUtils.getIntFromStringPref(mPrefs,
                    mContext.getResources(), R.string.pref_shake_to_read_threshold_key,
                    R.string.pref_shake_to_read_threshold_default);
            if ((threshold > 0) && (speed >= threshold)) {
                mFullScreenReadController.startReading(false);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing.
    }
}
