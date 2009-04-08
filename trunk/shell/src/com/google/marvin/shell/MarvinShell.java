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

import com.google.marvin.shell.Param;
import com.google.marvin.shell.TouchGestureControlOverlay.Gesture;
import com.google.marvin.shell.TouchGestureControlOverlay.GestureListener;
import com.google.tts.ConfigurationManager;
import com.google.tts.TTS;
import com.google.tts.TTSEarcon;
import com.google.tts.TTSEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.telephony.PhoneNumberUtils;
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
  private static final int ttsCheckCode = 42;

  private PackageManager pm;
  private FrameLayout mainFrameLayout;
  private AppLauncherView appLauncherView;
  private boolean appLauncherActive;

  public TTS tts;
  private boolean ttsStartedSuccessfully;
  public boolean isFocused;
  private MarvinShell self;
  private AuditoryWidgets widgets;
  private HashMap<Gesture, MenuItem> items;
  private ArrayList<Menu> menus;

  long backKeyTimeDown = -1;
  /*
   * Set the isReturningFromTask in the onRestart method to distinguish between
   * a regular restart (returning to the Eyes-Free Shell after the launched
   * application has stopped) and starting fresh (ie, the user has decided to
   * bail and go back to the Eyes-Free Shell by pressing the Home key).
   */
  private boolean isReturningFromTask;

  /*
   * There is a race condition caused by the initialization of the TTS happening
   * at about the same time as the Activity's onRestart which leads to the
   * Marvin intro being cut off part way through by announceCurrentMenu. The
   * initial announcement is not interesting; it just says "Home". Fix is to not
   * even bother with the "Home" announcement when the Shell has just started
   * up.
   */
  private boolean justStarted;

  private Vibrator vibe;
  private static final long[] VIBE_PATTERN = {0, 10, 70, 80};
  private TouchGestureControlOverlay gestureOverlay;

  private TextView mainText;
  private TextView statusText;

  private boolean messageWaiting;
  public String voiceMailNumber = "";

  private BroadcastReceiver screenStateOnReceiver;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    appLauncherActive = false;
    pm = getPackageManager();
    ttsStartedSuccessfully = false;
    justStarted = true;
    if (checkTtsRequirements()) {
      initMarvinShell();
    }
  }

  private void initMarvinShell() {
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

    // Watch for voicemails
    TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(new PhoneStateListener() {
      @Override
      public void onMessageWaitingIndicatorChanged(boolean mwi) {
        messageWaiting = mwi;
      }
    }, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);
    voiceMailNumber = PhoneNumberUtils.extractNetworkPortion(tm.getVoiceMailNumber());

    screenStateOnReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        if (!isFocused && (tts != null)) {
          tts.speak(getString(R.string.press_menu_to_unlock), 0, null);
        }
      }
    };
    IntentFilter screenOnFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    registerReceiver(screenStateOnReceiver, screenOnFilter);
  }

  @Override
  protected void onRestart() {
    super.onRestart();
    isReturningFromTask = true;
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    isFocused = hasFocus;
    if (hasFocus) {
      if (gestureOverlay != null) {
        if (isReturningFromTask) {
          isReturningFromTask = false;
          resetTTS();
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
    shutdown();
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
    tts.addSpeech(getString(R.string.applications), pkgName, R.raw.applications);
    tts
        .addSpeech(getString(R.string.you_have_new_voicemail), pkgName,
            R.raw.you_have_new_voicemail);
    tts.addSpeech(getString(R.string.voicemail), pkgName, R.raw.voicemail);
    tts.addSpeech(getString(R.string.charging), pkgName, R.raw.charging);
    tts.addSpeech("north", pkgName, R.raw.north);
    tts.addSpeech("north east", pkgName, R.raw.north_east);
    tts.addSpeech("north west", pkgName, R.raw.north_west);
    tts.addSpeech("north north east", pkgName, R.raw.north_north_east);
    tts.addSpeech("north north west", pkgName, R.raw.north_north_west);
    tts.addSpeech("east north east", pkgName, R.raw.east_north_east);
    tts.addSpeech("west north west", pkgName, R.raw.west_north_west);
    tts.addSpeech("south", pkgName, R.raw.south);
    tts.addSpeech("south east", pkgName, R.raw.south_east);
    tts.addSpeech("south west", pkgName, R.raw.south_west);
    tts.addSpeech("south south east", pkgName, R.raw.south_south_east);
    tts.addSpeech("south south west", pkgName, R.raw.south_south_west);
    tts.addSpeech("east south east", pkgName, R.raw.east_south_east);
    tts.addSpeech("west south west", pkgName, R.raw.west_south_west);
    tts.addSpeech("east", pkgName, R.raw.east);
    tts.addSpeech("west", pkgName, R.raw.west);
    tts.addSpeech("Android Says", pkgName, R.raw.android_says);
    tts.addSpeech("<-", pkgName, R.raw.backspace);
    tts.addSpeech(getString(R.string.gps), pkgName, R.raw.gps);
    tts.addSpeech(getString(R.string.signal), pkgName, R.raw.signal);
    tts.addSpeech(getString(R.string.bluetooth), pkgName, R.raw.bluetooth);
    tts.addSpeech(getString(R.string.location), pkgName, R.raw.location);
    tts.addSpeech(getString(R.string.shortcuts), pkgName, R.raw.shortcuts);
    tts.addSpeech(getString(R.string.wifi), pkgName, R.raw.wifi);
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

      mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
      vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
      gestureOverlay = new TouchGestureControlOverlay(self, self);
      mainFrameLayout.addView(gestureOverlay);

      currentGesture = null;
      (new Thread(new ActionMonitor())).start();

      ttsStartedSuccessfully = true;

      new ProcessTask().execute();
    }
  };

  private void announceCurrentMenu() {
    if (gestureOverlay != null) {
      Menu currentMenu = menus.get(menus.size() - 1);
      String message = currentMenu.title;
      if (appLauncherActive) {
        message = getString(R.string.applications);
      }
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
      if (justStarted) {
        justStarted = false;
      } else {
        tts.speak(message, 0, null);
      }
    }
  }

  private void loadHomeMenu() {
    items = new HashMap<Gesture, MenuItem>();

    items.put(Gesture.UPLEFT, new MenuItem(getString(R.string.signal), "WIDGET", "CONNECTIVITY",
        null));
    items.put(Gesture.UP, new MenuItem(getString(R.string.time_and_date), "WIDGET", "TIME_DATE",
        null));
    items
        .put(Gesture.UPRIGHT, new MenuItem(getString(R.string.battery), "WIDGET", "BATTERY", null));

    items.put(Gesture.LEFT, new MenuItem(getString(R.string.shortcuts), "LOAD",
        "/sdcard/eyesfree/shortcuts.xml", null));

    items
        .put(Gesture.RIGHT, new MenuItem(getString(R.string.location), "WIDGET", "LOCATION", null));

    items.put(Gesture.DOWNLEFT, new MenuItem(getString(R.string.voicemail), "WIDGET", "VOICEMAIL",
        null));

    items.put(Gesture.DOWN, new MenuItem(getString(R.string.applications), "WIDGET", "APPLAUNCHER",
        null));

    items.put(Gesture.DOWNRIGHT, new MenuItem(getString(R.string.airplane_mode), "WIDGET",
        "AIRPLANE_MODE_TOGGLE", null));

    menus.add(new Menu(getString(R.string.home), ""));
    mainText.setText(menus.get(menus.size() - 1).title);
  }

  private void launchApplication(AppEntry appInfo) {
    try {
      String packageName = appInfo.getPackageName();
      String className = appInfo.getClassName();
      ArrayList<Param> params = appInfo.getParams();

      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(className);
      Intent intent = new Intent(myContext, appClass);

      if (params != null) {
        for (int i = 0; i < params.size(); i++) {
          boolean keyValue = params.get(i).value.equalsIgnoreCase("true");
          intent.putExtra(params.get(i).name, keyValue);
        }
      }
      tts.playEarcon(TTSEarcon.TICK, 0, null);
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
      // Time and date are already being spoken; lifting up should make it stop
      // speaking.
      tts.stop();
    } else if (widgetName.equals("BATTERY")) {
      widgets.announceBattery();
    } else if (widgetName.equals("VOICEMAIL")) {
      tts.playEarcon(TTSEarcon.TICK, 0, null);
      widgets.callVoiceMail();
    } else if (widgetName.equals("LOCATION")) {
      tts.playEarcon(TTSEarcon.TICK, 0, null);
      widgets.speakLocation();
    } else if (widgetName.equals("CONNECTIVITY")) {
      widgets.announceConnectivity();
    } else if (widgetName.equals("APPLAUNCHER")) {
      widgets.startAppLauncher();
    }
  }

  private class ActionMonitor implements Runnable {
    public void run() {
      if (((System.currentTimeMillis() - currentGestureTime) > 250) && (currentGesture != null)
          && (confirmedGesture == null)) {
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
    currentGestureTime = System.currentTimeMillis();
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
    if (confirmedGesture != null) {
      acceptedGesture = confirmedGesture;
    }
    MenuItem item = items.get(acceptedGesture);
    if (item != null) {
      if (item.action.equals("LAUNCH")) {
        launchApplication(item.appInfo);
      } else if (item.action.equals("WIDGET")) {
        runWidget(item.data);
      } else if (item.action.equals("LOAD")) {
        if (new File(item.data).isFile()) {
          menus.add(new Menu(item.label, item.data));
          items = MenuLoader.loadMenu(item.data);
          tts.playEarcon(TTSEarcon.TICK, 0, null);
        } else {
          final String label = item.label;
          final String data = item.data;
          // Write file and retry
          class createShortcutsFileThread implements Runnable {
            public void run() {
              try {
                String efDirStr = "/sdcard/eyesfree/";
                String filename = efDirStr + "shortcuts.xml";
                Resources res = getResources();
                InputStream fis = res.openRawResource(R.raw.default_shortcuts);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                String contents = "";
                String line = null;
                while ((line = reader.readLine()) != null) {
                  contents = contents + line;
                }
                File efDir = new File(efDirStr);
                boolean directoryExists = efDir.isDirectory();
                if (!directoryExists) {
                  efDir.mkdir();
                }
                FileWriter writer = new FileWriter(filename);
                writer.write(contents);
                writer.close();
                tts.speak("Default shortcuts dot X M L created.", 0, null);
              } catch (IOException e) {
                tts.speak("S D Card error.", 0, null);
              }
            }
          }
          new Thread(new createShortcutsFileThread()).start();
        }
      }
    }
    mainText.setText(menus.get(menus.size() - 1).title);
  }

  public void onGestureStart(Gesture g) {
    confirmedGesture = null;
    currentGesture = g;
    vibe.vibrate(VIBE_PATTERN, -1);
  }



  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (!ttsStartedSuccessfully) {
      return false;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_MENU:
        announceCurrentMenu();
        return true;
      case KeyEvent.KEYCODE_CALL:
        AppEntry talkingDialer =
            new AppEntry(null, "com.google.marvin.talkingdialer",
                "com.google.marvin.talkingdialer.TalkingDialer", null, null);
        launchApplication(talkingDialer);
        return true;
      case KeyEvent.KEYCODE_BACK:
        if (backKeyTimeDown == -1) {
          backKeyTimeDown = System.currentTimeMillis();
          class QuitCommandWatcher implements Runnable {
            public void run() {
              try {
                Thread.sleep(3000);
                if ((backKeyTimeDown > 0) && (System.currentTimeMillis() - backKeyTimeDown > 2500)) {
                  AppEntry regularHome =
                      new AppEntry(null, "com.android.launcher", "com.android.launcher.Launcher",
                          null, null);
                  launchApplication(regularHome);
                  shutdown();
                  finish();
                }
              } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
              }
            }
          }
          new Thread(new QuitCommandWatcher()).start();
        }
        return true;
    }
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (!ttsStartedSuccessfully) {
      return false;
    }
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        backKeyTimeDown = -1;
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
    return false;
  }


  /** Checks to make sure that all the requirements for the TTS are there */
  private boolean checkTtsRequirements() {
    if (!TTS.isInstalled(this)) {
      Uri marketUri = Uri.parse("market://search?q=pname:com.google.tts");
      Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
      startActivityForResult(marketIntent, ttsCheckCode);
      return false;
    }
    if (!ConfigurationManager.allFilesExist()) {
      Intent intent = null;
      try {
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Context myContext = createPackageContext("com.google.tts", flags);
        Class<?> appClass =
            myContext.getClassLoader().loadClass("com.google.tts.ConfigurationManager");
        intent = new Intent(myContext, appClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivityForResult(intent, ttsCheckCode);
      } catch (NameNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (ClassNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
      return false;
    }
    return true;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == ttsCheckCode) {
      if (TTS.isInstalled(this)) {
        initMarvinShell();
      } else {
        displayTTSMissing();
      }
    }
  }

  private void displayTTSMissing() {
    AlertDialog errorDialog = new Builder(this).create();
    errorDialog.setTitle("Unable to continue");
    errorDialog.setMessage("TTS is a required component. Please install it first.");
    errorDialog.setButton("Quit", new OnClickListener() {
      public void onClick(DialogInterface arg0, int arg1) {
        finish();
      }
    });
    errorDialog.setCancelable(false);
    errorDialog.show();
  }

  public void switchToAppLauncherView() {
    if (appLauncherView != null) {
      setContentView(appLauncherView);
      appLauncherView.requestFocus();
      appLauncherView.speakCurrentApp(true);
      appLauncherActive = true;
    }
  }

  public void switchToMainView() {
    setContentView(mainFrameLayout);
    mainFrameLayout.requestFocus();
    appLauncherActive = false;
    announceCurrentMenu();
  }

  public void launchApp(AppEntry theApp) {
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(theApp.getPackageName(), flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(theApp.getClassName());
      Intent intent = new Intent(myContext, appClass);
      startActivity(intent);
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void shutdown() {
    if (tts != null) {
      tts.shutdown();
    }
    if (widgets != null) {
      widgets.shutdown();
    }
    try {
      if (screenStateOnReceiver != null) {
        unregisterReceiver(screenStateOnReceiver);
      }
    } catch (IllegalArgumentException e) {
      // Sometimes there may be 2 shutdown requests in which case, the 2nd
      // request will fail
    }
  }


  private class ProcessTask extends UserTask<Void, Void, ArrayList<AppEntry>> {
    @SuppressWarnings("unchecked")
    @Override
    public ArrayList<AppEntry> doInBackground(Void... params) {
      // search for all launchable apps
      Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
      mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

      List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
      ArrayList<AppEntry> appList = new ArrayList<AppEntry>();
      for (ResolveInfo info : apps) {
        String title = info.loadLabel(pm).toString();
        if (title.length() == 0) {
          title = info.activityInfo.name.toString();
        }

        AppEntry entry = new AppEntry(title, info, null);
        appList.add(entry);
      }

      class appEntrySorter implements Comparator {
        public int compare(Object arg0, Object arg1) {
          String title0 = ((AppEntry) arg0).getTitle();
          String title1 = ((AppEntry) arg1).getTitle();
          return title0.compareTo(title1);
        }
      }
      Collections.sort(appList, new appEntrySorter());

      // now that app tree is built, pass along to adapter
      return appList;
    }

    @Override
    public void end(ArrayList<AppEntry> appList) {
      appLauncherView = new AppLauncherView(self, appList);
    }
  }
}
