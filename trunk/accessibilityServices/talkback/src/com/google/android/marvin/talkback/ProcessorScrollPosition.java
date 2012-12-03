/*
 * Copyright (C) 2011 The Android Open Source Project
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
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService.EventListener;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

import java.util.HashMap;

/**
 * Manages scroll position feedback. If a VIEW_SCROLLED event passes through
 * this processor and no further events are received for a specified duration, a
 * "scroll position" message is spoken.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class ProcessorScrollPosition implements EventListener {
    /** Default pitch adjustment for text event feedback. */
    private static final float DEFAULT_PITCH = 1.2f;

    /** Default rate adjustment for text event feedback. */
    private static final float DEFAULT_RATE = 1.0f;

    private final HashMap<AccessibilityNodeInfoCompat, Integer>
            mCachedFromValues = new HashMap<AccessibilityNodeInfoCompat, Integer>();
    private final Bundle mSpeechParams = new Bundle();

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final ScrollPositionHandler mHandler;

    public ProcessorScrollPosition(TalkBackService context) {
        mContext = context;
        mSpeechController = context.getSpeechController();
        mHandler = new ScrollPositionHandler(this);

        // TODO(alanv): Fix queuing of pitch-adjusted speech, then uncomment.
        //mSpeechParams.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_PITCH);
        //mSpeechParams.putFloat(SpeechController.SpeechParam.RATE, DEFAULT_RATE);
    }

    @Override
    public void process(AccessibilityEvent event) {
        if (shouldIgnoreEvent(event)) {
            return;
        }

        mHandler.cancelScrollTimeout();

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Window state changes clear the cache.
                mCachedFromValues.clear();
                break;
            case AccessibilityEventCompat.TYPE_VIEW_SCROLLED:
                mHandler.startScrollTimeout(event);
                break;
        }
    }

    private boolean shouldIgnoreEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEventCompat.TYPE_WINDOW_CONTENT_CHANGED:
            case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
            case AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
                return true;
            case AccessibilityEventCompat.TYPE_VIEW_SCROLLED:
                return shouldIgnoreScrollEvent(event);
            default:
                return false;
        }
    }

    private boolean shouldIgnoreScrollEvent(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat node = record.getSource();

        if (node == null) {
            if (Build.VERSION.SDK_INT >= 14) {
                // Event is coming from another window.
                return true;
            } else {
                // We don't have enough information to ignore this event.
                return false;
            }
        }

        final int fromIndex = event.getFromIndex() + 1;
        final Integer cachedFromIndex = mCachedFromValues.get(node);

        if ((cachedFromIndex != null) && (cachedFromIndex == fromIndex)) {
            // The from index hasn't changed, which means the event is coming
            // from a re-layout or resize and should not be spoken.
            node.recycle();
            return true;
        }

        // The behavior of put() for an existing key is unspecified, so we can't
        // recycle the old or new key nodes.
        mCachedFromValues.put(node, fromIndex);
        return false;
    }

    /**
     * Given an {@link AccessibilityEvent}, speaks a scroll position.
     *
     * @param event The source event.
     */
    private void handleScrollTimeout(AccessibilityEvent event) {
        final CharSequence text = getDescriptionForEvent(event);
        if (TextUtils.isEmpty(text)) {
            return;
        }

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.cleanUpAndSpeak(
                text, SpeechController.QUEUE_MODE_QUEUE, 0, mSpeechParams);
    }

    private CharSequence getDescriptionForEvent(AccessibilityEvent event) {
        // If the event has text, use that by default.
        final CharSequence text = AccessibilityEventUtils.getEventText(event);
        if (!TextUtils.isEmpty(text)) {
            return text;
        }

        // If the from index or item count are invalid, don't announce anything.
        final int fromIndex = (event.getFromIndex() + 1);
        final int itemCount = event.getItemCount();
        if ((fromIndex < 0) || (itemCount <= 0)) {
            return null;
        }

        // If the to and from indices are the same, or if the to index is
        // invalid, only announce the item at the from index.
        final int toIndex = (AccessibilityEventCompatUtils.getToIndex(event) + 1);
        if ((fromIndex == toIndex) || (toIndex <= 0) || (toIndex > itemCount)) {
            return mContext.getString(R.string.template_scroll_from_count, fromIndex, itemCount);
        }

        // Announce the range of visible items.
        return mContext.getString(
                R.string.template_scroll_from_to_count, fromIndex, toIndex, itemCount);
    }

    private static class ScrollPositionHandler
            extends WeakReferenceHandler<ProcessorScrollPosition> {
        /** Message identifier for a scroll position notification. */
        private static final int SCROLL_TIMEOUT = 1;

        /** Timeout before reading a scroll position notification. */
        private static final long DELAY_SCROLL_TIMEOUT = 1000;

        public ScrollPositionHandler(ProcessorScrollPosition parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorScrollPosition parent) {
            switch (msg.what) {
                case SCROLL_TIMEOUT: {
                    final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
                    parent.handleScrollTimeout(event);
                    event.recycle();
                    break;
                }
            }
        }

        /**
         * Starts the scroll position timeout. Call this for every VIEW_SCROLLED
         * event.
         */
        private void startScrollTimeout(AccessibilityEvent event) {
            final AccessibilityEvent eventClone = AccessibilityEventCompatUtils.obtain(event);
            final Message msg = obtainMessage(SCROLL_TIMEOUT, eventClone);

            sendMessageDelayed(msg, DELAY_SCROLL_TIMEOUT);
        }

        /**
         * Removes the scroll position timeout. Call this for every event.
         */
        private void cancelScrollTimeout() {
            removeMessages(SCROLL_TIMEOUT);
        }
    }
}