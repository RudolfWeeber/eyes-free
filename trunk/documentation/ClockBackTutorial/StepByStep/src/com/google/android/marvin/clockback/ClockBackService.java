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
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.SparseArray;
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

     //screen state broadcast related constants
    
    /** Feedback mapping index used as a key for the screen on broadcast */
    private static final int INDEX_SCREEN_ON = 0x00000100;

    /** Feedback mapping index used as a key for the screen off broadcast */
    private static final int INDEX_SCREEN_OFF = 0x00000200;

    // speech related constants

    /**
     * The queuing mode we are using - interrupt a spoken utterance before
     * speaking another one
     */
    private static final int QUEUING_MODE_INTERRUPT = 2;

    /** The empty string constant */
    private static final String SPACE = " ";

    /**
     * The class name of the number picker buttons with no text we want to
     * announce in the Clock application.
     */
    private static final String CLASS_NAME_NUMBER_PICKER_BUTTON_CLOCK = "android.widget.NumberPickerButton";

    /**
     * The class name of the number picker buttons with no text we want to
     * announce in the AlarmClock application.
     */
    private static final String CLASS_NAME_NUMBER_PICKER_BUTTON_ALARM_CLOCK = "com.android.internal.widget.NumberPickerButton";

    /**
     * The class name of the edit text box for hours and minutes we want to
     * better announce
     */
    private static final String CLASS_NAME_EDIT_TEXT = "android.widget.EditText";

    /**
     * Mapping from integer to string resource id where the keys are generated
     * from the {@link AccessibilityEvent#getItemCount()} and
     * {@link AccessibilityEvent#getCurrentItemIndex()} properties.
     */
    private static final SparseArray<Integer> sPositionMappedStringResourceIds = new SparseArray<Integer>();
    static {
        sPositionMappedStringResourceIds.put(11, R.string.value_plus);
        sPositionMappedStringResourceIds.put(114, R.string.value_plus);
        sPositionMappedStringResourceIds.put(112, R.string.value_minus);
        sPositionMappedStringResourceIds.put(116, R.string.value_minus);
        sPositionMappedStringResourceIds.put(111, R.string.value_hours);
        sPositionMappedStringResourceIds.put(115, R.string.value_minutes);
    }

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

    /** The {@link AudioManager} for detecting ringer state */
    private AudioManager mAudioManager;

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
                    mTts = new TextToSpeech(mContext, new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            // register here since to add earcons the TTS must be initialized
                            // the receiver is called immediately with the current ringer mode
                            registerBroadCastReceiver();
                        }
                    });
                    return;
                case WHAT_SHUTDOWN_TTS:
                    mTts.shutdown();
                    return;
            }
        }
    };

    /**
     * {@link BroadcastReceiver} for receiving updates for our context - device
     * state
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_ON.equals(action)) {
                provideScreenStateChangeFeedback(INDEX_SCREEN_ON);
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                provideScreenStateChangeFeedback(INDEX_SCREEN_OFF);
            } else {
                Log.w(LOG_TAG, "Registered for but not handling action " + action);
            }
        }

        /**
         * Provides feedback to announce the screen state change. Such a change
         * is turning the screen on or off.
         * 
         * @param feedbackIndex The index of the feedback in the statically
         *            mapped feedback resources.
         */
        private void provideScreenStateChangeFeedback(int feedbackIndex) {
            String utterance = generateScreenOnOrOffUtternace(feedbackIndex);
            mHandler.obtainMessage(WHAT_SPEAK, utterance).sendToTarget();
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

        //Create a filter with the broadcast intents we are interested in
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        // register for broadcasts of interest
        registerReceiver(mBroadcastReceiver, filter, null, null);

        // get the AudioManager and configure according the current ring mode
        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);

        setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);

        // we are in an initialized state now
        isInfrastructureInitialized = true;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (isInfrastructureInitialized) {
            // stop the TTS service
            mHandler.sendEmptyMessage(WHAT_SHUTDOWN_TTS);

            // unregister the intent broadcast receiver
            if (mBroadcastReceiver != null) {
                unregisterReceiver(mBroadcastReceiver);
            }

            // we are not in an initialized state anymore
            isInfrastructureInitialized = false;
        }
        return false;
    }

    /**
     * Registers the phone state observing broadcast receiver.
     */
    private void registerBroadCastReceiver() {
      //Create a filter with the broadcast intents we are interested in
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        // register for broadcasts of interest
        registerReceiver(mBroadcastReceiver, filter, null, null);
    }

    /**
     * Generates an utterance for announcing screen on and screen off.
     * 
     * @param feedbackIndex The feedback index for looking up feedback value.
     * @return The utterance.
     */
    private String generateScreenOnOrOffUtternace(int feedbackIndex) {
        // get the announce template
        int resourceId = (feedbackIndex == INDEX_SCREEN_ON) ? R.string.template_screen_on
                : R.string.template_screen_off;
        String template = mContext.getString(resourceId);

        // format the template with the ringer percentage
        int currentRingerVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
        int maxRingerVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_RING);
        int volumePercent = (100 / maxRingerVolume) * currentRingerVolume;

        // let us round to five so it sounds better
        int adjustment = volumePercent % 10;
        if (adjustment < 5) {
            volumePercent -= adjustment;
        } else if (adjustment > 5) {
            volumePercent += (10 - adjustment);
        }

        return String.format(template, volumePercent);
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

            // here we do a bit of enhancement of the UI presentation by using the semantic
            // of the event source in the context of the Clock application
            if (CLASS_NAME_EDIT_TEXT.equals(event.getClassName())) {
                // if the source is an edit text box and we have a mapping based on
                // its position in the items of the container parent of the event source
                // we append that value as well. We say "XX hours" and "XX minutes".
                String resourceValue = getPositionMappedStringResource(event.getItemCount(),
                        event.getCurrentItemIndex());
                if (resourceValue != null) {
                    utterance.append(resourceValue);
                }
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

        // No text and content description for the plus and minus buttons, so we lookup
        // custom values based on the event's itemCount and currentItemIndex properties.
        CharSequence className = event.getClassName();
        if (CLASS_NAME_NUMBER_PICKER_BUTTON_ALARM_CLOCK.equals(className)
                || CLASS_NAME_NUMBER_PICKER_BUTTON_CLOCK.equals(className)) {
            String resourceValue = getPositionMappedStringResource(event.getItemCount(),
                    event.getCurrentItemIndex());
            utterance.append(resourceValue);
        }

        return utterance.toString();
    }

    /**
     * Returns a string resource mapped for a given position based on
     * {@link AccessibilityEvent#getItemCount()} and
     * {@link AccessibilityEvent#getCurrentItemIndex()} properties.
     * 
     * @param itemCount The value of {@link AccessibilityEvent#getItemCount()}.
     * @param currentItemIndex The value of
     *            {@link AccessibilityEvent#getCurrentItemIndex()}.
     * @return The mapped string if such exists, null otherwise.
     */
    private String getPositionMappedStringResource(int itemCount, int currentItemIndex) {
        int lookupIndex = computeLookupIndex(itemCount, currentItemIndex);
        int resourceId = sPositionMappedStringResourceIds.get(lookupIndex);
        return getString(resourceId);
    }

    /**
     * Computes an index for looking up the custom text for views with neither
     * text not content description. The index is computed based on
     * {@link AccessibilityEvent#getItemCount()} and
     * {@link AccessibilityEvent#getCurrentItemIndex()} properties.
     * 
     * @param itemCount The number of all items in the event source.
     * @param currentItemIndex The index of the item source of the event.
     * @return The lookup index.
     */
    private int computeLookupIndex(int itemCount, int currentItemIndex) {
        int lookupIndex = itemCount;
        int divided = currentItemIndex;

        while (divided > 0) {
            lookupIndex *= 10;
            divided /= 10;
        }

        return (lookupIndex += currentItemIndex);
    }
}
