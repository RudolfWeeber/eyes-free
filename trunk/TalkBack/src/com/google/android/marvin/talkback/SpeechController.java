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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Handles text-to-speech.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
class SpeechController {
    /** Prefix for utterance IDs. */
    private static final String UTTERANCE_ID_PREFIX = "talkback_";

    /** Queuing mode - queue the utterance to be spoken. */
    public static final int QUEUING_MODE_QUEUE = TextToSpeech.QUEUE_ADD;

    /** Queuing mode - interrupt the current utterance. */
    public static final int QUEUING_MODE_INTERRUPT = TextToSpeech.QUEUE_FLUSH;

    /** Queuing mode - uninterruptible utterance. */
    public static final int QUEUING_MODE_UNINTERRUPTIBLE = 2;

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

    /** Handler used to return to the main thread from the TTS thread. */
    private final Handler mHandler = new Handler();

    /** The parent context. */
    private final Context mContext;

    /** The audio manager, used to query ringer volume. */
    private final AudioManager mAudioManager;
    
    /** The telephone manager, used to query ringer state. */
    private final TelephonyManager mTelephonyManager;

    /** Handles connecting to BT headsets. */
    private BluetoothHandler mBluetoothHandler;

    /** The package name of the default TTS engine. */
    private String mDefaultTtsEngine;

    /** The TTS engine. */
    private TextToSpeech mTts;

    /** A temporary TTS used for switching engines. */
    private TextToSpeech mTempTts;

    /** Whether the TTS is currently speaking an uninterruptible utterance. */
    private boolean mUninterruptible;

    /**
     * The next utterance index; each utterance id will be constructed from this
     * ever-increasing index.
     */
    private int mNextUtteranceIndex = 0;

    public SpeechController(Context context, boolean deviceIsPhone) {
        mContext = context;
        mContext.registerReceiver(mMediaStateMonitor, mMediaStateMonitor.getFilter());

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mUninterruptible = false;

        mTts = new TextToSpeech(context, mTtsInitListener);
    }

    /**
     * Sets whether speech will be output through a Bluetooth headset when
     * available.
     * 
     * @param enabled {@code true} to output speech through a Bluetooth headset.
     */
    public void setBluetoothEnabled(boolean enabled) {
        if (enabled && mBluetoothHandler == null) {
            mBluetoothHandler = new BluetoothHandler(5000, mBluetoothListener);
        } else if (!enabled && mBluetoothHandler != null) {
            mBluetoothHandler.stopSco();
            mBluetoothHandler.stop();
            mBluetoothHandler = null;
        }
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The clean up is replacing
     * special strings with predefined mappings and reordering of some RegExp
     * matches to improve presentation. The <code>queueMode</code> determines if
     * speaking the event interrupts the speaking of previous events or is
     * queued.
     * 
     * @param text The text to speak.
     * @param queueMode The queue mode to use for speaking.
     */
    public void cleanUpAndSpeak(CharSequence text, int queueMode) {
        if (TextUtils.isEmpty(text)) {
            return;
        }

        // Reuse the global instance of speech parameters.
        final HashMap<String, String> speechParams = mSpeechParametersMap;
        speechParams.clear();

        // Give every utterance an unique identifier with an increasing index.
        final String utteranceId = UTTERANCE_ID_PREFIX + mNextUtteranceIndex;
        speechParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);

        if (isDeviceRinging()) {
            manageRingerVolume(mNextUtteranceIndex);
        }

        if (queueMode == SpeechController.QUEUING_MODE_UNINTERRUPTIBLE) {
            queueMode = TextToSpeech.QUEUE_FLUSH;
            mUninterruptible = true;
            addUtteranceCompleteAction(mNextUtteranceIndex, mClearUninterruptible);
        } else if (queueMode == TextToSpeech.QUEUE_FLUSH && mUninterruptible) {
            LogUtils.log(Log.DEBUG, "Can't interrupt right now, queueing instead.");
            queueMode = TextToSpeech.QUEUE_ADD;
        }

        manageBluetoothConnection(speechParams);

        mNextUtteranceIndex++;

        LogUtils.log(TalkBackService.class, Log.VERBOSE, "Speaking with queue mode %d: \"%s\"",
                queueMode, text);

        mTts.speak(text.toString(), queueMode, speechParams);
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
     * Method that's called by TTS whenever an utterance is completed. Do common
     * tasks and execute any UtteranceCompleteActions associate with this
     * utterance index (or an earlier index, in case one was accidentally
     * dropped).
     * 
     * @param utteranceId The utteranceId from the onUtteranceCompleted callback
     *            - we expect this to consist of UTTERANCE_ID_PREFIX followed by
     *            the utterance index.
     */
    private void handleUtteranceCompleted(String utteranceId) {
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
    }

    /**
     * Decreases the ringer volume and registers a listener for the event of
     * completing to speak which restores the volume to its previous level.
     * 
     * @param utteranceIndex the index of this utterance, used to schedule an
     *            utterance completion action.
     */
    private void manageRingerVolume(int utteranceIndex) {
        final int currentRingerVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        final int maxRingerVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        final int lowerEnoughVolume = Math.max((maxRingerVolume / 3), (currentRingerVolume / 2));

        mAudioManager.setStreamVolume(AudioManager.STREAM_RING, lowerEnoughVolume, 0);

        addUtteranceCompleteAction(utteranceIndex, new Runnable() {
            @Override
            public void run() {
                mAudioManager.setStreamVolume(AudioManager.STREAM_RING, currentRingerVolume, 0);
            }
        });
    }

    /**
     * Try to switch the TTS engine.
     * 
     * @param engine The package name of the desired TTS engine
     */
    private void setTtsEngine(String engine) {
        mTempTts = new TextToSpeech(mContext, mTtsChangeListener, engine);
    }

    public void interrupt() {
        mTts.stop();
    }

    public void shutdown() {
        mTts.shutdown();

        if (mMediaStateMonitor != null) {
            mContext.unregisterReceiver(mMediaStateMonitor);
        }
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
                    handleUtteranceCompleted(utteranceId);
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
                    if (status == TextToSpeech.SUCCESS) {
                        if (mTts != null) {
                            mTts.shutdown();
                        }

                        mTts = mTempTts;
                    }
                }
            };

    /**
     * After initialization has completed, sets up an
     * OnUtteranceCompletedListener, registers earcons, and records the default
     * TTS engine.
     */
    private final TextToSpeech.OnInitListener mTtsInitListener = new TextToSpeech.OnInitListener() {
        @Override
        public void onInit(int status) {
            if (status != TextToSpeech.SUCCESS) {
                LogUtils.log(TalkBackService.class, Log.ERROR, "TTS init failed.");
                return;
            }

            // TODO(alanv): There is a race condition whereby this can execute
            // before mTts is set, resulting in a NPE.
            mTts.setOnUtteranceCompletedListener(mUtteranceCompletedListener);
            mDefaultTtsEngine = mTts.getDefaultEngine();

            final String mediaState = Environment.getExternalStorageState();

            if (!mediaState.equals(Environment.MEDIA_MOUNTED)
                    && !mediaState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                setTtsEngine("com.google.android.tts");
            }
        }
    };

    private final BluetoothHandler.Listener mBluetoothListener = new BluetoothHandler.Listener() {
        @Override
        public void onConnectionComplete() {
            final BluetoothHandler bluetoothHandler = mBluetoothHandler;

            if (bluetoothHandler != null) {
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mBluetoothHandler.startSco();
            }
        }
    };

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

            // If the SD card is unmounted, switch to the default TTS engine.
            // TODO(alanv): Shouldn't the TTS service handle this automatically?
            if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                if (mDefaultTtsEngine != null) {
                    setTtsEngine(mDefaultTtsEngine);
                }
            } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                setTtsEngine("com.google.android.tts");
            }
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
}
