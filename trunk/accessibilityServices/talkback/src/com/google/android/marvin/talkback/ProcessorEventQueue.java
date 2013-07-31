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

import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.google.android.marvin.talkback.formatter.EventSpeechRuleProcessor;
import com.google.android.marvin.talkback.test.TalkBackListener;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

/**
 * Manages the event feedback queue. Queued events are run through the
 * {@link EventSpeechRuleProcessor} to generate spoken, haptic, and audible
 * feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class ProcessorEventQueue implements AccessibilityEventListener {
    /** Manages pending speech events. */
    private final ProcessorEventHandler mHandler = new ProcessorEventHandler(this);

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

    private final SpeechController mSpeechController;

    /**
     * Processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private EventSpeechRuleProcessor mEventSpeechRuleProcessor;

    /** TalkBack-specific listener used for testing. */
    private TalkBackListener mTestingListener;

    /** Event type for the most recently processed event. */
    private int mLastEventType;

    /** Event time for the most recent window state changed event. */
    private long mLastWindowStateChanged = 0;

    public ProcessorEventQueue(TalkBackService context) {
        mSpeechController = context.getSpeechController();
        mEventSpeechRuleProcessor = new EventSpeechRuleProcessor(context);

        loadDefaultRules();
    }

    public void setTestingListener(TalkBackListener testingListener) {
        mTestingListener = testingListener;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        final int eventType = event.getEventType();

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            mLastWindowStateChanged = SystemClock.uptimeMillis();
        }

        synchronized (mEventQueue) {
            mEventQueue.enqueue(event);
            mHandler.postSpeak(event);
        }
    }


    /**
     * Loads default speech strategies based on the current SDK version.
     */
    private void loadDefaultRules() {
        // Add version-specific speech strategies for semi-bundled apps.
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_apps);
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_googletv);

        // Add platform-specific speech strategies for bundled apps.
        if (Build.VERSION.SDK_INT >= 16) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_jellybean);
        } else if (Build.VERSION.SDK_INT >= 14) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_ics);
        } else if (Build.VERSION.SDK_INT >= 11) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_honeycomb);
        } else if (Build.VERSION.SDK_INT >= 9) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_gingerbread);
        } else if (Build.VERSION.SDK_INT >= 8) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_froyo);
        }

        // Add generic speech strategy. This should always be added last so that
        // the app-specific rules above can override the generic rules.
        mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy);
    }

    /**
     * Processes an <code>event</code> by asking the
     * {@link EventSpeechRuleProcessor} to match it against its rules and in
     * case an utterance is generated it is spoken. This method is responsible
     * for recycling of the processed event.
     *
     * @param event The event to process.
     */
    private void processAndRecycleEvent(AccessibilityEvent event) {
        LogUtils.log(this, Log.DEBUG, "Processing event: %s", event);

        final Utterance utterance = Utterance.obtain();

        if (!mEventSpeechRuleProcessor.processEvent(event, utterance)) {
            // Failed to match event to a rule, so the utterance is empty.
            LogUtils.log(this, Log.WARN, "Failed to process event");
            utterance.recycle();
            return;
        }

        if (mTestingListener != null) {
            mTestingListener.onUtteranceQueued(utterance);
        }

        provideFeedbackForUtterance(event, utterance);

        utterance.recycle();
        event.recycle();
    }

    /**
     * Provides feedback for the specified utterance.
     *
     * @param event The source event.
     * @param utterance The utterance to provide feedback for.
     */
    private void provideFeedbackForUtterance(AccessibilityEvent event, Utterance utterance) {
        final Bundle metadata = utterance.getMetadata();
        final float earconRate = metadata.getFloat(Utterance.KEY_METADATA_EARCON_RATE, 1.0f);
        final float earconVolume = metadata.getFloat(Utterance.KEY_METADATA_EARCON_VOLUME, 1.0f);
        final Bundle nonSpeechMetadata = new Bundle();
        nonSpeechMetadata.putFloat(Utterance.KEY_METADATA_EARCON_RATE, earconRate);
        nonSpeechMetadata.putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, earconVolume);

        // Retrieve and play all spoken text.
        final CharSequence textToSpeak = StringBuilderUtils.getAggregateText(utterance.getSpoken());
        final int queueMode = computeQueuingMode(utterance, event);
        final int flags = metadata.getInt(Utterance.KEY_METADATA_SPEECH_FLAGS, 0);
        final Bundle speechMetadata = metadata.getBundle(Utterance.KEY_METADATA_SPEECH_PARAMS);

        mSpeechController.speak(textToSpeak, utterance.getAuditory(), utterance.getHaptic(),
                queueMode, flags, speechMetadata, nonSpeechMetadata);
    }

    /**
     * Computes the queuing mode for the current utterance.
     *
     * @param utterance
     * @return A queuing mode, one of:
     *         <ul>
     *         <li>{@link SpeechController#QUEUE_MODE_INTERRUPT}
     *         <li>{@link SpeechController#QUEUE_MODE_QUEUE}
     *         <li>{@link SpeechController#QUEUE_MODE_UNINTERRUPTIBLE}
     *         </ul>
     */
    private int computeQueuingMode(Utterance utterance, AccessibilityEvent event) {
        final Bundle metadata = utterance.getMetadata();
        final int eventType = event.getEventType();
        final int mode;

        // Queue events that occur automatically after window state changes.
        if (((event.getEventType() & TalkBackService.AUTOMATIC_AFTER_STATE_CHANGE) != 0)
                && ((event.getEventTime() - mLastWindowStateChanged)
                        < TalkBackService.DELAY_AUTO_AFTER_STATE)) {
            return SpeechController.QUEUE_MODE_QUEUE;
        }

        // Always collapse events of the same type.
        if (mLastEventType == eventType) {
            return SpeechController.QUEUE_MODE_INTERRUPT;
        }

        mLastEventType = eventType;

        final int queueMode = metadata.getInt(
                Utterance.KEY_METADATA_QUEUING, SpeechController.QUEUE_MODE_INTERRUPT);

        return queueMode;
    }

    private static class ProcessorEventHandler extends WeakReferenceHandler<ProcessorEventQueue> {
        /** Speak action. */
        private static final int WHAT_SPEAK = 1;

        /** Timeout before speaking. */
        private static final long TIMEOUT_SPEAK = 0;

        public ProcessorEventHandler(ProcessorEventQueue parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message message, ProcessorEventQueue parent) {
            switch (message.what) {
                case WHAT_SPEAK:
                    processAllEvents(parent);
                    break;
            }
        }

        /**
         * Attempts to process all events in the queue.
         */
        private void processAllEvents(ProcessorEventQueue parent) {
            while (true) {
                final AccessibilityEvent event;

                synchronized (parent.mEventQueue) {
                    if (parent.mEventQueue.isEmpty()) {
                        return;
                    }

                    event = parent.mEventQueue.dequeue();
                }

                parent.processAndRecycleEvent(event);
            }
        }

        /**
         * Sends {@link #WHAT_SPEAK} to the speech handler. This method cancels
         * the old message (if such exists) since it is no longer relevant.
         *
         * @param event The event to speak.
         */
        public void postSpeak(AccessibilityEvent event) {
            final Message message = obtainMessage(WHAT_SPEAK);

            removeMessages(WHAT_SPEAK);
            sendMessageDelayed(message, TIMEOUT_SPEAK);
        }
    }
}
