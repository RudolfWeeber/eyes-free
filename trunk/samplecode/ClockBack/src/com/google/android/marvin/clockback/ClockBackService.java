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
import android.os.Vibrator;
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
 *     Providing application specific feedback &mdash; the service handles only
 *     accessibility events from the clock application.
 *   </li>
 *   <li>
 *     Providing dynamic, context-dependent feedback &mdash; feedback type changes
 *     depending on the ringer state.
 *   </li>
 *   <li>
 *     Application specific UI enhancement - application domain knowledge is
 *     utilized to enhance the provided feedback.
 *   </li>
 * </ol>
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class ClockBackService extends AccessibilityService {

    /** Tag for logging from this service. */
    private static final String LOG_TAG = "ClockBackService";

    // fields for configuring how the system handles this accessibility service

    /** Minimal timeout between accessibility events we want to receive. */
    private static final int EVENT_NOTIFICATION_TIMEOUT_MILLIS = 80;

    /** Packages we are interested in. */
    // This works with AlarmClock and Clock whose package name changes in different releases
    private static final String[] PACKAGE_NAMES = new String[] {
            "com.android.alarmclock", "com.google.android.deskclock", "com.android.deskclock"
    };

    // message types we are passing around

    /** Speak. */
    private static final int WHAT_SPEAK = 1;

    /** Stop speaking. */
    private static final int WHAT_STOP_SPEAK = 2;

    /** Start the TTS service. */
    private static final int WHAT_START_TTS = 3;

    /** Stop the TTS service. */
    private static final int WHAT_SHUTDOWN_TTS = 4;

    /** Play an earcon. */
    private static final int WHAT_PLAY_EARCON = 5;

    /** Stop playing an earcon. */
    private static final int WHAT_STOP_PLAY_EARCON = 6;

    /** Vibrate a pattern. */
    private static final int WHAT_VIBRATE = 7;

    /** Stop vibrating. */
    private static final int WHAT_STOP_VIBRATE = 8;

    //screen state broadcast related constants

    /** Feedback mapping index used as a key for the screen on broadcast. */
    private static final int INDEX_SCREEN_ON = 0x00000100;

    /** Feedback mapping index used as a key for the screen off broadcast. */
    private static final int INDEX_SCREEN_OFF = 0x00000200;

    // ringer mode change related constants

    /** Feedback mapping index used as a key for normal ringer mode. */
    private static final int INDEX_RINGER_NORMAL = 0x00000400;

    /** Feedback mapping index used as a key for vibration ringer mode. */
    private static final int INDEX_RINGER_VIBRATE = 0x00000800;

    /** Feedback mapping index used as a key for silent ringer mode. */
    private static final int INDEX_RINGER_SILENT = 0x00001000;

    // speech related constants

    /**
     * The queuing mode we are using - interrupt a spoken utterance before
     * speaking another one.
     */
    private static final int QUEUING_MODE_INTERRUPT = 2;

    /** The space string constant. */
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
     * better announce.
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

    /** Mapping from integers to vibration patterns for haptic feedback. */
    private static final SparseArray<long[]> sVibrationPatterns = new SparseArray<long[]>();
    static {
        sVibrationPatterns.put(AccessibilityEvent.TYPE_VIEW_CLICKED, new long[] {
                0L, 100L
        });
        sVibrationPatterns.put(AccessibilityEvent.TYPE_VIEW_SELECTED, new long[] {
                0L, 15L, 10L, 15L
        });
        sVibrationPatterns.put(AccessibilityEvent.TYPE_VIEW_FOCUSED, new long[] {
                0L, 15L, 10L, 15L
        });
        sVibrationPatterns.put(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, new long[] {
                0L, 25L, 50L, 25L, 50L, 25L
        });
        sVibrationPatterns.put(INDEX_SCREEN_ON, new long[] {
                0L, 10L, 10L, 20L, 20L, 30L
        });
        sVibrationPatterns.put(INDEX_SCREEN_OFF, new long[] {
                0L, 30L, 20L, 20L, 10L, 10L
        });
    }

    /** Mapping from integers to raw sound resource ids. */
    private static SparseArray<Integer> sSoundsResourceIds = new SparseArray<Integer>();
    static {
        sSoundsResourceIds.put(AccessibilityEvent.TYPE_VIEW_CLICKED, R.raw.sound1);
        sSoundsResourceIds.put(AccessibilityEvent.TYPE_VIEW_SELECTED, R.raw.sound2);
        sSoundsResourceIds.put(AccessibilityEvent.TYPE_VIEW_FOCUSED, R.raw.sound2);
        sSoundsResourceIds.put(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, R.raw.sound3);
        sSoundsResourceIds.put(INDEX_SCREEN_ON, R.raw.sound4);
        sSoundsResourceIds.put(INDEX_SCREEN_OFF, R.raw.sound5);
        sSoundsResourceIds.put(INDEX_RINGER_SILENT, R.raw.sound6);
        sSoundsResourceIds.put(INDEX_RINGER_VIBRATE, R.raw.sound7);
        sSoundsResourceIds.put(INDEX_RINGER_NORMAL, R.raw.sound8);
    }

    // sound pool related member fields

    /** Mapping from integers to earcon names - dynamically populated. */
    private final SparseArray<String> mEarconNames = new SparseArray<String>();

    // auxiliary fields

    /**
     * Handle to this service to enable inner classes to access the {@link Context}.
     */
    private Context mContext;

    /** The feedback this service is currently providing. */
    private int mProvidedFeedbackType;

    /** Reusable instance for building utterances. */
    private final StringBuilder mUtterance = new StringBuilder();

    // feedback providing services

    /** The {@link TextToSpeech} used for speaking. */
    private TextToSpeech mTts;

    /** The {@link AudioManager} for detecting ringer state. */
    private AudioManager mAudioManager;

    /** Vibrator for providing haptic feedback. */
    private Vibrator mVibrator;

    /** Flag if the infrastructure is initialized. */
    private boolean isInfrastructureInitialized;

    /** {@link Handler} for executing messages on the service main thread. */
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
                case WHAT_PLAY_EARCON:
                    int resourceId = message.arg1;
                    playEarcon(resourceId);
                    return;
                case WHAT_STOP_PLAY_EARCON:
                    mTts.stop();
                    return;
                case WHAT_VIBRATE:
                    int key = message.arg1;
                    long[] pattern = sVibrationPatterns.get(key);
                    mVibrator.vibrate(pattern, -1);
                    return;
                case WHAT_STOP_VIBRATE:
                    mVibrator.cancel();
                    return;
            }
        }
    };

    /**
     * {@link BroadcastReceiver} for receiving updates for our context - device
     * state.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (AudioManager.RINGER_MODE_CHANGED_ACTION.equals(action)) {
                int ringerMode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,
                        AudioManager.RINGER_MODE_NORMAL);
                configureForRingerMode(ringerMode);
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
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
            // we take a specific action depending on the feedback we currently provide
            switch (mProvidedFeedbackType) {
                case AccessibilityServiceInfo.FEEDBACK_SPOKEN:
                    String utterance = generateScreenOnOrOffUtternace(feedbackIndex);
                    mHandler.obtainMessage(WHAT_SPEAK, utterance).sendToTarget();
                    return;
                case AccessibilityServiceInfo.FEEDBACK_AUDIBLE:
                    mHandler.obtainMessage(WHAT_PLAY_EARCON, feedbackIndex, 0).sendToTarget();
                    return;
                case AccessibilityServiceInfo.FEEDBACK_HAPTIC:
                    mHandler.obtainMessage(WHAT_VIBRATE, feedbackIndex, 0).sendToTarget();
                    return;
                default:
                    throw new IllegalStateException("Unexpected feedback type "
                            + mProvidedFeedbackType);
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

        // get the vibrator service
        mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);

        // get the AudioManager and configure according the current ring mode
        mAudioManager = (AudioManager) getSystemService(Service.AUDIO_SERVICE);
        // In Froyo the broadcast receiver for the ringer mode is called back with the
        // current state upon registering but in Eclair this is not done so we poll here
        int ringerMode = mAudioManager.getRingerMode();
        configureForRingerMode(ringerMode);

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
     * Configures the service according to a ringer mode. Possible
     * configurations:
     * <p>
     *   1. {@link AudioManager#RINGER_MODE_SILENT}<br/>
     *   Goal:     Provide only custom haptic feedback.<br/>
     *   Approach: Take over the haptic feedback by configuring this service to to provide
     *             such and do so. This way the system will not call the default haptic
     *             feedback service KickBack.<br/>
     *             Take over the audible and spoken feedback by configuring this
     *             service to provide such feedback but not doing so. This way the system
     *             will not call the default spoken feedback service TalkBack and the
     *             default audible feedback service SoundBack.
     * </p>
     * <p>
     *   2. {@link AudioManager#RINGER_MODE_VIBRATE}<br/>
     *   Goal:     Provide custom audible and default haptic feedback.</p>
     *   Approach: Take over the audible feedback and provide custom one.</p>
     *             Take over the spoken feedback but do not provide such.<br/>
     *             Let some other service provide haptic feedback (KickBack).
     * </p>
     * <p>
     *   3. {@link AudioManager#RINGER_MODE_NORMAL}
     *   Goal:     Provide custom spoken, default audible and default haptic feedback.<br/>
     *   Approach: Take over the spoken feedback and provide custom one.<br/>
     *             Let some other services provide audible feedback (SounBack) and haptic
     *             feedback (KickBack).
     * </p>
     * Note: In the above description an assumption is made that all default feedback
     *       services are enabled. Such services are TalkBack, SoundBack, and KickBack.
     *       Also the feature of defining a service as the default for a given feedback
     *       type will be available in Froyo and after. For previous releases the package
     *       specific accessibility service must be registered first i.e. checked in the
     *       settings.
     *
     * @param ringerMode The device ringer mode.
     */
    private void configureForRingerMode(int ringerMode) {
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            // when the ringer is silent we want to provide only haptic feedback
            mProvidedFeedbackType = AccessibilityServiceInfo.FEEDBACK_HAPTIC;

            // take over the spoken and sound feedback so no such feedback is provided
            setServiceInfo(AccessibilityServiceInfo.FEEDBACK_HAPTIC
                    | AccessibilityServiceInfo.FEEDBACK_SPOKEN
                    | AccessibilityServiceInfo.FEEDBACK_AUDIBLE);

            // use only an earcon to announce ringer state change
            mHandler.obtainMessage(WHAT_PLAY_EARCON, INDEX_RINGER_SILENT, 0).sendToTarget();
        } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            // when the ringer is vibrating we want to provide only audible
            // feedback
            mProvidedFeedbackType = AccessibilityServiceInfo.FEEDBACK_AUDIBLE;

            // take over the spoken feedback so no spoken feedback is provided
            setServiceInfo(AccessibilityServiceInfo.FEEDBACK_AUDIBLE
                    | AccessibilityServiceInfo.FEEDBACK_SPOKEN);

            // use only an earcon to announce ringer state change
            mHandler.obtainMessage(WHAT_PLAY_EARCON, INDEX_RINGER_VIBRATE, 0).sendToTarget();
        } else if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            // when the ringer is ringing we want to provide spoken feedback
            // overriding the default spoken feedback
            mProvidedFeedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
            setServiceInfo(AccessibilityServiceInfo.FEEDBACK_SPOKEN);

            // use only an earcon to announce ringer state change
            mHandler.obtainMessage(WHAT_PLAY_EARCON, INDEX_RINGER_NORMAL, 0).sendToTarget();
        }
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
        Log.i(LOG_TAG, mProvidedFeedbackType + " " + event.toString());

        // here we act according to the feedback type we are currently providing
        if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_SPOKEN) {
            mHandler.obtainMessage(WHAT_SPEAK, formatUtterance(event)).sendToTarget();
        } else if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_AUDIBLE) {
            mHandler.obtainMessage(WHAT_PLAY_EARCON, event.getEventType(), 0).sendToTarget();
        } else if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_HAPTIC) {
            mHandler.obtainMessage(WHAT_VIBRATE, event.getEventType(), 0).sendToTarget();
        } else {
            throw new IllegalStateException("Unexpected feedback type " + mProvidedFeedbackType);
        }
    }

    @Override
    public void onInterrupt() {
        // here we act according to the feedback type we are currently providing
        if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_SPOKEN) {
            mHandler.obtainMessage(WHAT_STOP_SPEAK);
        } else if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_AUDIBLE) {
            mHandler.obtainMessage(WHAT_STOP_PLAY_EARCON);
        } else if (mProvidedFeedbackType == AccessibilityServiceInfo.FEEDBACK_HAPTIC) {
            mHandler.obtainMessage(WHAT_STOP_VIBRATE);
        } else {
            throw new IllegalStateException("Unexpected feedback type " + mProvidedFeedbackType);
        }
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

    /**
     * Plays an earcon given its id.
     *
     * @param earconId The id of the earcon to be played.
     */
    private void playEarcon(int earconId) {
        String earconName = mEarconNames.get(earconId);
        if (earconName == null) {
            // we do not know the sound id, hence we need to load the sound
            int resourceId = sSoundsResourceIds.get(earconId);
            earconName = "[" + earconId + "]";
            mTts.addEarcon(earconName, getPackageName(), resourceId);
            mEarconNames.put(earconId, earconName);
        }

        mTts.playEarcon(earconName, QUEUING_MODE_INTERRUPT, null);
    }
}
