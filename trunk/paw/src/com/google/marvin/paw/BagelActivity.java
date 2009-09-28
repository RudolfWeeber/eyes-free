package com.google.marvin.paw;

import java.util.HashMap;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;

public class BagelActivity extends Activity {
  TextToSpeech mTts;
  String message;

  OnUtteranceCompletedListener completionListener = new OnUtteranceCompletedListener() {
    @Override
    public void onUtteranceCompleted(String utteranceId) {
      if (utteranceId.equals("BAGEL_MESSAGE")) {
        mTts.shutdown();
        finish();
      }
    }
  };

  @Override
  protected void onResume() {
    super.onResume();
    message = this.getIntent().getStringExtra("message");
    mTts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
      @Override
      public void onInit(int status) {
        mTts.setOnUtteranceCompletedListener(completionListener);
        HashMap<String, String> params = new HashMap<String, String>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "BAGEL_MESSAGE");
        params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String
            .valueOf(AudioManager.STREAM_NOTIFICATION));
        mTts.speak(message, 0, params);
      }
    });
  }

}
