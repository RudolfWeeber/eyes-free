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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService.EventListener;
import com.google.android.marvin.talkback.formatter.EventSpeechRuleProcessor;
import com.googlecode.eyesfree.utils.FeedbackController;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.WeakReferenceHandler;

/**
 * Manages the event feedback queue. Queued events are run through the
 * {@link EventSpeechRuleProcessor} to generate spoken, haptic, and audible
 * feedback.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class ProcessorEventQueue implements EventListener {
    /** Manages pending speech events. */
    private final ProcessorEventHandler mHandler = new ProcessorEventHandler(this);

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

    private final CustomResourceMapper mCustomResourceMapper;
    private final FeedbackController mFeedbackController;
    private final SpeechController mSpeechController;

    /**
     * Processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private EventSpeechRuleProcessor mEventSpeechRuleProcessor;

    private int mLastEventType;

    public ProcessorEventQueue(TalkBackService context) {
        mCustomResourceMapper = new CustomResourceMapper(context);
        mFeedbackController = context.getFeedbackController();
        mSpeechController = context.getSpeechController();
        mEventSpeechRuleProcessor = new EventSpeechRuleProcessor(context);

        loadDefaultRules();
    }

    @Override
    public void process(AccessibilityEvent event) {
        synchronized (mEventQueue) {
            mEventQueue.add(event);
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
        } else if (Build.VERSION.SDK_INT >= 5) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_eclair);
        } else if (Build.VERSION.SDK_INT >= 4) {
            mEventSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_donut);
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

        // Play all earcons.
        for (int resId : utterance.getEarcons()) {
            mFeedbackController.playSound(resId, earconRate, earconVolume);
        }

        // Play all vibration patterns.
        for (int resId : utterance.getVibrationPatterns()) {
            mFeedbackController.playVibration(resId);
        }

        // Retrieve and play all custom earcons.
        for (int prefId : utterance.getCustomEarcons()) {
            final int resId = mCustomResourceMapper.getResourceIdForPreference(prefId);

            if (resId > 0) {
                mFeedbackController.playSound(resId, earconRate, earconVolume);
            }
        }

        // Retrieve and play all custom vibrations.
        for (int prefId : utterance.getCustomVibrations()) {
            final int resId = mCustomResourceMapper.getResourceIdForPreference(prefId);

            if (resId > 0) {
                mFeedbackController.playVibration(resId);
            }
        }

        // Speak the text.  Utterances with empty speech will be filtered at the SpeechController.
        final Bundle params = metadata.getBundle(Utterance.KEY_METADATA_SPEECH_PARAMS);
        final int queueMode = computeQueuingMode(utterance, event);
        final StringBuilder text = utterance.getText();

        mSpeechController.cleanUpAndSpeak(text, queueMode, 0, params);
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

                    event = parent.mEventQueue.remove(0);
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
