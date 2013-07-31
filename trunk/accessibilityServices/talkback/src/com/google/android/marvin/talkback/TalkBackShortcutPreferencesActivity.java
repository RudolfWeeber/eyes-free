/*
 * Copyright 2010 Google Inc.
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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.Preference.OnPreferenceChangeListener;

import java.util.ArrayList;

/**
 * Activity used to set TalkBack's gesture preferences.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class TalkBackShortcutPreferencesActivity extends PreferenceActivity {

    /**
     * References to the string resources used as keys customizable gesture
     * mapping preferences
     */
    private static final int[] GESTURE_PREF_KEY_IDS = {
            R.string.pref_shortcut_down_and_left_key,
            R.string.pref_shortcut_down_and_right_key,
            R.string.pref_shortcut_left_and_down_key,
            R.string.pref_shortcut_left_and_up_key,
            R.string.pref_shortcut_right_and_down_key,
            R.string.pref_shortcut_right_and_up_key,
            R.string.pref_shortcut_up_and_left_key,
            R.string.pref_shortcut_up_and_right_key
    };

    /**
     * References to the string resources used as action values for the
     * available customizable gesture mapping preferences
     */
    private static final int[] GESTURE_PREF_VALUE_IDS = {
            R.string.shortcut_value_unassigned,
            R.string.shortcut_value_back,
            R.string.shortcut_value_home,
            R.string.shortcut_value_recents,
            R.string.shortcut_value_notifications,
            R.string.shortcut_value_talkback_breakout,
            R.string.shortcut_value_local_breakout,
            R.string.shortcut_value_read_from_top,
            R.string.shortcut_value_read_from_current
    };

    /** List of keys used for customizable gesture mapping preferences. */
    private final ArrayList<String> mGesturePrefKeys =
            new ArrayList<String>(GESTURE_PREF_KEY_IDS.length);

    /** List of values used for customizable gesture mapping preferences. */
    private final ArrayList<String> mGesturePrefValues =
            new ArrayList<String>(GESTURE_PREF_VALUE_IDS.length);

    private SharedPreferences mPrefs;

    /**
     * Loads the preferences from the XML preference definition for gestures.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Populate lists of available mapping keys and values
        for (int id : GESTURE_PREF_KEY_IDS) {
            mGesturePrefKeys.add(getString(id));
        }

        for (int id : GESTURE_PREF_VALUE_IDS) {
            mGesturePrefValues.add(getString(id));
        }

        addPreferencesFromResource(R.xml.gesture_preferences);
        fixListSummaries(getPreferenceScreen());
    }

    /**
     * Since the "%s" summary is currently broken, this sets the preference
     * change listener for all {@link ListPreference} views to fill in the
     * summary with the current entry value.
     */
    private void fixListSummaries(PreferenceGroup root) {
        if (root == null) {
            return;
        }

        final int count = root.getPreferenceCount();

        for (int i = 0; i < count; i++) {
            final Preference preference = root.getPreference(i);
            if (preference instanceof ListPreference) {
                fixUnboundPrefSummary(preference);
                preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
            } else if (preference instanceof PreferenceGroup) {
                fixListSummaries((PreferenceGroup) preference);
            }
        }
    }

    /**
     * After an update that removes an entry value from a {@link ListPreference}
     * is applied, we need to ensure that we don't display incorrect values in
     * the UI if TalkBack hasn't been enabled to remap the preference value.
     */
    private void fixUnboundPrefSummary(Preference pref) {
        final String prefKey = pref.getKey();
        final String prefValue = mPrefs.getString(prefKey, "");
        if (mGesturePrefKeys.contains(prefKey)) {
            // If we're ensuring consistency of a gesture customization
            // preference, ensure its mapped action exists within the set
            // of available values. If not, clear the preference summary until
            // the user chooses a new mapping, or TalkBackUpdateHelper gets a
            // chance to remap the actual preference.
            if (!mGesturePrefValues.contains(prefValue)) {
                pref.setSummary("");
            }
        }
    }

    /**
     * Listens for preference changes and updates the summary to reflect the
     * current setting. This shouldn't be necessary, since preferences are
     * supposed to automatically do this when the summary is set to "%s".
     */
    private final OnPreferenceChangeListener mPreferenceChangeListener =
            new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if (preference instanceof ListPreference && newValue instanceof String) {
                        final ListPreference listPreference = (ListPreference) preference;
                        final int index = listPreference.findIndexOfValue((String) newValue);
                        final CharSequence[] entries = listPreference.getEntries();

                        if (index >= 0 && index < entries.length) {
                            preference.setSummary(entries[index].toString().replaceAll("%", "%%"));
                        } else {
                            preference.setSummary("");
                        }
                    }

                    return true;
                }
            };
}
