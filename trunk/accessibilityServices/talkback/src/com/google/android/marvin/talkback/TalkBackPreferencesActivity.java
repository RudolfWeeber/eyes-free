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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
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
import android.telephony.TelephonyManager;

import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.os.VibratorCompatUtils;
import com.googlecode.eyesfree.utils.PackageManagerUtils;

/**
 * Activity used to set TalkBack's service preferences. This activity is loaded
 * when the TalkBackService is first connected to and allows the user to select
 * a font zoom size.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackPreferencesActivity extends PreferenceActivity {
    private final int DIALOG_EXPLORE_BY_TOUCH = 1;

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener for the font zoom size that restarts the
     * TalkBack service.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.preferences);

        fixListSummaries(getPreferenceScreen());

        assignTouchExplorationIntent();
        assignTutorialIntent();

        checkTelephonySupport();
        checkVibrationSupport();
        checkProximitySupport();
        checkDeveloperOverlaySupport();
        checkInstalledBacks();
    }

    @SuppressWarnings("deprecation")
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_EXPLORE_BY_TOUCH:
                return new AlertDialog.Builder(this).setTitle(R.string.attention)
                        .setMessage(R.string.explore_dialog_message)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.ok, mOnDialogClickListener)
                        .setNegativeButton(android.R.string.cancel, null).create();
        }

        return super.onCreateDialog(id);
    }

    /**
     * Assigns the appropriate intent to the tutorial preference.
     */
    private void assignTutorialIntent() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        final Preference prefTutorial = findPreferenceByResId(R.string.pref_tutorial_key);

        if (prefTutorial == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= AccessibilityTutorialActivity.MIN_API_LEVEL) {
            final Intent tutorialIntent = new Intent(this, AccessibilityTutorialActivity.class);
            prefTutorial.setIntent(tutorialIntent);
        } else {
            category.removePreference(prefTutorial);
        }
    }

    /**
     * Assigns the appropriate intent to the touch exploration preference.
     */
    private void assignTouchExplorationIntent() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_explore_by_touch_key);

        if (prefTouchExploration == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= AccessibilityTutorialActivity.MIN_API_LEVEL) {
            prefTouchExploration.setOnPreferenceChangeListener(mTouchExplorationChangeListener);
        } else {
            category.removePreference(prefTouchExploration);
        }
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
                // First make sure the current summary is correct, then set the
                // listener. This is necessary for summaries to show correctly
                // on SDKs < 14.
                mPreferenceChangeListener.onPreferenceChange(preference,
                        ((ListPreference) preference).getValue());

                preference.setOnPreferenceChangeListener(mPreferenceChangeListener);
            }
        }
    }

    /**
     * Ensure that telephony-related settings do not appear on devices without
     * telephony.
     */
    private void checkTelephonySupport() {
        final TelephonyManager telephony = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        final int phoneType = telephony.getPhoneType();

        if (phoneType != TelephonyManager.PHONE_TYPE_NONE) {
            return;
        }

        final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                R.string.pref_category_when_to_speak_key);
        final Preference prefCallerId = findPreferenceByResId(R.string.pref_caller_id_key);
        final Preference prefSpeakRinger = findPreferenceByResId(R.string.pref_speak_ringer_key);

        if (prefCallerId != null) {
            category.removePreference(prefCallerId);
        }

        if (prefSpeakRinger != null) {
            category.removePreference(prefSpeakRinger);
        }
    }

    /**
     * Ensure that the vibration setting does not appear on devices without a
     * vibrator.
     */
    private void checkVibrationSupport() {
        final Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        if (vibrator != null && VibratorCompatUtils.hasVibrator(vibrator)) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
        final CheckBoxPreference prefVibration =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_vibration_key);

        if (prefVibration != null) {
            prefVibration.setChecked(false);
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

        if (proximity != null) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
        final CheckBoxPreference prefProximity =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_proximity_key);

        if (prefProximity != null) {
            prefProximity.setChecked(false);
            category.removePreference(prefProximity);
        }
    }

    /**
     * Ensure that the developer overlay setting does not appear on devices
     * without touch exploration.
     */
    private void checkDeveloperOverlaySupport() {
        if (Build.VERSION.SDK_INT >= DeveloperOverlay.MIN_API_LEVEL) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_developer_key);
        final CheckBoxPreference preference =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_developer_overlay_key);

        if (preference != null) {
            preference.setChecked(false);
            category.removePreference(preference);
        }
    }

    /**
     * Ensure that sound and vibration preferences are removed if the latest
     * versions of KickBack and SoundBack are installed.
     */
    private void checkInstalledBacks() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_feedback_key);
        final CheckBoxPreference prefVibration =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_vibration_key);
        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                this, TalkBackService.KICKBACK_PACKAGE);

        if ((kickBackVersionCode >= TalkBackService.KICKBACK_REQUIRED_VERSION)
                && (prefVibration != null)) {
            category.removePreference(prefVibration);
        }

        final CheckBoxPreference prefSoundBack =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_soundback_key);
        final int soundBackVersionCode = PackageManagerUtils.getVersionCode(
                this, TalkBackService.SOUNDBACK_PACKAGE);

        if ((soundBackVersionCode >= TalkBackService.SOUNDBACK_REQUIRED_VERSION)
                && (prefSoundBack != null)) {
            category.removePreference(prefSoundBack);
        }
    }

    /**
     * Returns the preference associated with the specified resource identifier.
     *
     * @param resId A string resource identifier.
     * @return The preference associated with the specified resource identifier.
     */
    @SuppressWarnings("deprecation")
    private Preference findPreferenceByResId(int resId) {
        return findPreference(getString(resId));
    }

    private final OnPreferenceChangeListener mTouchExplorationChangeListener =
            new OnPreferenceChangeListener() {
                @SuppressWarnings("deprecation")
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                            R.string.pref_explore_by_touch_key);
                    if (Boolean.TRUE.equals(newValue)) {
                        showDialog(DIALOG_EXPLORE_BY_TOUCH);
                        return false;
                    }
                    return true;
                }
            };

    private final DialogInterface.OnClickListener
            mOnDialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                            R.string.pref_explore_by_touch_key);
                    prefTouchExploration.setChecked(true);
                }
            };

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
