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
package com.ideal.textenlarger;

import android.app.ActivityManagerNative;

// IMPORTANT! This app must be built against the Android framework code
// because the following two classes are NOT in the SDK.
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.preference.PreferenceManager;
import android.telephony.ServiceState;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service that enlarges the text.
 */
public class TextEnlargerService extends AccessibilityService {

    private static final String LOG_TAG = "TextEnlargerService";

    private static final int NOTIFICATION_TIMEOUT_MILLIS = 80;

    private final Configuration mCurConfig = new Configuration();

    private SharedPreferences prefs;

    private static boolean isRunning = false;

    @Override
    public void onServiceConnected() {
        if (!phoneCheckPassed()){
            this.stopSelf();
            return;
        }
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.notificationTimeout = NOTIFICATION_TIMEOUT_MILLIS;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
        isRunning = true;
    }

    public static boolean isServiceInitialized() {
        return isRunning;
    }

    @Override
    public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
        if (!phoneCheckPassed()){
            this.stopSelf();
            return;
        }
        
        if (event == null) {
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }

        Log.e(LOG_TAG, "About to enlarge font");

        try {
            if (prefs.getBoolean(event.getPackageName().toString(), true)) {

                mCurConfig.fontScale = Float.parseFloat(prefs.getString("text_enlargement_factor",
                        "1.5"));

                Log.e(LOG_TAG, "Enlarging font to:" + mCurConfig.fontScale);

                Configuration currentConfig = ActivityManagerNative.getDefault().getConfiguration();
                if (currentConfig.fontScale != mCurConfig.fontScale) {
                    ActivityManagerNative.getDefault().updateConfiguration(mCurConfig);
                }
       

                Log.e(LOG_TAG, "Font enlarged");
            } else {
                Log.e(LOG_TAG, "Skipping font enlargement for this app");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Font enlargement failed");
        }
    }

    @Override
    public void onInterrupt() {
    }
}
