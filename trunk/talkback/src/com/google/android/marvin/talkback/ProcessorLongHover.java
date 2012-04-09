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
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.TalkBackService.EventProcessor;
import com.google.android.marvin.talkback.speechrules.NodeProcessor;

class ProcessorLongHover implements EventProcessor {
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final LongHoverHandler mHandler;

    public ProcessorLongHover(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;
        mHandler = new LongHoverHandler();
    }

    @Override
    public void process(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        mHandler.cancelLongHoverTimeout();

        if (eventType != AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
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
            final AccessibilityNodeInfo node = event.getSource();

            if (node == null) {
                return;
            }

            final CharSequence text = NodeProcessor.processVerbose(mContext, node);

            node.recycle();

            if (TextUtils.isEmpty(text)) {
                return;
            }

            mSpeechController.cleanUpAndSpeak(text, TextToSpeech.QUEUE_FLUSH);
        }

        /**
         * Starts the long hover timeout. Call this for every VIEW_HOVER
         * event.
         */
        private void startLongHoverTimeout(AccessibilityEvent event) {
            mPendingLongHoverEvent = AccessibilityEvent.obtain(event);
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

                final AccessibilityEvent eventClone = AccessibilityEvent.obtain(event);
                final Message msg = obtainMessage(LONG_HOVER_TIMEOUT, eventClone);

                sendMessageDelayed(msg, DELAY_LONG_HOVER_TIMEOUT);
            }
        };
    }
}