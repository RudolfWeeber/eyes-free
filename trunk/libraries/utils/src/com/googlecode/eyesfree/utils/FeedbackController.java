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
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Handler;
import android.os.Vibrator;
import android.util.SparseArray;
import android.util.SparseIntArray;

import java.util.ArrayList;

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

    /**
     * Default delay time between repeated sounds.
     */
    private static final int DEFAULT_REPETITION_DELAY = 150;

    /** Map of resource IDs to vibration pattern arrays. */
    private final SparseArray<long[]> mResourceIdToVibrationPatternMap = new SparseArray<long[]>();

    /** Map of resource IDs to loaded sound stream IDs. */
    private final SparseIntArray mResourceIdToSoundMap = new SparseIntArray();

    /** Unloaded resources to play post-load */
    private final ArrayList<Integer> mPostLoadPlayables = new ArrayList<Integer>();

    /** Parent context. Required for mapping resource IDs to resources. */
    private final Context mContext;

    /** Vibration service used to play vibration patterns. */
    private final Vibrator mVibrator;

    /** Sound pool used to play auditory icons. */
    private final SoundPool mSoundPool;

    /** Handler used for delaying feedback */
    private final Handler mHandler;

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
        mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0) {
                    synchronized (mPostLoadPlayables) {
                        if (mPostLoadPlayables.contains(sampleId)) {
                            soundPool.play(
                                    sampleId, DEFAULT_VOLUME, DEFAULT_VOLUME, 1, 0, DEFAULT_RATE);
                            mPostLoadPlayables.remove(Integer.valueOf(sampleId));
                        }
                    }
                }
            }
        });
        mHandler = new Handler();

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
        if (mResourceIdToSoundMap.indexOfKey(resId) < 0) {
            final int soundPoolId = mSoundPool.load(mContext, resId, 1);
            mResourceIdToSoundMap.put(resId, soundPoolId);
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
     * @param volume The volume at which to play the sound, where 1.0 is normal
     *            volume and 0.5 is half-volume.
     * @return {@code true} if successful
     */
    public boolean playSound(int resId, float rate, float volume) {
        if (!mAuditoryEnabled) {
            return false;
        }

        final int soundId = mResourceIdToSoundMap.get(resId);

        if (soundId == 0) {
            // Make the sound available for the next time it is needed
            // to preserve backwards compatibility with callers who don't
            // preload the sounds.
            loadAndPlaySound(resId);

            // Since we need to play the sound immediately after it loads, we
            // should just return true.
            return true;
        }

        final float relativeVolume = mVolume * volume;
        final int stream = mSoundPool.play(soundId, relativeVolume, relativeVolume, 1, 0, rate);

        return (stream != 0);
    }

    /**
     * Plays the sound file specified by the given resource identifier repeatedly.
     *
     * @param resId The resource of the sound file to play
     * @param repetitions The number of times to repeat the sound file
     * @return {@code true} if successful
     */
    public boolean playRepeatedSound(int resId, int repetitions) {
        return playRepeatedSound(
                resId, DEFAULT_RATE, DEFAULT_VOLUME, repetitions, DEFAULT_REPETITION_DELAY);
    }

    /**
     * Plays the sound file specified by the given resource identifier
     * repeatedly.
     *
     * @param resId The resource of the sound file to play
     * @param rate The rate at which to play the sound file
     * @param volume The volume at which to play the sound, where 1.0 is normal
     *            volume and 0.5 is half-volume.
     * @param repetitions The number of times to repeat the sound file
     * @param delay The amount of time between calls to start playback of the
     *            sound in miliseconds. Should be the sound's playback time plus
     *            some delay.
     * @return {@code true} if successful
     */
    public boolean playRepeatedSound(
            int resId, float rate, float volume, int repetitions, long delay) {
        if (!mAuditoryEnabled) {
            return false;
        }

        mHandler.post(new RepeatedSoundRunnable(resId, rate, volume, repetitions, delay));
        return true;
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

        long[] pattern = getVibrationPattern(resId);
        mVibrator.vibrate(pattern, -1);
        return true;
    }

    /**
     * Plays the vibration pattern specified by the given resource identifier
     * and repeats it indefinitely from the specified index.
     *
     * @see #cancelVibration()
     * @param resId The vibration pattern's resource identifier.
     * @param repeatIndex The index at which to loop vibration in the pattern.
     * @return {@code true} if successful
     */
    public boolean playRepeatedVibration(int resId, int repeatIndex) {
        if (!mHapticEnabled || (mVibrator == null)) {
            return false;
        }

        long[] pattern = getVibrationPattern(resId);

        if (repeatIndex < 0 || repeatIndex >= pattern.length) {
            throw new ArrayIndexOutOfBoundsException(repeatIndex);
        }
        mVibrator.vibrate(pattern, repeatIndex);
        return true;
    }

    /**
     * Cancels vibration feedback if in progress.
     */
    public void cancelVibration() {
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    /**
     * Retrieves the vibration pattern associated with the array resource.
     *
     * @param resId The array resource id of the pattern to retrieve.
     * @return an array of {@code long} values from the pattern.
     */
    private long[] getVibrationPattern(int resId) {
        long[] pattern = mResourceIdToVibrationPatternMap.get(resId);

        if (pattern == null) {
            final Resources resources = mContext.getResources();
            final int[] intPattern = resources.getIntArray(resId);

            if (intPattern == null) {
                return new long[0];
            }

            pattern = new long[intPattern.length];

            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = intPattern[i];
            }

            mResourceIdToVibrationPatternMap.put(resId, pattern);
        }

        return pattern;
    }

    /**
     * Loads the sound specified by the raw resource id and plays it.
     * @param resId The resource id of the sound to play.
     */
    private void loadAndPlaySound(int resId) {
        if (mResourceIdToSoundMap.indexOfKey(resId) < 0) {
            final int soundPoolId = mSoundPool.load(mContext, resId, 1);
            mPostLoadPlayables.add(soundPoolId);
            mResourceIdToSoundMap.put(resId, soundPoolId);
        }
    }

    /**
     * Class used for repeated playing of sound resources.
     */
    private class RepeatedSoundRunnable implements Runnable {
        private final int mResId;

        private final float mPlaybackRate;

        private final float mPlaybackVolume;

        private final int mRepetitions;

        private final long mDelay;

        private int mTimesPlayed;

        public RepeatedSoundRunnable(
                int resId, float rate, float volume, int repetitions, long delay) {
            mResId = resId;
            mPlaybackRate = rate;
            mPlaybackVolume = volume;
            mRepetitions = repetitions;
            mDelay = delay;

            mTimesPlayed = 0;
        }

        @Override
        public void run() {
            if (mTimesPlayed < mRepetitions) {
                playSound(mResId, mPlaybackRate, mPlaybackVolume);
                mTimesPlayed++;
                mHandler.postDelayed(this, mDelay);
            }
        }
    }
}
