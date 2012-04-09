/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.utils.InfrastructureStateListener;
import com.google.android.marvin.utils.ProximitySensor;
import com.google.android.marvin.utils.ProximitySensor.ProximityChangeListener;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * {@link BroadcastReceiver} for receiving updates for our context - device
 * state
 */
class RingerModeAndScreenMonitor extends BroadcastReceiver implements InfrastructureStateListener {
    private final Context mContext;

    private final PreferenceFeedbackController mFeedbackController;

    private final SpeechController mSpeechController;

    private final AudioManager mAudioManager;

    private final TelephonyManager mTelephonyManager;

    private final NotificationCache mNotificationCache;

    /** Proximity sensor for implementing "shut up" functionality. */
    private ProximitySensor mProximitySensor;

    /** The intent filter to match phone state changes. */
    private final IntentFilter mPhoneStateChangeFilter = new IntentFilter();

    /** Handler to transfer broadcasts to the service thread. */
    private final BroadcastHandler mHandler = new BroadcastHandler() {
        @Override
        public void handleOnReceive(Intent intent) {
            internalOnReceive(intent);
        }
    };

    /** Whether the screen is currently off. */
    private boolean mScreenIsOff;

    /** When the screen was last turned on. */
    private long mTimeScreenOn;

    /** Whether to use the proximity sensor to silence speech. */
    private boolean mSilenceOnProximity;

    /** Whether the infrastructure has been initialized. */
    private boolean mInfrastructureInitialized;

    /** The current ringer mode. */
    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;

    /**
     * Creates a new instance.
     */
    public RingerModeAndScreenMonitor(Context context,
            PreferenceFeedbackController feedbackController, SpeechController speechController,
            AudioManager audioManager, TelephonyManager telephonyManager,
            NotificationCache notificationCache) {
        mContext = context;
        mFeedbackController = feedbackController;
        mSpeechController = speechController;
        mAudioManager = audioManager;
        mTelephonyManager = telephonyManager;
        mNotificationCache = notificationCache;

        mPhoneStateChangeFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_ON);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_USER_PRESENT);

        mScreenIsOff = false;
        mTimeScreenOn = SystemClock.uptimeMillis();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mHandler.onReceive(intent);
    }

    private void internalOnReceive(Intent intent) {
        if (!mInfrastructureInitialized) {
            LogUtils.log(RingerModeAndScreenMonitor.class, Log.WARN,
                    "Service not initialized during  broadcast.");
            return;
        }

        final String action = intent.getAction();

        if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
            mRingerMode =
                    intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,
                            AudioManager.RINGER_MODE_NORMAL);

            handleRingerModeChanged();
        } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            handleScreenOn();
        } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            handleScreenOff();
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            handleDeviceUnlocked();
        } else {
            LogUtils.log(TalkBackService.class, Log.WARN,
                    "Registered for but not handling action %s", action);
        }
    }

    @Override
    public void onInfrastructureStateChange(Context context, boolean isInitialized) {
        mInfrastructureInitialized = isInitialized;
    }

    public int getRingerMode() {
        return mRingerMode;
    }

    public IntentFilter getFilter() {
        return mPhoneStateChangeFilter;
    }

    public boolean isScreenOff() {
        return mScreenIsOff;
    }

    public void setSilenceOnProximity(boolean silenceOnProximity) {
        mSilenceOnProximity = silenceOnProximity;
        setProximitySensorState(mSilenceOnProximity);
    }

    public void shutdown() {
        setProximitySensorState(false);
    }

    /**
     * Handles when the device is unlocked. Just speaks "unlocked."
     */
    private void handleDeviceUnlocked() {
        final StringBuilder text =
                StringBuilderUtils.appendWithSeparator(null,
                        mContext.getString(R.string.value_device_unlocked));

        mSpeechController.cleanUpAndSpeak(text, QueuingMode.UNINTERRUPTIBLE, null);
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

        // Don't announce screen off if we're in a call.
        if (mTelephonyManager != null
                && mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            return;
        }

        // Don't speak anything if we're probably already speaking.
        if (SystemClock.uptimeMillis() - mTimeScreenOn < 2000) {
            return;
        }

        final StringBuilder builder =
                StringBuilderUtils.appendWithSeparator(null,
                        mContext.getString(R.string.value_screen_off));

        appendRingerStateAnouncement(builder);

        mSpeechController.cleanUpAndSpeak(builder, QueuingMode.INTERRUPT, null);
    }

    /**
     * Handles when the screen is turned off. Announces the current time, any
     * cached notifications, and the current ringer state.
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

        mSpeechController.cleanUpAndSpeak(builder, QueuingMode.INTERRUPT, null);
    }

    /**
     * Handles when the ringer mode (ex. volume) changes. Announces the current
     * ringer state.
     */
    private void handleRingerModeChanged() {
        final StringBuilder text = new StringBuilder();
        appendRingerStateAnouncement(text);

        mSpeechController.cleanUpAndSpeak(text, QueuingMode.INTERRUPT, null);
    }

    /**
     * Appends the current time announcement to a {@link StringBuilder}.
     * 
     * @param builder The string to append to.
     */
    private void appendCurrentTimeAnnouncement(StringBuilder builder) {
        int timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;

        if (DateFormat.is24HourFormat(mContext)) {
            timeFlags |= DateUtils.FORMAT_24HOUR;
        }

        final CharSequence dateTime =
                DateUtils.formatDateTime(mContext, System.currentTimeMillis(), timeFlags);

        StringBuilderUtils.appendWithSeparator(builder, dateTime);
    }

    /**
     * Appends the notification summary to a {@link StringBuilder}.
     * 
     * @param builder The string to append to.
     */
    private void appendCachedNotificationSummary(StringBuilder builder) {
        final CharSequence notificationSummary = mNotificationCache.getFormattedSummary();
        mNotificationCache.clear();

        StringBuilderUtils.appendWithSeparator(builder, notificationSummary);
    }

    private void setProximitySensorState(boolean enabled) {
        final boolean silenceOnProximity = mSilenceOnProximity;

        if (mProximitySensor == null) {
            if (!silenceOnProximity || !enabled) {
                // If we're not supposed to be using the proximity sensor, or if
                // we're supposed to be turning it off, then don't do anything.
                return;
            }

            // Otherwise, ensure that the proximity sensor is initialized.
            mProximitySensor = new ProximitySensor(mContext, true);
            mProximitySensor.setProximityChangeListener(mProximityChangeListener);
        }

        // Otherwise, start only if we're supposed to silence on proximity.
        if (enabled && silenceOnProximity) {
            mProximitySensor.start();
        } else {
            mProximitySensor.stop();
        }
    }

    /**
     * Appends the ringer state announcement to a {@link StringBuilder}.
     * 
     * @param builder The string to append to.
     */
    private void appendRingerStateAnouncement(StringBuilder builder) {
        final String announcement;

        switch (mRingerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                announcement = mContext.getString(R.string.value_ringer_silent);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                announcement = mContext.getString(R.string.value_ringer_vibrate);
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                final int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                final int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
                final int volumePercent = 5 * (int) (20 * currentVolume / maxVolume + 0.5);

                announcement = mContext.getString(R.string.template_ringer_volume, volumePercent);
                break;
            default:
                LogUtils.log(TalkBackService.class, Log.ERROR, "Unknown ringer mode: %d",
                        mRingerMode);
                return;
        }

        StringBuilderUtils.appendWithSeparator(builder, announcement);
    }

    /**
     * Stops the TTS engine when the proximity sensor is close.
     */
    private final ProximitySensor.ProximityChangeListener mProximityChangeListener =
            new ProximityChangeListener() {
                @Override
                public void onProximityChanged(boolean close) {
                    // Stop feedback if the user is close to the sensor.
                    if (close) {
                        mSpeechController.stopAll();
                        mFeedbackController.interrupt();
                    }
                }
            };
}
