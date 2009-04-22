package com.google.marvin.there;

import android.content.Context;
import android.hardware.SensorListener;
import android.hardware.SensorManager;

/**
 * Compass uses the magnetic compass to track the current heading.
 * @author clchen@google.com (Charles L. Chen)
 */

public class Compass {
  private Context ctx;

  public Compass(Context context) {
    ctx = context;
    sensorOk = true;
    sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
    sensorManager.registerListener(mListener, SensorManager.SENSOR_ORIENTATION,
        SensorManager.SENSOR_DELAY_GAME);
  }

  private static final String[] DIRECTION_NAMES =
      {"north", "north north east", "north east", "east north east", "east", "east south east",
          "south east", "south south east", "south", "south south west", "south west",
          "west south west", "west", "west north west", "north west", "north north west", "north"};

  private SensorManager sensorManager;

  private float currentHeading = -1;
  private boolean sensorOk;

  /**
   * Handles the sensor events for changes to readings and accuracy
   */
  private final SensorListener mListener = new SensorListener() {
    public void onSensorChanged(int sensor, float[] values) {
      currentHeading = values[0]; // Values are yaw (heading), pitch, and roll.
    }

    public void onAccuracyChanged(int arg0, int arg1) {
      sensorOk = (arg1 == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }
  };

  public String getCurrentHeading() {
    if (currentHeading == -1) {
      return "";
    }
    if (!sensorOk) {
      return "";
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
