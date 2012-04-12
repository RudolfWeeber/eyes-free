/*
 * Copyright (C) 2010 Google Inc.
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

package com.marvin.rocklock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

/**
 * Service that registers a receiver to catch the screen on intent and launches
 * the main RockLockActivity.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class ScreenOnHandlerService extends Service {
    private BroadcastReceiver screenwakeup = new BroadcastReceiver() {

        public static final String TAG = "screen wakeup";

        public static final String SCREEN = "android.intent.action.SCREEN_ON";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(SCREEN)) {
                return;
            }

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            boolean useAsPrelock = sharedPrefs.getBoolean("use_as_prelock", false);
            if (useAsPrelock) {
                Intent i = new Intent(context, RockLockActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(RockLockActivity.EXTRA_STARTED_BY_SERVICE, true);
                context.startActivity(i);
            }
        }
    };

    @Override
    public IBinder onBind(Intent arg0) {
        // Do nothing
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean useAsPrelock = sharedPrefs.getBoolean("use_as_prelock", false);
        if (useAsPrelock) {
            this.startForeground(0, null);
            IntentFilter onfilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            this.registerReceiver(screenwakeup, onfilter);
        }
    }

}
