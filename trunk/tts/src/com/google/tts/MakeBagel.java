package com.google.tts;

import com.google.tts.TTS.InitListener;
import com.google.tts.TTS.SpeechCompletedListener;

import java.util.HashMap;

import android.app.Activity;
import android.media.AudioManager;
import android.util.Log;


public class MakeBagel extends Activity {
  TTS mTts;
  String message;

  SpeechCompletedListener completionListener = new SpeechCompletedListener() {
    @Override
    public void onSpeechCompleted() {
      Log.e("Debug", "TTS speech finished!");
        mTts.shutdown();
        finish();
    }
  };

  @Override
  protected void onResume() {
    super.onResume();
    message = this.getIntent().getStringExtra("message");
    mTts = new TTS(this, new InitListener() {
      @Override
      public void onInit(int status) {
        mTts.setOnSpeechCompletedListener(completionListener);
        mTts.speak(message, 0, null);
      }
    }, true);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mTts != null){
      mTts.shutdown();
    }
  }
  
}
