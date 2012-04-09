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

package com.google.android.marvin.talkback;

import android.os.Bundle;

import java.util.LinkedList;
import java.util.List;

/**
 * This class represents an utterance composed of text to be spoken and meta
 * data about how this text to be spoken. The utterances are cached in a pool of
 * instances to be reused as an optimization to reduce new object instantiation.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class Utterance {

    /**
     * Key for obtaining the queuing meta-data property.
     */
    public static final String KEY_METADATA_QUEUING = "queuing";

    /**
     * Key for obtaining the earcon rate meta-data property.
     */
    public static final String KEY_METADATA_EARCON_RATE = "earcon_rate";

    /**
     * The maximal size of the pool with cached utterances.
     */
    private static final int MAX_POOL_SIZE = 3;

    /**
     * Mutex lock for accessing the utterance pool.
     */
    private static final Object sPoolLock = new Object();

    /**
     * Pool of cached utterances.
     */
    private static Utterance sPool;

    /**
     * The current size of the utterance pool.
     */
    private static int sPoolSize;

    /**
     * The text of the utterance.
     */
    private final StringBuilder mText = new StringBuilder();

    /**
     * Meta-data of how the utterance should be spoken.
     */
    private final Bundle mMetadata = new Bundle();

    /**
     * The list of resource identifiers for this utterance's earcons.
     */
    private final List<Integer> mEarcons = new LinkedList<Integer>();

    /**
     * The list of resource identifiers for this utterance's vibration patterns.
     */
    private final List<Integer> mVibrationPatterns = new LinkedList<Integer>();

    /**
     * The list of resource identifiers for this utterance's earcons.
     */
    private final List<Integer> mCustomEarcons = new LinkedList<Integer>();

    /**
     * The list of resource identifiers for this utterance's vibration patterns.
     */
    private final List<Integer> mCustomVibrations = new LinkedList<Integer>();

    /**
     * The next cached utterance
     */
    private Utterance mNext;

    /**
     * Denotes if an utterance is currently in the cache pool.
     */
    private boolean mIsInPool;

    /**
     * Creates a new instance.
     */
    private Utterance() {
        /* do nothing - reducing constructor visibility */
    }

    /**
     * Returns a cached instance if such is available or a new one is
     * instantiated.
     * 
     * @return An instance.
     */
    public static Utterance obtain() {
        return obtain("");
    }

    /**
     * Returns a cached instance if such is available or a new one is
     * instantiated and sets its <code>text</code>.
     * 
     * @param text The text of the returned utterance.
     * @return An instance.
     */
    public static Utterance obtain(String text) {
        synchronized (sPoolLock) {
            if (sPool != null) {
                Utterance utterance = sPool;
                sPool = sPool.mNext;
                sPoolSize--;
                utterance.mNext = null;
                utterance.mIsInPool = false;
                return utterance;
            }
            return new Utterance();
        }
    }

    /**
     * Gets the text of this utterance.
     * 
     * @return The utterance text.
     */
    public StringBuilder getText() {
        return mText;
    }

    /**
     * Gets the list of earcons for this utterance.
     * 
     * @return A list of sound resource identifiers.
     */
    public List<Integer> getEarcons() {
        return mEarcons;
    }

    /**
     * Gets the list of vibration patterns for this utterance.
     * 
     * @return A list of vibration pattern resource identifiers.
     */
    public List<Integer> getVibrationPatterns() {
        return mVibrationPatterns;
    }

    /**
     * @return A list of preference identifiers that correspond to earcon
     *         resource identifiers.
     */
    public List<Integer> getCustomEarcons() {
        return mCustomEarcons;
    }

    /**
     * @return A list of preference identifiers that correspond to vibration
     *         pattern resource identifiers.
     */
    public List<Integer> getCustomVibrations() {
        return mCustomVibrations;
    }

    /**
     * Gets the meta-data of this utterance.
     * 
     * @return The utterance meta-data.
     */
    public Bundle getMetadata() {
        return mMetadata;
    }

    /**
     * Return an instance back to be reused.
     * <p>
     * <b>Note: You must not touch the object after calling this function.</b>
     */
    public void recycle() {
        if (mIsInPool) {
            return;
        }
        clear();
        synchronized (sPoolLock) {
            if (sPoolSize <= MAX_POOL_SIZE) {
                mNext = sPool;
                sPool = this;
                mIsInPool = true;
                sPoolSize++;
            }
        }
    }

    /**
     * Clears the state of this instance.
     */
    private void clear() {
        mText.delete(0, mText.length());
        mMetadata.clear();
        mEarcons.clear();
        mVibrationPatterns.clear();
        mCustomEarcons.clear();
        mCustomVibrations.clear();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Text:{");
        builder.append(mText);
        builder.append("}, Metadata:");
        builder.append(mMetadata);
        return builder.toString();
    }
}
