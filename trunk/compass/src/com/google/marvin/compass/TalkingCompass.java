// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.marvin.compass;

import com.google.tts.TTS;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Config;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

/**
 * Provides spoken feedback augmented by non-speech audio and tactile feedback
 * to turn an Android handset into a wearable compass.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkingCompass extends Activity {
  private static final int MUTE_SPEECHMODE = 0;
  private static final int DEFAULT_SPEECHMODE = 1;
  private static final int VERBOSE_SPEECHMODE = 2;
  private static final int NUM_SPEECH_MODES = 3;

  private static final int NORTH = 0;
  private static final int EAST = 90;
  private static final int SOUTH = 180;
  private static final int WEST = 270;

  private static final String[] DIRECTION_NAMES =
      {"north", "north north east", "north east", "east north east", "east", "east south east",
          "south east", "south south east", "south", "south south west", "south west",
          "west south west", "west", "west north west", "north west", "north north west", "north"};

  private static final int TTS_MIN_VER = 1;
  private static final String TAG = "Compass";
  private static final String PLEASECALIBRATE =
      "Please calibrate the compass by shaking your handset";

  private static final int MIN_STABLECOUNT = 50;
  private static final int STABLECOUNT_AFTER_MODESETTING = 25;
  private static final int STABLECOUNT_FOR_VERBOSE = -100;
  private static final int STABLECOUNT_FOR_CALIBRATION = -200;

  // Degrees of tolerance for a reading to be considered stable
  private static final int STABLE_TOLERANCE = 5;
  private static final int CARDINAL_TOLERANCE = 1;
  private static final int NORTH_LEFT_MAX = 359;
  private static final int NORTH_RIGHT_MAX = 1;

  private static final long[] VIBE_PATTERN = {0, 1, 40, 41};

  private SensorManager sensorManager;
  private CompassView view;
  private float currentHeading;
  private float lastStableHeading;
  private int stableCount;
  private int speechMode;
  private int lastCardinalDir;
  private Vibrator vibe;
  private boolean sensorOk;

  private TalkingCompass self;

  private TTS tts;

  /**
   * Handles the sensor events for changes to readings and accuracy
   */
  private final SensorListener mListener = new SensorListener() {
    public void onSensorChanged(int sensor, float[] values) {
      currentHeading = values[0]; // Values are yaw (heading), pitch, and roll.
      if (view != null) {
        view.invalidate();
      }
      processDirection();
    }

    public void onAccuracyChanged(int arg0, int arg1) {
      sensorOk = (arg1 == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }
  };
  
  private TTS.InitListener ttsInitListener = new TTS.InitListener(){
    public void onInit(int version) {
      if (version < TTS_MIN_VER) {
        tts.showVersionAlert();
        return;
      }
      if (view != null) {
        view.setVisibility(View.GONE);
        view = null;
      }
      view = new CompassView(self);
      setContentView(view);
      stableCount = 0;
      currentHeading = 0;
      lastStableHeading = 0;
      speechMode = DEFAULT_SPEECHMODE;
      lastCardinalDir = 0;
      loadUtterances();
    }    
  };

  @Override
  protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    self = this;
    vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    sensorOk = true;
    sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    tts = new TTS(this, ttsInitListener);
  }



  private void loadUtterances() {
    String pkgName = TalkingCompass.class.getPackage().getName();
    tts.addSpeech("north", pkgName, R.raw.north);
    tts.addSpeech("north east", pkgName, R.raw.north_east);
    tts.addSpeech("north west", pkgName, R.raw.north_west);
    tts.addSpeech("north north east", pkgName, R.raw.north_north_east);
    tts.addSpeech("north north west", pkgName, R.raw.north_north_west);
    tts.addSpeech("east north east", pkgName, R.raw.east_north_east);
    tts.addSpeech("west north west", pkgName, R.raw.west_north_west);
    tts.addSpeech("south", pkgName, R.raw.south);
    tts.addSpeech("south east", pkgName, R.raw.south_east);
    tts.addSpeech("south west", pkgName, R.raw.south_west);
    tts.addSpeech("south south east", pkgName, R.raw.south_south_east);
    tts.addSpeech("south south west", pkgName, R.raw.south_south_west);
    tts.addSpeech("east south east", pkgName, R.raw.east_south_east);
    tts.addSpeech("west south west", pkgName, R.raw.west_south_west);
    tts.addSpeech("east", pkgName, R.raw.east);
    tts.addSpeech("west", pkgName, R.raw.west);
    tts.addSpeech("compass", pkgName, R.raw.compass);
    tts.addSpeech("muted", pkgName, R.raw.muted);
    tts.addSpeech("default", pkgName, R.raw.default_);
    tts.addSpeech("verbose", pkgName, R.raw.verbose);
    tts.addSpeech(PLEASECALIBRATE, pkgName, R.raw.please_calibrate);
    tts.speak("compass", 0, null);
  }



  @Override
  protected void onResume() {
    if (Config.LOGD) {
      Log.d(TAG, "onResume");
    }
    super.onResume();
    sensorManager.registerListener(mListener, SensorManager.SENSOR_ORIENTATION,
        SensorManager.SENSOR_DELAY_GAME);
  }

  @Override
  protected void onStop() {
    if (Config.LOGD) {
      Log.d(TAG, "onStop");
    }
    sensorManager.unregisterListener(mListener);
    super.onStop();
  }
  
  @Override
  protected void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }

  protected void processDirection() {
    // Do not speak immediately - wait until the sensor readings have been
    // stable for some time.
    if (Math.abs(lastStableHeading - currentHeading) < STABLE_TOLERANCE) {
      stableCount++;
    } else {
      lastStableHeading = currentHeading;
      stableCount = 0;
    }
    if (stableCount > MIN_STABLECOUNT) {
      speakDirection();
    }

    // Do not try bother determining if a new cardinal direction
    // was reached if the sensors are not functioning correctly.
    if (!sensorOk) {
      return;
    }
    boolean newCardinalDir = false;
    int candidateCardinal = findCardinalDir(currentHeading);

    if (candidateCardinal != lastCardinalDir) {
      newCardinalDir = true;
      lastCardinalDir = candidateCardinal;
    }

    if (newCardinalDir) {
      tts.speak("[tock]", 0, null);
      vibe.vibrate(VIBE_PATTERN, -1);
    }
  }

  private int findCardinalDir(float heading) {
    if ((heading > NORTH_LEFT_MAX) || (heading < NORTH_RIGHT_MAX)) {
      return NORTH;
    } else if ((heading > EAST - CARDINAL_TOLERANCE) && (heading < EAST + CARDINAL_TOLERANCE)) {
      return EAST;
    } else if ((heading > SOUTH - CARDINAL_TOLERANCE) && (heading < SOUTH + CARDINAL_TOLERANCE)) {
      return SOUTH;
    } else if ((heading > WEST - CARDINAL_TOLERANCE) && (heading < WEST + CARDINAL_TOLERANCE)) {
      return WEST;
    } else {
      return -1;
    }
  }

  public void setAndSpeakCurrentMode(int newSpeechMode) {
    speechMode = (newSpeechMode + NUM_SPEECH_MODES) % NUM_SPEECH_MODES;
    String text = "";
    switch (speechMode) {
      case VERBOSE_SPEECHMODE:
        stableCount = STABLECOUNT_AFTER_MODESETTING;
        text = "verbose";
        break;
      case DEFAULT_SPEECHMODE:
        stableCount = STABLECOUNT_AFTER_MODESETTING;
        text = "default";
        break;
      case MUTE_SPEECHMODE:
        text = "muted";
        break;
    }

    tts.speak(text, 0, null);
  }

  public void speakDirection() {
    stableCount = 0;
    if (!sensorOk) {
      tts.speak(PLEASECALIBRATE, 0, null);
      stableCount = STABLECOUNT_FOR_CALIBRATION;
      return;
    }
    if (speechMode == MUTE_SPEECHMODE) {
      return;
    }

    tts.speak(directionToString(currentHeading), 0, null);

    if (speechMode == VERBOSE_SPEECHMODE) {
      stableCount = STABLECOUNT_FOR_VERBOSE;
      int degrees = Math.round(currentHeading);
      tts.speak(Integer.toString(degrees), 1, null);
    }

  }

  public static String directionToString(float heading) {
    int index = (int) ((heading * 100 + 1125) / 2250);
    return DIRECTION_NAMES[index];
  }


  private class CompassView extends View {
    private Paint paint = new Paint();
    private Path path = new Path();
    private float downY;

    public CompassView(Context context) {
      super(context);

      // Construct a wedge-shaped path
      path.moveTo(0, -50);
      path.lineTo(-20, 60);
      path.lineTo(0, 50);
      path.lineTo(20, 60);
      path.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      canvas.drawColor(Color.WHITE);

      paint.setAntiAlias(true);
      paint.setColor(Color.BLACK);
      paint.setStyle(Paint.Style.FILL);

      int w = canvas.getWidth();
      int h = canvas.getHeight();
      int cx = w / 2;
      int cy = h / 2;

      canvas.translate(cx, cy);
      canvas.rotate(-currentHeading);
      canvas.drawPath(path, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        downY = event.getY();
        return true;
      } else if (event.getAction() == MotionEvent.ACTION_UP) {
        if (event.getY() + 100 < downY) {
          setAndSpeakCurrentMode(speechMode + 1);
        } else if (event.getY() - 100 > downY) {
          setAndSpeakCurrentMode(speechMode - 1);
        } else {
          speakDirection();
        }
        return true;
      }
      return false;
    }
  }

}
