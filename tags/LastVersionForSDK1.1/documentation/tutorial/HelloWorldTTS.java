package com.myttsproject.helloworld;

import com.google.tts.TTS;

import android.app.Activity;
import android.os.Bundle;

public class HelloWorldTTS extends Activity {
	private TTS myTts;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        myTts = new TTS(this, ttsInitListener, true);
    }
    
    private TTS.InitListener ttsInitListener = new TTS.InitListener() {
        public void onInit(int version) {
          myTts.speak("Hello world", 0, null);
        }
      };
}