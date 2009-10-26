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
package com.android.ocr.service;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

import com.android.ocr.R;
import com.android.ocr.client.Language;
import com.android.ocr.client.Ocr;
import com.android.ocr.client.VersionAlert;

/**
 * Provides access to global OCR preferences.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class PrefsActivity extends PreferenceActivity {
  private static final String TAG = "PrefsActivity";

  private static final int ACTION_INITIALIZED = 0;
  private static final int ACTION_LANGUAGES = 1;

  private Ocr mOcr;

  private final Handler mHandler = new Handler() {
    @Override
    public void handleMessage(Message message) {
      switch (message.what) {
        case ACTION_INITIALIZED: {
          onInitialized(message.arg1);
          break;
        }
      }
    }
  };

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    OnPreferenceClickListener onClick = new OnPreferenceClickListener() {
      @Override
      public boolean onPreferenceClick(Preference preference) {
        Intent intent = new Intent(PrefsActivity.this, LanguagesActivity.class);
        startActivityForResult(intent, ACTION_LANGUAGES);
        return true;
      }
    };

    addPreferencesFromResource(R.xml.prefs);

    findPreference("manage").setOnPreferenceClickListener(onClick);
    findPreference("lang_pref").setEnabled(false);

    Ocr.InitCallback onInit = new Ocr.InitCallback() {
      @Override
      public void onInitialized(int status) {
        Message msg = mHandler.obtainMessage(ACTION_INITIALIZED, status, 0);
        msg.sendToTarget();
      }
    };

    mOcr = new Ocr(this, onInit);
  }

  @Override
  protected void onDestroy() {
    if (mOcr != null) {
      mOcr.release();
    }

    super.onDestroy();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case ACTION_LANGUAGES: {
        mOcr.reloadLanguages();
        loadLanguages();
        break;
      }
    }
  }

  private void onInitialized(int status) {
    switch (status) {
      case Ocr.STATUS_SUCCESS: {
        loadLanguages();
        break;
      }
    }
  }

  /**
   * Loads languages from the Ocr object into the default language preference
   * list.
   */
  private void loadLanguages() {
    ListPreference lang_pref = (ListPreference) findPreference("lang_pref");
    lang_pref.setEnabled(false);

    Language[] langs = mOcr.getLanguages();

    if (langs != null && langs.length > 0) {
      String[] entries = new String[langs.length];
      String[] values = new String[langs.length];

      for (int i = 0; i < langs.length; i++) {
        values[i] = langs[i].iso_639_2;
        entries[i] = langs[i].english;
      }

      lang_pref.setEntries(entries);
      lang_pref.setEntryValues(values);

      lang_pref.setEnabled(true);
    } else {
      Log.e(TAG, "No languages found");

      VersionAlert.createLanguagesAlert(this, null, null, ACTION_LANGUAGES).show();
    }
  }
}
