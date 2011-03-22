/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.aime;

import java.text.BreakIterator;

/**
 * Stores begin and end positions of text. If any of the position, <code>mStart</code> or
 * <code>mEnd</code> is <code>INVALID_POSITION</code>, it is an invalid position.
 *
 * @author hiteshk@google.com (Hitesh Khandelwal)
 */
public class Position {
    /** Flag for indicating invalid Selection position. */
    public static final int INVALID_POSITION = BreakIterator.DONE;

    /** Begin index of Selection. */
    public int mStart;

    /** End index of Selection. */
    public int mEnd;

    /** Flag for checking selection mode is turned on. */
    public boolean mSelected;

    /** Maximum number of cached objects in pool. */
    private static final int MAX_POOL_SIZE = 2;

    /** Lock object. */
    private static final Object mPoolLock = new Object();

    /** Points to first object in the pool. */
    private static Position sPool;

    /** Current size of the pool. */
    private static int sPoolSize;

    /** Next object in the pool. */
    private Position mNext;

    /** Flag for checking if current object is in the pool. */
    private boolean mIsInPool;

    /**
     * Hide constructor.
     */
    private Position() {
        clear();
    }

    /**
     * Sets Selection positions and selection mode.
     */
    private void setPosition(int start, int end, boolean selected) {
        mStart = start;
        mEnd = end;
        mSelected = selected;
    }

    /**
     * Returns a cached instance if such is available or a new one is instantiated with appropriate
     * arguments.
     *
     * @return An instance.
     */
    public static Position obtain(int start, int end, boolean selected) {
        Position position = Position.obtain();
        position.setPosition(start, end, selected);
        return position;
    }

    /**
     * Returns a cached instance if such is available or a new one is instantiated.
     *
     * @return An instance.
     */
    public static Position obtain() {
        synchronized (mPoolLock) {
            if (sPool != null) {
                Position position = sPool;
                sPool = sPool.mNext;
                sPoolSize--;
                position.mNext = null;
                position.mIsInPool = false;
                return position;
            }
            return new Position();
        }
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
        synchronized (mPoolLock) {
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
        mStart = INVALID_POSITION;
        mEnd = INVALID_POSITION;
        mSelected = false;
    }
    
    @Override
    public String toString() {
        return "(" + mStart + ", " + mEnd + ") with selected = " + mSelected;
    }
}
