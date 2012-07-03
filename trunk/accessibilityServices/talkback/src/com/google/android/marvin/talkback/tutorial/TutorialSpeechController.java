/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback.tutorial;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.google.android.marvin.utils.WeakReferenceHandler;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Handles queuing speech for the tutorial. Prevents the currently active
 * {@link android.accessibilityservice.AccessibilityService} from interrupting
 * the tutorial.
 */
@TargetApi(16)
class TutorialSpeechController {
    private static final String TAG = TutorialSpeechController.class.getSimpleName();
    private static final String UTTERANCE_PREFIX = "TUTORIAL_";

    private final SpeechHandler mHandler = new SpeechHandler(this);
    private final LinkedList<String> mQueuedText = new LinkedList<String>();
    private final LinkedList<Integer> mQueuedIds = new LinkedList<Integer>();
    private final HashMap<String, String> mSpeechParams = new HashMap<String, String>();
    private final LinkedList<SpeechListener> mListeners = new LinkedList<SpeechListener>();

    private Context mContext;
    private TextToSpeech mTts;

    private String mLastUtteranceId;
    private boolean mTtsReady;

    public TutorialSpeechController(Context context) {
        mContext = context;
        mTts = new TextToSpeech(context, mInitListener);
        mTts.setOnUtteranceProgressListener(mProgressListener);
    }

    public void addListener(SpeechListener listener) {
        mListeners.add(listener);
    }

    public void speak(String text, int id, boolean shouldRepeat) {
        mHandler.speak(text, id, shouldRepeat);
    }

    public void interrupt() {
        mHandler.interrupt();

        try {
            mTts.stop();
        } catch (Exception e) {
            // This means the TTS service died.
            resetTtsEngine();
        }
    }

    public void shutdown() {
        mHandler.interrupt();

        mContext = null;
        mQueuedText.clear();
        mQueuedIds.clear();
        mListeners.clear();
        try {
            mTts.stop();
            mTts.shutdown();
        } catch (Exception e) {
            // This means the TTS service died. Doesn't matter.
        }
    }

    private void resetTtsEngine() {
        mQueuedText.clear();

        while (!mQueuedIds.isEmpty()) {
            mHandler.postDone(mQueuedIds.removeFirst());
        }

        if (mContext != null) {
            mTts = new TextToSpeech(mContext, mInitListener);
            mTts.setOnUtteranceProgressListener(mProgressListener);
        }
    }

    private boolean speakImmediately(String text, int id) {
        final String utteranceId = UTTERANCE_PREFIX + id;
        final int result;

        boolean wasFirstUtterance = false;

        synchronized (mTts) {
            if (mLastUtteranceId == null) {
                wasFirstUtterance = true;
            }

            mLastUtteranceId = utteranceId;
            mSpeechParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            result = mTts.speak(text, TextToSpeech.QUEUE_ADD, mSpeechParams);
        }

        if (wasFirstUtterance) {
            mHandler.postStartSpeaking();
        }

        return (result == TextToSpeech.SUCCESS);
    }

    private void readQueuedSpeech() {
        synchronized (mQueuedText) {
            while (!mQueuedText.isEmpty()) {
                final String queuedText = mQueuedText.removeFirst();
                final int queuedId = mQueuedIds.removeFirst();

                if (!speakImmediately(queuedText, queuedId)) {
                    mHandler.postDone(queuedId);
                    resetTtsEngine();
                }
            }

            mTtsReady = true;
        }
    }

    private final OnInitListener mInitListener = new OnInitListener() {
        @Override
        public void onInit(int status) {
            readQueuedSpeech();
        }
    };

    private final UtteranceProgressListener mProgressListener = new UtteranceProgressListener() {
        @Override
        public void onStart(String utteranceId) {
            // Do nothing.
        }

        @Override
        public void onError(String utteranceId) {
            // Do nothing.
        }

        @Override
        public void onDone(String utteranceId) {
            final int id = parseId(utteranceId);

            if (id < 0) {
                Log.e(TAG, "Failed to parse utterance ID from " + utteranceId);
                return;
            }

            mHandler.postDone(id);

            boolean wasLastUtterance = false;

            synchronized (mTts) {
                if (utteranceId.equals(mLastUtteranceId)) {
                    mLastUtteranceId = null;
                    wasLastUtterance = true;
                }
            }

            if (wasLastUtterance) {
                mHandler.postDoneSpeaking();
            }
        }

        /**
         * Parses a resource identifier out of a prefixed utterance ID string.
         * Returns -1 if the utterance ID does not start with the correct prefix
         * or fails to parse.
         *
         * @param utteranceId A prefixed utterance ID string.
         * @return A resource identifier, or -1 on error.
         */
        private final int parseId(String utteranceId) {
            if (!utteranceId.startsWith(UTTERANCE_PREFIX)) {
                return -1;
            }

            final String strId = utteranceId.substring(UTTERANCE_PREFIX.length());

            try {
                return Integer.parseInt(strId);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }

            return -1;
        }
    };

    public interface SpeechListener {
        /**
         * Called after the specified resource identifier is spoken.
         *
         * @param id
         */
        public void onDone(int id);

        /**
         * Called when the controller starts speaking.
         */
        public void onStartSpeaking();

        /**
         * Called when the speech queue has been emptied and the controller has
         * stopped speaking.
         */
        public void onDoneSpeaking();
    }

    private static class SpeechHandler extends WeakReferenceHandler<TutorialSpeechController> {
        private static final int MSG_SPEAK = 1;
        private static final int MSG_START_SPEAKING = 2;
        private static final int MSG_DONE_SPEAKING = 3;
        private static final int MSG_DONE = 4;
        private static final int MSG_REPEAT = 5;

        private static final long REPEAT_DELAY = 3000;

        private String mShouldRepeatTextAfterDone = null;
        private int mShouldRepeatIdAfterDone = -1;

        public SpeechHandler(TutorialSpeechController parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, TutorialSpeechController parent) {
            switch (msg.what) {
                case MSG_SPEAK:
                    speakInternal(parent, (String) msg.obj, msg.arg1);
                    break;
                case MSG_START_SPEAKING:
                    startSpeakingInternal(parent);
                    break;
                case MSG_DONE_SPEAKING:
                    doneSpeakingInternal(parent);
                    break;
                case MSG_DONE:
                    doneInternal(parent, msg.arg1);
                    break;
                case MSG_REPEAT:
                    speakInternal(parent, (String) msg.obj, msg.arg1);
                    break;
            }
        }

        public void interrupt() {
            removeMessages(MSG_SPEAK);
            removeMessages(MSG_REPEAT);
        }

        public void speak(String text, int id, boolean shouldRepeat) {
            removeMessages(MSG_REPEAT);
            mShouldRepeatIdAfterDone = (shouldRepeat ? id : -1);
            obtainMessage(MSG_SPEAK, id, 0, text).sendToTarget();
        }

        public void postStartSpeaking() {
            sendEmptyMessage(MSG_START_SPEAKING);
        }

        public void postDoneSpeaking() {
            sendEmptyMessage(MSG_DONE_SPEAKING);
        }

        public void postDone(int id) {
            obtainMessage(MSG_DONE_SPEAKING, id, 0).sendToTarget();
        }

        private void speakInternal(TutorialSpeechController parent, String text, int id) {
            synchronized (parent.mQueuedText) {
                if (!parent.mTtsReady) {
                    parent.mQueuedText.addLast(text);
                    parent.mQueuedIds.add(id);
                } else {
                    parent.speakImmediately(text, id);
                }
            }
        }

        private void startSpeakingInternal(TutorialSpeechController parent) {
            synchronized (parent.mListeners) {
                for (SpeechListener listener : parent.mListeners) {
                    listener.onStartSpeaking();
                }
            }
        }

        private void doneSpeakingInternal(TutorialSpeechController parent) {
            synchronized (parent.mListeners) {
                for (SpeechListener listener : parent.mListeners) {
                    listener.onDoneSpeaking();
                }
            }
        }

        private void doneInternal(TutorialSpeechController parent, int id) {
            final String shouldRepeatText = mShouldRepeatTextAfterDone;
            final int shouldRepeatId = mShouldRepeatIdAfterDone;

            if ((shouldRepeatId > 0) && (id == shouldRepeatId)) {
                final Message speak = obtainMessage(MSG_REPEAT, shouldRepeatId, 0, shouldRepeatId);
                sendMessageDelayed(speak, REPEAT_DELAY);
            }

            synchronized (parent.mListeners) {
                for (SpeechListener listener : parent.mListeners) {
                    listener.onDone(id);
                }
            }
        }
    }
}
