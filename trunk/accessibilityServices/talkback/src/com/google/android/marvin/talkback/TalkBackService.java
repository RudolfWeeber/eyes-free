/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.CursorController.CursorControllerListener;
import com.google.android.marvin.talkback.CursorGranularityManager.CursorGranularity;
import com.google.android.marvin.talkback.FullScreenReadController.AutomaticReadingState;
import com.google.android.marvin.talkback.RadialMenuManager.RadialMenuClient;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.compat.content.pm.PackageManagerCompatUtils;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.InfrastructureStateListener;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;
import com.googlecode.eyesfree.widget.RadialMenu;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * An {@link AccessibilityService} that provides spoken, haptic, and audible
 * feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackService extends AccessibilityService {
    /** Whether the current SDK supports optional touch exploration. */
    /* package */ static final boolean SUPPORTS_TOUCH_PREF = (Build.VERSION.SDK_INT >= 16);

    /** Whether the user has seen the TalkBack tutorial. */
    /* package */ static final String PREF_FIRST_TIME_USER = "first_time_user";

    /** Permission required to perform gestures. */
    /* package */ static final String PERMISSION_TALKBACK =
            "com.google.android.marvin.feedback.permission.TALKBACK";

    private static final int FEEDBACK_GESTURE_FAILED = R.raw.complete;

    /** Event types that should not interrupt continuous reading, if active. */
    private static final int MASK_EVENT_TYPES_DONT_INTERRUPT_CONTINUOUS =
            AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED |
            AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED |
            AccessibilityEvent.TYPE_VIEW_FOCUSED |
            AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY |
            AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END |
            AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END |
            AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END;

    /** Event types that are allowed to interrupt radial menus. */
    private static final int MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU =
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER |
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

    /** Action used to resume feedback. */
    private static final String ACTION_RESUME_FEEDBACK =
            "com.google.android.marvin.talkback.RESUME_FEEDBACK";

    /** Enumeration for ringer state speaking preferences. */
    private enum RingerPreference {
        /** Speak regardless of ringer state. */
        ALWAYS_SPEAK,
        /** Don't speak when the ringer is silent. */
        NOT_SILENT,
        /** Don't speak when the ringer is silent or on vibrate. */
        NOT_SILENT_OR_VIBRATE;

        public static RingerPreference valueOf(int ordinal) {
            return RingerPreference.values()[ordinal];
        }
    }

    /**
     * Listeners interested in the TalkBack initialization state.
     */
    private final ArrayList<InfrastructureStateListener> mInfrastructureStateListeners =
            new ArrayList<InfrastructureStateListener>();

    /**
     * Whether the infrastructure has been initialized. Used by the legacy
     * status provider so that applications can query TalkBack's status.
     */
    private static boolean sInfrastructureInitialized = false;

    /** Controller for cursor movement. */
    private CursorController mCursorController;

    /** Controller for speech feedback. */
    private SpeechController mSpeechController;

    /** Controller for audio and haptic feedback. */
    private PreferenceFeedbackController mFeedbackController;

    /** List of passive event processors. */
    private LinkedList<EventListener> mEventListeners;

    /** Focus-follow processor. Used in Jelly Bean and above. */
    private ProcessorFollowFocus mProcessorFollowFocus;

    /**
     * The audio manager used for changing the ringer volume for incoming calls.
     */
    private AudioManager mAudioManager;

    /**
     * {@link BroadcastReceiver} for tracking the ringer mode and screen state.
     */
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

    /** Orientation monitor for watching orientation changes. */
    private OrientationMonitor mOrientationMonitor;

    /** {@link BroadcastReceiver} for tracking the call state. */
    private CallStateMonitor mCallStateMonitor;

    /** {@link BroadcastReceiver} for tracking volume changes. */
    private VolumeMonitor mVolumeMonitor;

    /** {@link FullScreenReadController} for reading the entire hierarchy. */
    private FullScreenReadController mFullScreenReadController;

    /** {@link RadialMenuManager} for managing radial menus. */
    private RadialMenuManager mRadialMenuManager;

    /**
     * The last spoken accessibility event, used for detecting duplicate events.
     */
    private AccessibilityEvent mLastSpokenEvent;

    /** Text-to-speech overlay for debugging speech output. */
    private TextToSpeechOverlay mTtsOverlay;

    /** Whether speech should be silenced based on the ringer mode. */
    private RingerPreference mRingerPref;

    /** Global instance of the node speech rule processor. */
    private NodeSpeechRuleProcessor mNodeProcessor;

    /**
     * Whether speech should be allowed when the screen is off. When set to
     * {@code false}, the phone will remain silent when the screen is off.
     */
    private boolean mSpeakWhenScreenOff;

    /**
     * Whether TalkBack should speak caller ID information.
     */
    private boolean mSpeakCallerId;

    /**
     * Whether TalkBack has been temporarily disabled by the user.
     */
    private boolean mIsTalkBackSuspended;

    /**
     * Keep track of whether the user is currently touch exploring so that we
     * don't interrupt with notifications.
     */
    private boolean mIsUserTouchExploring;

    @Override
    public void onCreate() {
        // Initialize managers that are not dependent on infrastructure state.
        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mOrientationMonitor != null) {
            mOrientationMonitor.onConfigurationChanged(newConfig);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Shutdown and unregister all components.
        shutdownInfrastructure();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mIsTalkBackSuspended || shouldDropEvent(event)) {
            return;
        }

        maybeStopFullScreenReading(event);
        maintainExplorationState(event);
        cacheEvent(event);
        processEvent(event);
    }

    /**
     * Shows a dialog asking the user to confirm suspension of TalkBack.
     */
    private void confirmSuspendTalkBack() {
        final DialogInterface.OnClickListener okayClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        suspendTalkBack();
                        break;
                }
            }
        };

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_suspend_talkback)
                .setMessage(R.string.dialog_message_suspend_talkback)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okayClick)
                .create();

        // Ensure we can show the dialog from this service.
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        dialog.show();
    }

    /**
     * Suspends TalkBack and Explore by Touch.
     */
    private void suspendTalkBack() {
        if (mIsTalkBackSuspended) {
            LogUtils.log(this, Log.ERROR, "Attempted to suspend TalkBack while already suspended.");
            return;
        }

        mIsTalkBackSuspended = true;

        if (SUPPORTS_TOUCH_PREF) {
            requestTouchExploration(false);
        }

        if (mCursorController != null) {
            mCursorController.clearCursor();
        }

        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_RESUME_FEEDBACK);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(mSuspendedReceiver, filter, PERMISSION_TALKBACK, null);

        shutdownInfrastructure();

        final Intent resumeIntent = new Intent(ACTION_RESUME_FEEDBACK);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, resumeIntent, 0);
        final Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title_talkback_suspended))
                .setContentText(getString(R.string.notification_message_talkback_suspended))
                .setPriority(Notification.PRIORITY_MAX)
                .setSmallIcon(R.drawable.ic_stat_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setWhen(0)
                .build();

        startForeground(R.id.notification_suspended, notification);
    }

    /**
     * Resumes TalkBack and Explore by Touch.
     */
    private void resumeTalkBack() {
        if (!mIsTalkBackSuspended) {
            LogUtils.log(this, Log.ERROR, "Attempted to resume TalkBack when not suspended.");
            return;
        }

        unregisterReceiver(mSuspendedReceiver);

        initializeInfrastructure();
        stopForeground(true);
    }

    private void maybeStopFullScreenReading(AccessibilityEvent event) {
        if (mFullScreenReadController == null
                || mFullScreenReadController.getReadingState() == AutomaticReadingState.STOPPED) {
            return;
        }
        int type = event.getEventType();

        // Only interrupt full screen reading on events that can't be generated
        // by automated cursor movement or from delayed user interaction.
        if ((type & MASK_EVENT_TYPES_DONT_INTERRUPT_CONTINUOUS) == 0) {
            mFullScreenReadController.interrupt();
        }
    }

    @Override
    public boolean onGesture(int gestureId) {
        if (mCursorController == null) {
            // This will only happen when the service is shutting down.
            return true;
        }

        LogUtils.log(this, Log.VERBOSE, "Recognized gesture %s", gestureId);
        mFeedbackController.playSound(R.raw.gesture_end);

        // Gestures always stop global speech on API 16. On API 17+ we silence
        // on TOUCH_INTERACTION_START.
        // TODO: Will this negatively affect something like Books?
        if (Build.VERSION.SDK_INT <= 16) {
            interruptAllFeedback();
        }

        mRadialMenuManager.dismissAll();

        // Handle statically defined gestures.
        switch (gestureId) {
            case AccessibilityService.GESTURE_SWIPE_UP:
            case AccessibilityService.GESTURE_SWIPE_LEFT:
                if (!mCursorController.previous(true)) {
                    mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                }
                return true;
            case AccessibilityService.GESTURE_SWIPE_DOWN:
            case AccessibilityService.GESTURE_SWIPE_RIGHT:
                if (!mCursorController.next(true)) {
                    mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                }
                return true;
            case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
                mCursorController.previousGranularity();
                return true;
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
                mCursorController.nextGranularity();
                return true;
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
                if (!mCursorController.less()) {
                    mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                }
                return true;
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
                if (!mCursorController.more()) {
                    mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                }
                return true;
        }

        // Handle user-definable gestures.
        switch (gestureId) {
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
                performCustomGesture(R.string.pref_shortcut_down_and_left_key,
                        R.string.pref_shortcut_down_and_left_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
                performCustomGesture(R.string.pref_shortcut_down_and_right_key,
                        R.string.pref_shortcut_down_and_right_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
                performCustomGesture(R.string.pref_shortcut_up_and_left_key,
                        R.string.pref_shortcut_up_and_left_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
                performCustomGesture(R.string.pref_shortcut_up_and_right_key,
                        R.string.pref_shortcut_up_and_right_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
                performCustomGesture(R.string.pref_shortcut_right_and_down_key,
                        R.string.pref_shortcut_right_and_down_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
                performCustomGesture(R.string.pref_shortcut_right_and_up_key,
                        R.string.pref_shortcut_right_and_up_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
                performCustomGesture(R.string.pref_shortcut_left_and_down_key,
                        R.string.pref_shortcut_left_and_down_default);
                return true;
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
                performCustomGesture(R.string.pref_shortcut_left_and_up_key,
                        R.string.pref_shortcut_left_and_up_default);
                return true;
        }

        // Never let the system handle gestures.
        return true;
    }

    public NodeSpeechRuleProcessor getNodeProcessor() {
        if (mNodeProcessor == null) {
            throw new RuntimeException("mNodeProcessor has not been initialized");
        }

        return mNodeProcessor;
    }

    public PreferenceFeedbackController getFeedbackController() {
        if (mFeedbackController == null) {
            throw new RuntimeException("mFeedbackController has not been initialized");
        }

        return mFeedbackController;
    }

    public SpeechController getSpeechController() {
        if (mSpeechController == null) {
            throw new RuntimeException("mSpeechController has not been initialized");
        }

        return mSpeechController;
    }

    public CursorController getCursorController() {
        if (mCursorController == null) {
            throw new RuntimeException("mCursorController has not been initialized");
        }

        return mCursorController;
    }

    public FullScreenReadController getFullScreenReadController() {
        if (mFullScreenReadController == null) {
            throw new RuntimeException("mFullScreenReadController has not been initialized");
        }

        return mFullScreenReadController;
    }

    /* package */ static final String ACTION_PERFORM_GESTURE = "performCustomGesture";
    /* package */ static final String EXTRA_GESTURE_NAME = "gestureName";

    private static final String SHORTCUT_UNASSIGNED = "UNASSIGNED";
    private static final String SHORTCUT_BACK = "BACK";
    private static final String SHORTCUT_HOME = "HOME";
    private static final String SHORTCUT_RECENTS = "RECENTS";
    private static final String SHORTCUT_NOTIFICATIONS = "NOTIFICATIONS";
    private static final String SHORTCUT_READ_ALL_BREAKOUT = "READ_ALL_BREAKOUT";
    /* package */ static final String SHORTCUT_TALKBACK_BREAKOUT = "TALKBACK_BREAKOUT";

    private void performCustomGesture(int keyResId, int defaultResId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String key = getString(keyResId);
        final String defaultValue = getString(defaultResId);
        final String value = prefs.getString(key, defaultValue);

        performGesture(value);
    }

    private void performGesture(String value) {
        if (value.equals(SHORTCUT_UNASSIGNED)) {
            return;
        }

        if (value.equals(SHORTCUT_BACK)) {
            AccessibilityServiceCompatUtils.performGlobalAction(this, GLOBAL_ACTION_BACK);
        } else if (value.equals(SHORTCUT_HOME)) {
            AccessibilityServiceCompatUtils.performGlobalAction(this, GLOBAL_ACTION_HOME);
        } else if (value.equals(SHORTCUT_RECENTS)) {
            AccessibilityServiceCompatUtils.performGlobalAction(this, GLOBAL_ACTION_RECENTS);
        } else if (value.equals(SHORTCUT_NOTIFICATIONS)) {
            AccessibilityServiceCompatUtils.performGlobalAction(this, GLOBAL_ACTION_NOTIFICATIONS);
        } else if (value.equals(SHORTCUT_READ_ALL_BREAKOUT)) {
            mRadialMenuManager.showRadialMenu(R.menu.read_all);
        } else if (value.equals(SHORTCUT_TALKBACK_BREAKOUT)) {
            mRadialMenuManager.showRadialMenu(R.menu.breakout);
        }
    }

    @Override
    public void onInterrupt() {
        interruptAllFeedback();
    }

    public void interruptAllFeedback() {

        // Instruct ChromeVox to stop speech and halt any automatic actions.
        if (mCursorController != null) {
            final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
            if (currentNode != null && WebInterfaceUtils.hasWebContent(currentNode)) {
                if (WebInterfaceUtils.isScriptInjectionEnabled(this)) {
                    WebInterfaceUtils.performSpecialAction(
                            currentNode, WebInterfaceUtils.ACTION_STOP_SPEECH);
                }
            }
        }

        if (mFullScreenReadController != null) {
            mFullScreenReadController.interrupt();
        }

        if (mSpeechController != null) {
            mSpeechController.interrupt();
        }

        if (mFeedbackController != null) {
            mFeedbackController.interrupt();
        }
    }

    @Override
    protected void onServiceConnected() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                shutdownInfrastructure();
                initializeInfrastructure();

                final TalkBackUpdateHelper helper = new TalkBackUpdateHelper(TalkBackService.this);
                helper.showPendingNotifications();
                helper.checkUpdate();
            }
        });
    }

    /**
     * Returns true if TalkBack is running and initialized.
     */
    public static boolean isServiceInitialized() {
        return sInfrastructureInitialized;
    }

    /**
     * Initializes the infrastructure.
     */
    private void initializeInfrastructure() {
        if (sInfrastructureInitialized) {
            return;
        }

        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
        info.flags |= AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 0;
        setServiceInfo(info);

        mIsTalkBackSuspended = false;

        mSpeechController = new SpeechController(this);
        mFeedbackController = new PreferenceFeedbackController(this);
        mNodeProcessor = new NodeSpeechRuleProcessor(this);

        if (Build.VERSION.SDK_INT >= CursorController.MIN_API_LEVEL) {
            mCursorController = new CursorController(this);
            mCursorController.setListener(mCursorControllerListener);
        } else {
            mCursorController = null;
        }

        if (Build.VERSION.SDK_INT >= FullScreenReadController.MIN_API_LEVEL) {
            mFullScreenReadController = new FullScreenReadController(this);
        } else {
            mFullScreenReadController = null;
        }

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        // TODO: Figure out a clean way to avoid passing the feedback and speech
        // controllers everywhere.
        mEventListeners = new LinkedList<EventListener>();
        mEventListeners.add(new ProcessorEventQueue(this));
        mEventListeners.add(new ProcessorScrollPosition(this));

        if (Build.VERSION.SDK_INT >= ProcessorLongHover.MIN_API_LEVEL) {
            mEventListeners.add(new ProcessorLongHover(this));
        }

        if (Build.VERSION.SDK_INT >= ProcessorFollowFocus.MIN_API_LEVEL) {
            mProcessorFollowFocus = new ProcessorFollowFocus(this);
            mEventListeners.add(mProcessorFollowFocus);
        } else {
            mProcessorFollowFocus = null;
        }

        if (Build.VERSION.SDK_INT >= VolumeMonitor.MIN_API_LEVEL) {
            // TODO: Find a cleaner way to handle interaction between the volume
            // monitor (which needs to speak) and the speech controller (which
            // needs to adjust volume).
            mVolumeMonitor = new VolumeMonitor(this);
        } else {
            mVolumeMonitor = null;
        }

        if (Build.VERSION.SDK_INT >= ProcessorGestureVibrator.MIN_API_LEVEL) {
            mEventListeners.add(new ProcessorGestureVibrator(this));
        }

        if (Build.VERSION.SDK_INT >= ProcessorWebContent.MIN_API_LEVEL) {
            mEventListeners.add(new ProcessorWebContent(this));
        }

        final PackageManager packageManager = getPackageManager();

        final boolean deviceIsPhone = PackageManagerCompatUtils.hasSystemFeature(
                packageManager, PackageManagerCompatUtils.FEATURE_TELEPHONY, true);

        // Only initialize telephony and call state for phones.
        if (deviceIsPhone) {
            mCallStateMonitor = new CallStateMonitor(this);

            registerReceiver(mCallStateMonitor, mCallStateMonitor.getFilter());
            addInfrastructureStateListener(mCallStateMonitor);
        } else {
            mCallStateMonitor = null;
        }

        final boolean deviceHasTouchscreen = PackageManagerCompatUtils.hasSystemFeature(
                packageManager, PackageManagerCompatUtils.FEATURE_TOUCHSCREEN, true);

        if (deviceIsPhone || deviceHasTouchscreen) {
            // Although this receiver includes code responding to phone-specific
            // intents, it should also be registered for touch screen devices
            // without telephony.
            mRingerModeAndScreenMonitor = new RingerModeAndScreenMonitor(this);

            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
            addInfrastructureStateListener(mRingerModeAndScreenMonitor);
        } else {
            mRingerModeAndScreenMonitor = null;
        }

        mOrientationMonitor = new OrientationMonitor(this);

        mRadialMenuManager = new RadialMenuManager(this);
        mRadialMenuManager.setClient(mRadialMenuClient);
        registerReceiver(mRadialMenuManager, mRadialMenuManager.getFilter());

        // Register the preference change listener.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        reloadPreferences(prefs);

        // Register the class loading manager.
        addInfrastructureStateListener(ClassLoadingManager.getInstance());

        sInfrastructureInitialized = true;
        notifyInfrastructureStateListeners();

        // Make sure we start the tutorial if necessary.
        final ContentResolver resolver = getContentResolver();
        if (Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1) {
            onTouchExplorationEnabled();
        } else {
            final Uri touchExploreUri = Settings.Secure.getUriFor(
                    Settings.Secure.TOUCH_EXPLORATION_ENABLED);
            resolver.registerContentObserver(touchExploreUri, false, mTouchExploreObserver);
        }

        // Add the broadcast listener for gestures.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERFORM_GESTURE);
        registerReceiver(mActiveReceiver, filter, PERMISSION_TALKBACK, null);

        // Enable the proxy activity for long-press search.
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        if (!sInfrastructureInitialized) {
            return;
        }

        interruptAllFeedback();

        // Don't respond to any events or provide feedback when shut down.
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = 0;
        info.feedbackType = 0;
        setServiceInfo(info);

        unregisterReceiver(mActiveReceiver);

        // Disable the proxy activity for long-press search.
        final PackageManager packageManager = getPackageManager();
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // Unregister the touch exploration state observer.
        final ContentResolver resolver = getContentResolver();
        resolver.unregisterContentObserver(mTouchExploreObserver);

        // Unregister the preference change listener.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        // Remove any pending notifications that shouldn't persist.
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();

        if (mTtsOverlay != null) {
            mTtsOverlay.hide();
            mTtsOverlay = null;
        }

        if (mVolumeMonitor != null) {
            mVolumeMonitor.shutdown();
            mVolumeMonitor = null;
        }

        if (mCallStateMonitor != null) {
            unregisterReceiver(mCallStateMonitor);
            mCallStateMonitor = null;
        }

        if (mRingerModeAndScreenMonitor != null) {
            unregisterReceiver(mRingerModeAndScreenMonitor);
            mRingerModeAndScreenMonitor = null;
        }

        mOrientationMonitor.shutdown();
        mOrientationMonitor = null;

        // Unregister all infrastructure state listeners.
        sInfrastructureInitialized = false;
        notifyInfrastructureStateListeners();
        mInfrastructureStateListeners.clear();

        // Remove all event processors.
        mEventListeners.clear();
        mProcessorFollowFocus = null;

        mFeedbackController.shutdown();
        mFeedbackController = null;

        mSpeechController.shutdown();
        mSpeechController = null;

        if (mCursorController != null) {
            mCursorController.shutdown();
            mCursorController = null;
        }

        if (mFullScreenReadController != null) {
            mFullScreenReadController.shutdown();
            mFullScreenReadController = null;
        }

        mRadialMenuManager.clearCache();
        unregisterReceiver(mRadialMenuManager);
        mRadialMenuManager = null;
    }

    /**
     * Adds an {@link InfrastructureStateListener}.
     */
    private void addInfrastructureStateListener(InfrastructureStateListener listener) {
        mInfrastructureStateListeners.add(listener);
    }

    /**
     * Notifies the {@link InfrastructureStateListener}s.
     */
    private void notifyInfrastructureStateListeners() {
        for (InfrastructureStateListener listener : mInfrastructureStateListeners) {
            listener.onInfrastructureStateChange(this, sInfrastructureInitialized);
        }
    }

    /**
     * Returns whether the device should drop this event. Caches notifications
     * if necessary.
     *
     * @param event The current event.
     * @return {@code true} if the event should be dropped.
     */
    private boolean shouldDropEvent(AccessibilityEvent event) {
        // Always drop null events.
        if (event == null) {
            return true;
        }

        // Always drop events if the service isn't ready.
        if (!sInfrastructureInitialized) {
            return true;
        }

        // Always drop duplicate events.
        if (AccessibilityEventUtils.eventEquals(mLastSpokenEvent, event)) {
            return true;
        }

        // Always drop window content changed events since they are only used
        // by the framework for cache management.
        if (event.getEventType() == AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED) {
            return true;
        }

        final RingerPreference ringerPref = mRingerPref;
        final int ringerMode = mAudioManager.getRingerMode();

        // Don't speak when the ringer is silent or vibrate.
        if (ringerPref == RingerPreference.NOT_SILENT_OR_VIBRATE && (
                ringerMode == AudioManager.RINGER_MODE_VIBRATE
                || ringerMode == AudioManager.RINGER_MODE_SILENT)) {
            return true;
        }

        // Don't speak when the ringer is silent.
        if (ringerPref == RingerPreference.NOT_SILENT
                && ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return true;
        }

        final boolean isNotification =
                (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        final boolean isPhoneActive = (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE);
        final boolean canInterruptRadialMenu =
                ((event.getEventType() & MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU) != 0);
        final boolean silencedByScreen = ((mRingerModeAndScreenMonitor != null)
                && (!mSpeakWhenScreenOff && mRingerModeAndScreenMonitor.isScreenOff()));
        final boolean shouldOverrideScreenSilence = (mSpeakCallerId && (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState()
                        == TelephonyManager.CALL_STATE_RINGING));
        final boolean silencedByRadialMenu = (mRadialMenuManager.isRadialMenuShowing()
                && !canInterruptRadialMenu);

        // Don't speak certain events when the screen is off.
        if (silencedByScreen) {
            // Unless it's an event that should specifically be spoken and
            // override this condition.
            if (shouldOverrideScreenSilence) {
                return false;
            }
            return true;
        }

        // Don't speak events that cannot interrupt the radial menu, if showing
        if (silencedByRadialMenu) {
            return true;
        }

        // Don't speak notification events if the user is touch exploring or a phone call is active.
        if (isNotification && (mIsUserTouchExploring || isPhoneActive)) {
            return true;
        }

        return false;
    }

    /**
     * Manages touch exploration state.
     *
     * @param event The current event.
     */
    private void maintainExplorationState(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            mIsUserTouchExploring = true;
        } else if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            mIsUserTouchExploring = false;
        }
    }

    /**
     * Caches the last spoken event. Used for bug reports and duplicate
     * detection.
     *
     * @param event The current event.
     */
    private void cacheEvent(AccessibilityEvent event) {
        if (mLastSpokenEvent != null) {
            mLastSpokenEvent.recycle();
        }

        mLastSpokenEvent = AccessibilityEventCompatUtils.obtain(event);
    }

    /**
     * Passes the event to all registered {@link EventListener}s in the order
     * they were added.
     *
     * @param event The current event.
     */
    private void processEvent(AccessibilityEvent event) {
        for (EventListener eventProcessor : mEventListeners) {
            eventProcessor.process(event);
        }
    }

    /**
     * Adds an event listener.
     *
     * @param listener The listener to add.
     */
    public void addEventListener(EventListener listener) {
        mEventListeners.add(listener);
    }

    /**
     * Posts a {@link Runnable} to removes an event listener. This is safe to
     * call from inside {@link EventListener#process(AccessibilityEvent)}.
     *
     * @param listener The listener to remove.
     */
    /* protected */void postRemoveEventListener(final EventListener listener) {
        mHandler.post(new Runnable() {
                @Override
            public void run() {
                mEventListeners.remove(listener);
            }
        });
    }

    /**
     * Reloads service preferences.
     *
     * @param prefs The service's shared preferences.
     */
    private void reloadPreferences(SharedPreferences prefs) {
        final Resources res = getResources();

        final int ringerPref = SharedPreferencesUtils.getIntFromStringPref(
                prefs, res, R.string.pref_speak_ringer_key, R.string.pref_speak_ringer_default);
        mRingerPref = RingerPreference.valueOf(ringerPref);

        mSpeakWhenScreenOff = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_screenoff_key, R.bool.pref_screenoff_default);

        mSpeakCallerId = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_caller_id_key, R.bool.pref_caller_id_default);

        final boolean silenceOnProximity = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_proximity_key, R.bool.pref_proximity_default);

        mSpeechController.setSilenceOnProximity(silenceOnProximity);

        final int logLevel =
                SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                        R.string.pref_log_level_key, R.string.pref_log_level_default);

        LogUtils.setLogLevel(logLevel);

        if (SUPPORTS_TOUCH_PREF) {
            final boolean touchExploration = SharedPreferencesUtils.getBooleanPref(prefs, res,
                    R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
            requestTouchExploration(touchExploration);
        }
    }

    /**
     * Attempts to change the state of touch exploration.
     *
     * @param requestedState {@code true} to request exploration.
     */
    private void requestTouchExploration(boolean requestedState) {
        final AccessibilityServiceInfo info = AccessibilityServiceCompatUtils.getServiceInfo(this);

        // If we're in the process of shutting down, the info will be null and
        // we should abort. The state will be set correctly on the next launch.
        if (info == null) {
            LogUtils.log(this, Log.ERROR,
                    "Failed to change touch exploration request state, service info was null");
            return;
        }

        final boolean currentState = (
                (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0);

        if (currentState == requestedState) {
            return;
        }

        if (requestedState) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }

        setServiceInfo(info);
    }

    /**
     * Launches the touch exploration tutorial if necessary.
     */
    private void onTouchExplorationEnabled() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                TalkBackService.this);

        if (!prefs.getBoolean(PREF_FIRST_TIME_USER, true)) {
            return;
        }

        final Editor editor = prefs.edit();
        editor.putBoolean(PREF_FIRST_TIME_USER, false);
        editor.commit();

        if (Build.VERSION.SDK_INT >= AccessibilityTutorialActivity.MIN_API_LEVEL) {
            final Intent tutorial = new Intent(this, AccessibilityTutorialActivity.class);
            tutorial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(tutorial);
        }
    }

    /**
     * Handler used for receiving URI change updates.
     */
    private final Handler mHandler = new Handler();

    /**
     * Handles granularity change events by speaking the current mode.
     */
    private final CursorControllerListener
            mCursorControllerListener = new CursorControllerListener() {
        @Override
        public void onGranularityChanged(CursorGranularity granularity, boolean fromUser) {
            // Only announce the granularity change if it was requested
            // by the user.
            if (fromUser) {
                final int resId = granularity.resId;
                final String name = getString(resId);

                mSpeechController.cleanUpAndSpeak(
                        name, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
            }
        }

        @Override
        public void onActionPerformed(int action) {
            // The follow focus processor needs to know if we perform a
            // scroll action.
            if (mProcessorFollowFocus != null) {
                mProcessorFollowFocus.onActionPerformed(action);
            }
        }
    };

    private MenuInflater mMenuInflater;

    private MenuInflater getMenuInflater() {
        if (mMenuInflater == null) {
            mMenuInflater = new MenuInflater(this);
        }

        return mMenuInflater;
    }

    /**
     * Reloads preferences whenever their values change.
     */
    private final OnSharedPreferenceChangeListener
            mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            reloadPreferences(prefs);
        }
    };

    /**
     * Shows the accessibility tutorial after touch exploration is turned on for
     * the first time.
     */
    private final ContentObserver mTouchExploreObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            final boolean isEnabled = (Settings.Secure.getInt(
                    getContentResolver(), Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1);

            if (!isEnabled) {
                return;
            }

            // We don't need to listen any more!
            getContentResolver().unregisterContentObserver(mTouchExploreObserver);

            onTouchExplorationEnabled();
        }
    };

    /**
     * Broadcast receiver for actions that happen while the service is active.
     */
    private final BroadcastReceiver mActiveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ACTION_PERFORM_GESTURE.equals(action)) {
                final String gestureName = intent.getStringExtra(EXTRA_GESTURE_NAME);
                if (gestureName != null) {
                    performGesture(gestureName);
                }
            }
        }
    };

    /**
     * Broadcast receiver for actions that happen while the service is inactive.
     */
    private final BroadcastReceiver mSuspendedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ACTION_RESUME_FEEDBACK.equals(action)) {
                resumeTalkBack();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                final KeyguardManager keyguard = (KeyguardManager) getSystemService(
                        Context.KEYGUARD_SERVICE);
                if (keyguard.inKeyguardRestrictedInputMode()) {
                    resumeTalkBack();
                }
            }
        }
    };

    private final RadialMenuClient mRadialMenuClient = new RadialMenuClient() {
        @Override
        public void onCreateRadialMenu(int menuId, RadialMenu menu) {
            final int menuRes;

            switch (menuId) {
                case R.menu.read_all:
                    menuRes = R.menu.read_all;
                    break;
                case R.menu.breakout:
                    menuRes = R.menu.breakout;
                    break;
                default:
                    menuRes = 0;
            }

            if (menuRes > 0) {
                getMenuInflater().inflate(menuRes, menu);
            }
        }

        @Override
        public void onPrepareRadialMenu(int menuId, RadialMenu menu) {
            // Do nothing.
        }

        /**
         * Handles clicking on a radial menu item.
         *
         * @param menuItem The radial menu item that was clicked.
         */
        @Override
        public boolean onMenuItemClicked(MenuItem menuItem) {
            if (menuItem == null) {
                // Let the manager handle cancellations.
                return false;
            }

            switch (menuItem.getItemId()) {
                case R.id.read_from_top:
                    if (mFullScreenReadController.getReadingState()
                            == AutomaticReadingState.STOPPED) {
                        if (!mFullScreenReadController.startReadingFromBeginning()) {
                            mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                        }
                    }
                    return true;
                case R.id.read_from_current:
                    if (mFullScreenReadController.getReadingState()
                            == AutomaticReadingState.STOPPED) {
                        if (!mFullScreenReadController.startReadingFromNextNode()) {
                            mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                        }
                    }
                    return true;
                case R.id.repeat_current:
                    if (mFullScreenReadController.getReadingState()
                            == AutomaticReadingState.STOPPED) {
                        if (!mFullScreenReadController.speakCurrentNode()) {
                            mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                        }
                    }
                    return true;
                case R.id.repeat_last_utterance:
                    mSpeechController.repeatLastUtterance();
                    return true;
                case R.id.spell_last_utterance:
                    mSpeechController.spellLastUtterance();
                    return true;
                case R.id.pause_feedback:
                    confirmSuspendTalkBack();
                    return true;
                case R.id.talkback_settings:
                    final Intent settingsIntent = new Intent(
                            TalkBackService.this, TalkBackPreferencesActivity.class);
                    settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(settingsIntent);
                    return true;
            }

            return false;
        }

        @Override
        public boolean onMenuItemHovered(MenuItem menuItem) {
            // Let the manager handle spoken feedback from hovering.
            return false;
        }
    };

    /**
     * Interface for passive event processors.
     */
    public interface EventListener {
        public void process(AccessibilityEvent event);
    }
}
