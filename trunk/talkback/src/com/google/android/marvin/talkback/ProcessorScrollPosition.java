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
import android.os.Handler;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.TalkBackService.EventProcessor;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;

/**
 * Manages scroll position feedback. If a VIEW_SCROLLED event passes through
 * this processor and no further events are received for a specified duration, a
 * "scroll position" message is spoken.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
class ProcessorScrollPosition implements EventProcessor {
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final ScrollPositionHandler mHandler;
    
    public ProcessorScrollPosition(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;
        mHandler = new ScrollPositionHandler();
    }

    @Override
    public void process(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        mHandler.cancelScrollTimeout();

        // Only VIEW_SCROLLED events trigger a scroll position timeout.
        if (eventType != AccessibilityEventCompat.TYPE_VIEW_SCROLLED) {
            return;
        }

        final CharSequence text = AccessibilityEventUtils.getEventText(event);

        // Don't trigger a scroll position timeout if the event has text.
        if (!TextUtils.isEmpty(text)) {
            return;
        }

        mHandler.startScrollTimeout(event);
    }

    /**
     * Given an {@link AccessibilityEvent}, speaks a scroll position.
     * 
     * @param event The source event.
     */
    private void handleScrollTimeout(AccessibilityEvent event) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final int fromIndex = event.getFromIndex() + 1;
        final int toIndex = record.getToIndex() + 1;
        final int itemCount = event.getItemCount();

        // If the from index or the item count are invalid, don't announce
        // anything.
        if (fromIndex < 0 || itemCount < 0) {
            return;
        }

        final String text;

        // If the from and to indices are the same, or if the to index is
        // invalid, only announce the item at the from index. Otherwise,
        // announce the range of visible items.
        if ((fromIndex == toIndex) || toIndex < 0) {
            text = mContext.getString(R.string.template_scroll_from_count, fromIndex, itemCount);
        } else {
            text =
                    mContext.getString(R.string.template_scroll_from_to_count, fromIndex, toIndex,
                            itemCount);
        }

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.cleanUpAndSpeak(text, QueuingMode.QUEUE, null);
    }
    
    private class ScrollPositionHandler extends Handler {
        /** Message identifier for a scroll position notification. */
        private static final int SCROLL_TIMEOUT = 1;

        /** Timeout before reading a scroll position notification. */
        private static final long DELAY_SCROLL_TIMEOUT = 1000;
        
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SCROLL_TIMEOUT: {
                    final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
                    handleScrollTimeout(event);
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