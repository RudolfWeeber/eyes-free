/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * This class provides text-to-speech for the {@link LatinIMETutorial} class.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TutorialReader
        implements TextToSpeech.OnInitListener, TextToSpeech.OnUtteranceCompletedListener {
    private final AccessibilityManager mAccessibilityManager;
    private final LinkedList<ReaderUnit> mSpeechQueue;
    private final HashMap<String, String> mParams;
    private final TextToSpeech mTts;

    private ReaderListener mListener;
    private boolean mInitialized;

    public TutorialReader(Context context) {
        mInitialized = false;

        mAccessibilityManager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        mSpeechQueue = new LinkedList<ReaderUnit>();
        mParams = new HashMap<String, String>();
        mTts = new TextToSpeech(context, this);
    }

    public void setListener(ReaderListener listener) {
        mListener = listener;
    }

    public void release() {
        mInitialized = false;

        mTts.stop();
        mTts.shutdown();
    }

    public void clear() {
        if (mAccessibilityManager.isEnabled()) {
            mAccessibilityManager.interrupt();
        }

        synchronized (mSpeechQueue) {
            mSpeechQueue.clear();
        }

        mTts.stop();
    }

    public void queue(String text, int utteranceId) {
        LatinTutorialLogger.log(Log.DEBUG, "queue(%s, %s)", text, utteranceId);

        ReaderUnit readerUnit = new ReaderUnit(text, utteranceId);

        boolean initialized = false;
        boolean wasEmpty = false;

        synchronized (mSpeechQueue) {
            initialized = mInitialized;
            wasEmpty = mSpeechQueue.isEmpty();
            mSpeechQueue.addLast(readerUnit);
        }

        if (initialized && wasEmpty) {
            next();
        }
    }

    public void next() {
        LatinTutorialLogger.log(Log.DEBUG, "next()");

        boolean initialized = false;
        ReaderUnit nextUnit = null;

        synchronized (mSpeechQueue) {
            initialized = mInitialized;

            if (!mSpeechQueue.isEmpty()) {
                nextUnit = mSpeechQueue.getFirst();
            }
        }

        if (initialized && nextUnit != null) {
            String strUtteranceId = Integer.toString(nextUnit.utteranceId);
            HashMap<String, String> params = new HashMap<String, String>(mParams);
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, strUtteranceId);
            mTts.speak(nextUnit.text, TextToSpeech.QUEUE_FLUSH, params);
        }
    }

    @Override
    public void onInit(int status) {
        LatinTutorialLogger.log(Log.DEBUG, "onInit(%d)", status);

        mTts.setOnUtteranceCompletedListener(this);

        switch (status) {
            case TextToSpeech.SUCCESS:
                synchronized (mSpeechQueue) {
                    mInitialized = true;
                    next();
                }
                break;
        }
    }

    @Override
    public void onUtteranceCompleted(String utteranceId) {
        LatinTutorialLogger.log(Log.DEBUG, "onUtteranceCompleted(%s)", utteranceId);

        synchronized (mSpeechQueue) {
            if (!mSpeechQueue.isEmpty()) {
                mSpeechQueue.removeFirst();
                next();
            }
        }

        if (mListener == null) {
            return;
        }

        try {
            int intUtteranceId = Integer.parseInt(utteranceId);
            mListener.onUtteranceCompleted(intUtteranceId);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private class ReaderUnit {
        final String text;
        final int utteranceId;

        public ReaderUnit(String text, int utteranceId) {
            this.text = text;
            this.utteranceId = utteranceId;
        }
    }

    public interface ReaderListener {
        public void onUtteranceCompleted(int utteranceId);
    }
}
