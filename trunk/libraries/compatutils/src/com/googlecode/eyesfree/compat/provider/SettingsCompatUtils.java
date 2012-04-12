/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.googlecode.eyesfree.compat.provider;

import android.content.Context;
import android.provider.Settings;

public class SettingsCompatUtils {
    private SettingsCompatUtils() {
        // This class is non-instantiable.
    }

    public static class SecureCompatUtils {
        private SecureCompatUtils() {
            // This class is non-instantiable.
        }

        /**
         * Whether to speak passwords while in accessibility mode.
         */
        public static final String ACCESSIBILITY_SPEAK_PASSWORD = "speak_password";

        /**
         * Returns whether to speak passwords while in accessibility mode.
         * 
         * @param context The parent context.
         * @return {@code true} if passwords should always be spoken aloud.
         */
        public static final boolean shouldSpeakPasswords(Context context) {
            return (Settings.Secure.getInt(context.getContentResolver(),
                    ACCESSIBILITY_SPEAK_PASSWORD, 0) == 1);
        }
    }
}
