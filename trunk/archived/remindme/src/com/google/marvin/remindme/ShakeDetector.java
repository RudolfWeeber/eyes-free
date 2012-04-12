package com.google.marvin.remindme;

import android.content.Context;
import android.hardware.SensorListener;
import android.hardware.SensorManager;

public class ShakeDetector {

  public interface ShakeListener {
    public void onShakeDetected();
  }


  private SensorListener mListener;
  private ShakeListener cb;
  private SensorManager sensorManager;

  public ShakeDetector(Context context, ShakeListener callback) {
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
    sensorManager.unregisterListener(mListener);
  }



}
