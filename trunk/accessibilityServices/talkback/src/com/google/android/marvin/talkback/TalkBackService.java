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
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.marvin.talkback.CursorController.CursorControllerListener;
import com.google.android.marvin.talkback.KeyComboManager.KeyComboListener;
import com.google.android.marvin.talkback.TextToSpeechManager.TtsDiscoveryListener;
import com.google.android.marvin.talkback.speechrules.NodeHintRule;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.talkback.test.TalkBackListener;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityServiceInfoCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;
import com.googlecode.eyesfree.utils.TtsEngineUtils.TtsEngineInfo;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.LinkedList;
import java.util.List;

/**
 * An {@link AccessibilityService} that provides spoken, haptic, and audible
 * feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackService extends AccessibilityService
        implements Thread.UncaughtExceptionHandler {
    /** Whether the current SDK supports optional touch exploration. */
    /* package */ static final boolean SUPPORTS_TOUCH_PREF = (Build.VERSION.SDK_INT >= 16);

    /** Whether the user has seen the TalkBack tutorial. */
    /* package */ static final String PREF_FIRST_TIME_USER = "first_time_user";

    /** Permission required to perform gestures. */
    /* package */ static final String PERMISSION_TALKBACK =
            "com.google.android.marvin.feedback.permission.TALKBACK";

    /** The minimum delay between window state change and automatic events. */
    /* package */ static final long DELAY_AUTO_AFTER_STATE = 100;

    /** Event types to drop after receiving a window state change. */
    /* package */ static final int AUTOMATIC_AFTER_STATE_CHANGE =
            AccessibilityEvent.TYPE_VIEW_FOCUSED
            | AccessibilityEvent.TYPE_VIEW_SELECTED
            | AccessibilityEventCompat.TYPE_VIEW_SCROLLED;

    /** The intent action used to perform a custom gesture. */
    /* package */ static final String ACTION_PERFORM_GESTURE = "performCustomGesture";

    /**
     * The gesture name to pass with {@link #ACTION_PERFORM_GESTURE} as a string
     * extra. Must match the name of a {@link ShortcutGesture}.
     */
    /* package */ static final String EXTRA_GESTURE_NAME = "gestureName";

    /** Whether to force debugging mode on. Turn off when releasing. */
    private static final boolean DEBUG = false;

    /** Event types that are allowed to interrupt radial menus. */
    // TODO: What's the rationale for HOVER_ENTER? Navigation bar?
    private static final int MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU =
            AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
            | AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            | AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;

    /**
     * Event types that signal a change in touch interaction state and should be
     * dropped on {@link Configuration#TOUCHSCREEN_NOTOUCH} devices
     */
    private static final int MASK_EVENT_TYPES_TOUCH_STATE_CHANGES =
            AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START
            | AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END
            | AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START
            | AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END
            | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START
            | AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_END
            | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER
            | AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT;

    /** Action used to resume feedback. */
    private static final String ACTION_RESUME_FEEDBACK =
            "com.google.android.marvin.talkback.RESUME_FEEDBACK";

    /** An active instance of TalkBack. */
    private static TalkBackService sInstance = null;

    /** The possible states of the service. */
    public enum ServiceState {
        /**
         * The state of the service before the system has bound to it or after
         * it is destroyed.
         */
        INACTIVE,

        /** The state of the service when it initialized and active. */
        ACTIVE,

        /** The state of the service when it has been suspended by the user. */
        SUSPENDED
    }

    /**
     * List of passive event processors. All processors in the list are sent the
     * event in the order they were added.
     */
    private final LinkedList<AccessibilityEventListener>
            mAccessibilityEventListeners = new LinkedList<AccessibilityEventListener>();

    /**
     * List of key event processors. Processors in the list are sent the event
     * in the order they were added until a processor consumes the event.
     */
    private final LinkedList<KeyEventListener>
            mKeyEventListeners = new LinkedList<KeyEventListener>();

    /** The current state of the service. */
    private ServiceState mServiceState;

    /** Components to receive callbacks on changes in the service's state. */
    private List<ServiceStateListener>
            mServiceStateListeners = new LinkedList<ServiceStateListener>();

    /** TalkBack-specific listener used for testing. */
    private TalkBackListener mTestingListener;

    /** Controller for cursor movement. */
    private CursorController mCursorController;

    /** Controller for speech feedback. */
    private SpeechController mSpeechController;

    /** Controller for audio and haptic feedback. */
    private MappedFeedbackController mFeedbackController;

    /** Controller for reading the entire hierarchy. */
    private FullScreenReadController mFullScreenReadController;

    /** Listener for device shake events. */
    private ShakeDetector mShakeDetector;

    /** Manager for tracking available TTS engines and languages. */
    private TextToSpeechManager mTextToSpeechManager;

    /** Manager for showing radial menus. */
    private RadialMenuManager mRadialMenuManager;

    /** Processor for moving access focus. Used in Jelly Bean and above. */
    private ProcessorFocusAndSingleTap mProcessorFollowFocus;

    /** Processor for generating and providing feedback for events. */
    private ProcessorEventQueue mProcessorEventQueue;

    /** Orientation monitor for watching orientation changes. */
    private OrientationMonitor mOrientationMonitor;

    /** {@link BroadcastReceiver} for tracking the ringer and screen states. */
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

    /** {@link BroadcastReceiver} for tracking the call state. */
    private CallStateMonitor mCallStateMonitor;

    /** {@link BroadcastReceiver} for tracking volume changes. */
    private VolumeMonitor mVolumeMonitor;

    /** Power manager, used for checking screen state. */
    private PowerManager mPowerManager;

    /** The accessibility manager, used for querying state. */
    private AccessibilityManager mAccessibilityManager;

    /** The last spoken accessibility event, used to detect duplicate events. */
    private AccessibilityEvent mLastSpokenEvent;

    /** Event time for the most recent window state changed event. */
    private long mLastWindowStateChanged = 0;

    /** Text-to-speech overlay for debugging speech output. */
    private TextToSpeechOverlay mTtsOverlay;

    /** Alert dialog shown when the user attempts to suspend feedback. */
    private AlertDialog mSuspendDialog;

    /** Shared preferences used within TalkBack. */
    private SharedPreferences mPrefs;

    /** The system's uncaught exception handler */
    private UncaughtExceptionHandler mSystemUeh;

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
     * Whether TalkBack should interpret down-then-up and up-then-down gestures
     * as granularity cycles or moving of accessibility focus to the first or
     * last item on screen.
     * <p>
     * {@code true} to use granularity cycle, or {@code false} for moving focus.
     */
    private boolean mVerticalGestureCycleGranularity;

    /**
     * Keep track of whether the user is currently touch exploring so that we
     * don't interrupt with notifications.
     */
    private boolean mIsUserTouchExploring;

    /** Preference specifying when TalkBack should automatically resume. */
    private AutomaticResumePreference mAutomaticResume;

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;
        setServiceState(ServiceState.INACTIVE);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mSystemUeh = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        initializeInfrastructure();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isServiceActive()) {
            suspendInfrastructure();
        }

        sInstance = null;

        // Shutdown and unregister all components.
        shutdownInfrastructure();
        setServiceState(ServiceState.INACTIVE);
        mServiceStateListeners.clear();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isServiceActive() && (mOrientationMonitor != null)) {
            mOrientationMonitor.onConfigurationChanged(newConfig);
        }

        // Clear the radial menu cache to reload localized strings.
        mRadialMenuManager.clearCache();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mTestingListener != null) {
            mTestingListener.onAccessibilityEvent(event);
        }

        if (shouldDropEvent(event)) {
            return;
        }

        maintainExplorationState(event);

        // TODO(alanv): Figure out if this is actually needed for API < 14.
        if (Build.VERSION.SDK_INT < 14) {
            cacheEvent(event);
        }

        processEvent(event);
    }

    private void setServiceState(ServiceState newState) {
        if (mServiceState == newState) {
            return;
        }

        mServiceState = newState;
        for (ServiceStateListener listener : mServiceStateListeners) {
            listener.onServiceStateChanged(newState);
        }
    }

    public void addServiceStateListener(ServiceStateListener listener) {
        if (listener != null) {
            mServiceStateListeners.add(listener);
        }
    }

    public void removeServiceStateListener(ServiceStateListener listener) {
        if (listener != null) {
            mServiceStateListeners.remove(listener);
        }
    }

    public void setTestingListener(TalkBackListener testingListener) {
        mTestingListener = testingListener;

        if (mProcessorEventQueue != null) {
            mProcessorEventQueue.setTestingListener(testingListener);
        }
    }

    /**
     * Suspends TalkBack, showing a confirmation dialog if applicable.
     */
    public void requestSuspendTalkBack() {
        final boolean showConfirmation = SharedPreferencesUtils.getBooleanPref(mPrefs,
                getResources(), R.string.pref_show_suspension_confirmation_dialog,
                R.bool.pref_show_suspension_confirmation_dialog_default);
        if (showConfirmation) {
            confirmSuspendTalkBack();
        } else {
            suspendTalkBack();
        }
    }

    /**
     * Shows a dialog asking the user to confirm suspension of TalkBack.
     */
    private void confirmSuspendTalkBack() {
        // Ensure only one dialog is showing.
        if (mSuspendDialog != null) {
            if (mSuspendDialog.isShowing()) {
                return;
            } else {
                mSuspendDialog.dismiss();
                mSuspendDialog = null;
            }
        }

        final LayoutInflater inflater = LayoutInflater.from(this);
        final ScrollView root = (ScrollView) inflater.inflate(
                R.layout.suspend_talkback_dialog, null);
        final CheckBox confirmCheckBox = (CheckBox) root.findViewById(R.id.show_warning_checkbox);
        final TextView message = (TextView) root.findViewById(R.id.message_resume);

        final DialogInterface.OnClickListener okayClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        if (!confirmCheckBox.isChecked()) {
                            SharedPreferencesUtils.putBooleanPref(mPrefs, getResources(),
                                    R.string.pref_show_suspension_confirmation_dialog, false);
                        }

                        suspendTalkBack();
                        break;
                }
            }
        };

        final OnDismissListener onDismissListener = new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mSuspendDialog = null;
            }
        };

        switch (mAutomaticResume) {
            case KEYGUARD:
                message.setText(getString(R.string.message_resume_keyguard));
                break;
            case SCREEN_ON:
                message.setText(getString(R.string.message_resume_screen_on));
                break;
            case MANUAL:
                message.setText(getString(R.string.message_resume_manual));
                break;
        }

        mSuspendDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_suspend_talkback)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okayClick)
                .create();

        // Ensure we can show the dialog from this service.
        mSuspendDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        mSuspendDialog.setOnDismissListener(onDismissListener);
        mSuspendDialog.show();
    }

    /**
     * Suspends TalkBack and Explore by Touch.
     */
    private void suspendTalkBack() {
        if (!isServiceActive()) {
            LogUtils.log(this, Log.ERROR, "Attempted to suspend TalkBack while already suspended.");
            return;
        }

        mFeedbackController.playAuditory(R.id.sounds_paused_feedback);

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

        // Suspending infrastructure sets sIsTalkBackSuspended to true.
        suspendInfrastructure();

        final Intent resumeIntent = new Intent(ACTION_RESUME_FEEDBACK);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, resumeIntent, 0);
        final Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title_talkback_suspended))
                .setContentText(getString(R.string.notification_message_talkback_suspended))
                .setPriority(NotificationCompat.PRIORITY_MAX)
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
        if (isServiceActive()) {
            LogUtils.log(this, Log.ERROR, "Attempted to resume TalkBack when not suspended.");
            return;
        }

        unregisterReceiver(mSuspendedReceiver);
        resumeInfrastructure();
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        // Don't intercept keys if TalkBack is suspended.
        if (!isServiceActive()) {
            return false;
        }

        for (KeyEventListener listener : mKeyEventListeners) {
            if (listener.onKeyEvent(event)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean onGesture(int gestureId) {
        if (!isServiceActive()) {
            // Allow other services with touch exploration to handle gestures
            return false;
        }

        LogUtils.log(this, Log.VERBOSE, "Recognized gesture %s", gestureId);

        mFeedbackController.playAuditory(R.id.sounds_gesture_end);

        // Gestures always stop global speech on API 16. On API 17+ we silence
        // on TOUCH_INTERACTION_START.
        // TODO: Will this negatively affect something like Books?
        if (Build.VERSION.SDK_INT <= 16) {
            interruptAllFeedback();
        }

        mRadialMenuManager.dismissAll();

        boolean handled = true;
        boolean result = false;

        // Handle statically defined gestures.
        switch (gestureId) {
            case AccessibilityService.GESTURE_SWIPE_UP:
            case AccessibilityService.GESTURE_SWIPE_LEFT:
                result = mCursorController.previous(true /* shouldWrap */, true /* shouldScroll */);
                break;
            case AccessibilityService.GESTURE_SWIPE_DOWN:
            case AccessibilityService.GESTURE_SWIPE_RIGHT:
                result = mCursorController.next(true /* shouldWrap */, true /* shouldScroll */);
                break;
            case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
                // TODO(caseyburkhardt): Consider using existing custom gesture mechanism
                if (mVerticalGestureCycleGranularity) {
                    result = mCursorController.previousGranularity();
                } else {
                    result = mCursorController.jumpToTop();
                }
                break;
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
                if (mVerticalGestureCycleGranularity) {
                    result = mCursorController.nextGranularity();
                } else {
                    result = mCursorController.jumpToBottom();
                }
                break;
            case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
                result = mCursorController.less();
                break;
            case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
                result = mCursorController.more();
                break;
            default:
                handled = false;
        }

        if (handled) {
            if (!result) {
                mFeedbackController.playAuditory(R.id.sounds_complete);
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

    /**
     * Obtains the shared instance of TalkBack's {@link ShakeDetector}
     *
     * @return the shared {@link ShakeDetector} instance, or null if not initialized.
     */
    public ShakeDetector getShakeDetector() {
        return mShakeDetector;
    }

    /**
     * Performs a gesture associated with a preference key.
     *
     * @param keyResId The resource identifier for the preference key string.
     * @param defaultResId The resource identifier for the gesture to perform if
     *            the preference has not been set.
     */
    private boolean performCustomGesture(int keyResId, int defaultResId) {
        final String key = getString(keyResId);
        final String defaultValue = getString(defaultResId);
        final String value = mPrefs.getString(key, defaultValue);
        final ShortcutGesture gesture = ShortcutGesture.safeValueOf(value);

        return performGesture(gesture);
    }

    /**
     * Performs the action associated with a {@link ShortcutGesture}.
     *
     * @param value The gesture to perform.
     */
    private boolean performGesture(ShortcutGesture value) {
        switch (value) {
            case BACK:
                return AccessibilityServiceCompatUtils.performGlobalAction(
                        this, GLOBAL_ACTION_BACK);
            case HOME:
                return AccessibilityServiceCompatUtils.performGlobalAction(
                        this, GLOBAL_ACTION_HOME);
            case RECENTS:
                return AccessibilityServiceCompatUtils.performGlobalAction(
                        this, GLOBAL_ACTION_RECENTS);
            case NOTIFICATIONS:
                return AccessibilityServiceCompatUtils.performGlobalAction(
                        this, GLOBAL_ACTION_NOTIFICATIONS);
            case TALKBACK_BREAKOUT:
                return mRadialMenuManager.showRadialMenu(R.menu.global_context_menu);
            case LOCAL_BREAKOUT:
                return mRadialMenuManager.showRadialMenu(R.menu.local_context_menu);
            case READ_FROM_TOP:
                return mFullScreenReadController.startReading(true);
            case READ_FROM_CURRENT:
                return mFullScreenReadController.startReading(false);
            default:
                return false;
        }
    }

    @Override
    public void onInterrupt() {
        interruptAllFeedback();
    }

    public void interruptAllFeedback() {
        // Don't interrupt feedback if the tutorial is active.
        if (AccessibilityTutorialActivity.isTutorialActive()) {
            return;
        }

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
        LogUtils.log(this, Log.VERBOSE, "System bound to service.");
        resumeInfrastructure();

        // Handle any update actions.
        final TalkBackUpdateHelper helper = new TalkBackUpdateHelper(this);
        helper.showPendingNotifications();
        helper.checkUpdate();

        // Handle showing the tutorial if touch exploration is enabled.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final ContentResolver resolver = getContentResolver();
            if (Settings.Secure.getInt(resolver, Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1) {
                onTouchExplorationEnabled();
            } else {
                registerTouchSettingObserver(resolver);
            }
        }
    }

    /**
     * @return {@code true} if TalkBack is running and initialized,
     *         {@code false} otherwise.
     */
    public static boolean isServiceActive() {
        final TalkBackService service = getInstance();
        if (service == null) {
            return false;
        }

        return (service.mServiceState == ServiceState.ACTIVE);
    }

    /**
     * Returns the active TalkBack instance, or {@code null} if not available.
     */
    public static TalkBackService getInstance() {
        return sInstance;
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private void registerTouchSettingObserver(ContentResolver resolver) {
        final Uri uri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
        resolver.registerContentObserver(uri, false, mTouchExploreObserver);
    }

    /**
     * Initializes the controllers, managers, and processors. This should only
     * be called once from {@link #onCreate}.
     */
    private void initializeInfrastructure() {
        // Initialize static instances that do not have dependencies.
        NodeSpeechRuleProcessor.initialize(this);
        ClassLoadingManager.getInstance().init(this);

        mAccessibilityManager = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

        // Initialize the feedback controller and load the default theme.
        mFeedbackController = MappedFeedbackController.initialize(this);
        final MappedThemeLoader themeLoader = mFeedbackController.getThemeLoader();
        themeLoader.loadTheme(this, R.raw.feedbacktheme_default);

        mSpeechController = new SpeechController(this);

        if (Build.VERSION.SDK_INT >= CursorController.MIN_API_LEVEL) {
            mCursorController = new CursorController(this);
            mCursorController.setListener(mCursorControllerListener);
            mAccessibilityEventListeners.add(mCursorController);
        }

        if (Build.VERSION.SDK_INT >= FullScreenReadController.MIN_API_LEVEL) {
            mFullScreenReadController = new FullScreenReadController(this);
            mAccessibilityEventListeners.add(mFullScreenReadController);
        }

        if (Build.VERSION.SDK_INT >= ShakeDetector.MIN_API_LEVEL) {
            mShakeDetector = new ShakeDetector(this);
        }

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        mProcessorEventQueue = new ProcessorEventQueue(this);
        mProcessorEventQueue.setTestingListener(mTestingListener);

        mAccessibilityEventListeners.add(mProcessorEventQueue);
        mAccessibilityEventListeners.add(new ProcessorScrollPosition(this));

        if (Build.VERSION.SDK_INT >= ProcessorLongHover.MIN_API_LEVEL) {
            mAccessibilityEventListeners.add(new ProcessorLongHover(this));
        }

        if (Build.VERSION.SDK_INT >= ProcessorFocusAndSingleTap.MIN_API_LEVEL) {
            mProcessorFollowFocus = new ProcessorFocusAndSingleTap(this);
            mAccessibilityEventListeners.add(mProcessorFollowFocus);
        }

        if (Build.VERSION.SDK_INT >= VolumeMonitor.MIN_API_LEVEL) {
            mVolumeMonitor = new VolumeMonitor(this);
        }

        if (Build.VERSION.SDK_INT >= ProcessorGestureVibrator.MIN_API_LEVEL) {
            mAccessibilityEventListeners.add(new ProcessorGestureVibrator());
        }

        if (Build.VERSION.SDK_INT >= ProcessorWebContent.MIN_API_LEVEL) {
            mAccessibilityEventListeners.add(new ProcessorWebContent(this));
        }

        if (Build.VERSION.SDK_INT >= ProcessorVolumeStream.MIN_API_LEVEL) {
            final ProcessorVolumeStream processorVolumeStream = new ProcessorVolumeStream(this);
            mAccessibilityEventListeners.add(processorVolumeStream);
            mKeyEventListeners.add(processorVolumeStream);
        }

        if (Build.VERSION.SDK_INT >= KeyComboManager.MIN_API_LEVEL) {
            final KeyComboManager keyComboManager = new KeyComboManager();
            keyComboManager.setListener(mKeyComboListener);
            keyComboManager.loadDefaultCombos();
            mKeyEventListeners.add(keyComboManager);
        }

        mOrientationMonitor = new OrientationMonitor(this);

        final PackageManager packageManager = getPackageManager();
        final boolean deviceIsPhone = packageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);

        // Only initialize telephony and call state for phones.
        if (deviceIsPhone) {
            mCallStateMonitor = new CallStateMonitor(this);
        }

        final boolean deviceHasTouchscreen = packageManager.hasSystemFeature(
                PackageManager.FEATURE_TOUCHSCREEN);

        if (deviceIsPhone || deviceHasTouchscreen) {
            // Although this receiver includes code responding to phone-specific
            // intents, it should also be registered for touch screen devices
            // without telephony.
            mRingerModeAndScreenMonitor = new RingerModeAndScreenMonitor(this);
        }

        if (Build.VERSION.SDK_INT >= TextToSpeechManager.MIN_API_LEVEL) {
            mTextToSpeechManager = new TextToSpeechManager(this);
            mTextToSpeechManager.addListener(mTtsDiscoveryListener);
        }

        // Set up the radial menu manager and TalkBack-specific client.
        final TalkBackRadialMenuClient radialMenuClient = new TalkBackRadialMenuClient(this);
        mRadialMenuManager = new RadialMenuManager(this);
        mRadialMenuManager.setClient(radialMenuClient);
    }

    /**
     * Registers listeners, sets service info, loads preferences. This should be
     * called from {@link #onServiceConnected} and when TalkBack resumes from a
     * suspended state.
     */
    private void resumeInfrastructure() {
        if (isServiceActive()) {
            LogUtils.log(this, Log.ERROR, "Attempted to resume while not suspended");
            return;
        }

        setServiceState(ServiceState.ACTIVE);
        stopForeground(true);

        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
        info.flags |= AccessibilityServiceInfo.DEFAULT;
        info.flags |= AccessibilityServiceInfoCompatUtils.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        info.flags |= AccessibilityServiceInfoCompatUtils.FLAG_REPORT_VIEW_IDS;
        info.flags |= AccessibilityServiceInfoCompatUtils.FLAG_REQUEST_FILTER_KEY_EVENTS;
        info.notificationTimeout = 0;

        // Ensure the initial touch exploration request mode is correct.
        if (SUPPORTS_TOUCH_PREF && SharedPreferencesUtils.getBooleanPref(
                    mPrefs, getResources(), R.string.pref_explore_by_touch_key,
                    R.bool.pref_explore_by_touch_default)) {
            info.flags |= AccessibilityServiceInfoCompatUtils.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }

        setServiceInfo(info);

        if (mCallStateMonitor != null) {
            registerReceiver(mCallStateMonitor, mCallStateMonitor.getFilter());
        }

        if (mRingerModeAndScreenMonitor != null) {
            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
        }

        if (mTextToSpeechManager != null) {
            mTextToSpeechManager.startDiscovery();
        }

        if (mRadialMenuManager != null) {
            registerReceiver(mRadialMenuManager, mRadialMenuManager.getFilter());
        }

        if (mVolumeMonitor != null) {
            registerReceiver(mVolumeMonitor, mVolumeMonitor.getFilter());
        }

        mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        // Add the broadcast listener for gestures.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERFORM_GESTURE);
        registerReceiver(mActiveReceiver, filter, PERMISSION_TALKBACK, null);

        // Enable the proxy activity for long-press search.
        final PackageManager packageManager = getPackageManager();
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        reloadPreferences();
    }

    /**
     * Registers listeners, sets service info, loads preferences. This should be
     * called from {@link #onServiceConnected} and when TalkBack resumes from a
     * suspended state.
     */
    private void suspendInfrastructure() {
        if (!isServiceActive()) {
            LogUtils.log(this, Log.ERROR, "Attempted to suspend while already suspended");
            return;
        }

        interruptAllFeedback();
        setServiceState(ServiceState.SUSPENDED);

        setServiceInfo(new AccessibilityServiceInfo());

        mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        unregisterReceiver(mActiveReceiver);

        if (mCallStateMonitor != null) {
            unregisterReceiver(mCallStateMonitor);
        }

        if (mRingerModeAndScreenMonitor != null) {
            unregisterReceiver(mRingerModeAndScreenMonitor);
        }

        if (mRadialMenuManager != null) {
            unregisterReceiver(mRadialMenuManager);
            mRadialMenuManager.clearCache();
        }

        if (mVolumeMonitor != null) {
            unregisterReceiver(mVolumeMonitor);
            mVolumeMonitor.releaseControl();
        }

        if (mShakeDetector != null) {
            mShakeDetector.setEnabled(false);
        }

        if (SUPPORTS_TOUCH_PREF) {
            final ContentResolver resolver = getContentResolver();
            resolver.unregisterContentObserver(mTouchExploreObserver);
        }

        // Disable the proxy activity for long-press search.
        final PackageManager packageManager = getPackageManager();
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Remove any pending notifications that shouldn't persist.
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();

        if (mTtsOverlay != null) {
            mTtsOverlay.hide();
        }
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        if (mCursorController != null) {
            mCursorController.shutdown();
        }

        if (mFullScreenReadController != null) {
            mFullScreenReadController.shutdown();
        }

        if (mTextToSpeechManager != null) {
            mTextToSpeechManager.shutdown();
        }

        ClassLoadingManager.getInstance().shutdown();
        mFeedbackController.shutdown();
        mSpeechController.shutdown();
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

        // Always drop events if the service is suspended.
        if (!isServiceActive()) {
            return true;
        }

        // Always drop duplicate events (only applies to API < 14).
        if (AccessibilityEventUtils.eventEquals(mLastSpokenEvent, event)) {
            LogUtils.log(this, Log.VERBOSE, "Drop duplicate event");
            return true;
        }

        // If touch exploration is enabled, drop automatically generated events
        // that are sent immediately after a window state change... unless we
        // decide to keep the event.
        if (AccessibilityManagerCompat.isTouchExplorationEnabled(mAccessibilityManager)
                && ((event.getEventType() & AUTOMATIC_AFTER_STATE_CHANGE) != 0)
                && ((event.getEventTime() - mLastWindowStateChanged) < DELAY_AUTO_AFTER_STATE)
                && !shouldKeepAutomaticEvent(event)) {
            LogUtils.log(this, Log.VERBOSE, "Drop event after window state change");
            return true;
        }

        // Real notification events always have parcelable data.
        final boolean isNotification =
                (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)
                && (event.getParcelableData() != null);

        final boolean isPhoneActive = (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState() != TelephonyManager.CALL_STATE_IDLE);
        final boolean shouldSpeakCallerId = (mSpeakCallerId && (mCallStateMonitor != null)
                && (mCallStateMonitor.getCurrentCallState()
                        == TelephonyManager.CALL_STATE_RINGING));

        if (!mPowerManager.isScreenOn() && !shouldSpeakCallerId) {
            if (!mSpeakWhenScreenOff) {
                // If the user doesn't allow speech when the screen is
                // off, drop the event immediately.
                LogUtils.log(this, Log.VERBOSE, "Drop event due to screen state and user pref");
                return true;
            } else if (!isNotification) {
                // If the user allows speech when the screen is off, drop
                // all non-notification events.
                LogUtils.log(this, Log.VERBOSE, "Drop non-notification event due to screen state");
                return true;
            }
        }

        final boolean canInterruptRadialMenu = AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_INTERRUPT_RADIAL_MENU);
        final boolean silencedByRadialMenu = (mRadialMenuManager.isRadialMenuShowing()
                && !canInterruptRadialMenu);

        // Don't speak events that cannot interrupt the radial menu, if showing
        if (silencedByRadialMenu) {
            LogUtils.log(this, Log.VERBOSE, "Drop event due to radial menu state");
            return true;
        }

        // Don't speak notification events if the user is touch exploring or a phone call is active.
        if (isNotification && (mIsUserTouchExploring || isPhoneActive)) {
            LogUtils.log(this, Log.VERBOSE, "Drop notification due to touch or phone state");
            return true;
        }

        final int touchscreenState = getResources().getConfiguration().touchscreen;
        final boolean isTouchInteractionStateChange = AccessibilityEventUtils.eventMatchesAnyType(
                event, MASK_EVENT_TYPES_TOUCH_STATE_CHANGES);

        // Drop all events related to touch interaction state on devices that don't support touch.
        final int touchscreenConfig = getResources().getConfiguration().touchscreen;
        if ((touchscreenState == Configuration.TOUCHSCREEN_NOTOUCH)
                && isTouchInteractionStateChange) {
            return true;
        }

        return false;
    }

    /**
     * Helper method for {@link #shouldDropEvent} that handles events that
     * automatically occur immediately after a window state change.
     *
     * @param event The automatically generated event to consider retaining.
     * @return Whether to retain the event.
     */
    private boolean shouldKeepAutomaticEvent(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);

        // Don't drop focus events from EditTexts.
        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            AccessibilityNodeInfoCompat node = null;

            try {
                node = record.getSource();
                if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                        this, node, android.widget.EditText.class)) {
                    return true;
                }
            } finally {
                AccessibilityNodeInfoUtils.recycleNodes(node);
            }
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
        } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            mLastWindowStateChanged = SystemClock.uptimeMillis();
        }
    }

    /**
     * Caches the last spoken event. Used for duplicate detection on API < 14.
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
     * Passes the event to all registered {@link AccessibilityEventListener}s in the order
     * they were added.
     *
     * @param event The current event.
     */
    private void processEvent(AccessibilityEvent event) {
        for (AccessibilityEventListener eventProcessor : mAccessibilityEventListeners) {
            eventProcessor.onAccessibilityEvent(event);
        }
    }

    /**
     * Adds an event listener.
     *
     * @param listener The listener to add.
     */
    public void addEventListener(AccessibilityEventListener listener) {
        mAccessibilityEventListeners.add(listener);
    }

    /**
     * Posts a {@link Runnable} to removes an event listener. This is safe to
     * call from inside {@link AccessibilityEventListener#onAccessibilityEvent(AccessibilityEvent)}.
     *
     * @param listener The listener to remove.
     */
    /* protected */void postRemoveEventListener(final AccessibilityEventListener listener) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mAccessibilityEventListeners.remove(listener);
            }
        });
    }

    /**
     * Reloads service preferences.
     */
    private void reloadPreferences() {
        final Resources res = getResources();

        mSpeakWhenScreenOff = SharedPreferencesUtils.getBooleanPref(
                mPrefs, res, R.string.pref_screenoff_key, R.bool.pref_screenoff_default);
        mSpeakCallerId = SharedPreferencesUtils.getBooleanPref(
                mPrefs, res, R.string.pref_caller_id_key, R.bool.pref_caller_id_default);

        final String automaticResume = SharedPreferencesUtils.getStringPref(mPrefs, res,
                R.string.pref_resume_talkback_key, R.string.pref_resume_talkback_default);
        mAutomaticResume = AutomaticResumePreference.safeValueOf(automaticResume);

        final boolean silenceOnProximity = SharedPreferencesUtils.getBooleanPref(
                mPrefs, res, R.string.pref_proximity_key, R.bool.pref_proximity_default);
        mSpeechController.setSilenceOnProximity(silenceOnProximity);

        final int logLevel = (DEBUG ? Log.VERBOSE : SharedPreferencesUtils.getIntFromStringPref(
                mPrefs, res, R.string.pref_log_level_key, R.string.pref_log_level_default));
        LogUtils.setLogLevel(logLevel);

        if (mProcessorFollowFocus != null) {
            final boolean useSingleTap = SharedPreferencesUtils.getBooleanPref(
                    mPrefs, res, R.string.pref_single_tap_key, R.bool.pref_single_tap_default);

            mProcessorFollowFocus.setSingleTapEnabled(useSingleTap);

            // Update the "X to select" long-hover hint.
            NodeHintRule.NodeHintHelper.updateActionResId(useSingleTap);
        }

        if (mShakeDetector != null) {
            final int shakeThreshold = SharedPreferencesUtils.getIntFromStringPref(
                    mPrefs, res, R.string.pref_shake_to_read_threshold_key,
                    R.string.pref_shake_to_read_threshold_default);
            final boolean useShake = (shakeThreshold > 0) && ((mCallStateMonitor == null) || (
                    mCallStateMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_IDLE));

            mShakeDetector.setEnabled(useShake);
        }

        if (SUPPORTS_TOUCH_PREF) {
            final boolean touchExploration = SharedPreferencesUtils.getBooleanPref(mPrefs, res,
                    R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
            requestTouchExploration(touchExploration);

            final String verticalGesturesPref = SharedPreferencesUtils.getStringPref(mPrefs, res,
                    R.string.pref_two_part_vertical_gestures_key,
                    R.string.pref_two_part_vertical_gestures_default);
            mVerticalGestureCycleGranularity = verticalGesturesPref.equals(
                    getString(R.string.value_two_part_vertical_gestures_cycle));
        }
    }

    /**
     * Attempts to change the state of touch exploration.
     * <p>
     * Should only be called if {@link #SUPPORTS_TOUCH_PREF} is true.
     *
     * @param requestedState {@code true} to request exploration.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void requestTouchExploration(boolean requestedState) {
        final AccessibilityServiceInfo info = getServiceInfo();
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
        if (!mPrefs.getBoolean(PREF_FIRST_TIME_USER, true)) {
            return;
        }

        final Editor editor = mPrefs.edit();
        editor.putBoolean(PREF_FIRST_TIME_USER, false);
        editor.commit();

        final int touchscreenState = getResources().getConfiguration().touchscreen;

        if (Build.VERSION.SDK_INT >= AccessibilityTutorialActivity.MIN_API_LEVEL
                && (touchscreenState != Configuration.TOUCHSCREEN_NOTOUCH)) {
            final Intent tutorial = new Intent(this, AccessibilityTutorialActivity.class);
            tutorial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tutorial.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
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

                mSpeechController.speak(name, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
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

    private final KeyComboListener mKeyComboListener = new KeyComboListener() {
        @Override
        public boolean onComboPerformed(int id) {
            if (id == R.id.key_combo_suspend_resume) {
                if (isServiceActive()) {
                    confirmSuspendTalkBack();
                } else {
                    resumeTalkBack();
                }
                return true;
            }

            return false;
        }
    };

    /**
     * Reloads preferences whenever their values change.
     */
    private final OnSharedPreferenceChangeListener
            mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            LogUtils.log(this, Log.DEBUG, "A shared preference changed: %s", key);
            reloadPreferences();
        }
    };

    /**
     * Shows the accessibility tutorial after touch exploration is turned on for
     * the first time.
     */
    private final ContentObserver mTouchExploreObserver = new ContentObserver(mHandler) {
        @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            final ContentResolver resolver = getContentResolver();
            final boolean touchExplorationEnabled = (Settings.Secure.getInt(resolver,
                    Settings.Secure.TOUCH_EXPLORATION_ENABLED, 0) == 1);
            if (!touchExplorationEnabled) {
                return;
            }

            resolver.unregisterContentObserver(this);

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
                final ShortcutGesture gesture = ShortcutGesture.safeValueOf(gestureName);
                performGesture(gesture);
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
                switch (mAutomaticResume) {
                    case KEYGUARD:
                        final KeyguardManager keyguard = (KeyguardManager) getSystemService(
                                Context.KEYGUARD_SERVICE);
                        if (keyguard.inKeyguardRestrictedInputMode()) {
                            resumeTalkBack();
                        }
                        break;
                    case SCREEN_ON:
                        resumeTalkBack();
                        break;
                    default:
                        // Do nothing.
                }
            }
        }
    };

    private final TtsDiscoveryListener mTtsDiscoveryListener = new TtsDiscoveryListener() {
        @Override
        public void onTtsDiscovery(TtsEngineInfo engine, List<String> availableLanguages) {
            LogUtils.log(this, Log.INFO, "Discovered engine %s with %d languages", engine.name,
                    availableLanguages.size());
        }

        @Override
        public void onTtsRemoval(TtsEngineInfo engine) {
            LogUtils.log(this, Log.INFO, "Removed engine %s", engine.name);
        }
    };

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        try {
            if (mRadialMenuManager != null && mRadialMenuManager.isRadialMenuShowing()) {
                mRadialMenuManager.dismissAll();
            }

            if (mSuspendDialog != null) {
                mSuspendDialog.dismiss();
            }
        } catch (Exception e) {
            // Do nothing.
        } finally {
            if (mSystemUeh != null) {
                mSystemUeh.uncaughtException(thread, ex);
            }
        }
    }

    /**
     * Interface for receiving callbacks when the state of the TalkBack service
     * changes.
     * <p>
     * Implementing controllers should note that this may be invoked even after
     * the controller was explicitly shut down by TalkBack.
     * <p>
     * {@link ServiceState}
     * {@link TalkBackService#addServiceStateListener(ServiceStateListener)}
     * {@link TalkBackService#removeServiceStateListener(ServiceStateListener)}
     */
    public interface ServiceStateListener {
        public void onServiceStateChanged(ServiceState newState);
    }

    /**
     * Interface for passive event processors.
     */
    public interface AccessibilityEventListener {
        public void onAccessibilityEvent(AccessibilityEvent event);
    }

    /**
     * Interface for key event listeners.
     */
    public interface KeyEventListener {
        public boolean onKeyEvent(KeyEvent event);
    }
}
