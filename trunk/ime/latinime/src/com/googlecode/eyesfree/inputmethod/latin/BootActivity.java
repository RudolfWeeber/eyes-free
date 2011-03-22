/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod.latin;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.googlecode.eyesfree.inputmethod.latin.tutorial.LatinTutorialLogger;

/**
 * This activity waits for the IME to load, then finishes.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class BootActivity extends Activity {
    private boolean mFinishing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.default_ime_wizard);

        registerReceiver(receiver, new IntentFilter(LatinIME.BROADCAST_IME_LOADED));
        sendBroadcast(new Intent(LatinIME.REQUEST_LOADED_STATUS));

        LatinTutorialLogger.log(Log.DEBUG, "Attempting to load IME");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            sendBroadcast(new Intent(LatinIME.REQUEST_LOADED_STATUS));
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // We need to wait until we have window focus, otherwise the
            // Honeycomb window manager won't let us attach the input method.
            if (!mFinishing && hasWindowFocus()) {
                mFinishing = true;
                LatinTutorialLogger.log(Log.DEBUG, "Started IME");

                sendBroadcast(new Intent(LatinIME.REQUEST_KEYBOARD_MODE_CHANGE).putExtra(
                        LatinIME.EXTRA_MODE, LatinIME.FORCE_DPAD));

                LatinTutorialLogger.log(
                        Log.DEBUG, "IME loaded, switching to MODE_DPAD and finishing");

                finish();
            }
        }
    };
}
