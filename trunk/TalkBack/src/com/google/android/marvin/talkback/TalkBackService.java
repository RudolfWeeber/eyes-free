/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Parcelable;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.ToggleButton;

/**
 * {@link AccessibilityService} that provides spoken feedback.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkBackService extends AccessibilityService {
    private static final String MAGIC_SPLITTER = "____";

    // Earcons
    private static final String PROGRESS_EARCON = "[PROGRESS]";

    // To account for SVox camel-case trouble:
    private static final Pattern CamelCasePrefixPattern = Pattern.compile("([a-z0-9])([A-Z])");

    private static final Pattern CamelCaseSuffixPattern = Pattern.compile("([A-Z])([a-z0-9])");

    static final int INTERRUPTIBLE = 1;

    /**
     * {@link Intent} broadcast action for announcing the notifications state.
     */
    public static final String ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND = "com.google.android.marvin.talkback.ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND";

    /**
     * Permission to send {@link Intent} broadcast commands to TalkBack.
     */
    private static final String SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK = "com.google.android.marvin.talkback.SEND_INTENT_BROADCAST_COMMANDS_TO_TALKBACK";

    // intent filter with all commands that can be executed by third party
    // applications or services via intent broadcasting.
    private static final IntentFilter sCommandInterfaceIntentFilter = new IntentFilter();
    static {
        sCommandInterfaceIntentFilter.addAction(ACTION_ANNOUNCE_STATUS_SUMMARY_COMMAND);
        // add other command intents here
    }

    // these classes are either not exposed in the public API (android.jar) or
    // not visible
    private static final String HANDLE_VIEW_CLASS_NAME = "com.android.launcher.HandleView";

    private static final String STATUS_BAR_EXPANDED_DIALOG_CLASS_NAME = "com.android.server.status.StatusBarService$ExpandedDialog";

    private static final String ICON_MENU_VIEW_CLASS_NAME = "com.android.internal.view.menu.IconMenuView";

    private static final String SOFT_INPUT_WINDOW_CLASS_NAME = "android.inputmethodservice.SoftInputWindow";

    private static final String AUTO_COMPLETE_TEXT_VIEW_DROP_DOWN_CLASS_NAME = "android.widget.AutoCompleteTextView$DropDownListView";

    // some of these are fragile (numbers are auto generated) - some are not
    // included in android.jar
    // not included in android.jar
    // this is the value of: com.android.mms.R#stat_notify_sms
    private static final int SMS_ICON = 0x7F020036;

    // not included in android.jar
    // this is the value of: com.android.mms.R#stat_notify_sms_failed
    private static final int SMS_FAILED_ICON = 0x7f020035;

    // not included in android.jar
    private static final int USB_ICON = 0x01080239;

    private static final int MISSED_CALL_ICON = android.R.drawable.stat_notify_missed_call;

    private static final int MUTE_ICON = android.R.drawable.stat_notify_call_mute;

    private static final int CHAT_ICON = android.R.drawable.stat_notify_chat;

    private static final int ERROR_ICON = android.R.drawable.stat_notify_error;

    private static final int MORE_ICON = android.R.drawable.stat_notify_more;

    private static final int SDCARD_ICON = android.R.drawable.stat_notify_sdcard;

    private static final int SDCARD_USB_ICON = android.R.drawable.stat_notify_sdcard_usb;

    private static final int SYNC_ICON = android.R.drawable.stat_notify_sync;

    private static final int SYNC_NOANIM_ICON = android.R.drawable.stat_notify_sync_noanim;

    private static final int VOICEMAIL_ICON = android.R.drawable.stat_notify_voicemail;

    // some of these are fragile (numbers are auto generated) - some are not
    // included in android.jar
    // not included in android.jar
    private static final int PLAY_ICON = 0x7F020042;

    // tag for logcat logging
    private static final String LOG_TAG = "TalkBackService";

    // template replacement tags
    private static final String BEGIN_REPLACEMENT = "{";

    private static final String END_REPLACEMENT = "}";

    private static final String SPACE = " ";

    private String mCompoundButtonSelected;

    private String mCompoundButtonNotSelected;

    private NotificationCache mNotificationCache = null;

    private CommandInterfaceBroadcastReceiver mCommandInterfaceBroadcastReceiver;

    private StringBuilder mCompositionBuilder = new StringBuilder();

    private TextToSpeech mTts;

    private long lastSpeechRequestTime = 0;

    private ArrayList<String> lastMessage;

    private long waitTime = 75;

    private long sleepTime = 25;

    private final ReentrantLock delayedSpeakerLock = new ReentrantLock();

    @Override
    public void onCreate() {
        lastMessage = new ArrayList<String>();

        mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                mTts.addEarcon(PROGRESS_EARCON, "com.google.android.marvin.talkback",
                        R.raw.progress);
            }
        });

        mNotificationCache = new NotificationCache(this);

        mCommandInterfaceBroadcastReceiver = new CommandInterfaceBroadcastReceiver();
        registerReceiver(mCommandInterfaceBroadcastReceiver, sCommandInterfaceIntentFilter);

        loadCompoundButtonStateValues();

        super.onCreate();
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
    public void onDestroy() {
        unregisterReceiver(mCommandInterfaceBroadcastReceiver);
        mTts.shutdown();
        super.onDestroy();
    }

    @Override
    public synchronized void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            Log.e(LOG_TAG, "Received null accessibility event.");
            return;
        }
        int eventType = event.getEventType();

        //Log.e("DEBUG", event.getEventType() + ", " + event.getPackageName() + ", " + event.getClassName() + ", " + event.getText());
        
        // Special case for voice search
        // This is to prevent voice reco from trying to do
        // reco on the synthesized "Speak Now" prompt
        if ((eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
                && (event.getPackageName().equals("com.google.android.voicesearch"))
                && (event.getClassName().equals("com.google.android.voicesearch.IntentApiActivity"))) {
            // This is the startup message that says "Voice Search".
            // Sometimes this can be picked up by the reco depending on
            // timing/system load so it is best to be safe and just do nothing
            // here.
            return;
        }
        if ((eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) && (event.getPackageName().equals("com.google.android.voicesearch"))
                && (event.getClassName().equals("android.widget.TextView"))) {
            // This is the "Speak Now" prompt. This almost always gets reco'd if
            // it is spoken, so replace it with an earcon to avoid this problem.
            mTts.playEarcon(PROGRESS_EARCON, 0, null);
            return;
        }

        switch (eventType) {
            case AccessibilityEvent.TYPE_VIEW_FOCUSED:
                processAccessibilityEventViewFocusedType(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED:
                processAccessibilityEventViewSelectedType(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_CLICKED:
                processAccessibilityEventViewClickedType(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                processAccessibilityEventWindowStateChangedType(event);
                break;
            case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
                processAccessibilityEventViewTextChangedType(event);
                break;
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                processAccessibilityEventNotificationStateChangedType(event);
                break;
            default:
                Log.w(LOG_TAG, "Unknown accessibility event type " + eventType);
        }
    }

    @Override
    public void onInterrupt() {
        mTts.stop();
    }

    /**
     * Processes {@link AccessibilityEvent} of type
     * {@link AccessibilityEvent#TYPE_VIEW_CLICKED}.
     * 
     * @param event The event to process.
     */
    public void processAccessibilityEventViewClickedType(AccessibilityEvent event) {

        Class<?> clazz = loadAccessibilityEventSourceClass(event);
        if (clazz == null) {
            return;
        }

        if (CompoundButton.class.isAssignableFrom(clazz)) {
            announceCompoundButtonClicked(event, clazz);
        } else if (Button.class.isAssignableFrom(clazz)) {
            announceButtonClicked(event);
        } else if (TextView.class.isAssignableFrom(clazz)) {
            announceTextViewOrAdapterViewClicked(event);
        } else if (AdapterView.class.isAssignableFrom(clazz)) {
            announceTextViewOrAdapterViewClicked(event);
        }
    }

    /**
     * Announces the clicking on a {@link TextView} or {@link AdapterView}.
     * 
     * @param event The event with data to announce.
     */
    private void announceTextViewOrAdapterViewClicked(AccessibilityEvent event) {
        int resourceId = R.string.view_clicked_template;
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the clicking on a {@link CompoundButton}.
     * 
     * @param event The event with data to announce.
     */
    private void announceCompoundButtonClicked(AccessibilityEvent event, Class<?> clazz) {
        int resourceId = getCompoundButtonTemplateResourceId(clazz);
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        CharSequence state = getCompoundButtonState(event);
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message, state);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the clicking on a {@link Button}.
     * 
     * @param event The event with data to announce.
     */
    private void announceButtonClicked(AccessibilityEvent event) {
        int resourceId = R.string.button_clicked_template;
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Processes an {@link AccessibilityEvent} of type
     * {@link AccessibilityEvent#TYPE_VIEW_FOCUSED}.
     * 
     * @param event The event to process.
     */
    private void processAccessibilityEventViewFocusedType(AccessibilityEvent event) {
        Class<?> clazz = loadAccessibilityEventSourceClass(event);
        if (clazz == null) {
            return;
        }
        // announceCurrentItemPosition(event);
        if (CompoundButton.class.isAssignableFrom(clazz)) {
            announceCompoundButtonFocused(event, clazz);
        } else if (Button.class.isAssignableFrom(clazz)) {
            announceButtonFocused(event);
        } else if (EditText.class.isAssignableFrom(clazz)) {
            announceEditTextFocused(event);
        } else if (TextView.class.isAssignableFrom(clazz)) {
            announceFrameLayoutOrTextViewOrWebViewFocused(event);
        } else if (WebView.class.isAssignableFrom(clazz)) {
            announceFrameLayoutOrTextViewOrWebViewFocused(event);
        } else if (ImageView.class.isAssignableFrom(clazz)) {
            announceFrameLayoutOrTextViewOrWebViewFocused(event);
        } else if (FrameLayout.class.isAssignableFrom(clazz)) {
            announceFrameLayoutOrTextViewOrWebViewFocused(event);
        } else {
            // Default catch-all so that the user at least gets something.
            announceFrameLayoutOrTextViewOrWebViewFocused(event); 
        }
    }

    /**
     * Announces the focus of a {@link Button}.
     * 
     * @param event The event with data to announce.
     */
    private void announceButtonFocused(AccessibilityEvent event) {
        int resourceId = R.string.button_focused_template;
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the focus of a {@link Button}.
     * 
     * @param event The event with data to announce.
     */
    private void announceCompoundButtonFocused(AccessibilityEvent event, Class<?> clazz) {
        int resourceId = getCompoundButtonTemplateResourceId(clazz);
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        CharSequence state = getCompoundButtonState(event);
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message, state);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announce the focus of an {@link EditText}.
     * 
     * @param event The event with data to announce.
     */
    private void announceEditTextFocused(AccessibilityEvent event) {
        int resourceId = R.string.edit_text_focused_template;
        List<CharSequence> text = event.getText();

        if (event.isPassword()) {
            text.add(getResources().getString(R.string.value_password));
        }

        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announce the focus of a {@link FrameLayout} or {@link TextView} or
     * {@link WebView}.
     * 
     * @param event The event with data to announce.
     */
    private void announceFrameLayoutOrTextViewOrWebViewFocused(AccessibilityEvent event) {
        announceViewFocusedOrSelectedContent(event);
    }

    /**
     * Announces the content of focused or selected View. The most important
     * text which is at position 0 is announced immediately and the rest after a
     * hover timeout.
     * 
     * @param event The event with data to announce.
     */
    private void announceViewFocusedOrSelectedContent(AccessibilityEvent event) {
        int resourceId = 0;
        if (event.getClassName().equals(HANDLE_VIEW_CLASS_NAME)) {
            resourceId = R.string.handle_view_focused_template;
            String messageToBeSpoken = generateTemplatedUtterance(resourceId, "");
            ArrayList<String> messageArrayList = new ArrayList<String>();
            messageArrayList.add(messageToBeSpoken);
            speakDelayed(messageArrayList);
            return;
        } else {
            List<CharSequence> text = event.getText();
            resourceId = R.string.view_focused_or_selected_template;
            if (text.size() > 0) {
                CharSequence message = composeMessage(text, 0, 1);
                String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
                ArrayList<String> messageArrayList = new ArrayList<String>();
                messageArrayList.add(messageToBeSpoken);
                if (text.size() > 1) {
                    CharSequence state = removeStateSegment(text);
                    if (state != null) {
                        resourceId = R.string.announce_value_template;
                        messageToBeSpoken = generateTemplatedUtterance(resourceId, state);
                        messageArrayList.add(messageToBeSpoken);
                    }
                    message = composeMessage(text, 1, text.size());
                    if (!event.isEnabled()) {
                        message = message + SPACE
                                + getResources().getString(R.string.value_disabled);
                    }
                    // There is no real delay here...
                    messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
                    messageArrayList.add(messageToBeSpoken);
                }

                speakDelayed(messageArrayList);
            } else {
                CharSequence contentDescription = event.getContentDescription();
                if (!TextUtils.isEmpty(contentDescription)) {
                    String messageToBeSpoken = generateTemplatedUtterance(resourceId,
                            contentDescription);
                    ArrayList<String> messageArrayList = new ArrayList<String>();
                    messageArrayList.add(messageToBeSpoken);
                    speakDelayed(messageArrayList);
                }
            }
        }
    }

    /**
     * Processes {@link AccessibilityEvent} of type
     * {@link AccessibilityEvent#TYPE_VIEW_SELECTED}.
     * 
     * @param event The event to process.
     */
    public void processAccessibilityEventViewSelectedType(AccessibilityEvent event) {
        Class<?> clazz = loadAccessibilityEventSourceClass(event);
        if (clazz == null) {
            return;
        }

        if (AdapterView.class.isAssignableFrom(clazz)) {
            announceListViewOrWebViewSelected(event);
        } else if (WebView.class.isAssignableFrom(clazz)) {
            announceListViewOrWebViewSelected(event);
        }
    }

    /**
     * Announces the selection of a {@link WebView} or {@link ListView}.
     * 
     * @param event The event with data to announce.
     */
    private void announceListViewOrWebViewSelected(AccessibilityEvent event) {
        // todo: clchen -- fix flushing of speech
        announceViewFocusedOrSelectedContent(event);
        // announceCurrentItemPosition(event);
    }

    private CharSequence removeStateSegment(List<CharSequence> text) {
        for (int i = 0, count = text.size(); i < count; i++) {
            CharSequence segment = text.get(i);
            if (segment.equals(mCompoundButtonNotSelected)
                    || segment.equals(mCompoundButtonSelected)) {
                CharSequence state = text.get(i);
                text.remove(i);
                return state;
            }
        }
        return null;
    }

    /**
     * @param event The event to process.
     */
    private void processAccessibilityEventNotificationStateChangedType(AccessibilityEvent event) {

        Class<?> clazz = loadAccessibilityEventSourceClass(event);
        if (clazz == null) {
            return;
        }

        if (Notification.class.isAssignableFrom(clazz)) {
            announceNotification(event);
        }
    }

    /**
     * Announces the notification state change of a {@link Notification}.
     * 
     * @param event The event with data to announce.
     */
    private void announceNotification(AccessibilityEvent event) {
        // If the user is in a call, do NOT announce any status notifications!
        TelephonyManager tm = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));
        if (tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
            return;
        }

        Parcelable parcelable = event.getParcelableData();
        if (!(parcelable instanceof Notification)) {
            return;
        }

        int icon = ((Notification) parcelable).icon;
        NotificationType type = null;

        switch (icon) {
            case SMS_ICON:
                type = NotificationType.TEXT_MESSAGE;
                break;
            case SMS_FAILED_ICON:
                type = NotificationType.TEXT_MESSAGE_FAILED;
                break;
            case USB_ICON:
                type = NotificationType.USB_CONNECTED;
                break;
            case MISSED_CALL_ICON:
                type = NotificationType.MISSED_CALL;
                break;
            case MUTE_ICON:
                type = NotificationType.MUTE;
                break;
            case CHAT_ICON:
                type = NotificationType.CHAT;
                break;
            case ERROR_ICON:
                type = NotificationType.ERROR;
                break;
            case MORE_ICON:
                type = NotificationType.MORE;
                break;
            case SDCARD_ICON:
                type = NotificationType.SDCARD;
                break;
            case SDCARD_USB_ICON:
                type = NotificationType.SDCARD_USB;
                break;
            case SYNC_ICON:
                type = NotificationType.SYNC;
                break;
            case SYNC_NOANIM_ICON:
                type = NotificationType.SYNC_NOANIM;
                break;
            case VOICEMAIL_ICON:
                type = NotificationType.VOICEMAIL;
                break;
            case PLAY_ICON:
                type = NotificationType.PLAY;
                break;
            default:
                type = NotificationType.STATUS_NOTIFICATION;
        }

        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        updateNotificationCache(type, message);

        CharSequence typeText = getResources().getString(type.getValue());

        String messageToBeSpoken = generateTemplatedUtterance(
                R.string.notification_status_bar_template, typeText, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Updates the {@link NotificationCache}.
     * 
     * @param type The type of the notification.
     * @param text The notification text to be cached.
     */
    private void updateNotificationCache(NotificationType type, CharSequence text) {
        mNotificationCache.addNotification(type, text.toString());
    }

    /**
     * Processes an {@link AccessibilityEvent} of type
     * {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}.
     * 
     * @param event The event to process.
     */
    private void processAccessibilityEventWindowStateChangedType(AccessibilityEvent event) {

        Class<?> clazz = loadAccessibilityEventSourceClass(event);
        if (clazz == null) {
            return;
        }

        if (event.getClassName().equals(SOFT_INPUT_WINDOW_CLASS_NAME)) {
            announceInputMethod(event);
        } else if (Activity.class.isAssignableFrom(clazz)) {
            announceActivityStarted(event);
        } else if (event.getClassName().equals(STATUS_BAR_EXPANDED_DIALOG_CLASS_NAME)) {
            announceStatusBar(event);
        } else if (Dialog.class.isAssignableFrom(clazz)) {
            announceAlertDialog(event);
        } else if (event.getClassName().equals(AUTO_COMPLETE_TEXT_VIEW_DROP_DOWN_CLASS_NAME)) {
            announceAutoCompletion(event);
        } else if (event.getClassName().equals(ICON_MENU_VIEW_CLASS_NAME)) {
            announceOptionsMenu(event);
        } else if (SlidingDrawer.class.isAssignableFrom(clazz)) {
            announceSlidingDrawer(event);
        } else if (event.getClassName().equals(STATUS_BAR_EXPANDED_DIALOG_CLASS_NAME)) {
            announceStatusBar(event);
        } else if (LinearLayout.class.isAssignableFrom(clazz)) {
            announceShortMessage(event);
        }
    }

    /**
     * Announces the start of an {@link Activity}.
     * 
     * @param event The event to process.
     */
    private void announceActivityStarted(AccessibilityEvent event) {
        int resourceId = R.string.activity_started_template;
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state change of an {@link AlertDialog}.
     * 
     * @param event The event with data to announce.
     */
    private void announceAlertDialog(AccessibilityEvent event) {
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(
                R.string.notification_alert_dialog_template, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state change of an auto completion. Note: The class source
     * of the event is actually private inner class of
     * {@link android.widget.AutoCompleteTextView} extends {@link ListView}.
     * 
     * @param event The event with data to announce.
     */
    private void announceAutoCompletion(AccessibilityEvent event) {
        int resourceId = R.string.notification_auto_completion_shown_template;
        String messageToBeSpoken = generateTemplatedUtterance(resourceId);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state change of an options menu.
     * 
     * @param event The event with data to announce.
     */
    private void announceOptionsMenu(AccessibilityEvent event) {
        int resourceId = R.string.notification_options_menu_open_template;
        String messageToBeSpoken = generateTemplatedUtterance(resourceId);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state change of an
     * {@link android.view.inputmethod.InputMethod}.
     * 
     * @param event The event with data to announce.
     */
    private void announceInputMethod(AccessibilityEvent event) {
        int resourceId = R.string.notification_input_method_shown_template;
        String messageToBeSpoken = generateTemplatedUtterance(resourceId);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state change of a {@link SlidingDrawer}.
     * 
     * @param uiEvent The event with data to announce.
     */
    private void announceSlidingDrawer(AccessibilityEvent uiEvent) {
        int resourceId = R.string.notification_sliding_drawer_opened_template;
        String messageToBeSpoken = generateTemplatedUtterance(resourceId);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the state of the status bar. Note: The class source of the
     * event is private inner class.
     * 
     * @param event The event with data to announce.
     */
    private void announceStatusBar(AccessibilityEvent event) {
        int resourceId = R.string.notification_status_bar_opened_template;
        String messageToBeSpoken = generateTemplatedUtterance(resourceId);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Announces the notification state change of a {@link android.widget.Toast}
     * .
     * 
     * @param event The event with data to announce.
     */
    private void announceShortMessage(AccessibilityEvent event) {
        List<CharSequence> text = event.getText();
        CharSequence message = composeMessage(text, 0, text.size());
        String messageToBeSpoken = generateTemplatedUtterance(
                R.string.notification_short_message_template, message);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Processes {@link AccessibilityEvent} of type
     * {@link AccessibilityEvent#TYPE_VIEW_TEXT_CHANGED}
     * 
     * @param uiEvent The event to process.
     */
    private void processAccessibilityEventViewTextChangedType(AccessibilityEvent uiEvent) {
        int fromIndex = uiEvent.getFromIndex();
        int addedCount = uiEvent.getAddedCount();
        int removedCount = uiEvent.getRemovedCount();
        int resourceId = R.string.edit_text_empty_template;

        if (uiEvent.getText().size() == 0) {
            return;
        }

        CharSequence text = uiEvent.getText().get(0);
        if (text == null) {
            text = getResources().getString(R.string.value_blank);
        }
        String messageToBeSpoken = "";
        if (addedCount == 1 && removedCount == 0) {
            resourceId = R.string.single_character_text_added_template;
            CharSequence added = text.subSequence(fromIndex, fromIndex + addedCount);
            messageToBeSpoken = generateTemplatedUtterance(resourceId, added);
        } else if (addedCount > 1 && removedCount == 0) {
            resourceId = R.string.text_added_template;
            CharSequence added = text.subSequence(fromIndex, fromIndex + addedCount);
            messageToBeSpoken = generateTemplatedUtterance(resourceId, added);
        } else if (addedCount == 0 && removedCount > 0) {
            resourceId = R.string.text_removed_template;
            CharSequence beforeText = uiEvent.getBeforeText();
            CharSequence removed = beforeText.subSequence(fromIndex, fromIndex + removedCount);
            messageToBeSpoken = generateTemplatedUtterance(resourceId, removed);
        } else if (addedCount > 0 && removedCount > 0) {
            resourceId = R.string.multiple_characters_replaced_template;
            CharSequence beforeText = uiEvent.getBeforeText();
            CharSequence removed = beforeText.subSequence(fromIndex, fromIndex + removedCount);
            CharSequence added = text.subSequence(fromIndex, fromIndex + addedCount);
            messageToBeSpoken = generateTemplatedUtterance(resourceId, removed, added);
        }
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Returns the state of a {@link CompoundButton}.
     * 
     * @param event An {@link AccessibilityEvent}.
     * @return The state.
     */
    private CharSequence getCompoundButtonState(AccessibilityEvent event) {
        int stateResourceId = R.string.compound_button_not_checked;
        if (event.isChecked()) {
            stateResourceId = R.string.compound_button_checked;
        }
        return getResources().getString(stateResourceId);
    }

    /**
     * Populates a utterance template and speaks schedules it for speaking.
     * 
     * @param resourceId The resource id of the template.
     * @param priority The priority of the utterance.
     * @param timeoutInMiliseconds The timeout before actually speaking the
     *            utterance.
     * @param cancelCachedUtterances If to cancel the currently cached
     *            utterances (and interrupt the TTS if currently speaking).
     * @param parameters The template parameters.
     */
    private String generateTemplatedUtterance(int resourceId, CharSequence... parameters) {
        mCompositionBuilder.delete(0, mCompositionBuilder.length());
        CharSequence populatedTemplate = populateTemplate(mCompositionBuilder, resourceId,
                parameters);
        String message = populatedTemplate.toString();
        if (message != null) {
            return message;
        }
        return "";
    }

    /**
     * Announces the position of the current item in the available items given
     * an {@link AccessibilityEvent}.
     * 
     * @param event The event.
     */
    private void announceCurrentItemPosition(AccessibilityEvent event) {
        int resourceId = R.string.current_item_template;
        CharSequence count = String.valueOf(event.getItemCount());
        CharSequence index = String.valueOf(event.getCurrentItemIndex() + 1);
        String messageToBeSpoken = generateTemplatedUtterance(resourceId, index, count);
        ArrayList<String> messageArrayList = new ArrayList<String>();
        messageArrayList.add(messageToBeSpoken);
        speakDelayed(messageArrayList);
    }

    /**
     * Composes a message given a segment list and the begin and end indices of
     * the subsegment to be used for composition.
     * 
     * @param text The list.
     * @param beginIndex The begin index inclusive.
     * @param endIndex The end index exclusive.
     * @return The composed message.
     */
    private CharSequence composeMessage(List<CharSequence> text, int beginIndex, int endIndex) {
        int size = text.size();
        if (size == 1) {
            return text.get(0);
        } else if (size > 1) {
            StringBuilder builder = new StringBuilder();
            for (int i = beginIndex; i < endIndex; i++) {
                builder.append(text.get(i));
                builder.append(SPACE);
            }
            return builder;
        }
        return "";
    }

    /**
     * Populates a template given template replacement parameters.
     * 
     * @param builder {@link StringBuilder} to contain the populated template.
     * @param resourceId The resource id of the template.
     * @param parameters The parameters.
     * @return The populated template.
     */
    private CharSequence populateTemplate(StringBuilder builder, int resourceId,
            CharSequence... parameters) {
        CharSequence resource = getResources().getString(resourceId);
        StringBuilder populatedTemplate = mCompositionBuilder.append(resource);

        for (int i = 0; i < parameters.length; i++) {
            String substituted = BEGIN_REPLACEMENT + i + END_REPLACEMENT;
            String substitute = parameters[i] != null ? parameters[i].toString() : "";
            int beginIndex = populatedTemplate.indexOf(substituted);
            if (beginIndex > -1) {
                int endIndex = beginIndex + 3;
                populatedTemplate.replace(beginIndex, endIndex, substitute);
            }
        }
        return populatedTemplate;
    }

    /**
     * Loads the class of the {@link AccessibilityEvent} source.
     * 
     * @param event The {@link AccessibilityEvent}.
     * @return The class if loaded successfully, null otherwise.
     */
    private Class<?> loadAccessibilityEventSourceClass(AccessibilityEvent event) {
        Class<?> clazz = null;
        String className = event.getClassName().toString();
        try {
            // try the current ClassLoader first
            clazz = getClassLoader().loadClass(className);
        } catch (ClassNotFoundException cnfe) {
            // if the current ClassLoader fails try via creating a package
            // context
            String packageName = event.getPackageName().toString();
            try {
                Context context = getApplicationContext().createPackageContext(packageName,
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                clazz = context.getClassLoader().loadClass(className);
            } catch (NameNotFoundException nnfe) {
                Log.e(LOG_TAG, "Error during loading an event source class: "
                        + event.getClassName() + " " + nnfe);
            } catch (ClassNotFoundException cnfe2) {
                Log.e(LOG_TAG, "Error during loading an event source class: "
                        + event.getClassName() + " " + cnfe);
            }
        }
        return clazz;
    }

    /**
     * This receives commands send as {@link Intent} broadcasts. This is useful
     * in driving TalkBack from other applications that have the right
     * permissions.
     */
    private class CommandInterfaceBroadcastReceiver extends BroadcastReceiver {

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
                String messageToBeSpoken = getResources()
                        .getString(R.string.talkback_summary_title);
                ArrayList<String> messageArrayList = new ArrayList<String>();
                messageArrayList.add(messageToBeSpoken);
                messageToBeSpoken = mNotificationCache.getFormattedSummary();
                messageArrayList.add(messageToBeSpoken);
                speakDelayed(messageArrayList);
            }
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
     * Returns the template resource id for a {@link CompoundButton}
     * 
     * @param clazz A class that is assignable to {@link CompoundButton}
     *            (Ensured by the caller).
     * @return The resource id.
     */
    private int getCompoundButtonTemplateResourceId(Class<?> clazz) {
        if (CheckBox.class.isAssignableFrom(clazz)) {
            return R.string.checkbox_selected_template;
        } else if (RadioButton.class.isAssignableFrom(clazz)) {
            return R.string.radio_button_selected_template;
        } else if (ToggleButton.class.isAssignableFrom(clazz)) {
            return R.string.toggle_button_selected_template;
        } else {
            return R.string.compound_button_selected_template;
        }
    }

    /**
     * Loads the the values denoting the state of a {@link CompoundButton}.
     */
    private void loadCompoundButtonStateValues() {
        mCompoundButtonSelected = getResources().getString(
                R.string.accessibility_compound_button_selected);
        mCompoundButtonNotSelected = getResources().getString(
                R.string.accessibility_compound_button_unselected);
    }

    class DelayedSpeaker implements Runnable {
        public void run() {
            boolean delayedSpeakerAvailable = false;
            try {
                mTts.stop();
                delayedSpeakerAvailable = delayedSpeakerLock.tryLock();
                if (!delayedSpeakerAvailable) {
                    return;
                }
                while ((lastSpeechRequestTime + waitTime) > System.currentTimeMillis()) {
                    Thread.sleep(sleepTime);
                }
                ArrayList<String> messages = (ArrayList<String>) lastMessage.clone();

                for (int i = 0; i < messages.size(); i++) {
                    String msg = messages.get(i);
                    if (msg != null) {
                        mTts.speak(cleanUpString(msg), 1, null);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                if (delayedSpeakerAvailable) {
                    delayedSpeakerLock.unlock();
                }
            }
        }
    }

    // Workaround for SVOX TTS pronounciation problems
    private String cleanUpString(String text) {
        Matcher camelCasePrefix = CamelCasePrefixPattern.matcher(text);
        text = camelCasePrefix.replaceAll("$1 $2");
        Matcher camelCaseSuffix = CamelCaseSuffixPattern.matcher(text);
        text = camelCaseSuffix.replaceAll(" $1$2");
        return text.replaceAll(" & ", " and ");
    }

    private void speakDelayed(ArrayList<String> message) {
        lastSpeechRequestTime = System.currentTimeMillis();
        lastMessage = message;
        new Thread(new DelayedSpeaker()).start();
    }
}
