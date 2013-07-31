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

import android.os.Build;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.UtteranceCompleteRunnable;
import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.google.android.marvin.talkback.speechrules.NodeSpeechRuleProcessor;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.compat.view.accessibility.AccessibilityEventCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

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
class ProcessorLongHover implements AccessibilityEventListener {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 14;

    /** The accessibility action type that triggers long hover. */
    private static final int TRIGGER_ACTION =
            (Build.VERSION.SDK_INT >= 16) ? AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
                    : AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER;

    private final TalkBackService mContext;
    private final SpeechController mSpeechController;
    private final NodeSpeechRuleProcessor mRuleProcessor;
    private final LongHoverHandler mHandler;

    private AccessibilityNodeInfoCompat mWaitingForExit;
    private boolean mIsTouchExploring;

    public ProcessorLongHover(TalkBackService context) {
        mContext = context;
        mSpeechController = context.getSpeechController();

        mRuleProcessor = NodeSpeechRuleProcessor.getInstance();

        mHandler = new LongHoverHandler(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if ((eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
                || (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START)) {
            mIsTouchExploring = true;
        }

        if (eventType == AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END) {
            mIsTouchExploring = false;
            cacheEnteredNode(null);
            cancelLongHover();
            return;
        }

        if (!mIsTouchExploring || ((eventType != TRIGGER_ACTION)
                && (eventType != AccessibilityEvent.TYPE_VIEW_HOVER_EXIT))) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        if (eventType == TRIGGER_ACTION) {
            cacheEnteredNode(source);
            postLongHoverRunnable(event);
        } else if (eventType == AccessibilityEventCompat.TYPE_VIEW_HOVER_EXIT) {
            if (source.equals(mWaitingForExit)) {
                cancelLongHover();
            }
        }

        source.recycle();
    }

    /**
     * Given an {@link AccessibilityEvent}, obtains and speaks a long
     * hover utterance.
     *
     * @param event The source event.
     */
    private void speakLongHover(AccessibilityEvent event) {
        // Never speak hint text if the tutorial is active
        if (AccessibilityTutorialActivity.isTutorialActive()) {
            LogUtils.log(this, Log.VERBOSE,
                    "Dropping long hover hint speech because tutorial is active.");
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        AccessibilityNodeInfoCompat source = record.getSource();

        if (source == null) {
            return;
        }

        // If this was a HOVER_ENTER event, we need to compute the focused node.
        // TODO: We're already doing this in the touch exploration formatter --
        // maybe this belongs there instead?
        if (event.getEventType() == AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER) {
            source = AccessibilityNodeInfoUtils.findFocusFromHover(mContext, source);
            if (source == null) {
                return;
            }
        }

        final CharSequence text = mRuleProcessor.getHintForNode(source);
        source.recycle();

        // Use QUEUE mode so that we don't interrupt more important messages.
        mSpeechController.speak(
                text, SpeechController.QUEUE_MODE_QUEUE, FeedbackItem.FLAG_NO_HISTORY, null);
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

    /** The event that will be read by the utterance complete action. */
    private AccessibilityEvent mPendingLongHoverEvent;

    /**
     * Starts the long hover timeout. Call this for every VIEW_HOVER
     * event.
     */
    private void postLongHoverRunnable(AccessibilityEvent event) {
        cancelLongHover();

        mPendingLongHoverEvent = AccessibilityEventCompatUtils.obtain(event);

        // The long hover timeout starts after the current text is spoken.
        mSpeechController.addUtteranceCompleteAction(
                mSpeechController.peekNextUtteranceId(), mLongHoverRunnable);
    }

    /**
     * Removes the long hover timeout and completion action. Call this
     * for every event.
     */
    private void cancelLongHover() {
        mHandler.cancelLongHoverTimeout();

        if (mPendingLongHoverEvent != null) {
            mSpeechController.removeUtteranceCompleteAction(mLongHoverRunnable);

            mPendingLongHoverEvent.recycle();
            mPendingLongHoverEvent = null;
        }
    }

    /**
     * Posts a delayed long hover action.
     */
    private final UtteranceCompleteRunnable mLongHoverRunnable = new UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
            // The utterance must have been spoken successfully.
            if (status != SpeechController.STATUS_SPOKEN) {
                return;
            }

            final AccessibilityEvent event = mPendingLongHoverEvent;
            if (event == null) {
                return;
            }

            mHandler.startLongHoverTimeout(event);
        }
    };

    private static class LongHoverHandler extends WeakReferenceHandler<ProcessorLongHover> {
        /** Message identifier for a verbose (long-hover) notification. */
        private static final int LONG_HOVER_TIMEOUT = 1;

        /** Timeout before reading a verbose (long-hover) notification. */
        private static final long DELAY_LONG_HOVER_TIMEOUT = 1000;

        public LongHoverHandler(ProcessorLongHover parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorLongHover parent) {
            switch (msg.what) {
                case LONG_HOVER_TIMEOUT: {
                    final AccessibilityEvent event = (AccessibilityEvent) msg.obj;
                    parent.speakLongHover(event);
                    event.recycle();
                    break;
                }
            }
        }

        public void startLongHoverTimeout(AccessibilityEvent event) {
            final AccessibilityEvent eventClone = AccessibilityEventCompatUtils.obtain(event);
            final Message msg = obtainMessage(LONG_HOVER_TIMEOUT, eventClone);

            sendMessageDelayed(msg, DELAY_LONG_HOVER_TIMEOUT);
        }

        public void cancelLongHoverTimeout() {
            removeMessages(LONG_HOVER_TIMEOUT);
        }
    }
}