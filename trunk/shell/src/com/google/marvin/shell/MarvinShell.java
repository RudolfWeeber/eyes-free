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

import com.google.marvin.shell.ProximitySensor.ProximityChangeListener;
import com.google.marvin.utils.UserTask;
import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;
import com.google.tts.TTSEarcon;
import com.google.tts.TextToSpeechBeta;
import com.google.tts.TextToSpeechBeta.OnInitListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

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

/**
 * Shell An alternate home screen that is designed to be friendly for eyes-free
 * use
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MarvinShell extends Activity implements GestureListener, ProximityChangeListener {
    private static final int ttsCheckCode = 42;

    public static final int VOICE_RECO_CODE = 777;
    
    private static final int MAIN_VIEW = 1000;
    
    private static final int SHORTCUTS_VIEW = 1001;
    
    private static final int APPLAUNCHER_VIEW = 1002;
    
    private int activeView;

    private PackageManager pm;

    private FrameLayout mainFrameLayout;

    private AppLauncherView appLauncherView;

    public TextToSpeechBeta tts;

    private boolean ttsStartedSuccessfully;
    
    private boolean screenStateChanged;

    public boolean isFocused;

    private MarvinShell self;

    private AuditoryWidgets widgets;

    private HashMap<Integer, MenuItem> items;

    private ArrayList<Menu> menus;

    long backKeyTimeDown = -1;

    /*
     * Set the isReturningFromTask in the onRestart method to distinguish
     * between a regular restart (returning to the Eyes-Free Shell after the
     * launched application has stopped) and starting fresh (ie, the user has
     * decided to bail and go back to the Eyes-Free Shell by pressing the Home
     * key).
     */
    private boolean isReturningFromTask;

    /*
     * There is a race condition caused by the initialization of the TTS
     * happening at about the same time as the Activity's onRestart which leads
     * to the Marvin intro being cut off part way through by
     * announceCurrentMenu. The initial announcement is not interesting; it just
     * says "Home". Fix is to not even bother with the "Home" announcement when
     * the Shell has just started up.
     */
    private boolean justStarted;

    private Vibrator vibe;

    private static final long[] VIBE_PATTERN = {
            0, 10, 70, 80
    };

    private GestureOverlay gestureOverlay;

    private TextView mainText;

    private TextView statusText;

    private boolean messageWaiting;

    private int currentCallState;
    
    public String voiceMailNumber = "";

    private BroadcastReceiver screenStateChangeReceiver;
    
    private BroadcastReceiver appChangeReceiver;
    
    private IntentFilter screenStateChangeFilter;

    private ProximitySensor proximitySensor;

    private AudioManager audioManager;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        activeView = MAIN_VIEW;
        pm = getPackageManager();
        ttsStartedSuccessfully = false;
        justStarted = true;
        if (checkTtsRequirements()) {
            proximitySensor = new ProximitySensor(this, this);
            initMarvinShell();
            setContentView(R.layout.main);
            mainText = (TextView) self.findViewById(R.id.mainText);
            statusText = (TextView) self.findViewById(R.id.statusText);
            widgets = new AuditoryWidgets(tts, self);

            loadHomeMenu();

            updateStatusText();

            mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
            vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            gestureOverlay = new GestureOverlay(self, self);
            mainFrameLayout.addView(gestureOverlay);

            new ProcessTask().execute();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (screenStateChanged == false) {
            switchToMainView();
        }
    }

    private void initMarvinShell() {
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        self = this;
        gestureOverlay = null;
        tts = new TextToSpeechBeta(this, ttsInitListener);
        isFocused = true;
        messageWaiting = false;
        menus = new ArrayList<Menu>();
        isReturningFromTask = false;
        currentCallState = TelephonyManager.CALL_STATE_IDLE;
        screenStateChanged = false;

        // Receive notifications for app installations and removals
        appChangeReceiver = new BroadcastReceiver() {
          @Override
          public void onReceive(Context context, Intent intent) {
            // Obtain the package name of the changed application and create an Intent
            String packageName = intent.getData().getSchemeSpecificPart();
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
              // If the application is an update, we don't want to act on it.
              if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return;
              } else {
                // Since the application is being removed, we can no longer access its PackageInfo object.
                // Creating AppEntry object without one is acceptable because matching can be done by package name.
                AppEntry targetApp = new AppEntry("", null, null);
                targetApp.setPackageName(packageName);
                appLauncherView.removeApplication(targetApp);
              }
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
              // Get the application title and create the AppEntry object.
              Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
              targetIntent.setPackage(packageName);
              ResolveInfo info = pm.resolveActivity(targetIntent, 0);
              // Some packages won't have a main activity
              // and are not directly launchable, ignore them.
              if (info == null) {
                  return;
              }
              String title = info.loadLabel(pm).toString();
              if (title.length() == 0) {
                title = info.activityInfo.name.toString();
              }
              AppEntry targetApp = new AppEntry(title, info, null);
              
              // Add the application to the list only if it doesn't exist.
              if (!appLauncherView.applicationExists(targetApp)) {
                appLauncherView.addApplication(targetApp);
              }
            }           
          }
        };
        IntentFilter appChangeFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appChangeFilter.addDataScheme("package");
        registerReceiver(appChangeReceiver, appChangeFilter);
               
        // Watch for voicemails
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new PhoneStateListener() {
            @Override
            public void onMessageWaitingIndicatorChanged(boolean mwi) {
                messageWaiting = mwi;
            }
            
            @Override
            public void onCallStateChanged(int state, String incomingNumber){
                currentCallState = state;
            }
        }, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR | PhoneStateListener.LISTEN_CALL_STATE);
        voiceMailNumber = PhoneNumberUtils.extractNetworkPortion(tm.getVoiceMailNumber());

        // Receive notifications about the screen power changes
        screenStateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    Log.e("currentCallState", currentCallState + "");
                    // If the phone is ringing or the user is talking,
                    // don't try do anything else.
                    if (currentCallState != TelephonyManager.CALL_STATE_IDLE) {
                        return;
                    }
                    if (!isFocused && (tts != null)) {
                        tts.speak(getString(R.string.please_unlock), 0, null);
                    }
                } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    screenStateChanged = true;
                }
            }
        };
        screenStateChangeFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        screenStateChangeFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateChangeReceiver, screenStateChangeFilter);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        isReturningFromTask = true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        boolean announceLocation = true;
        isFocused = hasFocus;
        if (hasFocus) {
            if (widgets != null) {
                int callState = widgets.getCallState();
                if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    audioManager.setSpeakerphoneOn(true);
                }
            }
            if (gestureOverlay != null) {
                if (isReturningFromTask) {
                    isReturningFromTask = false;
                    announceLocation = false;
                    resetTTS();
                }
                if(activeView == MAIN_VIEW) {
                    menus = new ArrayList<Menu>();
                    loadHomeMenu();
                }
            }          
            if (screenStateChangeReceiver != null && screenStateChangeFilter != null) {
                registerReceiver(screenStateChangeReceiver, screenStateChangeFilter);
            }
            if (announceLocation) {
                announceCurrentMenu();   
            }
            
            // Now that the view has regained focus, reset the flag indicating
            // screen power down.
            screenStateChanged = false;
        }
        super.onWindowFocusChanged(hasFocus);
    }

    @Override
    protected void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void resetTTS() {
        String pkgName = MarvinShell.class.getPackage().getName();
        tts.addSpeech(getString(R.string.marvin_intro_snd_), pkgName, R.raw.marvin_intro);
        tts.addEarcon(TTSEarcon.TOCK.toString(), pkgName, R.raw.tock_snd);
    }

    private OnInitListener ttsInitListener = new OnInitListener() {
        public void onInit(int status, int version) {
            resetTTS();
            tts.speak(getString(R.string.marvin_intro_snd_), 0, null);
            ttsStartedSuccessfully = true;
        }
    };

    private void announceCurrentMenu() {
        if (gestureOverlay != null) {
            Menu currentMenu = menus.get(menus.size() - 1);
            String message = currentMenu.title;
            if (activeView == APPLAUNCHER_VIEW) {
                message = getString(R.string.applications);
            }
            updateStatusText();
            // Only announce airplane mode and voicemails
            // if the user is on the home screen.
            if (currentMenu.title.equals(getString(R.string.home))) {
                if (messageWaiting) {
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
        items = new HashMap<Integer, MenuItem>();

        items.put(Gesture.UPLEFT, new MenuItem(getString(R.string.signal), "WIDGET",
                "CONNECTIVITY", null));
        items.put(Gesture.UP, new MenuItem(getString(R.string.time), "WIDGET",
                "TIME_DATE", null));
        items.put(Gesture.UPRIGHT, new MenuItem(getString(R.string.battery), "WIDGET", "BATTERY",
                null));

        items.put(Gesture.LEFT, new MenuItem(getString(R.string.shortcuts), "LOAD",
                "/sdcard/eyesfree/shortcuts.xml", null));

        items.put(Gesture.RIGHT, new MenuItem(getString(R.string.location), "WIDGET", "LOCATION",
                null));

        items.put(Gesture.DOWNLEFT, new MenuItem(getString(R.string.voicemail), "WIDGET",
                "VOICEMAIL", null));

        items.put(Gesture.DOWN, new MenuItem(getString(R.string.applications), "WIDGET",
                "APPLAUNCHER", null));

        items.put(Gesture.DOWNRIGHT, new MenuItem(getString(R.string.search), "WIDGET", "VOICE_SEARCH", null));

        menus.add(new Menu(getString(R.string.home), ""));
        mainText.setText(menus.get(menus.size() - 1).title);
    }

    private Intent makeClassLaunchIntent(String packageName, String className) {
        return new Intent("android.intent.action.MAIN").addCategory(
                "android.intent.category.LAUNCHER").setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setClassName(packageName, className);
    }

    public void launchApplication(AppEntry appInfo) {
        Intent intent = makeClassLaunchIntent(appInfo.getPackageName(), appInfo.getClassName());
        ArrayList<Param> params = appInfo.getParams();
        if (params != null) {
            for (int i = 0; i < params.size(); i++) {
                boolean keyValue = params.get(i).value.equalsIgnoreCase("true");
                intent.putExtra(params.get(i).name, keyValue);
            }
        }
        tts.playEarcon(TTSEarcon.TICK.toString(), 0, null);
        boolean launchSuccessful = true;
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            tts.speak(getString(R.string.application_not_installed), 0, null);
            launchSuccessful = false;
        }
        if (screenStateChangeReceiver != null && launchSuccessful == true) {
            unregisterReceiver(screenStateChangeReceiver);
        }
    }

    public void runAseScript(String scriptName) {
        Intent intent = makeClassLaunchIntent("com.google.ase", "com.google.ase.terminal.Terminal");
        intent.putExtra("com.google.ase.extra.SCRIPT_NAME", scriptName);
        tts.playEarcon(TTSEarcon.TICK.toString(), 0, null);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            tts.speak(getString(R.string.application_not_installed), 0, null);
        }
    }

    private void updateStatusText() {
        statusText.setText("");
    }

    private void runWidget(String widgetName) {
        if (widgetName.equals("TIME_DATE")) {
            widgets.announceTime();
        } else if (widgetName.equals("BATTERY")) {
            widgets.announceBattery();
        } else if (widgetName.equals("VOICEMAIL")) {
            tts.playEarcon(TTSEarcon.TICK.toString(), 0, null);
            widgets.callVoiceMail();
        } else if (widgetName.equals("LOCATION")) {
            tts.playEarcon(TTSEarcon.TICK.toString(), 0, null);
            widgets.speakLocation();
        } else if (widgetName.equals("CONNECTIVITY")) {
            widgets.announceConnectivity();
        } else if (widgetName.equals("APPLAUNCHER")) {
            widgets.startAppLauncher();
        } else if (widgetName.equals("VOICE_SEARCH")) {
            widgets.launchVoiceSearch();
        }
    }

    public void onGestureChange(int g) {
        MenuItem item = items.get(g);
        if (item != null) {
            String label = item.label;
            mainText.setText(label);
            if (label.equals(getString(R.string.voicemail)) && messageWaiting) {
                tts.speak(getString(R.string.you_have_new_voicemail), 0, null);
            } else {
                tts.speak(label, 0, null);
            }
        } else {
            String titleText = menus.get(menus.size() - 1).title;
            mainText.setText(titleText);
            tts.speak(titleText, 0, null);
        }
        vibe.vibrate(VIBE_PATTERN, -1);
    }


    public void onGestureFinish(int g) {
        MenuItem item = items.get(g);
        if (item != null) {
            if (item.action.equals("LAUNCH")) {
                launchApplication(item.appInfo);
            } else if (item.action.equals("WIDGET")) {
                runWidget(item.data);
            } else if (item.action.equals("ASE")) {
                MenuItem itam = item;
                AppEntry info = item.appInfo;
                runAseScript(item.appInfo.getScriptName());
            } else if (item.action.equals("LOAD")) {
                // Populate menus for shortcuts and load the shortcuts view.
                if (new File(item.data).isFile()) {
                    menus.add(new Menu(item.label, item.data));
                    items = MenuLoader.loadMenu(item.data);
                    tts.playEarcon(TTSEarcon.TICK.toString(), 0, null);
                } else {
                    // Write file and retry
                    /**
                     * Class for asynchronously writing out the shortcuts.xml
                     * file.
                     */
                    class CreateShortcutsFileThread implements Runnable {
                        public void run() {
                            try {
                                String efDirStr = "/sdcard/eyesfree/";
                                String filename = efDirStr + "shortcuts.xml";
                                Resources res = getResources();
                                InputStream fis = res.openRawResource(R.raw.default_shortcuts);
                                BufferedReader reader = new BufferedReader(new InputStreamReader(
                                        fis));
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
                    new Thread(new CreateShortcutsFileThread()).start();
                }
                activeView = SHORTCUTS_VIEW;
            }
        }
        mainText.setText(menus.get(menus.size() - 1).title);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    public void onGestureStart(int g) {
        vibe.vibrate(VIBE_PATTERN, -1);
        // The ringer volume will be adjusted during a gesture.
        setVolumeControlStream(AudioManager.STREAM_RING);
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
            case KeyEvent.KEYCODE_SEARCH:
                AppEntry talkingDialer1 = new AppEntry(null, "com.google.marvin.talkingdialer",
                        "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                launchApplication(talkingDialer1);
                return true;
            case KeyEvent.KEYCODE_CALL:
                AppEntry talkingDialer = new AppEntry(null, "com.google.marvin.talkingdialer",
                        "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                launchApplication(talkingDialer);
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (backKeyTimeDown == -1) {
                    backKeyTimeDown = System.currentTimeMillis();
                    class QuitCommandWatcher implements Runnable {
                        public void run() {
                            try {
                                Thread.sleep(3000);
                                if ((backKeyTimeDown > 0)
                                        && (System.currentTimeMillis() - backKeyTimeDown > 2500)) {
                                    AppEntry regularHome = new AppEntry(null,
                                            "com.android.launcher",
                                            "com.android.launcher.Launcher", "", null, null);
                                    launchApplication(regularHome);
                                    shutdown();
                                    finish();
                                }
                            } catch (InterruptedException e) {
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
                }
                activeView = MAIN_VIEW;
                loadHomeMenu();
                announceCurrentMenu();
                return true;
        }
        return false;
    }

    /** Checks to make sure that all the requirements for the TTS are there */
    private boolean checkTtsRequirements() {
        // Disable TTS check for now
        /*
         * if (!TTS.isInstalled(this)) { Uri marketUri =
         * Uri.parse("market://search?q=pname:com.google.tts"); Intent
         * marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
         * startActivityForResult(marketIntent, ttsCheckCode); return false; }
         */
        /*
         * if (!ConfigurationManager.allFilesExist()) { Intent intent =
         * makeClassLaunchIntent("com.google.tts",
         * "com.google.tts.ConfigurationManager");
         * startActivityForResult(intent, ttsCheckCode); return false; }
         */
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ttsCheckCode) {
            // if (TTS.isInstalled(this)) {
            initMarvinShell();
            // } else {
            // displayTTSMissing();
            // }
        }
        if (requestCode == VOICE_RECO_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<String> results = data.getExtras().getStringArrayList(
                        RecognizerIntent.EXTRA_RESULTS);
                new Thread(new OneVoxSpeaker(results.get(0))).start();
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
            appLauncherView.resetListState();
            appLauncherView.speakCurrentApp(false);
            activeView = APPLAUNCHER_VIEW;
        }
    }

    public void switchToMainView() {
        setContentView(mainFrameLayout);
        mainFrameLayout.requestFocus();
        activeView = MAIN_VIEW;
        announceCurrentMenu();
    }
    
    private void shutdown() {
        if (tts != null) {
            tts.shutdown();
        }
        if (widgets != null) {
            widgets.shutdown();
        }
        if (proximitySensor != null) {
            proximitySensor.shutdown();
        }
        try {
            if (screenStateChangeReceiver != null) {
                unregisterReceiver(screenStateChangeReceiver);
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

            class AppEntrySorter implements Comparator {
                public int compare(Object arg0, Object arg1) {
                    String title0 = ((AppEntry) arg0).getTitle().toLowerCase();
                    String title1 = ((AppEntry) arg1).getTitle().toLowerCase();
                    return title0.compareTo(title1);
                }
            }
            Collections.sort(appList, new AppEntrySorter());

            // now that app tree is built, pass along to adapter
            return appList;
        }

        @Override
        public void onPostExecute(ArrayList<AppEntry> appList) {
            appLauncherView = new AppLauncherView(self, appList);
        }
    }

    /**
     * Class for asynchronously doing a search, scraping the one box, and
     * speaking it.
     */
    class OneVoxSpeaker implements Runnable {
        String q;

        public OneVoxSpeaker(String query) {
            q = query;
        }

        public void run() {
            String contents = OneBoxScraper.processGoogleResults(q);
            if (contents.length() > 0) {
                if (contents.indexOf("PAW_YOUTUBE:") == 0) {
                    Intent ytIntent = new Intent("android.intent.action.VIEW");
                    ytIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            + Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    ytIntent.setClassName("com.google.android.youtube",
                            "com.google.android.youtube.PlayerActivity");
                    ytIntent.setData(Uri.parse(contents.substring(12)));
                    self.startActivity(ytIntent);
                } else {
                    tts.speak(contents, 0, null);
                }
            } else {
                tts.speak("Sorry, no short answer for " + q, 0, null);
            }

        }
    }

    public void onProximityChanged(float proximity) {
        if (proximity == 0) {
            if (tts != null) {
                tts.speak("", 2, null);
            }
        }
    }
}
