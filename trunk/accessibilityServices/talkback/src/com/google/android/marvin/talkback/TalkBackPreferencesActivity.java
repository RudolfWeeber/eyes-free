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

package com.google.android.marvin.mytalkback;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import com.google.android.marvin.mytalkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.os.VibratorCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.PackageManagerUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

/**
 * Activity used to set TalkBack's service preferences.
 *
 * @author alanv@google.com (Alan Viverette)
 */
@SuppressWarnings("deprecation")
public class TalkBackPreferencesActivity extends PreferenceActivity {
    /** Preferences managed by this activity. */
    private SharedPreferences mPrefs;

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        addPreferencesFromResource(R.xml.preferences);

        fixListSummaries(getPreferenceScreen());

        assignTutorialIntent();

        checkTouchExplorationSupport();
        checkTelephonySupport();
        checkVibrationSupport();
        checkProximitySupport();
        checkAccelerometerSupport();
        checkInstalledBacks();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (TalkBackService.SUPPORTS_TOUCH_PREF) {
            registerTouchSettingObserver();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (TalkBackService.SUPPORTS_TOUCH_PREF) {
            getContentResolver().unregisterContentObserver(mTouchExploreObserver);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void registerTouchSettingObserver() {
        final Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
        getContentResolver().registerContentObserver(uri, false, mTouchExploreObserver);
    }

    /**
     * Assigns the appropriate intent to the tutorial preference.
     */
    private void assignTutorialIntent() {
        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_miscellaneous_key);
        final Preference prefTutorial = findPreferenceByResId(R.string.pref_tutorial_key);

        if ((category == null) || (prefTutorial == null)) {
            return;
        }

        final int touchscreenState = getResources().getConfiguration().touchscreen;
        if (Build.VERSION.SDK_INT < AccessibilityTutorialActivity.MIN_API_LEVEL
                || (touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH)) {
            category.removePreference(prefTutorial);
            return;
        }

        final Intent tutorialIntent = new Intent(this, AccessibilityTutorialActivity.class);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tutorialIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        prefTutorial.setIntent(tutorialIntent);
    }

    /**
     * Assigns the appropriate intent to the touch exploration preference.
     */
    private void checkTouchExplorationSupport() {
        final PreferenceGroup category = (PreferenceGroup) findPreferenceByResId(
                R.string.pref_category_touch_exploration_key);
        if (category == null) {
            return;
        }

        // Touch exploration is managed by the system before JellyBean.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            getPreferenceScreen().removePreference(category);
            return;
        }

        checkTouchExplorationSupportInner(category);
    }

    /**
     * Touch exploration preference management code specific to devices running
     * Jelly Bean and above.
     *
     * @param category The touch exploration category.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void checkTouchExplorationSupportInner(PreferenceGroup category) {
        final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_explore_by_touch_reflect_key);
        if (prefTouchExploration == null) {
            return;
        }

        // Remove single-tap preference if it's not supported on this device.
        final CheckBoxPreference prefSingleTap = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_single_tap_key);
        if ((prefSingleTap != null)
                && (Build.VERSION.SDK_INT < ProcessorFocusAndSingleTap.MIN_API_LEVEL_SINGLE_TAP)) {
            category.removePreference(prefSingleTap);
        }

        // Ensure that changes to the reflected preference's checked state never
        // trigger content observers.
        prefTouchExploration.setPersistent(false);

        // Synchronize the reflected state.
        updateTouchExplorationState();

        // Set up listeners that will keep the state synchronized.
        prefTouchExploration.setOnPreferenceChangeListener(mTouchExplorationChangeListener);

        // Hook in the external PreferenceActivity for gesture management
        final Preference shortcutsScreen = findPreferenceByResId(
                R.string.pref_category_manage_gestures_key);
        final Intent shortcutsIntent = new Intent(this, TalkBackShortcutPreferencesActivity.class);
        shortcutsScreen.setIntent(shortcutsIntent);
    }

    /**
     * Updates the preferences state to match the actual state of touch
     * exploration. This is called once when the preferences activity launches
     * and again whenever the actual state of touch exploration changes.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void updateTouchExplorationState() {
        final CheckBoxPreference prefTouchExploration = (CheckBoxPreference) findPreferenceByResId(
                R.string.pref_explore_by_touch_reflect_key);

        if (prefTouchExploration == null) {
            return;
        }

        final ContentResolver resolver = getContentResolver();
        final Resources res = getResources();
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean requestedState = SharedPreferencesUtils.getBooleanPref(prefs, res,
                R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
        final boolean reflectedState = prefTouchExploration.isChecked();
        final boolean actualState;

        // If accessibility is disabled then touch exploration is always
        // disabled, so the "actual" state should just be the requested state.
        if (TalkBackService.isServiceActive()) {
            actualState = isTouchExplorationEnabled(resolver);
        } else {
            actualState = requestedState;
        }

        // If touch exploration is actually off and we requested it on, the user
        // must have declined the "Enable touch exploration" dialog. Update the
        // requested value to reflect this.
        if (requestedState != actualState) {
            LogUtils.log(this, Log.DEBUG,
                    "Set touch exploration preference to reflect actual state %b", actualState);
            SharedPreferencesUtils.putBooleanPref(
                    prefs, res, R.string.pref_explore_by_touch_key, actualState);
        }

        // Ensure that the check box preference reflects the requested state,
        // which was just synchronized to match the actual state.
        if (reflectedState != actualState) {
            prefTouchExploration.setChecked(actualState);
        }
    }

    /**
     * Returns whether touch exploration is enabled. This is more reliable than
     * {@link AccessibilityManager#isTouchExplorationEnabled()} because it
     * updates atomically.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private boolean isTouchExplorationEnabled(ContentResolver resolver) {
        return (Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1);
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

        if (prefCallerId != null) {
            category.removePreference(prefCallerId);
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
     * Ensure that the shake to start continuous reading setting does not
     * appear on devices without a proximity sensor.
     */
    private void checkAccelerometerSupport() {
        final SensorManager manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        final Sensor accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accel != null && (Build.VERSION.SDK_INT >= ShakeDetector.MIN_API_LEVEL)) {
            return;
        }

        final PreferenceGroup category =
                (PreferenceGroup) findPreferenceByResId(R.string.pref_category_when_to_speak_key);
        final ListPreference prefShake =
                (ListPreference) findPreferenceByResId(R.string.pref_shake_to_read_threshold_key);

        if (prefShake != null) {
            category.removePreference(prefShake);
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
                this, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        final boolean removeKickBack = (kickBackVersionCode
                >= TalkBackUpdateHelper.KICKBACK_REQUIRED_VERSION);

        if (removeKickBack) {
            if (prefVibration != null) {
                category.removePreference(prefVibration);
            }
        }

        final CheckBoxPreference prefSoundBack =
                (CheckBoxPreference) findPreferenceByResId(R.string.pref_soundback_key);
        final Preference prefSoundBackVolume =
                findPreferenceByResId(R.string.pref_soundback_volume_key);
        final int soundBackVersionCode = PackageManagerUtils.getVersionCode(
                this, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
        final boolean removeSoundBack = (soundBackVersionCode
                >= TalkBackUpdateHelper.SOUNDBACK_REQUIRED_VERSION);

        if (removeSoundBack) {
            if (prefSoundBackVolume != null) {
                category.removePreference(prefSoundBackVolume);
            }

            if (prefSoundBack != null) {
                category.removePreference(prefSoundBack);
            }
        }

        if (removeKickBack && removeSoundBack) {
            if (category != null) {
                getPreferenceScreen().removePreference(category);
            }
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
     * Updates the preference that controls whether TalkBack will attempt to
     * request Explore by Touch.
     *
     * @param requestedState The state requested by the user.
     * @return Whether to update the reflected state.
     */
    private boolean setTouchExplorationRequested(boolean requestedState) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                TalkBackPreferencesActivity.this);

        // Update the "requested" state. This will trigger a listener in
        // TalkBack that changes the "actual" state.
        SharedPreferencesUtils.putBooleanPref(prefs, getResources(),
                R.string.pref_explore_by_touch_key, requestedState);

        // If TalkBack is inactive, we should immediately reflect the change in
        // "requested" state.
        if (!TalkBackService.isServiceActive()) {
            return true;
        }

        // If accessibility is on, we should wait for the "actual" state to
        // change, then reflect that change. If the user declines the system's
        // touch exploration dialog, the "actual" state will not change and
        // nothing needs to happen.
        LogUtils.log(this, Log.DEBUG, "TalkBack active, waiting for EBT request to take effect");
        return false;
    }

    private void confirmDisableExploreByTouch() {
        final DialogInterface.OnClickListener onClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (setTouchExplorationRequested(false)) {
                    // Manually tick the check box since we're not returning to
                    // the preference change listener.
                    final CheckBoxPreference prefTouchExploration =
                            (CheckBoxPreference) findPreferenceByResId(
                                    R.string.pref_explore_by_touch_reflect_key);
                    prefTouchExploration.setChecked(false);
                }
            }
        };

        new AlertDialog.Builder(this).setTitle(
                R.string.dialog_title_disable_exploration)
                .setMessage(R.string.dialog_message_disable_exploration)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes, onClick).show();
    }

    private final Handler mHandler = new Handler();

    private final ContentObserver mTouchExploreObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            if (selfChange) {
                return;
            }

            // The actual state of touch exploration has changed.
            updateTouchExplorationState();
        }
    };

    private final OnPreferenceChangeListener
            mTouchExplorationChangeListener = new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    final boolean requestedState = Boolean.TRUE.equals(newValue);

                    // If the user is trying to turn touch exploration off, show
                    // a confirmation dialog and don't change anything.
                    if (!requestedState) {
                        confirmDisableExploreByTouch();
                        return false;
                    }

                    return setTouchExplorationRequested(requestedState);
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

                    final String key = preference.getKey();
                    if (getString(R.string.pref_resume_talkback_key).equals(key)) {
                        final String oldValue = SharedPreferencesUtils.getStringPref(
                                mPrefs, getResources(), R.string.pref_resume_talkback_key,
                                R.string.pref_resume_talkback_default);
                        if (!newValue.equals(oldValue)) {
                            // Reset the suspend warning dialog when the resume
                            // preference changes.
                            SharedPreferencesUtils.putBooleanPref(mPrefs, getResources(),
                                    R.string.pref_show_suspension_confirmation_dialog, true);
                        }
                    }

                    return true;
                }
            };
}
