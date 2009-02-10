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

import com.google.marvin.shell.TouchGestureControlOverlay.Gesture;
import com.google.marvin.shell.TouchGestureControlOverlay.GestureListener;
import com.google.tts.TTS;
import com.google.tts.TTSEngine;

import java.util.HashMap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Shell An alternate home screen that is designed to be friendly for eyes-free
 * use
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MarvinShell extends Activity implements GestureListener {
  // private AppSelectView mView;
  public TTS tts;
  public boolean isFocused;
  private MarvinShell self;
  private AuditoryWidgets widgets;
  private HashMap<Gesture, MenuItem> items;

  private Vibrator vibe;
  private static final long[] VIBE_PATTERN = {0, 1, 40, 41};
  private TouchGestureControlOverlay gestureOverlay;

  private TextView mainText;
  private TextView statusText;

  private boolean messageWaiting;
  public String voiceMailNumber = "";

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);
    self = this;
    gestureOverlay = null;
    tts = new TTS(this, ttsInitListener, true);
    isFocused = true;
    messageWaiting = false;

    // Watch for voicemails
    TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(new PhoneStateListener() {
      @Override
      public void onMessageWaitingIndicatorChanged(boolean mwi) {
        messageWaiting = mwi;
      }
    }, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);
    voiceMailNumber = tm.getVoiceMailNumber();

  }

  @Override
  public void onResume() {
    if (isFocused) {
      super.onResume();
      return;
    }
    tts.speak(getString(R.string.press_menu_to_unlock), 0, null);
    super.onResume();
  }


  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    isFocused = hasFocus;
    if (hasFocus && (gestureOverlay != null)) {
      String message = getString(R.string.home);
      updateStatusText();
      if (widgets.airplaneModeEnabled()) {
        message = getString(R.string.airplane_mode);
      } else if (messageWaiting) {
        message = getString(R.string.you_have_new_voicemail);
      }
      tts.speak(message, 0, null);
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
    tts.addSpeech(getString(R.string.marvin_intro_snd_), pkgName, R.raw.marvin_intro);
    tts.addSpeech(getString(R.string.home), pkgName, R.raw.home);
    tts.addSpeech(getString(R.string.press_menu_to_unlock), pkgName, R.raw.press_menu_to_unlock);
    tts.addSpeech(getString(R.string.compass), pkgName, R.raw.compass);
    tts.addSpeech(getString(R.string.battery), pkgName, R.raw.battery);
    tts.addSpeech(getString(R.string.application_not_installed), pkgName, R.raw.application_not_installed);
    tts.addSpeech(getString(R.string.january), pkgName, R.raw.january);
    tts.addSpeech(getString(R.string.february), pkgName, R.raw.february);
    tts.addSpeech(getString(R.string.march), pkgName, R.raw.march);
    tts.addSpeech(getString(R.string.april), pkgName, R.raw.april);
    tts.addSpeech(getString(R.string.may), pkgName, R.raw.may);
    tts.addSpeech(getString(R.string.june), pkgName, R.raw.june);
    tts.addSpeech(getString(R.string.july), pkgName, R.raw.july);
    tts.addSpeech(getString(R.string.august), pkgName, R.raw.august);
    tts.addSpeech(getString(R.string.september), pkgName, R.raw.september);
    tts.addSpeech(getString(R.string.october), pkgName, R.raw.october);
    tts.addSpeech(getString(R.string.november), pkgName, R.raw.november);
    tts.addSpeech(getString(R.string.december), pkgName, R.raw.december);
    tts.addSpeech(getString(R.string.midnight), pkgName, R.raw.midnight);
    tts.addSpeech(getString(R.string.noon), pkgName, R.raw.noon);
    tts.addSpeech(getString(R.string.am), pkgName, R.raw.am);
    tts.addSpeech(getString(R.string.pm), pkgName, R.raw.pm);
    tts.addSpeech(getString(R.string.airplane_mode), pkgName, R.raw.airplane_mode);
    tts.addSpeech(getString(R.string.enabled), pkgName, R.raw.enabled);
    tts.addSpeech(getString(R.string.disabled), pkgName, R.raw.disabled);
    tts.addSpeech(getString(R.string.camera), pkgName, R.raw.camera);
    tts.addSpeech(getString(R.string.weather), pkgName, R.raw.weather);
    tts.addSpeech(getString(R.string.applications), pkgName, R.raw.applications);
    tts.addSpeech(getString(R.string.you_have_new_voicemail), pkgName, R.raw.you_have_new_voicemail);
    tts.addSpeech(getString(R.string.voicemail), pkgName, R.raw.voicemail);
  }

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      resetTTS();
      tts.speak(getString(R.string.marvin_intro_snd_), 0, null);

      setContentView(R.layout.main);
      widgets = new AuditoryWidgets(tts, self);
      loadItems();

      mainText = (TextView) self.findViewById(R.id.mainText);
      statusText = (TextView) self.findViewById(R.id.statusText);
      updateStatusText();
      
      FrameLayout mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
      vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      gestureOverlay = new TouchGestureControlOverlay(self, self);
      mainFrameLayout.addView(gestureOverlay);

    }
  };
  
  private void loadItems(){
    items = new HashMap<Gesture, MenuItem>();
    
    items.put(Gesture.UPLEFT, new MenuItem(getString(R.string.airplane_mode), "WIDGET", "AIRPLANE_MODE_TOGGLE"));
    items.put(Gesture.UP, new MenuItem(getString(R.string.time_and_date), "WIDGET", "TIME_DATE"));
    items.put(Gesture.UPRIGHT, new MenuItem(getString(R.string.battery), "WIDGET", "BATTERY"));
    
    items.put(Gesture.LEFT, new MenuItem(getString(R.string.applications), "LOAD", "apps.xml"));
    items.put(Gesture.RIGHT, new MenuItem(getString(R.string.voicemail), "WIDGET", "VOICEMAIL"));
    
    items.put(Gesture.DOWNLEFT, new MenuItem(getString(R.string.weather), "WIDGET", "WEATHER"));
    items.put(Gesture.DOWN, new MenuItem(getString(R.string.compass), "LAUNCH", "com.google.marvin.compass.TalkingCompass"));
    items.put(Gesture.DOWNRIGHT, new MenuItem(getString(R.string.camera), "LAUNCH", "com.android.camera.Camera"));
  }
  
  private void launchApplication(String launchData){
    try {
      String packageName = launchData.substring(0,launchData.lastIndexOf("."));
      String className = launchData.substring(launchData.lastIndexOf(".")+1);
      
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(packageName + "." + className);
      Intent intent = new Intent(myContext, appClass);
      
      // Add in functionality for complex launch data that includes flags
      // such as intent.putExtra("com.mycorp.appfoo.flagbar", true);      
      //intent.putExtra(keyName, keyValue);

      startActivity(intent);
    } catch (NameNotFoundException e) {
      tts.speak(getString(R.string.application_not_installed), 0, null);
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      tts.speak(getString(R.string.application_not_installed), 0, null);
      e.printStackTrace();
    }
  }
  
  private void updateStatusText(){
    if (widgets.airplaneModeEnabled()) {
      statusText.setText(getString(R.string.airplane_mode));
    } else {
      statusText.setText("");
    }
  }
  
  private void runWidget(String widgetName){
    if (widgetName.equals("AIRPLANE_MODE_TOGGLE")){
      widgets.toggleAirplaneMode();
      updateStatusText();
    } else if (widgetName.equals("TIME_DATE")){
      widgets.announceDate();
    } else if (widgetName.equals("BATTERY")){
      widgets.announceBattery();
    } else if (widgetName.equals("VOICEMAIL")){
      widgets.callVoiceMail();
    } else if (widgetName.equals("WEATHER")){
      widgets.announceWeather();
    }
  }
  
  

  public void onGestureChange(Gesture g) {
    MenuItem item = items.get(g);
    if (item != null){
      String label = item.label;
      mainText.setText(label);
      if (label.equals(getString(R.string.time_and_date))){
        widgets.announceTime();
      } else if (label.equals(getString(R.string.voicemail)) && messageWaiting){
        tts.speak(getString(R.string.you_have_new_voicemail), 0, null);
      } else{
        tts.speak(label, 0, null);
      }
    } else {
      tts.speak("[tock]", 0, null);
      mainText.setText("Home");
    }
    vibe.vibrate(VIBE_PATTERN, -1);
  }

  public void onGestureFinish(Gesture g) {
    MenuItem item = items.get(g);
    if (item != null){
      if (item.action.equals("LAUNCH")){
        launchApplication(item.data);
      } else if (item.action.equals("WIDGET")){
        runWidget(item.data);
      } else if (item.action.equals("LOAD")){
        
      }
    }
    mainText.setText(getString(R.string.home));
  }

  public void onGestureStart(Gesture g) {
    tts.speak("[tock]", 0, null);
    vibe.vibrate(VIBE_PATTERN, -1);
  }
  
  

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_MENU:
        String message = getString(R.string.home);
        if (widgets.airplaneModeEnabled()) {
          message = getString(R.string.airplane_mode);
        }
        tts.speak(message, 0, null);
        return true;
      case KeyEvent.KEYCODE_CALL:
        launchApplication("com.google.marvin.talkingdialer.TalkingDialer");
        return true;
      case KeyEvent.KEYCODE_BACK:
        return true;
    }
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        long duration = event.getEventTime() - event.getDownTime();
        if (duration > 3000) {
          launchApplication("com.android.launcher.Launcher");
          return true;
        } else {
          return true;
        }
    }
    return false;
  }
}
