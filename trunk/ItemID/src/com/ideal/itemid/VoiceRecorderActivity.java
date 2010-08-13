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

package com.ideal.itemid;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;

/**
 * Main Activity for IDEAL Item ID Label Maker. Enables users to record
 * a message and then generate a QR code that can be printed out.
 * Scanning this QR code later will cause the recorded message to be
 * played back to the user.
 */
public class VoiceRecorderActivity extends Activity {
    final public long FILE_TIMESTAMP = System.currentTimeMillis();

    private VoiceRecorderRecordingView mRecordingView;

    private VoiceRecorderConfirmationView mConfirmationView;

    public TextToSpeech mTts;

    public Vibrator mVibe;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mVibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        mTts = new TextToSpeech(this, new OnInitListener() {
            @Override
            public void onInit(int status) {
                mTts.addEarcon("[tock]", "com.ideal.itemid", R.raw.tock_snd);
                showRecordingView();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mTts != null) {
            mTts.shutdown();
        }
    }

    public void showConfirmationView() {
        mConfirmationView = new VoiceRecorderConfirmationView(this);
        this.setContentView(mConfirmationView);
    }

    public void showRecordingView() {
        mRecordingView = new VoiceRecorderRecordingView(this);
        this.setContentView(mRecordingView);
    }
}
