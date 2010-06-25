/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.tts;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Environment;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.CheckBoxPreference;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

public class TextToSpeechSettings extends PreferenceActivity implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener,
        TextToSpeechBeta.OnInitListener {

    private static final String TAG = "TextToSpeechSettings";

    private static final String SYSTEM_TTS = "com.svox.pico";

    private static final String KEY_TTS_PLAY_EXAMPLE = "tts_play_example";

    private static final String KEY_TTS_INSTALL_DATA = "tts_install_data";

    private static final String KEY_TTS_USE_DEFAULT = "toggle_use_default_tts_settings";

    private static final String KEY_TTS_DEFAULT_RATE = "tts_default_rate";

    private static final String KEY_TTS_DEFAULT_LANG = "tts_default_lang";

    private static final String KEY_TTS_DEFAULT_COUNTRY = "tts_default_country";

    private static final String KEY_TTS_DEFAULT_VARIANT = "tts_default_variant";

    private static final String KEY_TTS_DEFAULT_SYNTH = "tts_default_synth";

    private static final String KEY_TTS_ENABLED_PLUGINS = "tts_enabled_plugins";

    private static final String KEY_PLUGIN_ENABLED_PREFIX = "ENABLED_";

    private static final String KEY_PLUGIN_SETTINGS_PREFIX = "SETTINGS_";

    // TODO move default Locale values to TextToSpeech.Engine
    private static final String DEFAULT_LANG_VAL = "eng";

    private static final String DEFAULT_COUNTRY_VAL = "USA";

    private static final String DEFAULT_VARIANT_VAL = "";

    private static final String LOCALE_DELIMITER = "-";

    private static final String FALLBACK_TTS_DEFAULT_SYNTH = TextToSpeechBeta.Engine.DEFAULT_SYNTH;

    private SharedPreferences prefs;

    private Editor prefsEditor;

    private Preference mPlayExample = null;

    private Preference mInstallData = null;

    private CheckBoxPreference mUseDefaultPref = null;

    private ListPreference mDefaultRatePref = null;

    private ListPreference mDefaultLocPref = null;

    private ListPreference mDefaultSynthPref = null;

    private String mDefaultLanguage = null;

    private String mDefaultCountry = null;

    private String mDefaultLocVariant = null;

    private String mDefaultEng = "";

    private int mDefaultRate = TextToSpeechBeta.Engine.DEFAULT_RATE;

    // Array of strings used to demonstrate TTS in the different languages.
    private String[] mDemoStrings;

    // Index of the current string to use for the demo.
    private int mDemoStringIndex = 0;

    private boolean mEnableDemo = false;

    private boolean mVoicesMissing = false;

    private TextToSpeechBeta mTts = null;

    private boolean mTtsStarted = false;

    /**
     * Request code (arbitrary value) for voice data check through
     * startActivityForResult.
     */
    private static final int VOICE_DATA_INTEGRITY_CHECK = 1977;

    private static final int GET_SAMPLE_TEXT = 1983;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();

        addPreferencesFromResource(R.xml.tts_settings);

        addEngineSpecificSettings();

        mDemoStrings = getResources().getStringArray(R.array.tts_demo_strings);

        setVolumeControlStream(TextToSpeechBeta.Engine.DEFAULT_STREAM);

        mEnableDemo = false;
        mTtsStarted = false;

        Locale currentLocale = Locale.getDefault();
        mDefaultLanguage = currentLocale.getISO3Language();
        mDefaultCountry = currentLocale.getISO3Country();
        mDefaultLocVariant = currentLocale.getVariant();

        mTts = new TextToSpeechBeta(this, this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if ((mDefaultRatePref != null) && (mDefaultRatePref.getDialog() != null)) {
            mDefaultRatePref.getDialog().dismiss();
        }
        if ((mDefaultLocPref != null) && (mDefaultLocPref.getDialog() != null)) {
            mDefaultLocPref.getDialog().dismiss();
        }
        if ((mDefaultSynthPref != null) && (mDefaultSynthPref.getDialog() != null)) {
            mDefaultSynthPref.getDialog().dismiss();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mTtsStarted) {
            // whenever we return to this screen, we don't know the state of the
            // system, so we have to recheck that we can play the demo, or it
            // must be disabled.
            // TODO make the TTS service listen to "changes in the system", i.e.
            // sd card un/mount
            initClickers();
            updateWidgetState();
            checkVoiceData();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTts != null) {
            mTts.shutdown();
        }
    }

    private void addEngineSpecificSettings() {
        PreferenceGroup enginesCategory = (PreferenceGroup) findPreference("tts_engines_section");
        Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");
        ResolveInfo[] enginesArray = new ResolveInfo[0];
        PackageManager pm = getPackageManager();
        enginesArray = pm.queryIntentActivities(intent, 0).toArray(enginesArray);
        for (int i = 0; i < enginesArray.length; i++) {
            String prefKey = "";
            final String pluginPackageName = enginesArray[i].activityInfo.packageName;
            if (!enginesArray[i].activityInfo.packageName.equals(SYSTEM_TTS)) {
                CheckBoxPreference chkbxPref = new CheckBoxPreference(this);
                prefKey = KEY_PLUGIN_ENABLED_PREFIX + pluginPackageName;
                chkbxPref.setKey(prefKey);
                chkbxPref.setTitle(enginesArray[i].loadLabel(pm));
                enginesCategory.addPreference(chkbxPref);
            }
            if (pluginHasSettings(pluginPackageName)) {
                Preference pref = new Preference(this);
                prefKey = KEY_PLUGIN_SETTINGS_PREFIX + pluginPackageName;
                pref.setKey(prefKey);
                pref.setTitle(enginesArray[i].loadLabel(pm));
                CharSequence settingsLabel = getResources().getString(
                        R.string.tts_engine_name_settings, enginesArray[i].loadLabel(pm));
                pref.setSummary(settingsLabel);
                pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                    public boolean onPreferenceClick(Preference preference) {
                        Intent i = new Intent();
                        i.setClassName(pluginPackageName, pluginPackageName + ".EngineSettings");
                        startActivity(i);
                        return true;
                    }
                });
                enginesCategory.addPreference(pref);
            }
        }
    }

    private boolean pluginHasSettings(String pluginPackageName) {
        PackageManager pm = getPackageManager();
        Intent i = new Intent();
        i.setClassName(pluginPackageName, pluginPackageName + ".EngineSettings");
        if (pm.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return true;
        }
        return false;
    }

    private void initClickers() {
        mPlayExample = findPreference(KEY_TTS_PLAY_EXAMPLE);
        mPlayExample.setOnPreferenceClickListener(this);

        mInstallData = findPreference(KEY_TTS_INSTALL_DATA);
        mInstallData.setOnPreferenceClickListener(this);
    }

    private void initDefaultSettings() {
        ContentResolver resolver = getContentResolver();

        // Find the default TTS values in the settings, initialize and store the
        // settings if they are not found.

        // "Use Defaults"
        int useDefault = 0;
        mUseDefaultPref = (CheckBoxPreference) findPreference(KEY_TTS_USE_DEFAULT);
        useDefault = prefs.getInt(KEY_TTS_USE_DEFAULT, TextToSpeechBeta.Engine.USE_DEFAULTS);
        mUseDefaultPref.setChecked(useDefault == 1);
        mUseDefaultPref.setOnPreferenceChangeListener(this);

        // Default synthesis engine
        mDefaultSynthPref = (ListPreference) findPreference(KEY_TTS_DEFAULT_SYNTH);
        loadEngines();
        mDefaultSynthPref.setOnPreferenceChangeListener(this);
        String engine = prefs.getString(KEY_TTS_DEFAULT_SYNTH, FALLBACK_TTS_DEFAULT_SYNTH);
        mDefaultEng = engine;

        // Default rate
        mDefaultRatePref = (ListPreference) findPreference(KEY_TTS_DEFAULT_RATE);
        mDefaultRate = prefs.getInt(KEY_TTS_DEFAULT_RATE, mDefaultRate);
        mDefaultRatePref.setValue(String.valueOf(mDefaultRate));
        mDefaultRatePref.setOnPreferenceChangeListener(this);

        // Default language / country / variant : these three values map to a
        // single ListPref
        // representing the matching Locale
        mDefaultLocPref = (ListPreference) findPreference(KEY_TTS_DEFAULT_LANG);
        initDefaultLang();
        mDefaultLocPref.setOnPreferenceChangeListener(this);
    }

    /**
     * Ask the current default engine to launch the matching CHECK_TTS_DATA
     * activity to check the required TTS files are properly installed.
     */
    private void checkVoiceData() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent();
        intent.setAction(TextToSpeechBeta.Engine.ACTION_CHECK_TTS_DATA);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        // query only the package that matches that of the default engine
        for (int i = 0; i < resolveInfos.size(); i++) {
            ActivityInfo currentActivityInfo = resolveInfos.get(i).activityInfo;
            if (mDefaultEng.equals(currentActivityInfo.packageName)) {
                intent.setClassName(mDefaultEng, currentActivityInfo.name);
                this.startActivityForResult(intent, VOICE_DATA_INTEGRITY_CHECK);
            }
        }
    }

    /**
     * Ask the current default engine to launch the matching INSTALL_TTS_DATA
     * activity so the required TTS files are properly installed.
     */
    private void installVoiceData() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent();
        intent.setAction(TextToSpeechBeta.Engine.ACTION_INSTALL_TTS_DATA);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        // query only the package that matches that of the default engine
        for (int i = 0; i < resolveInfos.size(); i++) {
            ActivityInfo currentActivityInfo = resolveInfos.get(i).activityInfo;
            if (mDefaultEng.equals(currentActivityInfo.packageName)) {
                intent.setClassName(mDefaultEng, currentActivityInfo.name);
                this.startActivity(intent);
            }
        }
    }

    /**
     * Ask the current default engine to return a string of sample text to be
     * spoken to the user.
     */
    private void getSampleText() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent();
        // TODO (clchen): Replace Intent string with the actual
        // Intent defined in the list of platform Intents.
        intent.setAction("android.speech.tts.engine.GET_SAMPLE_TEXT");
        intent.putExtra("language", mDefaultLanguage);
        intent.putExtra("country", mDefaultCountry);
        intent.putExtra("variant", mDefaultLocVariant);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        // query only the package that matches that of the default engine
        for (int i = 0; i < resolveInfos.size(); i++) {
            ActivityInfo currentActivityInfo = resolveInfos.get(i).activityInfo;
            if (mDefaultEng.equals(currentActivityInfo.packageName)) {
                intent.setClassName(mDefaultEng, currentActivityInfo.name);
                this.startActivityForResult(intent, GET_SAMPLE_TEXT);
            }
        }
        // -- Only needed in TTS Extended to deal with pre-Froyo system Pico
        // The version of Pico built into the system for versions 4-7 of Android
        // does not return all the information needed by TTS Extended.
        // Compensate for that here by adding in the missing bits.
        if (mDefaultEng.equals(SYSTEM_TTS) && (Integer.parseInt(android.os.Build.VERSION.SDK) < 8)
                && (Integer.parseInt(android.os.Build.VERSION.SDK) > 3)) {
            intent.setClassName("com.google.tts", "com.google.tts.GetSampleText");
            this.startActivityForResult(intent, GET_SAMPLE_TEXT);
        }
        // -- End of workaround for pre-Froyo system Pico
    }

    /**
     * Called when the TTS engine is initialized.
     */
    public void onInit(int status, int version) {
        if (status == TextToSpeech.SUCCESS) {
            mEnableDemo = true;
            if (mDefaultLanguage == null) {
                mDefaultLanguage = Locale.getDefault().getISO3Language();
            }
            if (mDefaultCountry == null) {
                mDefaultCountry = Locale.getDefault().getISO3Country();
            }
            if (mDefaultLocVariant == null) {
                mDefaultLocVariant = new String();
            }
            initDefaultSettings();
            mTts.setLanguage(new Locale(mDefaultLanguage, mDefaultCountry, mDefaultLocVariant));
            mTts.setSpeechRate((mDefaultRate / 100.0f));
            mTts.setEngineByPackageNameExtended(mDefaultEng);
            initClickers();
            updateWidgetState();
            checkVoiceData();
            mTtsStarted = true;
            Log.v(TAG, "TTS engine for settings screen initialized.");
        } else {
            Log.v(TAG, "TTS engine for settings screen failed to initialize successfully.");
            mEnableDemo = false;
        }
        updateWidgetState();
    }

    /**
     * Called when voice data integrity check returns
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == VOICE_DATA_INTEGRITY_CHECK) {
            if (data == null) {
                // The CHECK_TTS_DATA activity for the plugin did not run
                // properly;
                // disable the preview and install controls and return.
                mEnableDemo = false;
                mVoicesMissing = false;
                updateWidgetState();
                return;
            }
            // TODO (clchen): Add these extras to TextToSpeech.Engine
            ArrayList<String> available = data.getStringArrayListExtra("availableVoices");
            ArrayList<String> unavailable = data.getStringArrayListExtra("unavailableVoices");

            // -- Only needed in TTS Extended to deal with pre-Froyo system Pico
            // The version of Pico built into the system for versions 4-7 of
            // Android
            // does not return all the information needed by TTS Extended.
            // Compensate for that here by adding in the missing bits.
            if (mDefaultEng.equals(SYSTEM_TTS)
                    && (Integer.parseInt(android.os.Build.VERSION.SDK) < 8)
                    && (Integer.parseInt(android.os.Build.VERSION.SDK) > 3)) {
                available = new ArrayList<String>();
                unavailable = new ArrayList<String>();
                if (resultCode < 0) {
                    unavailable.add("deu-DEU");
                    unavailable.add("eng-GBR");
                    unavailable.add("eng-USA");
                    unavailable.add("spa-ESP");
                    unavailable.add("fra-FRA");
                    unavailable.add("ita-ITA");
                } else {
                    available.add("deu-DEU");
                    available.add("eng-GBR");
                    available.add("eng-USA");
                    available.add("spa-ESP");
                    available.add("fra-FRA");
                    available.add("ita-ITA");
                }
            }
            // -- End of workaround for pre-Froyo system Pico

            if ((available == null) || (unavailable == null)) {
                // The CHECK_TTS_DATA activity for the plugin did not run
                // properly;
                // disable the preview and install controls and return.
                mEnableDemo = false;
                mVoicesMissing = false;
                updateWidgetState();
                return;
            }
            if (available.size() > 0) {
                if (mTts == null) {
                    mTts = new TextToSpeechBeta(this, this);
                }
                ListPreference ttsLanguagePref = (ListPreference) findPreference("tts_default_lang");
                CharSequence[] entries = new CharSequence[available.size()];
                CharSequence[] entryValues = new CharSequence[available.size()];
                int selectedLanguageIndex = -1;
                String selectedLanguagePref = mDefaultLanguage;
                if (mDefaultCountry.length() > 0) {
                    selectedLanguagePref = selectedLanguagePref + LOCALE_DELIMITER
                            + mDefaultCountry;
                }
                if (mDefaultLocVariant.length() > 0) {
                    selectedLanguagePref = selectedLanguagePref + LOCALE_DELIMITER
                            + mDefaultLocVariant;
                }
                for (int i = 0; i < available.size(); i++) {
                    String[] langCountryVariant = available.get(i).split("-");
                    Locale loc = null;
                    if (langCountryVariant.length == 1) {
                        loc = new Locale(langCountryVariant[0]);
                    } else if (langCountryVariant.length == 2) {
                        loc = new Locale(langCountryVariant[0], langCountryVariant[1]);
                    } else if (langCountryVariant.length == 3) {
                        loc = new Locale(langCountryVariant[0], langCountryVariant[1],
                                langCountryVariant[2]);
                    }
                    if (loc != null) {
                        entries[i] = loc.getDisplayName();
                        entryValues[i] = available.get(i);
                        if (entryValues[i].equals(selectedLanguagePref)) {
                            selectedLanguageIndex = i;
                        }
                    }
                }
                ttsLanguagePref.setEntries(entries);
                ttsLanguagePref.setEntryValues(entryValues);
                if (selectedLanguageIndex > -1) {
                    ttsLanguagePref.setValueIndex(selectedLanguageIndex);
                }
                mEnableDemo = true;
                // Make sure that the default language can be used.
                int languageResult = mTts.setLanguage(new Locale(mDefaultLanguage, mDefaultCountry,
                        mDefaultLocVariant));
                if (languageResult < TextToSpeech.LANG_AVAILABLE) {
                    Locale currentLocale = Locale.getDefault();
                    mDefaultLanguage = currentLocale.getISO3Language();
                    mDefaultCountry = currentLocale.getISO3Country();
                    mDefaultLocVariant = currentLocale.getVariant();
                    languageResult = mTts.setLanguage(new Locale(mDefaultLanguage, mDefaultCountry,
                            mDefaultLocVariant));
                    // If the default Locale isn't supported, just choose the
                    // first available
                    // language so that there is at least something.
                    if (languageResult < TextToSpeech.LANG_AVAILABLE) {
                        parseLocaleInfo(ttsLanguagePref.getEntryValues()[0].toString());
                        mTts.setLanguage(new Locale(mDefaultLanguage, mDefaultCountry,
                                mDefaultLocVariant));
                    }
                    prefsEditor.putString(KEY_TTS_DEFAULT_LANG, mDefaultLanguage);
                    prefsEditor.putString(KEY_TTS_DEFAULT_COUNTRY, mDefaultCountry);
                    prefsEditor.putString(KEY_TTS_DEFAULT_VARIANT, mDefaultLocVariant);
                    prefsEditor.commit();
                }
            } else {
                mEnableDemo = false;
            }

            if (unavailable.size() > 0) {
                mVoicesMissing = true;
            } else {
                mVoicesMissing = false;
            }

            updateWidgetState();
        } else if (requestCode == GET_SAMPLE_TEXT) {
            if (resultCode == TextToSpeech.LANG_AVAILABLE) {
                String sample = getString(R.string.tts_demo);
                if ((data != null) && (data.getStringExtra("sampleText") != null)) {
                    sample = data.getStringExtra("sampleText");
                }
                if (mTts != null) {
                    mTts.speak(sample, TextToSpeech.QUEUE_FLUSH, null);
                }
            } else {
                // TODO: Display an error here to the user.
                Log.e(TAG, "Did not have a sample string for the requested language");
            }
        }
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (KEY_TTS_USE_DEFAULT.equals(preference.getKey())) {
            // "Use Defaults"
            int value = (Boolean) objValue ? 1 : 0;
            prefsEditor.putInt(KEY_TTS_USE_DEFAULT, value);
            prefsEditor.commit();
            Log.i(TAG, "TTS use default settings is " + objValue.toString());
        } else if (KEY_TTS_DEFAULT_RATE.equals(preference.getKey())) {
            // Default rate
            mDefaultRate = Integer.parseInt((String) objValue);
            try {
                prefsEditor.putInt(KEY_TTS_DEFAULT_RATE, mDefaultRate);
                prefsEditor.commit();
                if (mTts != null) {
                    mTts.setSpeechRate((mDefaultRate / 100.0f));
                }
                Log.i(TAG, "TTS default rate is " + mDefaultRate);
            } catch (NumberFormatException e) {
                Log.e(TAG, "could not persist default TTS rate setting", e);
            }
        } else if (KEY_TTS_DEFAULT_LANG.equals(preference.getKey())) {
            // Default locale
            ContentResolver resolver = getContentResolver();
            parseLocaleInfo((String) objValue);
            prefsEditor.putString(KEY_TTS_DEFAULT_LANG, mDefaultLanguage);
            prefsEditor.putString(KEY_TTS_DEFAULT_COUNTRY, mDefaultCountry);
            prefsEditor.putString(KEY_TTS_DEFAULT_VARIANT, mDefaultLocVariant);
            prefsEditor.commit();

            Log.v(TAG, "TTS default lang/country/variant set to " + mDefaultLanguage + "/"
                    + mDefaultCountry + "/" + mDefaultLocVariant);
            if (mTts != null) {
                mTts.setLanguage(new Locale(mDefaultLanguage, mDefaultCountry, mDefaultLocVariant));
            }
            int newIndex = mDefaultLocPref.findIndexOfValue((String) objValue);
            Log.v("Settings", " selected is " + newIndex);
            mDemoStringIndex = newIndex > -1 ? newIndex : 0;
        } else if (KEY_TTS_DEFAULT_SYNTH.equals(preference.getKey())) {
            mDefaultEng = objValue.toString();
            prefsEditor.putString(KEY_TTS_DEFAULT_SYNTH, mDefaultEng);
            prefsEditor.commit();
            if (mTts != null) {
                mTts.setEngineByPackageNameExtended(mDefaultEng);
                mEnableDemo = false;
                mVoicesMissing = false;
                updateWidgetState();
                checkVoiceData();
            }
            Log.v("Settings", "The default synth is: " + objValue.toString());
        }

        return true;
    }

    /**
     * Called when mPlayExample or mInstallData is clicked
     */
    public boolean onPreferenceClick(Preference preference) {
        if (preference == mPlayExample) {
            // Get the sample text from the TTS engine; onActivityResult will do
            // the actual speaking
            getSampleText();
            return true;
        }
        if (preference == mInstallData) {
            installVoiceData();
            // quit this activity so it needs to be restarted after installation
            // of the voice data
            finish();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if (preference instanceof CheckBoxPreference) {
            final CheckBoxPreference chkPref = (CheckBoxPreference) preference;
            if (!chkPref.getKey().equals(KEY_TTS_USE_DEFAULT)) {
                if (chkPref.isChecked()) {
                    chkPref.setChecked(false);
                    AlertDialog d = (new AlertDialog.Builder(this)).setTitle(
                            android.R.string.dialog_alert_title).setIcon(
                            android.R.drawable.ic_dialog_alert).setMessage(
                            getString(R.string.tts_engine_security_warning, chkPref.getTitle()))
                            .setCancelable(true).setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            chkPref.setChecked(true);
                                            loadEngines();
                                        }
                                    }).setNegativeButton(android.R.string.cancel,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    }).create();
                    d.show();
                } else {
                    loadEngines();
                }
                return true;
            }
        }
        return false;
    }

    private void updateWidgetState() {
        mPlayExample.setEnabled(mEnableDemo);
        mUseDefaultPref.setEnabled(mEnableDemo);
        mDefaultRatePref.setEnabled(mEnableDemo);
        mDefaultLocPref.setEnabled(mEnableDemo);

        mInstallData.setEnabled(mVoicesMissing);
    }

    private void parseLocaleInfo(String locale) {
        StringTokenizer tokenizer = new StringTokenizer(locale, LOCALE_DELIMITER);
        mDefaultLanguage = "";
        mDefaultCountry = "";
        mDefaultLocVariant = "";
        if (tokenizer.hasMoreTokens()) {
            mDefaultLanguage = tokenizer.nextToken().trim();
        }
        if (tokenizer.hasMoreTokens()) {
            mDefaultCountry = tokenizer.nextToken().trim();
        }
        if (tokenizer.hasMoreTokens()) {
            mDefaultLocVariant = tokenizer.nextToken().trim();
        }
    }

    /**
     * Initialize the default language in the UI and in the preferences. After
     * this method has been invoked, the default language is a supported Locale.
     */
    private void initDefaultLang() {
        // if there isn't already a default language preference
        if (!hasLangPref()) {
            // if the current Locale is supported
            if (isCurrentLocSupported()) {
                // then use the current Locale as the default language
                useCurrentLocAsDefault();
            } else {
                // otherwise use a default supported Locale as the default
                // language
                useSupportedLocAsDefault();
            }
        }

        // Update the language preference list with the default language and the
        // matching
        // demo string (at this stage there is a default language pref)

        mDefaultLanguage = prefs.getString(KEY_TTS_DEFAULT_LANG, DEFAULT_LANG_VAL);
        mDefaultCountry = prefs.getString(KEY_TTS_DEFAULT_COUNTRY, DEFAULT_COUNTRY_VAL);
        mDefaultLocVariant = prefs.getString(KEY_TTS_DEFAULT_VARIANT, DEFAULT_VARIANT_VAL);

        // update the demo string
        mDemoStringIndex = mDefaultLocPref.findIndexOfValue(mDefaultLanguage + LOCALE_DELIMITER
                + mDefaultCountry);
        if (mDemoStringIndex > -1) {
            mDefaultLocPref.setValueIndex(mDemoStringIndex);
        }
    }

    /**
     * (helper function for initDefaultLang() ) Returns whether there is a
     * default language in the TTS settings.
     */
    private boolean hasLangPref() {
        String language = prefs.getString(KEY_TTS_DEFAULT_LANG, null);
        if ((language == null) || (language.length() < 1)) {
            return false;
        }

        String country = prefs.getString(KEY_TTS_DEFAULT_COUNTRY, null);
        if (country == null) {
            return false;
        }

        String variant = prefs.getString(KEY_TTS_DEFAULT_VARIANT, null);
        if (variant == null) {
            return false;
        }
        return true;
    }

    /**
     * (helper function for initDefaultLang() ) Returns whether the current
     * Locale is supported by this Settings screen
     */
    private boolean isCurrentLocSupported() {
        String currentLocID = Locale.getDefault().getISO3Language() + LOCALE_DELIMITER
                + Locale.getDefault().getISO3Country();
        return (mDefaultLocPref.findIndexOfValue(currentLocID) > -1);
    }

    /**
     * (helper function for initDefaultLang() ) Sets the default language in TTS
     * settings to be the current Locale. This should only be used after
     * checking that the current Locale is supported.
     */
    private void useCurrentLocAsDefault() {
        Locale currentLocale = Locale.getDefault();
        prefsEditor.putString(KEY_TTS_DEFAULT_LANG, currentLocale.getISO3Language());
        prefsEditor.putString(KEY_TTS_DEFAULT_COUNTRY, currentLocale.getISO3Country());
        prefsEditor.putString(KEY_TTS_DEFAULT_VARIANT, currentLocale.getVariant());
        prefsEditor.commit();
    }

    /**
     * (helper function for initDefaultLang() ) Sets the default language in TTS
     * settings to be one known to be supported
     */
    private void useSupportedLocAsDefault() {
        prefsEditor.putString(KEY_TTS_DEFAULT_LANG, DEFAULT_LANG_VAL);
        prefsEditor.putString(KEY_TTS_DEFAULT_COUNTRY, DEFAULT_COUNTRY_VAL);
        prefsEditor.putString(KEY_TTS_DEFAULT_VARIANT, DEFAULT_VARIANT_VAL);
        prefsEditor.commit();
    }

    private void loadEngines() {
        mDefaultSynthPref = (ListPreference) findPreference(KEY_TTS_DEFAULT_SYNTH);

        // TODO (clchen): Try to see if it is possible to be more efficient here
        // and not search for plugins again.
        Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");
        ResolveInfo[] enginesArray = new ResolveInfo[0];
        PackageManager pm = getPackageManager();
        enginesArray = pm.queryIntentActivities(intent, 0).toArray(enginesArray);
        ArrayList<CharSequence> entries = new ArrayList<CharSequence>();
        ArrayList<CharSequence> values = new ArrayList<CharSequence>();
        String enabledEngines = "";
        for (int i = 0; i < enginesArray.length; i++) {
            String pluginPackageName = enginesArray[i].activityInfo.packageName;
            if (pluginPackageName.equals(SYSTEM_TTS)) {
                entries.add(enginesArray[i].loadLabel(pm));
                values.add(pluginPackageName);
            } else {
                CheckBoxPreference pref = (CheckBoxPreference) findPreference(KEY_PLUGIN_ENABLED_PREFIX
                        + pluginPackageName);
                if ((pref != null) && pref.isChecked()) {
                    entries.add(enginesArray[i].loadLabel(pm));
                    values.add(pluginPackageName);
                    enabledEngines = enabledEngines + pluginPackageName + " ";
                }
            }
        }

        prefsEditor.putString(KEY_TTS_ENABLED_PLUGINS, enabledEngines);
        prefsEditor.commit();

        CharSequence entriesArray[] = new CharSequence[entries.size()];
        CharSequence valuesArray[] = new CharSequence[values.size()];

        mDefaultSynthPref.setEntries(entries.toArray(entriesArray));
        mDefaultSynthPref.setEntryValues(values.toArray(valuesArray));

        // Set the selected engine based on the saved preference
        String selectedEngine = prefs.getString(KEY_TTS_DEFAULT_SYNTH, FALLBACK_TTS_DEFAULT_SYNTH);
        int selectedEngineIndex = mDefaultSynthPref.findIndexOfValue(selectedEngine);
        if (selectedEngineIndex == -1) {
            selectedEngineIndex = mDefaultSynthPref.findIndexOfValue(SYSTEM_TTS);
        }
        mDefaultSynthPref.setValueIndex(selectedEngineIndex);
    }

}
