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

import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

import java.util.HashMap;

/**
 * Manages scroll position feedback. If a VIEW_SCROLLED event passes through
 * this processor and no further events are received for a specified duration, a
 * "scroll position" message is spoken.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class ProcessorScrollPosition implements AccessibilityEventListener {
    /** Default pitch adjustment for text event feedback. */
    private static final float DEFAULT_PITCH = 1.2f;

    /** Default rate adjustment for text event feedback. */
    private static final float DEFAULT_RATE = 1.0f;

    private final HashMap<AccessibilityNodeInfoCompat, Integer>
            mCachedFromValues = new HashMap<AccessibilityNodeInfoCompat, Integer>();
    private final Bundle mSpeechParams = new Bundle();
    private final ScrollPositionHandler mHandler = new ScrollPositionHandler(this);

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final FullScreenReadController mFullScreenReadController;

    private AccessibilityNodeInfoCompat mRecentlyExplored;

    public ProcessorScrollPosition(TalkBackService context) {
        mContext = context;
        mSpeechController = context.getSpeechController();

        if (Build.VERSION.SDK_INT >= FullScreenReadController.MIN_API_LEVEL) {
            mFullScreenReadController = context.getFullScreenReadController();
        } else {
            mFullScreenReadController = null;
        }

        mSpeechParams.putFloat(SpeechController.SpeechParam.PITCH, DEFAULT_PITCH);
        mSpeechParams.putFloat(SpeechController.SpeechParam.RATE, DEFAULT_RATE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        updateRecentlyExplored(event);

        if (shouldIgnoreEvent(event)) {
            return;
        }

        mHandler.cancelSeekFeedback();
        mHandler.cancelScrollFeedback();

        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Window state changes clear the cache.
                mCachedFromValues.clear();
                break;
            case AccessibilityEvent.TYPE_VIEW_SCROLLED:
                mHandler.postScrollFeedback(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                // SeekBars incorrectly send TYPE_VIEW_SELECTED events (verified
                // up to 4.1.2).
                if (AccessibilityEventUtils.eventMatchesClass(
                        mContext, event, android.widget.SeekBar.class.getName())) {
                    mHandler.postSeekFeedback(event);
                }
                break;
        }
    }

    private void updateRecentlyExplored(AccessibilityEvent event) {
        // TODO(alanv): We track this in several places. Need to refactor.

        // We only need to track recently explored views on ICS.
        if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)) {
            return;
        }

        // Exploration is the result of HOVER_ENTER events.
        if (event.getEventType() != AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat node = record.getSource();

        if (mRecentlyExplored != null) {
            if (mRecentlyExplored.equals(node)) {
                return;
            }

            mRecentlyExplored.recycle();
            mRecentlyExplored = null;
        }

        if (node != null) {
            mRecentlyExplored = AccessibilityNodeInfoCompat.obtain(node);
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
        // Don't speak during full-screen read.
        if ((mFullScreenReadController != null) && mFullScreenReadController.isActive()) {
            return true;
        }

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
    private void handleScrollFeedback(AccessibilityEvent event) {
        final CharSequence text = getDescriptionForScrollEvent(event);
        if (TextUtils.isEmpty(text)) {
            return;
        }

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.speak(text, SpeechController.QUEUE_MODE_QUEUE, 0, mSpeechParams);
    }

    /**
     * Given an {@link AccessibilityEvent}, speaks a scroll position.
     *
     * @param event The source event.
     */
    private void handleSeekFeedback(AccessibilityEvent event) {
        // For pre-ICS devices, we don't have enough information to
        // determine whether to speak this event.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat node = record.getSource();
        if (node == null) {
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                // On Jelly Bean, the view must have accessibility focus.
                if (!node.isAccessibilityFocused()) {
                    return;
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                // On ICS, the view must be the most recently explored view.
                if ((mRecentlyExplored == null) || !mRecentlyExplored.equals(node)) {
                    return;
                }
            }

            final CharSequence text = getDescriptionForSeekEvent(event);
            if (TextUtils.isEmpty(text)) {
                return;
            }

            // Use QUEUE mode so that we don't interrupt important messages.
            mSpeechController.speak(text, SpeechController.QUEUE_MODE_QUEUE, 0, mSpeechParams);
        } finally {
            AccessibilityNodeInfoUtils.recycleNodes(node);
        }
    }

    private CharSequence getDescriptionForScrollEvent(AccessibilityEvent event) {
        // If the event has text, use that by default.
        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
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

    private CharSequence getDescriptionForSeekEvent(AccessibilityEvent event) {
        // If the event has text, use that by default.
        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (!TextUtils.isEmpty(text)) {
            return text;
        }

        // If the current index or item count are invalid, don't announce anything.
        final int currentItemIndex = event.getCurrentItemIndex();
        final int itemCount = event.getItemCount();
        if ((currentItemIndex < 0) || (currentItemIndex > itemCount)) {
            return null;
        }

        // Announce the percentage.
        final int percentage = (100 * currentItemIndex / itemCount);
        return mContext.getString(R.string.template_percent, percentage);
    }

    private static class ScrollPositionHandler
            extends WeakReferenceHandler<ProcessorScrollPosition> {
        /** Message identifier for a scroll position notification. */
        private static final int SCROLL_FEEDBACK = 1;

        /** Message identifier for a seek bar position notification. */
        private static final int SEEK_FEEDBACK = 2;

        /** Delay before reading a scroll position notification. */
        private static final long DELAY_SCROLL_FEEDBACK = 1000;

        /** Delay before reading a seek bar position notification. */
        private static final long DELAY_SEEK_FEEDBACK = 1000;

        public ScrollPositionHandler(ProcessorScrollPosition parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorScrollPosition parent) {
            final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
            switch (msg.what) {
                case SCROLL_FEEDBACK:
                    parent.handleScrollFeedback(event);
                    break;
                case SEEK_FEEDBACK:
                    parent.handleSeekFeedback(event);
                    break;
            }

            event.recycle();
        }

        /**
         * Posts the delayed seek bar position feedback. Call this for every
         * VIEW_SELECTED event sent from a SeekBar.
         */
        private void postSeekFeedback(AccessibilityEvent event) {
            final AccessibilityEvent eventClone = AccessibilityEventCompatUtils.obtain(event);
            final Message msg = obtainMessage(SEEK_FEEDBACK, eventClone);

            sendMessageDelayed(msg, DELAY_SEEK_FEEDBACK);
        }

        /**
         * Posts the delayed scroll position feedback. Call this for every
         * VIEW_SCROLLED event.
         */
        private void postScrollFeedback(AccessibilityEvent event) {
            final AccessibilityEvent eventClone = AccessibilityEventCompatUtils.obtain(event);
            final Message msg = obtainMessage(SCROLL_FEEDBACK, eventClone);

            sendMessageDelayed(msg, DELAY_SCROLL_FEEDBACK);
        }

        /**
         * Removes any pending seek bar position feedback. Call this for every
         * event.
         */
        private void cancelSeekFeedback() {
            removeMessages(SEEK_FEEDBACK);
        }

        /**
         * Removes any pending scroll position feedback. Call this for every
         * event.
         */
        private void cancelScrollFeedback() {
            removeMessages(SCROLL_FEEDBACK);
        }
    }
}