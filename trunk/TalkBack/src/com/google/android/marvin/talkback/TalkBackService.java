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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link AccessibilityService} that provides spoken feedback.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkBackService extends AccessibilityService {

    /**
     * {@link Intent} broadcast action for announcing the notifications state.
     */
    public static final String ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND = "com.google.android.marvin.talkback.ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND";

    /**
     * Permission to send {@link Intent} broadcast commands to TalkBack.
     */
    private static final String SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK = "com.google.android.marvin.talkback.SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK";

    /**
     *  Tag for logging.
     */
    private static final String LOG_TAG = "TalkBackService";

    /**
     *  To account for SVox camel-case trouble.
     */
    private static final Pattern sCamelCasePrefixPattern = Pattern.compile("([a-z0-9])([A-Z])");

    /**
     *  To account for SVox camel-case trouble.
     */
    private static final Pattern sCamelCaseSuffixPattern = Pattern.compile("([A-Z])([a-z0-9])");

    /**
     *  Manages the pending notifications.
     */
    private static final NotificationCache sNotificationCache = new NotificationCache();

    /**
     *  timeout for waiting the events to settle down before speaking
     */
    private static final long EVENT_TIMEOUT = 100;

    /**
     *  Message type for sepaking.
     */
    private static final int MESSAGE_TYPE_SPEAK = 0;

    /**
     *  Space string constant.
     */
    private static final String SPACE = " ";

    /**
     *  Opening bracket character constant.
     */
    private static final char OPEN_SQUARE_BRACKET = '[';

    /**
     *  Closing bracket character constant.
     */
    private static final char CLOSE_SQUARE_BRACKET = ']';

    /**
     *  {@link IntentFilter} with all commands that can be executed by third party
     *  applications or services via intent broadcasting.
     */
    private static final IntentFilter sCommandInterfaceIntentFilter = new IntentFilter();
    static {
        sCommandInterfaceIntentFilter.addAction(ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND);
        // add other command intents here
    }

    // TODO(svetoslavganov): The hard coding of predefined punctuation leads
    // to internationalization problems. Refactor this while internationalizing.

    /**
     *  This table will force the TTS to speak out the punctuation by
     *  mapping punctuation to its spoken equivalent.
     */
    private static final HashMap<String, String> sSpokenEquivalentsMap = new HashMap<String, String>();
    static {
        sSpokenEquivalentsMap.put("?", "question mark");
        sSpokenEquivalentsMap.put(" ", "space");
        sSpokenEquivalentsMap.put(",", "comma");
        sSpokenEquivalentsMap.put(".", "dot");
        sSpokenEquivalentsMap.put("!", "exclamation");
        sSpokenEquivalentsMap.put("(", "open paren");
        sSpokenEquivalentsMap.put(")", "close paren");
        sSpokenEquivalentsMap.put("\"", "double quote");
        sSpokenEquivalentsMap.put(";", "semi-colon");
        sSpokenEquivalentsMap.put(":", "colon");
    }

    /**
     *  TalkBackService exposes package level convenience access to the context.
     */
    private static Context sContext;

    /**
     *  Used for intent based interaction with TalkBack
     */
    private CommandInterfaceBroadcastReceiver mCommandInterfaceBroadcastReceiver;

    /**
     *  Queuing mode - queue the utterance to be spoken.
     */
    public static final int QUEUING_MODE_QUEUE = 1;

    /**
     *  Queuing mode - interrupt the spoken before speaking.
     */
    public static final int QUEUING_MODE_INTERRUPT = 2;

    /**
     *  Queuing mode - compute the queuing mode depending on previous events.
     */
    public static final int QUEUING_MODE_AUTO_COMPUTE_FROM_EVENT_CONTEXT = 3;

    /**
     *  The maximal size to the queue of cached events.
     */
    private static final int EVENT_QUEUE_MAX_SIZE = 2;

    /**
     *  We keep the accessibility events to be processed. If a received event
     *  is the same type as the previous one it replaces the latter, otherwise
     *  it is added to the queue. All events in this queue are processed while
     *  we speak and this occurs after a certain timeout since the last
     *  received event.
     */
    private final ArrayList<AccessibilityEvent> mEventQueue = new ArrayList<AccessibilityEvent>();

    /**
     *  Temporary stores processed events to reduce holding the lock on the
     *  event queue.
     */
    private final ArrayList<AccessibilityEvent> mTempEventList = new ArrayList<AccessibilityEvent>();

    /**
     *  The TTS engine
     */
    private TextToSpeech mTts;

    /**
     *  processor for {@link AccessibilityEvent}s that populates {@link Utterance}s.
     */
    private SpeechRuleProcessor mSpeechRuleProcessor;

    /**
     *  The last event - used to auto-determine the speech queue mode.
     */
    private int mLastEventType;

    /**
     *  Watches the call state to controls speaking, thus we speak
     *  only when appropriate.    
     */
    private InCallWatcher mInCallWatcher;

    @Override
    public void onCreate() {
        super.onCreate();

        mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mTts.addEarcon(getString(R.string.earcon_progress),
                        "com.google.android.marvin.talkback", R.raw.progress);
            }
        });

        sContext = this;
        mSpeechRuleProcessor = new SpeechRuleProcessor(this, R.raw.speechstrategy);

        mCommandInterfaceBroadcastReceiver = new CommandInterfaceBroadcastReceiver();
        registerReceiver(mCommandInterfaceBroadcastReceiver, sCommandInterfaceIntentFilter);

        mInCallWatcher = new InCallWatcher();
        TelephonyManager telephonyManager = ((TelephonyManager) TalkBackService.asContext()
                .getSystemService(Context.TELEPHONY_SERVICE));
        telephonyManager.listen(mInCallWatcher, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mCommandInterfaceBroadcastReceiver);

        TelephonyManager telephonyManager = ((TelephonyManager) TalkBackService.asContext()
                .getSystemService(Context.TELEPHONY_SERVICE));
        telephonyManager.listen(mInCallWatcher, PhoneStateListener.LISTEN_NONE);

        mTts.shutdown();
    }

    @Override
    public void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.notificationTimeout = 0;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }

        synchronized (mEventQueue) {
            if (!mInCallWatcher.mIsInCall) {
                // not in call - process all events
                enqueueEventLocked(event, true);
                sendSpeakMessageLocked(QUEUING_MODE_AUTO_COMPUTE_FROM_EVENT_CONTEXT); 
            } else {
                // in call - cache all notifications without enforcing event queue size
                if (event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
                    enqueueEventLocked(event, false);       
                }
            }
        }
        return;
    }

    @Override
    public void onInterrupt() {
        mTts.stop();
    }

    /**
     * Enqueues the an <code>event</code>. The queuing operates as follows: </p>
     * 1. Events within the event timeout with type same as the last event
     * replace the latter. </br> 2. Events within the event timeout with type
     * different from the type of the last event are enqueued. </br> 3. All
     * events outside of the timeout are enqueued.
     *
     * @param event The event to enqueue.
     * @param enforceSize If to enforce the event queue size.
     */
    private void enqueueEventLocked(AccessibilityEvent event, boolean enforceSize) {
        AccessibilityEvent current = clone(event);
        ArrayList<AccessibilityEvent> eventQueue = mEventQueue;

        int lastIndex = eventQueue.size() - 1;
        if (lastIndex > -1) {
            AccessibilityEvent last = eventQueue.get(lastIndex);
            if (current.getEventType() == last.getEventType()) {
                eventQueue.set(lastIndex, current);
                return;
            }
        }

        eventQueue.add(current);

        if (enforceSize) {
            enforceEventQueueSize();
        }
    }

    /**
     * Enforces that the event queue is not more than
     * {@link #EVENT_QUEUE_MAX_SIZE}. The excessive events are
     * pruned through a FIFO strategy i.e. removing the oldest event first.
     */
    private void enforceEventQueueSize() {
        ArrayList<AccessibilityEvent> eventQueue = mEventQueue;
        while (eventQueue.size() > EVENT_QUEUE_MAX_SIZE) {
            eventQueue.remove(0);
        }
    }

    /**
     * Sends {@link #MESSAGE_TYPE_SPEAK} to the speech handler. This
     * method cancels the old message (if such exists) since it is
     * no longer relevant.
     * 
     * @param queueMode The queuing mode to be used while processing
     *       cached events.
     */
    public void sendSpeakMessageLocked(int queueMode) {
        Handler handler = mSpeechHandler;
        handler.removeMessages(MESSAGE_TYPE_SPEAK);
        Message message = handler.obtainMessage(MESSAGE_TYPE_SPEAK);
        message.arg1 = queueMode;
        handler.sendMessageDelayed(message, EVENT_TIMEOUT);
    }

    /**
     * Processes an <code>event</code> by asking the {@link SpeechRuleProcessor}
     * to match it against its rules and in case an utterance is generated it is
     * spoken. This method is responsible for recycling of the processed event.
     *
     * @param event The event to process.
     * @param queueMode The queuing mode to use while processing events.
     */
    private void processAndRecycleEvent(AccessibilityEvent event, int queueMode) {
        Log.d(LOG_TAG, "Processing event: " + event);

        Utterance utterance = Utterance.obtain();
        if (mSpeechRuleProcessor.processEvent(event, utterance)) {
            HashMap<String, Object> metadata = utterance.getMetadata();

            if (metadata.containsKey(Utterance.KEY_METADATA_QUEUING)) {
                // speech rules queue mode overrides the default TalkBack behavior
                // the case is safe since SpeechRule did the preprocessing
                queueMode = (Integer) metadata.get(Utterance.KEY_METADATA_QUEUING);
                Log.e("Test", "1" + queueMode);
            } else if (queueMode == QUEUING_MODE_AUTO_COMPUTE_FROM_EVENT_CONTEXT) {
                // if we are asked to compute the queue mode, we do so
                queueMode = (mLastEventType == event.getEventType() ? QUEUING_MODE_INTERRUPT
                    : QUEUING_MODE_QUEUE);
                Log.e("Test", "2" + queueMode);
            }

            if (isEarcon(utterance)) {
                String earcon = utterance.getText().toString();
                // earcons always use QUEUING_MODE_QUEUE
                mTts.playEarcon(earcon, QUEUING_MODE_QUEUE, null);
            } else {               
                mLastEventType = event.getEventType();
                cleanUpAndSpeak(utterance, queueMode);
            }

            utterance.recycle();
            event.recycle();
        }
    }

    /**
     * Returns the TalkBackService as a {@link Context}. This is a package level
     * convenience method.
     * 
     * @return The context.
     */
    static Context asContext() {
        return sContext;
    }

    /**
     * Updates the {@link NotificationCache}. If a notification is present in
     * the cache it is removed, otherwise it is added.
     * 
     * @param type The type of the notification.
     * @param text The notification text to be cached.
     */
    static void updateNotificationCache(NotificationType type, CharSequence text) {
        // if the cache has the notification - remove, otherwise add it
        if (!sNotificationCache.removeNotification(type, text.toString())) {
            sNotificationCache.addNotification(type, text.toString());
        }
    }

    /**
     * Clones an <code>event</code>.
     * 
     * @param event The event to clone.
     */
    private AccessibilityEvent clone(AccessibilityEvent event) {
        AccessibilityEvent clone = AccessibilityEvent.obtain();

        clone.setAddedCount(event.getAddedCount());
        clone.setBeforeText(event.getBeforeText());
        clone.setChecked(event.isChecked());
        clone.setClassName(event.getClassName());
        clone.setContentDescription(event.getContentDescription());
        clone.setCurrentItemIndex(event.getCurrentItemIndex());
        clone.setEventTime(event.getEventTime());
        clone.setEventType(event.getEventType());
        clone.setFromIndex(event.getFromIndex());
        clone.setFullScreen(event.isFullScreen());
        clone.setItemCount(event.getItemCount());
        clone.setPackageName(event.getPackageName());
        clone.setParcelableData(event.getParcelableData());
        clone.setPassword(event.isPassword());
        clone.setRemovedCount(event.getRemovedCount());
        clone.getText().clear();
        clone.getText().addAll(event.getText());

        return clone;
    }

    /**
     * Cleans up and speaks an <code>utterance</code>. The clean up is replacing
     * special strings with predefined mappings and reordering of some RegExp
     * matches to improve presentation. The <code>queueMode</code> determines if
     * speaking the event interrupts the speaking of previous events
     * {@link #QUEUING_MODE_INTERRUPT} or is queued {@link #QUEUING_MODE_QUEUE}.
     * 
     * @param utterance The utterance to speak.
     * @param queueMode The queue mode to use for speaking.
     */
    private void cleanUpAndSpeak(Utterance utterance, int queueMode) {
        String text = cleanUpString(utterance.getText().toString());
        mTts.speak(text, queueMode, null);
    }

    /**
     * Cleans up <code>text</text> by separating camel case words with space
     * to compensate for the not robust pronounciation of the SVOX TTS engine
     * and replacing the text with predefined strings.
     *
     * TODO(svetoslavganov): Hard coding a dependency on a TTS engine behavior
     *      is wrong. We have to move engine specific rules in a configuration
     *      file.
     * 
     * @param text The text to clean up.
     * @return The cleaned text.
     */
    private String cleanUpString(String text) {
        // use mapping first
        String cleanedText = sSpokenEquivalentsMap.get(text);

        if (cleanedText == null) {
            cleanedText = text;
            Matcher camelCasePrefix = sCamelCasePrefixPattern.matcher(cleanedText);
            cleanedText = camelCasePrefix.replaceAll("$1 $2");
            Matcher camelCaseSuffix = sCamelCaseSuffixPattern.matcher(cleanedText);
            cleanedText = camelCaseSuffix.replaceAll(" $1$2");
            cleanedText = cleanedText.replaceAll(" & ", " and ");
        }

        return cleanedText;
    }

    /**
     * Determines if an <code>utterance</code> refers to an earcon. The
     * convention is that earcons are enclosed in square brackets.
     * 
     * @param utterance The utterance.
     * @return True if the utterance is an earcon, false otherwise.
     */
    private boolean isEarcon(Utterance utterance) {
        StringBuilder text = utterance.getText();
        if (text.length() > 0) {
            return (text.charAt(0) == OPEN_SQUARE_BRACKET &&
                    text.charAt(text.length() - 1) == CLOSE_SQUARE_BRACKET);
        } else {
            return false;
        }
    }

    Handler mSpeechHandler = new Handler() {

        @Override
        public void handleMessage(Message message) {
            ArrayList<AccessibilityEvent> eventQueue = mEventQueue;
            ArrayList<AccessibilityEvent> events = mTempEventList;

            synchronized (eventQueue) {
                // pick the last events while holding a lock
                events.addAll(eventQueue);
                eventQueue.clear();
            }

            // now process all events and clean up
            Iterator<AccessibilityEvent> iterator = events.iterator();
            while (iterator.hasNext()) {
                AccessibilityEvent event = iterator.next();
                int queueMode = message.arg1;
                processAndRecycleEvent(event, queueMode);
                iterator.remove();
            }
        }
    };

    /**
     * This receives commands send as {@link Intent} broadcasts. This is useful
     * in driving TalkBack from other applications that have the right
     * permissions.
     */
    class CommandInterfaceBroadcastReceiver extends BroadcastReceiver {

        /**
         * {@inheritDoc BroadcastReceiver#onReceive(Context, Intent)}
         * 
         * @throws SecurityException if the user does not have
         *             com.google.android.marvin.talkback.
         *             SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK permission.
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            verifyCallerPermission(context, SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK);

            if (intent.getAction().equals(ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND)) {
                Utterance utterance = Utterance.obtain();
                utterance.getMetadata().put("temper", "RUDE");

                StringBuilder utteranceBuilder = utterance.getText();
                utteranceBuilder.append(getResources().getString(
                        R.string.value_notification_summary));
                utteranceBuilder.append(SPACE);
                utteranceBuilder.append(sNotificationCache.getFormattedSummary());

                cleanUpAndSpeak(utterance, QUEUING_MODE_INTERRUPT);
            }
            // other intent commands go here ...
        }

        /**
         * Verifies if the context of a caller has a certain permission.
         *
         * @param context the {@link Context}.
         * @param permissionName The permission name.
         */
        private void verifyCallerPermission(Context context, String permissionName) {
            int permissionState = context.checkPermission(permissionName, android.os.Process
                    .myPid(), 0);
            if (permissionState != PackageManager.PERMISSION_GRANTED) {
                String message = "Permission denied - " + permissionName;
                Log.e(LOG_TAG, message);
                throw new SecurityException(message);
            }
        }
    }

    /**
     * This class listens for the state of the {@link TelephonyManager} to
     * determine when to speak so speaking during a phone call is disabled.
     * Events received while speaking is not allowed are cached and as soon
     * as speaking is allowed these events are processed.
     */
    class InCallWatcher extends PhoneStateListener {

        boolean mIsInCall = false;

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state != TelephonyManager.CALL_STATE_IDLE) {
                // a call has started, so interrupt all the stuff that
                // speaks and stuff scheduled for speaking
                mIsInCall = true;
                mTts.speak("", QUEUING_MODE_INTERRUPT, null);
                mSpeechHandler.removeMessages(MESSAGE_TYPE_SPEAK);
            } else {
                mIsInCall = false;
                // we can speak now so announce all cached events with
                // no interruption
                synchronized (mSpeechHandler) {
                    sendSpeakMessageLocked(QUEUING_MODE_QUEUE);
                }
            }
        }
    }
}
