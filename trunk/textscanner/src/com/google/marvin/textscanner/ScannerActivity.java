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
package com.google.marvin.textscanner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.text.Editable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;
import com.android.ocr.client.Ocr;
import com.android.ocr.client.Result;

import java.util.Set;

/**
 * Demonstration application for using Intents. Can load text from the camera
 * and copy it to the clipboard.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ScannerActivity extends Activity implements OnClickListener {
  private static final String TAG = "ScannerActivity";

  private static final int REQUEST_CAPTURE = 0;
  private static final int REQUEST_RECOGNIZE = 1;

  private static final int PREFS_ID = Menu.FIRST;

  private Ocr mOcr;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.i(TAG, "Creating ScannerActivity...");

    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    Window window = getWindow();
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    final Button capture = (Button) findViewById(R.id.capture);
    capture.setEnabled(false);
    
    Button clipboard = (Button) findViewById(R.id.clipboard);

    View.OnClickListener onCapture = new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        requestCapture();
      }
    };
    
    Ocr.InitCallback onInit = new Ocr.InitCallback() {
      @Override
      public void onInitialized(int status) {
        switch (status) {
          case Ocr.STATUS_SUCCESS: {
            capture.setEnabled(true);
            break;
          }
          case Ocr.STATUS_MISSING:
          case Ocr.STATUS_FAILURE: {
            finish();
            break;
          }
        }
      }
    };

    // Load OCR library in this Activity so that we don't have to
    // start & stop the service every time we open RecognizeActivity
    mOcr = new Ocr(this, onInit);

    capture.setOnClickListener(onCapture);
    clipboard.setOnClickListener(this);
  }

  @Override
  protected void onDestroy() {
    mOcr.release();
    
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    menu.add(0, PREFS_ID, 0, R.string.menu_prefs);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case PREFS_ID: {
        Intent intent = new Intent(this, PrefsActivity.class);
        startActivity(intent);
        break;
      }
    }

    return super.onOptionsItemSelected(item);
  }

  public void requestCapture() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    String resolution = prefs.getString(PrefsActivity.RESOLUTION, PrefsActivity.DEFAULT_RESOLUTION);

    Intent capture = new Intent(Intents.Capture.ACTION);

    if (resolution != null) {
      String[] values = resolution.split("x");
      int width = Integer.parseInt(values[0]);
      int height = Integer.parseInt(values[1]);
      capture.putExtra(Intents.Capture.WIDTH, width);
      capture.putExtra(Intents.Capture.HEIGHT, height);
    }

    startActivityForResult(capture, REQUEST_CAPTURE);
  }

  public void requestRecognize(Intent data) {
    Parcelable extra = data.getParcelableExtra(Intents.Capture.CONFIG);

    if (!(extra instanceof Config)) {
      Log.e(TAG, "requestRecognize received wrong parcelable type (was " + extra + ")");
      return;
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    Config config = (Config) extra;
    config.pageSegMode = Config.PSM_AUTO;
    config.language = prefs.getString(PrefsActivity.LANGUAGE, PrefsActivity.DEFAULT_LANGUAGE);
    config.debug = prefs.getBoolean(PrefsActivity.DEBUG, PrefsActivity.DEFAULT_DEBUG);

    boolean textdetect =
        prefs.getBoolean(PrefsActivity.TEXTDETECT, PrefsActivity.DEFAULT_TEXTDETECT);
    if (textdetect) {
      config.options |= Config.OPT_DETECT_TEXT;
    }

    boolean normalize = prefs.getBoolean(PrefsActivity.NORMALIZE, PrefsActivity.DEFAULT_NORMALIZE);
    if (normalize) {
      config.options |= Config.OPT_NORMALIZE_BG;
    }

    if (config.image != null) {
      Intent recognize = new Intent(Intents.Recognize.ACTION);
      recognize.putExtra(Intents.Recognize.CONFIG, config);
      startActivityForResult(recognize, REQUEST_RECOGNIZE);
    } else {
      Log.e(TAG, "requestRecognize received null image");
    }
  }

  public void handleCompleted(Intent data) {
    if (!data.hasExtra(Intents.Recognize.RESULTS)) {
      Log.e(TAG, "handleCompleted received empty intent");

      Set<String> keys = data.getExtras().keySet();
      for (String key : keys) {
        Log.e(TAG, "    Contains '" + key + "'");
      }
    }

    Parcelable[] results = data.getParcelableArrayExtra(Intents.Recognize.RESULTS);

    if (results == null) {
      Log.e(TAG, "handleCompleted received null results");
    } else if (results.length <= 0) {
      Log.e(TAG, "handleCompleted received empty results");
    } else {
      EditText textresult = (EditText) findViewById(R.id.textresult);
      textresult.setText("");

      for (int i = 0; i < results.length; i++) {
        String result = postProcess(((Result) results[i]).getString());
        if (result.length() > 0) {
          textresult.append(result.trim() + "\n");
        }
      }
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

    boolean clipboard = prefs.getBoolean(PrefsActivity.CLIPBOARD, PrefsActivity.DEFAULT_CLIPBOARD);
    if (clipboard) {
      copyToClipboard();
    }
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
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CAPTURE: {
        if (resultCode == RESULT_OK) {
          requestRecognize(data);
        } else {
          Toast.makeText(this, R.string.capture_failed, 5).show();
          Log.e(TAG, "REQUEST_CAPTURE received unexpected resultCode (" + resultCode + ")");
        }
        break;
      }
      case REQUEST_RECOGNIZE: {
        if (resultCode == RESULT_OK) {
          handleCompleted(data);
        } else if (resultCode == RESULT_CANCELED) {
          Toast.makeText(this, R.string.recognize_canceled, 3).show();
          Log.i(TAG, "REQUEST_RECOGNIZED received RESULT_CANCELED");
        } else {
          Toast.makeText(this, R.string.recognize_failed, 5).show();
          Log.e(TAG, "REQUEST_RECOGNIZE received unexpected resultCode (" + resultCode + ")");
        }
        break;
      }
      default: {
        Log.i(TAG, "Received unknown activity request code (" + requestCode + ")");
        super.onActivityResult(requestCode, resultCode, data);
      }
    }
  }

  @Override
  public void onClick(View v) {
    copyToClipboard();
  }

  private void copyToClipboard() {
    EditText textresult = (EditText) findViewById(R.id.textresult);
    Editable text = textresult.getText();

    ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    manager.setText(text.toString());

    Toast.makeText(this, R.string.clipboard, 3).show();
  }
}
