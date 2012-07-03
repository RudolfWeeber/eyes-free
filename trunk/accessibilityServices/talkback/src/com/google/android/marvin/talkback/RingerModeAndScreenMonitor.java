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

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.InfrastructureStateListener;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * {@link BroadcastReceiver} for receiving updates for our context - device
 * state
 */
class RingerModeAndScreenMonitor extends BroadcastReceiver implements InfrastructureStateListener {
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final AudioManager mAudioManager;
    private final TelephonyManager mTelephonyManager;
    private final NotificationCache mNotificationCache;

    /** The intent filter to match phone state changes. */
    private final IntentFilter mPhoneStateChangeFilter = new IntentFilter();

    /** Handler to transfer broadcasts to the service thread. */
    private final RingerModeHandler mHandler = new RingerModeHandler(this);

    /** Whether the screen is currently off. */
    private boolean mScreenIsOff;

    /** Whether the infrastructure has been initialized. */
    private boolean mInfrastructureInitialized;

    /** The current ringer mode. */
    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;

    /**
     * Creates a new instance.
     */
    public RingerModeAndScreenMonitor(Context context, SpeechController speechController,
            NotificationCache notificationCache) {
        mContext = context;
        mSpeechController = speechController;
        mNotificationCache = notificationCache;

        mAudioManager = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);

        mPhoneStateChangeFilter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_ON);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mPhoneStateChangeFilter.addAction(Intent.ACTION_USER_PRESENT);

        mScreenIsOff = false;
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

        mSpeechController.setScreenIsOn(false);

        // Don't announce screen off if we're in a call.
        if ((mTelephonyManager != null)
                && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
            return;
        }

        final StringBuilder builder =
                StringBuilderUtils.appendWithSeparator(null,
                        mContext.getString(R.string.value_screen_off));

        // Only append ringer state if the device has a phone.
        if ((mTelephonyManager != null)
                && (mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE)) {
            appendRingerStateAnouncement(builder);
        }

        mSpeechController.cleanUpAndSpeak(builder, QueuingMode.INTERRUPT, null);
    }

    /**
     * Handles when the screen is turned off. Announces the current time, any
     * cached notifications, and the current ringer state.
     */
    private void handleScreenOn() {
        mScreenIsOff = false;

        // TODO: This doesn't look right. Should probably be using a listener.
        mSpeechController.setScreenIsOn(true);

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
     *
     * TODO: Does this duplicate functionality in VolumeMonitor?
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

    private static class RingerModeHandler extends BroadcastHandler<RingerModeAndScreenMonitor> {
        public RingerModeHandler(RingerModeAndScreenMonitor parent) {
            super(parent);
        }

        @Override
        public void handleOnReceive(Intent intent, RingerModeAndScreenMonitor parent) {
            parent.internalOnReceive(intent);
        }
    }
}
