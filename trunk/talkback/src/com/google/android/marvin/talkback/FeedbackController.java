/*
 * Copyright (C) 2011 Google Inc.
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

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;

import java.util.Map;
import java.util.TreeMap;

/**
 * Provides auditory and haptic feedback.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
class FeedbackController {
    private static FeedbackController sInstance;

    /**
     * @return The shared instance of FeedbackController.
     */
    public static FeedbackController getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FeedbackController(context);
        }

        return sInstance;
    }

    /**
     * Default stream for audio feedback.
     */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    /**
     * Default volume for sound playback. Use 1.0f to match the current stream
     * volume.
     */
    private static final float DEFAULT_VOLUME = 1.0f;

    /**
     * Default rate for sound playback.
     */
    private static final float DEFAULT_RATE = 1.0f;

    /**
     * Number of channels to use in SoundPool for auditory icon feedback.
     */
    private static final int NUMBER_OF_CHANNELS = 10;

    /**
     * The parent context. Required for mapping resource identifiers to
     * resources.
     */
    private final Context mContext;

    /**
     * Vibration service used to play vibration patterns.
     */
    private final Vibrator mVibrator;

    /**
     * Sound pool used to play auditory icons.
     */
    private final SoundPool mSoundPool;

    /**
     * Whether haptic feedback is enabled.
     */
    private boolean mHapticEnabled = true;

    /**
     * Whether auditory feedback is enabled.
     */
    private boolean mAuditoryEnabled = true;

    /** Current volume (range 0..1). */
    private float mVolume = DEFAULT_VOLUME;

    private final Map<Integer, long[]> mResourceIdToVibrationPatternMap;
    private final Map<Integer, Integer> mResourceIdToSoundMap;

    /**
     * Constructs a new feedback controller.
     * 
     * @param context The parent context.
     */
    private FeedbackController(Context context) {
        mContext = context;
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mSoundPool = new SoundPool(NUMBER_OF_CHANNELS, DEFAULT_STREAM, 1);
        mResourceIdToSoundMap = new TreeMap<Integer, Integer>();
        mResourceIdToVibrationPatternMap = new TreeMap<Integer, long[]>();

        // Since the sound pool loads resources asynchronously, we need to play
        // resources immediately after they load.
        mSoundPool.setOnLoadCompleteListener(mOnLoadCompleteListener);
    }

    /**
     * Sets the current volume for auditory feedback.
     * 
     * @param volume Volume value (range 0..100).
     */
    public void setVolume(int volume) {
        mVolume = (Math.min(100, Math.max(0, volume)) / 100.0f);
    }

    /**
     * @param enabled Whether haptic feedback should be enabled.
     */
    public void setHapticEnabled(boolean enabled) {
        mHapticEnabled = enabled;
    }

    /**
     * @param enabled Whether auditory feedback should be enabled.
     */
    public void setAuditoryEnabled(boolean enabled) {
        mAuditoryEnabled = enabled;
    }

    /**
     * Stops all active feedback.
     */
    public void interrupt() {
        mVibrator.cancel();
        mSoundPool.autoPause();
    }

    /**
     * Releases resources associated with this feedback controller. It is good
     * practice to call this method when you're done using the controller.
     */
    public void shutdown() {
        mVibrator.cancel();
        mSoundPool.release();
    }

    /**
     * Plays the sound file specified by the given resource identifier at the
     * specified rate.
     * 
     * @param resId The sound file's resource identifier.
     * @param rate The rate at which to play back the sound, where 1.0 is normal
     *            speed and 0.5 is half-speed.
     * @return {@code true} if successful
     */
    public boolean playSound(int resId, float rate) {
        if (!mAuditoryEnabled) {
            return false;
        }

        Integer soundId = mResourceIdToSoundMap.get(resId);

        if (soundId == null) {
            soundId = mSoundPool.load(mContext, resId, 1);

            mResourceIdToSoundMap.put(resId, soundId);

            // Since we need to play the sound immediately after it loads, we
            // should just return true.
            return true;
        }

        final int stream = mSoundPool.play(soundId, mVolume, mVolume, 1, 0, rate);

        return (stream != 0);
    }

    /**
     * Plays the vibration pattern specified by the given resource identifier.
     * 
     * @param resId The vibration pattern's resource identifier.
     * @return {@code true} if successful
     */
    public boolean playVibration(int resId) {
        if (!mHapticEnabled) {
            return false;
        }

        long[] pattern = mResourceIdToVibrationPatternMap.get(resId);

        if (pattern == null) {
            final Resources resources = mContext.getResources();
            final int[] intPattern = resources.getIntArray(resId);

            if (intPattern == null) {
                return false;
            }

            pattern = new long[intPattern.length];

            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = intPattern[i];
            }

            mResourceIdToVibrationPatternMap.put(resId, pattern);
        }

        mVibrator.vibrate(pattern, -1);

        return true;
    }

    private final SoundPool.OnLoadCompleteListener mOnLoadCompleteListener =
            new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    if (status != 0) {
                        return;
                    }

                    soundPool.play(sampleId, mVolume, mVolume, 1, 0, DEFAULT_RATE);
                }
            };
}
