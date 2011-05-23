// Copyright 2011 Google Inc. All Rights Reserved.

package com.marvin.preferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

/**
 * This is a simple activity that, when launched, broadcasts a command to reset TalkBack
 * 
 * @author credo@google.com (Tim Credo)
 */
public class ResetTalkBackActivity extends Activity {
    
    public static final String ACTION_RESET_TALKBACK_COMMAND = "com.google.android.marvin.talkback.ACTION_RESET_TALKBACK_COMMAND";

    TextToSpeech mTts;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(ACTION_RESET_TALKBACK_COMMAND);
        sendBroadcast(intent);
        mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    if (mTts != null) {
                        mTts.speak("TalkBack reset", TextToSpeech.QUEUE_FLUSH, null);
                        while (mTts.isSpeaking()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        mTts.shutdown();
                    }  
                }
                finish();
            }
        });
    }
}
