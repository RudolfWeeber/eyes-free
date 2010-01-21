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
package com.google.marvin.config;

import com.google.tts.ConfigurationManager;
import com.google.tts.TextToSpeechBeta;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * Alternate home screen that dispatches to the actual shell home screen
 * replacement.
 * 
 * @author sdoyon@google.com (Stephane Doyon)
 * @author clchen@google.com (Charles L. Chen)
 */

public class MarvinHomeScreen extends Activity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    
    String packageName = "com.android.launcher";
    String className = "com.android.launcher.Launcher";
    
    Intent intent = new Intent("android.intent.action.MAIN");
    intent.addCategory("android.intent.category.HOME");

    ResolveInfo[] homeAppsArray = new ResolveInfo[0];
    PackageManager pm = getPackageManager();
    homeAppsArray = pm.queryIntentActivities(intent, 0).toArray(homeAppsArray);

    for (int i = 0; i < homeAppsArray.length; i++) {
        ActivityInfo aInfo = homeAppsArray[i].activityInfo;
        if (!aInfo.packageName.equals("com.google.marvin.config")){
            packageName = aInfo.packageName;
            className = aInfo.name;
            break;
        }
    }
    
    
    
    if (prefs.getBoolean("use_shell", false)
        && Utils.applicationInstalled(this, "com.google.marvin.shell")
        && ttsChecksAllPassed() ) {
      packageName = "com.google.marvin.shell";
      className = "com.google.marvin.shell.MarvinShell";
    }
    Intent homeIntent = Utils.getAppStartIntent(this, packageName, className);
        
    startActivity(homeIntent);
    finish();
  }
  
  
  private boolean ttsChecksAllPassed(){
    //return TTS.isInstalled(this) && ConfigurationManager.allFilesExist();
    return ConfigurationManager.allFilesExist();
  }
}
