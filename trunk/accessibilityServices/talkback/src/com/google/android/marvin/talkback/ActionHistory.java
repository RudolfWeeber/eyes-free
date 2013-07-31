/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import com.googlecode.eyesfree.compat.util.SparseLongArray;

/**
 * Maintains a history of when actions occurred.
 */
public class ActionHistory {
    /** Lazily-initialized shared instance. */
    private static ActionHistory sInstance;

    /**
     * @return A shared instance of the action history.
     */
    public static ActionHistory getInstance() {
        if (sInstance == null) {
            sInstance = new ActionHistory();
        }
        return sInstance;
    }

    /** Map of action identifiers to start times. */
    private final SparseLongArray mActionStartMap = new SparseLongArray();

    /** Map of action identifiers to finish times. */
    private final SparseLongArray mActionFinishMap = new SparseLongArray();

    private ActionHistory() {
        // This class is not publicly instantiable.
    }

    /**
     * Stores the start time for an action. This should be called immediately
     * before {@link AccessibilityNodeInfoCompat#performAction}.
     *
     * @param action The action that will be performed.
     */
    public void before(int action) {
        mActionStartMap.put(action, SystemClock.uptimeMillis());
    }

    /**
     * Stores the finish time for an action. This should be called immediately
     * after {@link AccessibilityNodeInfoCompat#performAction}.
     *
     * @param action The action that was performed.
     */
    public void after(int action) {
        mActionFinishMap.put(action, SystemClock.uptimeMillis());
    }

    /**
     * Returns whether the specified event time falls between the start and
     * finish times of the specified action.
     *
     * @param action The action to check.
     * @param eventTime The event time to check.
     * @return {@code true} if the event time falls between the start and finish
     *         times of the specified action.
     */
    public boolean hasActionAtTime(int action, long eventTime) {
        final long startTime = mActionStartMap.get(action);
        final boolean hasStarted = (startTime > 0);
        if (!hasStarted || (startTime > eventTime)) {
            // The action hasn't started, or started after the event.
            return false;
        }

        final long finishTime = mActionFinishMap.get(action);
        final boolean hasFinished = (finishTime >= startTime);
        if (hasFinished && (finishTime < eventTime)) {
            // The action finished before the event.
            return false;
        }

        return true;
    }
}
