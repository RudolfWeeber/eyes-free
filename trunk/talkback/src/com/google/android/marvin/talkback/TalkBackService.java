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
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.TalkBackShortcutHandler.TalkBackShortcut;
import com.google.android.marvin.utils.ClassLoadingManager;
import com.google.android.marvin.utils.InfrastructureStateListener;
import com.google.android.marvin.utils.PackageManagerUtils;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.content.pm.PackageManagerCompatUtils;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
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

    /* package */ static final String KICKBACK_PACKAGE = "com.google.android.marvin.kickback";
    /* package */ static final String SOUNDBACK_PACKAGE = "com.google.android.marvin.soundback";

    /** The minimum version required for KickBack to disable itself. */
    /* package */ static final int KICKBACK_REQUIRED_VERSION = 5;

    /** The minimum version required for SoundBack to disable itself. */
    /* package */ static final int SOUNDBACK_REQUIRED_VERSION = 7;

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
     * Flag if the infrastructure has been initialized.
     */
    private boolean mInfrastructureInitialized = false;

    /**
     * Handler for uncaught exceptions and crash reports.
     */
    private TalkBackExceptionHandler mExceptionHandler;

    /** Manages the pending notifications. */
    private NotificationCache mNotificationCache;

    /**
     * Controller for speech feedback.
     */
    private SpeechController mSpeechController;

    /**
     * Controller for audio and haptic feedback.
     */
    private PreferenceFeedbackController mFeedbackController;

    /**
     * List of passive event processors.
     */
    private LinkedList<EventProcessor> mEventProcessors;

    /**
     * The audio manager used for changing the ringer volume for incoming calls.
     */
    private AudioManager mAudioManager;

    /**
     * The telephony manager used to determine the call state.
     */
    private TelephonyManager mTelephonyManager;

    /**
     * {@link BroadcastReceiver} for tracking the ringer mode and screen state.
     */
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

    /**
     * {@link BroadcastReceiver} for tracking the call state.
     */
    private CallStateMonitor mCallStateMonitor;

    /**
     * {@link BroadcastReceiver} for tracking volume changes.
     */
    private VolumeMonitor mVolumeMonitor;

    /**
     * The last spoken accessibility event, used for crash reporting.
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

        checkUpdate();
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
    public void onInterrupt() {
        mSpeechController.interrupt();
    }

    @Override
    protected void onServiceConnected() {
        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 0;

        setServiceInfo(info);
        shutdownInfrastructure();
        initializeInfrastructure();
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
        mExceptionHandler = new TalkBackExceptionHandler(this);
        mExceptionHandler.register();

        mNotificationCache = new NotificationCache(this);
        mSpeechController = new SpeechController(this);
        mFeedbackController = new PreferenceFeedbackController(this);

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        mEventProcessors = new LinkedList<EventProcessor>();
        mEventProcessors.add(new ProcessorEventQueue(this, mFeedbackController, mSpeechController));
        mEventProcessors.add(new ProcessorScrollPosition(this, mSpeechController));

        if (Build.VERSION.SDK_INT >= ProcessorLongHover.MIN_API_LEVEL) {
            mEventProcessors.add(new ProcessorLongHover(this, mSpeechController));
        }

        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= VolumeMonitor.MIN_API_LEVEL) {
            mVolumeMonitor = new VolumeMonitor(this, mSpeechController);
        }

        final PackageManager packageManager = getPackageManager();

        final boolean deviceIsPhone = PackageManagerCompatUtils.hasSystemFeature(
                packageManager, PackageManagerCompatUtils.FEATURE_TELEPHONY, true);

        // Only initialize telephony and call state for phones.
        if (deviceIsPhone) {
            mTelephonyManager = (TelephonyManager) getSystemService(Service.TELEPHONY_SERVICE);
            mCallStateMonitor = new CallStateMonitor(mSpeechController, mFeedbackController);

            registerReceiver(mCallStateMonitor, mCallStateMonitor.getFilter());
            addInfrastructureStateListener(mCallStateMonitor);
        }

        final boolean deviceHasTouchscreen = PackageManagerCompatUtils.hasSystemFeature(
                packageManager, PackageManagerCompatUtils.FEATURE_TOUCHSCREEN, true);

        if (deviceIsPhone || deviceHasTouchscreen) {
            // Although this receiver includes code responding to phone-specific
            // intents, it should also be registered for touch screen devices
            // without telephony.
            mRingerModeAndScreenMonitor =
                    new RingerModeAndScreenMonitor(this, mFeedbackController, mSpeechController,
                            mAudioManager, mTelephonyManager, mNotificationCache);

            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
            addInfrastructureStateListener(mRingerModeAndScreenMonitor);
        }

        // Register the preference change listener.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        reloadPreferences(prefs);

        // Register the class loading manager.
        addInfrastructureStateListener(ClassLoadingManager.getInstance());

        mInfrastructureInitialized = true;
        notifyInfrastructureStateListeners();

        // Attempt to process any pending crash reports.
        mExceptionHandler.processCrashReport();

        // Register the shortcut broadcast listener.
        final IntentFilter filter = new IntentFilter(TalkBackShortcutHandler.BROADCAST_SHORTCUT);
        registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        if (!mInfrastructureInitialized) {
            return;
        }

        // Unregister the broadcast receiver.
        unregisterReceiver(mBroadcastReceiver);

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
            mRingerModeAndScreenMonitor.shutdown();
            unregisterReceiver(mRingerModeAndScreenMonitor);
            mRingerModeAndScreenMonitor = null;
        }

        // Unregister all infrastructure state listeners.
        mInfrastructureInitialized = false;
        notifyInfrastructureStateListeners();
        mInfrastructureStateListeners.clear();

        // Remove all event processors.
        mEventProcessors.clear();

        mFeedbackController.shutdown();
        mFeedbackController = null;

        mSpeechController.shutdown();
        mSpeechController = null;

        mNotificationCache.clear();
        mNotificationCache = null;

        if (mExceptionHandler != null) {
            mExceptionHandler.unregister();
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
            listener.onInfrastructureStateChange(this, mInfrastructureInitialized);
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
        if (!mInfrastructureInitialized) {
            return true;
        }

        // Always drop duplicate events.
        if (AccessibilityEventUtils.eventEquals(mLastSpokenEvent, event)) {
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
     * Passes the event to all registered {@link EventProcessor}s in the order
     * they were added.
     *
     * @param event The current event.
     */
    private void processEvent(AccessibilityEvent event) {
        for (EventProcessor eventProcessor : mEventProcessors) {
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

        if (mRingerModeAndScreenMonitor != null) {
            mRingerModeAndScreenMonitor.setSilenceOnProximity(silenceOnProximity);
        }

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
     * Handles broadcast {@link TalkBackShortcut}s.
     *
     * @param shortcut The shortcut received from the broadcast.
     */
    private void handleShortcut(TalkBackShortcut shortcut) {
        switch (shortcut) {
        case REPEAT_LAST_UTTERANCE:
                mSpeechController.repeatLastUtterance();
                break;
        case SPELL_LAST_UTTERANCE:
                mSpeechController.spellLastUtterance();
                break;
        }
    }

    /**
     * Receives {@link TalkBackShortcutHandler#BROADCAST_SHORTCUT} broadcasts.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (TalkBackShortcutHandler.BROADCAST_SHORTCUT.equals(action)) {
                final String commandName =
                        intent.getStringExtra(TalkBackShortcutHandler.EXTRA_SHORTCUT_NAME);
                final TalkBackShortcut shortcut = TalkBackShortcut.valueOf(commandName);

                if (shortcut != null) {
                    handleShortcut(shortcut);
                }
            }
        }
    };

    /**
     * Reloads preferences whenever their values change.
     */
    private final
            SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
                    new SharedPreferences.OnSharedPreferenceChangeListener() {
                            @Override
                        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                            reloadPreferences(prefs);
                        }
                    };

    /**
     * Interface for passive event processors.
     */
    interface EventProcessor {
        public void process(AccessibilityEvent event);
    }
}
