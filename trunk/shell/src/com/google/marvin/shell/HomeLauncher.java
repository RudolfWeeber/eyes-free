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

package com.google.marvin.shell;

import com.google.tts.ConfigurationManager;
import com.google.tts.TextToSpeechBeta;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Launches the Eyes-Free Shell if the user has Talkback enabled; otherwise, it
 * launches the default Home app.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class HomeLauncher extends Activity {
    private static final String QUERY_ACTION = "com.google.android.marvin.talkback.ACTION_QUERY_TALKBACK_ENABLED_COMMAND";

    private BroadcastReceiver mResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getResultCode() == 1) {
                homeIntent = new Intent(self, MarvinShell.class);
            }
            startActivity(homeIntent);
            finish();
        }
    };

    private Intent homeIntent;

    private HomeLauncher self;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;

        homeIntent = new Intent("android.intent.action.MAIN");
        homeIntent.addCategory("android.intent.category.HOME");

        ResolveInfo[] homeAppsArray = new ResolveInfo[0];
        PackageManager pm = getPackageManager();
        homeAppsArray = pm.queryIntentActivities(homeIntent, 0).toArray(homeAppsArray);

        for (int i = 0; i < homeAppsArray.length; i++) {
            ActivityInfo aInfo = homeAppsArray[i].activityInfo;
            if (!aInfo.packageName.equals("com.google.marvin.shell")
                    && !aInfo.packageName.equals("com.google.marvin.config")) {
                homeIntent.setClassName(aInfo.packageName, aInfo.name);
                break;
            }
        }

        if (talkbackInstalled()) {
            Intent queryIntent = new Intent(QUERY_ACTION);
            sendOrderedBroadcast(queryIntent, null, mResultReceiver, null, 0, null, null);
        } else {
            startActivity(homeIntent);
            finish();
        }
    }

    private boolean talkbackInstalled() {
        try {
            Context ctx = createPackageContext("com.google.android.marvin.talkback", 0);
        } catch (NameNotFoundException e) {
            return false;
        }
        return true;
    }
}
