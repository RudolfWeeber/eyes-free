/*
 * Copyright (C) 2010 The IDEAL Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ideal.webreader;

import com.ideal.webaccess.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

import java.io.File;

/**
 * Preferences activity for adjusting the various settings in IDEAL Web Reader.
 */
public class PrefsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        Preference ttsPref = findPreference("tts_settings");
        ttsPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent();
                i.setClassName("com.android.settings", "com.android.settings.TextToSpeechSettings");
                startActivity(i);
                return false;
            }
        });

        Preference defineGesturesPref = findPreference("define_gestures");
        defineGesturesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent();
                i.setClassName("com.ideal.webaccess",
                        "com.ideal.webreader.CreateGestureWizardActivity");
                startActivity(i);
                return false;
            }
        });

        Preference resetGesturesPref = findPreference("reset_gestures");
        resetGesturesPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                File storeFile = new File(Environment.getExternalStorageDirectory()
                        + "/ideal-webaccess/gestures");
                if (storeFile.exists()) {
                    storeFile.delete();
                }
                finish();
                return false;
            }
        });
    }
}
