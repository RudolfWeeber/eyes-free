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

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.ProximitySensor.ProximityChangeListener;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * {@link AccessibilityService} that provides spoken feedback.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author clchen@google.com (Charles L. Chen)
 * @author credo@google.com (Tim Credo)
 */
public class TalkBackService extends AccessibilityService {
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
     * Period for the proximity sensor to remain active after last utterance (in
     * milliseconds).
     */
    public static final long PROXIMITY_SENSOR_CUTOFF_THRESHOLD = 1000;

    /**
     * Manages pending speech events.
     */
    private final TalkBackHandler mSpeechHandler = new TalkBackHandler();

    /**
     * Manages the pending notifications.
     */
    private final NotificationCache mNotificationCache = new NotificationCache();

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

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

    /**
     * Proximity sensor for implementing "shut up" functionality.
     */
    private ProximitySensor mProximitySensor;

    /**
     * Controller for speech feedback.
     */
    private SpeechController mSpeechController;

    /**
     * Controller for audio and haptic feedback.
     */
    private FeedbackController mFeedbackController;

    /** Maps custom resources defined in preferences to resource identifiers. */
    private CustomResourceMapper mResourceMapper;

    /**
     * processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private SpeechRuleProcessor mSpeechRuleProcessor;

    /**
     * List of passive event processors.
     */
    private LinkedList<EventProcessor> mEventProcessors;

    /**
     * Loader of speech rules.
     */
    private SpeechRuleLoader mSpeechRuleLoader;

    /**
     * The last event - used to auto-determine the speech queue mode.
     */
    private int mLastEventType;

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
     * Access to preferences.
     */
    private SharedPreferences mPrefs;

    /**
     * Whether speech should be silenced based on the ringer mode.
     */
    private int mRingerPref;

    /**
     * Whether speech should be allowed when the screen is off. When set to
     * {@code false}, the phone will remain silent when the screen is off.
     */
    private boolean mSpeakWhenScreenOff;

    /**
     * Whether Caller ID should be spoken.
     */
    private boolean mAnnounceCallerId;

    /**
     * Whether to use the proximity sensor to silence speech.
     */
    private boolean mSilenceOnProximity;

    /**
     * The last spoken accessibility event, used for crash reporting.
     */
    private AccessibilityEvent mLastSpokenEvent = null;

    /**
     * Flag if the device has a telephony feature, so we know if to initialize
     * phone specific stuff.
     */
    private boolean mDeviceIsPhone;

    /**
     * Flag if the device has a touchscreen feature, so we know to initialize
     * touchscreen-specific stuff.
     */
    private boolean mDeviceHasTouchscreen;

    /**
     * Keep track of whether the user is currently touch exploring so that we
     * don't interrupt with notifications.
     */
    private boolean mIsUserTouchExploring = false;

    /**
     * Static handle to TalkBack so CommandInterfaceBroadcastReceiver can access
     * it.
     */
    private static TalkBackService sInstance;

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
        if (!mInfrastructureInitialized) {
            return;
        }

        setProximitySensorState(false);

        mSpeechController.shutdown();
        mFeedbackController.shutdown();

        if (mCallStateMonitor != null) {
            unregisterReceiver(mCallStateMonitor);
            mCallStateMonitor = null;
        }

        if (mRingerModeAndScreenMonitor != null) {
            unregisterReceiver(mRingerModeAndScreenMonitor);
            mRingerModeAndScreenMonitor = null;
        }

        mInfrastructureInitialized = false;
        notifyInfrastructureStateListeners();
        mInfrastructureStateListeners.clear();

        if (mExceptionHandler != null) {
            mExceptionHandler.unregister();
        }
    }

    @Override
    public void onServiceConnected() {
        shutdownInfrastructure();
        initializeInfrastructure();
    }

    /**
     * Initializes the infrastructure.
     */
    private void initializeInfrastructure() {
        mExceptionHandler = new TalkBackExceptionHandler(this);
        mExceptionHandler.register();

        final PackageManager packageManager = getPackageManager();

        mDeviceIsPhone = packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        mDeviceHasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

        mSpeechController = new SpeechController(this, mDeviceIsPhone);
        mFeedbackController = FeedbackController.getInstance(this);
        mResourceMapper = new CustomResourceMapper();
        mSpeechRuleProcessor = new SpeechRuleProcessor(this);

        // initialize the speech rule loader and load speech rules
        mSpeechRuleLoader =
                new SpeechRuleLoader(getPackageName(), mSpeechRuleProcessor, mDeviceIsPhone);
        mSpeechRuleLoader.loadSpeechRules();
        
        mEventProcessors = new LinkedList<EventProcessor>();
        mEventProcessors.add(new ProcessorLongHover(this, mSpeechController));
        mEventProcessors.add(new ProcessorScrollPosition(this, mSpeechController));

        addInfrastructureStateListener(mSpeechRuleLoader);

        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);

        // We initialize phone specific stuff only if needed
        if (mDeviceIsPhone) {
            mTelephonyManager = (TelephonyManager) getSystemService(Service.TELEPHONY_SERVICE);

            mCallStateMonitor = new CallStateMonitor();
            registerReceiver(mCallStateMonitor, mCallStateMonitor.getFilter());
        }

        if (mDeviceIsPhone || mDeviceHasTouchscreen) {
            /*
             * Although this receiver includes code responding to phone-specific
             * intents, it should also be registered for touch screen devices
             * without telephony.
             */
            mRingerModeAndScreenMonitor = new RingerModeAndScreenMonitor();
            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
        }

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        reloadPreferences();

        // register the class loading manager
        addInfrastructureStateListener(ClassLoadingManager.getInstance());

        mInfrastructureInitialized = true;
        notifyInfrastructureStateListeners();

        mExceptionHandler.processCrashReport();
    }

    /**
     * Adds an {@link InfrastructureStateListener}.
     */
    public void addInfrastructureStateListener(InfrastructureStateListener listener) {
        mInfrastructureStateListeners.add(listener);
    }

    /**
     * Removes an {@link InfrastructureStateListener}.
     */
    public void removeInfrastructureStateListener(InfrastructureStateListener listener) {
        mInfrastructureStateListeners.remove(listener);
    }

    /**
     * Notifies the {@link InfrastructureStateListener}s.
     */
    private void notifyInfrastructureStateListeners() {
        for (InfrastructureStateListener listener : mInfrastructureStateListeners) {
            listener.onInfrastructureStateChange(mInfrastructureInitialized);
        }
    }

    /**
     * Reload the preferences from the SharedPreferences object.
     */
    public void reloadPreferences() {
        final Resources res = getResources();

        final String ringerPref =
                mPrefs.getString(getString(R.string.pref_speak_ringer_key),
                        res.getString(R.string.pref_speak_ringer_default));
        mRingerPref = Integer.parseInt(ringerPref);

        mAnnounceCallerId =
                mPrefs.getBoolean(getString(R.string.pref_caller_id_key),
                        res.getBoolean(R.bool.pref_caller_id_default));
        mSilenceOnProximity =
                mPrefs.getBoolean(getString(R.string.pref_proximity_key),
                        res.getBoolean(R.bool.pref_proximity_default));
        mSpeakWhenScreenOff =
                mPrefs.getBoolean(getString(R.string.pref_screenoff_key),
                        res.getBoolean(R.bool.pref_screenoff_default));

        final String logLevelPref =
                mPrefs.getString(getString(R.string.pref_log_level_key),
                        getString(R.string.pref_log_level_default));
        final int logLevel = Integer.valueOf(logLevelPref);

        LogUtils.setLogLevel(logLevel);

        final boolean vibrationPref =
                mPrefs.getBoolean(getString(R.string.pref_vibration_key),
                        res.getBoolean(R.bool.pref_vibration_default));
        final boolean soundbackPref =
                mPrefs.getBoolean(getString(R.string.pref_soundback_key),
                        res.getBoolean(R.bool.pref_soundback_default));
        final String soundbackVolumePref = mPrefs.getString(getString(R.string.pref_soundback_volume_key), res.getString(R.string.pref_soundback_volume_default));
        final int volumePref = Integer.parseInt(soundbackVolumePref);

        mFeedbackController.setHapticEnabled(vibrationPref);
        mFeedbackController.setAuditoryEnabled(soundbackPref);
        mFeedbackController.setVolume(volumePref);

        final boolean bluetoothPref =
                mPrefs.getBoolean(getString(R.string.pref_bluetooth_key),
                        res.getBoolean(R.bool.pref_bluetooth_default));

        mSpeechController.setBluetoothEnabled(bluetoothPref);

        // Load custom resource mappings from preferences.
        mResourceMapper.loadFromPreferences(this, mPrefs);

        setProximitySensorState(mSilenceOnProximity);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            LogUtils.log(TalkBackService.class, Log.ERROR, "Dropped event: Received null event");
            return;
        }

        if (!mInfrastructureInitialized) {
            LogUtils.log(TalkBackService.class, Log.WARN, "Dropped event: No TTS instance found");
            return;
        }

        if (Utils.accessibilityEventEquals(mLastSpokenEvent, event)) {
            LogUtils.log(TalkBackService.class, Log.VERBOSE, "Dropped event: Received duplicate "
                    + "event");
            return;
        }

        final int eventType = event.getEventType();

        // Cache notifications until touch exploration is over.
        if (mIsUserTouchExploring
                && eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            cacheNotificationEvent(event);
            LogUtils.log(TalkBackService.class, Log.VERBOSE, "Cached event: User is busy touch "
                    + "exploring");
            return;
        }

        // If this is a phone, determine if the device should speak.
        if (mDeviceIsPhone) {
            if (!isSilencedByRinger()) {
                return;
            }

            final boolean speakCallerId =
                    mAnnounceCallerId
                            && mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_RINGING;

            if (!mSpeakWhenScreenOff && mRingerModeAndScreenMonitor.isScreenOff() && !speakCallerId) {
                // Cache notifications when the screen is off.
                if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                    cacheNotificationEvent(event);
                    LogUtils.log(TalkBackService.class, Log.VERBOSE, "Cached event: Screen is off");
                } else {
                    LogUtils.log(TalkBackService.class, Log.VERBOSE, "Dropped event: Screen "
                            + "is off");
                }
                return;
            }
        }

        // Manage touch exploration state.
        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START) {
            mIsUserTouchExploring = true;
        } else if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            mIsUserTouchExploring = false;
            speakCachedNotificationEvents();
        }

        // Manage cached last spoken event.
        if (mLastSpokenEvent != null) {
            mLastSpokenEvent.recycle();
        }
        mLastSpokenEvent = AccessibilityEvent.obtain(event);

        synchronized (mEventQueue) {
            mEventQueue.add(event);
            mSpeechHandler.postSpeak(event);
        }
        
        for (EventProcessor eventProcessor : mEventProcessors) {
            eventProcessor.process(event);
        }
    }

    /**
     * @return {@code true} if the device should speak based on the ringer state.
     */
    private boolean isSilencedByRinger() {
        final int ringerMode = mAudioManager.getRingerMode();
        
        // Don't speak depending on the ringer preference and mode.
        if (mRingerPref == PREF_RINGER_NOT_SILENT_OR_VIBRATE
                && (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_SILENT)) {
            LogUtils.log(TalkBackService.class, Log.VERBOSE, "Dropped event: Ringer is "
                    + "silent or on vibrate");
            return  false;
        } else if (mRingerPref == PREF_RINGER_NOT_SILENT
                && ringerMode == AudioManager.RINGER_MODE_SILENT) {
            LogUtils.log(TalkBackService.class, Log.VERBOSE, "Dropped event: Ringer is silent");
            return  false;
        }
            return true;
    }

    /**
     * Cache a notification event so that we can speak it later. Use this when
     * speaking the notification directly would be disruptive (when the screen
     * is off or during touch exploration).
     * 
     * @param event An AccessibilityEvent of TYPE_NOTIFICATION_STATE_CHANGED
     */
    private void cacheNotificationEvent(AccessibilityEvent event) {
        final String text = Utils.getEventText(this, event).toString();
        final Parcelable parcelable = event.getParcelableData();

        if (!(parcelable instanceof Notification)) {
            return;
        }

        final Notification notification = (Notification) parcelable;
        final int icon = notification.icon;

        // Don't record phone calls in the cache because we get
        // missed call anyway.
        if (icon == NotificationType.ICON_PHONE_CALL) {
            return;
        }

        final NotificationType type = NotificationType.getNotificationTypeFromIcon(icon);

        updateNotificationCache(type, text);
    }

    /**
     * Speaks cached notification events.
     */
    private void speakCachedNotificationEvents() {
        final String notificationSummary = mNotificationCache.getFormattedSummary();

        if (!TextUtils.isEmpty(notificationSummary)) {
            final StringBuilder text = new StringBuilder();
            text.append(getString(R.string.value_notification_summary));
            text.append(" ");
            text.append(mNotificationCache.getFormattedSummary());

            mSpeechController.cleanUpAndSpeak(text, SpeechController.QUEUING_MODE_INTERRUPT);
        }

        mNotificationCache.removeNotificationsAll();
    }

    @Override
    public void onInterrupt() {
        mSpeechController.interrupt();
        mFeedbackController.interrupt();
    }

    /**
     * Processes an <code>event</code> by asking the {@link SpeechRuleProcessor}
     * to match it against its rules and in case an utterance is generated it is
     * spoken. This method is responsible for recycling of the processed event.
     * 
     * @param event The event to process.
     */
    private void processAndRecycleEvent(AccessibilityEvent event) {
        LogUtils.log(TalkBackService.class, Log.DEBUG, "Processing event : %s", event.toString());

        final Bundle filterArgs = new Bundle();
        final Bundle formatterArgs = new Bundle();
        final Utterance utterance = Utterance.obtain();

        if (!mSpeechRuleProcessor.processEvent(event, utterance, filterArgs, formatterArgs)) {
            // Failed to match event to a rule, so the utterance is empty.
            utterance.recycle();
            return;
        }

        final Bundle metadata = utterance.getMetadata();
        final float earconRate = metadata.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);

        for (int resId : utterance.getEarcons()) {
            mFeedbackController.playSound(resId, earconRate);
        }

        for (int resId : utterance.getVibrationPatterns()) {
            mFeedbackController.playVibration(resId);
        }

        for (int prefId : utterance.getCustomEarcons()) {
            final int resId = mResourceMapper.getResourceIdForPreference(prefId);

            if (resId > 0) {
                mFeedbackController.playSound(resId, earconRate);
            }
        }

        for (int prefId : utterance.getCustomVibrations()) {
            final int resId = mResourceMapper.getResourceIdForPreference(prefId);

            if (resId > 0) {
                mFeedbackController.playVibration(resId);
            }
        }

        if (TextUtils.isEmpty(utterance.getText())) {
            // No text in the utterance, so don't do any further processing.
            utterance.recycle();
            return;
        }

        final int queueMode = computeQueuingMode(utterance, event);
        final StringBuilder text = utterance.getText();

        mSpeechController.cleanUpAndSpeak(text, queueMode);

        utterance.recycle();
        event.recycle();
    }

    /**
     * Computes the queuing mode for the current utterance.
     * 
     * @param utterance
     * @return A queuing mode, one of:
     *         <ul>
     *         <li>{@link SpeechController#QUEUING_MODE_INTERRUPT}
     *         <li>{@link SpeechController#QUEUING_MODE_QUEUE}
     *         <li>{@link SpeechController#QUEUING_MODE_UNINTERRUPTIBLE}
     *         </ul>
     */
    private int computeQueuingMode(Utterance utterance, AccessibilityEvent event) {
        final Bundle metadata = utterance.getMetadata();
        final int eventType = event.getEventType();
        final int mode;

        // Always collapse events of the same type.
        if (mLastEventType == eventType) {
            return SpeechController.QUEUING_MODE_INTERRUPT;
        }

        mLastEventType = eventType;

        return metadata.getInt(Utterance.KEY_METADATA_QUEUING,
                SpeechController.QUEUING_MODE_INTERRUPT);
    }

    /**
     * Updates the {@link NotificationCache}. If a notification is present in
     * the cache it is removed, otherwise it is added.
     * 
     * @param type The type of the notification.
     * @param text The notification text to be cached.
     */
    private void updateNotificationCache(NotificationType type, String text) {
        // if the cache has the notification - remove, otherwise add it
        if (!mNotificationCache.removeNotification(type, text)) {
            mNotificationCache.addNotification(type, text);
        }
    }

    private void setProximitySensorState(boolean enabled) {
        if (mProximitySensor == null) {
            if (!mSilenceOnProximity || !mDeviceIsPhone || !enabled) {
                // If we're not supposed to be using the proximity sensor, or if
                // we're supposed to be turning it off, then don't do anything.
                return;
            } else {
                // Otherwise, ensure that the proximity sensor is initialized.
                mProximitySensor = new ProximitySensor(this, true);
                mProximitySensor.setProximityChangeListener(mProximityChangeListener);
            }
        }

        // Otherwise, start only if we're supposed to silence on proximity.
        if (enabled && mSilenceOnProximity) {
            mProximitySensor.start();
        } else {
            mProximitySensor.stop();
        }
    }

    /**
     * Reloads preferences whenever their values change.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                        String key) {
                    reloadPreferences();
                }
            };

    /**
     * Stops the TTS engine when the proximity sensor is close.
     */
    private final ProximitySensor.ProximityChangeListener mProximityChangeListener =
            new ProximityChangeListener() {
                @Override
                public void onProximityChanged(boolean close) {
                    // Stop feedback if the user is close to the sensor.
                    if (close) {
                        mSpeechController.interrupt();
                        mFeedbackController.interrupt();
                    }
                }
            };

    /**
     * {@link BroadcastReceiver} for receiving updates for our context - device
     * state
     */
    private class RingerModeAndScreenMonitor extends BroadcastReceiver {
        private static final String SEPARATOR = " ";
        
        /** The intent filter to match phone state changes. */
        private final IntentFilter mPhoneStateChangeFilter = new IntentFilter();
        
        /** Whether the screen is currently off. */
        private boolean mScreenIsOff;

        /** When the screen was last turned on. */
        private long mTimeScreenOn;

        /**
         * Creates a new instance.
         */
        public RingerModeAndScreenMonitor() {
            mPhoneStateChangeFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_ON);
            mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_OFF);
            mPhoneStateChangeFilter.addAction(Intent.ACTION_USER_PRESENT);

            mScreenIsOff = false;
            mTimeScreenOn = SystemClock.uptimeMillis();
        }

        public IntentFilter getFilter() {
            return mPhoneStateChangeFilter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mInfrastructureInitialized) {
                LogUtils.log(RingerModeAndScreenMonitor.class, Log.WARN,
                        "Service not initialized during broadcast.");
                return;
            }

            final String action = intent.getAction();

            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                handleRingerModeChanged();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                handleScreenOn();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                handleScreenOff();
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                handleDeviceUnlocked();
            } else {
                LogUtils.log(TalkBackService.class, Log.WARN,
                        "Registered for but not handling action " + action);
            }
        }
        
        public boolean isScreenOff() {
            return mScreenIsOff;
        }

        /**
         * Handles when the device is unlocked. Just speaks "unlocked."
         */
        private void handleDeviceUnlocked() {
            final StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.value_device_unlocked));
            builder.append(SEPARATOR);
            
            mSpeechController.cleanUpAndSpeak(builder, SpeechController.QUEUING_MODE_UNINTERRUPTIBLE);
        }

        /**
         * Handles when the screen is turned off. Announces "screen off" and
         * suspends the proximity sensor.
         */
        private void handleScreenOff() {
            mScreenIsOff = true;

            // TODO(alanv): If we're allowed to speak when the screen if off,
            // should we still suspend the proximity sensor?
            setProximitySensorState(false);

            if (mTelephonyManager != null
                    && mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                // Don't announce screen off if we're in a call.
                return;
            }

            if (SystemClock.uptimeMillis() - mTimeScreenOn < 2000) {
                // Don't speak anything, let above notification continue.
                return;
            }

            final StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.value_screen_off));
            builder.append(SEPARATOR);
            appendRingerStateAnouncement(builder);
            
            mSpeechController.cleanUpAndSpeak(builder, SpeechController.QUEUING_MODE_INTERRUPT);
        }

        /**
         * Handles when the screen is turned off. Announces the current time,
         * any cached notifications, and the current ringer state.
         */
        private void handleScreenOn() {
            mScreenIsOff = false;
            mTimeScreenOn = SystemClock.uptimeMillis();

            setProximitySensorState(true);

            if (mTelephonyManager != null
                    && mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                // Don't announce screen on if we're in a call.
                return;
            }

            final StringBuilder builder = new StringBuilder();
            appendCurrentTimeAnnouncement(builder);
            appendCachedNotificationSummary(builder);
            appendRingerStateAnouncement(builder);
            
            mNotificationCache.removeNotificationsAll();
            
            mSpeechController.cleanUpAndSpeak(builder, SpeechController.QUEUING_MODE_INTERRUPT);
        }

        /**
         * Handles when the ringer mode (ex. volume) changes. Announces the
         * current ringer state.
         */
        private void handleRingerModeChanged() {
            final StringBuilder text = new StringBuilder();
            appendRingerStateAnouncement(text);
            
            mSpeechController.cleanUpAndSpeak(text, SpeechController.QUEUING_MODE_INTERRUPT);
        }

        /**
         * Appends the current time announcement to a {@link StringBuilder}.
         * 
         * @param builder The string to append to.
         */
        private void appendCurrentTimeAnnouncement(StringBuilder builder) {
            int timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;

            if (DateFormat.is24HourFormat(TalkBackService.asContext())) {
                timeFlags |= DateUtils.FORMAT_24HOUR;
            }

            builder.append(DateUtils.formatDateTime(asContext(), System.currentTimeMillis(), timeFlags));
            builder.append(SEPARATOR);
        }

        /**
         * Appends the notification summary to a {@link StringBuilder}.
         * 
         * @param builder The string to append to.
         */
        private void appendCachedNotificationSummary(StringBuilder builder) {
            builder.append(mNotificationCache.getFormattedSummary());
            builder.append(SEPARATOR);
        }

        /**
         * Appends the ringer state announcement to a {@link StringBuilder}.
         * 
         * @param builder The string to append to.
         */
        private void appendRingerStateAnouncement(StringBuilder builder) {
            final int ringerMode = mAudioManager.getRingerMode();
            final String announcement;

            switch (ringerMode) {
                case AudioManager.RINGER_MODE_SILENT:
                    announcement = getString(R.string.value_ringer_silent);
                    break;
                case AudioManager.RINGER_MODE_VIBRATE:
                    announcement = getString(R.string.value_ringer_vibrate);
                    break;
                case AudioManager.RINGER_MODE_NORMAL:
                    final int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                    final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                    final int volumePercent = 5 * (int) (20 * currentVolume / maxVolume + 0.5);

                    announcement = getString(R.string.template_ringer_volume, volumePercent);
                    break;
                default:
                    LogUtils.log(TalkBackService.class, Log.ERROR, "Unknown ringer mode: %d",
                            ringerMode);
                    return;
            }

            builder.append(announcement);
            builder.append(SEPARATOR);
        }
    }

    /**
     * {@link BroadcastReceiver} for detecting incoming calls.
     */
    private class CallStateMonitor extends BroadcastReceiver {
        private final IntentFilter mPhoneStateChangedFilter = new IntentFilter(
                TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        public IntentFilter getFilter() {
            return mPhoneStateChangedFilter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!mInfrastructureInitialized) {
                LogUtils.log(CallStateMonitor.class, Log.WARN, "Service not initialized during "
                        + "broadcast.");
                return;
            }

            final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                mSpeechController.interrupt();
                mFeedbackController.interrupt();
            }
        }
    }

    private class TalkBackHandler extends Handler {
        /** Speak action. */
        private static final int WHAT_SPEAK = 1;
        
        /** Timeout before speaking. */
        private static final long TIMEOUT_SPEAK = 0;

        @Override
        public void handleMessage(Message message) {
            final int what = message.what;

            switch (what) {
                case WHAT_SPEAK:
                    processAllEvents();
                    break;
            }
        }

        /**
         * Attempts to process all events in the queue.
         */
        private void processAllEvents() {
            while (true) {
                final AccessibilityEvent event;

                synchronized (mEventQueue) {
                    if (mEventQueue.isEmpty()) {
                        return;
                    }

                    event = mEventQueue.remove(0);
                }

                processAndRecycleEvent(event);
            }
        }

        /**
         * Sends {@link #WHAT_SPEAK} to the speech handler. This method cancels
         * the old message (if such exists) since it is no longer relevant.
         * 
         * @param event The event to speak.
         */
        public void postSpeak(AccessibilityEvent event) {
            final Message message = obtainMessage(WHAT_SPEAK);

            removeMessages(WHAT_SPEAK);
            sendMessageDelayed(message, TIMEOUT_SPEAK);
        }
    }
    
    /**
     * Interface for passive event processors.
     */
    interface EventProcessor {
        public void process(AccessibilityEvent event);
    }
    
    /**
     * Interface for listeners for the TalkBack initialization state.
     */
    interface InfrastructureStateListener {
        public void onInfrastructureStateChange(boolean isInitialized);
    }
}
