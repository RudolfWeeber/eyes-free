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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.marvin.shell.ProximitySensor.ProximityChangeListener;
import com.googlecode.eyesfree.utils.FeedbackController;
import com.googlecode.eyesfree.widget.GestureOverlay;
import com.googlecode.eyesfree.widget.GestureOverlay.Gesture;
import com.googlecode.eyesfree.widget.GestureOverlay.GestureListener;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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

    public static final int REQUEST_CODE_PICK_CONTACT = 5001;

    public static final int REQUEST_CODE_PICK_BOOKMARK = 5003;

    public static final int REQUEST_CODE_PICK_SETTINGS = 5004;

    public static final int REQUEST_CODE_PICK_DIRECT_DIAL = 5005;

    public static final int REQUEST_CODE_PICK_DIRECT_MESSAGE = 5006;

    public static final int REQUEST_CODE_TALKING_DIALER_DIAL = 5007;

    public static final int REQUEST_CODE_TALKING_DIALER_MESSAGE = 5008;

    public static final int REQUEST_CODE_PICK_GMAIL_LABEL = 5009;

    public static final int REQUEST_CODE_PICK_VIDEO_CHAT = 5010;

    public static final int REQUEST_CODE_TALKING_DIALER_VIDEO = 5011;

    public static final int REQUEST_CODE_VOICE_RECO = 777;

    public static final String HOME_MENU = "Home";

    public static final int DIALOG_RENAME_MENU = 1312341;

    private int activeMode;

    private PackageManager pm;

    private FrameLayout mainFrameLayout;

    // We use an ImageView rather than setBackground to avoid the automatic
    // stretching caused by setting an image to be the background of a view.
    private ImageView wallpaperView;

    private AppChooserView appChooserView;

    public TextToSpeech tts;

    private FeedbackController feedbackController;

    public boolean isFocused;

    private MarvinShell self;

    private AuditoryWidgets widgets;

    private Menu currentMenu;

    private MenuManager menus;

    private ArrayList<String> menuHistory;

    long backKeyTimeDown = -1;

    /*
     * There is a race condition caused by the initialization of the TTS
     * happening at about the same time as the Activity's onRestart which leads
     * to the Marvin intro being cut off part way through by
     * announceCurrentMenu. The initial announcement is not interesting; it just
     * says "Home". Fix is to not even bother with the "Home" announcement when
     * the Shell has just started up.
     */
    private boolean justStarted;

    private boolean isTalkActive;

    private GestureOverlay gestureOverlay;

    private TextView mainText;

    private ProximitySensor proximitySensor;

    private AudioManager audioManager;

    int lastGesture = -1;

    // Path to shortcuts file.
    private String eyesfreePath = "/sdcard/eyesfree/";

    private String shortcutsFilename = eyesfreePath + "shortcuts.xml";

    private TelephonyManager mTelephonyManager;

    private HashMap<String, String> shortcutDescriptionToAction;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;

        justStarted = true;
        isFocused = true;
        activeMode = MAIN_VIEW;

        proximitySensor = new ProximitySensor(this, true, new ProximityChangeListener() {
                @Override
            public void onProximityChanged(float proximity) {
                if ((proximity == 0) && (tts != null)) {
                    // Stop speech if the user is touching the proximity sensor
                    tts.stop();
                }
            }
        });

        tts = new TextToSpeech(this, ttsInitListener);
        feedbackController = new FeedbackController(this);
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        setVolumeControlStream(AudioManager.STREAM_RING);
        pm = getPackageManager();

        IntentFilter appChangeFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        appChangeFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        appChangeFilter.addDataScheme("package");
        registerReceiver(appChangeReceiver, appChangeFilter);

        IntentFilter screenStateChangeFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateChangeReceiver, screenStateChangeFilter);

        initializeShortcutDescriptions();

        setContentView(R.layout.main);
        wallpaperView = (ImageView) self.findViewById(R.id.wallpaper);
        mainText = (TextView) self.findViewById(R.id.mainText);
        mainFrameLayout = (FrameLayout) findViewById(R.id.mainFrameLayout);
        gestureOverlay = new GestureOverlay(this, new ShellGestureListener());
        mainFrameLayout.addView(gestureOverlay, mainFrameLayout.getChildCount() - 2);

        widgets = new AuditoryWidgets(tts, self);

        menuHistory = new ArrayList<String>();
        menus = new MenuManager();
        menus.put(HOME_MENU, new Menu(HOME_MENU));
        loadMenus();
        switchMenu(HOME_MENU);

        IntentFilter mediaIntentFilter = new IntentFilter();
        mediaIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mediaIntentFilter.addDataScheme("file");
        registerReceiver(sdcardReceiver, mediaIntentFilter);

        new InitAppChooserTask().execute();
    }

    @Override
    public void onResume() {
        super.onResume();
        switchToMainView();
        if (proximitySensor != null) {
            proximitySensor.resume();
        }
        isTalkActive = false;
        if (Build.VERSION.SDK_INT > 10) {
            new TalkCheckTask().execute();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (proximitySensor != null) {
            proximitySensor.standby();
        }
    }

    /**
     * This function will be called when the shell receives a new launch intent
     * but is already on top of the activity stack. We use it to return to the
     * main menu when the home button is pressed.
     */
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        switchMenu(HOME_MENU);
        // Since switchMenu does not speak the menu name, we need to speak it
        // here.
        tts.speak(menus.get(HOME_MENU).getName(), TextToSpeech.QUEUE_FLUSH, null);
        if (activeMode == MENU_EDIT_MODE) {
            menus.save(shortcutsFilename);
            tts.speak(getString(R.string.exiting_edit_mode), TextToSpeech.QUEUE_ADD, null);
            activeMode = MAIN_VIEW;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        isFocused = hasFocus;
        if (hasFocus) {
            if (widgets != null) {
                int callState = widgets.getCallState();
                if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                    audioManager.setSpeakerphoneOn(true);
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
            if (sdcardReceiver != null) {
                unregisterReceiver(sdcardReceiver);
            }
        } catch (IllegalArgumentException e) {
            /*
             * Sometimes there may be two shutdown requests, in which case the
             * second request will fail.
             */
        }
    }

    /**
     * Once the TTS is initialized, loads the earcons and plays the Marvin intro
     * clip. ("Here I am, brain the size of a planet ...")
     */
    private OnInitListener ttsInitListener = new OnInitListener() {
            @Override
        public void onInit(int status) {
            switch (status) {
                case TextToSpeech.SUCCESS:
                    String pkgName = MarvinShell.class.getPackage().getName();
                    tts.addSpeech(
                            getString(R.string.marvin_intro_snd_), pkgName, R.raw.marvin_intro);
                    tts.addEarcon(getString(R.string.earcon_tock), pkgName, R.raw.tock_snd);
                    tts.addEarcon(getString(R.string.earcon_tick), pkgName, R.raw.tick_snd);
                    tts.speak(
                            getString(R.string.marvin_intro_snd_), TextToSpeech.QUEUE_FLUSH, null);
                    break;
            }
        }
    };

    /**
     * Listen for ACTION_MEDIA_MOUNTED broadcast to load shortcuts from card
     * after mounting. This is necessary if the shell is the default home
     * screen, since it will start before external storage is mounted.
     */
    private BroadcastReceiver sdcardReceiver = new BroadcastReceiver() {
            @Override
        public void onReceive(Context arg0, Intent intent) {
            if (new File(shortcutsFilename).isFile()) {
                loadMenus();
                switchMenu(HOME_MENU);
            }
        }
    };

    /**
     * Listens for ACTION_SCREEN_ON broadcasts so we can prompt the user to
     * unlock the phone if the shell is not focused.
     */
    private BroadcastReceiver screenStateChangeReceiver = new BroadcastReceiver() {
            @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                /*
                 * If the user is not in a phone call and the phone is not
                 * ringing, we can speak.
                 */
                if (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE) {
                    if (!isFocused && (tts != null)) {
                        tts.speak(
                                getString(R.string.please_unlock), TextToSpeech.QUEUE_FLUSH, null);
                    }
                }
            }
        }
    };

    /**
     * Listens for changes in the installed packages. If a package is added, we
     * reset our list of the activities for that package by querying with a
     * launch intent.
     */
    private BroadcastReceiver appChangeReceiver = new BroadcastReceiver() {
            @Override
        public void onReceive(Context context, Intent intent) {
            String packageName = intent.getData().getSchemeSpecificPart();
            if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                appChooserView.removePackage(packageName);
                tts.speak(getString(R.string.applist_reload), TextToSpeech.QUEUE_FLUSH, null);
            } else if (intent.getAction().equals(Intent.ACTION_PACKAGE_ADDED)) {
                appChooserView.removePackage(packageName);
                Intent targetIntent = new Intent(Intent.ACTION_MAIN, null);
                targetIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                targetIntent.setPackage(packageName);
                for (ResolveInfo info : pm.queryIntentActivities(targetIntent, 0)) {
                    String title = info.loadLabel(pm).toString();
                    if (title.length() == 0) {
                        title = info.activityInfo.name.toString();
                    }
                    appChooserView.addApplication(new AppInfo(title, info));
                }
                tts.speak(getString(R.string.applist_reload), TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    };

    /**
     * Initializes the shortcut type descriptions from resources.
     */
    private void initializeShortcutDescriptions() {
        shortcutDescriptionToAction = new HashMap<String, String>();
        shortcutDescriptionToAction.put(getString(R.string.application), "LAUNCH");
        shortcutDescriptionToAction.put(getString(R.string.bookmark), "BOOKMARK");
        shortcutDescriptionToAction.put(getString(R.string.contact), "CONTACT");
        shortcutDescriptionToAction.put(getString(R.string.direct_dial), "CALL");
        shortcutDescriptionToAction.put(getString(R.string.direct_message), "SMS");
        shortcutDescriptionToAction.put(getString(R.string.eyes_free_widget), "WIDGET");
        shortcutDescriptionToAction.put(getString(R.string.settings), "SETTINGS");
        shortcutDescriptionToAction.put(getString(R.string.none), "");

        // Check to see if the Gmail Label picker is available
        Intent intentGmailLabel = new Intent();
        ComponentName settings = new ComponentName(
                "com.google.android.gm", "com.google.android.gm.CreateLabelShortcutActivity");
        intentGmailLabel.setComponent(settings);
        intentGmailLabel.setAction(Intent.ACTION_CREATE_SHORTCUT);
        if (!pm.queryIntentActivities(intentGmailLabel, 0).isEmpty()) {
            shortcutDescriptionToAction.put(getString(R.string.gmail_label), "GMAIL_LABEL");
        }
        if (Build.VERSION.SDK_INT > 10) {
            // Check to see if video chat is available
            Intent i = new Intent();
            i.setClassName(
                    "com.google.android.talk",
                    "com.google.android.talk.videochat.VideoChatActivity");
            i.setAction("initiate");
            if (!pm.queryIntentActivities(i, 0).isEmpty()) {
                shortcutDescriptionToAction.put(getString(R.string.video_chat),
                        "VIDEO_CHAT");
            }
        }
    }

    /**
     * Move to the menu specified by id. Note that switchMenu does not cause the
     * TTS to announce the new menu name, since in most cases the name of the
     * menu has already been spoken while navigating the previous menu.
     */
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

    /**
     * Loads the menus from the XML file at shortcutsFilename. If no
     * user-specified shortcuts are present, loads the default shortcuts from
     * resources instead.
     */
    private void loadMenus() {
        final Context ctx = this;
        File efDir = new File(eyesfreePath);
        boolean directoryExists = efDir.isDirectory();
        if (!directoryExists) {
            efDir.mkdir();
        }

        if (new File(shortcutsFilename).isFile()) {
            menus = MenuManager.loadMenus(ctx, shortcutsFilename);
            if (!menus.containsKey(HOME_MENU)) {
                new File(shortcutsFilename).delete();
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

    /**
     * Create an intent to launch the specified application.
     *
     * @param packageName The application package.
     * @param className The class name of the activity to launch.
     * @return An intent that launches the application.
     */
    private static Intent makeClassLaunchIntent(String packageName, String className) {
        return new Intent("android.intent.action.MAIN").addCategory(
                "android.intent.category.LAUNCHER").setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setClassName(packageName, className);
    }

    public void onAppSelected(AppInfo appInfo) {
        if (activeMode == MENU_EDIT_MODE) {
            if (lastGesture > 0) {
                MenuItem menuItem = new MenuItem(appInfo.getTitle(), "LAUNCH", "", appInfo);
                currentMenu.put(lastGesture, menuItem);
                tts.speak(lastGesture + " - " + menuItem.label, TextToSpeech.QUEUE_FLUSH, null);
            }
            switchToMainView();
        } else {
            Intent intent = makeClassLaunchIntent(appInfo.getPackageName(), appInfo.getClassName());
            tts.playEarcon(getString(R.string.earcon_tick), 0, null);
            boolean launchSuccessful = true;
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                tts.speak(getString(R.string.application_not_installed), TextToSpeech.QUEUE_FLUSH,
                        null);
                launchSuccessful = false;
            }
        }
    }

    private void selectWidget() {
        final String[] items = new String[widgets.descriptionToWidget.size()];
        widgets.descriptionToWidget.keySet().toArray(items);
        Arrays.sort(items);
        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        builder.setTitle("Select widget");
        builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
            public void onClick(DialogInterface dialog, int item) {
                String widgetData = widgets.descriptionToWidget.get(items[item]);
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

        @Override
        public void onGestureStart(int g) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            if (g == GestureOverlay.Gesture.CENTER) {
                // If the gesture starts in the middle, don't start speaking.
                feedbackController.playVibration(R.array.pattern_center);
            } else {
                onGestureChange(g);
            }
        }

        @Override
        public void onGestureChange(int g) {
            String feedback;
            if (g == GestureOverlay.Gesture.CENTER) {
                feedbackController.playVibration(R.array.pattern_center);
                feedback = currentMenu.getName();
            } else {
                feedbackController.playVibration(R.array.pattern_center);
                MenuItem item = currentMenu.get(g);
                if (item != null) {
                    feedback = item.label;
                    // If the item is a menu, we want to look up the name.
                    if (item.action.equalsIgnoreCase("MENU") && menus.get(item.data) != null) {
                        feedback = menus.get(item.data).getName();
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

        @Override
        public void onGestureFinish(int g) {
            setVolumeControlStream(AudioManager.STREAM_RING);

            if (g == Gesture.CENTER) {
                switchMenu(HOME_MENU);
                tts.speak(menus.get(HOME_MENU).getName(), TextToSpeech.QUEUE_FLUSH, null);
                return;
            }

            MenuItem item = currentMenu.get(g);

            // If the gesture switches menus, that takes precedence.
            if (item != null && item.action.equalsIgnoreCase("MENU")) {
                if (menus.containsKey(item.data)) {
                    switchMenu(item.data);
                    if (!tts.isSpeaking()) {
                        tts.playEarcon(
                                getString(R.string.earcon_tick), TextToSpeech.QUEUE_FLUSH, null);
                    }
                }
                return;
            }

            handleMenuItem(g, item);
        }

        /**
         * @param g
         * @param item
         */
        private void handleMenuItem(int g, MenuItem item) {
            // Otherwise do the appropriate thing depending on mode.
            switch (activeMode) {
                case MAIN_VIEW:
                    if (item != null) {
                        if (item.action.equals("LAUNCH")) {
                            onAppSelected(item.appInfo);
                        } else if (item.action.equals("WIDGET")) {
                            widgets.runWidget(item.data);
                        } else if (item.action.equals("BOOKMARK")) {
                            Intent intentBookmark = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                    item.data));
                            if (isValidIntent(intentBookmark)) {
                                startActivity(intentBookmark);
                            }
                        } else if (item.action.equals("CONTACT")) {
                            Intent intentContact = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                    item.data));
                            if (isValidIntent(intentContact)) {
                                startActivity(intentContact);
                            }
                        } else if (item.action.equals("SETTINGS")) {
                            Intent intentSettings = new Intent(item.data);
                            if (isValidIntent(intentSettings)) {
                                startActivity(intentSettings);
                            }
                        } else if (item.action.equals("CALL")) {
                            Intent intentCall = new Intent(Intent.ACTION_CALL, Uri.parse(
                                    "tel:" + item.data));
                            if (isValidIntent(intentCall)) {
                                startActivity(intentCall);
                            }
                        } else if (item.action.equals("SMS")) {
                            Intent intentSms = new Intent(Intent.ACTION_VIEW, Uri.parse(
                                    "sms:" + item.data));
                            if (isValidIntent(intentSms)) {
                                startActivity(intentSms);
                            }
                        } else if (item.action.equals("GMAIL_LABEL")) {
                            Intent intentGmailLabel = new Intent();
                            ComponentName gmailActivity = new ComponentName("com.google.android.gm",
                                    "com.google.android.gm.ConversationListActivityGmail");
                            intentGmailLabel.setComponent(gmailActivity);
                            int firstSpace = item.data.indexOf(" ");
                            String account = item.data.substring(0, firstSpace);
                            String label = item.data.substring(firstSpace + 1);
                            intentGmailLabel.putExtra("account", account);
                            intentGmailLabel.putExtra("label", label);
                            if (isValidIntent(intentGmailLabel)) {
                                startActivity(intentGmailLabel);
                            }
                        } else if (item.action.equals("VIDEO_CHAT")) {
                            // If the user is not signed into talk, video calls
                            // will fail
                            if (!isTalkActive) {
                                tts.speak(
                                        getString(R.string.talk_disabled), TextToSpeech.QUEUE_FLUSH,
                                        null);
                                return;
                            }
                            String address = item.data;
                            if (Build.VERSION.SDK_INT < 10) {
                                Intent i = new Intent();
                                i.setClassName("com.google.android.talk",
                                        "com.google.android.talk.videochat.VideoChatActivity");
                                i.setAction("initiate");
                                String uriString = String
                                        .format(
                                                "content://com.google.android.providers.talk/messagesByAcctAndContact/1/%s",
                                                Uri.encode(address));
                                i.setData(Uri.parse(uriString));
                                if (isValidIntent(i)) {
                                    startActivity(i);
                                }
                            } else {
                                Intent i = new Intent();
                                i.setClassName("com.google.android.talk",
                                        "com.google.android.talk.videochat.VideoChatActivity");
                                i.setAction("initiate");
                                i.putExtra("accountId", (long) 1);
                                i.putExtra("from", address);
                                if (isValidIntent(i)) {
                                    startActivity(i);
                                }
                            }
                        }
                    }
                    break;
                case MENU_EDIT_MODE:
                    if (g != GestureOverlay.Gesture.CENTER) {
                        lastGesture = g;
                        showShortcutTypeDialog();
                    }
                    break;
            }
            mainText.setText(currentMenu.getName());
        }
    }

    /**
     * Displays an AlertDialog prompting the user to choose a shortcut type.
     * After a type is selected, launches the appropriate activity for selecting
     * the shortcut. When the activity finishes, the shortcut will be added in
     * onActivityResult.
     */
    private void showShortcutTypeDialog() {
        final CharSequence[] items = new CharSequence[shortcutDescriptionToAction.size()];
        shortcutDescriptionToAction.keySet().toArray(items);
        Arrays.sort(items);
        AlertDialog.Builder builder = new AlertDialog.Builder(self);
        builder.setTitle(getString(R.string.add_to_shell));
        builder.setItems(items, new DialogInterface.OnClickListener() {
                @Override
            public void onClick(DialogInterface dialog, int item) {
                String action = shortcutDescriptionToAction.get(items[item]);
                if (action.equals("LAUNCH")) {
                    switchToAppChooserView();
                } else if (action.equals("BOOKMARK")) {
                    Intent intentBookmark = new Intent();
                    ComponentName bookmarks = new ComponentName("com.google.marvin.shell",
                            "com.google.marvin.shell.BookmarkChooserActivity");
                    intentBookmark.setComponent(bookmarks);
                    startActivityForResult(intentBookmark, REQUEST_CODE_PICK_BOOKMARK);
                } else if (action.equals("CONTACT")) {
                    Intent intentContact = new Intent(
                            Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
                    startActivityForResult(intentContact, REQUEST_CODE_PICK_CONTACT);
                } else if (action.equals("CALL")) {
                    if (isTalkingDialerContactChooserAvailable()) {
                        Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
                        ComponentName slideDial = new ComponentName(
                                "com.google.marvin.talkingdialer",
                                "com.google.marvin.talkingdialer.TalkingDialer");
                        talkingDialerIntent.setComponent(slideDial);
                        startActivityForResult(
                                talkingDialerIntent, REQUEST_CODE_TALKING_DIALER_DIAL);
                    } else {
                        Intent intentDirectDial = new Intent(Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                        startActivityForResult(intentDirectDial, REQUEST_CODE_PICK_DIRECT_DIAL);
                    }
                } else if (action.equals("SMS")) {
                    if (isTalkingDialerContactChooserAvailable()) {
                        Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
                        ComponentName slideDial = new ComponentName(
                                "com.google.marvin.talkingdialer",
                                "com.google.marvin.talkingdialer.TalkingDialer");
                        talkingDialerIntent.setComponent(slideDial);
                        startActivityForResult(
                                talkingDialerIntent, REQUEST_CODE_TALKING_DIALER_MESSAGE);
                    } else {
                        Intent intentDirectMessage = new Intent(Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                        startActivityForResult(
                                intentDirectMessage, REQUEST_CODE_PICK_DIRECT_MESSAGE);
                    }
                } else if (action.equals("VIDEO_CHAT")) {
                    if (isTalkingDialerContactChooserAvailable()) {
                        Intent talkingDialerIntent = new Intent(Intent.ACTION_PICK);
                        talkingDialerIntent.putExtra("ContactsMode",
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI.toString());
                        ComponentName slideDial = new ComponentName(
                                "com.google.marvin.talkingdialer",
                                "com.google.marvin.talkingdialer.TalkingDialer");
                        talkingDialerIntent.setComponent(slideDial);
                        startActivityForResult(
                                talkingDialerIntent, REQUEST_CODE_TALKING_DIALER_VIDEO);
                    } else {
                        Intent intentVideoChat = new Intent(Intent.ACTION_PICK,
                                ContactsContract.CommonDataKinds.Email.CONTENT_URI);
                        startActivityForResult(intentVideoChat, REQUEST_CODE_PICK_VIDEO_CHAT);
                    }
                } else if (action.equals("WIDGET")) {
                    selectWidget();
                } else if (action.equals("SETTINGS")) {
                    Intent intentSettings = new Intent();
                    ComponentName settings = new ComponentName("com.google.marvin.shell",
                            "com.google.marvin.shell.SettingsShortcutChooserActivity");
                    intentSettings.setComponent(settings);
                    startActivityForResult(intentSettings, REQUEST_CODE_PICK_SETTINGS);
                } else if (action.equals("GMAIL_LABEL")) {
                    Intent intentGmailLabel = new Intent();
                    ComponentName settings = new ComponentName("com.google.android.gm",
                            "com.google.android.gm.CreateLabelShortcutActivity");
                    intentGmailLabel.setComponent(settings);
                    intentGmailLabel.setAction(Intent.ACTION_CREATE_SHORTCUT);
                    startActivityForResult(intentGmailLabel, REQUEST_CODE_PICK_GMAIL_LABEL);
                } else {
                    currentMenu.remove(lastGesture);
                }
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
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
                    "com.google.marvin.talkingdialer.TalkingDialer");
            talkingDialerIntent.setComponent(slideDial);
            return (pm.queryIntentActivities(talkingDialerIntent, 0).size() > 0);
        } else {
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_SEARCH:
                if (activeMode == MAIN_VIEW) {
                    AppInfo talkingDialer = new AppInfo(null, "com.google.marvin.talkingdialer",
                            "com.google.marvin.talkingdialer.TalkingDialer");
                    onAppSelected(talkingDialer);
                    return true;
                } else {
                    return false;
                }
            case KeyEvent.KEYCODE_CALL:
                if (activeMode == MAIN_VIEW) {
                    AppInfo talkingDialer = new AppInfo(null, "com.google.marvin.talkingdialer",
                            "com.google.marvin.talkingdialer.TalkingDialer");
                    onAppSelected(talkingDialer);
                    return true;
                } else {
                    return false;
                }
            case KeyEvent.KEYCODE_BACK:
                if (backKeyTimeDown == -1) {
                    backKeyTimeDown = System.currentTimeMillis();
                    class QuitCommandWatcher implements Runnable {
                    @Override
                        public void run() {
                            try {
                                Thread.sleep(3000);
                                if ((backKeyTimeDown > 0)
                                        && (System.currentTimeMillis() - backKeyTimeDown > 2500)) {
                                    startActivity(getSystemHomeIntent());
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
                        tts.speak(currentMenu.getName(), TextToSpeech.QUEUE_FLUSH, null);
                        return true;
                    case MENU_EDIT_MODE:
                        menus.save(shortcutsFilename);
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
            case REQUEST_CODE_VOICE_RECO:
                if (resultCode == Activity.RESULT_OK) {
                    ArrayList<String> results = data.getExtras()
                            .getStringArrayList(RecognizerIntent.EXTRA_RESULTS);
                    new Thread(new OneVoxSpeaker(results.get(0))).start();
                }
                break;
            case REQUEST_CODE_PICK_BOOKMARK:
            case REQUEST_CODE_PICK_CONTACT:
            case REQUEST_CODE_PICK_SETTINGS:
            case REQUEST_CODE_PICK_DIRECT_DIAL:
            case REQUEST_CODE_PICK_VIDEO_CHAT:
            case REQUEST_CODE_PICK_DIRECT_MESSAGE:
            case REQUEST_CODE_TALKING_DIALER_DIAL:
            case REQUEST_CODE_TALKING_DIALER_MESSAGE:
            case REQUEST_CODE_TALKING_DIALER_VIDEO:
            case REQUEST_CODE_PICK_GMAIL_LABEL:
                if (resultCode == Activity.RESULT_OK) {
                    addActivityResultShortcut(requestCode, data);
                }
                break;
        }
    }

    /**
     * Handles all the possible cases (indexed by requestCode) of constructing a
     * MenuItem from intent data returned via activity result. Then adds the
     * MenuItem as a shortcut.
     */
    private void addActivityResultShortcut(int requestCode, Intent data) {
        MenuItem menuItem = null;
        Cursor c;
        String title, name, phoneNumber, emailAddress, number, label;
        switch (requestCode) {
            case REQUEST_CODE_PICK_BOOKMARK:
                title = data.getStringExtra("TITLE");
                String url = data.getStringExtra("URL");
                menuItem = new MenuItem(title, "BOOKMARK", url, null);
                break;
            case REQUEST_CODE_PICK_CONTACT:
                c = managedQuery(data.getData(), null, null, null, null);
                if (c.moveToFirst()) {
                    name = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    long id = c.getLong(c.getColumnIndexOrThrow(BaseColumns._ID));
                    String lookup = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY));
                    String uriString = ContactsContract.Contacts.getLookupUri(id, lookup)
                            .toString();
                    menuItem = new MenuItem(name, "CONTACT", uriString, null);
                }
                break;
            case REQUEST_CODE_PICK_SETTINGS:
                title = data.getStringExtra("TITLE");
                String action = data.getStringExtra("ACTION");
                menuItem = new MenuItem(title, "SETTINGS", action, null);
                break;
            case REQUEST_CODE_PICK_DIRECT_DIAL:
                c = managedQuery(data.getData(), null, null, null, null);
                if (c.moveToFirst()) {
                    name = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    phoneNumber = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    menuItem = new MenuItem(
                            getString(R.string.call, name), "CALL", phoneNumber, null);
                }
                break;
            case REQUEST_CODE_PICK_DIRECT_MESSAGE:
                c = managedQuery(data.getData(), null, null, null, null);
                if (c.moveToFirst()) {
                    name = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    phoneNumber = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    menuItem = new MenuItem(
                            getString(R.string.message, name), "SMS", phoneNumber, null);
                }
                break;
            case REQUEST_CODE_TALKING_DIALER_DIAL:
                number = data.getStringExtra("number");
                label = data.getStringExtra("label");
                if (label == null)
                    label = number;
                menuItem = new MenuItem(getString(R.string.call, label), "CALL", number, null);
                break;
            case REQUEST_CODE_TALKING_DIALER_MESSAGE:
                number = data.getStringExtra("number");
                label = data.getStringExtra("label");
                if (label == null)
                    label = number;
                menuItem = new MenuItem(getString(R.string.message, label), "SMS", number, null);
                break;
            case REQUEST_CODE_PICK_GMAIL_LABEL:
                name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
                Intent labelIntent = (Intent) data.getExtras().get(Intent.EXTRA_SHORTCUT_INTENT);
                String account = labelIntent.getStringExtra("account");
                label = labelIntent.getStringExtra("label");
                menuItem = new MenuItem(name + " " + getString(R.string.gmail), "GMAIL_LABEL",
                        account + " " + label, null);
                break;
            case REQUEST_CODE_PICK_VIDEO_CHAT:
                c = managedQuery(data.getData(), null, null, null, null);
                if (c.moveToFirst()) {
                    name = c.getString(
                            c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));
                    emailAddress = c.getString(
                            c.getColumnIndexOrThrow(
                                    ContactsContract.CommonDataKinds.Email.ADDRESS));
                    menuItem = new MenuItem(
                            getString(R.string.video_call, name), "VIDEO_CHAT", emailAddress, null);
                }
                break;
            case REQUEST_CODE_TALKING_DIALER_VIDEO:
                emailAddress = data.getStringExtra("email");
                label = data.getStringExtra("label");
                if (label == null)
                    label = emailAddress;
                menuItem = new MenuItem(
                        getString(R.string.video_call, label), "VIDEO_CHAT", emailAddress, null);
                break;
        }
        if (menuItem != null && lastGesture > 0) {
            currentMenu.put(lastGesture, menuItem);
            tts.speak(lastGesture + " - " + menuItem.label, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * Brings up the app. chooser view. If in edit mode, this will not change
     * the active mode, which leads to a bug where edit mode menu items appear
     * in the app. chooser. TODO(credo): Fix activeMode problems. This may
     * require making the app. chooser a separate activity.
     */
    public void switchToAppChooserView() {
        if (appChooserView != null) {
            LinearLayout ll = (LinearLayout) findViewById(R.id.appChooserControlsArea);
            ll.setVisibility(View.VISIBLE);
            RelativeLayout rl = (RelativeLayout) findViewById(R.id.homeScreenControlsArea);
            rl.setVisibility(View.GONE);
            appChooserView.requestFocus();
            appChooserView.resetListState();
            appChooserView.speakCurrentApp(false);
            if (activeMode == MAIN_VIEW) {
                activeMode = APPLAUNCHER_VIEW;
            }
        }
    }

    /**
     * Brings up the main view. Note that this does not change edit mode, this
     * is ugly and needs to change. When in edit mode, gives some verbal
     * feedback to disambiguate.
     */
    public void switchToMainView() {
        LinearLayout ll = (LinearLayout) findViewById(R.id.appChooserControlsArea);
        ll.setVisibility(View.GONE);
        RelativeLayout rl = (RelativeLayout) findViewById(R.id.homeScreenControlsArea);
        rl.setVisibility(View.VISIBLE);
        setContentView(mainFrameLayout);
        mainFrameLayout.requestFocus();
        switch (activeMode) {
            case MENU_EDIT_MODE:
                tts.speak(getString(R.string.editing) + " " + currentMenu.getName(),
                        TextToSpeech.QUEUE_ADD, null);
                break;
            case APPLAUNCHER_VIEW:
                activeMode = MAIN_VIEW;
                if (justStarted) {
                    justStarted = false;
                } else {
                    tts.speak(currentMenu.getName(), TextToSpeech.QUEUE_FLUSH, null);
                }
                break;
            case MAIN_VIEW:
                break;
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
                String detailsFor = getString(
                        R.string.details_for, appChooserView.getCurrentAppTitle());
                menu.add(NONE, R.string.details_for, 0, detailsFor)
                        .setIcon(android.R.drawable.ic_menu_info_details);
                menu.add(NONE, R.string.uninstall, 1, uninstallText)
                        .setIcon(android.R.drawable.ic_menu_delete);
                return true;
            case MAIN_VIEW:
                String editMenus = getString(R.string.edit_menus);
                menu.add(NONE, R.string.edit_menus, 0, editMenus)
                        .setIcon(android.R.drawable.ic_menu_edit);
                return true;
            case MENU_EDIT_MODE:
                String restoreDefault = getString(R.string.restore_default_menus);
                menu.add(NONE, R.string.restore_default_menus, 2, restoreDefault)
                        .setIcon(android.R.drawable.ic_menu_revert);
                String insertMenuLeft = getString(R.string.insert_menu_left);
                menu.add(NONE, R.string.insert_menu_left, 0, insertMenuLeft)
                        .setIcon(R.drawable.ic_menu_left);
                String insertMenuRight = getString(R.string.insert_menu_right);
                menu.add(NONE, R.string.insert_menu_right, 1, insertMenuRight)
                        .setIcon(R.drawable.ic_menu_right);
                String renameMenu = getString(R.string.rename_menu);
                menu.add(NONE, R.string.rename_menu, 3, renameMenu)
                        .setIcon(android.R.drawable.ic_menu_edit);
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
                if (new File(shortcutsFilename).isFile()) {
                    new File(shortcutsFilename).delete();
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
                    alert.setTitle(getString(R.string.enter_new_menu_name));
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
        if (filepath != null && filepath.length() > 0) {
            Bitmap bmp = BitmapFactory.decodeFile(filepath);
            if (bmp != null) {
                wallpaperView.setVisibility(View.VISIBLE);
                wallpaperView.setImageBitmap(bmp);
                return;
            }
        }
        wallpaperView.setVisibility(View.GONE);
    }

    /**
     * This is an asynchronous task that queries the package manager for
     * launchable activities, builds a sorted list of AppInfo and uses it to
     * initialize the AppChooserView.
     */
    private class InitAppChooserTask extends AsyncTask<Void, Void, ArrayList<AppInfo>> {
        @Override
        public ArrayList<AppInfo> doInBackground(Void... params) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
            ArrayList<AppInfo> appList = new ArrayList<AppInfo>();
            for (ResolveInfo info : apps) {
                String title = info.loadLabel(pm).toString();
                if (title.length() == 0) {
                    title = info.activityInfo.name.toString();
                }
                appList.add(new AppInfo(title, info));
            }
            Collections.sort(appList);
            return appList;
        }

        @Override
        public void onPostExecute(ArrayList<AppInfo> appList) {
            appChooserView = (AppChooserView) findViewById(R.id.appChooserView);
            appChooserView.setAppList(appList);
        }
    }

    /**
     * Call this to get an intent for returning to a home screen other than the
     * shell. Ideally, this is the system default launcher, but might be
     * something else.
     */
    public Intent getSystemHomeIntent() {
        Intent homeIntent = new Intent("android.intent.action.MAIN");
        homeIntent.addCategory("android.intent.category.HOME");

        ResolveInfo[] homeAppsArray = new ResolveInfo[0];
        PackageManager pm = getPackageManager();
        homeAppsArray = pm.queryIntentActivities(homeIntent, 0).toArray(homeAppsArray);

        for (int i = 0; i < homeAppsArray.length; i++) {
            ActivityInfo aInfo = homeAppsArray[i].activityInfo;
            if (!aInfo.packageName.equals("com.google.marvin.shell")
                    && !aInfo.packageName.equals("com.google.marvin.config")) {
                homeIntent.setClassName(aInfo.packageName, aInfo.name);
                break;
            }
        }
        return homeIntent;
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

        @Override
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

    
    private boolean isValidIntent(Intent i) {
        if (pm.queryIntentActivities(i, 0).isEmpty()) {
           tts.speak(getString(R.string.item_invalid), TextToSpeech.QUEUE_FLUSH, null);
           return false;
        }
        return true;
    }
    
    /**
     * Sends a test query to gtalk for the actual user. If this query fails, the
     * gtalk is bugging out and needs a good reboot. Otherwise, we want to
     * confirm the user is in fact logged in.
     *
     * @return true if talk is active
     */
    private boolean isTalkActive() {

        String[] proj = new String[] {
                Contacts.DISPLAY_NAME, Email.ADDRESS,
                StatusUpdates.CHAT_CAPABILITY,
                StatusUpdates.PRESENCE };

        String filter = StatusUpdates.CHAT_CAPABILITY + " > -1";

        Cursor statusCursor = getBaseContext().getContentResolver().query(
                ContactsContract.Data.CONTENT_URI, proj, filter, null, null);

        // we want to check that we in fact have supported contacts
        if (!statusCursor.moveToFirst()) {
            return false;
        } else if (statusCursor.getCount() == 1) {
            AccountManager accountManager = AccountManager.get(getBaseContext());
            Account[] accounts = accountManager.getAccountsByType("com.google");
            Account account;
            if (accounts.length > 0) {
                account = accounts[0];
            } else {
                return false;
            }
            String userEmail = account.name;
            if (statusCursor.getString(1).equals(userEmail)) {
                return false;
            }
        }

        return true;
    }

    /**
     * This is an asynchronous task that queries the StatusUpdates database for
     * each of the phone contacts to determine if that are vChatCapable
     */
    private class TalkCheckTask extends AsyncTask<Void, Void, Boolean> {

        public TalkCheckTask() {
            super();
        }

        @Override
        public Boolean doInBackground(Void... params) {
            return isTalkActive();
        }

        @Override
        public void onPostExecute(Boolean result) {
            isTalkActive = result;
        }
    }
}
