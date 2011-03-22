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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.util.Log;

/**
 * Handles logging formatted strings.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinTutorialLogger {
    private static final String TAG = "LatinTutorial";

    /**
     * The minimum log level that will be printed to the console. Set this to
     * {@link Log#ERROR} for release or {@link Log#VERBOSE} for debugging.
     */
    private static final int LOG_LEVEL = Log.ERROR;

    public static void log(int priority, String format, Object... args) {
        if (priority < LOG_LEVEL) {
            return;
        }

        Log.println(priority, TAG, String.format(format, args));
    }
}
