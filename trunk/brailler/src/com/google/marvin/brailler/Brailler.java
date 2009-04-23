package com.google.marvin.brailler;

import com.google.tts.TTS;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

/**
 * Demonstration of using the touch screen as a Braille-input device.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class Brailler extends Activity {
  private BraillerView braillerView;
  public TTS tts;
  private Brailler self;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tts = new TTS(this, ttsInitListener, true);
        self = this;
    }
    
    private TTS.InitListener ttsInitListener = new TTS.InitListener() {
      public void onInit(int version) {        
        if (braillerView != null) {
          braillerView.setVisibility(View.GONE);
        }
        braillerView = new BraillerView(self);
        setContentView(braillerView);
      }
    };
    

}