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
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.TalkBackService.EventListener;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.utils.WeakReferenceHandler;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;

/**
 * Manages long-hover feedback. If a HOVER_ENTER event passes through this
 * processor and no further events are received for a specified duration, a
 * "long hover" message is spoken.
 * <p>
 * Requires API 14+.
 * </p>
 *
 * @author alanv@google.com (Alan Viverette)
 */
class ProcessorLongHover implements EventListener {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 14;

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final LongHoverHandler mHandler;

    private AccessibilityNodeInfoCompat mWaitingForExit;

    public ProcessorLongHover(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;
        mHandler = new LongHoverHandler(this);
    }

    @Override
    public void process(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            cacheEnteredNode(null);
            mHandler.cancelLongHoverTimeout(this);
            return;
        }

        if ((eventType != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                && (eventType != AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        switch (eventType) {
            case AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER:
                mHandler.startLongHoverTimeout(event, this);
                break;
            case AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT:
                mHandler.cancelLongHoverTimeout(this);
                break;
        }

        source.recycle();
    }


    private void cacheEnteredNode(AccessibilityNodeInfoCompat node) {
        if (mWaitingForExit != null) {
            mWaitingForExit.recycle();
            mWaitingForExit = null;
        }

        if (node != null) {
            mWaitingForExit = AccessibilityNodeInfoCompat.obtain(node);
        }
    }

    private static class LongHoverHandler extends WeakReferenceHandler<ProcessorLongHover> {
        /** Message identifier for a verbose (long-hover) notification. */
        private static final int LONG_HOVER_TIMEOUT = 1;

        /** Timeout before reading a verbose (long-hover) notification. */
        private static final long DELAY_LONG_HOVER_TIMEOUT = 1000;

        /** The event that will be read by the utterance complete action. */
        private AccessibilityEvent mPendingLongHoverEvent;

        public LongHoverHandler(ProcessorLongHover parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorLongHover parent) {
            switch (msg.what) {
                case LONG_HOVER_TIMEOUT: {
                    final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
                    handleLongHoverTimeout(event, parent);
                    event.recycle();
                    break;
                }
            }
        }

        /**
         * Given an {@link AccessibilityEvent}, obtains and speaks a long
         * hover utterance.
         *
         * @param event The source event.
         */
        private void handleLongHoverTimeout(AccessibilityEvent event, ProcessorLongHover parent) {
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final AccessibilityNodeInfoCompat source = record.getSource();

            if (source == null) {
                return;
            }

            final CharSequence text = NodeSpeechRuleProcessor.processVerbose(
                    parent.mContext, source);
            source.recycle();

            // Use QUEUE mode so that we don't interrupt more important messages.
            parent.mSpeechController.cleanUpAndSpeak(text, QueuingMode.QUEUE, null);
        }

        /**
         * Starts the long hover timeout. Call this for every VIEW_HOVER
         * event.
         */
        private void startLongHoverTimeout(AccessibilityEvent event, ProcessorLongHover parent) {
            mPendingLongHoverEvent = AccessibilityEventCompatUtils.obtain(event);

            // The long hover timeout starts after the current text is spoken.
            parent.mSpeechController.addUtteranceCompleteAction(-1, mLongHoverRunnable);
        }

        /**
         * Removes the long hover timeout and completion action. Call this
         * for every event.
         */
        private void cancelLongHoverTimeout(ProcessorLongHover parent) {
            removeMessages(LONG_HOVER_TIMEOUT);

            if (mPendingLongHoverEvent != null) {
                parent.mSpeechController.removeUtteranceCompleteAction(mLongHoverRunnable);
                mPendingLongHoverEvent.recycle();
                mPendingLongHoverEvent = null;
            }
        }

        /**
         * Posts a delayed long hover action.
         */
        private final Runnable mLongHoverRunnable = new Runnable() {
            @Override
            public void run() {
                final AccessibilityEvent event = mPendingLongHoverEvent;

                if (event == null) {
                    return;
                }

                final AccessibilityEvent eventClone = AccessibilityEventCompatUtils.obtain(event);
                final Message msg = obtainMessage(LONG_HOVER_TIMEOUT, eventClone);

                sendMessageDelayed(msg, DELAY_LONG_HOVER_TIMEOUT);
            }
        };
    }
}