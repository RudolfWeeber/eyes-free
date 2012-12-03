/*
 * Copyright (C) 2011 Google Inc.
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

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.marvin.utils.ProximitySensor;
import com.google.android.marvin.utils.ProximitySensor.ProximityChangeListener;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils.EngineCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Handles text-to-speech.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class SpeechController {
    /**
     * Utterances must be no longer than MAX_UTTERANCE_LENGTH for the TTS to be
     * able to handle them properly. See MAX_SPEECH_ITEM_CHAR_LENGTH in //
     * android/frameworks/base/core/java/android/speech/tts/TextToSpeechService.java
     */
    private static final int MAX_UTTERANCE_LENGTH = 3999;

    /** Prefix for utterance IDs. */
    private static final String UTTERANCE_ID_PREFIX = "talkback_";

    /** Number of times a TTS engine can fail before switching. */
    private static final int MAX_TTS_FAILURES = 3;

    /** Constant to flush speech globally. */
    private static final int SPEECH_FLUSH_ALL = 2;

    /** Default stream for speech output. */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    /** Flag to prevent speech from being read as the "last" utterance. */
    public static final int FLAG_NO_HISTORY = 0x2;

    public static final int QUEUE_MODE_INTERRUPT = 0;
    public static final int QUEUE_MODE_QUEUE = 1;
    public static final int QUEUE_MODE_UNINTERRUPTIBLE = 2;
    public static final int QUEUE_MODE_FLUSH_ALL = 3;

    public static class SpeechParam {
        /** Float parameter for controlling speech pan. Range is {-1 ... 1}. */
        public static final String PAN = EngineCompatUtils.KEY_PARAM_PAN;

        /** Float parameter for controlling speech volume. Range is {0 ... 2}. */
        public static final String VOLUME = EngineCompatUtils.KEY_PARAM_VOLUME;

        /** Float parameter for controlling speech rate. Range is {0 ... 2}. */
        public static final String RATE = "rate";

        /** Float parameter for controlling speech pitch. Range is {0 ... 2}. */
        public static final String PITCH = "pitch";
    }

    /**
     * Reusable map used for passing parameters to the TextToSpeech.
     */
    private final HashMap<String, String> mSpeechParametersMap = new HashMap<String, String>();

    /**
     * Array of actions to perform when an utterance completes.
     */
    private final ArrayList<UtteranceCompleteAction> mUtteranceCompleteActions =
            new ArrayList<UtteranceCompleteAction>();

    /** A list of installed TTS engines. */
    private final LinkedList<String> mInstalledTtsEngines = new LinkedList<String>();

    /**
     * {@link BroadcastReceiver} for determining changes in the media state used
     * for switching the TTS engine.
     */
    private final MediaMountStateMonitor mMediaStateMonitor = new MediaMountStateMonitor();

    /** The parent service. */
    private final TalkBackService mService;

    /** Proximity sensor for implementing "shut up" functionality. */
    private ProximitySensor mProximitySensor;

    /** Whether to use the proximity sensor to silence speech. */
    private boolean mSilenceOnProximity;

    /** Whether we expect the proximity sensor to be enabled. */
    private boolean mRequestedProximityState;

    /** Whether or not the screen is on. */
    // This is set by RingerModeAndScreenMonitor and used by SpeechController
    // to determine if the ProximitySensor should be on or off.
    private boolean mScreenIsOn;

    /** The last spoken utterance. */
    private CharSequence mLastSpokenUtterance;

    /** The TTS engine. */
    private TextToSpeech mTts;

    /** The engine loaded into the current TTS. */
    private String mTtsEngine;

    /** The number of time the current TTS has failed. */
    private int mTtsFailures;

    /** The package name of the preferred TTS engine. */
    private String mDefaultTtsEngine;

    /** The package name of the system TTS engine. */
    private String mSystemTtsEngine;

    /** A temporary TTS used for switching engines. */
    private TextToSpeech mTempTts;

    /** The engine loading into the temporary TTS. */
    private String mTempTtsEngine;

    /** The text-to-speech screen overlay. */
    private TextToSpeechOverlay mTtsOverlay;

    /** Whether the TTS is currently speaking an uninterruptible utterance. */
    private boolean mUninterruptible;

    /**
     * Whether the speech controller should add utterance callbacks to
     * FullScreenReadController
     */
    private boolean mInjectFullScreenReadCallbacks;

    /** The utterance completed callback for FullScreenReadController */
    private Runnable mFullScreenReadNextCallback;

    /**
     * The next utterance index; each utterance id will be constructed from this
     * ever-increasing index.
     */
    private int mNextUtteranceIndex = 0;

    /** Whether rate and pitch can change. */
    private boolean mIntonationEnabled;

    /** The speech volume (out of 100). */
    private int mSpeechVolume;

    private float mCurrentRate = -1.0f;
    private float mCurrentPitch = -1.0f;

    private float mDefaultRate;
    private float mDefaultPitch;

    private CharSequence mPendingText;
    private int mPendingQueueMode;
    private int mPendingFlags;
    private Bundle mPendingParams;
    private Runnable mPendingCompletedAction;

    public SpeechController(TalkBackService context) {
        mService = context;
        mService.registerReceiver(mMediaStateMonitor, mMediaStateMonitor.getFilter());

        mUninterruptible = false;
        mInjectFullScreenReadCallbacks = false;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        final Resources res = mService.getResources();

        manageTtsOverlayEnabled(res, prefs);
        manageIntonationEnabled(res, prefs);
        manageSpeechVolume(res, prefs);

        final ContentResolver resolver = context.getContentResolver();

        // Updating the default engine reloads the list of installed engines and
        // the system engine. This also loads the default engine.
        updateDefaultEngine(resolver);
        updateDefaultPitch(resolver);
        updateDefaultRate(resolver);

        final Uri defaultSynth = Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH);
        final Uri defaultPitch = Secure.getUriFor(Secure.TTS_DEFAULT_PITCH);
        final Uri defaultRate = Secure.getUriFor(Secure.TTS_DEFAULT_RATE);

        resolver.registerContentObserver(defaultSynth, false, mSynthObserver);
        resolver.registerContentObserver(defaultPitch, false, mPitchObserver);
        resolver.registerContentObserver(defaultRate, false, mRateObserver);

        mScreenIsOn = true;
    }

    /**
     * Repeats the last spoken utterance.
     */
    public void repeatLastUtterance() {
        if (mLastSpokenUtterance == null) {
            return;
        }

        final String utterance = mLastSpokenUtterance.toString();

        speakWithFailover(utterance, TextToSpeech.QUEUE_FLUSH, null);
    }

    /**
     * Spells the last spoken utterance.
     */
    public void spellLastUtterance() {
        if (mLastSpokenUtterance == null) {
            return;
        }

        final CharSequence lastUtterance = mLastSpokenUtterance;
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < lastUtterance.length(); i++) {
            final CharSequence character = Character.toString(lastUtterance.charAt(i));
            final CharSequence cleanedChar = SpeechCleanupUtils.cleanUp(mService, character);

            StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
        }

        final String utterance = builder.toString();

        speakWithFailover(utterance, TextToSpeech.QUEUE_FLUSH, null);
    }

    /**
     * @see #cleanUpAndSpeak(CharSequence, int, int, Bundle, Runnable)
     */
    public void cleanUpAndSpeak(CharSequence text, int queueMode, int flags, Bundle params) {
        cleanUpAndSpeak(text, queueMode, flags, params, null);
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The <code>queueMode</code> determines
     * whether the speech will interrupt or wait on queued speech events.
     * <p>
     * This method does nothing if the text to speak is empty. See
     * {@link TextUtils#isEmpty(CharSequence)} for implementation.
     * </p>
     * <p>
     * See {@link SpeechCleanupUtils#cleanUp(Context, CharSequence)} for text
     * clean-up implementation.
     * </p>
     *
     * @param text The text to speak.
     * @param queueMode The queue mode to use for speaking. One of:
     *            <ul>
     *            <li>{@link #QUEUE_MODE_INTERRUPT}
     *            <li>{@link #QUEUE_MODE_QUEUE}
     *            <li>{@link #QUEUE_MODE_UNINTERRUPTIBLE}
     *            </ul>
     * @param flags Bit mask of speaking flags. Use {@code 0} for no flags, or a combination of:
     *            <ul>
     *            <li>{@link #FLAG_NO_HISTORY}</li>
     *            </ul>
     * @param params Speaking parameters. Not all parameters are supported by
     *            all engines. One of:
     *            <ul>
     *            <li>{@link SpeechParam#PAN}
     *            <li>{@link SpeechParam#PITCH}
     *            <li>{@link SpeechParam#RATE}
     *            <li>{@link SpeechParam#VOLUME}
     *            </ul>
     * @param completedAction The action to run after this utterance has been
     *            spoken.
     */
    public void cleanUpAndSpeak(CharSequence text, int queueMode, int flags,
            Bundle params, Runnable completedAction) {
        if (mTts == null) {
            mPendingText = text;
            mPendingQueueMode = queueMode;
            mPendingFlags = flags;
            mPendingParams = params;
            mPendingCompletedAction = completedAction;
            LogUtils.log(this, Log.ERROR, "Attempted to speak before TTS was initialized.");
            return;
        }

        if (text == null) {
            // If full screen reading is enabled, advance to the next node.
            if (mInjectFullScreenReadCallbacks) {
                mFullScreenReadNextCallback.run();
            }
            return;
        }

        final CharSequence trimmedText = text.toString().trim();

        if (trimmedText.length() == 0) {
            // Don't speak empty text.  Advance to the next node if full screen reading is enabled.
            if (mInjectFullScreenReadCallbacks) {
                mFullScreenReadNextCallback.run();
            }
            return;
        }

        if (params == null) {
            params = Bundle.EMPTY;
        }

        // Attempt to clean up the text.
        final CharSequence cleanedText = SpeechCleanupUtils.cleanUp(mService, trimmedText);

        if ((flags & FLAG_NO_HISTORY) != FLAG_NO_HISTORY) {
            mLastSpokenUtterance = cleanedText;
        }

        // Give every utterance an unique identifier with an increasing index.
        final int utteranceIndex = getNextUtteranceId();
        final String utteranceId = UTTERANCE_ID_PREFIX + utteranceIndex;

        if (completedAction != null) {
            addUtteranceCompleteAction(utteranceIndex, completedAction);
        }

        if (mInjectFullScreenReadCallbacks) {
            addUtteranceCompleteAction(utteranceIndex, mFullScreenReadNextCallback);
        }

        // Reuse the global instance of speech parameters.
        final HashMap<String, String> speechParams = mSpeechParametersMap;
        speechParams.clear();

        // Add all custom parameters.
        if (params != null) {
            for (String key : params.keySet()) {
                final Object value = params.get(key);
                speechParams.put(key, String.valueOf(value));
            }
        }

        // The utterance ID overwrites whatever the params may have set.
        speechParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

        final int ttsQueueMode = computeQueuingMode(queueMode, utteranceIndex);

        LogUtils.log(this, Log.VERBOSE, "Speaking with queue mode %d: \"%s\"",
                queueMode, cleanedText);

        speakWithFailover(cleanedText.toString(), ttsQueueMode, speechParams);
    }

    /**
     * Returns the next utterance identifier.
     */
    public int peekNextUtteranceId() {
        return mNextUtteranceIndex;
    }

    /**
     * Returns the next utterance iterator and increments the utterance id.
     *
     * @return The next utterance identifier.
     */
    private int getNextUtteranceId() {
        return mNextUtteranceIndex++;
    }

    /**
     * Add a new action that will be run when the given utterance index
     * completes.
     *
     * @param utteranceIndex The index of the utterance that should finish
     *            before this action is executed.
     * @param runnable The code to execute.
     */
    public void addUtteranceCompleteAction(int utteranceIndex, Runnable runnable) {
        synchronized (mUtteranceCompleteActions) {
            final UtteranceCompleteAction action =
                    new UtteranceCompleteAction(utteranceIndex, runnable);

            mUtteranceCompleteActions.add(action);
        }
    }

    /**
     * Removes all instance of the specified runnable from the utterance
     * complete action list.
     *
     * @param runnable The runnable to remove.
     */
    public void removeUtteranceCompleteAction(Runnable runnable) {
        synchronized (mUtteranceCompleteActions) {
            final Iterator<UtteranceCompleteAction> i = mUtteranceCompleteActions.iterator();

            while (i.hasNext()) {
                final UtteranceCompleteAction action = i.next();

                if (action.runnable == runnable) {
                    i.remove();
                }
            }
        }
    }

    /**
     * Stops all speech.
     */
    public void interrupt() {
        try {
            mTts.speak("", SPEECH_FLUSH_ALL, null);
        } catch (Exception e) {
            // Don't care, we're not speaking.
        }
        setProximitySensorState(mScreenIsOn);

        // Ensure all pending completion actions happen.
        handleUtteranceCompleted(UTTERANCE_ID_PREFIX + Integer.MAX_VALUE, false);
    }

    /**
     * Stops speech and shuts down this controller.
     */
    public void shutdown() {
        interrupt();

        attemptTtsShutdown(mTts);

        if (mMediaStateMonitor != null) {
            mService.unregisterReceiver(mMediaStateMonitor);
        }

        final ContentResolver resolver = mService.getContentResolver();
        resolver.unregisterContentObserver(mSynthObserver);
        resolver.unregisterContentObserver(mPitchObserver);
        resolver.unregisterContentObserver(mRateObserver);

        manageTtsOverlayEnabled(null, null);

        setProximitySensorState(false);
    }

    /**
     * Manages whether speech output will be displayed on-screen.
     *
     * @param res Activity resources.
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to disable the overlay.
     */
    private void manageTtsOverlayEnabled(Resources res, SharedPreferences prefs) {
        final boolean ttsOverlayEnabled;

        if (prefs != null && res != null) {
            ttsOverlayEnabled = SharedPreferencesUtils.getBooleanPref(prefs, res,
                    R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default);
        } else {
            ttsOverlayEnabled = false;
        }

        if (ttsOverlayEnabled && mTtsOverlay == null) {
            mTtsOverlay = new TextToSpeechOverlay(mService);
        } else if (!ttsOverlayEnabled && mTtsOverlay != null) {
            mTtsOverlay.hide();
            mTtsOverlay = null;
        }
    }

    /**
     * Manages whether pitch will be changed to convey extra information.
     *
     * @param res Activity resources.
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to disable intonation.
     */
    private void manageIntonationEnabled(Resources res, SharedPreferences prefs) {
        if (prefs != null && res != null) {
            mIntonationEnabled = SharedPreferencesUtils.getBooleanPref(prefs, res,
                    R.string.pref_intonation_key, R.bool.pref_intonation_default);
        } else {
            mIntonationEnabled = false;
        }
    }

    /**
     * Manages the relative speech volume.
     *
     * @param res Activity resources.
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to set the default volume.
     */
    private void manageSpeechVolume(Resources res, SharedPreferences prefs) {
        if (prefs != null && res != null) {
            mSpeechVolume = SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                    R.string.pref_speech_volume_key, R.string.pref_speech_volume_default);
        } else {
            mSpeechVolume = 100;
        }
    }

    /**
     * Speaks the specified utterance. Attempts to switch TTS engines if
     * speaking throws an {@link Exception}.
     *
     * @param utterance
     * @param queueMode
     * @param speechParams
     */
    private void speakWithFailover(String utterance, int queueMode,
            HashMap<String, String> speechParams) {
        if (mTts == null) {
            LogUtils.log(this, Log.ERROR, "Attempted to speak before TTS was initialized.");
            return;
        }

        if (speechParams == null) {
            speechParams = mSpeechParametersMap;
            speechParams.clear();
        }

        // Make sure to set an utterance ID so that we get a callback.
        if (!speechParams.containsKey(Engine.KEY_PARAM_UTTERANCE_ID)) {
            final String utteranceId = UTTERANCE_ID_PREFIX + getNextUtteranceId();
            speechParams.put(Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        }

        // Set the default output stream.
        speechParams.put(Engine.KEY_PARAM_STREAM, "" + DEFAULT_STREAM);
        speechParams.put(Engine.KEY_PARAM_VOLUME, "" + (mSpeechVolume / 100.0f));

        final float pitch;
        final float rate;

        if (mIntonationEnabled) {
            pitch = parseFloatParam(speechParams, SpeechParam.PITCH, 1.0f) * mDefaultPitch;
            rate = parseFloatParam(speechParams, SpeechParam.RATE, 1.0f) * mDefaultRate;
        } else {
            pitch = mDefaultPitch;
            rate = mDefaultRate;
        }

        final boolean needsAdjustment = (pitch != mCurrentPitch) || (rate != mCurrentRate);

        try {
            // TODO(alanv): We should postpone adjustment if the queue mode is ADD.
            if (needsAdjustment) {
                mTts.stop();
                mTts.setPitch(pitch);
                mTts.setSpeechRate(rate);

                mCurrentPitch = pitch;
                mCurrentRate = rate;
            }

            // Split long utterances to avoid killing TTS. TTS will die if
            // the incoming string is greater than 3999 characters.
            ArrayList<String> speakableUtterances = null;
            if (utterance.length() > MAX_UTTERANCE_LENGTH) {
                speakableUtterances = splitUtterancesIntoSpeakableStrings(utterance);
                utterance = speakableUtterances.get(0);
            }

            // TODO(alanv): Most applications don't know how to duck audio!
            // AudioManagerCompatUtils.requestAudioFocus(mAudioManager, null,
            // AudioManager.STREAM_MUSIC,
            // AudioManagerCompatUtils.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            final int result = mTts.speak(utterance, queueMode, speechParams);
            if (speakableUtterances != null) {
                for (int i = 1; i < speakableUtterances.size(); i++) {
                    mTts.speak(speakableUtterances.get(i), TextToSpeech.QUEUE_ADD, speechParams);
                }
            }
            // Always enable the proximity sensor after speaking.
            setProximitySensorState(true);

            if (result != TextToSpeech.SUCCESS) {
                // Treat the utterance as completed since we won't get a
                // callback.
                final String utteranceId = speechParams.get(Engine.KEY_PARAM_UTTERANCE_ID);
                handleUtteranceCompleted(utteranceId, false);

                // TTS engine failed, we should try another.
                attemptTtsFailover(mTtsEngine);
            }
        } catch (Exception e) {
            e.printStackTrace();

            // TTS engine crashed, we should try another.
            attemptTtsFailover(mTtsEngine);
            return;
        }

        if (mTtsOverlay != null) {
            mTtsOverlay.speak(utterance);
        }
    }

    /**
     * Splits long utterances up into shorter utterances. This is needed because
     * the Android TTS does not support Strings that are longer than 3999
     * characters.
     *
     * @param utterance The original utterance.
     * @return An ArrayList where the original utterance has been broken up into
     *         Strings that are no longer than 3999 characters.
     */
    private ArrayList<String> splitUtterancesIntoSpeakableStrings(String utterance) {
        ArrayList<String> speakableUtterances = new ArrayList<String>();
        while (utterance.length() > MAX_UTTERANCE_LENGTH) {
            int splitLocation = utterance.lastIndexOf(" ", MAX_UTTERANCE_LENGTH);
            if (splitLocation == -1) {
                splitLocation = MAX_UTTERANCE_LENGTH;
            }
            speakableUtterances.add(utterance.substring(0, splitLocation));
            utterance = utterance.substring(splitLocation);
        }
        speakableUtterances.add(utterance);
        return speakableUtterances;
    }

    /**
     * Returns the TTS queuing mode based on the requested queuing mode and the
     * speech controller's state.
     *
     * @param queueMode The requested queuing mode.
     * @param utteranceIndex The index for this utterance.
     * @return A TTS queuing mode.
     */
    private int computeQueuingMode(int queueMode, int utteranceIndex) {
        final int ttsQueueMode;

        if (queueMode == QUEUE_MODE_UNINTERRUPTIBLE) {
            ttsQueueMode = TextToSpeech.QUEUE_FLUSH;

            // Set uninterruptible to true.
            mUninterruptible = true;
            removeUtteranceCompleteAction(mClearUninterruptible);
            addUtteranceCompleteAction(utteranceIndex, mClearUninterruptible);
        } else if (queueMode == QUEUE_MODE_INTERRUPT && !mUninterruptible) {
            ttsQueueMode = TextToSpeech.QUEUE_FLUSH;
        } else if (queueMode == QUEUE_MODE_FLUSH_ALL && !mUninterruptible) {
            ttsQueueMode = SPEECH_FLUSH_ALL;
        } else {
            ttsQueueMode = TextToSpeech.QUEUE_ADD;

            // Clear uninterruptible.
            mUninterruptible = false;
            removeUtteranceCompleteAction(mClearUninterruptible);
        }

        return ttsQueueMode;
    }

    /**
     * Method that's called by TTS whenever an utterance is completed. Do common
     * tasks and execute any UtteranceCompleteActions associate with this
     * utterance index (or an earlier index, in case one was accidentally
     * dropped).
     *
     * @param utteranceId The utteranceId from the onUtteranceCompleted callback
     *            - we expect this to consist of UTTERANCE_ID_PREFIX followed by
     *            the utterance index.
     * @param success {@code true} if the utterance was spoken successfully.
     */
    private void handleUtteranceCompleted(String utteranceId, boolean success) {
        if (success) {
            mTtsFailures = 0;
        }

        if (!utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
            LogUtils.log(this, Log.ERROR, "Utterance has wrong prefix: %s", utteranceId);
            return;
        }

        final int utteranceIndex;

        try {
            utteranceIndex = Integer.parseInt(utteranceId.substring(UTTERANCE_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return;
        }

        synchronized (mUtteranceCompleteActions) {
            final Iterator<UtteranceCompleteAction> iterator = mUtteranceCompleteActions.iterator();

            while (iterator.hasNext()) {
                final UtteranceCompleteAction action = iterator.next();

                if (action.utteranceIndex <= utteranceIndex) {
                    mHandler.post(action.runnable);
                    iterator.remove();
                }
            }
        }

        // Handle audio focus.
        if ((mNextUtteranceIndex - 1) == utteranceIndex) {
            handleAllUtterancesCompleted();
        }
    }

    /**
     * Called when all queued utterances have been spoken.
     */
    private void handleAllUtterancesCompleted() {
        // TODO(alanv): Most applications don't know how to duck audio!
        // AudioManagerCompatUtils.abandonAudioFocus(mAudioManager, null);
    }

    /**
     * Handles media state changes.
     *
     * @param action The current media state.
     */
    private void handleMediaStateChanged(String action) {
        if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            if (!TextUtils.equals(mSystemTtsEngine, mTtsEngine)) {
                // Temporarily switch to the system TTS engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media unmount");
                setTtsEngine(mSystemTtsEngine, true);
            }
        }

        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            if (!TextUtils.equals(mDefaultTtsEngine, mTtsEngine)) {
                // Try to switch back to the default engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media mount");
                setTtsEngine(mDefaultTtsEngine, true);
            }
        }
    }

    /**
     * Try to switch the TTS engine.
     *
     * @param engine The package name of the desired TTS engine
     */
    private void setTtsEngine(String engine, boolean resetFailures) {
        if (resetFailures) {
            mTtsFailures = 0;
        }

        // Always try to stop the current engine before switching.
        interrupt();

        if (mTempTts != null) {
            LogUtils.log(SpeechController.class, Log.ERROR,
                    "Can't start TTS engine %s while still loading previous engine", engine);
            return;
        }

        // If this is an old version of Android, we need to use a deprecated
        // method to switch the engine.
        if ((mTts != null) && (Build.VERSION.SDK_INT < 14) && (Build.VERSION.SDK_INT > 8)) {
            setEngineByPackageName_GB_HC(engine);
            return;
        }

        LogUtils.log(SpeechController.class, Log.INFO, "Starting TTS engine: %s", engine);

        mTempTtsEngine = engine;
        mTempTts = TextToSpeechCompatUtils.newTextToSpeech(mService, mTtsChangeListener, engine);
    }

    /**
     * Attempts to set the engine by package name. Supported in GB and HC.
     *
     * @param engine The name of the engine to use.
     */
    private void setEngineByPackageName_GB_HC(String engine) {
        mTempTtsEngine = engine;
        mTempTts = mTts;

        final int status = TextToSpeechCompatUtils.setEngineByPackageName(mTts, mTempTtsEngine);
        mTtsChangeListener.onInit(status);
    }

    /**
     * Assumes the current engine has failed and attempts to start the next
     * available engine.
     *
     * @param failedEngine The package name of the engine to switch from.
     */
    private void attemptTtsFailover(String failedEngine) {
        LogUtils.log(
                SpeechController.class, Log.ERROR, "Attempting TTS failover from %s", failedEngine);

        mTtsFailures++;

        // If there is only one installed engine, or if the current engine
        // hasn't failed enough times, just restart the current engine.
        if ((mInstalledTtsEngines.size() <= 1) || (mTtsFailures < MAX_TTS_FAILURES)) {
            setTtsEngine(failedEngine, false);
            return;
        }

        // Move the engine to the back of the list.
        if (failedEngine != null) {
            mInstalledTtsEngines.remove(failedEngine);
            mInstalledTtsEngines.addLast(failedEngine);
        }

        // Try to use the first available TTS engine.
        final String nextEngine = mInstalledTtsEngines.getFirst();

        setTtsEngine(nextEngine, true);
    }

    /**
     * Handles TTS engine initialization.
     *
     * @param status The status returned by the TTS engine.
     */
    @SuppressWarnings("deprecation")
    private void handleTtsInitialized(int status) {
        if (mTempTts == null) {
            LogUtils.log(this, Log.ERROR, "Attempted to initialize TTS more than once!");
            return;
        }

        final TextToSpeech tempTts = mTempTts;
        final String tempTtsEngine = mTempTtsEngine;

        mTempTts = null;
        mTempTtsEngine = null;

        if (status != TextToSpeech.SUCCESS) {
            attemptTtsFailover(tempTtsEngine);
            return;
        }

        final boolean isSwitchingEngines = (mTts != null);

        if (isSwitchingEngines) {
            attemptTtsShutdown(mTts);
        }

        mTts = tempTts;
        mTts.setOnUtteranceCompletedListener(mUtteranceCompletedListener);

        mTtsEngine = tempTtsEngine;

        LogUtils.log(SpeechController.class, Log.INFO, "Switched to TTS engine: %s", tempTtsEngine);

        if (isSwitchingEngines) {
            speakCurrentEngine();
        } else if (mPendingText != null) {
            speakAndClearPendingUtterance();
        }
    }

    /**
     * Removes and speaks the pending utterance.
     */
    private void speakAndClearPendingUtterance() {
        final CharSequence text = mPendingText;
        final Bundle params = mPendingParams;
        final Runnable completedAction = mPendingCompletedAction;

        mPendingText = null;
        mPendingParams = null;
        mPendingCompletedAction = null;

        cleanUpAndSpeak(text, mPendingQueueMode, mPendingFlags, params, completedAction);
    }

    /**
     * Speaks the name of the currently active TTS engine.
     */
    private void speakCurrentEngine() {
        final CharSequence engineLabel = getLabelForEngine(mService, mTtsEngine);

        if (engineLabel == null) {
            return;
        }

        final String utterance =
                mService.getString(R.string.template_current_tts_engine, engineLabel);

        speakWithFailover(utterance, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void updateDefaultEngine(ContentResolver resolver) {
        // Always refresh the list of available engines, since the user may have
        // installed a new TTS and then switched to it.
        reloadInstalledTtsEngines();

        // This may be null if the user hasn't specified an engine.
        mDefaultTtsEngine = Secure.getString(resolver, Secure.TTS_DEFAULT_SYNTH);

        // Always switch engines when the system default changes.
        setTtsEngine(mDefaultTtsEngine, true);
    }

    private void updateDefaultPitch(ContentResolver resolver) {
        mDefaultPitch = Secure.getInt(resolver, Secure.TTS_DEFAULT_PITCH, 100) / 100.0f;
    }

    private void updateDefaultRate(ContentResolver resolver) {
        mDefaultRate = Secure.getInt(resolver, Secure.TTS_DEFAULT_RATE, 100) / 100.0f;
    }

    /**
     * Reloads the list of installed TTS engines.
     */
    private void reloadInstalledTtsEngines() {
        final PackageManager pm = mService.getPackageManager();

        mInstalledTtsEngines.clear();

        // ICS and pre-ICS have different ways of enumerating installed TTS
        // services.
        if (Build.VERSION.SDK_INT >= 14) {
            reloadInstalledTtsEngines_ICS(pm);
        } else {
            reloadInstalledTtsEngines_HC(pm);
        }

        LogUtils.log(this, Log.INFO, "Found %d TTS engines. System engine is %s.",
                mInstalledTtsEngines.size(), mSystemTtsEngine);
    }

    /**
     * Reloads the list of installed engines for devices running API 14 or
     * above.
     *
     * @param pm The package manager.
     */
    private void reloadInstalledTtsEngines_ICS(PackageManager pm) {
        final Intent intent = new Intent(EngineCompatUtils.INTENT_ACTION_TTS_SERVICE);
        final List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                intent, PackageManager.GET_SERVICES);

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
            final ApplicationInfo appInfo = serviceInfo.applicationInfo;
            final String packageName = serviceInfo.packageName;
            final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            mInstalledTtsEngines.add(serviceInfo.packageName);

            if (isSystemApp) {
                mSystemTtsEngine = packageName;
            }
        }
    }

    /**
     * Reloads the list of installed engines for devices running API 13 or
     * below.
     *
     * @param pm The package manager.
     */
    private void reloadInstalledTtsEngines_HC(PackageManager pm) {
        final Intent intent = new Intent("android.intent.action.START_TTS_ENGINE");
        final List<ResolveInfo> resolveInfos = pm.queryIntentActivities(
                intent, PackageManager.GET_ACTIVITIES);

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ActivityInfo activityInfo = resolveInfo.activityInfo;
            final ApplicationInfo appInfo = activityInfo.applicationInfo;
            final String packageName = activityInfo.packageName;
            final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

            mInstalledTtsEngines.add(packageName);

            if (isSystemApp) {
                mSystemTtsEngine = packageName;
            }
        }
    }

    /**
     * Attempts to parse a float value from a {@link HashMap} of strings.
     *
     * @param params The map to obtain the value from.
     * @param key The key that the value is assigned to.
     * @param defaultValue The default value.
     * @return The parsed float value, or the default value on failure.
     */
    private static float parseFloatParam(
            HashMap<String, String> params, String key, float defaultValue) {
        final String value = params.get(key);

        if (value == null) {
            return defaultValue;
        }

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return defaultValue;
    }

    /**
     * Attempts to shutdown the specified TTS engine, ignoring any errors.
     *
     * @param tts The TTS engine to shutdown.
     */
    private static void attemptTtsShutdown(TextToSpeech tts) {
        try {
            tts.shutdown();
        } catch (Exception e) {
            // Don't care, we're shutting down.
        }
    }

    /**
     * Returns the localized name of the TTS engine with the specified package
     * name.
     *
     * @param context The parent context.
     * @param enginePackage The package name of the TTS engine.
     * @return The localized name of the TTS engine.
     */
    private static CharSequence getLabelForEngine(Context context, String enginePackage) {
        if (enginePackage == null) {
            return null;
        }

        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(EngineCompatUtils.INTENT_ACTION_TTS_SERVICE);
        intent.setPackage(enginePackage);

        final List<ResolveInfo> resolveInfos =
                pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if ((resolveInfos == null) || resolveInfos.isEmpty()) {
            return null;
        }

        final ResolveInfo resolveInfo = resolveInfos.get(0);
        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

        if (serviceInfo == null) {
            return null;
        }

        return serviceInfo.loadLabel(pm);
    }

    /**
     * Enables/disables the proximity sensor. The proximity sensor should be
     * disabled when not in use to save battery.
     * <p>
     * This is a no-op if the user has turned off the "silence on proximity"
     * preference.
     */
    private void setProximitySensorState(boolean enabled) {
        mRequestedProximityState = enabled;

        // Should we be using the proximity sensor at all?
        if (!mSilenceOnProximity) {
            if (mProximitySensor != null) {
                mProximitySensor.stop();
                mProximitySensor = null;
            }

            return;
        }

        // Do we need to initialize the proximity sensor?
        if (mProximitySensor == null) {
            if (enabled) {
                mProximitySensor = new ProximitySensor(mService);
                mProximitySensor.setProximityChangeListener(mProximityChangeListener);
            } else {
                return;
            }
        }

        // Manage the proximity sensor state.
        if (enabled) {
            mProximitySensor.start();
        } else {
            mProximitySensor.stop();
        }
    }

    /**
     * Sets whether or not the proximity sensor should be used to silence
     * speech.
     * <p>
     * This should be called when the user changes the state of the "silence on
     * proximity" preference.
     */
    public void setSilenceOnProximity(boolean silenceOnProximity) {
        mSilenceOnProximity = silenceOnProximity;

        // We should only turn proximity on if it's enabled and already
        // supposed to be on.
        setProximitySensorState(mSilenceOnProximity && mRequestedProximityState);
    }

    /**
     * Lets the SpeechController know whether the screen is on.
     */
    public void setScreenIsOn(boolean screenIsOn) {
        mScreenIsOn = screenIsOn;
        // The proximity sensor should always be on when the screen is on so
        // that the proximity gesture can be used to silence all apps.
        if (mScreenIsOn) {
            setProximitySensorState(true);
        }
    }

    /**
     * Sets whether the SpeechController should inject utterance completed
     * callbacks.
     */
    public void setShouldInjectAutoReadingCallbacks(
            boolean shouldInject, Runnable nextItemCallback) {
        mFullScreenReadNextCallback = (shouldInject) ? nextItemCallback : null;
        mInjectFullScreenReadCallbacks = shouldInject;

        if (!shouldInject) {
            removeUtteranceCompleteAction(nextItemCallback);
        }
    }

    /**
     * Stops the TTS engine when the proximity sensor is close.
     */
    private final ProximityChangeListener mProximityChangeListener = new ProximityChangeListener() {
        @Override
        public void onProximityChanged(boolean isClose) {
            // Stop feedback if the user is close to the sensor.
            if (isClose) {
                mService.interruptAllFeedback();
            }
        }
    };

    /** Clears the flag indicating that the TTS shouldn't be interrupted. */
    private final Runnable mClearUninterruptible = new Runnable() {
        @Override
        public void run() {
            mUninterruptible = false;
        }
    };

    /** Hands off utterance completed processing. */
    private final TextToSpeech.OnUtteranceCompletedListener mUtteranceCompletedListener =
            new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    // After each utterance has finished, re-evaluate whether
                    // or not to turn off the proximity sensor.
                    setProximitySensorState(mScreenIsOn);
                    mHandler.onUtteranceCompleted(utteranceId);
                }
            };

    /**
     * When changing TTS engines, switches the active TTS engine when the new
     * engine is initialized.
     */
    private final TextToSpeech.OnInitListener mTtsChangeListener =
            new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    mHandler.onTtsInitialized(status);
                }
            };

    private final SpeechHandler mHandler = new SpeechHandler(this);

    /**
     * Handles changes to the default TTS engine.
     */
    private final ContentObserver mSynthObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mService.getContentResolver();
            updateDefaultEngine(resolver);
        }
    };

    private final ContentObserver mPitchObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mService.getContentResolver();
            updateDefaultPitch(resolver);
        }
    };

    private final ContentObserver mRateObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mService.getContentResolver();
            updateDefaultRate(resolver);
        }
    };

    /**
     * Handles preference changes that affect speech.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    final Resources res = mService.getResources();

                    // TODO: Optimize this to use the key.
                    manageTtsOverlayEnabled(res, sharedPreferences);
                    manageIntonationEnabled(res, sharedPreferences);
                    manageSpeechVolume(res, sharedPreferences);
                }
            };

    /** Handler used to return to the main thread from the TTS thread. */
    private static class SpeechHandler extends WeakReferenceHandler<SpeechController> {
        /** Hand-off engine initialized. */
        private static final int MSG_INITIALIZED = 1;

        /** Hand-off utterance completed. */
        private static final int MSG_UTTERANCE_COMPLETED = 2;

        /** Hand-off media state changes. */
        private static final int MSG_MEDIA_STATE_CHANGED = 3;

        public SpeechHandler(SpeechController parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, SpeechController parent) {
            switch (msg.what) {
                case MSG_INITIALIZED:
                    parent.handleTtsInitialized(msg.arg1);
                    break;
                case MSG_UTTERANCE_COMPLETED:
                    parent.handleUtteranceCompleted((String) msg.obj, true);
                    break;
                case MSG_MEDIA_STATE_CHANGED:
                    parent.handleMediaStateChanged((String) msg.obj);
            }
        }

        public void onTtsInitialized(int status) {
            obtainMessage(MSG_INITIALIZED, status, 0).sendToTarget();
        }

        public void onUtteranceCompleted(String utteranceId) {
            obtainMessage(MSG_UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }

        public void onMediaStateChanged(String action) {
            obtainMessage(MSG_MEDIA_STATE_CHANGED, action).sendToTarget();
        }
    }

    /**
     * {@link BroadcastReceiver} for detecting media mount and unmount.
     */
    private class MediaMountStateMonitor extends BroadcastReceiver {
        private final IntentFilter mMediaIntentFilter;

        public MediaMountStateMonitor() {
            mMediaIntentFilter = new IntentFilter();
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            mMediaIntentFilter.addDataScheme("file");
        }

        public IntentFilter getFilter() {
            return mMediaIntentFilter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            mHandler.onMediaStateChanged(action);
        }
    }

    /**
     * An action that should be performed after a particular utterance index
     * completes.
     */
    private static class UtteranceCompleteAction {
        public UtteranceCompleteAction(int utteranceIndex, Runnable runnable) {
            this.utteranceIndex = utteranceIndex;
            this.runnable = runnable;
        }

        /**
         * The minimum utterance index that must complete before this action
         * should be performed.
         */
        public int utteranceIndex;

        /**
         * The action to execute.
         */
        public Runnable runnable;
    }
}
