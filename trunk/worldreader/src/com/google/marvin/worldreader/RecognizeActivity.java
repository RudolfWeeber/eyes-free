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
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;
import com.android.ocr.client.Ocr;
import com.android.ocr.client.Result;
import com.android.ocr.client.StatusMonitor;

import java.util.HashMap;

/**
 * This activity runs text recognition and displays bounding box results. If the
 * OCR service fails or is missing, this activity will return null.
 * 
 * Modified from com.google.marvin.ocr.intent.RecognizeActivity to speak results
 * out loud.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class RecognizeActivity extends Activity implements Button.OnClickListener,
    OnUtteranceCompletedListener {
  private static final String TAG = "RecognizeActivity";

  private static final int ACTION_INITIALIZED = 0;
  private static final int ACTION_RESULT = 1;
  private static final int ACTION_RECOGNIZED = 2;
  private static final int ACTION_UPDATE = 3;

  private Ocr mOcr;
  private Bitmap mBitmap;
  private ImageView mImageView;
  private RectsView mOverlayView;
  private Button mCancel;
  private Config mConfig;
  private ProgressBar mProgress;
  private StatusMonitor mStatusMonitor;
  private TextToSpeech mTts;
  private boolean mOcrBusy;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
        case ACTION_INITIALIZED: {
          if (message.arg1 == Ocr.STATUS_SUCCESS) {
            processConfig();
          } else {
            Log.e(TAG, "Ocr initialization failed");
            processResults(null);
          }
          break;
        }
        case ACTION_RESULT: {
          if (message.obj == null || message.obj instanceof Result) {
            processResult((Result) message.obj);
          }
          break;
        }
        case ACTION_RECOGNIZED: {
          mStatusMonitor.release();

          if (message.obj == null || message.obj instanceof Result[]) {
            processResults((Result[]) message.obj);
          }
          break;
        }
        case ACTION_UPDATE: {
          updateProgress((String) message.obj, message.arg1, message.arg2);
          break;
        }
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.recognize);

    mTts = ReaderActivity.mTts;
    mTts.setOnUtteranceCompletedListener(this);

    mProgress = (ProgressBar) findViewById(R.id.progress);
    mProgress.setIndeterminate(false);

    mImageView = (ImageView) findViewById(R.id.image);
    mOverlayView = (RectsView) findViewById(R.id.overlay);
    mCancel = (Button) findViewById(R.id.cancelOcr);
    mCancel.setOnClickListener(this);

    mConfig = (Config) getIntent().getParcelableExtra(Intents.Recognize.CONFIG);

    setBackground();

    Ocr.InitCallback onInit = new Ocr.InitCallback() {
      @Override
      public void onInitialized(int status) {
        Message msg = mHandler.obtainMessage(ACTION_INITIALIZED, status, 0);
        msg.sendToTarget();
      }
    };

    mOcr = new Ocr(this, onInit);
    mStatusMonitor = new StatusMonitor(mOcr, mHandler, ACTION_UPDATE, 500L);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK: {
        if (mOcrBusy) {
          mOcr.stop();
          return true;
        } else {
          mTts.stop();
        }
        break;
      }
    }

    return false;
  }

  @Override
  public void onDestroy() {
    mBitmap.recycle();
    mOcr.release();
    mTts.setOnUtteranceCompletedListener(null);
    mTts.stop();

    super.onDestroy();
  }

  @Override
  public void onClick(View v) {
    if (v == mCancel) {
      KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
      onKeyDown(KeyEvent.KEYCODE_BACK, event);
    }
  }

  private void processConfig() {
    Log.i(TAG, "Processing supplied configuration...");

    Ocr.CompletionCallback onCompleted = new Ocr.CompletionCallback() {
      @Override
      public void onCompleted(Result[] results) {
        Message msg = mHandler.obtainMessage(ACTION_RECOGNIZED, results);
        msg.sendToTarget();
      }
    };

    Ocr.ResultCallback onResult = new Ocr.ResultCallback() {
      @Override
      public void onResult(Result result) {
        Message msg = mHandler.obtainMessage(ACTION_RESULT, result);
        msg.sendToTarget();
      }
    };

    if (!mOcr.recognizeText(mConfig, onResult, onCompleted)) {
      Log.e(TAG, "Text recognition call failed");

      onCompleted.onCompleted(null);
    } else {
      mOcrBusy = true;
      mStatusMonitor.start();
    }
  }

  private void processResults(Result[] results) {
    if (results == null) {
      Log.e(TAG, "Received null results");
      setResult(RESULT_CANCELED);
    } else {
      Intent result = new Intent();
      result.setAction(Intents.Recognize.ACTION);
      result.putExtra(Intents.Recognize.RESULTS, results);

      setResult(RESULT_OK, result);
      Log.e(TAG, "Set OUT_RESULTS to array with length " + results.length);
      for (Result res : results) {
        Log.e(TAG, "    Result: " + res.getString());
      }
      Log.e(TAG, "Confirm contains " + result.getExtras().size() + " extras");
    }

    HashMap<String, String> params = new HashMap<String, String>();
    params.put("utteranceId", TAG);
    params.put("utterance_id", TAG);
    params.put("utterance-id", TAG);

    mOcrBusy = false;
    mTts.speak("end", TextToSpeech.QUEUE_ADD, params);
  }

  /**
   * Speak the text and draw the bounding box of a single result.
   * 
   * @param result
   */
  private void processResult(Result result) {
    String str = postProcess(result.getString());
    mTts.speak(str, TextToSpeech.QUEUE_ADD, null);

    mOverlayView.addRect(result.getBounds());
  }

  private void setBackground() {
    byte[] image = mConfig.image;
    WindowManager manager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    Display display = manager.getDefaultDisplay();
    int width = display.getWidth();
    int scale = Integer.highestOneBit(mConfig.width / width);

    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = Math.max(1, scale);

    mBitmap = BitmapFactory.decodeByteArray(image, 0, image.length, opts);
    mImageView.setImageBitmap(mBitmap);

    mOverlayView.setScaling(mConfig.width, mConfig.height, display.getWidth(), display.getHeight());
  }

  private void updateProgress(String status, int current, int max) {
    if (current < 0 || max <= 0) {
      return;
    }

    ProgressBar progress = (ProgressBar) findViewById(R.id.progress);
    TextView txtPercent = (TextView) findViewById(R.id.progress_percent);
    TextView txtNumber = (TextView) findViewById(R.id.progress_number);

    int intPercent = 100 * current / max;

    String strPercent = getString(R.string.percent, intPercent);
    String strNumber = getString(R.string.ratio, current, max);

    progress.setMax(max);
    progress.setProgress(current);
    txtPercent.setText(strPercent);
    txtNumber.setText(strNumber);

    progress.postInvalidate();
    txtPercent.postInvalidate();
    txtNumber.postInvalidate();
  }

  /**
   * Removes words that consist of more than 1/3 non-word characters.
   * 
   * @param text the text to process
   * @return the processed text
   */
  private String postProcess(String text) {
    String[] input = text.split(" ");
    String output = "";

    for (int i = 0; i < input.length; i++) {
      if (input[i].length() <= 0) {
        continue;
      }
      int letterCount = 0;
      for (int j = 0; j < input[i].length(); j++) {
        char chr = input[i].charAt(j);
        if (chr == '\n' || Character.isLetterOrDigit(chr)) {
          letterCount++;
        }
      }
      if (10 * letterCount / input[i].length() > 6) {
        output += input[i] + " ";
      }
    }

    return output;
  }

  @Override
  public void onUtteranceCompleted(String utteranceId) {
    if (utteranceId.equals(TAG)) {
      finish();
    }
  }
}
