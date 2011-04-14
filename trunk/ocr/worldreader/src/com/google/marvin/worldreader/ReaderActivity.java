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
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;

/**
 * Main activity for WorldReader. Creates a TTS object and launches
 * CaptureActivity.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ReaderActivity extends Activity {
  private static final String TAG = "ReaderActivity";

  private static final int REQUEST_CAPTURE = 0;
  private static final int REQUEST_RECOGNIZE = 1;

  private static final int ACTION_INITIALIZED = 0;

  protected static TextToSpeech mTts;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
        case ACTION_INITIALIZED: {
          requestCapture();
          break;
        }
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "Creating ReaderActivity...");

    super.onCreate(savedInstanceState);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    TextToSpeech.OnInitListener onInit = new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        Message msg = mHandler.obtainMessage(ACTION_INITIALIZED, status, 0);
        msg.sendToTarget();
      }
    };

    mTts = new TextToSpeech(this, onInit);
  }

  @Override
  protected void onDestroy() {
    if (mTts != null) {
      mTts.shutdown();
    }

    super.onDestroy();
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK: {
        requestCapture();
        return true;
      }
    }

    return super.onKeyDown(keyCode, event);
  }

  public void requestCapture() {
    Intent capture = new Intent(this, CaptureActivity.class);
    startActivityForResult(capture, REQUEST_CAPTURE);
  }

  public void requestRecognize(Intent data) {
    Parcelable extra = data.getParcelableExtra(Intents.Capture.CONFIG);

    if (!(extra instanceof Config)) {
      Log.e(TAG, "requestRecognize received wrong parcelable type (was " + extra + ")");
      return;
    }

    Config config = (Config) extra;
    config.pageSegMode = Config.PSM_AUTO;
    config.language = "eng";
    config.debug = false;

    if (config.image != null) {
      Intent recognize = new Intent(this, RecognizeActivity.class);
      recognize.putExtra(Intents.Recognize.CONFIG, config);
      startActivityForResult(recognize, REQUEST_RECOGNIZE);
    } else {
      Log.e(TAG, "requestRecognize received null image");
    }
  }

  public void handleCompleted(Intent data) {
    requestCapture();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CAPTURE: {
        if (resultCode == RESULT_OK) {
          requestRecognize(data);
        } else if (resultCode == RESULT_CANCELED) {
          // Maintain the illusion that Capture is the main activity
          finish();
        } else {
          Toast.makeText(this, R.string.capture_failed, 5).show();
          Log.e(TAG, "REQUEST_CAPTURE received unexpected resultCode (" + resultCode + ")");
          finish();
        }
        break;
      }
      case REQUEST_RECOGNIZE: {
        if (resultCode == RESULT_OK) {
          handleCompleted(data);
        } else if (resultCode == RESULT_CANCELED) {
          Toast.makeText(this, R.string.recognize_canceled, 3).show();
          Log.i(TAG, "REQUEST_RECOGNIZED received RESULT_CANCELED");
          requestCapture();
        } else {
          Toast.makeText(this, R.string.recognize_failed, 5).show();
          Log.e(TAG, "REQUEST_RECOGNIZE received unexpected resultCode (" + resultCode + ")");
          requestCapture();
        }
        break;
      }
      default: {
        Log.i(TAG, "Received unknown activity request code (" + requestCode + ")");
        super.onActivityResult(requestCode, resultCode, data);
      }
    }
  }
}
