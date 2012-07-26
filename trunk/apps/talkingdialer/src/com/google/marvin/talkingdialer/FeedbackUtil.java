/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.marvin.talkingdialer;

import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Vibrator;

import java.util.TreeMap;

/**
 * This class provides haptic and auditory feedback.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class FeedbackUtil {
    private boolean mVibrationEnabled = true;
    private boolean mSoundEnabled = true;

    private final Context mContext;
    private final SoundPool mSoundPool;
    private final Vibrator mVibrator;
    private final TreeMap<Integer, Integer> mSoundMap;

    /**
     * Constructs a new FeedbackUtil based on the specified parent
     * {@link Context}.
     * 
     * @param context The parent {@link Context}.
     */
    public FeedbackUtil(Context context) {
        mContext = context;
        mSoundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 1);
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        mSoundMap = new TreeMap<Integer, Integer>();
    }

    /**
     * Releases resources associated with this FeedbackUtil. It is good practice
     * to call this method when you're done using the FeedbackUtil.
     */
    public void release() {
        mSoundPool.release();
    }

    /**
     * Loads a resource ahead of time
     * @param resId
     * @return soundId
     */
    public Integer load(int resId) {
        Integer soundId = mSoundPool.load(mContext, resId, 1);
        mSoundMap.put(resId, soundId);
        return soundId;
    }
    
    /**
     * Plays a sound file identified by the given resource id.
     * 
     * @param resId The resource id of the sound file.
     */
    public void playSound(int resId) {
        if (!mSoundEnabled) {
            return;
        }

        Integer soundId = mSoundMap.get(resId);

        if (soundId == null) {
            soundId = load(resId);
        }

        mSoundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f);
    }

    /**
     * Vibrates for the specified duration.
     * 
     * @param milliseconds How long to vibrate for.
     */
    public void vibrate(long milliseconds) {
        if (!mVibrationEnabled) {
            return;
        }

        mVibrator.vibrate(milliseconds);
    }

    /**
     * Vibrates with a given pattern.
     * 
     * @param pattern The vibration pattern as defined in
     *            {@link Vibrator#vibrate(long[], int)}.
     * @see Vibrator#vibrate(long[], int)
     */
    public void vibrate(long[] pattern) {
        if (!mVibrationEnabled) {
            return;
        }

        mVibrator.vibrate(pattern, -1);
    }

    /**
     * @param enabled {@code true} to enable vibration.
     */
    public void setVibrationEnabled(boolean enabled) {
        mVibrationEnabled = enabled;
    }

    /**
     * @param enabled {@code true} to enable sound.
     */
    public void setSoundEnabled(boolean enabled) {
        mSoundEnabled = enabled;
    }
}
