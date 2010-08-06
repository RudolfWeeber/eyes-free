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

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import java.util.List;

/**
 * Launches the Eyes-Free Shell if the user has Talkback enabled; otherwise, it
 * launches the default Home app.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class HomeLauncher extends Activity {
    private final static String SCREENREADER_INTENT_ACTION = "android.accessibilityservice.AccessibilityService";

    private final static String SCREENREADER_INTENT_CATEGORY = "android.accessibilityservice.category.FEEDBACK_SPOKEN";

    private Intent homeIntent;

    private HomeLauncher self;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;

        homeIntent = getSystemHomeIntent(this);
        if (isScreenReaderActive()) {
            homeIntent = new Intent(self, MarvinShell.class);
        }
        startActivity(homeIntent);
        finish();
    }
    
    public static Intent getSystemHomeIntent(Context ctx){
        Intent homeIntent = new Intent("android.intent.action.MAIN");
        homeIntent.addCategory("android.intent.category.HOME");

        ResolveInfo[] homeAppsArray = new ResolveInfo[0];
        PackageManager pm = ctx.getPackageManager();
        homeAppsArray = pm.queryIntentActivities(homeIntent, 0).toArray(homeAppsArray);

        for (int i = 0; i < homeAppsArray.length; i++) {
            ActivityInfo aInfo = homeAppsArray[i].activityInfo;
            if (!aInfo.packageName.equals("com.google.marvin.shell")
                    && !aInfo.packageName.equals("com.google.marvin.config")) {
                homeIntent.setClassName(aInfo.packageName, aInfo.name);
                break;
            }
        }
        return homeIntent;
    }

    private boolean isScreenReaderActive() {
        // Restrict the set of intents to only accessibility services that have
        // the category FEEDBACK_SPOKEN (aka, screen readers).
        Intent screenReaderIntent = new Intent(SCREENREADER_INTENT_ACTION);
        screenReaderIntent.addCategory(SCREENREADER_INTENT_CATEGORY);
        List<ResolveInfo> screenReaders = getPackageManager().queryIntentServices(
                screenReaderIntent, 0);
        ContentResolver cr = getContentResolver();
        Cursor cursor = null;
        int status = 0;
        for (ResolveInfo screenReader : screenReaders) {
            // All screen readers are expected to implement a content provider
            // that responds to
            // content://<nameofpackage>.providers.StatusProvider
            cursor = cr.query(Uri.parse("content://" + screenReader.serviceInfo.packageName
                    + ".providers.StatusProvider"), null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                // These content providers use a special cursor that only has one element, 
                // an integer that is 1 if the screen reader is running.
                status = cursor.getInt(0);
                cursor.close();
                if (status == 1) {
                    return true;
                }
            }
        }
        return false;
    }
}
