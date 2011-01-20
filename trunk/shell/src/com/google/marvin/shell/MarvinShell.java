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
import com.google.marvin.widget.GestureOverlay;
import com.google.marvin.widget.GestureOverlay.Gesture;
import com.google.marvin.widget.GestureOverlay.GestureListener;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shell An alternate home screen that is designed to be friendly for eyes-free
 * use
 *
 * @author clchen@google.com (Charles L. Chen), credo@google.com (Tim Credo)
 */
public class MarvinShell extends Activity {
    private static final int ttsCheckCode = 42;

    public static final int VOICE_RECO_CODE = 777;

    private static final int MAIN_VIEW = 1000;

    private static final int APPLAUNCHER_VIEW = 1002;

    private static final int MENU_EDIT_MODE = 1003;

    public static final String HOME_MENU = "Home";
    
    public static final int DIALOG_RENAME_MENU = 1312341;

    public static boolean editAllItems = true;

    private int activeMode;

    private PackageManager pm;

    private FrameLayout mainFrameLayout;

    private AppChooserView appChooserView;

    public TextToSpeech tts;

    private boolean ttsStartedSuccessfully;

    private boolean screenStateChanged;

    public boolean isFocused;

    private MarvinShell self;

    private AuditoryWidgets widgets;

    private Menu currentMenu;

    private MenuManager menus;

    private ArrayList<String> menuHistory;

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

    private static final long[] VIBE_PATTERN = { 0, 80 };

    private static final long[] CENTER_PATTERN = { 0, 40, 40, 40, 40, 40 };

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

    int lastGesture = -1;

    // Path to shortcuts file.
    private String efDirStr = "/sdcard/eyesfree/";

    private String filename = efDirStr + "shortcuts.xml";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        activeMode = MAIN_VIEW;
        pm = getPackageManager();
        ttsStartedSuccessfully = false;
        justStarted = true;
        proximitySensor = new ProximitySensor(this, true, new ProximityChangeListener() {
            @Override
            public void onProximityChanged(float proximity) {
                if ((proximity == 0) && (tts != null)) {
                    // Stop all speech if the user is touching the proximity
                    // sensor
                    tts.speak("", 2, null);
                }
            }
        });

        initMarvinShell();
        setContentView(R.layout.main);
        mainText = (TextView) self.findViewById(R.id.mainText);
        statusText = (TextView) self.findViewById(R.id.statusText);
        widgets = new AuditoryWidgets(tts, self);

        menus = new MenuManager();
        menus.put(HOME_MENU, new Menu(HOME_MENU));
        loadMenus();
        switchMenu(HOME_MENU);

        updateStatusText();

        mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        gestureOverlay = new GestureOverlay(this, new ShellGestureListener());
        mainFrameLayout.addView(gestureOverlay);

        new ProcessTask().execute();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (screenStateChanged == false) {
            switchToMainView();
        }
        if (proximitySensor != null) {
            proximitySensor.resume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (proximitySensor != null) {
            proximitySensor.standby();
        }
    }

    private void initMarvinShell() {
        setVolumeControlStream(AudioManager.STREAM_RING);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        self = this;
        gestureOverlay = null;
        tts = new TextToSpeech(this, ttsInitListener);
        isFocused = true;
        messageWaiting = false;
        menuHistory = new ArrayList<String>();
        isReturningFromTask = false;
        currentCallState = TelephonyManager.CALL_STATE_IDLE;
        screenStateChanged = false;

        // Receive notifications for app installations and removals
        appChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Obtain the package name of the changed application and create
                // an Intent
                String packageName = intent.getData().getSchemeSpecificPart();
                if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                    // Since the application is being removed, we can no longer
                    // access its PackageInfo object.
                    // Creating AppEntry object without one is acceptable
                    // because matching can be done by package name.
                    AppEntry targetApp = new AppEntry(null, packageName, null, null, null, null);
                    appChooserView.removeMatchingApplications(targetApp);
                    tts.speak(getString(R.string.applist_reload), 0, null);

                } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {

                    // Remove all entries in the app list with a package
                    // matching this one.
                    AppEntry targetApp = new AppEntry(null, packageName, null, null, null, null);
                    appChooserView.removeMatchingApplications(targetApp);

                    // Create intent filter to obtain only launchable activities
                    // within the given package.
                    Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
                    targetIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    targetIntent.setPackage(packageName);

                    // For every launchable activity in the installed package,
                    // add it to the app list.
                    for (ResolveInfo info : pm.queryIntentActivities(targetIntent, 0)) {
                        String title = info.loadLabel(pm).toString();
                        if (title.length() == 0) {
                            title = info.activityInfo.name.toString();
                        }
                        targetApp = new AppEntry(title, info, null);

                        appChooserView.addApplication(targetApp);
                    }
                    tts.speak(getString(R.string.applist_reload), 0, null);
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
            public void onCallStateChanged(int state, String incomingNumber) {
                currentCallState = state;
            }
        }, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR
                | PhoneStateListener.LISTEN_CALL_STATE);
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
        tts.addEarcon(getString(R.string.earcon_tock), pkgName, R.raw.tock_snd);
        tts.addEarcon(getString(R.string.earcon_tick), pkgName, R.raw.tick_snd);
    }

    private OnInitListener ttsInitListener = new OnInitListener() {
        public void onInit(int status) {
            switch (status) {
                case TextToSpeech.SUCCESS:
                    resetTTS();
                    tts.speak(getString(R.string.marvin_intro_snd_), 0, null);
                    ttsStartedSuccessfully = true;
                    break;
            }
        }
    };

    private void announceCurrentMenu() {
        if (gestureOverlay != null) {
            String message = currentMenu.getName();
            if (activeMode == MENU_EDIT_MODE) {
                message = getString(R.string.editing) + " " + message;
            }
            if (activeMode == APPLAUNCHER_VIEW) {
                message = getString(R.string.applications);
            }
            updateStatusText();
            // Only announce airplane mode and voicemails
            // if the user is on the home screen.
            if (message.equals(HOME_MENU)) {
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

    private void switchMenu(String id) {
        if (!menus.containsKey(id)) {
            id = HOME_MENU;
        }
        currentMenu = menus.get(id);
        if (id.equalsIgnoreCase(HOME_MENU)) {
            menuHistory = new ArrayList<String>();
        }
        menuHistory.add(id);
        mainText.setText(currentMenu.getName());
    }

    private void loadMenus() {
        final Context ctx = this;
        File efDir = new File(efDirStr);
        boolean directoryExists = efDir.isDirectory();
        if (!directoryExists) {
            efDir.mkdir();
        }

        if (new File(filename).isFile()) {
            menus = MenuManager.loadMenus(ctx, filename);
        } else {
            Resources res = getResources();
            InputStream is = res.openRawResource(R.raw.default_shortcuts);
            menus = MenuManager.loadMenus(ctx, is);
        }
    }

    private Intent makeClassLaunchIntent(String packageName, String className) {
        return new Intent("android.intent.action.MAIN").addCategory(
                "android.intent.category.LAUNCHER").setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setClassName(packageName, className);
    }

    public void onAppSelected(AppEntry appInfo) {
        if (activeMode == MENU_EDIT_MODE) {
            if (lastGesture > 0) {
                tts.speak(lastGesture + " - " + appInfo.getTitle(), 0, null);
                if (appInfo.getTitle().equalsIgnoreCase("(" + getString(R.string.none) + ")")) {
                    currentMenu.remove(lastGesture);
                } else {
                    MenuItem menuItem = new MenuItem(appInfo.getTitle(), "LAUNCH", "", appInfo);
                    currentMenu.put(lastGesture, menuItem);
                }
            }
            switchToMainView();
        } else {
            Intent intent = makeClassLaunchIntent(appInfo.getPackageName(), appInfo.getClassName());
            ArrayList<Param> params = appInfo.getParams();
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    boolean keyValue = params.get(i).value.equalsIgnoreCase("true");
                    intent.putExtra(params.get(i).name, keyValue);
                }
            }
            tts.playEarcon(getString(R.string.earcon_tick), 0, null);
            boolean launchSuccessful = true;
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                tts.speak(getString(R.string.application_not_installed), 0, null);
                launchSuccessful = false;
            }
            if (screenStateChangeReceiver != null && launchSuccessful == true) {
                try {
                    unregisterReceiver(screenStateChangeReceiver);
                } catch (IllegalArgumentException e) {
                    // Sometimes there may be 2 shutdown requests in which
                    // case, the
                    // 2nd request will fail
                }
            }
        }
    }

    public void runAseScript(String scriptName) {
        Intent intent = makeClassLaunchIntent("com.google.ase", "com.google.ase.terminal.Terminal");
        intent.putExtra("com.google.ase.extra.SCRIPT_NAME", scriptName);
        tts.playEarcon(getString(R.string.earcon_tick), 0, null);
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
            tts.playEarcon(getString(R.string.earcon_tick), 0, null);
            widgets.callVoiceMail();
        } else if (widgetName.equals("LOCATION")) {
            tts.playEarcon(getString(R.string.earcon_tick), 0, null);
            widgets.speakLocation();
        } else if (widgetName.equals("CONNECTIVITY")) {
            widgets.announceConnectivity();
        } else if (widgetName.equals("APPLAUNCHER")) {
            widgets.startAppChooser();
        } else if (widgetName.equals("VOICE_SEARCH")) {
            widgets.launchVoiceSearch();
        }
    }

    private class ShellGestureListener implements GestureListener {

        public void onGestureStart(int g) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            if (g == GestureOverlay.Gesture.CENTER) {
                // if the gesture starts in the middle, don't start speaking
                vibe.vibrate(CENTER_PATTERN, -1);
            } else {
                onGestureChange(g);
            }
        }

        public void onGestureChange(int g) {
            String feedback;
            if (g == GestureOverlay.Gesture.CENTER) {
                vibe.vibrate(CENTER_PATTERN, -1);
                feedback = currentMenu.getName();
            } else {
                vibe.vibrate(VIBE_PATTERN, -1);
                MenuItem item = currentMenu.get(g);
                if (item != null) {
                    // if the item is a menu, we want to look up the name
                    if (item.action.equalsIgnoreCase("MENU")) {
                        feedback = menus.get(item.data).getName();
                    } else {
                        feedback = item.label;
                    }
                } else if (activeMode == MENU_EDIT_MODE) {
                    feedback = getString(R.string.none);
                } else {
                    feedback = currentMenu.getName();
                }
            }
            mainText.setText(feedback);
            if (feedback.equals(getString(R.string.voicemail)) && messageWaiting
                    && activeMode == MAIN_VIEW) {
                tts.speak(getString(R.string.you_have_new_voicemail), 0, null);
            } else {
                tts.speak(feedback, 0, null);
            }
        }

        public void onGestureFinish(int g) {
            setVolumeControlStream(AudioManager.STREAM_RING);
            MenuItem item = currentMenu.get(g);

            // if the gesture switches menus, that takes precedence
            if (item != null && item.action.equalsIgnoreCase("MENU")) {
                if (menus.containsKey(item.data)) {
                    switchMenu(item.data);
                    if (!tts.isSpeaking()) {
                        tts.playEarcon(getString(R.string.earcon_tick), 0, null);
                    }
                }
                return;
            }

            // otherwise do the appropriate thing depending on mode
            switch (activeMode) {
                case MAIN_VIEW:
                    // activate this item
                    if (item != null) {
                        if (item.action.equals("LAUNCH")) {
                            onAppSelected(item.appInfo);
                        } else if (item.action.equals("WIDGET")) {
                            runWidget(item.data);
                        } else if (item.action.equals("ASE")) {
                            MenuItem itam = item;
                            AppEntry info = item.appInfo;
                            runAseScript(item.appInfo.getScriptName());
                        }
                    }
                    break;
                case MENU_EDIT_MODE:
                    // edit this item
                    if (g != GestureOverlay.Gesture.CENTER) {
                        if (item == null || item.action.equalsIgnoreCase("LAUNCH")
                                || editAllItems) {
                            lastGesture = g;
                            switchToAppChooserView();
                        } else {
                            tts.speak(getString(R.string.cannot_edit_this_item), 0, null);
                        }
                    }
                    break;
            }
            mainText.setText(currentMenu.getName());
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!ttsStartedSuccessfully) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                AppEntry talkingDialer1 = new AppEntry(null, "com.google.marvin.talkingdialer",
                        "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                onAppSelected(talkingDialer1);
                return true;
            case KeyEvent.KEYCODE_CALL:
                AppEntry talkingDialer = new AppEntry(null, "com.google.marvin.talkingdialer",
                        "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                onAppSelected(talkingDialer);
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
                                    Intent systemHomeIntent = HomeLauncher.getSystemHomeIntent(
                                            self);
                                    startActivity(systemHomeIntent);
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
            case KeyEvent.KEYCODE_VOLUME_UP:
                audioManager.adjustStreamVolume(getVolumeControlStream(), AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI);
                if (getVolumeControlStream() == AudioManager.STREAM_MUSIC) {
                    tts.playEarcon(getString(R.string.earcon_tick), 0, null);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                audioManager.adjustStreamVolume(getVolumeControlStream(), AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI);
                if (getVolumeControlStream() == AudioManager.STREAM_MUSIC) {
                    tts.playEarcon(getString(R.string.earcon_tick), 1, null);
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
                switch (activeMode) {
                    case MAIN_VIEW:
                        if (menuHistory.size() > 1) {
                            menuHistory.remove(menuHistory.size() - 1);
                            switchMenu(menuHistory.get(menuHistory.size() - 1));
                        }
                        activeMode = MAIN_VIEW;
                        announceCurrentMenu();
                        return true;
                    case MENU_EDIT_MODE:
                        menus.save(filename);
                        tts.speak(getString(R.string.exiting_edit_mode), 0, null);
                        activeMode = MAIN_VIEW;
                        return true;
                }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ttsCheckCode) {
            initMarvinShell();
        } else if (requestCode == VOICE_RECO_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                ArrayList<String> results = data.getExtras().getStringArrayList(
                        RecognizerIntent.EXTRA_RESULTS);
                new Thread(new OneVoxSpeaker(results.get(0))).start();
            }
        }
    }

    public void switchToAppChooserView() {
        if (appChooserView != null) {
            setContentView(appChooserView);
            appChooserView.requestFocus();
            appChooserView.resetListState();
            appChooserView.speakCurrentApp(false);
            switch (activeMode) {
                case MENU_EDIT_MODE:
                    break;
                case APPLAUNCHER_VIEW:
                    break;
                case MAIN_VIEW:
                    activeMode = APPLAUNCHER_VIEW;
                    break;
            }
        }
    }

    public void switchToMainView() {
        setContentView(mainFrameLayout);
        mainFrameLayout.requestFocus();
        switch (activeMode) {
            case MENU_EDIT_MODE:
                break;
            case APPLAUNCHER_VIEW:
                activeMode = MAIN_VIEW;
                announceCurrentMenu();
                break;
            case MAIN_VIEW:
                break;
        }
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
            if (appChangeReceiver != null) {
                unregisterReceiver(appChangeReceiver);
            }
        } catch (IllegalArgumentException e) {
            // Sometimes there may be 2 shutdown requests in which case, the 2nd
            // request will fail
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(android.view.Menu menu) {
        menu.clear();
        int NONE = android.view.Menu.NONE;
        switch (activeMode) {
            case APPLAUNCHER_VIEW:
                String uninstallText = getString(R.string.uninstall) + " "
                        + appChooserView.getCurrentAppTitle();
                String detailsFor = getString(R.string.details_for) + " "
                        + appChooserView.getCurrentAppTitle();
                menu.add(NONE, R.string.details_for, 0, detailsFor).setIcon(
                        android.R.drawable.ic_menu_info_details);
                menu.add(NONE, R.string.uninstall, 1, uninstallText).setIcon(
                        android.R.drawable.ic_menu_delete);
                return true;
            case MAIN_VIEW:
                String editMenus = getString(R.string.edit_menus);
                menu.add(NONE, R.string.edit_menus, 0, editMenus).setIcon(
                        android.R.drawable.ic_menu_edit);
                return true;
            case MENU_EDIT_MODE:
                String restoreDefault = getString(R.string.restore_default_menus);
                menu.add(NONE, R.string.restore_default_menus, 2, restoreDefault).setIcon(
                        android.R.drawable.ic_menu_revert);
                String insertMenuLeft = getString(R.string.insert_menu_left);
                menu.add(NONE, R.string.insert_menu_left, 0, insertMenuLeft).setIcon(
                        R.drawable.ic_menu_left);
                String insertMenuRight = getString(R.string.insert_menu_right);
                menu.add(NONE, R.string.insert_menu_right, 1, insertMenuRight).setIcon(
                        R.drawable.ic_menu_right);
                String renameMenu = getString(R.string.rename_menu);
                menu.add(NONE, R.string.rename_menu, 3, renameMenu).setIcon(
                        android.R.drawable.ic_menu_edit);
                return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {
        switch (item.getItemId()) {
            case R.string.details_for:
                appChooserView.showCurrentAppInfo();
                break;
            case R.string.uninstall:
                appChooserView.uninstallCurrentApp();
                break;
            case R.string.restore_default_menus:
                if (new File(filename).isFile()) {
                    new File(filename).delete();
                }
                loadMenus();
                switchMenu(HOME_MENU);
                break;
            case R.string.edit_menus:
                tts.speak(getString(R.string.entering_edit_mode), 0, null);
                activeMode = MENU_EDIT_MODE;
                break;
            case R.string.rename_menu:
                if (currentMenu.getID().equalsIgnoreCase(HOME_MENU)) {
                    tts.speak(getString(R.string.cannot_edit_this_item), 0, null);
                } else {
                    AlertDialog.Builder alert = new AlertDialog.Builder(this);
                    final EditText input = new EditText(this);
                    alert.setTitle("Enter new menu name");
                    alert.setView(input);
                    alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           currentMenu.setName(input.getText().toString().trim());
                           switchMenu(currentMenu.getID());
                       }
                   });
                   alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           dialog.cancel();
                       }
                   });
                   alert.show();
                }
                break;
            case R.string.insert_menu_left:
                menus.insertMenu(currentMenu, Gesture.EDGELEFT, getString(R.string.new_menu));
                break;
            case R.string.insert_menu_right:
                menus.insertMenu(currentMenu, Gesture.EDGERIGHT, getString(R.string.new_menu));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ProcessTask extends AsyncTask<Void, Void, ArrayList<AppEntry>> {
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
            class AppEntrySorter implements Comparator<AppEntry> {
                public int compare(AppEntry arg0, AppEntry arg1) {
                    String title0 = arg0.getTitle().toLowerCase();
                    String title1 = arg1.getTitle().toLowerCase();
                    return title0.compareTo(title1);
                }
            }
            Collections.sort(appList, new AppEntrySorter());

            // now that app tree is built, pass along to adapter
            return appList;
        }

        @Override
        public void onPostExecute(ArrayList<AppEntry> appList) {
            appChooserView = new AppChooserView(self, appList);
            /*
             *launchableApps = appList; launchableApps.add(0, new
             * AppEntry("(none)","","","", null, null)); loadUi();
             * shortcutChooser = new ShortcutChooserView(self,appList);
             */
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
            String contents = OneBoxScraper.processGoogleResults(q, getString(R.string.search_url));
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
                tts.speak(getString(R.string.no_short_answer) + " " + q, 0, null);
            }
        }
    }
}
