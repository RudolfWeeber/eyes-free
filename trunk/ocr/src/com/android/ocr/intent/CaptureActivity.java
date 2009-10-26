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
package com.android.ocr.intent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;

import com.android.ocr.R;
import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;

import java.io.IOException;

/**
 * Based on code from ZXing licensed under Apache License, Version 2.0.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class CaptureActivity extends Activity implements SurfaceHolder.Callback {
  private static final String TAG = "CaptureActivity";

  private static final int DEFAULT_WIDTH = 1024;
  private static final int DEFAULT_HEIGHT = 768;

  private static final int ACTION_FOCUS = 0;
  private static final int ACTION_TAKE_PICTURE = 1;

  private enum State {
    IDLE, FOCUSING, TAKING_PICTURE, COMPLETE
  }

  private boolean mHasSurface;
  private boolean mHasFocus;

  private CameraManager mCameraManager;
  private State mState;

  private Handler mHandler = new Handler() {
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
    
    OnTouchListener onTouch = new OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        int action = KeyEvent.ACTION_UP;
        int code = KeyEvent.KEYCODE_DPAD_CENTER;
        KeyEvent kevent = new KeyEvent(action, code);
        
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN: {
            event.setAction(KeyEvent.ACTION_DOWN);
            onKeyDown(code, kevent);
            break;
          }
          case MotionEvent.ACTION_UP: {
            event.setAction(KeyEvent.ACTION_UP);
            onKeyUp(code, kevent);
            break;
          }
        }
        
        return false;
      }
    };
    
    ImageButton shutter = (ImageButton) findViewById(R.id.btn_shutter);
    shutter.setOnTouchListener(onTouch);

    mCameraManager = CameraManager.init(getApplication());
    mHasSurface = false;

    processIntent(getIntent());
  }

  private void processIntent(Intent settings) {
    int width = settings.getIntExtra(Intents.Capture.WIDTH, DEFAULT_WIDTH);
    int height = settings.getIntExtra(Intents.Capture.HEIGHT, DEFAULT_HEIGHT);

    mCameraManager.setPictureSize(width, height);
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
          if (mHasFocus) {
            mCameraManager.requestTakePicture(mHandler, ACTION_TAKE_PICTURE);
          } else {
            mCameraManager.requestAutoFocus(mHandler, ACTION_FOCUS);
          }
        }
        return true;
    }

    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_FOCUS: {
        mHasFocus = false;
        return true;
      }
    }

    return super.onKeyUp(keyCode, event);
  }

  private void initCamera(SurfaceHolder surfaceHolder) {
    try {
      mCameraManager.openDriver(surfaceHolder);
      mCameraManager.startPreview();
    } catch (IOException e) {
      Log.e(TAG, e.toString());
    }
  }

  private void onFocused() {
    mHasFocus = true;
    if (mState == State.TAKING_PICTURE) {
      mCameraManager.requestTakePicture(mHandler, ACTION_TAKE_PICTURE);
    } else {
      mState = State.IDLE;
    }
  }

  private void onPictureTaken(final byte[] data, int width, int height) {
    mState = State.IDLE;
    mHasFocus = false;

    Config config = new Config();
    config.image = data;
    config.width = width;
    config.height = height;
    config.format = Config.FORMAT_JPEG;

    Intent result = new Intent();
    result.setAction(Intents.Capture.ACTION);
    result.putExtra(Intents.Capture.CONFIG, config);
    setResult(RESULT_OK, result);
    finish();
  }
}
