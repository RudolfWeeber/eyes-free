/*
 * Copyright (C) 2012 Google Inc.
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
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.view.WindowManager;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.googlecode.eyesfree.compat.media.AudioManagerCompatUtils;
import com.googlecode.eyesfree.widget.SimpleOverlay;

/**
 * Listens for and responds to volume changes.
 */
public class VolumeMonitor {
    /** Pseudo stream type for master volume. */
    private static final int STREAM_MASTER = -100;

    /** Minimum API version required for this class to function. */
    // TODO(alanv): Figure out if this works on API 10 (Honeycomb 3.0)
    public static final int MIN_API_LEVEL = 14;

    private Context mContext;
    private SpeechController mSpeechController;
    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private VolumeOverlay mVolumeOverlay;

    /** The stream type currently being controlled. */
    private int mCurrentStream = -1;

    /**
     * Creates and initializes a new volume monitor.
     *
     * @param context The parent context.
     * @param speechController The speech controller.
     */
    public VolumeMonitor(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mVolumeOverlay = new VolumeOverlay(context);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AudioManagerCompatUtils.VOLUME_CHANGED_ACTION);
        intentFilter.addAction(AudioManagerCompatUtils.MASTER_VOLUME_CHANGED_ACTION);

        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
    }

    /**
     * Shuts down and disables the volume monitor.
     */
    public void shutdown() {
        mContext.unregisterReceiver(mBroadcastReceiver);
        mContext = null;

        mSpeechController = null;
    }

    /**
     * Called after volume changes. Handles acquiring control of the current
     * stream and providing feedback.
     *
     * @param streamType The stream type constant.
     * @param volume The current volume.
     * @param prevVolume The previous volume.
     */
    private void internalOnVolumeChanged(int streamType, int volume, int prevVolume) {
        if (mCurrentStream < 0) {
            // If the current stream hasn't been set, acquire control.
            mCurrentStream = streamType;
            AudioManagerCompatUtils.forceVolumeControlStream(mAudioManager, mCurrentStream);
            mVolumeOverlay.show();
            mHandler.onControlAcquired(streamType);
            return;
        }

        if (volume == prevVolume) {
            // Ignore ADJUST_SAME if we've already acquired control.
            return;
        }

        // Interrupt TTS is it's already speaking...
        mSpeechController.interrupt();
        mHandler.releaseControl();
    }

    /**
     * Called after control of a particular volume stream has been acquired and
     * the audio stream has had a chance to quiet down.
     *
     * @param streamType The stream type over which control has been acquired.
     */
    private void internalOnControlAcquired(int streamType) {
        final String streamName = getStreamName(streamType);
        final int volume = getStreamVolume(streamType);
        final String text = mContext.getString(
                R.string.template_stream_volume, streamName, volume);

        speakWithCompletion(text, mStartReleaseTimeout);
    }

    /**
     * Called after adjustments have been made and the user has not taken any
     * action for a certain duration. Announces the current volume and releases
     * control of the stream.
     */
    private void internalOnReleaseControl() {
        final int streamId = mCurrentStream;

        if (streamId < 0) {
            // Already released!
            return;
        }

        mCurrentStream = -1;
        AudioManagerCompatUtils.forceVolumeControlStream(mAudioManager, -1);

        final String streamName = getStreamName(streamId);
        final int volume = getStreamVolume(streamId);
        final String text = mContext.getString(
                R.string.template_stream_volume_set, streamName, volume);

        speakWithCompletion(text, mHideVolumeOverlay);
    }

    /**
     * Speaks text with a completion action, or just runs the completion action
     * if the volume monitor should be quiet.
     *
     * @param text The text to speak.
     * @param completedAction The action to run after speaking.
     */
    private void speakWithCompletion(String text, Runnable completedAction) {
        if ((mTelephonyManager != null)
                && (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE)) {
            // If the phone is busy, don't speak anything.
            mHandler.post(completedAction);
            return;
        }

        mSpeechController.cleanUpAndSpeak(text, QueuingMode.QUEUE, null, completedAction);
    }

    /**
     * Returns the localized stream name for a given stream type constant.
     *
     * @param streamType A stream type constant.
     * @return The localized stream name.
     */
    private String getStreamName(int streamType) {
        final int resId;

        switch (streamType) {
            case STREAM_MASTER:
                resId = R.string.value_stream_master;
                break;
            case AudioManager.STREAM_VOICE_CALL:
                resId = R.string.value_stream_voice_call;
                break;
            case AudioManager.STREAM_SYSTEM:
                resId = R.string.value_stream_system;
                break;
            case AudioManager.STREAM_RING:
                resId = R.string.value_stream_ring;
                break;
            case AudioManager.STREAM_MUSIC:
                resId = R.string.value_stream_music;
                break;
            case AudioManager.STREAM_ALARM:
                resId = R.string.value_stream_alarm;
                break;
            case AudioManager.STREAM_NOTIFICATION:
                resId = R.string.value_stream_notification;
                break;
            case AudioManagerCompatUtils.STREAM_DTMF:
                resId = R.string.value_stream_dtmf;
                break;
            default:
                return "";
        }

        return mContext.getString(resId);
    }

    /**
     * Returns the stream volume as a percentage.
     *
     * @param streamType A stream type constant.
     * @return The stream volume as a percentage.
     */
    private int getStreamVolume(int streamType) {
        final int currentVolume = mAudioManager.getStreamVolume(streamType);
        final int maxVolume = mAudioManager.getStreamMaxVolume(streamType);
        final int volumePercent = 5 * (int) (20 * currentVolume / maxVolume + 0.5);

        return volumePercent;
    }

    private final VolumeHandler mHandler = new VolumeHandler();

    /**
     * Runnable that hides the volume overlay. Used as a completion action for
     * the "volume set" utterance.
     */
    private final Runnable mHideVolumeOverlay = new Runnable() {
        @Override
        public void run() {
            mVolumeOverlay.hide();
        }
    };

    /**
     * Runnable that starts the "release control" timeout. Used as a completion
     * action for the "current volume" utterance.
     */
    private final Runnable mStartReleaseTimeout = new Runnable() {
        @Override
        public void run() {
            mHandler.releaseControl();
        }
    };

    /**
     * Broadcast receiver for system volume change actions.
     */
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (AudioManagerCompatUtils.VOLUME_CHANGED_ACTION.equals(action)) {
                final int type = intent.getIntExtra(
                        AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_TYPE, -1);
                final int value = intent.getIntExtra(
                        AudioManagerCompatUtils.EXTRA_VOLUME_STREAM_VALUE, -1);
                final int prevValue = intent.getIntExtra(
                        AudioManagerCompatUtils.EXTRA_PREV_VOLUME_STREAM_VALUE, -1);

                if (type < 0 || value < 0 || prevValue < 0) {
                    return;
                }

                mHandler.onVolumeChanged(type, value, prevValue);
            } else if (AudioManagerCompatUtils.MASTER_VOLUME_CHANGED_ACTION.equals(action)) {
                final int value = intent.getIntExtra(
                        AudioManagerCompatUtils.EXTRA_MASTER_VOLUME_VALUE, -1);
                final int prevValue = intent.getIntExtra(
                        AudioManagerCompatUtils.EXTRA_PREV_MASTER_VOLUME_VALUE, -1);

                if (value < 0 || prevValue < 0) {
                    return;
                }

                mHandler.onVolumeChanged(STREAM_MASTER, value, prevValue);
            }
        }
    };

    /**
     * Handler class for the volume monitor. Transfers volume broadcasts to the
     * service thread. Maintains timeout actions, including volume control
     * acquisition and release.
     */
    private class VolumeHandler extends Handler {
        /** Timeout in milliseconds before the volume control disappears. */
        private static final long RELEASE_CONTROL_TIMEOUT = 2000;

        /** Timeout in milliseconds before the audio channel is available. */
        private static final long ACQUIRED_CONTROL_TIMEOUT = 1000;

        private static final int MSG_VOLUME_CHANGED = 1;
        private static final int MSG_CONTROL = 2;
        private static final int MSG_RELEASE_CONTROL = 3;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_VOLUME_CHANGED: {
                    final Integer type = (Integer) msg.obj;
                    final int value = msg.arg1;
                    final int prevValue = msg.arg2;

                    internalOnVolumeChanged(type, value, prevValue);
                    break;
                }
                case MSG_CONTROL: {
                    final int streamType = msg.arg1;
                    final int volume = msg.arg2;

                    internalOnControlAcquired(streamType);
                    break;
                }
                case MSG_RELEASE_CONTROL: {
                    internalOnReleaseControl();
                    break;
                }
            }
        }

        /**
         * Starts the volume control release timeout.
         *
         * @see #internalOnReleaseControl
         */
        public void releaseControl() {
            removeMessages(MSG_CONTROL);
            removeMessages(MSG_RELEASE_CONTROL);

            final Message msg = obtainMessage(MSG_RELEASE_CONTROL);
            sendMessageDelayed(msg, RELEASE_CONTROL_TIMEOUT);
        }

        /**
         * Starts the volume control acquisition timeout.
         *
         * @param type The stream type.
         * @see #internalOnControlAcquired
         */
        public void onControlAcquired(int type) {
            removeMessages(MSG_CONTROL);
            removeMessages(MSG_RELEASE_CONTROL);

            // There is a small delay before we can speak.
            final Message msg = obtainMessage(MSG_CONTROL, type, 0);
            sendMessageDelayed(msg, ACQUIRED_CONTROL_TIMEOUT);
        }

        /**
         * Transfers volume broadcasts to the handler thread.
         *
         * @param type The stream type.
         * @param value The current volume index.
         * @param prevValue The previous volume index.
         */
        public void onVolumeChanged(int type, int value, int prevValue) {
            obtainMessage(MSG_VOLUME_CHANGED, value, prevValue, type).sendToTarget();
        }
    }

    /**
     * An overlay used to capture the volume button events.
     * <p>
     * <b>Warning:</b> When shown, this overlay will capture <b>all</a> key events.
     * </p>
     */
    private static class VolumeOverlay extends SimpleOverlay {
        public VolumeOverlay(Context context) {
            super(context);

            final WindowManager.LayoutParams params = getParams();
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            params.format = PixelFormat.OPAQUE;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            params.width = 0;
            params.height = 0;
            setParams(params);
        }
    }
}
