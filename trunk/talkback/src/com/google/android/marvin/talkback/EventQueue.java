/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * This class is an event queue which keeps track of relevant events. Such
 * events do not have {@link AccessibilityEvent#TYPE_NOTIFICATION_STATE_CHANGED}
 * . We treat such events in a special manner.
 */
class EventQueue extends ArrayList<AccessibilityEvent> {
    /** The maximal size to the queue of cached events. */
    private static final int EVENT_QUEUE_MAX_SIZE = 10;
    
    private int mNonNotificationEventCount;

    @Override
    public boolean add(AccessibilityEvent event) {
        final AccessibilityEvent clone = AccessibilityEvent.obtain(event);
        
        if (!isNotificationEvent(clone)) {
            mNonNotificationEventCount++;
        }

        final boolean result = super.add(clone);

        enforceRelevantEventSize();

        return result;
    }

    @Override
    public void add(int location, AccessibilityEvent object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends AccessibilityEvent> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int location, Collection<? extends AccessibilityEvent> collection) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AccessibilityEvent remove(int location) {
        final AccessibilityEvent event = get(location);

        if (event != null && !isNotificationEvent(event)) {
            mNonNotificationEventCount--;
        }

        return super.remove(location);
    }

    @Override
    public boolean remove(Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        final Iterator<AccessibilityEvent> iterator = iterator();

        while (iterator.hasNext()) {
            final AccessibilityEvent next = iterator.next();

            // Never remove notification events.
            if (!isNotificationEvent(next)) {
                iterator.remove();
            }
        }

        mNonNotificationEventCount = 0;
    }

    /**
     * Enforces that the event queue is not more than
     * {@link #EVENT_QUEUE_MAX_SIZE}. The excessive events are
     * pruned through a FIFO strategy i.e. removing the oldest event first.
     */
    public void enforceRelevantEventSize() {
        final Iterator<AccessibilityEvent> iterator = iterator();

        while (iterator().hasNext()
                && (mNonNotificationEventCount > EVENT_QUEUE_MAX_SIZE)) {
            final AccessibilityEvent next = iterator.next();

            // Never remove notification events.
            if (!isNotificationEvent(next)) {
                mNonNotificationEventCount--;
                iterator.remove();
            }
        }
    }

    /**
     * Returns if an event type is
     * AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED.
     */
    private static boolean isNotificationEvent(AccessibilityEvent event) {
        return (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
    }
}
