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
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Shell An alternate home screen that is designed to be friendly for eyes-free
 * use
 * 
 * @author clchen@google.com (Charles L. Chen),
 * @author credo@google.com (Tim Credo)
 */
public class MarvinShell extends Activity {

    private static final int MAIN_VIEW = 1000;

    private static final int APPLAUNCHER_VIEW = 1002;

    private static final int MENU_EDIT_MODE = 1003;

    public static final String HOME_MENU = "Home";

    public static final int DIALOG_RENAME_MENU = 1312341;

    public static final int VOICE_RECO_CODE = 777;

    public static final int REQUEST_CODE_PICK_CONTACT = 5001;

    public static final int REQUEST_CODE_PICK_BOOKMARK = 5003;

    public static final int REQUEST_CODE_PICK_SETTINGS = 5004;

    public static final int REQUEST_CODE_PICK_DIRECT_DIAL = 5005;

    public static final int REQUEST_CODE_PICK_DIRECT_MESSAGE = 5006;
    
    public static final int REQUEST_CODE_TALKING_DIALER_DIAL = 5007;
    
    public static final int REQUEST_CODE_TALKING_DIALER_MESSAGE = 5008;

    private int activeMode;

    private PackageManager pm;

    private FrameLayout mainFrameLayout;

    // We use an ImageView rather than setBackground to avoid the automatic
    // stretching caused by setting an image to be the background of a view.
    private ImageView wallpaperView;

    private AppChooserView appChooserView;

    public TextToSpeech tts;

    private boolean ttsStartedSuccessfully;

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

    private static final long[] VIBE_PATTERN = {
            0, 80
    };

    private static final long[] CENTER_PATTERN = {
            0, 40, 40, 40, 40, 40
    };

    private GestureOverlay gestureOverlay;

    private TextView mainText;

    private BroadcastReceiver screenStateChangeReceiver;

    private BroadcastReceiver appChangeReceiver;

    private IntentFilter screenStateChangeFilter;

    private ProximitySensor proximitySensor;

    private AudioManager audioManager;

    int lastGesture = -1;

    // Path to shortcuts file.
    private String efDirStr = "/sdcard/eyesfree/";

    private String filename = efDirStr + "shortcuts.xml";

    private TelephonyManager mTelephonyManager;

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
        wallpaperView = (ImageView) self.findViewById(R.id.wallpaper);
        mainText = (TextView) self.findViewById(R.id.mainText);
        widgets = new AuditoryWidgets(tts, self);

        menus = new MenuManager();
        menus.put(HOME_MENU, new Menu(HOME_MENU));
        loadMenus();
        switchMenu(HOME_MENU);

        mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        gestureOverlay = new GestureOverlay(this, new ShellGestureListener());
        mainFrameLayout.addView(gestureOverlay);

        new ProcessTask().execute();
    }

    @Override
    public void onResume() {
        super.onResume();
        switchToMainView();
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
    
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        switchMenu(HOME_MENU);
        if (activeMode == MENU_EDIT_MODE) {
            menus.save(filename);
            tts.speak(getString(R.string.exiting_edit_mode), TextToSpeech.QUEUE_ADD,
                    null);
            activeMode = MAIN_VIEW;
        }
    }

    private void initMarvinShell() {
        setVolumeControlStream(AudioManager.STREAM_RING);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        self = this;
        tts = new TextToSpeech(this, ttsInitListener);
        isFocused = true;
        menuHistory = new ArrayList<String>();
        isReturningFromTask = false;

        // Receive notifications for app installations and removals
        appChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Obtain the package name of the changed application and create
                // an Intent
                String packageName = intent.getData().getSchemeSpecificPart();
                if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                    appChooserView.removePackage(packageName);
                    tts.speak(getString(R.string.applist_reload), TextToSpeech.QUEUE_FLUSH, null);
                } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                    // Remove all entries in the app list with a package
                    // matching this one.
                    appChooserView.removePackage(packageName);

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
                        appChooserView.addApplication(new AppEntry(title, info, null));
                    }
                    tts.speak(getString(R.string.applist_reload), TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        };
        IntentFilter appChangeFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appChangeFilter.addDataScheme("package");
        registerReceiver(appChangeReceiver, appChangeFilter);

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        screenStateChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    // If the user is not in a phone call and the phone is not
                    // ringing, we can speak something.
                    if (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                        if (!isFocused && (tts != null)) {
                            tts.speak(getString(R.string.please_unlock), TextToSpeech.QUEUE_FLUSH,
                                    null);
                        }
                    }
                }
            }
        };
        screenStateChangeFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
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
                    tts
                            .speak(getString(R.string.marvin_intro_snd_), TextToSpeech.QUEUE_FLUSH,
                                    null);
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
            if (justStarted) {
                justStarted = false;
            } else {
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
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
        setWallpaper(currentMenu.getWallpaper());
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
            if (!menus.containsKey(HOME_MENU)) {
                new File(filename).delete();
                Resources res = getResources();
                InputStream is = res.openRawResource(R.raw.default_shortcuts);
                menus = MenuManager.loadMenus(ctx, is);
            }
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
                tts.speak(lastGesture + " - " + appInfo.getTitle(), TextToSpeech.QUEUE_FLUSH, null);
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
                tts.speak(getString(R.string.application_not_installed), TextToSpeech.QUEUE_FLUSH,
                        null);
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

    private void selectWidget() {
        final String[] items = new String[AuditoryWidgets.descriptionToWidget.size()];
        AuditoryWidgets.descriptionToWidget.keySet().toArray(items);
        Arrays.sort(items);
        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        builder.setTitle("Select widget");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                String widgetData = AuditoryWidgets.descriptionToWidget.get(items[item]);
                if (widgetData != null) {
                    MenuItem menuItem = new MenuItem(items[item], "WIDGET", widgetData, null);
                    currentMenu.put(lastGesture, menuItem);
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
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
                        Menu nextMenu = menus.get(item.data);
                        if (nextMenu != null) {
                            feedback = menus.get(item.data).getName();
                        } else {
                            feedback = item.label;
                        }
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
            tts.speak(feedback, TextToSpeech.QUEUE_FLUSH, null);
        }

        public void onGestureFinish(int g) {
            setVolumeControlStream(AudioManager.STREAM_RING);
            MenuItem item = currentMenu.get(g);

            // if the gesture switches menus, that takes precedence
            if (item != null && item.action.equalsIgnoreCase("MENU")) {
                if (menus.containsKey(item.data)) {
                    switchMenu(item.data);
                    if (!tts.isSpeaking()) {
                        tts.playEarcon(getString(R.string.earcon_tick), TextToSpeech.QUEUE_FLUSH,
                                null);
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
                            widgets.runWidget(item.data);
                        } else if (item.action.equals("BOOKMARK")) {
                            Intent intentBookmark = new Intent(Intent.ACTION_VIEW, Uri
                                    .parse(item.data));
                            startActivity(intentBookmark);
                        } else if (item.action.equals("CONTACT")) {
                            Intent intentContact = new Intent(Intent.ACTION_VIEW, Uri
                                    .parse(item.data));
                            startActivity(intentContact);
                        } else if (item.action.equals("SETTINGS")) {
                            Intent intentSettings = new Intent(item.data);
                            startActivity(intentSettings);
                        } else if (item.action.equals("CALL")) {
                            Intent intentCall = new Intent(Intent.ACTION_CALL, Uri.parse("tel:"
                                    + item.data));
                            startActivity(intentCall);
                        } else if (item.action.equals("SMS")) {
                            Intent intentSms = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:"
                                    + item.data));
                            startActivity(intentSms);
                        }
                    }
                    break;
                case MENU_EDIT_MODE:
                    // edit this item
                    if (g != GestureOverlay.Gesture.CENTER) {
                        lastGesture = g;
                        final CharSequence[] items = {
                                "Application", "Bookmark", "Contact", "Direct Dial",
                                "Direct Message", "Eyes-free Widget", "Settings", "None",
                        };
                        AlertDialog.Builder builder = new AlertDialog.Builder(self);
                        builder.setTitle("Add to shell");
                        builder.setItems(items, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                if (item == 0) {
                                    switchToAppChooserView();
                                } else if (item == 1) {
                                    Intent intentBookmark = new Intent();
                                    ComponentName bookmarks = new ComponentName(
                                            "com.google.marvin.shell",
                                            "com.google.marvin.shell.BookmarkChooserActivity");
                                    intentBookmark.setComponent(bookmarks);
                                    startActivityForResult(intentBookmark,
                                            REQUEST_CODE_PICK_BOOKMARK);
                                } else if (item == 2) {
                                    Intent intentContact = new Intent(Intent.ACTION_PICK,
                                            ContactsContract.Contacts.CONTENT_URI);
                                    startActivityForResult(intentContact,
                                            REQUEST_CODE_PICK_CONTACT);
                                } else if (item == 3) {
                                    if (isTalkingDialerContactChooserAvailable()) {
                                        Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
                                        ComponentName slideDial = new ComponentName(
                                                "com.google.marvin.talkingdialer",
                                                "com.google.marvin.talkingdialer.SlideDial");
                                        talkingDialerIntent.setComponent(slideDial);
                                        startActivityForResult(talkingDialerIntent, REQUEST_CODE_TALKING_DIALER_DIAL);
                                    } else {
                                        Intent intentDirectDial = new Intent(Intent.ACTION_PICK,
                                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                                        startActivityForResult(intentDirectDial,
                                                REQUEST_CODE_PICK_DIRECT_DIAL);
                                    }
                                } else if (item == 4) {
                                    if (isTalkingDialerContactChooserAvailable()) {
                                        Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
                                        ComponentName slideDial = new ComponentName(
                                                "com.google.marvin.talkingdialer",
                                                "com.google.marvin.talkingdialer.SlideDial");
                                        talkingDialerIntent.setComponent(slideDial);
                                        startActivityForResult(talkingDialerIntent, REQUEST_CODE_TALKING_DIALER_MESSAGE);
                                    } else {
                                        Intent intentDirectMessage = new Intent(Intent.ACTION_PICK,
                                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                                        startActivityForResult(intentDirectMessage,
                                                REQUEST_CODE_PICK_DIRECT_MESSAGE);
                                    }
                                } else if (item == 5) {
                                    selectWidget();
                                } else if (item == 6) {
                                    Intent intentSettings = new Intent();
                                    ComponentName settings = new ComponentName(
                                            "com.google.marvin.shell",
                                            "com.google.marvin.shell.SettingsShortcutChooserActivity");
                                    intentSettings.setComponent(settings);
                                    startActivityForResult(intentSettings,
                                            REQUEST_CODE_PICK_SETTINGS);
                                } else if (item == 7) {
                                    currentMenu.remove(lastGesture);
                                }
                            }
                        });
                        AlertDialog alert = builder.create();
                        alert.show();
                    }
                    break;
            }
            mainText.setText(currentMenu.getName());
        }
    }
    
    /**
     * Check whether or not TalkingDialer is installed and whether the contact
     * chooser activity it provides can be used for choosing shortcuts.
     */
    public boolean isTalkingDialerContactChooserAvailable() {
        int versionCode;
        try {
            versionCode = pm.getPackageInfo("com.google.marvin.talkingdialer", 0).versionCode;
        } catch (NameNotFoundException e) {
            return false;
        }
        if (versionCode > 8) {
            /*
             * TalkingDialer has contact chooser for version code 9 and up.
             * Still we make sure the intent can be resolved to double check.
             */
            Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
            ComponentName slideDial = new ComponentName(
                    "com.google.marvin.talkingdialer",
                    "com.google.marvin.talkingdialer.SlideDial");
            talkingDialerIntent.setComponent(slideDial);
            return (pm.queryIntentActivities(talkingDialerIntent, 0).size() > 0);            
        } else {
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!ttsStartedSuccessfully) {
            return false;
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                if (activeMode == MAIN_VIEW) {
                    AppEntry talkingDialer = new AppEntry(null, "com.google.marvin.talkingdialer",
                            "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                    onAppSelected(talkingDialer);
                    return true;
                } else {
                    return false;
                }
            case KeyEvent.KEYCODE_CALL:
                if (activeMode == MAIN_VIEW) {
                    AppEntry talkingDialer = new AppEntry(null, "com.google.marvin.talkingdialer",
                            "com.google.marvin.talkingdialer.TalkingDialer", "", null, null);
                    onAppSelected(talkingDialer);
                    return true;
                } else {
                    return false;
                }
            case KeyEvent.KEYCODE_BACK:
                if (backKeyTimeDown == -1) {
                    backKeyTimeDown = System.currentTimeMillis();
                    class QuitCommandWatcher implements Runnable {
                        public void run() {
                            try {
                                Thread.sleep(3000);
                                if ((backKeyTimeDown > 0)
                                        && (System.currentTimeMillis() - backKeyTimeDown > 2500)) {
                                    Intent systemHomeIntent = HomeLauncher
                                            .getSystemHomeIntent(self);
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
                audioManager.adjustStreamVolume(getVolumeControlStream(),
                        AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                if (getVolumeControlStream() == AudioManager.STREAM_MUSIC) {
                    tts.playEarcon(getString(R.string.earcon_tick), 0, null);
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                audioManager.adjustStreamVolume(getVolumeControlStream(),
                        AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
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
                            String backMenu = menuHistory.get(menuHistory.size() - 1);
                            
                            // We need to remove the back menu from history
                            // since it will be added again on switchMenu.
                            menuHistory.remove(menuHistory.size() - 1);
                            switchMenu(backMenu);
                        }
                        activeMode = MAIN_VIEW;
                        announceCurrentMenu();
                        return true;
                    case MENU_EDIT_MODE:
                        menus.save(filename);
                        tts.speak(getString(R.string.exiting_edit_mode), TextToSpeech.QUEUE_FLUSH,
                                null);
                        activeMode = MAIN_VIEW;
                        return true;
                }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case VOICE_RECO_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    ArrayList<String> results = data.getExtras().getStringArrayList(
                            RecognizerIntent.EXTRA_RESULTS);
                    new Thread(new OneVoxSpeaker(results.get(0))).start();
                }
                break;
            case REQUEST_CODE_PICK_BOOKMARK:
                if (resultCode == Activity.RESULT_OK) {
                    String title = data.getStringExtra("TITLE");
                    String url = data.getStringExtra("URL");
                    MenuItem menuItem = new MenuItem(title, "BOOKMARK", url, null);
                    currentMenu.put(lastGesture, menuItem);
                }
                break;
            case REQUEST_CODE_PICK_CONTACT:
                if (resultCode == Activity.RESULT_OK) {
                    Cursor c = managedQuery(data.getData(), null, null, null, null);
                    if (c.moveToFirst()) {
                        String name = c.getString(c
                                .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                        long id = c.getLong(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID));
                        String lookup = c.getString(c
                                .getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
                        String uriString = ContactsContract.Contacts.getLookupUri(id, lookup)
                                .toString();
                        MenuItem menuItem = new MenuItem(name, "CONTACT", uriString, null);
                        currentMenu.put(lastGesture, menuItem);
                    }
                }
                break;
            case REQUEST_CODE_PICK_SETTINGS:
                if (resultCode == Activity.RESULT_OK) {
                    String title = data.getStringExtra("TITLE");
                    String action = data.getStringExtra("ACTION");
                    MenuItem menuItem = new MenuItem(title, "SETTINGS", action, null);
                    currentMenu.put(lastGesture, menuItem);
                }
                break;
            case REQUEST_CODE_PICK_DIRECT_DIAL:
                if (resultCode == Activity.RESULT_OK) {
                    Cursor c = managedQuery(data.getData(), null, null, null, null);
                    if (c.moveToFirst()) {
                        String name = c.getString(c
                                .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                        String phoneNumber = c
                                .getString(c.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        MenuItem menuItem = new MenuItem(getString(R.string.call) + " " + name,
                                "CALL", phoneNumber, null);
                        currentMenu.put(lastGesture, menuItem);
                    }
                }
                break;
            case REQUEST_CODE_PICK_DIRECT_MESSAGE:
                if (resultCode == Activity.RESULT_OK) {
                    Cursor c = managedQuery(data.getData(), null, null, null, null);
                    if (c.moveToFirst()) {
                        String name = c.getString(c
                                .getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                        String phoneNumber = c
                                .getString(c.getColumnIndexOrThrow(
                                ContactsContract.CommonDataKinds.Phone.NUMBER));
                        MenuItem menuItem = new MenuItem(getString(R.string.message) + " " + name,
                                "SMS", phoneNumber, null);
                        currentMenu.put(lastGesture, menuItem);
                    }
                }
                break;
            case REQUEST_CODE_TALKING_DIALER_DIAL:
                if (resultCode == Activity.RESULT_OK) {
                    if (resultCode == Activity.RESULT_OK) {
                        String number = data.getStringExtra("number");
                        String label = data.getStringExtra("label");
                        MenuItem menuItem;
                        if (label != null) {
                            menuItem = new MenuItem(getString(R.string.call) + " " + label, "CALL", number, null);
                        } else {
                            menuItem = new MenuItem(getString(R.string.call) + " " + number, "CALL", number, null);
                        }
                        currentMenu.put(lastGesture, menuItem);
                    }
                }
                break;
            case REQUEST_CODE_TALKING_DIALER_MESSAGE:
                if (resultCode == Activity.RESULT_OK) {
                    if (resultCode == Activity.RESULT_OK) {
                        String number = data.getStringExtra("number");
                        String label = data.getStringExtra("label");
                        MenuItem menuItem;
                        if (label != null) {
                            menuItem = new MenuItem(getString(R.string.message) + " " + label, "SMS", number, null);
                        } else {
                            menuItem = new MenuItem(getString(R.string.message) + " " + number, "SMS", number, null);
                        }
                        currentMenu.put(lastGesture, menuItem);
                    }
                }
                break;
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
                tts.speak(getString(R.string.entering_edit_mode), TextToSpeech.QUEUE_FLUSH, null);
                activeMode = MENU_EDIT_MODE;
                break;
            case R.string.rename_menu:
                if (currentMenu.getID().equalsIgnoreCase(HOME_MENU)) {
                    tts.speak(getString(R.string.cannot_edit_this_item), TextToSpeech.QUEUE_FLUSH,
                            null);
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

    private void setWallpaper(String filepath) {
        Bitmap bmp = BitmapFactory.decodeFile(filepath);
        if (bmp != null) {
            wallpaperView.setVisibility(View.VISIBLE);
            wallpaperView.setImageBitmap(bmp);
        } else {
            wallpaperView.setVisibility(View.GONE);
        }
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
                    tts.speak(contents, TextToSpeech.QUEUE_FLUSH, null);
                }
            } else {
                tts.speak(getString(R.string.no_short_answer) + " " + q, TextToSpeech.QUEUE_FLUSH,
                        null);
            }
        }
    }
}
