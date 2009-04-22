package com.google.marvin.slidetext;


import com.google.tts.TTS;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;

/**
 * Enables the user to type text without looking at the phone.
 * 
 * There are 4 layers, with each layer having 8 characters arranged in a ring.
 * Each layer has 2 entry points, arranged at opposite sides of the ring. When
 * you first touch down, you choose which layer you want to enter by selecting
 * either of the entry points. The entry points are letters themselves, so after
 * entering a layer, if you immediately lift up, you will type the character you
 * entered on. Otherwise, you can proceed through the ring, move over the letter
 * that you want, and lift up.
 * 
 * ASCII diagrams of the layers: Layer 0: a i q _ y u m e
 * 
 * Layer 1: a b c h d g f e
 * 
 * Layer 2: p i j o k n m l
 * 
 * Layer 3: w x q v r u t s
 * 
 * Layer 4: , ! MODE _ y . ? z
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class SlideText extends Activity {

  private SlideText self;
  private SlideTextView mView;
  public TTS tts;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    tts = new TTS(this, ttsInitListener, true);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
  }


  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      String pkgName = SlideText.class.getPackage().getName();

      tts.addSpeech("Nothing to delete", pkgName, R.raw.nothing_to_delete);

      if (mView != null) {
        mView.setVisibility(View.GONE);
      }
      mView = new SlideTextView(self);
      self.setContentView(mView);
    }
  };
  
  @Override
  protected void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }
}
