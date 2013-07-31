/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;

/**
 * Enumeration of shortcut gestures supported by TalkBack.
 */
public enum ShortcutGesture {
    UNASSIGNED(R.string.shortcut_value_unassigned, R.string.shortcut_unassigned),
    BACK(R.string.shortcut_value_back, R.string.shortcut_back),
    HOME(R.string.shortcut_value_home, R.string.shortcut_home),
    RECENTS(R.string.shortcut_value_recents, R.string.shortcut_recents),
    NOTIFICATIONS(R.string.shortcut_value_notifications, R.string.shortcut_notifications),
    TALKBACK_BREAKOUT(
            R.string.shortcut_value_talkback_breakout, R.string.shortcut_talkback_breakout),
    LOCAL_BREAKOUT(R.string.shortcut_value_local_breakout, R.string.shortcut_local_breakout),
    READ_FROM_TOP(R.string.shortcut_value_read_from_top, R.string.shortcut_read_from_top),
    READ_FROM_CURRENT(
            R.string.shortcut_value_read_from_current, R.string.shortcut_read_from_current);

    /** The resource identifier associated with this gesture. */
    private final int mValueResId;

    /** The resource identifier for the label associated with this gesture. */
    private final int mLabelResId;

    private ShortcutGesture(int valueResId, int labelResId) {
        mValueResId = valueResId;
        mLabelResId = labelResId;
    }

    /**
     * Returns the preference value that represents this shortcut gesture.
     *
     * @param context The parent context.
     * @return A string preference value.
     */
    public String getPreferenceValue(Context context) {
        return context.getString(mValueResId);
    }

    /**
     * Returns the label associated with this shortcut gesture.
     *
     * @param context The parent context.
     * @return A string label.
     */
    public String getLabel(Context context) {
        return context.getString(mLabelResId);
    }

    /**
     * Returns the enum constant of the specified enum type with the specified
     * name. If the name does not exactly match an identifier used to declare an
     * enum constant in this type, returns a default value of
     * {@link #UNASSIGNED}.
     *
     * @param name The name of the constant to return.
     * @return The enum constant of the specified enum type with the specified
     *         name, or {@link #UNASSIGNED} on failure.
     */
    public static ShortcutGesture safeValueOf(String name) {
        if (name == null) {
            return ShortcutGesture.UNASSIGNED;
        }

        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            // Do nothing.
        }

        return ShortcutGesture.UNASSIGNED;
    }
}
