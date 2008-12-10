/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.marvin.shell;

import com.google.tts.TTS;
import com.google.tts.TTSEngine;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.View;

/**
 * Shell An alternate home screen that is designed to be friendly for eyes-free
 * use
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MarvinShell extends Activity {
  private AppSelectView mView;
  public TTS tts;
  public boolean isFocused;
  private MarvinShell self;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    self = this;
    tts = new TTS(this, ttsInitListener, true);
    mView = null;
    isFocused = true;
  }

  @Override
  public void onResume() {
    resetTTS();
    if (isFocused) {
      super.onResume();
      return;
    }
    tts.speak("Press menu to unlock", 0, null);
    mView.currentValue = -1;

    super.onResume();
  }


  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    isFocused = hasFocus;
    if (hasFocus && (mView != null)) {
      String message = "Home";
      if (mView.airplaneModeEnabled()) {
        message = "Airplane mode";
      }
      tts.speak(message, 0, null);
      mView.currentValue = -1;
    }
    super.onWindowFocusChanged(hasFocus);
  }

  @Override
  protected void onDestroy() {
    tts.shutdown();
    super.onDestroy();
  }

  private void resetTTS() {
    tts.setEngine(TTSEngine.PRERECORDED_WITH_ESPEAK);

    String pkgName = MarvinShell.class.getPackage().getName();
    tts.addSpeech("[marvin intro]", pkgName, R.raw.marvin_intro);
    tts.addSpeech("Home", pkgName, R.raw.home);
    tts.addSpeech("Press menu to unlock", pkgName, R.raw.press_menu_to_unlock);
    tts.addSpeech("Compass", pkgName, R.raw.compass);
    tts.addSpeech("Make a call", pkgName, R.raw.make_a_call);
    tts.addSpeech("Battery", pkgName, R.raw.battery);
    tts.addSpeech("Text input", pkgName, R.raw.text_input);
    tts.addSpeech("Android Says", pkgName, R.raw.android_says);
    tts.addSpeech("Application not installed.", pkgName, R.raw.application_not_installed);
    tts.addSpeech("January", pkgName, R.raw.january);
    tts.addSpeech("February", pkgName, R.raw.february);
    tts.addSpeech("March", pkgName, R.raw.march);
    tts.addSpeech("April", pkgName, R.raw.april);
    tts.addSpeech("May", pkgName, R.raw.may);
    tts.addSpeech("June", pkgName, R.raw.june);
    tts.addSpeech("July", pkgName, R.raw.july);
    tts.addSpeech("August", pkgName, R.raw.august);
    tts.addSpeech("September", pkgName, R.raw.september);
    tts.addSpeech("October", pkgName, R.raw.october);
    tts.addSpeech("November", pkgName, R.raw.november);
    tts.addSpeech("December", pkgName, R.raw.december);
    tts.addSpeech("midnight", pkgName, R.raw.midnight);
    tts.addSpeech("noon", pkgName, R.raw.noon);
    tts.addSpeech("AM", pkgName, R.raw.am);
    tts.addSpeech("PM", pkgName, R.raw.pm);
    tts.addSpeech("Airplane mode", pkgName, R.raw.airplane_mode);
    tts.addSpeech("enabled", pkgName, R.raw.enabled);
    tts.addSpeech("disabled", pkgName, R.raw.disabled);
    tts.addSpeech("Camera", pkgName, R.raw.camera);
    tts.addSpeech("Weather", pkgName, R.raw.weather);
  }

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      resetTTS();
      tts.speak("[marvin intro]", 0, null);

      if (mView != null) {
        mView.setVisibility(View.GONE);
      }
      mView = new AppSelectView(self);
      self.setContentView(mView);
    }
  };
}
