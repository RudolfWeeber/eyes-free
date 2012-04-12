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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.TalkBackService.EventProcessor;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
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
class ProcessorLongHover implements EventProcessor {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 14;

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final LongHoverHandler mHandler;

    private boolean mWaitingForExit;

    public ProcessorLongHover(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;
        mHandler = new LongHoverHandler();
    }

    @Override
    public void process(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        // We expect to get hover events out of order, so moving from A to
        // long-hover on B looks like this:
        // - A HOVER_ENTER
        // - B HOVER_ENTER
        // - A HOVER_EXIT
        // And we'll never get three HOVER_ENTER events in a row, but we may get
        // hover events that are in order, like:
        // - A HOVER_ENTER
        // - B HOVER_EXIT
        // So we'll flip a bit every time we see one of these events, such that
        // we need an odd number of events for an EXIT event to be ordered.

        if (eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            mWaitingForExit = !mWaitingForExit;
        }

        // HOVER_EXIT events are received after the subsequent HOVER_ENTER event, so we need to keep track.
        if ((eventType != AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT) || mWaitingForExit) {
            mHandler.cancelLongHoverTimeout();
        }

        if (eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT) {
            mWaitingForExit = !mWaitingForExit;
        }

        // Only HOVER_ENTER events trigger a long hover timeout.
        if (eventType != AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            return;
        }

        mHandler.startLongHoverTimeout(event);
    }

    private class LongHoverHandler extends Handler {
        /** Message identifier for a verbose (long-hover) notification. */
        private static final int LONG_HOVER_TIMEOUT = 1;

        /** Timeout before reading a verbose (long-hover) notification. */
        private static final long DELAY_LONG_HOVER_TIMEOUT = 1000;

        /** The event that will be read by the utterance complete action. */
        private AccessibilityEvent mPendingLongHoverEvent;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LONG_HOVER_TIMEOUT: {
                    final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
                    handleLongHoverTimeout(event);
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
        private void handleLongHoverTimeout(AccessibilityEvent event) {
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final AccessibilityNodeInfoCompat source = record.getSource();

            if (source == null) {
                return;
            }

            final CharSequence text = NodeSpeechRuleProcessor.processVerbose(mContext, source);
            source.recycle();

            // Use QUEUE mode so that we don't interrupt more important messages.
            mSpeechController.cleanUpAndSpeak(text, QueuingMode.QUEUE, null);
        }

        /**
         * Starts the long hover timeout. Call this for every VIEW_HOVER
         * event.
         */
        private void startLongHoverTimeout(AccessibilityEvent event) {
            mPendingLongHoverEvent = AccessibilityEventCompatUtils.obtain(event);
            mSpeechController.addUtteranceCompleteAction(-1, mLongHoverRunnable);
        }

        /**
         * Removes the long hover timeout and completion action. Call this
         * for every event.
         */
        private void cancelLongHoverTimeout() {
            removeMessages(LONG_HOVER_TIMEOUT);

            if (mPendingLongHoverEvent != null) {
                mSpeechController.removeUtteranceCompleteAction(mLongHoverRunnable);
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