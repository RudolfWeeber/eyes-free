/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import com.google.android.marvin.talkback.ProximitySensor.ProximityChangeListener;
import com.google.tts.TextToSpeechBeta;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link AccessibilityService} that provides spoken feedback.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkBackService extends AccessibilityService {

    /**
     * {@link Intent} broadcast action for announcing the notifications state.
     * </p> Note: Sending intent broadcast commands to TalkBack must be
     * performed through {@link Context#sendBroadcast(Intent, String)}
     */
    public static final String ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND = "com.google.android.marvin.talkback.ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND";

    /**
     * {@link Intent} broadcast action for querying the state of TalkBack. </p>
     * Note: Sending intent broadcast commands to TalkBack must be performed
     * through {@link Context#sendBroadcast(Intent, String)}
     */
    @Deprecated
    // TODO(caseyburkhardt): Remove when we decide to no longer support intent broadcasts for
    // querying the current state of TalkBack.
    public static final String ACTION_QUERY_TALKBACK_ENABLED_COMMAND = "com.google.android.marvin.talkback.ACTION_QUERY_TALKBACK_ENABLED_COMMAND";

    /**
     * Result that TalkBack is enabled.
     *
     * @see #ACTION_QUERY_TALKBACK_ENABLED_COMMAND
     */
    public static final int RESULT_TALKBACK_ENABLED = 0x00000001;

    /**
     * Result that TalkBack is disabled.
     *
     * @see #ACTION_QUERY_TALKBACK_ENABLED_COMMAND
     */
    public static final int RESULT_TALKBACK_DISABLED = 0x00000002;

    /**
     * Permission to send {@link Intent} broadcast commands to TalkBack.
     */
    public static final String PERMISSION_SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK = "com.google.android.marvin.talkback.PERMISSION_SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK";

    /**
     * Tag for logging.
     */
    private static final String LOG_TAG = "TalkBackService";

    /**
     * To account for SVox camel-case trouble.
     */
    private static final Pattern sCamelCasePrefixPattern = Pattern.compile("([a-z0-9])([A-Z])");

    /**
     * To account for SVox camel-case trouble.
     */
    private static final Pattern sCamelCaseSuffixPattern = Pattern.compile("([A-Z])([a-z0-9])");

    /**
     * Manages the pending notifications.
     */
    private static final NotificationCache sNotificationCache = new NotificationCache();

    /**
     * Timeout for waiting the events to settle down before speaking
     */
    private static final long EVENT_TIMEOUT = 200;

    /**
     * Timeout for waiting the events to settle down before speaking
     */
    private static final long EVENT_TIMEOUT_IN_CALL_SCREEN = 3000;

    /**
     * The class name of the in-call screen.
     */
    private static final String CLASS_NAME_IN_CALL_SCREEN = "com.android.phone.InCallScreen";

    /**
     * The package name of the Accessibility Settings Manager
     */
    private static final String SETTINGS_MANAGER_PACKAGE = "com.google.android.marvin.asm";

    /**
     * Speak action.
     */
    private static final int WHAT_SPEAK = 1;

    /**
     * Speak while the phone is ringing action.
     */
    private static final int WHAT_SPEAK_WHILE_IN_CALL = 2;

    /**
     * Stop speaking action.
     */
    private static final int WHAT_STOP_ALL_SPEAKING = 3;

    /**
     * Start the TTS service.
     */
    private static final int WHAT_START_TTS = 4;

    /**
     * Stop the TTS service.
     */
    private static final int WHAT_SHUTDOWN_TTS = 5;

    /**
     * Switch TTS systems (from or to TTS Extended).
     */
    private static final int WHAT_SWITCH_TTS = 6;

    /**
     * Space string constant.
     */
    private static final String SPACE = " ";

    /**
     * Opening bracket character constant.
     */
    private static final char OPEN_SQUARE_BRACKET = '[';

    /**
     * Closing bracket character constant.
     */
    private static final char CLOSE_SQUARE_BRACKET = ']';

    /**
     * The name of the contacts package used to fix a specific behavior in
     * Dialer
     */
    private static final String PACKAGE_NAME_CONTACTS = "com.android.contacts";

    /**
     * Key for registering a callback for speech completion.
     */
    private static final String KEY_SPEAK_COMPLETE_CALLBACK = "key_speak_complete_callback";

    /**
     * {@link IntentFilter} with all commands that can be executed by third
     * party applications or services via intent broadcasting.
     */
    private static final IntentFilter sCommandInterfaceIntentFilter = new IntentFilter();
    static {
        sCommandInterfaceIntentFilter.addAction(ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND);
        // add other command intents here
    }

    /**

     * Notification ID for the "TalkBack Crash Report" notification
     */
    private static final int CRASH_NOTIFICTION_ID = 2;
    
    /**
     * Queuing mode - undefined.
     */
    public static final int QUEUING_MODE_UNDEFINED = 0;

    /**
     * Queuing mode - queue the utterance to be spoken.
     */
    public static final int QUEUING_MODE_QUEUE = 1;

    /**
     * Queuing mode - interrupt the spoken before speaking.
     */
    public static final int QUEUING_MODE_INTERRUPT = 2;

    /**
     * The maximal size to the queue of cached events.
     */
    private static final int EVENT_QUEUE_MAX_SIZE = 2;

    /**
     * Ringer preference - speak at all ringer volumes.
     */
    public static final int PREF_RINGER_ALL = 0;

    /**
     * Ringer preference - speak unless silent mode.
     */
    public static final int PREF_RINGER_NOT_SILENT = 1;

    /**
     * Ringer preference - speak unless silent or vibrate mode.
     */
    public static final int PREF_RINGER_NOT_SILENT_OR_VIBRATE = 2;

    /**
     * Ringer preference - default.
     */
    public static final int PREF_RINGER_DEFAULT = PREF_RINGER_ALL;

    /**
     * Screen preference - allow speech when screen is off.
     */
    public static final int PREF_SCREEN_OFF_ALLOWED = 0;

    /**
     * Screen preference - do not allow speech when screen is off.
     */
    public static final int PREF_SCREEN_OFF_DISALLOWED = 1;

    /**
     * Screen preference - default.
     */
    public static final int PREF_SCREEN_DEFAULT = PREF_SCREEN_OFF_ALLOWED;

    /**
     * Caller ID preference - default.
     */
    public static final boolean PREF_CALLER_ID_DEFAULT = true;

    /**
     * TTS Extended preference - default.
     */
    public static final boolean PREF_TTS_EXTENDED_DEFAULT = false;
    
    /**
     * Proximity Sensor preference - default.
     */
    public static final boolean PREF_PROXIMITY_DEFAULT = true;
    
    /**
     * Period for the proximity sensor to remain active after last utterance (in milliseconds).
     */
    public static final long PROXIMITY_SENSOR_CUTOFF_THRESHOLD = 1000;
    
    /**
     * Flag if the infrastructure has been initialized.
     */
    private static boolean sInfrastructureInitialized = false;
    
    /**
     * Flag if the TTS service has been initialized.
     */
    private static boolean sTtsInitialized = false;

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

    /**
     * Reusable map used for passing parameters to the TextToSpeech.
     */
    private final HashMap<String, String> mSpeechParamtersMap = new HashMap<String, String>();

    /**
     * Listeners interested in the TalkBack initialization state.
     */
    private final ArrayList<InfrastructureStateListener> mInfrastructureStateListeners = new ArrayList<InfrastructureStateListener>();

    /**
     * Flag if a notification is currently spoken.
     */
    private boolean mSpeakingNotification;

    /**
     * The TTS engine. Only one of this and mTtsExtended will be non-null at a time.
     */
    private TextToSpeech mTts = null;

    /**
     * The TTS engine being initialized.
     */
    private TextToSpeech mTtsInitializing = null;

    /**
     * The TTS extended engine. Only one of this and mTts will be non-null at a time.
     */
    private TextToSpeechBeta mTtsExtended = null;

    /**
     * The TTS extended engine being initialized.
     */
    private TextToSpeechBeta mTtsExtendedInitializing = null;

    /**
     * Proximity sensor for implementing "shut up" functionality.
     */
    private ProximitySensor mProximitySensor;

    /**
     * processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private SpeechRuleProcessor mSpeechRuleProcessor;

    /**
     * The last event - used to auto-determine the speech queue mode.
     */
    private int mLastEventType;

    /**
     * The audio manager used for changing the ringer volume for incoming calls.
     */
    private AudioManager mAudioManager;

    /**
     * The activity manager used for determining currently open activities.
     */
    private ActivityManager mActivityManager;

    /**
     * The telephony manager used to determine the call state.
     */
    private TelephonyManager mTelephonyManager;

    /**
     * The manager for TalkBack plug-ins.
     */
    private PluginManager mPluginManager;

    /**
     * {@link BroadcastReceiver} for tracking the phone state.
     */
    private BroadcastReceiver mPhoneStateBroadcastReceiver;
    
    /**
     * {@link Runnable} for processing the standby of the proximity sensor.
     */
    private Runnable mProximitySensorSilencer;
    
    /**
     * The current ringer mode of the device.
     */
    private int mRingerMode;

    /**
     * The override for the queue mode of the next event.
     */
    private int mNextEventQueueMode;

    /**
     * Access to preferences.
     */
    SharedPreferences mPrefs;

    /**
     * Whether speech should be silenced based on the ringer mode.
     */
    private int mRingerPref;

    /**
     * Whether speech should be silenced based on screen status.
     */
    private int mScreenPref;

    /**
     * Whether Caller ID should be spoken.
     */
    private boolean mCallerIdPref;
    
    /**
     * Whether TTS Extended should be used.
     */
    private boolean mTtsExtendedPref;

    /**
     * Whether to use the proximity sensor to silence speech.
     */
    private boolean mProximityPref;

    /**
     * The version code of the last known running version of TalkBack
     */
    private int mLastVersion;

    /**
     * The version code of the currently running version of TalkBack
     */
    private int mCurVersion;

    /**
     * Whether the screen is off.
     */
    private boolean mScreenIsOff;

    /**
     * The last spoken accessibility event, used for crash reporting.
     */
    private AccessibilityEvent mLastSpokenEvent = null;
    
    /**
     * The filename used for logging crash reports.
     */
    private static final String TALKBACK_CRASH_LOG = "talkback_crash.log";

    /**
     * The email address that receives TalkBack crash reports.
     */
    private static final String[] CRASH_REPORT_EMAILS = {"eyes.free.crash.reports@gmail.com"};
    
    /**
     * Static handle to TalkBack so CommandInterfaceBroadcastReceiver can access
     * it.
     */
    static TalkBackService sInstance;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        shutdownInfrastructure();
    }

    /**
     * Returns true if TalkBack is running and initialized.
     */
    public static boolean isServiceInitialized() {
        return sInfrastructureInitialized && sTtsInitialized;
    }

    /**
     * Returns the TalkBackService instance if it's running and initialized,
     * otherwise returns null.
     */
    public static TalkBackService getInstance() {
        if (sInfrastructureInitialized) {
            return sInstance;
        }

        return null;
    }

    /**
     * @return The service instance as {@link Context} if it has been
     *         instantiated regardless if infrastructure has been initialized;
     */
    public static Context asContext() {
        return sInstance;
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        if (!sInfrastructureInitialized) {
            return;
        }
        if (mProximitySensor != null) {
            mProximitySensor.shutdown();
        }

        mSpeechHandler.obtainMessage(WHAT_SHUTDOWN_TTS).sendToTarget();

        unregisterReceiver(mPhoneStateBroadcastReceiver);

        sInfrastructureInitialized = false;
        notifyInfrastructureSteteListeners();
    }

    @Override
    public void onServiceConnected() {
        setServiceInfo();
        initializeInfrastructure();
        processVersionSpecificTasks();
        registerUncaughtExceptionHandler();
        processCrashLog();
    }

    /**
     * Sets the {@link AccessibilityService} for configuring how the system
     * handles TalkBack.
     */
    public void setServiceInfo() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
    }

    /**
     * Initializes the infrastructure.
     */
    private void initializeInfrastructure() {
        // start the TTS service
        mSpeechHandler.obtainMessage(WHAT_START_TTS).sendToTarget();

        // create a speech processor for generating utterances
        mSpeechRuleProcessor = new SpeechRuleProcessor();

        // add speech strategy for third-party apps; later this may be loaded
        // dynamically from another file
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_thirdparty);

        // add speech strategy for specific built-in Android apps
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_apps);

        // add generic speech strategy for views in any app; this should always be last
        // so that the app-specific rules above can override the generic rules
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy);
       
        // Create and register in a proximity sensor for stopping speech
        initializeProximitySensor();

        // Create a filter with the phone state broadcast intents we are
        // interested in
        mPhoneStateBroadcastReceiver = new PhoneStateBroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // register for broadcasts of interest
        registerReceiver(mPhoneStateBroadcastReceiver, filter, null, null);

        // get the AudioManager and configure according the current ring mode
        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);

        // get the ringer mode on start
        mRingerMode = mAudioManager.getRingerMode();

        // get the ActivityManager
        mActivityManager = (ActivityManager) getSystemService(Service.ACTIVITY_SERVICE);

        // get the TelephonyManager
        mTelephonyManager = (TelephonyManager) getSystemService(Service.TELEPHONY_SERVICE);

        // instantiate the plug-in manager and load plug-ins
        mPluginManager = new PluginManager(this, mSpeechRuleProcessor);
        addInfrastructureSteteListener(mPluginManager);

        // load preferences
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        reloadPreferences();

        // write preferences in case we set anything to its default value for the first time.
        SharedPreferences.Editor prefsEditor = mPrefs.edit();
        prefsEditor.putString(getString(R.string.pref_speak_ringer_key), "" + mRingerPref);
        prefsEditor.putString(getString(R.string.pref_speak_screenoff_key), "" + mScreenPref);
        prefsEditor.putBoolean(getString(R.string.pref_caller_id_key), mCallerIdPref);
        prefsEditor.putBoolean(getString(R.string.pref_tts_extended_key), mTtsExtendedPref);
        prefsEditor.putBoolean(getString(R.string.pref_proximity_key), mProximityPref);
        prefsEditor.commit();

        mScreenIsOff = false;

        sInfrastructureInitialized = true;
        notifyInfrastructureSteteListeners();
    }
    
    private void initializeProximitySensor() {
        mProximitySensor = new ProximitySensor(this, true, new ProximityChangeListener() {
            @Override
            public void onProximityChanged(float proximity) {
                if (proximity == 0) {
                    // Stop all speech if the user is touching the proximity sensor
                    if (mTts != null || mTtsExtended != null) {
                        mSpeechHandler.obtainMessage(WHAT_STOP_ALL_SPEAKING).sendToTarget();
                    }
                }
            }
        });
        
        // Create a Runnable for causing the standby of the proximity sensor.
        mProximitySensorSilencer = new Runnable() {
            @Override
            public void run() {
                if (mProximitySensor != null) {
                    mProximitySensor.standby();
                }
            }
        };
    }

    /**
     * Adds an {@link InfrastructureStateListener}.
     */
    public void addInfrastructureSteteListener(InfrastructureStateListener listener) {
        mInfrastructureStateListeners.add(listener);
    }

    /**
     * Removes an {@link InfrastructureStateListener}.
     */
    public void removeInfrastructureStateListener(InfrastructureStateListener listener) {
        mInfrastructureStateListeners.remove(listener);
    }
 
     /**
      * Version 12 (2.4.0) introduced a preference for tracking the last run
      * version of TalkBack. This method processes one-time tasks defined to run
      * after upgrades or installations from a specific version.
      */
     private void processVersionSpecificTasks() {
         // Obtain the current and previous version numbers
         mLastVersion = mPrefs.getInt(getString(R.string.pref_last_talkback_version), 0);
         PackageManager packageManager = getPackageManager();
         try {
             mCurVersion = packageManager.getPackageInfo(getPackageName(), 0).versionCode;
         } catch (NameNotFoundException e) {
             // TalkBack couldn't locate it's own PackageInfo, probably not a
             // good thing.
             mCurVersion = 0;
         }
         if (mLastVersion == 0) {
             // Display a notification about the settings activity only if
             // the user is running version 12 or higher and did a clean install
             // or upgraded from a version earlier than 12 and the application is
             // not yet already installed.
             Intent settingsManager = new Intent(Intent.ACTION_MAIN);
             settingsManager.setPackage(SETTINGS_MANAGER_PACKAGE);
             List<ResolveInfo> settingsManagerPresence = packageManager.queryIntentActivities(
                     settingsManager, 0);
             if (settingsManagerPresence == null || settingsManagerPresence.isEmpty()) {
                 displaySettingsAvailableNotification();
             }
         } else {
             // We want to track the number of times that TalkBack has started
             // without notifying the user about the new settings activity. This
             // will be used later to tune further announcements.
             int notificationlessLaunches = mPrefs.getInt(
                     getString(R.string.pref_notificationless_launches), 0);
             SharedPreferences.Editor editor = mPrefs.edit();
             editor.putInt(getString(R.string.pref_notificationless_launches),
                     notificationlessLaunches + 1);
             editor.commit();
         }
         if (mLastVersion != mCurVersion) {
             SharedPreferences.Editor editor = mPrefs.edit();
             editor.putInt(getString(R.string.pref_last_talkback_version), mCurVersion);
             editor.commit();
         }
     }
 
     /**
      * Displays a notification that TalkBack now offers customizable settings by
      * downloading the Accessibility Settings Manager application.
      */
    private void displaySettingsAvailableNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent launchMarketIntent = new Intent(Intent.ACTION_VIEW);
        launchMarketIntent.setData(Uri.parse(getString(R.string.settings_manager_market_uri)));
        Notification notification = new Notification(-1,
                getString(R.string.title_talkback_settings_availible),
                System.currentTimeMillis());
        notification.setLatestEventInfo(this,
                getString(R.string.title_talkback_settings_availible),
                getString(R.string.message_talkback_settings_availible), PendingIntent.getActivity(
                        this, 0, launchMarketIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONGOING_EVENT;
    }
    
    /**
     * Displays a notification that TalkBack has crashed and data can be sent to
     * developers to help improve TalkBack.
     */
    private void displayCrashReportNotification(String crashReport) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, CRASH_REPORT_EMAILS);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.subject_crash_report_email));
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                getString(R.string.header_crash_report_email) + crashReport);
        Notification notification = new Notification(
                -1, getString(R.string.title_talkback_crash), System.currentTimeMillis());
        notification.setLatestEventInfo(this,
                getString(R.string.title_talkback_crash),
                getString(R.string.message_talkback_crash), PendingIntent.getActivity(
                        this, 0, emailIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        nm.notify(CRASH_NOTIFICTION_ID, notification);

    }

    /**
     * Notifies the {@link InfrastructureStateListener}s.
     */
    private void notifyInfrastructureSteteListeners() {
        ArrayList<InfrastructureStateListener> listeners = mInfrastructureStateListeners;
        for (int i = 0, count = listeners.size(); i < count; i++) {
            InfrastructureStateListener listener = listeners.get(i);
            listener.onInfrastructureStateChange(sInfrastructureInitialized);
        }
    }

    /**
     * Registers an uncaught exception handler for TalkBack. Causes TalkBack to
     * store crash data before the thread terminates.
     */
    private void registerUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new UncaughtExceptionHandler() {
            private UncaughtExceptionHandler sysUeh = Thread.getDefaultUncaughtExceptionHandler();
            
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                String timestamp = new Date().toString();
                String androidOsVersion = Build.VERSION.SDK_INT + " - " + Build.VERSION.INCREMENTAL;
                String deviceInfo = Build.MANUFACTURER + ", " + Build.MODEL + ", " + Build.PRODUCT;
                String talkBackVersion = "" + mCurVersion;
                StringBuilder stackTrace = new StringBuilder();
                stackTrace.append(ex.toString() + "\n");
                for (StackTraceElement element : ex.getStackTrace()) {
                    stackTrace.append(element.toString() + "\n");
                }
                String lastEvent = mLastSpokenEvent != null ? mLastSpokenEvent.toString() : "null";
                writeCrashReport(
                        String.format(getString(R.string.template_crash_report_message), timestamp,
                                androidOsVersion, deviceInfo, talkBackVersion, stackTrace,
                                lastEvent));
                // Show the standard "Force Close" alert dialog.
                if (sysUeh != null) {
                    sysUeh.uncaughtException(thread, ex);
                }
            }
            
            private void writeCrashReport(String report) {
                FileOutputStream fos;
                try {
                    fos = openFileOutput(TALKBACK_CRASH_LOG, Context.MODE_APPEND);
                    fos.write(report.getBytes());
                    fos.flush();
                    fos.close();
                } catch (FileNotFoundException e) {
                    // Ignored
                } catch (IOException e) {
                    // Ignored
                }
            }
        });
    }
    
    /**
     * Checks the TalkBack crash log and prompts the user to submit reports if appropriate. 
     */
    private void processCrashLog() {
        StringBuilder crashReport = new StringBuilder();
        String line = null;
        try {
            BufferedReader br = new BufferedReader(
                    new FileReader(getFilesDir() + "/" + TALKBACK_CRASH_LOG));
            while ((line = br.readLine()) != null) {
                crashReport.append(line + "\n");
            }
        } catch (FileNotFoundException e) {
            // Handles the case where no crash log exists.
            return;
        } catch (IOException e) {
            // Don't bother the user with this.
            return;
        }
        deleteFile(TALKBACK_CRASH_LOG);
        displayCrashReportNotification(crashReport.toString());
    }
    
    /**
     * Reload the preferences from the SharedPreferences object.
     */
    public void reloadPreferences() {
        mRingerPref = Integer.parseInt(mPrefs.getString(
                getString(R.string.pref_speak_ringer_key), "" + PREF_RINGER_DEFAULT));
        mScreenPref = Integer.parseInt(mPrefs.getString(
                getString(R.string.pref_speak_screenoff_key), "" + PREF_SCREEN_DEFAULT));
        mCallerIdPref = mPrefs.getBoolean(
                getString(R.string.pref_caller_id_key), PREF_CALLER_ID_DEFAULT);
        mTtsExtendedPref = mPrefs.getBoolean(
                getString(R.string.pref_tts_extended_key), PREF_TTS_EXTENDED_DEFAULT);
        mProximityPref = mPrefs.getBoolean(
                getString(R.string.pref_proximity_key), PREF_PROXIMITY_DEFAULT);

        // Switch TTS engines if necessary.
        if (sInfrastructureInitialized) {
            if ((mTtsExtendedPref && mTts != null) ||
                (!mTtsExtendedPref && mTtsExtended != null)) {
                mSpeechHandler.obtainMessage(WHAT_SWITCH_TTS).sendToTarget();
            }
        }

        // Power off the proximity sensor if necessary.
        if (mProximitySensor != null) {
            if (!mProximityPref && mProximitySensor.getState() != ProximitySensor.STATE_STOPPED) {
                mProximitySensor.shutdown();
                // After calling shutdown, the instance loses state and must be discarded.
                mProximitySensor = null;
            }
        }
        
        // Power on the proximity sensor if necessary.
        if (mProximityPref) {
            if (mProximitySensor == null
                    || mProximitySensor.getState() == ProximitySensor.STATE_STOPPED) {
                initializeProximitySensor();
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }
        
        if (!isServiceInitialized()) {
            Log.w(LOG_TAG, "No TTS instance found - dropping event.");
            return;
        }

        // Check ringer state
        if (mRingerPref == PREF_RINGER_NOT_SILENT_OR_VIBRATE
            && (mRingerMode == AudioManager.RINGER_MODE_VIBRATE
                || mRingerMode == AudioManager.RINGER_MODE_SILENT)) {
            return;
        } else if (mRingerPref == PREF_RINGER_NOT_SILENT &&
                   mRingerMode == AudioManager.RINGER_MODE_SILENT) {
            return;
        }

        // Check screen state; screen off can be silenced, but caller ID can override.
        boolean silence = false;
        if (mScreenPref == PREF_SCREEN_OFF_DISALLOWED && mScreenIsOff) {
            silence = true;
        }

        // If the state is ringing, then the Caller ID pref overrides what we do.
        if (mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_RINGING) {
            silence = (mCallerIdPref == false);
        }

        if (silence) {
            return;
        }

        // Keep a representation of the last spoken event for crash reporting.
        mLastSpokenEvent = clone(event);
        
        synchronized (mEventQueue) {
            enqueueEventLocked(event);
            if (isSourceInCallScreenActivity(event)) {
                sendSpeakMessageLocked(WHAT_SPEAK_WHILE_IN_CALL, EVENT_TIMEOUT_IN_CALL_SCREEN);
            } else {
                sendSpeakMessageLocked(WHAT_SPEAK, EVENT_TIMEOUT);
            }
        }

        return;
    }

    /**
     * Returns if the <code>event</code> source is the in-call screen activity.
     */
    private boolean isSourceInCallScreenActivity(AccessibilityEvent event) {
        return (AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED == event.getEventType() 
                && CLASS_NAME_IN_CALL_SCREEN.equals(event.getClassName()));
    }

    @Override
    public void onInterrupt() {
        mSpeechHandler.obtainMessage(WHAT_STOP_ALL_SPEAKING).sendToTarget();
    }

    /**
     * Enqueues the an <code>event</code>. The queuing operates as follows: </p>
     * 1. Events within the event timeout with type same as the last event
     * replace the latter if they are not notification with different icon.
     * </br> 2. All other events are enqueued.
     *
     * @param event The event to enqueue.
     */
    private void enqueueEventLocked(AccessibilityEvent event) {
        AccessibilityEvent current = clone(event);
        ArrayList<AccessibilityEvent> eventQueue = mEventQueue;

        int lastIndex = eventQueue.size() - 1;
        if (lastIndex > -1) {
            AccessibilityEvent last = eventQueue.get(lastIndex);
            if (isSameEventTypeAndSameNotificationIconAndTickerText(event, last)) {
                // in this special case we want to keep the first event
                // since the system is adding hyphens to the dialed number
                // which generates events we want to disregard
                if (isFromDialerInput(event)) {
                    return;
                }
                eventQueue.clear();
            }
        }

        eventQueue.add(current);
    }

    /**
     * Returns if the <code>currentEvent</code> has different type from the
     * <code>lastEvent</code> or if they are
     * {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED} if the
     * {@link Notification} instances they carry do not have the same icon and
     * ticker text.
     */
    private boolean isSameEventTypeAndSameNotificationIconAndTickerText(
            AccessibilityEvent currentEvent, AccessibilityEvent lastEvent) {
        if (currentEvent.getEventType() != lastEvent.getEventType()) {
            return false;
        }

        if (currentEvent.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            return true;
        }

        Notification currentNotification = (Notification) currentEvent.getParcelableData();
        Notification lastNotification = (Notification) lastEvent.getParcelableData();

        if (currentNotification == null) {
            if (lastNotification != null) {
                return false;
            }
            return true;
        } else if (lastNotification == null) {
            return false;
        }

        if (currentNotification.icon != lastNotification.icon) {
            return false;
        }

        if (currentNotification.tickerText == null) {
            if (lastNotification.tickerText != null) {
                return false;
            }
            return true; 
        } else {
            return currentNotification.tickerText.equals(lastNotification.tickerText);
        }
    }

    /**
     * Returns if a given <code>event</code> is fired by the dialer input which
     * contains the currently dialed number. </p> Note: The Android framework
     * adds hyphens between the dialed number digits which fires accessibility
     * events. Since TalkBackService processes only the last event of a given
     * type in a given time frame the original event is replaced by a more
     * recent hyphen adding event.
     *
     * @param event The event we are checking.
     * @return True if the event comes from the dialer input box, false
     *         ohterwise.
     */
    private boolean isFromDialerInput(AccessibilityEvent event) {
        return (PACKAGE_NAME_CONTACTS.equals(event.getPackageName()) && EditText.class
                .getCanonicalName().equals(event.getClassName()));
    }

    /**
     * Sends {@link #WHAT_SPEAK} to the speech handler. This method cancels the
     * old message (if such exists) since it is no longer relevant.
     *
     * @param action The action to perform with the message.
     * @param timeout The timeout after which to send the message.
     */
    public void sendSpeakMessageLocked(int action, long timeout) {
        Handler handler = mSpeechHandler;
        handler.removeMessages(action);
        Message message = handler.obtainMessage(action);
        handler.sendMessageDelayed(message, timeout);
    }

    /**
     * Processes an <code>event</code> by asking the {@link SpeechRuleProcessor}
     * to match it against its rules and in case an utterance is generated it is
     * spoken. This method is responsible for recycling of the processed event.
     *
     * @param event The event to process.
     * @param queueMode The queuing mode to use while processing events.
     * @param action The action to perform with the message.
     */
    private void processAndRecycleEvent(AccessibilityEvent event, int queueMode, int action) {
        String currentActivity = "";
        List<ActivityManager.RunningTaskInfo> tasks = mActivityManager.getRunningTasks(1);
        if (tasks.size() >= 1) {
            currentActivity = tasks.get(0).topActivity.getClassName();
        }

        Log.d(LOG_TAG, "Processing event: " + event + " activity=" + currentActivity);

        Utterance utterance = Utterance.obtain();
        // For now we do not pass any filter/formatter arguments. Their purpose is to
        // ensure future flexibility.
        if (mSpeechRuleProcessor.processEvent(event, currentActivity, utterance, null, null)) {
            HashMap<String, Object> metadata = utterance.getMetadata();
            // notifications are never interruptible
            boolean speakingNotification = mSpeakingNotification;

            // The event filter was matched but no text generated.
            // Do not process utterances that are empty since we either
            // drop the event on the floor or the source has no text
            // and contentDescription.
            if (utterance.getText().length() == 0) {
                utterance.recycle();
                return;
            }

            if (isEarcon(utterance)) {
                String earcon = utterance.getText().toString();
                // earcons always use QUEUING_MODE_QUEUE
                playEarcon(earcon, QUEUING_MODE_QUEUE, null);
                return;
            }

            // we let ourselves to override the queue mode for the next event
            if (mNextEventQueueMode != QUEUING_MODE_UNDEFINED) {
                queueMode = mNextEventQueueMode;
                mNextEventQueueMode = QUEUING_MODE_UNDEFINED;
            } else {
                if (!speakingNotification && metadata.containsKey(Utterance.KEY_METADATA_QUEUING)) {
                    // speech rules queue mode overrides the default TalkBack
                    // behavior
                    // the case is safe since SpeechRule did the preprocessing
                    queueMode = (Integer) metadata.get(Utterance.KEY_METADATA_QUEUING);
                } else {
                    // we never interrupt notification events
                    if (mSpeakingNotification) {
                        queueMode = QUEUING_MODE_QUEUE;
                    } else {
                        // if event type is the same as the last we interrupt
                        queueMode = (mLastEventType == event.getEventType() ? QUEUING_MODE_INTERRUPT
                                : QUEUING_MODE_QUEUE);
                    }
                }
            }

            mLastEventType = event.getEventType();
            cleanUpAndSpeak(utterance, queueMode, action,
                    event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);

            utterance.recycle();
            event.recycle();
        }
    }

    /**
     * Updates the {@link NotificationCache}. If a notification is present in
     * the cache it is removed, otherwise it is added.
     *
     * @param type The type of the notification.
     * @param text The notification text to be cached.
     */
    static void updateNotificationCache(NotificationType type, CharSequence text) {
        // if the cache has the notification - remove, otherwise add it
        if (!sNotificationCache.removeNotification(type, text.toString())) {
            sNotificationCache.addNotification(type, text.toString());
        }
    }

    /**
     * Clones an <code>event</code>.
     *
     * @param event The event to clone.
     */
    private AccessibilityEvent clone(AccessibilityEvent event) {
        AccessibilityEvent clone = AccessibilityEvent.obtain();

        clone.setAddedCount(event.getAddedCount());
        clone.setBeforeText(event.getBeforeText());
        clone.setChecked(event.isChecked());
        clone.setClassName(event.getClassName());
        clone.setContentDescription(event.getContentDescription());
        clone.setCurrentItemIndex(event.getCurrentItemIndex());
        clone.setEventTime(event.getEventTime());
        clone.setEventType(event.getEventType());
        clone.setEnabled(event.isEnabled());
        clone.setFromIndex(event.getFromIndex());
        clone.setFullScreen(event.isFullScreen());
        clone.setItemCount(event.getItemCount());
        clone.setPackageName(event.getPackageName());
        clone.setParcelableData(event.getParcelableData());
        clone.setPassword(event.isPassword());
        clone.setRemovedCount(event.getRemovedCount());
        clone.getText().clear();
        clone.getText().addAll(event.getText());

        return clone;
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The clean up is replacing
     * special strings with predefined mappings and reordering of some RegExp
     * matches to improve presentation. The <code>queueMode</code> determines if
     * speaking the event interrupts the speaking of previous events
     * {@link #QUEUING_MODE_INTERRUPT} or is queued {@link #QUEUING_MODE_QUEUE}.
     *
     * @param utterance The utterance to speak.
     * @param queueMode The queue mode to use for speaking.
     * @param action The action to perform with the message.
     * @param isNotification If the utterance announces a notification.
     */
    private void cleanUpAndSpeak(Utterance utterance, int queueMode, int action,
            boolean isNotification) {
        String text = cleanUpString(utterance.getText().toString());
        if (text.equals("")) {
            return;
        }
        HashMap<String, String> parameters = mSpeechParamtersMap;
        
        if (action == WHAT_SPEAK_WHILE_IN_CALL) {
            manageRingerVolume();
            parameters.clear();
            parameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, KEY_SPEAK_COMPLETE_CALLBACK);
        } else if (isNotification) {
            manageSpeakingNotification();
            parameters.clear();
            parameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, KEY_SPEAK_COMPLETE_CALLBACK);
        }

        if (mProximitySensor != null
                && mProximitySensor.getState() != ProximitySensor.STATE_STOPPED) {
            mSpeechHandler.removeCallbacks(mProximitySensorSilencer);
            mProximitySensor.resume();
            final TextToSpeech tts = mTts;
            parameters.clear();
            parameters.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, KEY_SPEAK_COMPLETE_CALLBACK);
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    mSpeechHandler.postDelayed(
                            mProximitySensorSilencer, PROXIMITY_SENSOR_CUTOFF_THRESHOLD);
                }
            });
        }

        // Workaround for the strange behavior of the Pico TTS engine
        // TODO(svetoslavganov): Remove as soon as the Pico issue is resolved
        if (queueMode == QUEUING_MODE_INTERRUPT) {
            // It seems that the stop() call is non-blocking and if we try
            // to speak immediately after that the stopping process is confused
            ttsStop();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ie) {
                /* ignore */
            }
        }

        ttsSpeak(text, QUEUING_MODE_QUEUE /* queueMode */, parameters);
    }

    /**
     * Decreases the ringer volume and registers a listener for the event of
     * completing to speak which restores the volume to its previous level.
     */
    private void manageRingerVolume() {
        final AudioManager audioManger = getAudioManager();

        final int currentRingerVolume = audioManger.getStreamVolume(AudioManager.STREAM_RING);
        final int maxRingerVolume = audioManger.getStreamMaxVolume(AudioManager.STREAM_RING);
        final int lowerEnoughVolume = Math.max((maxRingerVolume / 3), (currentRingerVolume / 2));

        audioManger.setStreamVolume(AudioManager.STREAM_RING, lowerEnoughVolume, 0);

        if (mTts != null) {
            final TextToSpeech tts = mTts;
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    tts.setOnUtteranceCompletedListener(null);
                    audioManger.setStreamVolume(AudioManager.STREAM_RING, currentRingerVolume, 0);
                }
            });
        } else if (mTtsExtended != null) {
            final TextToSpeechBeta tts = mTtsExtended;
            tts.setOnUtteranceCompletedListener(
                new TextToSpeechBeta.OnUtteranceCompletedListener() {
                    @Override
                    public void onUtteranceCompleted(String utteranceId) {
                        tts.setOnUtteranceCompletedListener(
                            (TextToSpeechBeta.OnUtteranceCompletedListener) null);
                        audioManger.setStreamVolume(AudioManager.STREAM_RING,
                                                    currentRingerVolume, 0);
                    }
                });
        }
    }

    /**
     * Rises a flag that a notification has been spoken and adds a listener to
     * clear the flag after speaking completes.
     */
    private void manageSpeakingNotification() {
        mSpeakingNotification = true;

        if (mTts != null) {
            final TextToSpeech tts = mTts;
            tts.setOnUtteranceCompletedListener(new TextToSpeech.OnUtteranceCompletedListener() {
                @Override
                public void onUtteranceCompleted(String utteranceId) {
                    tts.setOnUtteranceCompletedListener(null);
                    mSpeakingNotification = false;
                }
            });
        } else if (mTtsExtended != null) {
            final TextToSpeechBeta tts = mTtsExtended;
            tts.setOnUtteranceCompletedListener(
                new TextToSpeechBeta.OnUtteranceCompletedListener() {
                    @Override
                    public void onUtteranceCompleted(String utteranceId) {
                        tts.setOnUtteranceCompletedListener(
                            (TextToSpeechBeta.OnUtteranceCompletedListener) null);
                        mSpeakingNotification = false;
                    }
                });
        }
    }

    /**
     * Gets the {@link AudioManager} instance.
     *
     * @return The audio manager.
     */
    private AudioManager getAudioManager() {
        if (mAudioManager == null) {
            mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);
        }
        return mAudioManager;
    }

    /**
     * Cleans up <code>text</text> by separating camel case words with space
     * to compensate for the not robust pronounciation of the SVOX TTS engine
     * and replacing the text with predefined strings.
     *
     * @param text The text to clean up.
     * @return The cleaned text.
     */
    private String cleanUpString(String text) {
        String cleanedText = text;

        Matcher camelCasePrefix = sCamelCasePrefixPattern.matcher(cleanedText);
        cleanedText = camelCasePrefix.replaceAll("$1 $2");
        Matcher camelCaseSuffix = sCamelCaseSuffixPattern.matcher(cleanedText);
        cleanedText = camelCaseSuffix.replaceAll(" $1$2");
        cleanedText = cleanedText.replaceAll(" & ", " and ");

        return cleanedText;
    }

    /**
     * Determines if an <code>utterance</code> refers to an earcon. The
     * convention is that earcons are enclosed in square brackets.
     *
     * @param utterance The utterance.
     * @return True if the utterance is an earcon, false otherwise.
     */
    private boolean isEarcon(Utterance utterance) {
        StringBuilder text = utterance.getText();
        if (text.length() > 0) {
            return (text.charAt(0) == OPEN_SQUARE_BRACKET
                    && text.charAt(text.length() - 1) == CLOSE_SQUARE_BRACKET);
        } else {
            return false;
        }
    }

    Handler mSpeechHandler = new Handler() {

        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case WHAT_SPEAK:
                case WHAT_SPEAK_WHILE_IN_CALL:
                    ArrayList<AccessibilityEvent> eventQueue = mEventQueue;
                    while (true) {
                        AccessibilityEvent event = null;
                        synchronized (mEventQueue) {
                            if (eventQueue.isEmpty()) {
                                return;
                            }
                            event = eventQueue.remove(0);
                        }
                        processAndRecycleEvent(event, message.arg1, message.what);
                    }
                case WHAT_STOP_ALL_SPEAKING:
                    ttsSpeak("", QUEUING_MODE_INTERRUPT, null);
                    return;
                case WHAT_START_TTS:
                    startTts();
                    return;
                case WHAT_SHUTDOWN_TTS:
                    shutdownTts();
                    return;
                case WHAT_SWITCH_TTS:
                    if ((mTtsExtendedPref && mTts != null) ||
                        (!mTtsExtendedPref && mTtsExtended != null)) {
                        shutdownTts();
                        startTts();
                        return;
                    }
            }
        }

        private void startTts() {
            mTts = null;
            mTtsExtended = null;
            if (mTtsExtendedPref) {
                mTtsExtendedInitializing = new TextToSpeechBeta(
                    sInstance, new TextToSpeechBeta.OnInitListener() {
                        @Override
                        public void onInit(int status, int version) {
                            if (status != TextToSpeechBeta.SUCCESS) {
                                Log.e(LOG_TAG, "TTS extended init failed.");
                                return;
                            }

                                mTtsExtended = mTtsExtendedInitializing;
                                mTtsExtended.addEarcon(getString(R.string.earcon_progress),
                                        getPackageName(), R.raw.progress);
                                sTtsInitialized = true;
                            }
                        });
            } else {
                mTtsInitializing = new TextToSpeech(
                    sInstance, new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status != TextToSpeech.SUCCESS) {
                                Log.e(LOG_TAG, "TTS init failed.");
                                return;
                            }

                        mTts = mTtsInitializing;
                        mTts.addEarcon(getString(R.string.earcon_progress), getPackageName(),
                                R.raw.progress);
                        sTtsInitialized = true;
                    }
                });
            }
        }

        private void shutdownTts() {
            if (mTts != null) {
                mTts.shutdown();
                mTts = null;
            }
            if (mTtsExtended != null) {
                mTtsExtended.shutdown();
                mTtsExtended = null;
            }
            sTtsInitialized = false;
        }
    };

    /**
     * Abstraction that calls stop on either TTS or TTS extended.
     */
    private void ttsStop() {
        if (mTts != null) {
            mTts.stop();
        } else if (mTtsExtended != null) {
            mTtsExtended.stop();
        }
    }

    /**
     * Abstraction that calls speak on either TTS or TTS extended.
     */
    private void ttsSpeak(String text, int queueMode, HashMap<String, String> params) {
        if (mTts != null) {
            mTts.speak(text, queueMode, params);
        } else if (mTtsExtended != null) {
            mTtsExtended.speak(text, queueMode, params);
        }
    }

    /**
     * Abstraction that calls playEarcon on either TTS or TTS extended.
     */
    private void playEarcon(String earcon, int queueMode, HashMap<String, String> params) {
        if (mTts != null) {
            mTts.playEarcon(earcon, queueMode, params);
        } else if (mTtsExtended != null) {
            mTtsExtended.playEarcon(earcon, queueMode, params);
        }
    }

    /**
     * Appends the ringer state announcement to an {@link Utterance}.
     *
     * @param ringerMode the device ringer mode.
     * @param utterance The utterance to append to.
     */
    private void appendRingerStateAnouncement(int ringerMode, Utterance utterance) {
        switch (ringerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                String silentText = getString(R.string.value_ringer_silent);
                utterance.getText().append(silentText);
                return;
            case AudioManager.RINGER_MODE_VIBRATE:
                String vibrateText = getString(R.string.value_ringer_vibrate);
                utterance.getText().append(vibrateText);
                return;
            case AudioManager.RINGER_MODE_NORMAL:
                String template = getString(R.string.template_ringer_volume);

                // format the template with the ringer percentage
                int currentRingerVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                int maxRingerVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                int volumePercent = (100 / maxRingerVolume) * currentRingerVolume;

                // let us round to five so it sounds better
                int adjustment = volumePercent % 10;
                if (adjustment < 5) {
                    volumePercent -= adjustment;
                } else if (adjustment > 5) {
                    volumePercent += (10 - adjustment);
                }

                String populatedTemplate = String.format(template, volumePercent);
                utterance.getText().append(populatedTemplate);
                return;
            default:
                throw new IllegalArgumentException("Unknown ringer mode: " + ringerMode);
        }
    }

    /**
     * This receives commands send as {@link Intent} broadcasts. This is useful
     * in driving TalkBack from other applications that have the right
     * permissions.
     */
    public static class CommandInterfaceBroadcastReceiver extends BroadcastReceiver {

        /**
         * {@inheritDoc BroadcastReceiver#onReceive(Context, Intent)}
         *
         * @throws SecurityException if the user does not have
         *             com.google.android.marvin.talkback.
         *             SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK permission.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND.equals(intentAction)) {
                // TalkBack is not running so for now we fail silently
                if (sInstance == null) {
                    return;
                }

                Utterance utterance = Utterance.obtain();
                StringBuilder utteranceBuilder = utterance.getText();
                utteranceBuilder.append(context.getString(R.string.value_notification_summary));
                utteranceBuilder.append(SPACE);
                utteranceBuilder.append(sNotificationCache.getFormattedSummary());

                sInstance.cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT, WHAT_SPEAK, false);
            } else if (ACTION_QUERY_TALKBACK_ENABLED_COMMAND.equals(intentAction)) {
                // TODO(caseyburkhardt): Remove this block when we decide to no longer support
                // intent broadcasts for determining the state of TalkBack in favor of the content
                // provider method.
                if (sInfrastructureInitialized) {
                    setResultCode(RESULT_TALKBACK_ENABLED);
                } else {
                    setResultCode(RESULT_TALKBACK_DISABLED);
                }
            }

            abortBroadcast();
            // other intent commands go here ...
        }
    }

    /**
     * {@link BroadcastReceiver} for receiving updates for our context - device
     * state
     */
    class PhoneStateBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isServiceInitialized()) {
                Log.w(LOG_TAG, "Service not initialized during broadcast.");
                return;
            }

            String action = intent.getAction();

            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                // Hold a separate local to speak updated ringer state before
                // updating the instance, so we can actually announce silent
                // mode.
                int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,
                        AudioManager.RINGER_MODE_NORMAL);
                Utterance utterance = Utterance.obtain();
                appendRingerStateAnouncement(ringerMode, utterance);
                cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT, WHAT_SPEAK, false);
                mRingerMode = ringerMode;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenIsOff = false;
                if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_RINGING) {
                    Utterance utterance = Utterance.obtain();
                    String screenState = getString(R.string.value_screen_on);
                    utterance.getText().append(screenState);
                    appendRingerStateAnouncement(mRingerMode, utterance);
                    cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT, WHAT_SPEAK, false);
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenIsOff = true;
                Utterance utterance = Utterance.obtain();
                String screenState = getString(R.string.value_screen_off);
                utterance.getText().append(screenState);
                appendRingerStateAnouncement(mRingerMode, utterance);
                cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT, WHAT_SPEAK, false);
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // we want the phone unlock message to be uninterruptible
                mNextEventQueueMode = QUEUING_MODE_QUEUE;
                Utterance utterance = Utterance.obtain();
                String screenState = getString(R.string.value_phone_unlocked);
                utterance.getText().append(screenState);
                cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT, WHAT_SPEAK, false);
            } else {
                Log.w(LOG_TAG, "Registered for but not handling action " + action);
            }
        }
    }

    /**
     * This class is an event queue which keeps track of relevant events. Such
     * events do not have
     * {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED}. We treat such
     * events in a special manner.
     */
    class EventQueue extends ArrayList<AccessibilityEvent> {
        private int mRelevantEventCount;

        @Override
        public boolean add(AccessibilityEvent event) {
            if (!isNotificationEvent(event)) {
                mRelevantEventCount++;
            }
            boolean result = super.add(event);
            enforceRelevantEventSize();
            return result;
        }

        @Override
        public void add(int location, AccessibilityEvent object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends AccessibilityEvent> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int location, Collection<? extends AccessibilityEvent> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AccessibilityEvent remove(int location) {
            AccessibilityEvent event = get(location);
            if (event != null && !isNotificationEvent(event)) {
                mRelevantEventCount--;
            }
            return super.remove(location);
        }

        @Override
        public boolean remove(Object object) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            // never remove notification event - they are always spoken
            Iterator<AccessibilityEvent> iterator = iterator();
            while (iterator.hasNext()) {
                AccessibilityEvent next = iterator.next();
                if (!isNotificationEvent(next)) {
                    iterator.remove();
                    mRelevantEventCount--;
                }
            }
        }

        private int relevantEventCount() {
            return mRelevantEventCount;
        }

        /**
         * Enforces that the event queue is not more than
         * {@link #EVENT_QUEUE_MAX_SIZE}. The excessive events are pruned
         * through a FIFO strategy i.e. removing the oldest event first.
         */
        public void enforceRelevantEventSize() {
            for (int i = 0, count = size(); i < count; i++) {
                if (relevantEventCount() <= EVENT_QUEUE_MAX_SIZE) {
                    break;
                }
                AccessibilityEvent bottom = get(i);
                if (!isNotificationEvent(bottom)) {
                    remove(i);
                    i--;
                    count--;
                }
            }
        }

        /**
         * Returns if an event type is
         * AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED.
         */
        private boolean isNotificationEvent(AccessibilityEvent event) {
            return (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        }
    }

    /**
     * Interface for listeners for the TalkBack initialization state. 
     */
    interface InfrastructureStateListener {
        public void onInfrastructureStateChange(boolean isInitialized);
    }
}
