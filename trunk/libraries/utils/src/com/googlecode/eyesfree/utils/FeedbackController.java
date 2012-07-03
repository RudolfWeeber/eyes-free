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

package com.googlecode.eyesfree.utils;

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
public class FeedbackController {
    /** Default stream for audio feedback. */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    /**
     * Default volume for sound playback. Use 1.0f to match the current stream
     * volume.
     */
    private static final float DEFAULT_VOLUME = 1.0f;

    /**
     * Default rate for sound playback. Use 1.0f for normal speed playback.
     */
    private static final float DEFAULT_RATE = 1.0f;

    /**
     * Number of channels to use in SoundPool for auditory icon feedback.
     */
    private static final int NUMBER_OF_CHANNELS = 10;

    /** Map of resource IDs to vibration pattern arrays. */
    private final Map<Integer, long[]> mResourceIdToVibrationPatternMap =
            new TreeMap<Integer, long[]>();

    /** Map of resource IDs to loaded sound stream IDs. */
    private final Map<Integer, Integer> mResourceIdToSoundMap = new TreeMap<Integer, Integer>();

    /** Parent context. Required for mapping resource IDs to resources. */
    private final Context mContext;

    /** Vibration service used to play vibration patterns. */
    private final Vibrator mVibrator;

    /** Sound pool used to play auditory icons. */
    private final SoundPool mSoundPool;

    /** Whether haptic feedback is enabled. */
    private boolean mHapticEnabled = true;

    /** Whether auditory feedback is enabled. */
    private boolean mAuditoryEnabled = true;

    /** Current volume (range 0..1). */
    private float mVolume = DEFAULT_VOLUME;

    /**
     * Constructs and initializes a new feedback controller.
     */
    public FeedbackController(Context context) {
        mContext = context;
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mSoundPool = new SoundPool(NUMBER_OF_CHANNELS, DEFAULT_STREAM, 1);

        mResourceIdToSoundMap.clear();
        mResourceIdToVibrationPatternMap.clear();
    }

    /**
     * @return The parent context.
     */
    protected final Context getContext() {
        return mContext;
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
     * Asynchronously make a sound available for later use if audio feedback is
     * enabled. Sounds should be loaded using this function whenever audio
     * feedback is enabled.
     *
     * @param resId resource id of the sound to be loaded
     */
    public void preloadSound(int resId) {
        if (!mResourceIdToSoundMap.containsKey(resId)) {
            mResourceIdToSoundMap.put(resId, mSoundPool.load(mContext, resId, 1));
        }
    }

    /**
     * Plays the sound file specified by the given resource identifier at the
     * default rate.
     *
     * @param resId The sound file's resource identifier.
     * @return {@code true} if successful
     */
    public boolean playSound(int resId) {
        return playSound(resId, DEFAULT_RATE, DEFAULT_VOLUME);
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
    public boolean playSound(int resId, float rate, float volume) {
        if (!mAuditoryEnabled) {
            return false;
        }

        Integer soundId = mResourceIdToSoundMap.get(resId);

        if (soundId == null) {
            // Make the sound available for the next time it is needed
            // to preserve backwars compatibility with callers who don't
            // preload the sounds.
            preloadSound(resId);

            // Since we need to play the sound immediately after it loads, we
            // should just return true.
            return true;
        }

        final float relativeVolume = mVolume * volume;
        final int stream = mSoundPool.play(soundId, relativeVolume, relativeVolume, 1, 0, rate);

        return (stream != 0);
    }

    /**
     * Plays the vibration pattern specified by the given resource identifier.
     *
     * @param resId The vibration pattern's resource identifier.
     * @return {@code true} if successful
     */
    public boolean playVibration(int resId) {
        if (!mHapticEnabled || (mVibrator == null)) {
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
}
