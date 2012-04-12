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
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceChangeListener;

/**
 * Activity used to set TalkBack's service preferences. This activity is loaded
 * when the TalkBackService is first connected to and allows the user to select
 * a font zoom size.
 * 
 * @author dmazzoni@google.com (Dominic Mazzoni)
 */
public class TalkBackPreferencesActivity extends PreferenceActivity {

    private static final String ACTION_ACCESSIBILITY_SETTINGS = "android.settings.ACCESSIBILITY_SETTINGS";

    private ListPreference mScreenOff = null;

    private String[] mScreenOffStrings = null;

    private ListPreference mRinger = null;

    private String[] mRingerStrings = null;

    private CheckBoxPreference mCallerId = null;

    private CheckBoxPreference mTtsExtended = null;

    private CheckBoxPreference mProximity = null;

    private PreferenceScreen mManagePluginsScreen = null;

    private Handler mHandler = null;

    /**
     * Loads the preferences from the XML preference definition and defines an
     * onPreferenceChangeListener for the font zoom size that restarts the
     * TalkBack service.
     */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (Build.VERSION.SDK_INT < 8) {
            addPreferencesFromResource(R.xml.preferences_prefroyo);
        } else {
            addPreferencesFromResource(R.xml.preferences);
        }

        mHandler = new Handler();

        int valueIndex;

        mScreenOff = (ListPreference) findPreference(getString(R.string.pref_speak_screenoff_key));
        mScreenOffStrings = getResources().getStringArray(R.array.pref_speak_screenoff_entries);
        valueIndex = mScreenOff.findIndexOfValue(mScreenOff.getValue());
        if (valueIndex >= 0) {
            mScreenOff.setSummary(mScreenOffStrings[valueIndex]);
        }
        mScreenOff.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int valueIndex = mScreenOff.findIndexOfValue((String) newValue);
                mScreenOff.setSummary(mScreenOffStrings[valueIndex]);
                deferReloadPreferences();
                return true;
            }
        });

        mRinger = (ListPreference) findPreference(getString(R.string.pref_speak_ringer_key));
        mRingerStrings = getResources().getStringArray(R.array.pref_speak_ringer_entries);
        valueIndex = mRinger.findIndexOfValue(mRinger.getValue());
        if (valueIndex >= 0) {
            mRinger.setSummary(mRingerStrings[valueIndex]);
        }
        mRinger.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                int valueIndex = mRinger.findIndexOfValue((String) newValue);
                mRinger.setSummary(mRingerStrings[valueIndex]);
                deferReloadPreferences();
                return true;
            }
        });

        mCallerId = (CheckBoxPreference) findPreference(getString(R.string.pref_caller_id_key));
        mCallerId.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                deferReloadPreferences();
                return true;
            }
        });

        // TTS Extended only exists pre-Froyo.
        if (Build.VERSION.SDK_INT < 8) {
            mTtsExtended = (CheckBoxPreference) findPreference(getString(R.string.pref_tts_extended_key));
            mTtsExtended.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    deferReloadPreferences();
                    return true;
                }
            });
        }

        mProximity = (CheckBoxPreference) findPreference(getString(R.string.pref_proximity_key));
        mProximity.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                deferReloadPreferences();
                return true;
            }
        });

        mManagePluginsScreen = (PreferenceScreen) findPreference(getString(R.string.pref_manage_plugins_key));
    }

    /**
     * Checks if the TalkBack service is currently running and displays an
     * AlertDialog directing the user to enable TalkBack from the accessibility
     * settings menu.
     */
    @Override
    public void onResume() {
        super.onRestart();
        if (!TalkBackService.isServiceInitialized()) {
            createInactiveServiceAlert().show();
        }
        setManagePluginsPreferenceState();
    }

    /**
     * Sets the state of the manage plug-ins preference.
     */
    private void setManagePluginsPreferenceState() {
        boolean hasInstalledPlugins = !PluginManager.getPlugins(getPackageManager()).isEmpty();
        mManagePluginsScreen.setEnabled(hasInstalledPlugins);
        if (hasInstalledPlugins) {
            mManagePluginsScreen.setIntent(new Intent(this, PluginPreferencesActivity.class));
        }
    }

    /**
     * Tell the service to reload preferences, but defer until the next event
     * loop, because onPreferenceChange gets called before the preference
     * changes, not after.
     */
    private void deferReloadPreferences() {
        mHandler.post(new Runnable() {
            public void run() {
                if (TalkBackService.isServiceInitialized()) {
                    TalkBackService.getInstance().reloadPreferences();
                }
            }
        });
    }

    /**
     * Constructs an AlertDialog that displays a warning when TalkBack is
     * disabled. Clicking the "Yes" button launches the accessibility settings
     * menu to enable the Talkback service.
     * 
     * @return an AlertDialog containing a warning message about TalkBack's
     *         disabled state
     */
    private AlertDialog createInactiveServiceAlert() {
        return new AlertDialog.Builder(this).setTitle(
                getString(R.string.title_talkback_inactive_alert)).setMessage(
                getString(R.string.message_talkback_inactive_alert)).setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        /*
                         * There is no guarantee that an accessibility settings
                         * menu exists, so if the ACTION_ACCESSIBILITY_SETTINGS
                         * intent doesn't match an activity, simply start the
                         * main settings activity.
                         */
                        Intent launchSettings = new Intent(ACTION_ACCESSIBILITY_SETTINGS);
                        try {
                            startActivity(launchSettings);
                        } catch (ActivityNotFoundException ae) {
                            showNoAccessibilityWarning();
                        }
                        dialog.dismiss();
                    }
                }).setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                }).create();
    }

    private void showNoAccessibilityWarning() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.title_no_accessibility_alert))
                .setMessage(getString(R.string.message_no_accessibility_alert)).setPositiveButton(
                        android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                TalkBackPreferencesActivity.this.finish();
                            }
                        }).create().show();
    }
}
