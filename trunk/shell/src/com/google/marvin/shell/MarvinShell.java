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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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
  private ArrayList<Menu> menus;

  /*
   * Set the isReturningFromTask in the onRestart method to distinguish between
   * a regular restart (returning to the Eyes-Free Shell after the launched
   * application has stopped) and starting fresh (ie, the user has decided to
   * bail and go back to the Eyes-Free Shell by pressing the Home key).
   */
  private boolean isReturningFromTask;

  private Vibrator vibe;
  private static final long[] VIBE_PATTERN = {0, 10, 70, 80};
  private TouchGestureControlOverlay gestureOverlay;

  private TextView mainText;
  private TextView statusText;

  private boolean messageWaiting;
  public String voiceMailNumber = "";

  // Need a flag here to prevent the keyUp for the Back button from firing when
  // the Back button was pressed to get out of an application that was launched;
  // the keyUp should only be active if the initial keyDown for the Back button
  // was pressed in the shell itself.
  private boolean backButtonPressed;



  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setVolumeControlStream(AudioManager.STREAM_MUSIC);
    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        0);
    self = this;
    gestureOverlay = null;
    tts = new TTS(this, ttsInitListener, true);
    isFocused = true;
    messageWaiting = false;
    menus = new ArrayList<Menu>();
    isReturningFromTask = false;
    backButtonPressed = false;

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
  protected void onRestart() {
    super.onRestart();
    isReturningFromTask = true;
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
    if (hasFocus) {
      if (gestureOverlay != null) {
        if (isReturningFromTask) {
          isReturningFromTask = false;
        } else {
          menus = new ArrayList<Menu>();
          loadHomeMenu();
        }
      }
      announceCurrentMenu();
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
    tts.addSpeech(getString(R.string.application_not_installed), pkgName,
        R.raw.application_not_installed);
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
    tts
        .addSpeech(getString(R.string.you_have_new_voicemail), pkgName,
            R.raw.you_have_new_voicemail);
    tts.addSpeech(getString(R.string.voicemail), pkgName, R.raw.voicemail);
    tts.addSpeech(getString(R.string.charging), pkgName, R.raw.charging);
    tts.addSpeech("[cancel]", pkgName, R.raw.cancel_snd);
    tts.addSpeech("[launch]", pkgName, R.raw.launch_snd);
  }

  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int version) {
      resetTTS();
      tts.speak(getString(R.string.marvin_intro_snd_), 0, null);

      setContentView(R.layout.main);
      mainText = (TextView) self.findViewById(R.id.mainText);
      statusText = (TextView) self.findViewById(R.id.statusText);

      widgets = new AuditoryWidgets(tts, self);
      loadHomeMenu();

      updateStatusText();

      FrameLayout mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
      vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      gestureOverlay = new TouchGestureControlOverlay(self, self);
      mainFrameLayout.addView(gestureOverlay);
      
      (new Thread(new ActionMonitor())).start();
    }
  };

  private void announceCurrentMenu() {
    if (gestureOverlay != null) {
      Menu currentMenu = menus.get(menus.size() - 1);
      String message = currentMenu.title;
      updateStatusText();
      // Only announce airplane mode and voicemails
      // if the user is on the home screen.
      if (currentMenu.title.equals(getString(R.string.home))) {
        if (widgets.airplaneModeEnabled()) {
          message = getString(R.string.airplane_mode);
        } else if (messageWaiting) {
          message = getString(R.string.you_have_new_voicemail);
        }
      }
      tts.speak(message, 0, null);
    }
  }

  private void loadHomeMenu() {
    items = new HashMap<Gesture, MenuItem>();

    items.put(Gesture.UPLEFT, new MenuItem(getString(R.string.airplane_mode), "WIDGET",
        "AIRPLANE_MODE_TOGGLE"));
    items.put(Gesture.UP, new MenuItem(getString(R.string.time_and_date), "WIDGET", "TIME_DATE"));
    items.put(Gesture.UPRIGHT, new MenuItem(getString(R.string.battery), "WIDGET", "BATTERY"));

    items.put(Gesture.LEFT, new MenuItem(getString(R.string.applications), "LOAD",
        "/sdcard/eyesfree/apps.xml"));
    items.put(Gesture.RIGHT, new MenuItem(getString(R.string.voicemail), "WIDGET", "VOICEMAIL"));

    items.put(Gesture.DOWNLEFT, new MenuItem(getString(R.string.weather), "WIDGET", "WEATHER"));
    items.put(Gesture.DOWN, new MenuItem(getString(R.string.compass), "LAUNCH",
        "com.google.marvin.compass.TalkingCompass"));
    items.put(Gesture.DOWNRIGHT, new MenuItem(getString(R.string.camera), "LAUNCH",
        "com.android.camera.Camera"));

    menus.add(new Menu(getString(R.string.home), ""));
    mainText.setText(menus.get(menus.size() - 1).title);
  }

  private void launchApplication(String launchData) {
    try {
      String appInfo = "";
      String params = "";
      if (launchData.indexOf("|") != -1) {
        appInfo = launchData.substring(0, launchData.indexOf("|"));
        params = launchData.substring(launchData.indexOf("|") + 1);
      } else {
        appInfo = launchData;
        params = "";
      }

      String packageName = appInfo.substring(0, appInfo.lastIndexOf("."));
      String className = appInfo.substring(appInfo.lastIndexOf(".") + 1);

      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(packageName + "." + className);
      Intent intent = new Intent(myContext, appClass);

      while (params.length() > 0) {
        int nameValueSeparatorIndex = params.indexOf(":");
        int nextParamIndex = params.indexOf("|");
        String keyName = params.substring(0, nameValueSeparatorIndex);
        String keyValueStr = "";
        if (nextParamIndex != -1) {
          keyValueStr = params.substring(nameValueSeparatorIndex + 1, nextParamIndex);
          params = params.substring(nextParamIndex + 1);
        } else {
          keyValueStr = params.substring(nameValueSeparatorIndex + 1);
          params = "";
        }
        boolean keyValue = keyValueStr.equalsIgnoreCase("true");
        intent.putExtra(keyName, keyValue);
      }

      tts.speak("[launch]", 0, null);
      startActivity(intent);
    } catch (NameNotFoundException e) {
      tts.speak(getString(R.string.application_not_installed), 0, null);
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      tts.speak(getString(R.string.application_not_installed), 0, null);
      e.printStackTrace();
    }
  }

  private void updateStatusText() {
    if (widgets.airplaneModeEnabled()) {
      statusText.setText(getString(R.string.airplane_mode));
    } else {
      statusText.setText("");
    }
  }

  private void runWidget(String widgetName) {
    if (widgetName.equals("AIRPLANE_MODE_TOGGLE")) {
      widgets.toggleAirplaneMode();
      updateStatusText();
    } else if (widgetName.equals("TIME_DATE")) {
      widgets.announceDate();
    } else if (widgetName.equals("BATTERY")) {
      widgets.announceBattery();
    } else if (widgetName.equals("VOICEMAIL")) {
      widgets.callVoiceMail();
    } else if (widgetName.equals("WEATHER")) {
      widgets.announceWeather();
    }
  }

  private class ActionMonitor implements Runnable {
    public void run() {
      if (((System.currentTimeMillis() - currentGestureTime) > 100) && (currentGesture != null) && (confirmedGesture == null)){
        confirmedGesture = currentGesture;
        MenuItem item = items.get(confirmedGesture);
        if (item != null) {
          String label = item.label;
          if (label.equals(getString(R.string.time_and_date))) {
            widgets.announceTime();
          } else if (label.equals(getString(R.string.voicemail)) && messageWaiting) {
            tts.speak(getString(R.string.you_have_new_voicemail), 0, null);
          } else {
            tts.speak(label, 0, null);
          }
        } else {
          String titleText = menus.get(menus.size() - 1).title;
          tts.speak(titleText, 0, null);
        }
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      (new Thread(new ActionMonitor())).start();
    }  
  }
  
  private Gesture currentGesture = Gesture.CENTER;
  private long currentGestureTime = 0;
  private Gesture confirmedGesture = null;


  public void onGestureChange(Gesture g) {
    confirmedGesture = null;
    currentGesture = g;
    MenuItem item = items.get(g);
    if (item != null) {
      String label = item.label;
      mainText.setText(label);
    } else {
      String titleText = menus.get(menus.size() - 1).title;
      mainText.setText(titleText);
    }
    vibe.vibrate(VIBE_PATTERN, -1);
  }

  public void onGestureFinish(Gesture g) {
    Gesture acceptedGesture = Gesture.CENTER;
    if (confirmedGesture != null){
      acceptedGesture = confirmedGesture;
    }
    MenuItem item = items.get(acceptedGesture);
    if (item != null) {
      if (item.action.equals("LAUNCH")) {
        launchApplication(item.data);
      } else if (item.action.equals("WIDGET")) {
        tts.speak("[launch]", 0, null);
        runWidget(item.data);
      } else if (item.action.equals("LOAD")) {
        if (new File(item.data).isFile()) {
          menus.add(new Menu(item.label, item.data));
          items = MenuLoader.loadMenu(item.data);
          tts.speak("[launch]", 0, null);
        } else {
          tts.speak("Unable to load " + item.data, 0, null);
        }
      }
    }
    mainText.setText(menus.get(menus.size() - 1).title);
  }

  public void onGestureStart(Gesture g) {
    confirmedGesture = null;
    currentGesture = g;
    tts.speak(menus.get(menus.size() - 1).title, 0, null);
    vibe.vibrate(VIBE_PATTERN, -1);
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_MENU:
        announceCurrentMenu();
        return true;
      case KeyEvent.KEYCODE_CALL:
        launchApplication("com.google.marvin.talkingdialer.TalkingDialer");
        return true;
      case KeyEvent.KEYCODE_BACK:
        backButtonPressed = true;
        return true;
    }
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        if (backButtonPressed) {
          backButtonPressed = false;
          long duration = event.getEventTime() - event.getDownTime();
          if (duration > 3000) {
            launchApplication("com.android.launcher.Launcher");
            return true;
          } else {
            if (menus.size() > 1) {
              menus.remove(menus.size() - 1);
              Menu currentMenu = menus.get(menus.size() - 1);
              if (currentMenu.title.equals(getString(R.string.home))) {
                loadHomeMenu();
              } else {
                items = MenuLoader.loadMenu(currentMenu.filename);
                mainText.setText(currentMenu.title);
              }
              announceCurrentMenu();
            }
            return true;
          }
        }
    }
    return false;
  }
  
  
}
