package com.google.marvin.paw;

import java.util.ArrayList;
import java.util.Calendar;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class WidgetService extends Service {
  private static final int TIMEOUT_LIMIT = 300;
  private static final long DOUBLE_TAP_THRESHOLD = 1500;

  private WidgetService self;

  private SensorManager accelerometer;

  private AudioManager audioManager;

  private boolean needReset = true;

  private boolean isTalking = false;

  private boolean isDizzy = false;

  String lastState = "";

  private Vibrator vibe;

  private long[] pattern = {0, 1, 40, 41};

  long lastTapTime = 0;

  private int idleTimeout = TIMEOUT_LIMIT;


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

    if (needReset) {
      self = this;

      vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      accelerometer = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

      accelerometer.registerListener(accelerometerListener, accelerometer
          .getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL,
          new Handler());

      needReset = false;

      isTalking = false;
      isDizzy = false;

      audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

      idleTimeout = TIMEOUT_LIMIT;

      Intent updateAnimIntent = new Intent("com.google.marvin.paw.idle");
      sendBroadcast(updateAnimIntent);

      (new Thread(new animationUpdater())).start();
    } else {
      Calendar cal = Calendar.getInstance();
      if ((cal.getTimeInMillis() - lastTapTime) < DOUBLE_TAP_THRESHOLD) {
        vibe.vibrate(pattern, -1);
        Intent recoIntent = new Intent("com.google.marvin.paw.doReco");
        recoIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(recoIntent);
      }
      lastTapTime = cal.getTimeInMillis();
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
      idleTimeout = TIMEOUT_LIMIT;
    } else if (audioManager.isMusicActive()) {
      currentState = "com.google.marvin.paw.dance";
      idleTimeout = TIMEOUT_LIMIT;
    } else if (isDizzy) {
      currentState = "com.google.marvin.paw.dizzy";
      idleTimeout = TIMEOUT_LIMIT;
    } else {
      currentState = "com.google.marvin.paw.idle";
      idleTimeout = idleTimeout - 1;
    }
    if (!lastState.equals(currentState)) {
      Intent updateAnimIntent = new Intent(currentState);
      sendBroadcast(updateAnimIntent);
      lastState = currentState;
    }
    if (idleTimeout < 1) {
      needReset = true;
      Intent sleepIntent = new Intent("com.google.marvin.paw.sleep");
      sendBroadcast(sleepIntent);
      this.stopSelf();
      return;
    }
    (new Thread(new animationUpdater())).start();
  }

  class animationUpdater implements Runnable {
    public void run() {
      try {
        Thread.sleep(1000);
        updateAnimationState();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }



}
