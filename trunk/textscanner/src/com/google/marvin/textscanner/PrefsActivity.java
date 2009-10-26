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

import android.hardware.Camera;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;

import com.android.ocr.client.Language;
import com.android.ocr.client.Ocr;

/**
 * Displays preferences for Text Scanner.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class PrefsActivity extends PreferenceActivity {
  private static final String TAG = "PrefsActivity";

  protected static final String RESOLUTION = "resolution";
  protected static final String LANGUAGE = "language";
  protected static final String CLIPBOARD = "clipboard";
  protected static final String NORMALIZE = "normalize";
  protected static final String TEXTDETECT = "textdetect";
  protected static final String DEBUG = "debug";

  protected static final String DEFAULT_RESOLUTION = "1024x768";
  protected static final String DEFAULT_LANGUAGE = null;
  protected static final boolean DEFAULT_CLIPBOARD = true;
  protected static final boolean DEFAULT_NORMALIZE = false;
  protected static final boolean DEFAULT_TEXTDETECT = false;
  protected static final boolean DEFAULT_DEBUG = false;

  private Ocr mOcr;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.prefs);

    Preference temp_pref;

    temp_pref = findPreference("language");
    temp_pref.setEnabled(false);
    temp_pref = findPreference("resolution");
    temp_pref.setEnabled(false);

    Ocr.InitCallback onInit = new Ocr.InitCallback() {
      @Override
      public void onInitialized(int status) {
        if (status == Ocr.STATUS_SUCCESS) {
          loadLanguages();
        } else {
          Log.e(TAG, "OCR service initialized with status " + status);
        }
      }
    };

    mOcr = new Ocr(this, onInit);

    loadResolutions();
  }

  @Override
  protected void onDestroy() {
    if (mOcr != null) {
      mOcr.release();
    }

    super.onDestroy();
  }

  private void loadResolutions() {
    Log.i(TAG, "Loading resolutions...");

    ListPreference res_pref = (ListPreference) findPreference("resolution");

    Camera camera = Camera.open();
    Camera.Parameters params = camera.getParameters();
    camera.release();

    String current = params.get("picture-size");
    String delimited = params.get("picture-size-values");
    String[] values;

    if (delimited == null || delimited.length() == 0) {
      values = new String[1];
      values[0] = current;
    } else {
      values = delimited.split(",");
    }

    res_pref.setEntries(values);
    res_pref.setEntryValues(values);
    res_pref.setEnabled(true);

    Log.i(TAG, "Loaded resolutions!");
  }

  /**
   * Loads languages from the Ocr object into the default language preference
   * list.
   */
  private void loadLanguages() {
    ListPreference lang_pref = (ListPreference) findPreference("language");
    Language[] langs = mOcr.getLanguages();
    String[] entries;
    String[] values;

    if (langs != null) {
      entries = new String[langs.length];
      values = new String[langs.length];

      for (int i = 0; i < langs.length; i++) {
        values[i] = langs[i].iso_639_2;
        entries[i] = langs[i].english;
      }
    } else {
      entries = new String[1];
      values = new String[1];

      entries[0] = getString(R.string.default_language);
      values[0] = "";
    }

    lang_pref.setEntries(entries);
    lang_pref.setEntryValues(values);
    lang_pref.setEnabled(true);

    mOcr.release();
    mOcr = null;
  }
}
