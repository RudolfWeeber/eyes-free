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
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.CursorController.CursorControllerListener;
import com.google.android.marvin.talkback.CursorGranularityManager.CursorGranularity;
import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.googlecode.eyesfree.compat.content.pm.PackageManagerCompatUtils;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.InfrastructureStateListener;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.PackageManagerUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * An {@link AccessibilityService} that provides spoken, haptic, and audible
 * feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackService extends AccessibilityService {
    private static final String PREF_APP_VERSION = "app_version";
    private static final String PREF_FIRST_TIME_USER = "first_time_user";

    /* package */ static final String KICKBACK_PACKAGE = "com.google.android.marvin.kickback";
    /* package */ static final String SOUNDBACK_PACKAGE = "com.google.android.marvin.soundback";

    /** The minimum version required for KickBack to disable itself. */
    /* package */ static final int KICKBACK_REQUIRED_VERSION = 5;

    /** The minimum version required for SoundBack to disable itself. */
    /* package */ static final int SOUNDBACK_REQUIRED_VERSION = 7;

    private static final int FEEDBACK_GESTURE_FAILED = R.raw.complete;

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

    /** Flag if the infrastructure has been initialized. */
    private static boolean sInfrastructureInitialized = false;

    /** Manages the pending notifications. */
    private NotificationCache mNotificationCache;

    /** Controller for cursor movement. */
    private CursorController mCursorController;

    /** Controller for speech feedback. */
    private SpeechController mSpeechController;

    /** Controller for audio and haptic feedback. */
    private PreferenceFeedbackController mFeedbackController;

    /** List of passive event processors. */
    private LinkedList<EventListener> mEventListeners;

    /** Focus-follow processor. Used in JellyBean and above. */
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

    /**
     * The last spoken accessibility event, used for detecting duplicate events.
     */
    private AccessibilityEvent mLastSpokenEvent;

    /** Developer overlay for debugging touch exploration. */
    private DeveloperOverlay mDeveloperOverlay;

    /** Text-to-speech overlay for debugging speech output. */
    private TextToSpeechOverlay mTtsOverlay;

    /** Whether speech should be silenced based on the ringer mode. */
    private RingerPreference mRingerPref;

    /**
     * Whether speech should be allowed when the screen is off. When set to
     * {@code false}, the phone will remain silent when the screen is off.
     */
    private boolean mSpeakWhenScreenOff;

    /**
     * Keep track of whether the user is currently touch exploring so that we
     * don't interrupt with notifications.
     */
    private boolean mIsUserTouchExploring;

    @Override
    public void onCreate() {
        super.onCreate();

        // The service had been created, but not connected. It will not receive
        // events until onServiceConnected() is called.
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

        shutdownInfrastructure();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (shouldDropEvent(event)) {
            return;
        }

        maintainExplorationState(event);
        cacheEvent(event);
        processEvent(event);
    }

    @Override
    public boolean onGesture(int gestureId) {
        if (mCursorController == null) {
            // This will only happen when the service is shutting down.
            return true;
        }

        LogUtils.log(this, Log.VERBOSE, "Recognized gesture %s", gestureId);

        // Gestures always stop global speech.
        // TODO: Will this negatively affect something like Books?
        mSpeechController.stopAll();

        // Handle statically defined gestures.
        switch (gestureId) {
            case AccessibilityService.GESTURE_SWIPE_UP:
            case AccessibilityService.GESTURE_SWIPE_LEFT:
                if (!mCursorController.previous()) {
                    mFeedbackController.playSound(FEEDBACK_GESTURE_FAILED);
                }
                return true;
            case AccessibilityService.GESTURE_SWIPE_DOWN:
            case AccessibilityService.GESTURE_SWIPE_RIGHT:
                if (!mCursorController.next()) {
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
        }

        // Never let the system handle gestures.
        return true;
    }

    private static final String SHORTCUT_UNASSIGNED = "UNASSIGNED";
    private static final String SHORTCUT_BACK = "BACK";
    private static final String SHORTCUT_HOME = "HOME";
    private static final String SHORTCUT_RECENTS = "RECENTS";
    private static final String SHORTCUT_NOTIFICATIONS = "NOTIFICATIONS";

    private void performCustomGesture(int keyResId, int defaultResId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String key = getString(keyResId);
        final String defaultValue = getString(defaultResId);
        final String value = prefs.getString(key, defaultValue);

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
        }
    }

    @Override
    public void onInterrupt() {
        mSpeechController.interrupt();
    }

    @Override
    protected void onServiceConnected() {
        if (Build.VERSION.SDK_INT < 14) {
            final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
            info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
            info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
            info.flags |= AccessibilityServiceInfo.DEFAULT;
            info.notificationTimeout = 0;
            setServiceInfo(info);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                checkUpdate();
                shutdownInfrastructure();
                initializeInfrastructure();
            }
        });
    }

    /**
     * Returns true if TalkBack is running and initialized.
     */
    public static boolean isServiceInitialized() {
        return sInfrastructureInitialized;
    }

    private void checkUpdate() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final int previousVersion = prefs.getInt(PREF_APP_VERSION, -1);

        final PackageManager pm = getPackageManager();
        final int currentVersion;

        try {
            final PackageInfo packageInfo = pm.getPackageInfo(getPackageName(), 0);
            currentVersion = packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        if (previousVersion == currentVersion) {
            return;
        }

        final Editor editor = prefs.edit();
        editor.putInt(PREF_APP_VERSION, currentVersion);

        // Update the application to r42, which is the first version to use updates.
        if (previousVersion < 42) {
            // If KickBack is installed, disable vibration feedback by default.
            if (PackageManagerUtils.hasPackage(this, KICKBACK_PACKAGE)) {
                final String prefVibration = getString(R.string.pref_vibration_key);
                editor.putBoolean(prefVibration, false);
            }

            // If SoundBack is installed, disable sound feedback by default.
            if (PackageManagerUtils.hasPackage(this, SOUNDBACK_PACKAGE)) {
                final String prefSoundback = getString(R.string.pref_soundback_key);
                editor.putBoolean(prefSoundback, false);
            }

            // TODO: Update KickBack and SoundBack apps to mute output when
            // TalkBack >= r42 is installed, use service enabled states to
            // control feedback from TalkBack.
        }

        editor.commit();
    }

    /**
     * Initializes the infrastructure.
     */
    private void initializeInfrastructure() {
        mNotificationCache = new NotificationCache(this);
        mSpeechController = new SpeechController(this);
        mFeedbackController = new PreferenceFeedbackController(this);

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        // TODO: Figure out a clean way to avoid passing the feedback and speech
        // controllers everywhere.
        mEventListeners = new LinkedList<EventListener>();
        mEventListeners.add(new ProcessorEventQueue(this, mFeedbackController, mSpeechController));
        mEventListeners.add(new ProcessorScrollPosition(this, mSpeechController));

        if (Build.VERSION.SDK_INT >= ProcessorLongHover.MIN_API_LEVEL) {
            mEventListeners.add(new ProcessorLongHover(this, mSpeechController));
        }

        if (Build.VERSION.SDK_INT >= ProcessorFollowFocus.MIN_API_LEVEL) {
            mProcessorFollowFocus = new ProcessorFollowFocus(this);
            mEventListeners.add(mProcessorFollowFocus);
        } else {
            mProcessorFollowFocus = null;
        }

        if (Build.VERSION.SDK_INT >= CursorController.MIN_API_LEVEL) {
            mCursorController = new CursorController(this);
            mCursorController.setListener(mCursorControllerListener);
        } else {
            mCursorController = null;
        }

        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= VolumeMonitor.MIN_API_LEVEL) {
            // TODO: Find a cleaner way to handle interaction between the volume
            // monitor (which needs to speak) and the speech controller (which
            // needs to adjust volume).
            mVolumeMonitor = new VolumeMonitor(this, mSpeechController);
            mSpeechController.setVolumeMonitor(mVolumeMonitor);
        } else {
            mVolumeMonitor = null;
        }

        final PackageManager packageManager = getPackageManager();

        final boolean deviceIsPhone = PackageManagerCompatUtils.hasSystemFeature(
                packageManager, PackageManagerCompatUtils.FEATURE_TELEPHONY, true);

        // Only initialize telephony and call state for phones.
        if (deviceIsPhone) {
            mCallStateMonitor = new CallStateMonitor(mSpeechController, mFeedbackController);

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
            mRingerModeAndScreenMonitor = new RingerModeAndScreenMonitor(
                    this, mSpeechController, mNotificationCache);

            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
            addInfrastructureStateListener(mRingerModeAndScreenMonitor);
        } else {
            mRingerModeAndScreenMonitor = null;
        }

        mOrientationMonitor = new OrientationMonitor(this, mSpeechController);

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
            final Uri touchExploreUri = Settings.Secure.getUriFor(Settings.Secure.TOUCH_EXPLORATION_ENABLED);
            resolver.registerContentObserver(touchExploreUri, false, mTouchExploreObserver);
        }
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        if (!sInfrastructureInitialized) {
            return;
        }

        final ContentResolver resolver = getContentResolver();
        resolver.unregisterContentObserver(mTouchExploreObserver);

        // Unregister the preference change listener.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        if (mDeveloperOverlay != null) {
            mDeveloperOverlay.hide();
            mDeveloperOverlay = null;
        }

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

        mNotificationCache.clear();
        mNotificationCache = null;

        if (mCursorController != null) {
            mCursorController.shutdown();
            mCursorController = null;
        }
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
        final boolean silencedByScreen = ((mRingerModeAndScreenMonitor != null)
                && (!mSpeakWhenScreenOff && mRingerModeAndScreenMonitor.isScreenOff()));
        final boolean silencedByTouch = (mIsUserTouchExploring && isNotification);

        // Cache notifications and don't speak when the screen is off or the
        // user is touch exploring.
        if (silencedByScreen || silencedByTouch) {
            if (isNotification) {
                cacheNotificationEvent(event);
            }

            return true;
        }

        return false;
    }

    /**
     * Cache a notification event so that we can speak it later. Use this when
     * speaking the notification directly would be disruptive (when the screen
     * is off or during touch exploration).
     *
     * @param event An event of type
     *            {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED}.
     */
    private void cacheNotificationEvent(AccessibilityEvent event) {
        mNotificationCache.add(event);
    }

    /**
     * Manages touch exploration state. Speaks cached notifications when
     * exploration ends.
     *
     * @param event The current event.
     */
    private void maintainExplorationState(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            mIsUserTouchExploring = true;
        } else if (eventType == AccessibilityEventCompat.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            mIsUserTouchExploring = false;
            speakCachedNotificationEvents();
        }
    }

    /**
     * Speaks cached notification events.
     */
    private void speakCachedNotificationEvents() {
        final CharSequence notificationSummary = mNotificationCache.getFormattedSummary();

        if (!TextUtils.isEmpty(notificationSummary)) {
            final StringBuilder text = StringBuilderUtils.appendWithSeparator(
                    null, getString(R.string.value_notification_summary), notificationSummary);

            mSpeechController.cleanUpAndSpeak(text, QueuingMode.INTERRUPT, null);
        }

        mNotificationCache.clear();
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

        final boolean silenceOnProximity = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_proximity_key, R.bool.pref_proximity_default);

        mSpeechController.setSilenceOnProximity(silenceOnProximity);

        final int logLevel =
                SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                        R.string.pref_log_level_key, R.string.pref_log_level_default);

        LogUtils.setLogLevel(logLevel);

        final boolean overlaySupported = (Build.VERSION.SDK_INT >= DeveloperOverlay.MIN_API_LEVEL);
        final boolean overlayPref = SharedPreferencesUtils.getBooleanPref(
                prefs, res, R.string.pref_developer_overlay_key,
                R.bool.pref_developer_overlay_default);

        if (overlaySupported && overlayPref && mDeveloperOverlay == null) {
            mDeveloperOverlay = new DeveloperOverlay(this);
            mDeveloperOverlay.show();
        } else if (!overlayPref && mDeveloperOverlay != null) {
            mDeveloperOverlay.hide();
            mDeveloperOverlay = null;
        }
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
                    final int resId = granularity.resId;
                    final String name = getString(resId);

                    // User actions should interrupt the current speech.
                    final QueuingMode queuingMode = fromUser ? QueuingMode.INTERRUPT
                            : QueuingMode.QUEUE;

                    mSpeechController.cleanUpAndSpeak(name, queuingMode, null);
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
     * Interface for passive event processors.
     */
    interface EventListener {
        public void process(AccessibilityEvent event);
    }
}
