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
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;

import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

// TODO(caseyburkhardt): Refactor this class into two separate receivers
// with listener interfaces. This will remove the need to hold dependencies
// and call into other classes.
/**
 * {@link BroadcastReceiver} for receiving updates for our context - device
 * state
 */
class RingerModeAndScreenMonitor extends BroadcastReceiver {
    /** The intent filter to match phone and screen state changes. */
    private static final IntentFilter STATE_CHANGE_FILTER = new IntentFilter();

    static {
        STATE_CHANGE_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_ON);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_USER_PRESENT);
    }

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final ShakeDetector mShakeDetector;
    private final AudioManager mAudioManager;
    private final MappedFeedbackController mFeedbackController;
    private final TelephonyManager mTelephonyManager;

    /** Whether the screen is currently off. */
    private boolean mScreenIsOff;

    /** The current ringer mode. */
    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;

    /**
     * Creates a new instance.
     */
    public RingerModeAndScreenMonitor(TalkBackService context) {
        mContext = context;
        mSpeechController = context.getSpeechController();
        mShakeDetector = context.getShakeDetector();
        mFeedbackController = MappedFeedbackController.getInstance();

        mAudioManager = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);

        mScreenIsOff = false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mHandler.onReceive(intent);
    }

    private void internalOnReceive(Intent intent) {
        if (!TalkBackService.isServiceActive()) {
            LogUtils.log(RingerModeAndScreenMonitor.class, Log.WARN,
                    "Service not initialized during broadcast.");
            return;
        }

        final String action = intent.getAction();

        if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
            final int ringerMode = intent.getIntExtra(
                    AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL);

            handleRingerModeChanged(ringerMode);
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

    public IntentFilter getFilter() {
        return STATE_CHANGE_FILTER;
    }

    public boolean isScreenOff() {
        return mScreenIsOff;
    }

    /**
     * Handles when the device is unlocked. Just speaks "unlocked."
     */
    private void handleDeviceUnlocked() {
        final String text = mContext.getString(R.string.value_device_unlocked);

        mSpeechController.speak(text, SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, null);
    }

    /**
     * Handles when the screen is turned off. Announces "screen off" and
     * suspends the proximity sensor.
     */
    @SuppressWarnings("deprecation")
    private void handleScreenOff() {
        mScreenIsOff = true;

        mSpeechController.setScreenIsOn(false);

        // Don't announce screen off if we're in a call.
        if ((mTelephonyManager != null)
                && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
            return;
        }

        final SpannableStringBuilder builder =
                new SpannableStringBuilder(mContext.getString(R.string.value_screen_off));

        appendRingerStateAnouncement(builder);

        if (mShakeDetector != null) {
            mShakeDetector.pausePolling();
        }

        if (mRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            final int soundId;
            final float volume;
            final float musicVolume = getStreamVolume(AudioManager.STREAM_MUSIC);
            if ((musicVolume > 0)
                && (mAudioManager.isWiredHeadsetOn()
                    ||  mAudioManager.isBluetoothA2dpOn())) {
                // Play the ringer beep on the default (music) stream to avoid
                // issues with ringer audio (e.g. no speech on ICS and
                // interruption of music on JB). Adjust playback volume to
                // compensate for music volume.
                final float ringVolume = getStreamVolume(AudioManager.STREAM_RING);
                soundId = R.id.sounds_volume_beep;
                volume = Math.min(1.0f, (ringVolume / musicVolume));
            } else {
                // Normally we'll play the volume beep on the ring stream.
                soundId = R.id.sounds_volume_beep_ring;
                volume = 1.0f;
            }

            mFeedbackController.playAuditory(soundId, 1.0f /* rate */, volume, 0.0f /* pan */);
        }

        mSpeechController.speak(
                builder, SpeechController.QUEUE_MODE_INTERRUPT, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    /**
     * Handles when the screen is turned on. Announces the current time and the
     * current ringer state.
     */
    private void handleScreenOn() {
        mScreenIsOff = false;

        // TODO: This doesn't look right. Should probably be using a listener.
        mSpeechController.setScreenIsOn(true);

        if ((mTelephonyManager != null)
                && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
            // Don't announce screen on if we're in a call.
            return;
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();
        appendCurrentTimeAnnouncement(builder);

        if (mShakeDetector != null) {
            mShakeDetector.resumePolling();
        }

        mSpeechController.speak(builder, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
    }

    /**
     * Handles when the ringer mode (ex. volume) changes. Announces the current
     * ringer state.
     */
    private void handleRingerModeChanged(int ringerMode) {
        mRingerMode = ringerMode;

        // Don't announce ringer mode changes if the volume monitor is active.
        if (Build.VERSION.SDK_INT >= VolumeMonitor.MIN_API_LEVEL) {
            return;
        }

        final SpannableStringBuilder text = new SpannableStringBuilder();
        appendRingerStateAnouncement(text);

        mSpeechController.speak(text, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
    }

    /**
     * Appends the current time announcement to a {@link StringBuilder}.
     *
     * @param builder The string to append to.
     */
    @SuppressWarnings("deprecation")
    private void appendCurrentTimeAnnouncement(SpannableStringBuilder builder) {
        int timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;

        if (DateFormat.is24HourFormat(mContext)) {
            timeFlags |= DateUtils.FORMAT_24HOUR;
        }

        final CharSequence dateTime =
                DateUtils.formatDateTime(mContext, System.currentTimeMillis(), timeFlags);

        StringBuilderUtils.appendWithSeparator(builder, dateTime);
    }

    /**
     * Appends the ringer state announcement to a {@link StringBuilder}.
     *
     * @param builder The string to append to.
     */
    private void appendRingerStateAnouncement(SpannableStringBuilder builder) {
        if (mTelephonyManager == null) {
            return;
        }

        final String announcement;

        switch (mRingerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                announcement = mContext.getString(R.string.value_ringer_silent);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                announcement = mContext.getString(R.string.value_ringer_vibrate);
                break;
            default:
                LogUtils.log(TalkBackService.class, Log.ERROR, "Unknown ringer mode: %d",
                        mRingerMode);
                return;
        }

        StringBuilderUtils.appendWithSeparator(builder, announcement);
    }

    /**
     * Returns the volume a stream as a fraction of its maximum volume.
     *
     * @param streamType The stream type for which to return the volume.
     * @return The stream volume as a fraction of its maximum volume.
     */
    private float getStreamVolume(int streamType) {
        final int currentVolume = mAudioManager.getStreamVolume(streamType);
        final int maxVolume = mAudioManager.getStreamMaxVolume(streamType);
        return (currentVolume / (float) maxVolume);
    }

    /**
     * Handler to transfer broadcasts to the service thread.
     */
    private final RingerModeHandler mHandler = new RingerModeHandler(this);

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
