package com.google.tts;

import com.google.tts.TTS.InitListener;
import com.google.tts.TTS.SpeechCompletedListener;

import java.util.Locale;

import android.app.Activity;
import android.util.Log;


public class MakeBagel extends Activity {
  private TTS mTts;
  private String message;
  private String langRegionString;
  private MakeBagel self;

  SpeechCompletedListener completionListener = new SpeechCompletedListener() {
    public void onSpeechCompleted() {
      mTts.shutdown();
      self.setResult(Activity.RESULT_OK);
      finish();
    }
  };

  @Override
  protected void onResume() {
    super.onResume();
    self = this;
    self.setResult(Activity.RESULT_CANCELED);
    message = this.getIntent().getStringExtra("message");
    String language = this.getIntent().getStringExtra("language") + "";
    String country = this.getIntent().getStringExtra("country") + "";
    String variant = this.getIntent().getStringExtra("variant") + "";
    Locale loc = new Locale(language, country, variant);
    language = loc.getISO3Language();
    country = loc.getISO3Country();
    langRegionString = "";
    if (language.length() == 3) {
      if (country.length() == 3) {
        langRegionString = language + "-" + country;
      } else {
        langRegionString = language;
      }
    }

    mTts = new TTS(this, new InitListener() {
      public void onInit(int status) {
        mTts.setOnSpeechCompletedListener(completionListener);
        if (langRegionString.length() > 0) {
          mTts.setLanguage(langRegionString);
        }
        mTts.speak(message, 0, null);
      }
    }, true);
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (mTts != null) {
      mTts.shutdown();
    }
  }

}
