/*
 * Copyright (C) 2009 Google Inc.
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

package com.google.tts;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import com.google.tts.ITTSCallback;
import android.util.Log;
import android.util.TypedValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;

import javax.xml.parsers.FactoryConfigurationError;

/**
 * @hide Synthesizes speech from text. This is implemented as a service so that
 *       other applications can call the TTS without needing to bundle the TTS
 *       in the build.
 */
public class TTSService extends Service implements OnCompletionListener {

    // This is for legacy purposes. The old TTS used languages of the format
    // "xx-rYY" (xx denotes language and YY denotes region).
    // This now needs to be mapped to Locale, which is of the format:
    // xxx-YYY-variant (xxx denotes language, YYY denotes country, and variant
    // is the name of the variant).
    static final HashMap<String, String> langRegionToLocale = new HashMap<String, String>();
    static {
        langRegionToLocale.put("af", "afr");
        langRegionToLocale.put("bs", "bos");
        langRegionToLocale.put("zh-rHK", "yue");
        langRegionToLocale.put("zh", "cmn");
        langRegionToLocale.put("hr", "hrv");
        langRegionToLocale.put("cz", "ces");
        langRegionToLocale.put("cs", "ces");
        langRegionToLocale.put("nl", "nld");
        langRegionToLocale.put("en", "eng");
        langRegionToLocale.put("en-rUS", "eng-USA");
        langRegionToLocale.put("en-rGB", "eng-GBR");
        langRegionToLocale.put("eo", "epo");
        langRegionToLocale.put("fi", "fin");
        langRegionToLocale.put("fr", "fra");
        langRegionToLocale.put("fr-rFR", "fra-FRA");
        langRegionToLocale.put("de", "deu");
        langRegionToLocale.put("de-rDE", "deu-DEU");
        langRegionToLocale.put("el", "ell");
        langRegionToLocale.put("hi", "hin");
        langRegionToLocale.put("hu", "hun");
        langRegionToLocale.put("is", "isl");
        langRegionToLocale.put("id", "ind");
        langRegionToLocale.put("it", "ita");
        langRegionToLocale.put("it-rIT", "ita-ITA");
        langRegionToLocale.put("ku", "kur");
        langRegionToLocale.put("la", "lat");
        langRegionToLocale.put("mk", "mkd");
        langRegionToLocale.put("no", "nor");
        langRegionToLocale.put("pl", "pol");
        langRegionToLocale.put("pt", "por");
        langRegionToLocale.put("ro", "ron");
        langRegionToLocale.put("ru", "rus");
        langRegionToLocale.put("sr", "srp");
        langRegionToLocale.put("sk", "slk");
        langRegionToLocale.put("es", "spa");
        langRegionToLocale.put("es-rES", "spa-ESP");
        langRegionToLocale.put("es-rMX", "spa-MEX");
        langRegionToLocale.put("sw", "swa");
        langRegionToLocale.put("sv", "swe");
        langRegionToLocale.put("ta", "tam");
        langRegionToLocale.put("tr", "tur");
        langRegionToLocale.put("vi", "vie");
        langRegionToLocale.put("cy", "cym");
    }

    private static class SpeechItem {
        public static final int TEXT = 0;

        public static final int EARCON = 1;

        public static final int SILENCE = 2;

        public static final int TEXT_TO_FILE = 3;

        public String mText = "";

        public ArrayList<String> mParams = null;

        public int mType = TEXT;

        public long mDuration = 0;

        public String mFilename = null;

        public String mCallingApp = "";

        public SpeechItem(String source, String text, ArrayList<String> params, int itemType) {
            mText = text;
            mParams = params;
            mType = itemType;
            mCallingApp = source;
        }

        public SpeechItem(String source, long silenceTime, ArrayList<String> params) {
            mDuration = silenceTime;
            mParams = params;
            mType = SILENCE;
            mCallingApp = source;
        }

        public SpeechItem(String source, String text, ArrayList<String> params,
                int itemType, String filename) {
            mText = text;
            mParams = params;
            mType = itemType;
            mFilename = filename;
            mCallingApp = source;
        }

    }

    /**
     * Contains the information needed to access a sound resource; the name of
     * the package that contains the resource and the resID of the resource
     * within that package.
     */
    private static class SoundResource {
        public String mSourcePackageName = null;

        public int mResId = -1;

        public String mFilename = null;

        public SoundResource(String packageName, int id) {
            mSourcePackageName = packageName;
            mResId = id;
            mFilename = null;
        }

        public SoundResource(String file) {
            mSourcePackageName = null;
            mResId = -1;
            mFilename = file;
        }
    }

    // If the speech queue is locked for more than 5 seconds, something has gone
    // very wrong with processSpeechQueue.
    private static final int SPEECHQUEUELOCK_TIMEOUT = 5000;

    private static final int MAX_SPEECH_ITEM_CHAR_LENGTH = 4000;

    private static final int MAX_FILENAME_LENGTH = 250;

    // TODO use the TTS stream type when available
    private static final int DEFAULT_STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private static final String ACTION = "android.intent.action.USE_TTS";

    private static final String CATEGORY = "android.intent.category.TTS";

    private static final String BETA_ACTION = "com.google.intent.action.START_TTS_SERVICE_BETA";

    private static final String BETA_CATEGORY = "com.google.intent.category.TTS_BETA";

    private static final String PKGNAME = "android.tts";

    // Change this to the system/lib path when in the framework
    private static final String DEFAULT_SYNTH = "com.svox.pico";

    protected static final String SERVICE_TAG = "TtsService";

    private final RemoteCallbackList<ITtsCallbackBeta> mCallbacks = new RemoteCallbackList<ITtsCallbackBeta>();

    private HashMap<String, ITtsCallbackBeta> mCallbacksMap;

    private final RemoteCallbackList<ITTSCallback> mCallbacksOld = new RemoteCallbackList<ITTSCallback>();

    private boolean mIsSpeaking;
    
    private boolean mSynthBusy;

    private ArrayList<SpeechItem> mSpeechQueue;

    private HashMap<String, SoundResource> mEarcons;

    private HashMap<String, SoundResource> mUtterances;

    private MediaPlayer mPlayer;

    private SpeechItem mCurrentSpeechItem;

    private HashMap<SpeechItem, Boolean> mKillList; // Used to ensure that

    // in-flight synth calls
    // are killed when stop is used.
    private TTSService mSelf;

    // lock for the speech queue (mSpeechQueue) and the current speech item
    // (mCurrentSpeechItem)
    private final ReentrantLock speechQueueLock = new ReentrantLock();

    private final ReentrantLock synthesizerLock = new ReentrantLock();

    private static SynthProxyBeta sNativeSynth = null;

    private String currentSpeechEngineSOFile = "";

    private boolean deprecatedKeepBlockingFlag = false;


    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(SERVICE_TAG, "TtsService.onCreate()");

        // String soLibPath = "/data/data/com.google.tts/lib/libttspico.so";
        // Use this path when building in the framework:
        // setEngine("/system/lib/libttspico.so");
        // setEngine("/data/data/com.google.tts/lib/libespeakengine.so");
        // setEngine("/data/data/com.google.tts/lib/libttspico.so");
        // Also, switch to using the system settings in the framework.
        currentSpeechEngineSOFile = "";
        String preferredEngine = PreferenceManager.getDefaultSharedPreferences(this).getString(
                "tts_default_synth", DEFAULT_SYNTH);
        if (setEngine(preferredEngine) != TextToSpeechBeta.SUCCESS) {
            Log.e(SERVICE_TAG, "Unable to start up with " + preferredEngine
                    + ". Falling back to the default TTS engine.");
            setEngine(DEFAULT_SYNTH);
        }

        mSelf = this;
        mIsSpeaking = false;
        mSynthBusy = false;

        mEarcons = new HashMap<String, SoundResource>();
        mUtterances = new HashMap<String, SoundResource>();
        mCallbacksMap = new HashMap<String, ITtsCallbackBeta>();

        mSpeechQueue = new ArrayList<SpeechItem>();
        mPlayer = null;
        mCurrentSpeechItem = null;
        mKillList = new HashMap<SpeechItem, Boolean>();

        setDefaultSettings();

        // Standalone library only - include a set of default earcons
        // and pre-recorded audio.
        // These are not in the framework due to concerns about the size.
        Resources res = getResources();
        InputStream fis = res.openRawResource(R.raw.soundsamples);
        try {

            Properties soundsamples = new Properties();
            soundsamples.load(fis);
            Enumeration<Object> textKeys = soundsamples.keys();
            while (textKeys.hasMoreElements()) {
                String text = textKeys.nextElement().toString();
                String name = "com.google.tts:raw/" + soundsamples.getProperty(text);
                TypedValue value = new TypedValue();
                getResources().getValue(name, value, false);
                mUtterances.put(text, new SoundResource(PKGNAME, value.resourceId));
            }

        } catch (FactoryConfigurationError e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }

        // Deprecated - these should be earcons from now on!
        // Leave this here for one more version before removing it completely.
        mUtterances.put("[tock]", new SoundResource(PKGNAME, R.raw.tock_snd));
        mUtterances.put("[slnc]", new SoundResource(PKGNAME, R.raw.slnc_snd));

        mEarcons.put("[tock]", new SoundResource(PKGNAME, R.raw.tock_snd));
        mEarcons.put("[slnc]", new SoundResource(PKGNAME, R.raw.slnc_snd));

        Log.e(SERVICE_TAG, "onCreate completed.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        killAllUtterances();

        // Don't hog the media player
        cleanUpPlayer();

        if (sNativeSynth != null) {
            sNativeSynth.shutdown();
        }
        sNativeSynth = null;

        // Unregister all callbacks.
        mCallbacks.kill();
        mCallbacksOld.kill();

        Log.v(SERVICE_TAG, "onDestroy() completed");
    }

    private int setEngine(String enginePackageName) {
        if (isDefaultEnforced()){
            enginePackageName = getDefaultEngine();
        }
        
        // This is a hack to prevent the user from trying to run the Pico
        // engine if they are on Cupcake since the Pico auto-install only works
        // on
        // Donut or higher and no equivalent has not been backported.
        int sdkInt = 4;
        try {
            sdkInt = Integer.parseInt(android.os.Build.VERSION.SDK);
        } catch (NumberFormatException e) {
            Log.e(SERVICE_TAG, "Unable to parse SDK version: " + android.os.Build.VERSION.SDK);
        }
        if ((sdkInt < 4) && enginePackageName.equals("com.svox.pico")) {
            enginePackageName = "com.google.tts";
        }

        String soFilename = "";
        // The SVOX TTS is an exception to how the TTS packaging scheme works
        // because
        // it is part of the system and not a 3rd party add-on; thus its binary
        // is
        // actually located under /system/lib/
        if (enginePackageName.equals("com.svox.pico")) {
            // This is the path to use when this is integrated with the
            // framework
            // soFilename = "/system/lib/libttspico.so";
            if (sdkInt < 5) {
                soFilename = "/data/data/com.google.tts/lib/libttspico_4.so";
            } else {
                soFilename = "/data/data/com.google.tts/lib/libttspico.so";
            }
        } else {
            // Find the package
            // This is the correct way to do this; but it won't work in Cupcake.
            // :(
            // Intent intent = new
            // Intent("android.intent.action.START_TTS_ENGINE");
            // intent.setPackage(enginePackageName);
            // ResolveInfo[] enginesArray = new ResolveInfo[0];
            // PackageManager pm = getPackageManager();
            // List <ResolveInfo> resolveInfos =
            // pm.queryIntentActivities(intent, 0);
            // if ((resolveInfos == null) || resolveInfos.isEmpty()) {
            // Log.e(SERVICE_TAG, "Invalid TTS Engine Package: " +
            // enginePackageName);
            // return TextToSpeechBeta.ERROR;
            // }
            // enginesArray = resolveInfos.toArray(enginesArray);
            // // Generate the TTS .so filename from the package
            // ActivityInfo aInfo = enginesArray[0].activityInfo;
            // soFilename = aInfo.name.replace(aInfo.packageName + ".", "") +
            // ".so";
            // soFilename = soFilename.toLowerCase();
            // soFilename = "/data/data/" + aInfo.packageName + "/lib/libtts" +
            // soFilename;

            /* Start of hacky way of doing this */
            // Using a loop since we can't set the package name for the intent
            // in
            // Cupcake
            Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");
            ResolveInfo[] enginesArray = new ResolveInfo[0];
            PackageManager pm = getPackageManager();
            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
            enginesArray = resolveInfos.toArray(enginesArray);
            ActivityInfo aInfo = null;
            for (int i = 0; i < enginesArray.length; i++) {
                if (enginesArray[i].activityInfo.packageName.equals(enginePackageName)) {
                    aInfo = enginesArray[i].activityInfo;
                    break;
                }
            }
            if (aInfo == null) {
                Log.e(SERVICE_TAG, "Invalid TTS Engine Package: " + enginePackageName);
                return TextToSpeechBeta.ERROR;
            }

            // Try to get a platform SDK specific binary
            if (sdkInt < 5) {
                sdkInt = 4;
            }
            soFilename = aInfo.name.replace(aInfo.packageName + ".", "") + "_" + sdkInt + ".so";
            soFilename = soFilename.toLowerCase();
            soFilename = "/data/data/" + aInfo.packageName + "/lib/libtts" + soFilename;
            File f = new File(soFilename);
            // If no such binary is available, default to a generic binary
            if (!f.exists()) {
                soFilename = aInfo.name.replace(aInfo.packageName + ".", "") + ".so";
                soFilename = soFilename.toLowerCase();
                soFilename = "/data/data/" + aInfo.packageName + "/lib/libtts" + soFilename;
            }

            /* End of hacky way of doing this */
        }

        if (currentSpeechEngineSOFile.equals(soFilename)) {
            return TextToSpeechBeta.SUCCESS;
        }

        File f = new File(soFilename);
        if (!f.exists()) {
            Log.e(SERVICE_TAG, "Invalid TTS Binary: " + soFilename);
            return TextToSpeechBeta.ERROR;
        }
        if (sNativeSynth != null) {
            // Should really be a stopSync here, but that is not available in
            // Donut...
            // sNativeSynth.stopSync();
            sNativeSynth.stop();
            sNativeSynth.shutdown();
            sNativeSynth = null;
        }
        sNativeSynth = new SynthProxyBeta(soFilename);
        currentSpeechEngineSOFile = soFilename;
        return TextToSpeechBeta.SUCCESS;
    }

    private void setDefaultSettings() {
        setLanguage("", getDefaultLanguage(), getDefaultCountry(), getDefaultLocVariant());

        // speech rate
        setSpeechRate("", getDefaultRate());
    }

    private boolean isDefaultEnforced() {
        return (PreferenceManager.getDefaultSharedPreferences(this).getInt("toggle_use_default_tts_settings",
                0) == 1);
        // In the framework, use the Secure settings instead by doing:
        //
        // return (android.provider.Settings.Secure.getInt(mResolver,
        // android.provider.Settings.Secure.TTS_USE_DEFAULTS,
        // TextToSpeechBeta.Engine.USE_DEFAULTS)
        // == 1 );
    }

    private String getDefaultEngine() {
        // In the framework, use the Secure settings instead by doing:
        // String defaultEngine = android.provider.Settings.Secure.getString(mResolver,
        // android.provider.Settings.Secure.TTS_DEFAULT_SYNTH);
        String defaultEngine = PreferenceManager.getDefaultSharedPreferences(this).getString("tts_default_synth", DEFAULT_SYNTH);
        if (defaultEngine == null) {
            return TextToSpeechBeta.Engine.DEFAULT_SYNTH;
        } else {
            return defaultEngine;
        }
    }

    private int getDefaultRate() {
        return Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(this).getString(
                "rate_pref", "100"));
        // In the framework, use the Secure settings instead by doing:
        //    	
        // return android.provider.Settings.Secure.getInt(mResolver,
        // android.provider.Settings.Secure.TTS_DEFAULT_RATE,
        // TextToSpeechBeta.Engine.DEFAULT_RATE);
    }

    private int getDefaultPitch() {
        // Pitch is not user settable; the default pitch is always 100.
        return 100;
    }

    private String getDefaultLanguage() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(
                "tts_default_lang", Locale.getDefault().getISO3Language());

        // In the framework, use the Secure settings instead by doing:
        //       	
        // String defaultLang =
        // android.provider.Settings.Secure.getString(mResolver,
        // android.provider.Settings.Secure.TTS_DEFAULT_LANG);
    }

    private String getDefaultCountry() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(
                "tts_default_country", Locale.getDefault().getISO3Country());
        
        // In the framework, use the Secure settings instead by doing:
        //       	        
        // String defaultCountry =
        // android.provider.Settings.Secure.getString(mResolver,
        // android.provider.Settings.Secure.TTS_DEFAULT_COUNTRY);
    }

    private String getDefaultLocVariant() {
        return PreferenceManager.getDefaultSharedPreferences(this).getString(
                "tts_default_variant", Locale.getDefault().getVariant());
        // In the framework, use the Secure settings instead by doing:
        //       	          	
        // String defaultVar =
        // android.provider.Settings.Secure.getString(mResolver,
        // android.provider.Settings.Secure.TTS_DEFAULT_VARIANT);
    }

    private int setSpeechRate(String callingApp, int rate) {
        int res = TextToSpeechBeta.ERROR;
        try {
            if (isDefaultEnforced()) {
                res = sNativeSynth.setSpeechRate(getDefaultRate());
            } else {
                res = sNativeSynth.setSpeechRate(rate);
            }
        } catch (NullPointerException e) {
            // synth will become null during onDestroy()
            res = TextToSpeechBeta.ERROR;
        }
        return res;
    }

    private int setPitch(String callingApp, int pitch) {
        int res = TextToSpeechBeta.ERROR;
        try {
            res = sNativeSynth.setPitch(pitch);
        } catch (NullPointerException e) {
            // synth will become null during onDestroy()
            res = TextToSpeechBeta.ERROR;
        }
        return res;
    }

    private int isLanguageAvailable(String lang, String country, String variant) {
        int res = TextToSpeechBeta.LANG_NOT_SUPPORTED;
        try {
            res = sNativeSynth.isLanguageAvailable(lang, country, variant);
        } catch (NullPointerException e) {
            // synth will become null during onDestroy()
            res = TextToSpeechBeta.LANG_NOT_SUPPORTED;
        }
        return res;
    }

    private String[] getLanguage() {
        try {
            return sNativeSynth.getLanguage();
        } catch (Exception e) {
            return null;
        }
    }

    private int setLanguage(String callingApp, String lang, String country, String variant) {
        Log
                .v(SERVICE_TAG, "TtsService.setLanguage(" + lang + ", " + country + ", " + variant
                        + ")");
        int res = TextToSpeechBeta.ERROR;
        try {
            if (isDefaultEnforced()) {
                res = sNativeSynth.setLanguage(getDefaultLanguage(), getDefaultCountry(),
                        getDefaultLocVariant());
            } else {
                res = sNativeSynth.setLanguage(lang, country, variant);
            }
        } catch (NullPointerException e) {
            // synth will become null during onDestroy()
            res = TextToSpeechBeta.ERROR;
        }
        return res;
    }

    /**
     * Adds a sound resource to the TTS.
     * 
     * @param text The text that should be associated with the sound resource
     * @param packageName The name of the package which has the sound resource
     * @param resId The resource ID of the sound within its package
     */
    private void addSpeech(String callingApp, String text, String packageName, int resId) {
        mUtterances.put(text, new SoundResource(packageName, resId));
    }

    /**
     * Adds a sound resource to the TTS.
     * 
     * @param text The text that should be associated with the sound resource
     * @param filename The filename of the sound resource. This must be a
     *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
     */
    private void addSpeech(String callingApp, String text, String filename) {
        mUtterances.put(text, new SoundResource(filename));
    }

    /**
     * Adds a sound resource to the TTS as an earcon.
     * 
     * @param earcon The text that should be associated with the sound resource
     * @param packageName The name of the package which has the sound resource
     * @param resId The resource ID of the sound within its package
     */
    private void addEarcon(String callingApp, String earcon, String packageName, int resId) {
        mEarcons.put(earcon, new SoundResource(packageName, resId));
    }

    /**
     * Adds a sound resource to the TTS as an earcon.
     * 
     * @param earcon The text that should be associated with the sound resource
     * @param filename The filename of the sound resource. This must be a
     *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
     */
    private void addEarcon(String callingApp, String earcon, String filename) {
        mEarcons.put(earcon, new SoundResource(filename));
    }

    /**
     * Speaks the given text using the specified queueing mode and parameters.
     * 
     * @param text The text that should be spoken
     * @param queueMode TextToSpeech.TTS_QUEUE_FLUSH for no queue (interrupts
     *            all previous utterances), TextToSpeech.TTS_QUEUE_ADD for
     *            queued
     * @param params An ArrayList of parameters. This is not implemented for all
     *            engines.
     */
    private int speak(String callingApp, String text, int queueMode, ArrayList<String> params) {
        Log.v(SERVICE_TAG, "TTS service received " + text);
        if (queueMode == TextToSpeechBeta.QUEUE_FLUSH) {
            stop(callingApp);
        } else if (queueMode == 2) {
            stopAll(callingApp);
        }
        mSpeechQueue.add(new SpeechItem(callingApp, text, params, SpeechItem.TEXT));
        if (!mIsSpeaking) {
            processSpeechQueue();
        }
        return TextToSpeechBeta.SUCCESS;
    }

    /**
     * Plays the earcon using the specified queueing mode and parameters.
     * 
     * @param earcon The earcon that should be played
     * @param queueMode TextToSpeech.TTS_QUEUE_FLUSH for no queue (interrupts
     *            all previous utterances), TextToSpeech.TTS_QUEUE_ADD for
     *            queued
     * @param params An ArrayList of parameters. This is not implemented for all
     *            engines.
     */
    private int playEarcon(String callingApp, String earcon, int queueMode, ArrayList<String> params) {
        if (queueMode == TextToSpeechBeta.QUEUE_FLUSH) {
            stop(callingApp);
        } else if (queueMode == 2) {
            stopAll(callingApp);
        }
        mSpeechQueue.add(new SpeechItem(callingApp, earcon, params, SpeechItem.EARCON));
        if (!mIsSpeaking) {
            processSpeechQueue();
        }
        return TextToSpeechBeta.SUCCESS;
    }

    /**
     * Stops all speech output and removes any utterances still in the queue for
     * the calling app.
     */
    private int stop(String callingApp) {
        int result = TextToSpeechBeta.ERROR;
        boolean speechQueueAvailable = false;
        try{
            speechQueueAvailable =
                    speechQueueLock.tryLock(SPEECHQUEUELOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (speechQueueAvailable) {
                Log.i(SERVICE_TAG, "Stopping");
                for (int i = mSpeechQueue.size() - 1; i > -1; i--){
                    if (mSpeechQueue.get(i).mCallingApp.equals(callingApp)){
                        mSpeechQueue.remove(i);
                    }
                }
                if ((mCurrentSpeechItem != null) &&
                     mCurrentSpeechItem.mCallingApp.equals(callingApp)) {
                    try {
                        result = sNativeSynth.stop();
                    } catch (NullPointerException e1) {
                        // synth will become null during onDestroy()
                        result = TextToSpeechBeta.ERROR;
                    }
                    mKillList.put(mCurrentSpeechItem, true);
                    if (mPlayer != null) {
                        try {
                            mPlayer.stop();
                        } catch (IllegalStateException e) {
                            // Do nothing, the player is already stopped.
                        }
                    }
                    mIsSpeaking = false;
                    mCurrentSpeechItem = null;
                } else {
                    result = TextToSpeechBeta.SUCCESS;
                }
                Log.i(SERVICE_TAG, "Stopped");
            } else {
                Log.e(SERVICE_TAG, "TTS stop(): queue locked longer than expected");
                result = TextToSpeechBeta.ERROR;
            }
        } catch (InterruptedException e) {
          Log.e(SERVICE_TAG, "TTS stop: tryLock interrupted");
          e.printStackTrace();
        } finally {
            // This check is needed because finally will always run; even if the
            // method returns somewhere in the try block.
            if (speechQueueAvailable) {
                speechQueueLock.unlock();
            }
            return result;
        }
    }

    /**
     * Stops all speech output, both rendered to a file and directly spoken, and
     * removes any utterances still in the queue globally. Files that were being
     * written are deleted.
     */
    @SuppressWarnings("finally")
    private int killAllUtterances() {
        int result = TextToSpeechBeta.ERROR;
        boolean speechQueueAvailable = false;

        try {
            speechQueueAvailable = speechQueueLock.tryLock(SPEECHQUEUELOCK_TIMEOUT,
                    TimeUnit.MILLISECONDS);
            if (speechQueueAvailable) {
                // remove every single entry in the speech queue
                mSpeechQueue.clear();

                // clear the current speech item
                if (mCurrentSpeechItem != null) {
                    // Should be a stopSync - only using a stop for Donut
                    // compatibility
                    // result = sNativeSynth.stopSync();
                    result = sNativeSynth.stop();
                    mKillList.put(mCurrentSpeechItem, true);
                    mIsSpeaking = false;

                    // was the engine writing to a file?
                    if (mCurrentSpeechItem.mType == SpeechItem.TEXT_TO_FILE) {
                        // delete the file that was being written
                        if (mCurrentSpeechItem.mFilename != null) {
                            File tempFile = new File(mCurrentSpeechItem.mFilename);
                            Log.v(SERVICE_TAG, "Leaving behind " + mCurrentSpeechItem.mFilename);
                            if (tempFile.exists()) {
                                Log.v(SERVICE_TAG, "About to delete "
                                        + mCurrentSpeechItem.mFilename);
                                if (tempFile.delete()) {
                                    Log.v(SERVICE_TAG, "file successfully deleted");
                                }
                            }
                        }
                    }

                    mCurrentSpeechItem = null;
                }
            } else {
                Log.e(SERVICE_TAG, "TTS killAllUtterances(): queue locked longer than expected");
                result = TextToSpeechBeta.ERROR;
            }
        } catch (InterruptedException e) {
            Log.e(SERVICE_TAG, "TTS killAllUtterances(): tryLock interrupted");
            result = TextToSpeechBeta.ERROR;
        } finally {
            // This check is needed because finally will always run, even if the
            // method returns somewhere in the try block.
            if (speechQueueAvailable) {
                speechQueueLock.unlock();
            }
            return result;
        }
    }

    /**
     * Stops all speech output and removes any utterances still in the queue
     * globally, except those intended to be synthesized to file.
     */
    private int stopAll(String callingApp) {
        int result = TextToSpeechBeta.ERROR;
        boolean speechQueueAvailable = false;
        try{
            speechQueueAvailable =
                    speechQueueLock.tryLock(SPEECHQUEUELOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (speechQueueAvailable) {
                for (int i = mSpeechQueue.size() - 1; i > -1; i--){
                    if (mSpeechQueue.get(i).mType != SpeechItem.TEXT_TO_FILE){
                        mSpeechQueue.remove(i);
                    }
                }
                if ((mCurrentSpeechItem != null) &&
                    ((mCurrentSpeechItem.mType != SpeechItem.TEXT_TO_FILE) ||
                      mCurrentSpeechItem.mCallingApp.equals(callingApp))) {
                    try {
                        result = sNativeSynth.stop();
                    } catch (NullPointerException e1) {
                        // synth will become null during onDestroy()
                        result = TextToSpeechBeta.ERROR;
                    }
                    mKillList.put(mCurrentSpeechItem, true);
                    if (mPlayer != null) {
                        try {
                            mPlayer.stop();
                        } catch (IllegalStateException e) {
                            // Do nothing, the player is already stopped.
                        }
                    }
                    mIsSpeaking = false;
                    mCurrentSpeechItem = null;
                } else {
                    result = TextToSpeechBeta.SUCCESS;
                }
                Log.i(SERVICE_TAG, "Stopped all");
            } else {
                Log.e(SERVICE_TAG, "TTS stopAll(): queue locked longer than expected");
                result = TextToSpeechBeta.ERROR;
            }
        } catch (InterruptedException e) {
          Log.e(SERVICE_TAG, "TTS stopAll: tryLock interrupted");
          e.printStackTrace();
        } finally {
            // This check is needed because finally will always run; even if the
            // method returns somewhere in the try block.
            if (speechQueueAvailable) {
                speechQueueLock.unlock();
            }
            return result;
        }
    }

    public void onCompletion(MediaPlayer arg0) {
        // mCurrentSpeechItem may become null if it is stopped at the same
        // time it completes.
        SpeechItem currentSpeechItemCopy = mCurrentSpeechItem;
        if (currentSpeechItemCopy != null) {
            String callingApp = currentSpeechItemCopy.mCallingApp;
            ArrayList<String> params = currentSpeechItemCopy.mParams;
            String utteranceId = "";
            if (params != null) {
                for (int i = 0; i < params.size() - 1; i = i + 2) {
                    String param = params.get(i);
                    if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID)) {
                        utteranceId = params.get(i + 1);
                    }
                }
            }
            if (utteranceId.length() > 0) {
                dispatchUtteranceCompletedCallback(utteranceId, callingApp);
            }
        }
        processSpeechQueue();
    }

    private int playSilence(String callingApp, long duration, int queueMode,
            ArrayList<String> params) {
        if (queueMode == TextToSpeechBeta.QUEUE_FLUSH) {
            stop(callingApp);
        }
        mSpeechQueue.add(new SpeechItem(callingApp, duration, params));
        if (!mIsSpeaking) {
            processSpeechQueue();
        }
        return TextToSpeechBeta.SUCCESS;
    }

    private void silence(final SpeechItem speechItem) {
        class SilenceThread implements Runnable {
            public void run() {
                String utteranceId = "";
                if (speechItem.mParams != null){
                    for (int i = 0; i < speechItem.mParams.size() - 1; i = i + 2){
                        String param = speechItem.mParams.get(i);
                        if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID)){
                            utteranceId = speechItem.mParams.get(i+1);
                        }
                    }
                }
                try {
                    Thread.sleep(speechItem.mDuration);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (utteranceId.length() > 0){
                        dispatchUtteranceCompletedCallback(utteranceId, speechItem.mCallingApp);
                    }
                    processSpeechQueue();
                }
            }
        }
        Thread slnc = (new Thread(new SilenceThread()));
        slnc.setPriority(Thread.MIN_PRIORITY);
        slnc.start();
    }

    boolean synthThreadBusy = false;
    
    private void speakInternalOnly(final SpeechItem speechItem) {
        Log.e(SERVICE_TAG, "Creating synth thread for: " + speechItem.mText);
        class SynthThread implements Runnable {
            public void run() {
                boolean synthAvailable = false;
                String utteranceId = "";
                try {
                    synthAvailable = synthesizerLock.tryLock();
                    if (!synthAvailable) {
                        mSynthBusy = true;
                        Thread.sleep(100);
                        Thread synth = (new Thread(new SynthThread()));
                        synth.start();
                        mSynthBusy = false;
                        return;
                    }
                    int streamType = DEFAULT_STREAM_TYPE;
                    String language = "";
                    String country = "";
                    String variant = "";
                    String speechRate = "";
                    String engine = "";
                    String pitch = "";
                    if (speechItem.mParams != null){
                        for (int i = 0; i < speechItem.mParams.size() - 1; i = i + 2){
                            String param = speechItem.mParams.get(i);
                            if (param != null) {
                                if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_RATE)) {
                                    speechRate = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_LANGUAGE)) {
                                    language = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_COUNTRY)) {
                                    country = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_VARIANT)) {
                                    variant = speechItem.mParams.get(i + 1);
                                } else if (param
                                        .equals(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID)) {
                                    utteranceId = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_STREAM)) {
                                    try {
                                        streamType = Integer
                                                .parseInt(speechItem.mParams.get(i + 1));
                                    } catch (NumberFormatException e) {
                                        streamType = DEFAULT_STREAM_TYPE;
                                    }
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_ENGINE)) {
                                    engine = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_PITCH)) {
                                    pitch = speechItem.mParams.get(i + 1);
                                }
                            }
                        }
                    }
                    // Only do the synthesis if it has not been killed by a subsequent utterance.
                    if (mKillList.get(speechItem) == null) {
                        if (engine.length() > 0) {
                            setEngine(engine);
                        } else {
                            setEngine(getDefaultEngine());
                        }
                        if (language.length() > 0){
                            setLanguage("", language, country, variant);
                        } else {
                            setLanguage("", getDefaultLanguage(), getDefaultCountry(),
                                    getDefaultLocVariant());
                        }
                        if (speechRate.length() > 0){
                            setSpeechRate("", Integer.parseInt(speechRate));
                        } else {
                            setSpeechRate("", getDefaultRate());
                        }
                        if (pitch.length() > 0){
                            setPitch("", Integer.parseInt(pitch));
                        } else {
                            setPitch("", getDefaultPitch());
                        }
                        try {
                            sNativeSynth.speak(speechItem.mText, streamType);
                        } catch (NullPointerException e) {
                            // synth will become null during onDestroy()
                            Log.v(SERVICE_TAG, " null synth, can't speak");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(SERVICE_TAG, "TTS speakInternalOnly(): tryLock interrupted");
                    e.printStackTrace();
                } finally {
                    // This check is needed because finally will always run;
                    // even if the
                    // method returns somewhere in the try block.
                    if (utteranceId.length() > 0){
                        dispatchUtteranceCompletedCallback(utteranceId, speechItem.mCallingApp);
                    }
                    if (synthAvailable) {
                        synthesizerLock.unlock();
                        processSpeechQueue();
                    }
                }
            }
        }
        Thread synth = (new Thread(new SynthThread()));
        synth.setPriority(Thread.MAX_PRIORITY);
        synth.start();
    }

    private void synthToFileInternalOnly(final SpeechItem speechItem) {
        class SynthThread implements Runnable {
            public void run() {
                boolean synthAvailable = false;
                String utteranceId = "";
                Log.i(SERVICE_TAG, "Synthesizing to " + speechItem.mFilename);
                try {
                    synthAvailable = synthesizerLock.tryLock();
                    if (!synthAvailable) {
                        synchronized (this) {
                            mSynthBusy = true;
                        }
                        Thread.sleep(100);
                        Thread synth = (new Thread(new SynthThread()));
                        // synth.setPriority(Thread.MIN_PRIORITY);
                        synth.start();
                        synchronized (this) {
                            mSynthBusy = false;
                        }
                        return;
                    }
                    String language = "";
                    String country = "";
                    String variant = "";
                    String speechRate = "";
                    String engine = "";
                    String pitch = "";
                    if (speechItem.mParams != null){
                        for (int i = 0; i < speechItem.mParams.size() - 1; i = i + 2){
                            String param = speechItem.mParams.get(i);
                            if (param != null) {
                                if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_RATE)) {
                                    speechRate = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_LANGUAGE)) {
                                    language = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_COUNTRY)) {
                                    country = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_VARIANT)) {
                                    variant = speechItem.mParams.get(i + 1);
                                } else if (param
                                        .equals(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID)) {
                                    utteranceId = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_ENGINE)) {
                                    engine = speechItem.mParams.get(i + 1);
                                } else if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_PITCH)) {
                                    pitch = speechItem.mParams.get(i + 1);
                                }
                            }
                        }
                    }
                    // Only do the synthesis if it has not been killed by a subsequent utterance.
                    if (mKillList.get(speechItem) == null){
                        if (engine.length() > 0) {
                            setEngine(engine);
                        } else {
                            setEngine(getDefaultEngine());
                        }
                        if (language.length() > 0){
                            setLanguage("", language, country, variant);
                        } else {
                            setLanguage("", getDefaultLanguage(), getDefaultCountry(),
                                    getDefaultLocVariant());
                        }
                        if (speechRate.length() > 0){
                            setSpeechRate("", Integer.parseInt(speechRate));
                        } else {
                            setSpeechRate("", getDefaultRate());
                        }
                        if (pitch.length() > 0){
                            setPitch("", Integer.parseInt(pitch));
                        } else {
                            setPitch("", getDefaultPitch());
                        }
                        try {
                            sNativeSynth.synthesizeToFile(speechItem.mText, speechItem.mFilename);
                        } catch (NullPointerException e) {
                            // synth will become null during onDestroy()
                            Log.v(SERVICE_TAG, " null synth, can't synthesize to file");
                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(SERVICE_TAG, "TTS synthToFileInternalOnly(): tryLock interrupted");
                    e.printStackTrace();
                } finally {
                    // This check is needed because finally will always run;
                    // even if the
                    // method returns somewhere in the try block.
                    if (utteranceId.length() > 0){
                        dispatchUtteranceCompletedCallback(utteranceId, speechItem.mCallingApp);
                    }
                    if (synthAvailable) {
                        synthesizerLock.unlock();
                        processSpeechQueue();
                    }
                    deprecatedKeepBlockingFlag = false;
                }
            }
        }
        Thread synth = (new Thread(new SynthThread()));
        synth.setPriority(Thread.MAX_PRIORITY);
        synth.start();
    }

    private SoundResource getSoundResource(SpeechItem speechItem) {
        SoundResource sr = null;
        String text = speechItem.mText;
        if (speechItem.mType == SpeechItem.SILENCE) {
            // Do nothing if this is just silence
        } else if (speechItem.mType == SpeechItem.EARCON) {
            sr = mEarcons.get(text);
        } else {
            sr = mUtterances.get(text);
        }
        return sr;
    }

    private void broadcastTtsQueueProcessingCompleted() {
        Intent i = new Intent(TextToSpeechBeta.ACTION_TTS_QUEUE_PROCESSING_COMPLETED);
        sendBroadcast(i);
    }

    private void dispatchUtteranceCompletedCallback(String utteranceId, String packageName) {
        /* Legacy support for TTS */
        final int oldN = mCallbacksOld.beginBroadcast();
        for (int i = 0; i < oldN; i++) {
            try {
                mCallbacksOld.getBroadcastItem(i).markReached("");
            } catch (RemoteException e) {
                // The RemoteCallbackList will take care of removing
                // the dead object for us.
            }
        }
        try {
            mCallbacksOld.finishBroadcast();
        } catch (IllegalStateException e) {
            // May get an illegal state exception here if there is only
            // one app running and it is trying to quit on completion.
            // This is the exact scenario triggered by MakeBagel
            return;
        }
        /* End of legacy support for TTS */
        ITtsCallbackBeta cb = mCallbacksMap.get(packageName);
        if (cb == null) {
            return;
        }
        Log.v(SERVICE_TAG, "TTS callback: dispatch started");
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        try {
            cb.utteranceCompleted(utteranceId);
        } catch (RemoteException e) {
            // The RemoteCallbackList will take care of removing
            // the dead object for us.
        }
        mCallbacks.finishBroadcast();
        Log.v(SERVICE_TAG, "TTS callback: dispatch completed to " + N);
    }

    private SpeechItem splitCurrentTextIfNeeded(SpeechItem currentSpeechItem){
        if (currentSpeechItem.mText.length() < MAX_SPEECH_ITEM_CHAR_LENGTH){
            return currentSpeechItem;
        } else {
            String callingApp = currentSpeechItem.mCallingApp;
            ArrayList<SpeechItem> splitItems = new ArrayList<SpeechItem>();
            int start = 0;
            int end = start + MAX_SPEECH_ITEM_CHAR_LENGTH - 1;
            String splitText;
            SpeechItem splitItem;
            while (end < currentSpeechItem.mText.length()){
                splitText = currentSpeechItem.mText.substring(start, end);
                splitItem = new SpeechItem(callingApp, splitText, null, SpeechItem.TEXT);
                splitItems.add(splitItem);
                start = end;
                end = start + MAX_SPEECH_ITEM_CHAR_LENGTH - 1;
            }
            splitText = currentSpeechItem.mText.substring(start);
            splitItem = new SpeechItem(callingApp, splitText, null, SpeechItem.TEXT);
            splitItems.add(splitItem);
            mSpeechQueue.remove(0);
            for (int i = splitItems.size() - 1; i >= 0; i--){
                mSpeechQueue.add(0, splitItems.get(i));
            }
            return mSpeechQueue.get(0);
        }
    }

    private void processSpeechQueue() {
        boolean speechQueueAvailable = false;
        synchronized (this) {
            if (mSynthBusy){
                // There is already a synth thread waiting to run.
                return;
            }
        }
        try {
            speechQueueAvailable =
                    speechQueueLock.tryLock(SPEECHQUEUELOCK_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!speechQueueAvailable) {
                Log.e(SERVICE_TAG, "processSpeechQueue - Speech queue is unavailable.");
                return;
            }
            if (mSpeechQueue.size() < 1) {
                mIsSpeaking = false;
                mKillList.clear();
                broadcastTtsQueueProcessingCompleted();
                return;
            }

            mCurrentSpeechItem = mSpeechQueue.get(0);
            mIsSpeaking = true;
            SoundResource sr = getSoundResource(mCurrentSpeechItem);
            // Synth speech as needed - synthesizer should call
            // processSpeechQueue to continue running the queue
            Log.v(SERVICE_TAG, "TTS processing: " + mCurrentSpeechItem.mText);
            if (sr == null) {
                if (mCurrentSpeechItem.mType == SpeechItem.TEXT) {
                    mCurrentSpeechItem = splitCurrentTextIfNeeded(mCurrentSpeechItem);
                    speakInternalOnly(mCurrentSpeechItem);
                } else if (mCurrentSpeechItem.mType == SpeechItem.TEXT_TO_FILE) {
                    synthToFileInternalOnly(mCurrentSpeechItem);
                } else {
                    // This is either silence or an earcon that was missing
                    silence(mCurrentSpeechItem);
                }
            } else {
                cleanUpPlayer();
                if (sr.mSourcePackageName == PKGNAME) {
                    // Utterance is part of the TTS library
                    mPlayer = MediaPlayer.create(this, sr.mResId);
                } else if (sr.mSourcePackageName != null) {
                    // Utterance is part of the app calling the library
                    Context ctx;
                    try {
                        ctx = this.createPackageContext(sr.mSourcePackageName, 0);
                    } catch (NameNotFoundException e) {
                        e.printStackTrace();
                        mSpeechQueue.remove(0); // Remove it from the queue and
                        // move on
                        mIsSpeaking = false;
                        return;
                    }
                    mPlayer = MediaPlayer.create(ctx, sr.mResId);
                } else {
                    // Utterance is coming from a file
                    mPlayer = MediaPlayer.create(this, Uri.parse(sr.mFilename));
                }

                // Check if Media Server is dead; if it is, clear the queue and
                // give up for now - hopefully, it will recover itself.
                if (mPlayer == null) {
                    mSpeechQueue.clear();
                    mIsSpeaking = false;
                    return;
                }
                mPlayer.setOnCompletionListener(this);
                try {
                    mPlayer.setAudioStreamType(getStreamTypeFromParams(mCurrentSpeechItem.mParams));
                    mPlayer.start();
                } catch (IllegalStateException e) {
                    mSpeechQueue.clear();
                    mIsSpeaking = false;
                    cleanUpPlayer();
                    return;
                }
            }
            if (mSpeechQueue.size() > 0) {
                mSpeechQueue.remove(0);
            }
        } catch (InterruptedException e) {
            Log.e(SERVICE_TAG, "TTS processSpeechQueue: tryLock interrupted");
            e.printStackTrace();
        } finally {
            // This check is needed because finally will always run; even if the
            // method returns somewhere in the try block.
            if (speechQueueAvailable) {
                speechQueueLock.unlock();
            }
        }
    }

    private int getStreamTypeFromParams(ArrayList<String> paramList) {
        int streamType = DEFAULT_STREAM_TYPE;
        if (paramList == null) {
            return streamType;
        }
        for (int i = 0; i < paramList.size() - 1; i = i + 2) {
            String param = paramList.get(i);
            if ((param != null) && (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_STREAM))) {
                try {
                    streamType = Integer.parseInt(paramList.get(i + 1));
                } catch (NumberFormatException e) {
                    streamType = DEFAULT_STREAM_TYPE;
                }
            }
        }
        return streamType;
    }

    private void cleanUpPlayer() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }

    /**
     * Synthesizes the given text to a file using the specified parameters.
     * 
     * @param text
     *            The String of text that should be synthesized
     * @param params
     *            An ArrayList of parameters. The first element of this array
     *            controls the type of voice to use.
     * @param filename
     *            The string that gives the full output filename; it should be
     *            something like "/sdcard/myappsounds/mysound.wav".
     * @return A boolean that indicates if the synthesis can be started
     */
    private boolean synthesizeToFile(String callingApp, String text, ArrayList<String> params,
            String filename) {
        // Don't allow a filename that is too long
        if (filename.length() > MAX_FILENAME_LENGTH) {
            return false;
        }
        // Don't allow anything longer than the max text length; since this
        // is synthing to a file, don't even bother splitting it.
        if (text.length() >= MAX_SPEECH_ITEM_CHAR_LENGTH) {
            return false;
        }
        // Check that the output file can be created
        try {
            File tempFile = new File(filename);
            if (tempFile.exists()) {
                Log.v("TtsService", "File " + filename + " exists, deleting.");
                tempFile.delete();
            }
            if (!tempFile.createNewFile()) {
                Log.e("TtsService", "Unable to synthesize to file: can't create " + filename);
                return false;
            }
            tempFile.delete();
        } catch (IOException e) {
            Log.e("TtsService", "Can't create " + filename + " due to exception " + e);
            return false;
        }
        mSpeechQueue.add(new SpeechItem(callingApp, text, params, SpeechItem.TEXT_TO_FILE, filename));
        if (!mIsSpeaking) {
            processSpeechQueue();
        }
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            for (String category : intent.getCategories()) {
                if (category.equals(CATEGORY)) {
                    return mBinderOld;
                }
            }
        }
        if (BETA_ACTION.equals(intent.getAction())) {
            for (String category : intent.getCategories()) {
                if (category.equals(BETA_CATEGORY)) {
                    return mBinder;
                }
            }
        }
        return null;
    }

    private final ITtsBeta.Stub mBinder = new ITtsBeta.Stub() {

        public int registerCallback(String packageName, ITtsCallbackBeta cb) {
            if (cb != null) {
                mCallbacks.register(cb);
                mCallbacksMap.put(packageName, cb);
                return TextToSpeechBeta.SUCCESS;
            }
            return TextToSpeechBeta.ERROR;
        }

        public int unregisterCallback(String packageName, ITtsCallbackBeta cb) {
            if (cb != null) {
                mCallbacksMap.remove(packageName);
                mCallbacks.unregister(cb);
                return TextToSpeechBeta.SUCCESS;
            }
            return TextToSpeechBeta.ERROR;
        }

        /**
         * Speaks the given text using the specified queueing mode and
         * parameters.
         * 
         * @param text The text that should be spoken
         * @param queueMode TextToSpeech.TTS_QUEUE_FLUSH for no queue
         *            (interrupts all previous utterances)
         *            TextToSpeech.TTS_QUEUE_ADD for queued
         * @param params An ArrayList of parameters. The first element of this
         *            array controls the type of voice to use.
         */
        public int speak(String callingApp, String text, int queueMode, String[] params) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                speakingParams = new ArrayList<String>(Arrays.asList(params));
            }
            return mSelf.speak(callingApp, text, queueMode, speakingParams);
        }

        /**
         * Plays the earcon using the specified queueing mode and parameters.
         * 
         * @param earcon The earcon that should be played
         * @param queueMode TextToSpeech.TTS_QUEUE_FLUSH for no queue
         *            (interrupts all previous utterances)
         *            TextToSpeech.TTS_QUEUE_ADD for queued
         * @param params An ArrayList of parameters.
         */
        public int playEarcon(String callingApp, String earcon, int queueMode, String[] params) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                speakingParams = new ArrayList<String>(Arrays.asList(params));
            }
            return mSelf.playEarcon(callingApp, earcon, queueMode, speakingParams);
        }

        /**
         * Plays the silence using the specified queueing mode and parameters.
         * 
         * @param duration The duration of the silence that should be played
         * @param queueMode TextToSpeech.TTS_QUEUE_FLUSH for no queue
         *            (interrupts all previous utterances)
         *            TextToSpeech.TTS_QUEUE_ADD for queued
         * @param params An ArrayList of parameters.
         */
        public int playSilence(String callingApp, long duration, int queueMode, String[] params) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                speakingParams = new ArrayList<String>(Arrays.asList(params));
            }
            return mSelf.playSilence(callingApp, duration, queueMode, speakingParams);
        }

        /**
         * Stops all speech output and removes any utterances still in the
         * queue.
         */
        public int stop(String callingApp) {
            return mSelf.stop(callingApp);
        }

        /**
         * Returns whether or not the TTS is speaking.
         * 
         * @return Boolean to indicate whether or not the TTS is speaking
         */
        public boolean isSpeaking() {
            return (mSelf.mIsSpeaking && (mSpeechQueue.size() < 1));
        }

        /**
         * Adds a sound resource to the TTS.
         * 
         * @param text The text that should be associated with the sound
         *            resource
         * @param packageName The name of the package which has the sound
         *            resource
         * @param resId The resource ID of the sound within its package
         */
        public void addSpeech(String callingApp, String text, String packageName, int resId) {
            mSelf.addSpeech(callingApp, text, packageName, resId);
        }

        /**
         * Adds a sound resource to the TTS.
         * 
         * @param text The text that should be associated with the sound
         *            resource
         * @param filename The filename of the sound resource. This must be a
         *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
         */
        public void addSpeechFile(String callingApp, String text, String filename) {
            mSelf.addSpeech(callingApp, text, filename);
        }

        /**
         * Adds a sound resource to the TTS as an earcon.
         * 
         * @param earcon The text that should be associated with the sound
         *            resource
         * @param packageName The name of the package which has the sound
         *            resource
         * @param resId The resource ID of the sound within its package
         */
        public void addEarcon(String callingApp, String earcon, String packageName, int resId) {
            mSelf.addEarcon(callingApp, earcon, packageName, resId);
        }

        /**
         * Adds a sound resource to the TTS as an earcon.
         * 
         * @param earcon The text that should be associated with the sound
         *            resource
         * @param filename The filename of the sound resource. This must be a
         *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
         */
        public void addEarconFile(String callingApp, String earcon, String filename) {
            mSelf.addEarcon(callingApp, earcon, filename);
        }

        /**
         * Sets the speech rate for the TTS. Note that this will only have an
         * effect on synthesized speech; it will not affect pre-recorded speech.
         * 
         * @param speechRate The speech rate that should be used
         */
        public int setSpeechRate(String callingApp, int speechRate) {
            return mSelf.setSpeechRate(callingApp, speechRate);
        }

        /**
         * Sets the pitch for the TTS. Note that this will only have an effect
         * on synthesized speech; it will not affect pre-recorded speech.
         * 
         * @param pitch The pitch that should be used for the synthesized voice
         */
        public int setPitch(String callingApp, int pitch) {
            return mSelf.setPitch(callingApp, pitch);
        }

        /**
         * Returns the level of support for the specified language.
         * 
         * @param lang the three letter ISO language code.
         * @param country the three letter ISO country code.
         * @param variant the variant code associated with the country and
         *            language pair.
         * @return one of TTS_LANG_NOT_SUPPORTED, TTS_LANG_MISSING_DATA,
         *         TTS_LANG_AVAILABLE, TTS_LANG_COUNTRY_AVAILABLE,
         *         TTS_LANG_COUNTRY_VAR_AVAILABLE as defined in
         *         android.speech.tts.TextToSpeech.
         */
        public int isLanguageAvailable(String lang, String country, String variant,
                String[] params) {
            for (int i = 0; i < params.length - 1; i = i + 2){
                String param = params[i];
                if (param != null) {
                    if (param.equals(TextToSpeechBeta.Engine.KEY_PARAM_ENGINE)) {
                        mSelf.setEngine(params[i + 1]);
                        break;
                    }
                }
            }
            return mSelf.isLanguageAvailable(lang, country, variant);
        }

        /**
         * Returns the currently set language / country / variant strings
         * representing the language used by the TTS engine.
         * 
         * @return null is no language is set, or an array of 3 string
         *         containing respectively the language, country and variant.
         */
        public String[] getLanguage() {
            return mSelf.getLanguage();
        }

        /**
         * Sets the speech rate for the TTS, which affects the synthesized
         * voice.
         * 
         * @param lang the three letter ISO language code.
         * @param country the three letter ISO country code.
         * @param variant the variant code associated with the country and
         *            language pair.
         */
        public int setLanguage(String callingApp, String lang, String country, String variant) {
            return mSelf.setLanguage(callingApp, lang, country, variant);
        }

        /**
         * Synthesizes the given text to a file using the specified parameters.
         * 
         * @param text The String of text that should be synthesized
         * @param params An ArrayList of parameters. The first element of this
         *            array controls the type of voice to use.
         * @param filename The string that gives the full output filename; it
         *            should be something like
         *            "/sdcard/myappsounds/mysound.wav".
         * @return A boolean that indicates if the synthesis succeeded
         */
        public boolean synthesizeToFile(String callingApp, String text, String[] params,
                String filename) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                speakingParams = new ArrayList<String>(Arrays.asList(params));
            }
            return mSelf.synthesizeToFile(callingApp, text, speakingParams, filename);
        }

        /**
         * Sets the speech synthesis engine for the TTS by specifying its packagename
         *
         * @param packageName  the packageName of the speech synthesis engine (ie, "com.svox.pico")
         *
         * @return SUCCESS or ERROR as defined in android.speech.tts.TextToSpeech.
         */
        public int setEngineByPackageName(String packageName) {
            return mSelf.setEngine(packageName);
        }

        /**
         * Returns the packagename of the default speech synthesis engine.
         *
         * @return Packagename of the TTS engine that the user has chosen as their default.
         */
        public String getDefaultEngine() {
            return mSelf.getDefaultEngine();
        }

        /**
         * Returns whether or not the user is forcing their defaults to override the
         * Text-To-Speech settings set by applications.
         *
         * @return Whether or not defaults are enforced.
         */
        public boolean areDefaultsEnforced() {
            return mSelf.isDefaultEnforced();
        }

    };

    // ITTS can be entirely omitted when porting to the framework;
    // this exists solely to address the legacy issue of old apps
    // that are using the old version of the standalone TTS library.
    private final ITTS.Stub mBinderOld = new ITTS.Stub() {

        public void registerCallback(ITTSCallback cb) {
            if (cb != null)
                mCallbacksOld.register(cb);
        }

        public void unregisterCallback(ITTSCallback cb) {
            if (cb != null)
                mCallbacksOld.unregister(cb);
        }

        /**
         * Speaks the given text using the specified queueing mode and
         * parameters.
         * 
         * @param selectedEngine The TTS engine that should be used
         */
        public void setEngine(String selectedEngine) {
            TTSEngine theEngine;
            if (selectedEngine.equals(TTSEngine.TTS_ONLY.toString())) {
                // Deprecated, this case now does nothing!
                // theEngine = TTSEngine.TTS_ONLY;
            } else if (selectedEngine.equals(TTSEngine.PRERECORDED_WITH_TTS.toString())) {
                // Deprecated, this case now does nothing!
                // theEngine = TTSEngine.PRERECORDED_WITH_TTS;
            } else if (selectedEngine.equals(TTSEngine.PRERECORDED_ONLY.toString())) {
                // Deprecated, this case now does nothing!
                // theEngine = TTSEngine.PRERECORDED_ONLY;
            } else {
                if (selectedEngine.equals(TTSEngine.ESPEAK.toString())) {
                    mSelf.setEngine("/data/data/com.google.tts/lib/libespeakengine.so");
                } else if (selectedEngine.equals(TTSEngine.PICO.toString())) {
                    mSelf.setEngine("/system/lib/libttspico.so");
                } else {
                    mSelf.setEngine(selectedEngine);
                }
            }
        }

        /**
         * Speaks the given text using the specified queueing mode and
         * parameters.
         * 
         * @param text The text that should be spoken
         * @param queueMode 0 for no queue (interrupts all previous utterances),
         *            1 for queued
         * @param params An ArrayList of parameters. The first element of this
         *            array controls the type of voice to use.
         */
        public void speak(String text, int queueMode, String[] params) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                if (text.length() == 1) {
                    if (params[0].equals(TTSParams.VOICE_FEMALE.toString())) {
                        text = text + "[fem]";
                    }
                    if (params[0].equals(TTSParams.VOICE_ROBOT.toString())) {
                        text = text + "[robot]";
                    }
                }
            }
            speakingParams.add(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID);
            speakingParams.add("DEPRECATED");
            mSelf.speak("DEPRECATED", text, queueMode, speakingParams);
        }

        /**
         * Plays the earcon using the specified queueing mode and parameters.
         * 
         * @param earcon The earcon that should be played
         * @param queueMode 0 for no queue (interrupts all previous utterances),
         *            1 for queued
         * @param params An ArrayList of parameters.
         */
        public void playEarcon(String earcon, int queueMode, String[] params) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            speakingParams.add(TextToSpeechBeta.Engine.KEY_PARAM_UTTERANCE_ID);
            speakingParams.add("DEPRECATED");
            mSelf.playEarcon("DEPRECATED", earcon, queueMode, speakingParams);
        }

        /**
         * Stops all speech output and removes any utterances still in the
         * queue.
         */
        public void stop() {
            mSelf.stopAll("DEPRECATED");
        }

        /**
         * Returns whether or not the TTS is speaking.
         * 
         * @return Boolean to indicate whether or not the TTS is speaking
         */
        public boolean isSpeaking() {
            return (mSelf.mIsSpeaking && (mSpeechQueue.size() < 1));
        }

        /**
         * Adds a sound resource to the TTS.
         * 
         * @param text The text that should be associated with the sound
         *            resource
         * @param packageName The name of the package which has the sound
         *            resource
         * @param resId The resource ID of the sound within its package
         */
        public void addSpeech(String text, String packageName, int resId) {
            mSelf.addSpeech("DEPRECATED", text, packageName, resId);
        }

        /**
         * Adds a sound resource to the TTS.
         * 
         * @param text The text that should be associated with the sound
         *            resource
         * @param filename The filename of the sound resource. This must be a
         *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
         */
        public void addSpeechFile(String text, String filename) {
            mSelf.addSpeech("DEPRECATED", text, filename);
        }

        /**
         * Adds a sound resource to the TTS as an earcon.
         * 
         * @param earcon The text that should be associated with the sound
         *            resource
         * @param packageName The name of the package which has the sound
         *            resource
         * @param resId The resource ID of the sound within its package
         */
        public void addEarcon(String earcon, String packageName, int resId) {
            mSelf.addEarcon("DEPRECATED", earcon, packageName, resId);
        }

        /**
         * Adds a sound resource to the TTS as an earcon.
         * 
         * @param earcon The text that should be associated with the sound
         *            resource
         * @param filename The filename of the sound resource. This must be a
         *            complete path like: (/sdcard/mysounds/mysoundbite.mp3).
         */
        public void addEarconFile(String earcon, String filename) {
            mSelf.addEarcon("DEPRECATED", earcon, filename);
        }

        /**
         * Sets the speech rate for the TTS. Note that this will only have an
         * effect on synthesized speech; it will not affect pre-recorded speech.
         * 
         * @param speechRate The speech rate that should be used
         */
        public void setSpeechRate(int speechRate) {
            // TODO: Make sure the values make sense here
            mSelf.setSpeechRate("DEPRECATED", speechRate);
        }

        /**
         * Sets the speech rate for the TTS. Note that this will only have an
         * effect on synthesized speech; it will not affect pre-recorded speech.
         * 
         * @param language The language to be used. The languages are specified
         *            by their IETF language tags as defined by BCP 47. This is
         *            the same standard used for the lang attribute in HTML.
         *            See: http://en.wikipedia.org/wiki/IETF_language_tag
         */
        public void setLanguage(String language) {
            Locale loc;
            if (language.length() == 3) {
                loc = new Locale(language);
                // mSelf.setLanguage("DEPRECATED", loc.getISO3Language(), "",
                // "");
                mSelf.setLanguage("DEPRECATED", language, "", "");
                return;
            }
            if (language.length() == 7) {
                loc = new Locale(language.substring(0, 3), language.substring(4, 7));
                // mSelf.setLanguage("DEPRECATED", loc.getISO3Language(),
                // loc.getISO3Country(), "");
                mSelf.setLanguage("DEPRECATED", language.substring(0, 3), language.substring(4, 7),
                        "");
                return;
            }

            String isoLocale = langRegionToLocale.get(language);
            String lang = "";
            String country = "";
            String variant = "";
            if (isoLocale == null) {
                Log.e(SERVICE_TAG, "Error: " + language + " not supported.");
                return;
            }
            if (isoLocale.length() > 2) {
                lang = isoLocale.substring(0, 3);
            }
            if (isoLocale.length() > 6) {
                country = isoLocale.substring(4, 7);
            }
            if (isoLocale.length() > 8) {
                variant = isoLocale.substring(8);
            }

            loc = new Locale(lang, country, variant);
            // mSelf.setLanguage("DEPRECATED", loc.getISO3Language(),
            // loc.getISO3Country(), loc.getVariant());
            mSelf.setLanguage("DEPRECATED", lang, country, variant);
        }

        /**
         * Returns the version number of the TTS This version number is the
         * versionCode in the AndroidManifest.xml
         * 
         * @return The version number of the TTS
         */
        public int getVersion() {
            PackageInfo pInfo = new PackageInfo();
            try {
                PackageManager pm = mSelf.getPackageManager();
                pInfo = pm.getPackageInfo(mSelf.getPackageName(), 0);
            } catch (NameNotFoundException e) {
                // Ignore this exception - the packagename is itself, can't fail
                // here
                e.printStackTrace();
            }
            return pInfo.versionCode;
        }

        /**
         * Speaks the given text using the specified queueing mode and
         * parameters.
         * 
         * @param text The String of text that should be synthesized
         * @param params An ArrayList of parameters. The first element of this
         *            array controls the type of voice to use.
         * @param filename The string that gives the full output filename; it
         *            should be something like
         *            "/sdcard/myappsounds/mysound.wav".
         * @return A boolean that indicates if the synthesis succeeded
         */
        public boolean synthesizeToFile(String text, String[] params, String filename) {
            ArrayList<String> speakingParams = new ArrayList<String>();
            if (params != null) {
                speakingParams = new ArrayList<String>(Arrays.asList(params));
            }
            boolean success = mSelf.synthesizeToFile("DEPRECATED", text, speakingParams, filename);
            // Simulate the blocking behavior from before
            if (success) {
                deprecatedKeepBlockingFlag = true;
                while (deprecatedKeepBlockingFlag)
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
            }

            return success;
        }
    };
}
