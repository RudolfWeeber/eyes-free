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
package com.google.marvin.translate;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;

import com.android.ocr.client.Config;
import com.android.ocr.client.Intents;
import com.android.ocr.client.Language;
import com.android.ocr.client.Ocr;
import com.android.ocr.client.Result;

import java.util.Arrays;

/**
 * Main activity for Translate application. This app uses the Capture and
 * Recognize intents, in addition to a new Translate intent, to provide
 * photograph-based translation.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class Translate extends Activity implements Button.OnClickListener {
  private static final String TAG = "Translate";

  private static final int PICTURE_WIDTH = 1024;
  private static final int PICTURE_HEIGHT = 768;

  private static final int REQUEST_CAPTURE = 0;
  private static final int REQUEST_RECOGNIZE = 1;
  private static final int REQUEST_TRANSLATE = 2;

  private EditText mEditText;
  private Button mOpenCapture;
  private Button mOpenTranslate;
  protected static Spinner mSource;
  private Spinner mTarget;

  protected static Ocr mOcr;
  protected static byte[] mImage;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.main);

    mEditText = (EditText) findViewById(R.id.editText);

    mSource = (Spinner) findViewById(R.id.source);
    mTarget = (Spinner) findViewById(R.id.target);

    mOpenCapture = (Button) findViewById(R.id.openCapture);
    mOpenCapture.setOnClickListener(this);

    mOpenTranslate = (Button) findViewById(R.id.openTranslate);
    mOpenTranslate.setOnClickListener(this);

    mSource.setEnabled(false);
    mTarget.setEnabled(false);
    mOpenCapture.setEnabled(false);
    mOpenTranslate.setEnabled(false);

    Ocr.InitCallback onInit = new Ocr.InitCallback() {
      @Override
      public void onInitialized(int status) {
        switch (status) {
          case Ocr.STATUS_SUCCESS: {
            loadLanguages();

            mSource.setEnabled(true);
            mTarget.setEnabled(true);
            mOpenCapture.setEnabled(true);
            mOpenTranslate.setEnabled(true);
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

    mOcr = new Ocr(this, onInit);
  }

  @Override
  public void onDestroy() {
    if (mOcr != null) {
      mOcr.release();
    }

    super.onDestroy();
  }

  /**
   * Loads available destination languages from application resources and
   * available source languages from OCR library.
   */
  private void loadLanguages() {
    Log.i(TAG, "Loading languages...");

    // Load destination languages...
    Resources resources = getResources();
    String[] iso_639_1 = resources.getStringArray(R.array.iso_639_1);
    String[] iso_639_2 = resources.getStringArray(R.array.iso_639_2);
    String[] english = resources.getStringArray(R.array.english);
    Language[] dstLangs = new Language[english.length];

    // Add mappings for all destination languages
    for (int i = 0; i < dstLangs.length; i++) {
      dstLangs[i] = new Language(english[i], iso_639_1[i], iso_639_2[i]);
    }

    // Sort alphabetically by English name
    Arrays.sort(dstLangs);

    // Load source languages from supported OCR languages...
    Language[] ocrLangs = mOcr.getLanguages();
    Language[] srcLangs = new Language[ocrLangs.length + 1];

    // Add "Automatic" as a source languages option
    System.arraycopy(ocrLangs, 0, srcLangs, 1, ocrLangs.length);
    srcLangs[0] = new Language("Automatic", "", "eng");

    // Load preferences and find IDs for default languages
    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    String srcPref = prefs.getString("source_pref", "");
    String dstPref = prefs.getString("target_pref", "en");
    int defSrc = -1;
    int defDst = -1;
    for (int i = 0; i < dstLangs.length; i++) {
      if (defDst < 0 && dstLangs[i].iso_639_1.equals(dstPref)) {
        defDst = i;
        break;
      }
    }
    for (int i = 0; i < srcLangs.length; i++) {
      if (defSrc < 0 && srcLangs[i].iso_639_1.equals(srcPref)) {
        defSrc = i;
      }
    }

    // Set up source language drop-down view
    OnItemSelectedListener srcListener = new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        setLanguagePreference("source_pref", mSource);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    };

    ArrayAdapter<Language> src =
        new ArrayAdapter<Language>(this, android.R.layout.simple_spinner_item, srcLangs);
    src.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mSource.setAdapter(src);
    mSource.setSelection(defSrc);
    mSource.setOnItemSelectedListener(srcListener);

    // Set up destination language drop-down view
    OnItemSelectedListener dstListener = new OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        setLanguagePreference("target_pref", mTarget);
      }

      @Override
      public void onNothingSelected(AdapterView<?> arg0) {
      }
    };
    ArrayAdapter<Language> dst =
        new ArrayAdapter<Language>(this, android.R.layout.simple_spinner_item, dstLangs);
    dst.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    mTarget.setAdapter(dst);
    mTarget.setSelection(defDst);
    mTarget.setOnItemSelectedListener(dstListener);

    Log.i(TAG, "Languages loaded.");
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_CAPTURE: {
        handleCaptureCompleted(resultCode, data);
        break;
      }
      case REQUEST_RECOGNIZE: {
        handleRecognizeCompleted(resultCode, data);
        break;
      }
      case REQUEST_TRANSLATE: {
        handleTranslateCompleted(resultCode, data);
        break;
      }
      default: {
        Log.i(TAG, "Unknown activity request code: " + requestCode);
      }
    }
  }

  private void setLanguagePreference(String pref, Spinner spin) {
    Language source = (Language) spin.getSelectedItem();
    SharedPreferences prefs = getPreferences(MODE_PRIVATE);
    Editor edit = prefs.edit();
    edit.putString(pref, source.iso_639_1);
    edit.commit();
  }

  private void startCaptureActivity() {
    Intent intent = new Intent(Intents.Capture.ACTION);
    intent.putExtra(Intents.Capture.WIDTH, PICTURE_WIDTH);
    intent.putExtra(Intents.Capture.HEIGHT, PICTURE_HEIGHT);

    startActivityForResult(intent, REQUEST_CAPTURE);
  }

  private void handleCaptureCompleted(int resultCode, Intent data) {
    if (resultCode == RESULT_CANCELED) {
      Toast.makeText(this, R.string.capture_failed, 5).show();
      Log.e(TAG, "REQUEST_CAPTURE received RESULT_CANCELED");
    } else if (resultCode == RESULT_OK) {
      startRecognizeActivity(data);
    } else {
      Log.i(TAG, "CaptureActivity returned unknown result code: " + resultCode);
    }
  }

  private void startRecognizeActivity(Intent data) {
    Language source = (Language) mSource.getSelectedItem();

    // Pull data from CaptureActivity's intent and add options
    Parcelable parcel = data.getParcelableExtra(Intents.Capture.CONFIG);
    Config config = (Config) parcel;
    config.language = source.iso_639_2;
    config.options |= Config.OPT_NORMALIZE_BG;

    // Load data into intent for RecognizeActivity
    Intent intent = new Intent(Intents.Recognize.ACTION);
    intent.putExtra(Intents.Recognize.CONFIG, config);

    startActivityForResult(intent, REQUEST_RECOGNIZE);
  }

  private void handleRecognizeCompleted(int resultCode, Intent data) {
    if (resultCode == RESULT_CANCELED) {
      Toast.makeText(this, R.string.recognize_canceled, 3).show();
      Log.i(TAG, "REQUEST_RECOGNIZED received RESULT_CANCELED");
    } else if (resultCode == RESULT_OK) {
      Parcelable[] results = data.getParcelableArrayExtra(Intents.Recognize.RESULTS);
      String query = "";

      if (results != null) {
        for (Parcelable result : results) {
          query += ((Result) result).getString();
        }
      }

      mEditText.setText(query);
    } else {
      Log.i(TAG, "OcrActivity returned unknown result code: " + resultCode);
    }
  }

  private void startTranslateActivity() {
    Language source = (Language) mSource.getSelectedItem();
    Language target = (Language) mTarget.getSelectedItem();
    Editable text = mEditText.getText();

    // Load data into intent for TranslateActivity
    Intent intent = new Intent(this, TranslateActivity.class);
    intent.putExtra(TranslateActivity.EXTRA_SOURCE, source.iso_639_1);
    intent.putExtra(TranslateActivity.EXTRA_TARGET, target.iso_639_1);
    intent.putExtra(TranslateActivity.EXTRA_QUERY, text.toString());

    startActivityForResult(intent, REQUEST_TRANSLATE);
  }

  private void handleTranslateCompleted(int resultCode, Intent data) {
    if (resultCode == RESULT_CANCELED) {
      Toast.makeText(this, R.string.translate_failed, 5).show();
      Log.e(TAG, "REQUEST_TRANSLATE received RESULT_CANCELED");
    } else if (resultCode == RESULT_OK) {
      String result = data.getStringExtra(TranslateActivity.EXTRA_RESULTS);
      mEditText.setText(result);
    } else {
      Log.i(TAG, "TranslateActivity returned unknown result code: " + resultCode);
    }
  }

  @Override
  public void onClick(View v) {
    if (v == mOpenCapture) {
      startCaptureActivity();
    } else if (v == mOpenTranslate) {
      startTranslateActivity();
    }
  }
}
