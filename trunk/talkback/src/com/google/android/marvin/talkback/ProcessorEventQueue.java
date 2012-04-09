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

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.talkback.TalkBackService.EventProcessor;
import com.google.android.marvin.talkback.formatter.EventSpeechRuleLoader;
import com.google.android.marvin.talkback.formatter.EventSpeechRuleProcessor;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Manages the event feedback queue. Queued events are run through the
 * {@link EventSpeechRuleProcessor} to generate spoken, haptic, and audible
 * feedback.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class ProcessorEventQueue implements EventProcessor {
    /** Manages pending speech events. */
    private final TalkBackHandler mSpeechHandler = new TalkBackHandler();

    /**
     * We keep the accessibility events to be processed. If a received event is
     * the same type as the previous one it replaces the latter, otherwise it is
     * added to the queue. All events in this queue are processed while we speak
     * and this occurs after a certain timeout since the last received event.
     */
    private final EventQueue mEventQueue = new EventQueue();

    private final CustomResourceMapper mCustomResourceMapper;
    private final PreferenceFeedbackController mFeedbackController;
    private final SpeechController mSpeechController;

    /**
     * Loader of speech rules.
     */
    private EventSpeechRuleLoader mSpeechRuleLoader;

    /**
     * Processor for {@link AccessibilityEvent}s that populates
     * {@link Utterance}s.
     */
    private EventSpeechRuleProcessor mEventSpeechRuleProcessor;

    private int mLastEventType;

    public ProcessorEventQueue(Context context, PreferenceFeedbackController feedbackController,
            SpeechController speechController) {
        mCustomResourceMapper = new CustomResourceMapper(context);
        mFeedbackController = feedbackController;
        mSpeechController = speechController;
        mEventSpeechRuleProcessor = new EventSpeechRuleProcessor(context);

        mSpeechRuleLoader =
                new EventSpeechRuleLoader(context.getPackageName(), mEventSpeechRuleProcessor);
        mSpeechRuleLoader.loadSpeechRules();
    }

    @Override
    public void process(AccessibilityEvent event) {
        synchronized (mEventQueue) {
            mEventQueue.add(event);
            mSpeechHandler.postSpeak(event);
        }
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
        // AccessibilityEvent.toString() is expensive, only do it if we have to!
        if (Log.DEBUG >= LogUtils.LOG_LEVEL) {
            LogUtils.log(TalkBackService.class, Log.DEBUG, "Processing event: %s", event.toString());
        }

        final Utterance utterance = Utterance.obtain();

        if (!mEventSpeechRuleProcessor.processEvent(event, utterance)) {
            // Failed to match event to a rule, so the utterance is empty.
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

        final Bundle params = metadata.getBundle(Utterance.KEY_METADATA_SPEECH_PARAMS);

        // Speak the text, if available.
        if (!TextUtils.isEmpty(utterance.getText())) {
            final QueuingMode queueMode = computeQueuingMode(utterance, event);
            final StringBuilder text = utterance.getText();

            mSpeechController.cleanUpAndSpeak(text, queueMode, params);
        }
    }

    /**
     * Computes the queuing mode for the current utterance.
     * 
     * @param utterance
     * @return A queuing mode, one of:
     *         <ul>
     *         <li>{@link QueuingMode#INTERRUPT}
     *         <li>{@link QueuingMode#QUEUE}
     *         <li>{@link QueuingMode#UNINTERRUPTIBLE}
     *         </ul>
     */
    private QueuingMode computeQueuingMode(Utterance utterance, AccessibilityEvent event) {
        final Bundle metadata = utterance.getMetadata();
        final int eventType = event.getEventType();
        final int mode;

        // Always collapse events of the same type.
        if (mLastEventType == eventType) {
            return QueuingMode.INTERRUPT;
        }

        mLastEventType = eventType;

        final int ordinal =
                metadata.getInt(Utterance.KEY_METADATA_QUEUING, QueuingMode.INTERRUPT.ordinal());

        return QueuingMode.valueOf(ordinal);
    }

    private class TalkBackHandler extends Handler {
        /** Speak action. */
        private static final int WHAT_SPEAK = 1;

        /** Timeout before speaking. */
        private static final long TIMEOUT_SPEAK = 0;

        @Override
        public void handleMessage(Message message) {
            final int what = message.what;

            switch (what) {
                case WHAT_SPEAK:
                    processAllEvents();
                    break;
            }
        }

        /**
         * Attempts to process all events in the queue.
         */
        private void processAllEvents() {
            while (true) {
                final AccessibilityEvent event;

                synchronized (mEventQueue) {
                    if (mEventQueue.isEmpty()) {
                        return;
                    }

                    event = mEventQueue.remove(0);
                }

                processAndRecycleEvent(event);
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
