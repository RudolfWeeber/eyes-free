/*
 * Copyright (C) 2009 Google Inc.
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
package com.google.marvin.worldreader;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.View.OnTouchListener;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;

import java.io.IOException;

/**
 * Based on code from ZXing licensed under Apache License, Version 2.0.
 * 
 * Modified from com.google.marvin.ocr.intent.CaptureActivity to allow
 * swipe-based setting of the text enhancement mode.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class CaptureActivity extends Activity implements SurfaceHolder.Callback, OnTouchListener {
  private static final String TAG = "CaptureActivity";

  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  private static final int MODE_DENSE = 0;
  private static final int MODE_SPARSE = 1;

  private static final int ACTION_FOCUS = 0;
  private static final int ACTION_TAKE_PICTURE = 1;

  private enum State {
    IDLE, FOCUSING, TAKING_PICTURE, COMPLETE
  }

  private boolean mHasSurface;
  private int mMode;

  private TextToSpeech mTts;
  private CameraManager mCameraManager;
  private GestureDetector mGestureDetector;
  private SimpleOnGestureListener mGestureListener;
  private State mState;

  private String[] mModes;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
        case ACTION_FOCUS: {
          // Completed focusing, now attempt to take preview or picture
          onFocused();
          break;
        }
        case ACTION_TAKE_PICTURE: {
          // Obtained picture, now perform full text recognition
          if (message.obj == null || message.obj instanceof byte[]) {
            onPictureTaken((byte[]) message.obj, message.arg1, message.arg2);
          }
          break;
        }
      }
    }
  };

  /** Called with the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "Creating CaptureActivity");

    super.onCreate(savedInstanceState);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    setContentView(R.layout.capture);

    mTts = ReaderActivity.mTts;

    Resources resources = getResources();
    mModes = resources.getStringArray(R.array.modes);

    mGestureListener = new CaptureGestureListener();
    mGestureDetector = new GestureDetector(mGestureListener);

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview);
    surfaceView.setOnTouchListener(this);

    mCameraManager = CameraManager.init(getApplication());
    mCameraManager.setPictureSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    mHasSurface = false;
  }

  @Override
  protected void onResume() {
    super.onResume();

    SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview);
    SurfaceHolder surfaceHolder = surfaceView.getHolder();

    if (mHasSurface) {
      initCamera(surfaceHolder);
    } else {
      surfaceHolder.addCallback(this);
      surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }
  }

  @Override
  protected void onPause() {
    mCameraManager.closeDriver();

    super.onPause();
  }

  @Override
  protected void onDestroy() {

    super.onDestroy();
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {
    if (!mHasSurface) {
      mHasSurface = true;
      initCamera(holder);
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    mHasSurface = false;
  }

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // Do nothing
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        if (mState != State.TAKING_PICTURE) {
          setResult(RESULT_CANCELED);
          finish();
        }
        return true;
      case KeyEvent.KEYCODE_FOCUS:
        if (event.getRepeatCount() == 0) {
          mState = State.FOCUSING;
          mCameraManager.requestAutoFocus(mHandler, ACTION_FOCUS);
        }
        return true;
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_CAMERA:
        if (event.getRepeatCount() == 0) {
          mState = State.TAKING_PICTURE;
          mCameraManager.requestAutoFocus(mHandler, ACTION_FOCUS);
        }
        return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    // TODO Is this necessary?
    switch (keyCode) {
      case KeyEvent.KEYCODE_FOCUS:
        return true;
    }

    return super.onKeyUp(keyCode, event);
  }

  private void initCamera(SurfaceHolder surfaceHolder) {
    try {
      mCameraManager.openDriver(surfaceHolder);
      mCameraManager.startPreview();

      mTts.speak("ready", TextToSpeech.QUEUE_FLUSH, null);
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    }
  }

  private void onFocused() {
    if (mState == State.TAKING_PICTURE) {
      mCameraManager.requestTakePicture(mHandler, ACTION_TAKE_PICTURE);
    } else {
      mState = State.IDLE;
    }
  }

  private void onPictureTaken(final byte[] data, int width, int height) {
    mState = State.IDLE;

    Config config = new Config();
    config.image = data;
    config.width = width;
    config.height = height;
    config.format = Config.FORMAT_JPEG;

    switch (mMode) {
      case MODE_DENSE: {
        config.options |= Config.OPT_NORMALIZE_BG;
        break;
      }
      case MODE_SPARSE: {
        config.options |= Config.OPT_DETECT_TEXT;
      }
    }

    Intent result = new Intent();
    result.setAction(Intents.Capture.ACTION);
    result.putExtra(Intents.Capture.CONFIG, config);

    setResult(RESULT_OK, result);
    finish();
  }

  @Override
  public boolean onTouch(View v, MotionEvent event) {
    mGestureDetector.onTouchEvent(event);

    return true;
  }

  private void cycleMode(int direction) {
    mMode = (mMode + direction) % mModes.length;

    if (mMode < 0) {
      mMode += mModes.length;
    }

    mTts.speak(mModes[mMode], TextToSpeech.QUEUE_FLUSH, null);
  }

  private class CaptureGestureListener extends SimpleOnGestureListener {
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
      float distX = e1.getX() - e2.getX();
      float distY = e1.getY() - e2.getY();

      if (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY
          || Math.abs(velocityY) > SWIPE_THRESHOLD_VELOCITY) {
        if (distX > SWIPE_MIN_DISTANCE || distY > SWIPE_MIN_DISTANCE) {
          cycleMode(1);
          return true;
        } else if (distX < -1 * SWIPE_MIN_DISTANCE || distY < -1 * SWIPE_MIN_DISTANCE) {
          cycleMode(-1);
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      mState = State.TAKING_PICTURE;
      mCameraManager.requestAutoFocus(mHandler, ACTION_FOCUS);
      return true;
    }
  }
}
