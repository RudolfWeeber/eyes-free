/*
 * Copyright (C) 2010 The IDEAL Group
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

package com.ideal.webreader;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.view.View;
import android.view.MotionEvent;
import android.gesture.GestureLibraries;
import android.gesture.GestureOverlayView;
import android.gesture.Gesture;
import android.gesture.GestureLibrary;
import java.io.File;
import java.util.HashMap;

/**
 * This wizard walks the user step-by-step in creating their own set of
 * gestures.
 */
public class CreateGestureWizardActivity extends Activity {
    private static final float LENGTH_THRESHOLD = 120.0f;

    private Gesture mGesture;

    private String mCurrentGesture = "";

    private TextToSpeech mTts;

    private GestureLibrary mStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCurrentGesture = "";
        final File storeFile = new File(Environment.getExternalStorageDirectory()
                + "/ideal-webaccess/gestures");
        mStore = GestureLibraries.fromFile(storeFile);

        GestureOverlayView overlay = new GestureOverlayView(this);
        overlay.addOnGestureListener(new GesturesProcessor());
        setContentView(overlay);

        mTts = new TextToSpeech(this, new OnInitListener() {
            @Override
            public void onInit(int status) {
                drawNextGesture();
            }
        });
    }

    public void addGesture(View v) {
        if (mGesture != null) {
            final CharSequence name = mCurrentGesture;
            mStore.addGesture(name.toString(), mGesture);
        }
    }

    public void cancelGesture(View v) {
        setResult(RESULT_CANCELED);
        finish();
    }

    private class GesturesProcessor implements GestureOverlayView.OnGestureListener {
        public void onGestureStarted(GestureOverlayView overlay, MotionEvent event) {
            mGesture = null;
        }

        public void onGesture(GestureOverlayView overlay, MotionEvent event) {
        }

        public void onGestureEnded(GestureOverlayView overlay, MotionEvent event) {
            mGesture = overlay.getGesture();
            if (mGesture.getLength() < LENGTH_THRESHOLD) {
                overlay.clear(false);
            }
            addGesture(null);
            drawNextGesture();
        }

        public void onGestureCancelled(GestureOverlayView overlay, MotionEvent event) {
        }
    }

    private void drawNextGesture() {
        if (mCurrentGesture.equals("")) {
            mCurrentGesture = "next";
        } else if (mCurrentGesture.equals("next")) {
            mCurrentGesture = "previous";
        } else if (mCurrentGesture.equals("previous")) {
            mCurrentGesture = "up";
        } else if (mCurrentGesture.equals("up")) {
            mCurrentGesture = "down";
        } else if (mCurrentGesture.equals("down")) {
            mCurrentGesture = "action";
        } else if (mCurrentGesture.equals("action")) {
            mCurrentGesture = "read all";
        } else if (mCurrentGesture.equals("read all")) {
            mCurrentGesture = "switch web reader mode";
        } else if (mCurrentGesture.equals("switch web reader mode")) {
            mCurrentGesture = "add bookmark";
        } else if (mCurrentGesture.equals("add bookmark")) {
            mCurrentGesture = "get definition";
        } else if (mCurrentGesture.equals("get definition")) {
            HashMap<String, String> ttsParams = new HashMap<String, String>();
            // The utterance ID doesn't matter; we don't really care what was
            // said, just that the TTS has finished speaking.
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "done");
            mTts.setOnUtteranceCompletedListener(new OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    mStore.save();
                    finish();
                }
            });
            mTts.speak("All gestures have been defined.", 2, ttsParams);
            return;
        }
        mTts.speak("Draw the gesture for " + mCurrentGesture, 2, null);
    }

}
