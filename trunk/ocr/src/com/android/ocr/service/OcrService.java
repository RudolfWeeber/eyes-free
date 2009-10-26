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

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import com.android.ocr.R;
import com.android.ocr.client.Config;
import com.android.ocr.client.IOcr;
import com.android.ocr.client.IOcrCallback;
import com.android.ocr.client.Intents;
import com.android.ocr.client.Language;
import com.android.ocr.client.IOcr.Stub;

import java.util.Arrays;

/**
 * Provides services for recognizing text in images. Specifically handles
 * binding the service to clients.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class OcrService extends Service {
  private static final String TAG = "OcrService";

  private final OcrService mSelf = this;

  private Language[] mSupportedLang;
  private SharedPreferences mPrefs;
  private Processor mProcessor;

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Service starting...");

    mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
    mProcessor = new Processor();

    loadSupportedLanguages();

    mProcessor.start();
  }

  @Override
  public void onDestroy() {
    Log.e(TAG, "Service is shutting down...");

    mProcessor.kill();

    super.onDestroy();
  }

  /**
   * Loads supported languages from the OCR library into an array of Languages.
   * Maps each Tesseract language from ISO 639-2 format to its English name and
   * the ISO 639-1 format used by Google Translate.
   */
  private void loadSupportedLanguages() {
    String[] langs = OcrLib.getLanguages();
    mSupportedLang = new Language[langs.length];

    Resources resources = getResources();

    String[] iso_639_1 = resources.getStringArray(R.array.iso_639_1);
    String[] iso_639_2 = resources.getStringArray(R.array.iso_639_2);
    String[] english = resources.getStringArray(R.array.english);

    // Add mappings for all installed languages
    for (int i = 0; i < langs.length; i++) {
      int j = Arrays.binarySearch(iso_639_2, langs[i]);
      if (j >= 0) {
        mSupportedLang[i] = new Language(english[j], iso_639_1[j], iso_639_2[j]);
      } else {
        mSupportedLang[i] = new Language("Unknown [" + langs[i] + "]", "", langs[i]);
      }
    }

    // Sort alphabetically by English name
    Arrays.sort(mSupportedLang);
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (intent.getAction().equals(Intents.Service.ACTION)) {
      for (String category : intent.getCategories()) {
        if (category.equals(Intent.CATEGORY_DEFAULT)) {
          return mBinder;
        }
      }
    }

    return null;
  }

  /**
   * Implements Ocr preferences on top of the supplied configuration, then
   * enqueues the job in the processing queue.
   * 
   * @param jobId id assigned to this job
   * @param config configuration parameters for this job
   */
  private void enqueueJob(int jobId, Config config, IOcrCallback callback) {
    boolean override = mPrefs.getBoolean("override_pref", false);

    if (override || config.language == null) {
      config.language = mPrefs.getString("lang_pref", "eng");
    }

    if (override || !config.variables.containsKey(Config.VAR_ACCURACYVSPEED)) {
      config.variables.put(Config.VAR_ACCURACYVSPEED, mPrefs.getString("speed_pref", "0"));
    }

    if (override || !config.debug) {
      config.debug = mPrefs.getBoolean("debug_pref", false);
    }

    mProcessor.enqueueJob(jobId, config, callback);
  }

  /**
   * Cancels a job, stopping it if it is currently running or removing it from
   * the job queue if it is not.
   * 
   * @param jobId
   */
  private void cancel(int jobId) {
    mProcessor.cancelJob(jobId);
  }

  private int getProgress(int jobId) {
    return mProcessor.getProgress(jobId);
  }

  private final IOcr.Stub mBinder = new Stub() {
    @Override
    public void enqueueJob(Config config, IOcrCallback callback) {
      int jobId = getCallingPid();

      mSelf.enqueueJob(jobId, config, callback);
    }

    /**
     * Attempts to cancel the OCR processor job. Clears registered callbacks.
     */
    public void cancel() {
      int jobId = getCallingPid();

      mSelf.cancel(jobId);
    }

    /**
     * Returns the Language array of installed OCR languages.
     * 
     * @return The array of installed Languages
     */
    public Language[] getLanguages() {
      return mSupportedLang;
    }
    
    public void reloadLanguages() {
      OcrLib.reloadLanguages();
      loadSupportedLanguages();
    }

    /**
     * Returns the version number of the OCR package. This version number is the
     * versionCode from the AndroidManifest.xml
     * 
     * @return The version number of the OCR package
     */
    public int getVersion() {
      PackageManager manager = mSelf.getPackageManager();
      PackageInfo info = new PackageInfo();

      try {
        info = manager.getPackageInfo(mSelf.getPackageName(), 0);
      } catch (NameNotFoundException e) {
        Log.e(TAG, e.toString());
      }

      return info.versionCode;
    }

    public int getProgress() {
      int jobId = getCallingPid();

      return mSelf.getProgress(jobId);
    }
  };

}
