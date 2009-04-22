/*
 * Copyright (C) 2008 Google Inc.
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
package com.google.marvin.randroid;

import com.google.tts.TTS;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.WindowManager;

/**
 * Displays preferences
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class PrefsActivity extends PreferenceActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Have the system blur any windows behind this one.
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

    addPreferencesFromResource(R.xml.prefs);

    final Context ctx = this;

    Preference ttsSettings = findPreference("tts_settings");
    ttsSettings.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        if (TTS.isInstalled(ctx)) {
          try {
            int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
            Context myContext = createPackageContext("com.google.tts", flags);
            Class<?> appClass =
                myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
            Intent intent = new Intent(myContext, appClass);
            startActivity(intent);
          } catch (NameNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          } catch (ClassNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        } else {
          Uri marketUri = Uri.parse("market://search?q=pname:com.google.tts");
          Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
          startActivity(marketIntent);
        }
        return true;
      }
    });
  }


}
