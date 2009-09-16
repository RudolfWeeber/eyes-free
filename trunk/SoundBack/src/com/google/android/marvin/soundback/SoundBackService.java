/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.soundback;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.media.SoundPool;
import android.util.Log;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service that provides audible feedback.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class SoundBackService extends AccessibilityService {

    private static final String LOG_TAG = "SoundBackService";

    private static final int NUMBER_OF_CHANNELS = 4;
    private static final int STREAM_MUSIC = 3;
    private static final float SOUND_EFFECT_VOLUME = 1000.0f;

    private static final int NOTIFICATION_TIMEOUT_MILLIS = 80;

    private SoundPool mSoundPool = new SoundPool(NUMBER_OF_CHANNELS, STREAM_MUSIC, 0);
    private SparseArray<Integer> mResourceIdToSoundIdMap = new SparseArray<Integer>();
    private int currentSoundId;

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.notificationTimeout = NOTIFICATION_TIMEOUT_MILLIS;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
    }

    @Override
    public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null){
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }
        int eventType = event.getEventType();
        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED : 
                playEarcon(R.raw.open);
                return;
            case AccessibilityEvent.TYPE_VIEW_SELECTED : 
                playEarcon(R.raw.select);
                return;
            case AccessibilityEvent.TYPE_VIEW_CLICKED : 
                playEarcon(R.raw.button);
                return;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED :
                playEarcon(R.raw.open);
                return;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED :
                playEarcon(R.raw.item);
                return;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED :
                playEarcon(R.raw.working);
                return;
            default :
                Log.w(LOG_TAG, "Unknown accessibility event type " + eventType);
        }
    }

    @Override
    public void onInterrupt() {
        mSoundPool.stop(currentSoundId);
    }

    /**
     * Plays an earcon given its resource id.
     * 
     * @param resourceId The resource id of the earcon to be played.
     */
    private void playEarcon(int resourceId) {
        Integer soundId = mResourceIdToSoundIdMap.get(resourceId);
        if (soundId == null) {
            soundId = mSoundPool.load(this, resourceId, 1234567);
            mResourceIdToSoundIdMap.put(resourceId, soundId);
        }
        currentSoundId = soundId;
        mSoundPool.play(soundId, SOUND_EFFECT_VOLUME, SOUND_EFFECT_VOLUME, 0, 0, 1.0f);
    }
}
