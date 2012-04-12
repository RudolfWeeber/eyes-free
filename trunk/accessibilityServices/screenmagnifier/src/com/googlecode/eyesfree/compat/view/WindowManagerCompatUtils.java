/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.compat.view;

public class WindowManagerCompatUtils {
    private WindowManagerCompatUtils() {
        // This class is non-instantiable.
    }

    public static class LayoutParamsCompatUtils {
        /**
         * Window flag: when set the window will accept for touch events outside
         * of its bounds to be sent to other windows that also support split
         * touch. When this flag is not set, the first pointer that goes down
         * determines the window to which all subsequent touches go until all
         * pointers go up. When this flag is set, each pointer (not necessarily
         * the first) that goes down determines the window to which all
         * subsequent touches of that pointer will go until that pointer goes up
         * thereby enabling touches with multiple pointers to be split across
         * multiple windows.
         */
        public static final int FLAG_SPLIT_TOUCH = 0x00800000;

        /**
         * <p>
         * Indicates whether this window should be hardware accelerated.
         * Requesting hardware acceleration does not guarantee it will happen.
         * </p>
         * <p>
         * This flag can be controlled programmatically <em>only</em> to enable
         * hardware acceleration. To enable hardware acceleration for a given
         * window programmatically, do the following:
         * </p>
         *
         * <pre>
         * Window w = activity.getWindow(); // in Activity's onCreate() for instance
         * w.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
         *         WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
         * </pre>
         * <p>
         * It is important to remember that this flag <strong>must</strong> be
         * set before setting the content view of your activity or dialog.
         * </p>
         * <p>
         * This flag cannot be used to disable hardware acceleration after it
         * was enabled in your manifest using
         * android.R.attr.hardwareAccelerated. If you need to selectively and
         * programmatically disable hardware acceleration (for automated testing
         * for instance), make sure it is turned off in your manifest and enable
         * it on your activity or dialog when you need it instead, using the
         * method described above.
         * </p>
         * <p>
         * This flag is automatically set by the system if the
         * android:hardwareAccelerated XML attribute is set to true on an
         * activity or on the application.
         * </p>
         */
        public static final int FLAG_HARDWARE_ACCELERATED = 0x01000000;

        private LayoutParamsCompatUtils() {
            // This class is non-instantiable.
        }
    }
}
