/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.marvin.talkback;

/**
 * Enumeration of automatic resume preference values. Must be synchronized with
 * the values in {@code @array/pref_resume_talkback_values}.
 */
public enum AutomaticResumePreference {
    /** Resume when the screen turns on. */
    SCREEN_ON,
    /** Resume when the keyguard is shown. */
    KEYGUARD,
    /** Resume only when the user explicitly resumes. */
    MANUAL;

    /** The default value to return from {@link #safeValueOf}. */
    private static final AutomaticResumePreference DEFAULT = AutomaticResumePreference.SCREEN_ON;

    /**
     * Returns the enum constant of the specified enum type with the specified
     * name. If the name does not exactly match an identifier used to declare an
     * enum constant in this type, returns a default value of
     * {@link #KEYGUARD}.
     *
     * @param name The name of the constant to return.
     * @return The enum constant of the specified enum type with the specified
     *         name, or {@link #KEYGUARD} on failure.
     */
    public static AutomaticResumePreference safeValueOf(String name) {
        if (name == null) {
            return DEFAULT;
        }

        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            // Do nothing.
        }

        return DEFAULT;
    }
}