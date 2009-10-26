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
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.marvin.translate.GoogleTranslate.TranslationListener;

/**
 * Displays un-translated text and runs a Google Translate call in the
 * background. Closes automatically when translation finishes.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class TranslateActivity extends Activity implements Button.OnClickListener {
  private static final String TAG = "TranslateActivity";

  public static final String EXTRA_RESULTS = "results";
  public static final String EXTRA_CODE = "code";
  public static final String EXTRA_ERROR = "error";

  public static final String EXTRA_SOURCE = "source";
  public static final String EXTRA_TARGET = "target";
  public static final String EXTRA_QUERY = "query";

  private TextView mTextView;
  private Button mCancel;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.translate);

    mTextView = (TextView) findViewById(R.id.translate);
    mCancel = (Button) findViewById(R.id.cancelTranslate);

    mCancel.setOnClickListener(this);

    processIntent(getIntent());
  }

  private void processIntent(Intent intent) {
    String query = intent.getStringExtra(EXTRA_QUERY);
    String source = intent.getStringExtra(EXTRA_SOURCE);
    String target = intent.getStringExtra(EXTRA_TARGET);

    Log.i(TAG, "Translating from " + source + " to " + target + "...");

    mTextView.append("Translating from " + source + " to " + target + "\n\n");
    mTextView.append(query);

    processResults(query, source, target);
  }

  private void processResults(String query, String source, String target) {
    TranslationListener onComplete = new TranslationListener() {
      @Override
      public void onComplete(String result) {
        TranslateActivity.this.onComplete(result);
      }

      @Override
      public void onError(int code, String error) {
        TranslateActivity.this.onError(code, error);
      }
    };

    GoogleTranslate translate = new GoogleTranslate(query, source, target, onComplete);
    translate.start();
  }

  /**
   * Since Google Translate returns HTML-encoded text, we have to convert some
   * character representations in the string back to characters. This isn't a
   * complete list, but it works for most text.
   * 
   * @param input string with html-encoded characters
   * @return string with html-encoded characters decoded
   */
  private String fixHtml(String input) {
    input = input.replaceAll("&gt;", ">");
    input = input.replaceAll("&lt;", "<");
    input = input.replaceAll("&quot;", "\"");
    input = input.replaceAll("&#39;", "'");
    input = input.replaceAll("&amp;", "&");
    return input;
  }

  public void onComplete(String result) {
    if (result == null) {
      Log.i(TAG, "Translation returned null results");
      setResult(RESULT_CANCELED);
    } else {
      result = fixHtml(result);

      Intent data = new Intent();
      data.putExtra(EXTRA_RESULTS, result);
      setResult(RESULT_OK, data);
    }

    finish();
  }

  public void onError(int code, String error) {
    Log.e(TAG, "Error " + code + ": " + error);
    setResult(RESULT_CANCELED);
    finish();
  }

  @Override
  public void onClick(View v) {
    KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);

    if (v == mCancel) {
      onKeyDown(KeyEvent.KEYCODE_BACK, event);
    }
  }
}
