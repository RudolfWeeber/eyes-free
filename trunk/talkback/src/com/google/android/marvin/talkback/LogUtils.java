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

package com.google.android.marvin.talkback;

import android.util.Log;

import java.util.IllegalFormatException;

/**
 * Handles logging formatted strings.
 */
public class LogUtils {
    public static String TAG = "TalkBack";

    /**
     * The minimum log level that will be printed to the console. Set this to
     * {@link Log#ERROR} for release or {@link Log#VERBOSE} for debugging.
     */
    private static int sLogLevel = Log.ERROR;

    /**
     * Set the minimum log level that will be printed to the console. Set this
     * to {@link Log#ERROR} for release or {@link Log#VERBOSE} for debugging.
     * 
     * @param level
     */
    public static void setLogLevel(int level) {
        sLogLevel = level;
    }

    /**
     * Logs a formatted string to the console using the source object's name as
     * the log tag. If the source object is null, the default tag (see
     * {@link LogUtils#TAG} is used.
     * <p>
     * Example usage:
     * 
     * <pre class="prettyprint">
     * LogUtils.log(this.getClass(), Log.ERROR, &quot;Invalid value: %d&quot;, value);
     * </pre>
     * 
     * @param source The object that generated the log event.
     * @param priority The log entry priority, see
     *            {@link Log#println(int, String, String)}.
     * @param format A format string, see
     *            {@link String#format(String, Object...)}.
     * @param args String formatter arguments.
     */
    public static void log(Class<?> source, int priority, String format, Object... args) {
        if (priority < sLogLevel) {
            return;
        }

        final String sourceClass = source == null ? TAG : source.getSimpleName();

        try {
            Log.println(priority, sourceClass, String.format(format, args));
        } catch (IllegalFormatException e) {
            Log.e(TAG, "Bad formatting string: \"format\"", e);
        }
    }

    /**
     * Logs a formatted string to the console using the default tag (see
     * {@link LogUtils#TAG}.
     * 
     * @param priority The log entry priority, see
     *            {@link Log#println(int, String, String)}.
     * @param format A format string, see
     *            {@link String#format(String, Object...)}.
     * @param args String formatter arguments.
     */
    public static void log(int priority, String format, Object... args) {
        log(null, priority, format, args);
    }
}
