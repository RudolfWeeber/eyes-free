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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech.Engine;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.marvin.talkback.TalkBackService.ServiceState;
import com.google.android.marvin.utils.FailoverTextToSpeech;
import com.google.android.marvin.utils.FailoverTextToSpeech.FailoverTtsListener;
import com.google.android.marvin.utils.ProximitySensor;
import com.google.android.marvin.utils.ProximitySensor.ProximityChangeListener;
import com.googlecode.eyesfree.compat.media.AudioManagerCompatUtils;
import com.googlecode.eyesfree.compat.speech.tts.TextToSpeechCompatUtils.EngineCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Handles text-to-speech.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class SpeechController {
    /** Prefix for utterance IDs. */
    private static final String UTTERANCE_ID_PREFIX = "talkback_";

    /** Default stream for speech output. */
    public static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    // Queue modes.
    public static final int QUEUE_MODE_INTERRUPT = 0;
    public static final int QUEUE_MODE_QUEUE = 1;
    public static final int QUEUE_MODE_UNINTERRUPTIBLE = 2;
    public static final int QUEUE_MODE_FLUSH_ALL = 3;

    // Speech item status codes.
    public static final int STATUS_ERROR = 1;
    public static final int STATUS_SPEAKING = 2;
    public static final int STATUS_INTERRUPTED = 3;
    public static final int STATUS_SPOKEN = 4;

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
     * Priority queue of actions to perform when utterances are completed,
     * ordered by ascending utterance index.
     */
    private final PriorityQueue<UtteranceCompleteAction> mUtteranceCompleteActions =
            new PriorityQueue<UtteranceCompleteAction>();

    /** The list of items to be spoken. */
    private final LinkedList<FeedbackItem> mFeedbackQueue = new LinkedList<FeedbackItem>();

    /** The parent service. */
    private final TalkBackService mService;

    /** The audio manager, used to query ringer volume. */
    private final AudioManager mAudioManager;

    /** The feedback controller, used for playing auditory icons and vibration */
    private final MappedFeedbackController mFeedbackController;

    /** The text-to-speech service, used for speaking. */
    private final FailoverTextToSpeech mFailoverTts;

    /** Proximity sensor for implementing "shut up" functionality. */
    private ProximitySensor mProximitySensor;

    /** Listener used for testing. */
    private SpeechControllerListener mSpeechListener;

    /** An iterator at the fragment currently being processed */
    private Iterator<FeedbackFragment> mCurrentFragmentIterator = null;

    /** The item current being spoken, or {@code null} if the TTS is idle. */
    private FeedbackItem mCurrentFeedbackItem;

    /** The last processed feedback item. */
    private FeedbackItem mLastFeedbackItem = null;

    /** Whether to use the proximity sensor to silence speech. */
    private boolean mSilenceOnProximity;

    /** Whether we should request audio focus during speech. */
    private boolean mUseAudioFocus;

    /** Whether or not the screen is on. */
    // This is set by RingerModeAndScreenMonitor and used by SpeechController
    // to determine if the ProximitySensor should be on or off.
    private boolean mScreenIsOn;

    /** The text-to-speech screen overlay. */
    private TextToSpeechOverlay mTtsOverlay;

    /**
     * Whether the speech controller should add utterance callbacks to
     * FullScreenReadController
     */
    private boolean mInjectFullScreenReadCallbacks;

    /** The utterance completed callback for FullScreenReadController */
    private UtteranceCompleteRunnable mFullScreenReadNextCallback;

    /**
     * The next utterance index; each utterance value will be constructed from this
     * ever-increasing index.
     */
    private int mNextUtteranceIndex = 0;

    /** Whether rate and pitch can change. */
    private boolean mUseIntonation;

    /** The speech rate adjustment (default is 1.0). */
    private float mSpeechRate;

    /** The speech pitch adjustment (default is 1.0). */
    private float mSpeechPitch;

    /** The speech volume adjustment (default is 1.0). */
    private float mSpeechVolume;

    /**
     * Whether the controller is currently speaking utterances. Used to check
     * consistency of internal speaking state.
     */
    private boolean mIsSpeaking;

    public SpeechController(TalkBackService context) {
        mService = context;
        mService.addServiceStateListener(mServiceStateListener);

        mAudioManager = (AudioManager) mService.getSystemService(Context.AUDIO_SERVICE);

        mFailoverTts = new FailoverTextToSpeech(context);
        mFailoverTts.setListener(mFailoverTtsListener);
        mFeedbackController = MappedFeedbackController.getInstance();

        mInjectFullScreenReadCallbacks = false;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        reloadPreferences(prefs);

        mScreenIsOn = true;
    }

    /**
     * @return {@code true} if the speech controller is currently speaking.
     */
    public boolean isSpeaking() {
        return mIsSpeaking;
    }

    public void setSpeechListener(SpeechControllerListener speechListener) {
        mSpeechListener = speechListener;
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

        // Propagate the proximity sensor change.
        setProximitySensorState(mSilenceOnProximity);
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
     * callbacks for advancing continuous reading.
     */
    public void setShouldInjectAutoReadingCallbacks(
            boolean shouldInject, UtteranceCompleteRunnable nextItemCallback) {
        mFullScreenReadNextCallback = (shouldInject) ? nextItemCallback : null;
        mInjectFullScreenReadCallbacks = shouldInject;

        if (!shouldInject) {
            removeUtteranceCompleteAction(nextItemCallback);
        }
    }

    /**
     * Repeats the last spoken utterance.
     */
    public boolean repeatLastUtterance() {
        if (mLastFeedbackItem == null) {
            return false;
        }

        mLastFeedbackItem.addFlag(FeedbackItem.FLAG_NO_HISTORY);
        speak(mLastFeedbackItem, QUEUE_MODE_FLUSH_ALL, null);
        return true;
    }

    /**
     * Spells the last spoken utterance.
     */
    public boolean spellLastUtterance() {
        if (mLastFeedbackItem == null) {
            return false;
        }

        final CharSequence aggregateText = mLastFeedbackItem.getAggregateText();
        if (TextUtils.isEmpty(aggregateText)) {
            return false;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();

        for (int i = 0; i < aggregateText.length(); i++) {
            final String cleanedChar = SpeechCleanupUtils.getCleanValueFor(
                    mService, aggregateText.charAt(i));

            StringBuilderUtils.appendWithSeparator(builder, cleanedChar);
        }

        speak(builder, null, null, QUEUE_MODE_FLUSH_ALL, FeedbackItem.FLAG_NO_HISTORY, null, null,
                null);
        return true;
    }

    /**
     * Speaks the name of the currently active TTS engine.
     */
    private void speakCurrentEngine() {
        final CharSequence engineLabel = mFailoverTts.getEngineLabel();
        if (TextUtils.isEmpty(engineLabel)) {
            return;
        }

        final String text = mService.getString(R.string.template_current_tts_engine, engineLabel);

        speak(text, null, null, QUEUE_MODE_QUEUE, FeedbackItem.FLAG_NO_HISTORY, null, null);
    }

    /**
     * @see #speak(CharSequence, Set, Set, int, int, Bundle, Bundle, UtteranceCompleteRunnable)
     */
    public void speak(CharSequence text, int queueMode, int flags, Bundle speechParams) {
        speak(text, null, null, queueMode, flags, speechParams, null);
    }

    /**
     * @see #speak(CharSequence, Set, Set, int, int, Bundle, Bundle, UtteranceCompleteRunnable)
     */
    public void speak(CharSequence text, Set<Integer> earcons, Set<Integer> haptics, int queueMode,
            int flags, Bundle speechParams, Bundle nonSpeechParams) {
        speak(text, earcons, haptics, queueMode, flags, speechParams, nonSpeechParams, null);
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The <code>queueMode</code> determines
     * whether the speech will interrupt or wait on queued speech events.
     * <p>
     * This method does nothing if the text to speak is empty. See
     * {@link TextUtils#isEmpty(CharSequence)} for implementation.
     * <p>
     * See {@link SpeechCleanupUtils#cleanUp} for text clean-up implementation.
     *
     * @param text The text to speak.
     * @param earcons The set of earcon IDs to play.
     * @param haptics The set of vibration patterns to play.
     * @param queueMode The queue mode to use for speaking. One of:
     *            <ul>
     *            <li>{@link #QUEUE_MODE_INTERRUPT} <li>
     *            {@link #QUEUE_MODE_QUEUE} <li>
     *            {@link #QUEUE_MODE_UNINTERRUPTIBLE}
     *            </ul>
     * @param flags Bit mask of speaking flags. Use {@code 0} for no flags, or a
     *            combination of the flags defined in {@link FeedbackItem}
     * @param speechParams Speaking parameters. Not all parameters are supported by
     *            all engines. One of:
     *            <ul>
     *            <li>{@link SpeechParam#PAN} <li>{@link SpeechParam#PITCH} <li>
     *            {@link SpeechParam#RATE} <li>{@link SpeechParam#VOLUME}
     *            </ul>
     * @param nonSpeechParams Non-Speech parameters. Optional, but can include
     *            {@link Utterance#KEY_METADATA_EARCON_RATE} and
     *            {@link Utterance#KEY_METADATA_EARCON_VOLUME}
     * @param completedAction The action to run after this utterance has been
     *            spoken.
     */
    public void speak(CharSequence text, Set<Integer> earcons, Set<Integer> haptics, int queueMode,
            int flags, Bundle speechParams, Bundle nonSpeechParams,
            UtteranceCompleteRunnable completedAction) {

        final FeedbackItem pendingItem = FeedbackProcessingUtils.generateFeedbackItemFromInput(
                mService, text, earcons, haptics, flags, speechParams, nonSpeechParams);

        speak(pendingItem, queueMode, completedAction);
    }

    private void speak(
            FeedbackItem item, int queueMode, UtteranceCompleteRunnable completedAction) {

        // If this FeedbackItem is flagged as NO_SPEECH, ignore speech and
        // immediately process earcons and haptics without disrupting the speech
        // queue.
        // TODO(caseyburkhardt): Consider refactoring non-speech feedback out of
        // this class entirely.
        if (item.hasFlag(FeedbackItem.FLAG_NO_SPEECH)) {
            for (FeedbackFragment fragment : item.getFragments()) {
                playEarconsFromFragment(fragment);
                playHapticsFromFragment(fragment);
            }

            return;
        }

        item.setUninterruptible(queueMode == QUEUE_MODE_UNINTERRUPTIBLE);
        item.setCompletedAction(completedAction);

        if (queueMode != QUEUE_MODE_QUEUE) {
            mCurrentFragmentIterator = null;
            mFeedbackQueue.clear();
        }

        mFeedbackQueue.add(item);

        // If TTS isn't ready, this should be the only item in the queue.
        if (!mFailoverTts.isReady()) {
            LogUtils.log(this, Log.ERROR, "Attempted to speak before TTS was initialized.");
            return;
        }

        if ((mCurrentFeedbackItem == null)
                || ((queueMode != QUEUE_MODE_QUEUE) && !mCurrentFeedbackItem.isUninterruptible())) {
            speakNextItem();
        } else {
            LogUtils.log(this, Log.VERBOSE, "Queued speech item, waiting for \"%s\"",
                    mCurrentFeedbackItem.getUtteranceId());
        }
    }

    /**
     * Add a new action that will be run when the given utterance index
     * completes.
     *
     * @param index The index of the utterance that should finish before this
     *            action is executed.
     * @param runnable The code to execute.
     */
    public void addUtteranceCompleteAction(int index, UtteranceCompleteRunnable runnable) {
        final UtteranceCompleteAction action = new UtteranceCompleteAction(index, runnable);
        mUtteranceCompleteActions.add(action);
    }

    /**
     * Removes all instances of the specified runnable from the utterance
     * complete action list.
     *
     * @param runnable The runnable to remove.
     */
    public void removeUtteranceCompleteAction(UtteranceCompleteRunnable runnable) {
        final Iterator<UtteranceCompleteAction> i = mUtteranceCompleteActions.iterator();

        while (i.hasNext()) {
            final UtteranceCompleteAction action = i.next();
            if (action.runnable == runnable) {
                i.remove();
            }
        }
    }

    /**
     * Stops all speech.
     */
    public void interrupt() {
        // Clear all current and queued utterances.
        clearCurrentAndQueuedUtterances();

        // Clear and post all remaining completion actions.
        clearUtteranceCompletionActions(true);

        // Make sure TTS actually stops talking.
        mFailoverTts.stopAll();
    }

    /**
     * Stops speech and shuts down this controller.
     */
    public void shutdown() {
        interrupt();

        mFailoverTts.shutdown();

        setOverlayEnabled(false);
        setProximitySensorState(false);
    }

    /**
     * Returns the next utterance identifier.
     */
    public int peekNextUtteranceId() {
        return mNextUtteranceIndex;
    }

    /**
     * Returns the next utterance identifier and increments the utterance value.
     */
    private int getNextUtteranceId() {
        return mNextUtteranceIndex++;
    }

    /**
     * Reloads preferences for this controller.
     *
     * @param prefs The shared preferences for this service. Pass {@code null}
     *            to disable the overlay.
     */
    private void reloadPreferences(SharedPreferences prefs) {
        final Resources res = mService.getResources();

        final boolean ttsOverlayEnabled = SharedPreferencesUtils.getBooleanPref(prefs, res,
                R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default);

        setOverlayEnabled(ttsOverlayEnabled);

        mUseIntonation = SharedPreferencesUtils.getBooleanPref(prefs, res,
                R.string.pref_intonation_key, R.bool.pref_intonation_default);
        mSpeechPitch = SharedPreferencesUtils.getFloatFromStringPref(prefs, res,
                R.string.pref_speech_pitch_key, R.string.pref_speech_pitch_default);
        mSpeechRate = SharedPreferencesUtils.getFloatFromStringPref(prefs, res,
                R.string.pref_speech_rate_key, R.string.pref_speech_rate_default);
        mUseAudioFocus = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);

        // Speech volume is stored as int [0,100] and scaled to float [0,1].
        mSpeechVolume = (SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                R.string.pref_speech_volume_key, R.string.pref_speech_volume_default) / 100.0f);

        if (!mUseAudioFocus) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }
    }

    private void setOverlayEnabled(boolean enabled) {
        if (enabled && mTtsOverlay == null) {
            mTtsOverlay = new TextToSpeechOverlay(mService);
        } else if (!enabled && mTtsOverlay != null) {
            mTtsOverlay.hide();
            mTtsOverlay = null;
        }
    }

    /**
     * Returns {@code true} if speech should be silenced. Does not prevent
     * haptic or auditory feedback from occurring. The controller will run
     * utterance completion actions immediately for silenced utterances.
     * <p>
     * Silences speech in the following cases:
     * <ul>
     * <li>Speech recognition is active and the user is not using a headset
     * </ul>
     */
    @SuppressWarnings("deprecation")
    private boolean shouldSilenceSpeech(FeedbackItem item) {
        // Unless otherwise flagged, don't speak during speech recognition.
        if (!item.hasFlag(FeedbackItem.FLAG_DURING_RECO)
                && AudioManagerCompatUtils.isSpeechRecognitionActive(mAudioManager)
                && !mAudioManager.isBluetoothA2dpOn() && !mAudioManager.isWiredHeadsetOn()) {
            return true;
        }

        return false;
    }

    /**
     * Sends the specified item to the text-to-speech engine. Manages internal
     * speech controller state.
     * <p>
     * This method should only be called by {@link #speakNextItem()}.
     *
     * @param item The item to speak.
     */
    @SuppressLint("InlinedApi")
    private void speakNextItemInternal(FeedbackItem item) {
        final int utteranceIndex = getNextUtteranceId();
        final String utteranceId = UTTERANCE_ID_PREFIX + utteranceIndex;
        item.setUtteranceId(utteranceId);

        final UtteranceCompleteRunnable completedAction = item.getCompletedAction();
        if (completedAction != null) {
            addUtteranceCompleteAction(utteranceIndex, completedAction);
        }

        if (mInjectFullScreenReadCallbacks
                && item.hasFlag(FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING)) {
            addUtteranceCompleteAction(utteranceIndex, mFullScreenReadNextCallback);
        }

        if ((item != null) && !item.hasFlag(FeedbackItem.FLAG_NO_HISTORY)) {
            mLastFeedbackItem = item;
        }

        if (mSpeechListener != null) {
            mSpeechListener.onUtteranceStarted(utteranceIndex);
        }

        processNextFragmentInternal();
    }

    private boolean processNextFragmentInternal() {
        if (mCurrentFragmentIterator == null || !mCurrentFragmentIterator.hasNext()) {
            return false;
        }

        FeedbackFragment fragment = mCurrentFragmentIterator.next();
        playEarconsFromFragment(fragment);
        playHapticsFromFragment(fragment);

        // Reuse the global instance of speech parameters.
        final HashMap<String, String> params = mSpeechParametersMap;
        params.clear();

        // Add all custom speech parameters.
        final Bundle speechParams = fragment.getSpeechParams();
        for (String key : speechParams.keySet()) {
            params.put(key, String.valueOf(speechParams.get(key)));
        }

        // Utterance ID, stream, and volume override item params.
        params.put(Engine.KEY_PARAM_UTTERANCE_ID, mCurrentFeedbackItem.getUtteranceId());
        params.put(Engine.KEY_PARAM_STREAM, String.valueOf(DEFAULT_STREAM));
        params.put(EngineCompatUtils.KEY_PARAM_VOLUME, String.valueOf(mSpeechVolume));

        final float pitch =
                mSpeechPitch * (mUseIntonation ? parseFloatParam(params, SpeechParam.PITCH, 1) : 1);
        final float rate =
                mSpeechRate * (mUseIntonation ? parseFloatParam(params, SpeechParam.RATE, 1) : 1);
        final String text;
        if (shouldSilenceSpeech(mCurrentFeedbackItem) || TextUtils.isEmpty(fragment.getText())) {
            text = null;
        } else {
            text = fragment.getText().toString();
        }

        LogUtils.log(this, Log.VERBOSE, "Speaking fragment text \"%s\"", text);

        // It's okay if the utterance is empty, the fail-over TTS will
        // immediately call the fragment completion listener. This process is
        // important for things like continuous reading.
        mFailoverTts.speak(text, pitch, rate, params);

        if (mTtsOverlay != null) {
            mTtsOverlay.speak(text);
        }

        return true;
    }

    /**
     * Plays all earcons stored in a {@link FeedbackFragment}.
     *
     * @param fragment The fragment to process
     */
    private void playEarconsFromFragment(FeedbackFragment fragment) {
        final Bundle nonSpeechParams = fragment.getNonSpeechParams();
        final float earconRate = nonSpeechParams.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);
        final float earconVolume = nonSpeechParams.getFloat(
                Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);

        for (int keyResId : fragment.getEarcons()) {
            mFeedbackController.playAuditory(keyResId, earconRate, earconVolume, 0);
        }
    }

    /**
     * Produces all haptic feedback stored in a {@link FeedbackFragment}.
     *
     * @param fragment The fragment to process
     */
    private void playHapticsFromFragment(FeedbackFragment fragment) {
        for (int keyResId : fragment.getHaptics()) {
            mFeedbackController.playHaptic(keyResId);
        }
    }

    /**
     * @return The utterance ID, or -1 if the ID is invalid.
     */
    static int parseUtteranceId(String utteranceId) {
        // Check for bad utterance ID. This should never happen.
        if (!utteranceId.startsWith(UTTERANCE_ID_PREFIX)) {
            LogUtils.log(SpeechController.class, Log.ERROR, "Bad utterance ID: %s", utteranceId);
            return -1;
        }

        try {
            return Integer.parseInt(utteranceId.substring(UTTERANCE_ID_PREFIX.length()));
        } catch (NumberFormatException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Called when transitioning from an idle state to a speaking state, e.g.
     * the queue was empty, there was no current speech, and a speech item was
     * added to the queue.
     *
     * @see #handleSpeechCompleted()
     */
    private void handleSpeechStarting() {
        // Always enable the proximity sensor when speaking.
        setProximitySensorState(true);

        if (mUseAudioFocus) {
            mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        }

        if (mIsSpeaking) {
            LogUtils.log(this, Log.ERROR, "Started speech while already speaking!");
        }

        mIsSpeaking = true;
    }

    /**
     * Called when transitioning from a speaking state to an idle state, e.g.
     * all queued utterances have been spoken and the last utterance has
     * completed.
     *
     * @see #handleSpeechStarting()
     */
    private void handleSpeechCompleted() {
        // If the screen is on, keep the proximity sensor on.
        setProximitySensorState(mScreenIsOn);

        if (mUseAudioFocus) {
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
        }

        if (!mIsSpeaking) {
            LogUtils.log(this, Log.ERROR, "Completed speech while already completed!");
        }

        mIsSpeaking = false;
    }

    /**
     * Clears the speech queue and completes the current speech item, if any.
     */
    private void clearCurrentAndQueuedUtterances() {
        mFeedbackQueue.clear();
        mCurrentFragmentIterator = null;

        if (mCurrentFeedbackItem != null) {
            final String utteranceId = mCurrentFeedbackItem.getUtteranceId();
            onFragmentCompleted(utteranceId, false /* success */, true /* advance */);
            mCurrentFeedbackItem = null;
        }
    }

    /**
     * Clears (and optionally posts) all pending completion actions.
     *
     * @param execute {@code true} to post actions to the handler.
     */
    private void clearUtteranceCompletionActions(boolean execute) {
        if (!execute) {
            mUtteranceCompleteActions.clear();
            return;
        }

        while (!mUtteranceCompleteActions.isEmpty()) {
            final UtteranceCompleteRunnable runnable = mUtteranceCompleteActions.poll().runnable;
            if (runnable != null) {
                mHandler.post(new CompletionRunner(runnable, STATUS_INTERRUPTED));
            }
        }

        // Don't call handleSpeechCompleted(), it will be called by the TTS when
        // it stops the current current utterance.
    }

    /**
     * Handles completion of a {@link FeedbackFragment}.
     * <p>
     *
     * @param utteranceId The ID of the {@link FeedbackItem} the fragment belongs to.
     * @param success Whether the fragment was spoken successfully.
     * @param advance Whether to advance to the next queue item.
     */
    private void onFragmentCompleted(String utteranceId, boolean success, boolean advance) {
        final int utteranceIndex = SpeechController.parseUtteranceId(utteranceId);
        final boolean interrupted = (mCurrentFeedbackItem != null)
                && (!mCurrentFeedbackItem.getUtteranceId().equals(utteranceId));

        final int status;

        if (interrupted) {
            status = STATUS_INTERRUPTED;
        } else if (success) {
            status = STATUS_SPOKEN;
        } else {
            status = STATUS_ERROR;
        }

        // Process the next fragment for this FeedbackItem if applicable.
        if ((status == STATUS_SPOKEN) && processNextFragmentInternal()) {
            return;
        } else {
            // If speaking resulted in an error, was ultimately interrupted, or
            // there are no additional fragments to speak as part of the current
            // FeedbackItem, finish processing of this utterance.
            onUtteranceCompleted(utteranceIndex, status, interrupted, advance);
        }
    }

    /**
     * Handles the completion of an {@link Utterance}/{@link FeedbackItem}.
     *
     * @param utteranceIndex The ID of the utterance that has completed.
     * @param status One of {@link SpeechController#STATUS_ERROR},
     *            {@link SpeechController#STATUS_INTERRUPTED}, or
     *            {@link SpeechController#STATUS_SPOKEN}
     * @param interrupted {@code true} if the utterance was interrupted, {@code false} otherwise
     * @param advance Whether to advance to the next queue item.
     */
    private void onUtteranceCompleted(
            int utteranceIndex, int status, boolean interrupted, boolean advance) {
        while (!mUtteranceCompleteActions.isEmpty()
                && (mUtteranceCompleteActions.peek().utteranceIndex <= utteranceIndex)) {
            final UtteranceCompleteRunnable runnable = mUtteranceCompleteActions.poll().runnable;
            if (runnable != null) {
                mHandler.post(new CompletionRunner(runnable, status));
            }
        }

        if (mSpeechListener != null) {
            mSpeechListener.onUtteranceCompleted(utteranceIndex, status);
        }

        if (interrupted) {
            // We finished an utterance, but we weren't expecting to see a
            // completion. This means we interrupted a previous utterance and
            // can safely ignore this callback.
            LogUtils.log(this, Log.VERBOSE, "Interrupted %d with %s", utteranceIndex,
                    mCurrentFeedbackItem.getUtteranceId());
            return;
        }

        if (advance && !speakNextItem()) {
            handleSpeechCompleted();
        }
    }

    private void onTtsInitialized(boolean wasSwitchingEngines) {
        // The previous engine may not have shut down correctly, so make sure to
        // clear the "current" speech item.
        if (mCurrentFeedbackItem != null) {
            onFragmentCompleted(mCurrentFeedbackItem.getUtteranceId(),
                    false /* success */, false /* advance */);
            mCurrentFeedbackItem = null;
        }

        if (wasSwitchingEngines) {
            speakCurrentEngine();
        } else if (!mFeedbackQueue.isEmpty()) {
            speakNextItem();
        }
    }

    /**
     * Removes and speaks the next {@link FeedbackItem} in the queue,
     * interrupting the current utterance if necessary.
     *
     * @return {@code false} if there are no more queued speech items.
     */
    private boolean speakNextItem() {
        final FeedbackItem previousItem = mCurrentFeedbackItem;
        final FeedbackItem nextItem = (mFeedbackQueue.isEmpty() ? null
                : mFeedbackQueue.removeFirst());

        mCurrentFeedbackItem = nextItem;

        if (nextItem == null) {
            LogUtils.log(this, Log.VERBOSE, "No next item, stopping speech queue");
            return false;
        }

        if (previousItem == null) {
            handleSpeechStarting();
        }

        mCurrentFragmentIterator = nextItem.getFragments().iterator();
        speakNextItemInternal(nextItem);
        return true;
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
     * Enables/disables the proximity sensor. The proximity sensor should be
     * disabled when not in use to save battery.
     * <p>
     * This is a no-op if the user has turned off the "silence on proximity"
     * preference.
     *
     * @param enabled {@code true} if the proximity sensor should be enabled,
     *            {@code false} otherwise.
     */
    // TODO(caseyburkhardt): Rewrite for readability.
    private void setProximitySensorState(boolean enabled) {
        if (mProximitySensor != null) {
            // Should we be using the proximity sensor at all?
            if (!mSilenceOnProximity) {
                mProximitySensor.stop();
                mProximitySensor = null;
                return;
            }

            if (!TalkBackService.isServiceActive()) {
                mProximitySensor.stop();
                return;
            }
        } else {
            // Do we need to initialize the proximity sensor?
            if (enabled && mSilenceOnProximity) {
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

    private final Handler mHandler = new Handler();

    /**
     * Handles preference changes that affect speech.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    if ((key == null) || (prefs == null)) {
                        return;
                    }

                    reloadPreferences(prefs);
                }
            };

    private final FailoverTtsListener mFailoverTtsListener = new FailoverTtsListener() {
        @Override
        public void onTtsInitialized(boolean wasSwitchingEngines) {
            SpeechController.this.onTtsInitialized(wasSwitchingEngines);
        }

        @Override
        public void onUtteranceCompleted(String utteranceId, boolean success) {
            // Utterances from FailoverTts are considered fragments in SpeechController
            SpeechController.this.onFragmentCompleted(utteranceId, success, true /* advance */);
        }
    };

    private final AudioManager.OnAudioFocusChangeListener
            mAudioFocusListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    LogUtils.log(SpeechController.this, Log.DEBUG, "Saw audio focus change: %d",
                            focusChange);
                }
            };

    private final TalkBackService.ServiceStateListener
            mServiceStateListener = new TalkBackService.ServiceStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState newState) {
            if (newState == ServiceState.ACTIVE) {
                setProximitySensorState(true);
            } else if (newState == ServiceState.SUSPENDED) {
                setProximitySensorState(false);
            }
        }
    };

    public interface SpeechControllerListener {
        public void onUtteranceStarted(int utteranceIndex);

        public void onUtteranceCompleted(int utteranceIndex, int status);
    }

    /**
     * An action that should be performed after a particular utterance index
     * completes.
     */
    private static class UtteranceCompleteAction implements Comparable<UtteranceCompleteAction> {
        public UtteranceCompleteAction(int utteranceIndex, UtteranceCompleteRunnable runnable) {
            this.utteranceIndex = utteranceIndex;
            this.runnable = runnable;
        }

        /**
         * The minimum utterance index that must complete before this action
         * should be performed.
         */
        public int utteranceIndex;

        /** The action to execute. */
        public UtteranceCompleteRunnable runnable;

        @Override
        public int compareTo(UtteranceCompleteAction another) {
            return (utteranceIndex - another.utteranceIndex);
        }
    }

    public static class CompletionRunner implements Runnable {
        private final UtteranceCompleteRunnable mRunnable;
        private final int mStatus;

        public CompletionRunner(UtteranceCompleteRunnable runnable, int status) {
            mRunnable = runnable;
            mStatus = status;
        }

        @Override
        public void run() {
            mRunnable.run(mStatus);
        }
    }

    public interface UtteranceCompleteRunnable {
        public void run(int status);
    }
}
