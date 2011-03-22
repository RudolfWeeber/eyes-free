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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import com.googlecode.eyesfree.inputmethod.latin.tutorial.LatinTutorialLogger;

/**
 * This class receives the boot completed broadcast and opens the IME
 * automatically (assuming it is enabled and set as default).
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class BootReceiver extends BroadcastReceiver {
    private static final Class<?> TARGET_IME = LatinIME.class;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (AccessibilityUtils.isAccessibilityEnabled(context)) {
                if (AccessibilityUtils.isInputMethodDefault(context, TARGET_IME)) {
                    LatinTutorialLogger.log(Log.DEBUG, "Starting IME");

                    context.startActivity(new Intent(context, BootActivity.class).setFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK));
                } else {
                    final String defaultImeId = Settings.Secure.getString(
                            context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

                    LatinTutorialLogger.log(
                            Log.INFO, "Cannot start IME, current default IME is %s", defaultImeId);
                }
            } else {
                LatinTutorialLogger.log(Log.INFO, "Cannot start IME, accessibility is disabled");
            }
        }
    }
}
