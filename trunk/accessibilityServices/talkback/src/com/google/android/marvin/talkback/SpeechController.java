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
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.marvin.utils.BluetoothConnectionManager;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils.EngineCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.Collections;
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
    /** The package name of the system default TTS. */
    private final String SYSTEM_TTS_ENGINE = (Build.VERSION.SDK_INT < 14) ? "com.svox.pico" : "com.google.android.tts";

    /** Prefix for utterance IDs. */
    private static final String UTTERANCE_ID_PREFIX = "talkback_";

    /** Number of times a TTS engine can fail before switching. */
    private static final int MAX_TTS_FAILURES = 2;

    /** Constant to flush speech globally. */
    private static final int SPEECH_FLUSH_ALL = 2;

    /** Default stream for speech output. */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

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

    public enum QueuingMode {
        INTERRUPT, QUEUE, UNINTERRUPTIBLE;

        /**
         * Returns the queuing mode with the specified ordinal value.
         *
         * @param ordinal The ordinal value of the mode.
         * @return The queuing mode with the specified ordinal value.
         */
        public static QueuingMode valueOf(int ordinal) {
            final QueuingMode[] values = values();

            if (ordinal < 0 || ordinal >= values.length) {
                return values[0];
            }

            return values[ordinal];
        }
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

    /**
     * {@link BroadcastReceiver} for determining changes in the media state used
     * for switching the TTS engine.
     */
    private final MediaMountStateMonitor mMediaStateMonitor = new MediaMountStateMonitor();

    /** The parent context. */
    private final Context mContext;

    /** The audio manager, used to query ringer volume. */
    private final AudioManager mAudioManager;

    /** The telephone manager, used to query ringer state. */
    private final TelephonyManager mTelephonyManager;

    /** The volume monitor, used to set ringer volume. */
    private VolumeMonitor mVolumeMonitor;

    /** Handles connecting to BT headsets. */
    private BluetoothConnectionManager mBluetoothHandler;

    /** The last spoken utterance. */
    private CharSequence mLastSpokenUtterance;

    /** The TTS engine. */
    private TextToSpeech mTts;

    /** The engine loaded into the current TTS. */
    private String mTtsEngine;

    /** The number of time the current TTS has failed. */
    private int mTtsFailures;

    /** The package name of the preferred TTS engine. */
    private String mPreferredTtsEngine;

    /** A list of installed TTS engines. */
    private List<String> mTtsEngines;

    /** A temporary TTS used for switching engines. */
    private TextToSpeech mTempTts;

    /** The engine loading into the temporary TTS. */
    private String mTempTtsEngine;

    /** The text-to-speech screen overlay. */
    private TextToSpeechOverlay mTtsOverlay;

    /** Whether the TTS is currently speaking an uninterruptible utterance. */
    private boolean mUninterruptible;

    /**
     * The next utterance index; each utterance id will be constructed from this
     * ever-increasing index.
     */
    private int mNextUtteranceIndex = 0;

    /** Whether rate and pitch can change. */
    private boolean mIntonationEnabled;

    private float mCurrentRate = -1.0f;
    private float mCurrentPitch = -1.0f;

    private float mDefaultRate;
    private float mDefaultPitch;

    private Runnable mRestoreRingerAction;

    public SpeechController(Context context) {
        mContext = context;
        mContext.registerReceiver(mMediaStateMonitor, mMediaStateMonitor.getFilter());

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mUninterruptible = false;

        mTtsEngines = getAvailableTtsEngines(context);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        final Resources res = mContext.getResources();

        manageTtsOverlayEnabled(res, prefs);
        manageBluetoothEnabled(res, prefs);
        manageIntonationEnabled(res, prefs);

        final ContentResolver resolver = context.getContentResolver();

        updateDefaultEngine(resolver);
        updateDefaultPitch(resolver);
        updateDefaultRate(resolver);

        final Uri defaultSynth = Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH);
        final Uri defaultPitch = Secure.getUriFor(Secure.TTS_DEFAULT_PITCH);
        final Uri defaultRate = Secure.getUriFor(Secure.TTS_DEFAULT_RATE);

        resolver.registerContentObserver(defaultSynth, false, mSynthObserver);
        resolver.registerContentObserver(defaultPitch, false, mPitchObserver);
        resolver.registerContentObserver(defaultRate, false, mRateObserver);
    }

    public void setVolumeMonitor(VolumeMonitor volumeMonitor) {
        mVolumeMonitor = volumeMonitor;
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
            final CharSequence cleanedChar = SpeechCleanupUtils.cleanUp(mContext, character);

            StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
        }

        final String utterance = builder.toString();

        speakWithFailover(utterance, TextToSpeech.QUEUE_FLUSH, null);
    }

    /**
     * @see #cleanUpAndSpeak(CharSequence, QueuingMode, Bundle, Runnable)
     */
    public void cleanUpAndSpeak(CharSequence text, QueuingMode queueMode, Bundle params) {
        cleanUpAndSpeak(text, queueMode, params, null);
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
     *            <li>{@link QueuingMode#INTERRUPT}
     *            <li>{@link QueuingMode#QUEUE}
     *            <li>{@link QueuingMode#UNINTERRUPTIBLE}
     *            </ul>
     * @param params Speaking parameters. Not all parameters are supported by
     *            all engines. One of:
     *            <ul>
     *            <li>{@link SpeechParam#PAN}
     *            <li>{@link SpeechParam#PITCH}
     *            <li>{@link SpeechParam#RATE}
     *            <li>{@link SpeechParam#VOLUME}
     *            </ul>
     * @param completedAction The action to run after this utterance has been spoken.
     */
    public void cleanUpAndSpeak(CharSequence text, QueuingMode queueMode, Bundle params,
            Runnable completedAction) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        if (params == null) {
            params = Bundle.EMPTY;
        }

        // Attempt to clean up the text.
        final CharSequence trimmedText = text.toString().trim();
        final CharSequence cleanedText = SpeechCleanupUtils.cleanUp(mContext, trimmedText);

        mLastSpokenUtterance = cleanedText;

        // Give every utterance an unique identifier with an increasing index.
        final int utteranceIndex = getNextUtteranceId();
        final String utteranceId = UTTERANCE_ID_PREFIX + utteranceIndex;

        if (completedAction != null) {
            addUtteranceCompleteAction(utteranceIndex, completedAction);
        }

        if (isDeviceRinging()) {
            manageRingerVolume(utteranceIndex);
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

        LogUtils.log(this, Log.VERBOSE, "Speaking with queue mode %s: \"%s\"",
                queueMode.name(), cleanedText);

        speakWithFailover(cleanedText.toString(), ttsQueueMode, speechParams);
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
     * Stops speech from this controller.
     */
    public void interrupt() {
        try {
            mTts.stop();
        } catch (Exception e) {
            // Don't care, we're not speaking.
        }

        // Ensure all pending completion actions happen.
        handleUtteranceCompleted(UTTERANCE_ID_PREFIX + Integer.MAX_VALUE, false);
    }

    /**
     * Stops speech from all applications.
     */
    public void stopAll() {
        try {
            mTts.speak("", SPEECH_FLUSH_ALL, null);
        } catch (Exception e) {
            // Don't care, we're not speaking.
        }

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
            mContext.unregisterReceiver(mMediaStateMonitor);
        }

        final ContentResolver resolver = mContext.getContentResolver();
        resolver.unregisterContentObserver(mSynthObserver);
        resolver.unregisterContentObserver(mPitchObserver);
        resolver.unregisterContentObserver(mRateObserver);

        manageBluetoothEnabled(null, null);
        manageTtsOverlayEnabled(null, null);
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
            mTtsOverlay = new TextToSpeechOverlay(mContext);
        } else if (!ttsOverlayEnabled && mTtsOverlay != null) {
            mTtsOverlay.hide();
            mTtsOverlay = null;
        }
    }

    /**
     * Manages whether speech will be output through a Bluetooth headset when
     * available.
     *
     * @param res Activity resources.
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to disable Bluetooth.
     */
    private void manageBluetoothEnabled(Resources res, SharedPreferences prefs) {
        final boolean bluetoothSupported = (Build.VERSION.SDK_INT
                >= BluetoothConnectionManager.MIN_API_LEVEL);

        if (!bluetoothSupported) {
            return;
        }

        final boolean bluetoothEnabled;

        if (prefs != null && res != null) {
            bluetoothEnabled = SharedPreferencesUtils.getBooleanPref(prefs, res,
                    R.string.pref_bluetooth_key, R.bool.pref_bluetooth_default);
        } else {
            bluetoothEnabled = false;
        }

        if (bluetoothEnabled && mBluetoothHandler == null) {
            mBluetoothHandler = new BluetoothConnectionManager(5000, mBluetoothListener);
        } else if (!bluetoothEnabled && mBluetoothHandler != null) {
            mBluetoothHandler.stopSco();
            mBluetoothHandler.stop();
            mBluetoothHandler = null;
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

        manageBluetoothConnection(speechParams);

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
            if (needsAdjustment) {
                mTts.stop();
                mTts.setPitch(pitch);
                mTts.setSpeechRate(rate);

                mCurrentPitch = pitch;
                mCurrentRate = rate;
            }

            // TODO(alanv): Most applications don't know how to duck audio!
            //AudioManagerCompatUtils.requestAudioFocus(mAudioManager, null, AudioManager.STREAM_MUSIC,
            //        AudioManagerCompatUtils.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);

            final int result = mTts.speak(utterance, queueMode, speechParams);

            if (result != TextToSpeech.SUCCESS) {
                // Treat the utterance as completed since we won't get a callback.
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
     * Returns the TTS queuing mode based on the requested queuing mode and the
     * speech controller's state.
     *
     * @param queueMode The requested queuing mode.
     * @param utteranceIndex The index for this utterance.
     * @return A TTS queuing mode.
     */
    private int computeQueuingMode(QueuingMode queueMode, int utteranceIndex) {
        final int ttsQueueMode;

        if (queueMode == QueuingMode.UNINTERRUPTIBLE) {
            ttsQueueMode = TextToSpeech.QUEUE_FLUSH;

            // Set uninterruptible to true.
            mUninterruptible = true;
            removeUtteranceCompleteAction(mClearUninterruptible);
            addUtteranceCompleteAction(utteranceIndex, mClearUninterruptible);
        } else if (queueMode == QueuingMode.INTERRUPT && !mUninterruptible) {
            ttsQueueMode = TextToSpeech.QUEUE_FLUSH;
        } else {
            ttsQueueMode = TextToSpeech.QUEUE_ADD;

            // Clear uninterruptible.
            mUninterruptible = false;
            removeUtteranceCompleteAction(mClearUninterruptible);
        }

        return ttsQueueMode;
    }

    /**
     * @return {@code true} if the device is ringing.
     */
    private boolean isDeviceRinging() {
        return mTelephonyManager != null
                && (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_RINGING);
    }

    /**
     * Handle speaking through mono Bluetooth headsets.
     */
    private void manageBluetoothConnection(HashMap<String, String> speechParams) {
        if (mBluetoothHandler == null || !mBluetoothHandler.isBluetoothAvailable()) {
            // Can't output -- either disabled or no connection.
            return;
        }

        if (!mBluetoothHandler.isAudioConnected()) {
            mBluetoothHandler.start(mContext);
        }

        speechParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM,
                Integer.toString(AudioManager.STREAM_VOICE_CALL));

        LogUtils.log(SpeechController.class, Log.DEBUG, "Connected to Bluetooth headset!");
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
            return;
        }

        final int utteranceIndex;

        try {
            utteranceIndex = Integer.parseInt(utteranceId.substring(UTTERANCE_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
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
        //AudioManagerCompatUtils.abandonAudioFocus(mAudioManager, null);
    }

    /**
     * Handles media state changes.
     *
     * @param action The current media state.
     */
    private void handleMediaStateChanged(String action) {
        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            // When the SD card is mounted, switch to the preferred TTS
            // engine.
            if (mPreferredTtsEngine != null) {
                setTtsEngine(mPreferredTtsEngine);
            }
        } else if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            // If the SD card is unmounted, cache the preferred engine and
            // switch to the system TTS engine.
            mPreferredTtsEngine = mTtsEngine;
            setTtsEngine(SYSTEM_TTS_ENGINE);
        }
    }

    /**
     * Decreases the ringer volume and registers a listener for the event of
     * completing to speak which restores the volume to its previous level.
     *
     * @param utteranceIndex the index of this utterance, used to schedule an
     *            utterance completion action.
     */
    private void manageRingerVolume(int utteranceIndex) {
        if (mRestoreRingerAction != null) {
            removeUtteranceCompleteAction(mRestoreRingerAction);
            addUtteranceCompleteAction(utteranceIndex, mRestoreRingerAction);
            return;
        }

        // TODO(alanv): Do we need to manually duck audio?
        final int currentRingerVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        final int maxRingerVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        final int lowerEnoughVolume = Math.max((maxRingerVolume / 3), (currentRingerVolume / 2));

        if (mVolumeMonitor != null) {
            mVolumeMonitor.setStreamVolume(AudioManager.STREAM_RING, lowerEnoughVolume, 0);
        }
        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, lowerEnoughVolume, 0);

        mRestoreRingerAction = new RestoreRingerRunnable(currentRingerVolume, lowerEnoughVolume);

        addUtteranceCompleteAction(utteranceIndex, mRestoreRingerAction);
    }

    /**
     * Try to switch the TTS engine.
     *
     * @param engine The package name of the desired TTS engine
     */
    private void setTtsEngine(String engine) {
        if (mTempTts != null) {
            LogUtils.log(SpeechController.class, Log.ERROR, "Can't start TTS engine %s while still loading previous engine", engine);
            return;
        }

        LogUtils.log(SpeechController.class, Log.ERROR, "Starting TTS engine: %s", engine);

        mTempTtsEngine = engine;
        mTempTts = TextToSpeechCompatUtils.newTextToSpeech(mContext, mTtsChangeListener, engine);
    }

    /**
     * Assumes the current engine has failed and attempts to start the next
     * available engine.
     *
     * @param failedEngine The package name of the engine to switch from.
     */
    private void attemptTtsFailover(String failedEngine) {
        mTtsFailures++;

        if (mTtsFailures < MAX_TTS_FAILURES) {
            // Restart the current engine.
            setTtsEngine(mTtsEngine);
            return;
        }

        mTtsEngines.remove(failedEngine);
        mTtsFailures = 0;

        if (mTtsEngines.size() == 0) {
            LogUtils.log(SpeechController.class, Log.ERROR,
                    "Ran out of TTS engines during failover from %s", failedEngine);
            return;
        }

        // Try to use the first available TTS engine.
        final String nextEngine = mTtsEngines.get(0);

        setTtsEngine(nextEngine);
    }

    /**
     * Handles TTS engine initialization.
     *
     * @param status The status returned by the TTS engine.
     */
    @SuppressWarnings("deprecation")
    private void handleTtsInitialized(int status) {
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

        if (isSwitchingEngines) {
            speakCurrentEngine();
        }
    }

    /**
     * Speaks the name of the currently active TTS engine.
     */
    private void speakCurrentEngine() {
        final CharSequence engineLabel = getLabelForEngine(mContext, mTtsEngine);

        if (engineLabel == null) {
            return;
        }

        final String utterance =
                mContext.getString(R.string.template_current_tts_engine, engineLabel);

        speakWithFailover(utterance, TextToSpeech.QUEUE_FLUSH, null);
    }

    private void updateDefaultEngine(ContentResolver resolver) {
        // This may be null if the user hasn't specified an engine.
        final String defaultEngine = Secure.getString(resolver, Secure.TTS_DEFAULT_SYNTH);

        // Set the fall-back engine to the default engine.
        if (mPreferredTtsEngine == null) {
            mPreferredTtsEngine = defaultEngine;
        }

        // Don't do anything if this is already the default engine.
        if ((defaultEngine != null) && defaultEngine.equals(mTtsEngine)) {
            return;
        }

        // Automatically switch engines when the system default changes.
        interrupt();
        mTtsFailures = 0;
        setTtsEngine(defaultEngine);
    }

    private void updateDefaultPitch(ContentResolver resolver) {
        mDefaultPitch = Secure.getInt(resolver, Secure.TTS_DEFAULT_PITCH, 100) / 100.0f;
    }

    private void updateDefaultRate(ContentResolver resolver) {
        mDefaultRate = Secure.getInt(resolver, Secure.TTS_DEFAULT_RATE, 100) / 100.0f;
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

        if (resolveInfos.isEmpty()) {
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
     * Returns a list of available TTS engines.
     *
     * @param context The parent context.
     * @return A list of available TTS engines.
     */
    private static List<String> getAvailableTtsEngines(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(EngineCompatUtils.INTENT_ACTION_TTS_SERVICE);
        final List<ResolveInfo> resolveInfos =
                pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfos.isEmpty()) {
            return Collections.emptyList();
        }

        final List<String> results = new LinkedList<String>();

        for (ResolveInfo resolveInfo : resolveInfos) {
            final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

            if (serviceInfo == null) {
                continue;
            }

            results.add(serviceInfo.packageName);
        }

        return results;
    }

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

    private final BluetoothConnectionManager.Listener mBluetoothListener =
            new BluetoothConnectionManager.Listener() {
                @Override
                public void onConnectionComplete() {
                    final BluetoothConnectionManager bluetoothHandler = mBluetoothHandler;

                    if (bluetoothHandler != null) {
                        mAudioManager.setMode(AudioManager.MODE_IN_CALL);
                        mBluetoothHandler.startSco();
                    }
                }
            };

    private final SpeechHandler mHandler = new SpeechHandler();

    /**
     * Handles changes to the default TTS engine.
     */
    private final ContentObserver mSynthObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mContext.getContentResolver();
            updateDefaultEngine(resolver);
        }
    };

    private final ContentObserver mPitchObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mContext.getContentResolver();
            updateDefaultPitch(resolver);
        }
    };

    private final ContentObserver mRateObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            final ContentResolver resolver = mContext.getContentResolver();
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
                    final Resources res = mContext.getResources();

                    // TODO: Optimize this to use the key.
                    manageBluetoothEnabled(res, sharedPreferences);
                    manageTtsOverlayEnabled(res, sharedPreferences);
                    manageIntonationEnabled(res, sharedPreferences);
                }
            };

    /** Handler used to return to the main thread from the TTS thread. */
    private class SpeechHandler extends Handler {
        /** Hand-off engine initialized. */
        private static final int MSG_INITIALIZED = 1;

        /** Hand-off utterance completed. */
        private static final int MSG_UTTERANCE_COMPLETED = 2;

        /** Hand-off media state changes. */
        private static final int MSG_MEDIA_STATE_CHANGED = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_INITIALIZED:
                    handleTtsInitialized(msg.arg1);
                    break;
                case MSG_UTTERANCE_COMPLETED:
                    handleUtteranceCompleted((String) msg.obj, true);
                    break;
                case MSG_MEDIA_STATE_CHANGED:
                    handleMediaStateChanged((String) msg.obj);
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
     * Utterance completion action used to restore the ringer volume.
     */
    private class RestoreRingerRunnable implements Runnable {
        private final int mRestoreVolume;
        private final int mExpectedVolume;

        public RestoreRingerRunnable(int restoreVolume, int expectedVolume) {
            mRestoreVolume = restoreVolume;
            mExpectedVolume = expectedVolume;
        }

        @Override
        public void run() {
            mRestoreRingerAction = null;

            final int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);

            if (currentVolume != mExpectedVolume) {
                LogUtils.log(SpeechController.class, Log.WARN,
                        "Current volume does not match expected volume!");
                return;
            }

            if (mVolumeMonitor != null) {
                mVolumeMonitor.setStreamVolume(AudioManager.STREAM_RING, mRestoreVolume, 0);
            }
            mAudioManager.setStreamVolume(AudioManager.STREAM_RING, mRestoreVolume, 0);

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
