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

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;

/**
 * Activity used to set TalkBack's service preferences. This activity is loaded
 * when the TalkBackService is first connected to and allows the user to select
 * a font zoom size.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackPreferencesActivity extends PreferenceActivity {
    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener for the font zoom size that restarts the
     * TalkBack service.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        fixListSummaries(getPreferenceScreen());

        checkVibrationSupport();
        checkProximitySupport();
        checkDeveloperSupport();
    }

    /**
     * Since the "%s" summary is currently broken, this sets the preference
     * change listener for all {@link ListPreference} views to fill in the
     * summary with the current entry value.
     */
    private void fixListSummaries(PreferenceGroup group) {
        if (group == null) {
            return;
        }

        final int count = group.getPreferenceCount();

        for (int i = 0; i < count; i++) {
            final Preference preference = group.getPreference(i);

            if (preference instanceof PreferenceGroup) {
                fixListSummaries((PreferenceGroup) preference);
            } else if (preference instanceof ListPreference) {
                preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
            }
        }
    }

    /**
     * Ensure that the vibration setting does not appear on devices without a
     * vibrator.
     */
    private void checkVibrationSupport() {
        final Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
        final CheckBoxPreference prefVibration =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_vibration_key);
        final Preference prefVibrationPatterns =
                findPreferenceByResId(R.string.pref_vibration_patterns_key);

        if (vibrator == null || !vibrator.hasVibrator()) {
            prefVibration.setChecked(false);
            category.removePreference(prefVibrationPatterns);
            category.removePreference(prefVibration);
        }
    }

    /**
     * Ensure that the proximity sensor setting does not appear on devices
     * without a proximity sensor.
     */
    private void checkProximitySupport() {
        final SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor proximity = manager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
        final CheckBoxPreference prefProximity =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_proximity_key);

        if (proximity == null) {
            prefProximity.setChecked(false);
            category.removePreference(prefProximity);
        }
    }

    /**
     * Ensure that the developer settings category only appears on debug and
     * engineering builds.
     */
    private void checkDeveloperSupport() {
        final boolean isDebugBuild = Build.TYPE.contains("debug") || Build.TYPE.contains("eng");
        final Preference developer = findPreferenceByResId(R.string.pref_category_developer_key);

        if (!isDebugBuild) {
            getPreferenceScreen().removePreference(developer);
        }
    }

    /**
     * Returns the preference associated with the specified resource identifier.
     * 
     * @param resId A string resource identifier.
     * @return The preference associated with the specified resource identifier.
     */
    private Preference findPreferenceByResId(int resId) {
        return findPreference(getString(resId));
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
