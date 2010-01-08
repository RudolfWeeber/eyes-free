
package com.google.marvin.shell;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class ProximitySensor {
    public interface ProximityChangeListener {
        public void onProximityChanged(float proximity);
    }

    private SensorManager sManager;

    private Sensor pSensor;

    private float farValue;

    private ProximityChangeListener callback;

    private boolean close;

    SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

        @Override
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

    public boolean isClose() {
        return close;
    }

    public void shutdown() {
        if (pSensor != null) {
            sManager.unregisterListener(listener);
            pSensor = null;
        }
    }

}
