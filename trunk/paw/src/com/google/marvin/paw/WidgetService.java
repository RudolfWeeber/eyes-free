
package com.google.marvin.paw;

import java.util.ArrayList;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class WidgetService extends Service {

    private WidgetService self;

    private SensorManager accelerometer;

    private AudioManager audioManager;

    private boolean needReset = true;

    private boolean isTalking = false;

    private boolean isDizzy = false;
    
    String lastState = "";

    private Vibrator vibe;

    private long[] pattern = {
            0, 1, 40, 41
    };

    private SensorEventListener accelerometerListener = new SensorEventListener() {
        private final double deletionForce = 1;

        private final int deletionCount = 2;

        int shakeCount = 0;

        boolean lastShakePositive = false;

        private int shakeCountTimeout = 1500;

        boolean isResetting = false;

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub
        }

        public void onSensorChanged(SensorEvent event) {
            if ((event.values[0] > deletionForce) && !lastShakePositive) {
                if (!isResetting) {
                    isResetting = true;
                    (new Thread(new resetShakeCount())).start();
                }
                shakeCount++;
                lastShakePositive = true;
            } else if ((event.values[0] < -deletionForce) && lastShakePositive) {
                if (!isResetting) {
                    isResetting = true;
                    (new Thread(new resetShakeCount())).start();
                }
                shakeCount++;
                lastShakePositive = false;
            }
            if (shakeCount > deletionCount) {
                shakeCount = 0;
                isResetting = false;
                enterDizzyState();
            }
        }

        class resetShakeCount implements Runnable {
            public void run() {
                try {
                    Thread.sleep(shakeCountTimeout);
                    shakeCount = 0;
                    isResetting = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    };

    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        this.setForeground(true);

        Toast.makeText(this, "Widget Service started!", 1).show();

        if (needReset) {
            self = this;

            vibe = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
            accelerometer = (SensorManager)getSystemService(Context.SENSOR_SERVICE);

            accelerometer.registerListener(accelerometerListener, accelerometer
                    .getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL, new Handler());

            needReset = false;

            isTalking = false;
            isDizzy = false;

            audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

            
            (new Thread(new animationUpdater())).start();
        } else {

        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Not binding to the service
        return null;
    }

    public void enterDizzyState() {
        if (!isDizzy) {
            (new Thread(new undizzy())).start();
        }
        isDizzy = true;
    }

    class undizzy implements Runnable {
        public void run() {
            try {
                Thread.sleep(6000);
                isDizzy = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void updateAnimationState() {
        String currentState = "";
        if (isTalking) {
            Log.e("paw", "I'm talking!");
        } else if (audioManager.isMusicActive()) {
            currentState = "com.google.marvin.paw.dance";
        } else if (isDizzy) {
            currentState = "com.google.marvin.paw.dizzy";
        } else {
            currentState = "com.google.marvin.paw.idle";
        }
        if (!lastState.equals(currentState)){
            Intent updateAnimIntent = new Intent(currentState);
            sendBroadcast(updateAnimIntent);
            lastState = currentState;
        }
    }

    class animationUpdater implements Runnable {
        public void run() {
            try {
                Thread.sleep(1000);
                updateAnimationState();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            (new Thread(new animationUpdater())).start();
        }
    }
}
