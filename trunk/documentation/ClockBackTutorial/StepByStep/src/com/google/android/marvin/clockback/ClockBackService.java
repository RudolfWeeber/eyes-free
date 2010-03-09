/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.marvin.clockback;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

/**
 * This class is an {@link AccessibilityService} that provides custom feedback
 * for the Clock application that comes by default with Android devices. It
 * demonstrates the following key features of the Android accessibility APIs:
 * <ol>
 *   <li>
 *     Simple demonstration of how to use the accessibility APIs.
 *   </li>
 *   <li>
 *     Hands-on example of various ways to utilize the accessibility API for
 *     providing alternative and complementary feedback.
 *   </li>
 *   <li>
 *     Providing application specific feedback - the service handles only
 *     accessibility events from the clock application.
 *   </li>
 *   <li>
 *     Providing dynamic, context-dependent feedback - feedback type changes
 *     depending on the ringer state.</li>
 *   <li>
 *     Application specific UI enhancement - application domain knowledge is
 *     utilized to enhance the provided feedback.
 *   </li>
 * </ol>
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class ClockBackService extends AccessibilityService {

    /** Tag for logging from this service */
    private static final String LOG_TAG = "ClockBackService";

    // fields for configuring how the system handles this accessibility service

    /** Minimal timeout between accessibility events we want to receive */
    private static final int EVENT_NOTIFICATION_TIMEOUT_MILLIS = 80;

    /** Packages we are interested in */
    // This works with AlarmClock and Clock whose package name changes in different releases
    private static final String[] PACKAGE_NAMES = new String[] {
            "com.android.alarmclock", "com.google.android.deskclock", "com.android.deskclock"
    };

    // message types we are passing around

    /** Speak */
    private static final int WHAT_SPEAK = 1;

    /** Stop speaking */
    private static final int WHAT_STOP_SPEAK = 2;

    /** Start the TTS service */
    private static final int WHAT_START_TTS = 3;

    /** Stop the TTS service */
    private static final int WHAT_SHUTDOWN_TTS = 4;

    // speech related constants

    /**
     * The queuing mode we are using - interrupt a spoken utterance before
     * speaking another one
     */
    private static final int QUEUING_MODE_INTERRUPT = 2;

    /** The empty string constant */
    private static final String SPACE = " ";

    // auxiliary fields

    /**
     * Handle to this service to enable inner classes to access the {@link Context}
     */
    private Context mContext;

    /** Reusable instance for building utterances */
    private final StringBuilder mUtterance = new StringBuilder();

    // feedback providing services

    /** The {@link TextToSpeech} used for speaking */
    private TextToSpeech mTts;

    /** Flag if the infrastructure is initialized */
    private boolean isInfrastructureInitialized;

    /** {@link Handler} for executing messages on the service main thread */
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case WHAT_SPEAK:
                    String utterance = (String) message.obj;
                    mTts.speak(utterance, QUEUING_MODE_INTERRUPT, null);
                    return;
                case WHAT_STOP_SPEAK:
                    mTts.stop();
                    return;
                case WHAT_START_TTS:
                    mTts = new TextToSpeech(mContext, null);
                    return;
                case WHAT_SHUTDOWN_TTS:
                    mTts.shutdown();
                    return;
            }
        }
    };

    @Override
    public void onServiceConnected() {
        if (isInfrastructureInitialized) {
            return;
        }

        mContext = this;

        // send a message to start the TTS
        mHandler.sendEmptyMessage(WHAT_START_TTS);

        setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);

        // we are in an initialized state now
        isInfrastructureInitialized = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (isInfrastructureInitialized) {
            // stop the TTS service
            mHandler.sendEmptyMessage(WHAT_SHUTDOWN_TTS);

            // we are not in an initialized state anymore
            isInfrastructureInitialized = false;
        }
        return false;
    }

    /**
     * Sets the {@link AccessibilityServiceInfo} which informs the system how to
     * handle this {@link AccessibilityService}.
     * 
     * @param feedbackType The type of feedback this service will provide. </p>
     *            Note: The feedbackType parameter is an bitwise or of all
     *            feedback types this service would like to provide.
     */
    private void setServiceInfo(int feedbackType) {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // we are interested in all types of accessibility events
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        // we want to provide specific type of feedback
        info.feedbackType = feedbackType;
        // we want to receive events in a certain interval
        info.notificationTimeout = EVENT_NOTIFICATION_TIMEOUT_MILLIS;
        // we want to receive accessibility events only from certain packages
        info.packageNames = PACKAGE_NAMES;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        Log.i(LOG_TAG, event.toString());

        mHandler.obtainMessage(WHAT_SPEAK, formatUtterance(event)).sendToTarget();
    }

    @Override
    public void onInterrupt() {
        mHandler.obtainMessage(WHAT_STOP_SPEAK);
    }

    /**
     * Formats an utterance from an {@link AccessibilityEvent}.
     *
     * @param event The event from which to format an utterance.
     * @return The formatted utterance.
     */
    private String formatUtterance(AccessibilityEvent event) {
        StringBuilder utterance = mUtterance;

        // clear the utterance before appending the formatted text
        utterance.delete(0, utterance.length());

        List<CharSequence> eventText = event.getText();

        // We try to get the event text if such
        if (!eventText.isEmpty()) {
            for (CharSequence subText : eventText) {
                utterance.append(subText);
                utterance.append(SPACE);
            }

            return utterance.toString();
        }

        // There is no event text but we try to get the content description which is
        // an optional attribute for describing a view (typically used with ImageView)
        CharSequence contentDescription = event.getContentDescription();
        if (contentDescription != null) {
            utterance.append(contentDescription);
            return utterance.toString();
        }

        return utterance.toString();
    }
}
