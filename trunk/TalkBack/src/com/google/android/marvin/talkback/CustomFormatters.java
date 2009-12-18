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

import android.app.Notification;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;

/**
 * This class contains custom formatters used by TalkBack.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class CustomFormatters {

    private static final String SPACE = " ";

    /**
     * Formatter that returns an utterance to announce text addition.
     */
    public static final class AddedTextFormatter implements Formatter {

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            StringBuilder text = SpeechRule.getEventText(event);
            int begIndex = event.getFromIndex();
            int endIndex = begIndex + event.getAddedCount();
            utterance.getText().append(text.subSequence(begIndex, endIndex));
        }
    }

    /**
     * Formatter that returns an utterance to announce text removing.
     */
    public static final class RemovedTextFormatter implements Formatter {

        private static final String TEXT_REMOVED = "@com.google.android.marvin.talkback:string/value_text_removed";

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            CharSequence beforeText = event.getBeforeText();
            int begIndex = event.getFromIndex();
            int endIndex = begIndex + event.getRemovedCount();
            StringBuilder utteranceText = utterance.getText();
            utteranceText.append(beforeText.subSequence(begIndex, endIndex));
            utteranceText.append(SPACE);
            utteranceText.append(SpeechRule.getStringResource(TEXT_REMOVED));
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ReplacedTextFormatter implements Formatter {

        private static final String TEXT_REPLACED = "@com.google.android.marvin.talkback:string/template_text_replaced";

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            StringBuilder utteranceText = utterance.getText();

            CharSequence removedText = event.getBeforeText();
            int beforeBegIndex = event.getFromIndex();
            int beforeEndIndex = beforeBegIndex + event.getRemovedCount();
            utteranceText.append(removedText.subSequence(beforeBegIndex, beforeEndIndex));

            utteranceText.append(SPACE);
            utteranceText.append(SpeechRule.getStringResource(TEXT_REPLACED));
            utteranceText.append(SPACE);

            StringBuilder addedText = SpeechRule.getEventText(event);
            int addedBegIndex = event.getFromIndex();
            int addedEndIndex = addedBegIndex + event.getAddedCount();
            utteranceText.append(addedText.subSequence(addedBegIndex, addedEndIndex));
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class NotificationFormatter implements Formatter {

        // NOT INCLUDED in android.jar (NOT accessible via API)

        // com.android.mms.R#stat_notify_sms_failed
        private static final int ICON_SMS = 0x7F020036;

        // com.android.mms.R#stat_notify_sms_failed
        private static final int ICON_SMS_FAILED = 0x7f020035;

        // com.android.mms.R#stat_notify_sms_failed
        private static final int ICON_PLAY = 0x7F020042;

        // com.android.mms.R#stat_notify_sms_failed
        private static final int ICON_USB = 0x01080239;

        // INCLUDED in android.jar (accessible via API)
        private static final int ICON_MISSED_CALL = android.R.drawable.stat_notify_missed_call;

        private static final int ICON_MUTE = android.R.drawable.stat_notify_call_mute;

        private static final int ICON_CHAT = android.R.drawable.stat_notify_chat;

        private static final int ICON_ERROR = android.R.drawable.stat_notify_error;

        private static final int ICON_MORE = android.R.drawable.stat_notify_more;

        private static final int ICON_SDCARD = android.R.drawable.stat_notify_sdcard;

        private static final int ICON_SDCARD_USB = android.R.drawable.stat_notify_sdcard_usb;

        private static final int ICON_SYNC = android.R.drawable.stat_notify_sync;

        private static final int ICON_SYNC_NOANIM = android.R.drawable.stat_notify_sync_noanim;

        private static final int ICON_VOICEMAIL = android.R.drawable.stat_notify_voicemail;

        private static final int ICON_PHONE_CALL = android.R.drawable.stat_sys_phone_call;

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {

            Parcelable parcelable = event.getParcelableData();
            if (!(parcelable instanceof Notification)) {
                return;
            }

            // special case since the notification appears several
            // times while the phone call goes through different phases
            // resulting in multiple announcement of the fact that a phone
            // call is in progress which the user is already aware of
            int icon = ((Notification) parcelable).icon;
            if (icon == ICON_PHONE_CALL) {
                return;  // ignore this notification
            }

            StringBuilder utteranceText = utterance.getText();

            // use the event text if such otherwise use the icon
            if (!event.getText().isEmpty()) {
                utteranceText.append(SpeechRule.getEventText(event));
                return;
            }

            NotificationType type = null;

            switch (icon) {
                case ICON_SMS:
                    type = NotificationType.TEXT_MESSAGE;
                    break;
                case ICON_SMS_FAILED:
                    type = NotificationType.TEXT_MESSAGE_FAILED;
                    break;
                case ICON_USB:
                    type = NotificationType.USB_CONNECTED;
                    break;
                case ICON_MISSED_CALL:
                    type = NotificationType.MISSED_CALL;
                    break;
                case ICON_MUTE:
                    type = NotificationType.MUTE;
                    break;
                case ICON_CHAT:
                    type = NotificationType.CHAT;
                    break;
                case ICON_ERROR:
                    type = NotificationType.ERROR;
                    break;
                case ICON_MORE:
                    type = NotificationType.MORE;
                    break;
                case ICON_SDCARD:
                    type = NotificationType.SDCARD;
                    break;
                case ICON_SDCARD_USB:
                    type = NotificationType.SDCARD_USB;
                    break;
                case ICON_SYNC:
                    type = NotificationType.SYNC;
                    break;
                case ICON_SYNC_NOANIM:
                    type = NotificationType.SYNC_NOANIM;
                    break;
                case ICON_VOICEMAIL:
                    type = NotificationType.VOICEMAIL;
                    break;
                case ICON_PLAY:
                    type = NotificationType.PLAY;
                    break;
                default:
                    type = NotificationType.STATUS_NOTIFICATION;
            }

            CharSequence typeText = TalkBackService.asContext().getResources().getString(
                    type.getValue());

            utteranceText.append(SpeechRule.getEventText(event));
            utteranceText.append(SPACE);
            utteranceText.append(typeText);
        }
    }
}
