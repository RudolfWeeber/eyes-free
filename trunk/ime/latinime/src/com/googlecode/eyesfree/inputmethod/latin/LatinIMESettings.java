/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.googlecode.eyesfree.inputmethod.latin;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.backup.BackupManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.text.AutoText;
import android.util.Log;

import com.googlecode.eyesfree.inputmethod.voice.SettingsUtil;
import com.googlecode.eyesfree.inputmethod.voice.VoiceInputLogger;
import com.googlecode.eyesfree.utils.PackageManagerUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

public class LatinIMESettings extends PreferenceActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, DialogInterface.OnDismissListener {
    /** The package that provides the Linear Navigation service. */
    public static final String LINEAR_NAVIGATION_PACKAGE =
            "com.googlecode.eyesfree.linearnavigation";

    /** The URI string for launching the App Market. */
    private static final String MARKET_URI = "market://details?id=";

    /** Request code for accessibility settings activity. */
    private static final int REQUEST_ACCESSIBILITY_SETTINGS = 1;

    /** Request code for Market install activity. */
    private static final int REQUEST_MARKET_INSTALL = 2;
    
    /** Preference ordering for Linear Navigation setting. */
    private static final int LINEAR_NAVIGATION_ORDER = 7;

    private static final String VIBRATE_ON_KEY = "vibrate_on";
    private static final String QUICK_FIXES_KEY = "quick_fixes";
    private static final String PREDICTION_SETTINGS_KEY = "prediction_settings";
    private static final String VOICE_SETTINGS_KEY = "voice_mode";
    /* package */static final String PREF_DPAD_KEYS = "dpad_keys";

    private static final String TAG = "LatinIMESettings";

    // Dialog ids
    private static final int VOICE_INPUT_CONFIRM_DIALOG = 0;

    private CheckBoxPreference mHapticPreference;
    private CheckBoxPreference mQuickFixes;
    private ListPreference mVoicePreference;
    private boolean mVoiceOn;

    private Vibrator mVibrator;
    private VoiceInputLogger mLogger;

    private boolean mOkClicked = false;
    private String mVoiceModeOff;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs);
        mHapticPreference = (CheckBoxPreference) findPreference(VIBRATE_ON_KEY);
        mQuickFixes = (CheckBoxPreference) findPreference(QUICK_FIXES_KEY);
        mVoicePreference = (ListPreference) findPreference(VOICE_SETTINGS_KEY);
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);

        mVoiceModeOff = getString(R.string.voice_mode_off);
        mVoiceOn = !(prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff).equals(mVoiceModeOff));
        mLogger = VoiceInputLogger.getLogger(this);
        mVibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        updateLinearNavigationPreference();
        updateHapticPreferenceVisibility();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ACCESSIBILITY_SETTINGS:
            case REQUEST_MARKET_INSTALL:
                updateLinearNavigationPreference();
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        int autoTextSize = AutoText.getSize(getListView());
        if (autoTextSize < 1) {
            ((PreferenceGroup) findPreference(PREDICTION_SETTINGS_KEY))
                    .removePreference(mQuickFixes);
        }
        if (!LatinIME.VOICE_INSTALLED || !SpeechRecognizer.isRecognitionAvailable(this)) {
            getPreferenceScreen().removePreference(mVoicePreference);
        } else {
            updateVoiceModeSummary();
        }
    }

    @Override
    protected void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
        super.onDestroy();
    }

    /**
     * Returns <code>true</code> if this Android build supports
     * {@link BackupManager}.
     * 
     * @return <code>true</code> if this Android build supports
     *         {@link BackupManager}
     */
    private static boolean supportsBackupManager() {
        return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (supportsBackupManager()) {
            (new BackupManager(this)).dataChanged();
        }
        // If turning on voice input, show dialog
        if (key.equals(VOICE_SETTINGS_KEY) && !mVoiceOn) {
            if (!prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff).equals(mVoiceModeOff)) {
                showVoiceConfirmation();
            }
        }
        mVoiceOn = !(prefs.getString(VOICE_SETTINGS_KEY, mVoiceModeOff).equals(mVoiceModeOff));
        updateVoiceModeSummary();
    }

    private void updateHapticPreferenceVisibility() {
        if (Build.VERSION.SDK_INT < 11) {
            return;
        }

        try {
            Method hasVibrator = mVibrator.getClass().getMethod("hasVibrator");
            Object result = hasVibrator.invoke(mVibrator);
            if (result.equals(Boolean.FALSE)) {
                getPreferenceScreen().removePreference(mHapticPreference);
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    /**
     * Disables the "Linear navigation" preference, if applicable.
     */
    private void updateLinearNavigationPreference() {
        final Preference existingPreference = findPreference("linear_navigation");
        
        if (existingPreference != null) {
            getPreferenceScreen().removePreference(existingPreference);
        }

        final boolean serviceSupported = Build.VERSION.SDK_INT >= 14;

        if (!serviceSupported) {
            return;
        }

        final boolean serviceInstalled =
                PackageManagerUtils.hasPackage(this, LINEAR_NAVIGATION_PACKAGE);

        if (!serviceInstalled) {
            final Preference installPreference = new Preference(this);
            installPreference.setKey("linear_navigation");
            installPreference.setTitle(R.string.linear_navigation);
            installPreference.setSummary(R.string.linear_navigation_missing);
            installPreference.setOrder(LINEAR_NAVIGATION_ORDER);
            installPreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Intent intent =
                            new Intent(Intent.ACTION_VIEW, Uri.parse(MARKET_URI
                                    + LINEAR_NAVIGATION_PACKAGE));
                    startActivityForResult(intent, REQUEST_ACCESSIBILITY_SETTINGS);
                    return true;
                }
            });
            getPreferenceScreen().addPreference(installPreference);
            return;
        }

        final String enabledServices =
                Settings.Secure.getString(getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        final boolean serviceEnabled =
                (enabledServices != null) && enabledServices.contains(LINEAR_NAVIGATION_PACKAGE);

        if (!serviceEnabled) {
            final Preference enablePreference = new Preference(this);
            enablePreference.setKey("linear_navigation");
            enablePreference.setTitle(R.string.linear_navigation);
            enablePreference.setSummary(R.string.linear_navigation_disabled);
            enablePreference.setOrder(LINEAR_NAVIGATION_ORDER);
            enablePreference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    final Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivityForResult(intent, REQUEST_ACCESSIBILITY_SETTINGS);
                    return true;
                }
            });
            getPreferenceScreen().addPreference(enablePreference);
            return;
        }

        final CheckBoxPreference defaultPreference = new CheckBoxPreference(this);
        defaultPreference.setKey("linear_navigation");
        defaultPreference.setTitle(R.string.linear_navigation);
        defaultPreference.setSummary(R.string.linear_navigation_summary);
        defaultPreference.setOrder(LINEAR_NAVIGATION_ORDER);
        getPreferenceScreen().addPreference(defaultPreference);
    }

    private void showVoiceConfirmation() {
        mOkClicked = false;
        showDialog(VOICE_INPUT_CONFIRM_DIALOG);
    }

    private void updateVoiceModeSummary() {
        mVoicePreference.setSummary(getResources()
                .getStringArray(R.array.voice_input_modes_summary)[mVoicePreference
                .findIndexOfValue(mVoicePreference.getValue())]);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case VOICE_INPUT_CONFIRM_DIALOG:
                DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (whichButton == DialogInterface.BUTTON_NEGATIVE) {
                            mVoicePreference.setValue(mVoiceModeOff);
                            mLogger.settingsWarningDialogCancel();
                        } else if (whichButton == DialogInterface.BUTTON_POSITIVE) {
                            mOkClicked = true;
                            mLogger.settingsWarningDialogOk();
                        }
                        updateVoicePreference();
                    }
                };
                AlertDialog.Builder builder =
                        new AlertDialog.Builder(this).setTitle(R.string.voice_warning_title)
                                .setPositiveButton(android.R.string.ok, listener)
                                .setNegativeButton(android.R.string.cancel, listener);

                // Get the current list of supported locales and check the
                // current locale against
                // that list, to decide whether to put a warning that voice
                // input will not work in
                // the current language as part of the pop-up confirmation
                // dialog.
                String supportedLocalesString =
                        SettingsUtil.getSettingsString(getContentResolver(),
                                SettingsUtil.LATIN_IME_VOICE_INPUT_SUPPORTED_LOCALES,
                                LatinIME.DEFAULT_VOICE_INPUT_SUPPORTED_LOCALES);
                ArrayList<String> voiceInputSupportedLocales =
                        LatinIME.newArrayList(supportedLocalesString.split("\\s+"));
                boolean localeSupported =
                        voiceInputSupportedLocales.contains(Locale.getDefault().toString());

                if (localeSupported) {
                    String message =
                            getString(R.string.voice_warning_may_not_understand) + "\n\n"
                                    + getString(R.string.voice_hint_dialog_message);
                    builder.setMessage(message);
                } else {
                    String message =
                            getString(R.string.voice_warning_locale_not_supported) + "\n\n"
                                    + getString(R.string.voice_warning_may_not_understand) + "\n\n"
                                    + getString(R.string.voice_hint_dialog_message);
                    builder.setMessage(message);
                }

                AlertDialog dialog = builder.create();
                dialog.setOnDismissListener(this);
                mLogger.settingsWarningDialogShown();
                return dialog;
            default:
                Log.e(TAG, "unknown dialog " + id);
                return null;
        }
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        mLogger.settingsWarningDialogDismissed();
        if (!mOkClicked) {
            // This assumes that onPreferenceClick gets called first, and this
            // if the user
            // agreed after the warning, we set the mOkClicked value to true.
            mVoicePreference.setValue(mVoiceModeOff);
        }
    }

    private void updateVoicePreference() {
        boolean isChecked = !mVoicePreference.getValue().equals(mVoiceModeOff);
        if (isChecked) {
            mLogger.voiceInputSettingEnabled();
        } else {
            mLogger.voiceInputSettingDisabled();
        }
    }
}
