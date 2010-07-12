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
import android.content.Context;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains custom formatters used by TalkBack.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class CustomFormatters {

    private static final String SPACE = " ";

    private static final String VALUE_PASSWORD_CHARACTER_TYPED = "@com.google.android.marvin.talkback:string/value_cpassword_character_typed";

    /**
     * This table will force the TTS to speak out the punctuation by mapping
     * punctuation to its spoken equivalent.
     */
    private static final HashMap<String, String> sPunctuationSpokenEquivalentsMap = new HashMap<String, String>();

    /**
     * Creates a new instance.
     */
    private CustomFormatters() { /* hide constructor */}

    /**
     * Formatter that returns an utterance to announce text addition.
     */
    public static final class AddedTextFormatter implements Formatter {

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            if (event.isPassword()) {
                String message = SpeechRule.getStringResource(VALUE_PASSWORD_CHARACTER_TYPED);
                utterance.getText().append(message);
            } else {
                StringBuilder text = SpeechRule.getEventText(event);
                int begIndex = event.getFromIndex();
                int endIndex = begIndex + event.getAddedCount();                
                
                // TODO(svetoslavganov): Remove the following check once bug
                // 2513822 is resolved with a framework patch.
                if (endIndex <= AccessibilityEvent.MAX_TEXT_LENGTH) {
                    CharSequence addedText = text.subSequence(begIndex, endIndex);
                    if (addedText.length() == 1) {
                        addedText = getCharacterSpokenEquivalent(addedText);
                    }
                    utterance.getText().append(addedText);
                }
            }
        }
    }

    /**
     * Formatter that returns an utterance to announce text removing.
     */
    public static final class RemovedTextFormatter implements Formatter {

        private static final String VALUE_TEXT_REMOVED = "@com.google.android.marvin.talkback:string/value_text_removed";

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            if (event.isPassword()) {
                utterance.getText().append(SpeechRule.getStringResource(VALUE_TEXT_REMOVED));
            } else {
                CharSequence beforeText = event.getBeforeText();
                int begIndex = event.getFromIndex();
                int endIndex = begIndex + event.getRemovedCount();

                CharSequence removedText = beforeText.subSequence(begIndex, endIndex);                
                if (removedText.length() == 1) {
                    removedText = getCharacterSpokenEquivalent(removedText);
                }

                StringBuilder utteranceText = utterance.getText();
                utteranceText.append(getCharacterSpokenEquivalent(removedText));
                utteranceText.append(SPACE);
                utteranceText.append(SpeechRule.getStringResource(VALUE_TEXT_REMOVED));
            }
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ReplacedTextFormatter implements Formatter {

        private static final String TEMPLATE_TEXT_REPLACED = "@com.google.android.marvin.talkback:string/temlate_text_replaced";

        private static final String TEMPLATE_REPLACED_CHARACTERS = "@com.google.android.marvin.talkback:string/template_replaced_characters";

        private static final String VALUE_TEXT_REMOVED = "@com.google.android.marvin.talkback:string/value_text_removed";

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            StringBuilder utteranceText = utterance.getText();

            if (event.isPassword()) {
                String template = SpeechRule.getStringResource(TEMPLATE_REPLACED_CHARACTERS);
                String populatedTemplate = String.format(template, event.getRemovedCount(), event
                        .getAddedCount());
                utteranceText.append(populatedTemplate);
            } else {
                String text = SpeechRule.getEventText(event).toString();
                String beforeText = event.getBeforeText().toString();

                if (isTypedCharacter(text, beforeText)) {
                    // This happens if the application replaces the entire text
                    // while the user is typing. This logic leads to missing
                    // the very rare case of the user replacing text with the
                    // same text plus a single character but handles the much
                    // more frequent case mentioned above.
                    int begIndex = text.length() - 1;
                    int endIndex = begIndex + 1;
                    
                    // TODO(svetoslavganov): Remove the following check once bug
                    // 2513822 is resolved with a framework patch.
                    if (endIndex <= AccessibilityEvent.MAX_TEXT_LENGTH) {
                        CharSequence addedText = text.subSequence(begIndex, endIndex);
                        utteranceText.append(getCharacterSpokenEquivalent(addedText));
                    }
                } else if (isRemovedCharacter(text, beforeText)) {
                    // This happens if the application replaces the entire text
                    // while the user is typing. This logic leads to missing
                    // the very rare case of the user replacing text with the
                    // same text minus a single character but handles the much
                    // more frequent case mentioned above.
                    int begIndex = beforeText.length() - 1;
                    int endIndex = begIndex + 1;
                    CharSequence removedText = beforeText.subSequence(begIndex, endIndex); 
                    utteranceText.append(getCharacterSpokenEquivalent(removedText));
                    utteranceText.append(SPACE);
                    utteranceText.append(SpeechRule.getStringResource(VALUE_TEXT_REMOVED));
                } else {
                    int beforeBegIndex = event.getFromIndex();
                    int beforeEndIndex = beforeBegIndex + event.getRemovedCount();
                    CharSequence removedText = beforeText.subSequence(beforeBegIndex,
                            beforeEndIndex);

                    int addedBegIndex = event.getFromIndex();
                    int addedEndIndex = addedBegIndex + event.getAddedCount();

                    // TODO(svetoslavganov): Remove the following check once bug
                    // 2513822 is resolved with a framework patch.
                    if (addedEndIndex <= AccessibilityEvent.MAX_TEXT_LENGTH) {
                        CharSequence addedText = text.subSequence(addedBegIndex, addedEndIndex);

                        String template = SpeechRule.getStringResource(TEMPLATE_TEXT_REPLACED);
                        String populatedTemplate = String.format(template, removedText, addedText);
                        utteranceText.append(populatedTemplate);
                    }
                }
            }
        }

        /**
         * Returns if a character is added to the event <code>text</code> given
         * the event <code>beforeText</code>.
         */
        private boolean isTypedCharacter(String text, String beforeText) {
            if (text.length() != beforeText.length() + 1) {
                return false;
            }
            return text.startsWith(beforeText);
        }

        /**
         * Returns if a character is removed from the event <code>text</code>
         * given the event <code>beforeText</code>.
         */
        private boolean isRemovedCharacter(String text, String beforeText) {
            if (text.length() + 1 != beforeText.length()) {
                return false;
            }
            return beforeText.startsWith(text);
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class NotificationFormatter implements Formatter {

        // tag used for logging messages from this class
        private static final String LOG_TAG = "CustomFormatters.NotificationFormatter";

        // NOT INCLUDED in android.jar (NOT accessible via API)
        // Please keep track of the icon number for various releases
        // and add a comment for which resource file contains the icon

        // com.android.mms.R#stat_notify_sms
        private static final int ICON_SMS_DONUT = 0x7F020036;

        private static final int ICON_SMS_ECLAIR = 0x7f02003d;

        // com.android.mms.R#stat_notify_sms_failed
        private static final int ICON_SMS_FAILED_DONUT = 0x7f020035;

        private static final int ICON_SMS_FAILED_ECLAIR = 0x7f02003e;

        // com.android.music.R#stat_notify_musicplayer
        private static final int ICON_PLAY_DONUT = 0x7F020042;

        private static final int ICON_PLAY_ECLAIR = 0x7f02004e;

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
                return; // ignore this notification
            }

            StringBuilder utteranceText = utterance.getText();

            StringBuilder eventText = SpeechRule.getEventText(event);
            NotificationType type = null;

            switch (icon) {
                case ICON_SMS_DONUT:
                case ICON_SMS_ECLAIR:
                    type = NotificationType.TEXT_MESSAGE;
                    break;
                case ICON_SMS_FAILED_DONUT:
                case ICON_SMS_FAILED_ECLAIR:
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
                case ICON_PLAY_DONUT:
                case ICON_PLAY_ECLAIR:
                    type = NotificationType.PLAY;
                    break;
                default:
                    Log.w(LOG_TAG, "Unknown notification " + icon);
            }

            if (type != null) {
                CharSequence typeText = TalkBackService.asContext().getResources().getString(
                        type.getValue());
                utteranceText.append(typeText);
                utteranceText.append(SPACE);
            }
            utteranceText.append(eventText);
        }
    }

    /**
     * Formatter that returns an utterance to announce the incoming call screen.
     */
    public static final class InCallScreenFormatter implements Formatter {

        // Indices of the text elements for a in-call-screen event
        private static final int INDEX_UPPER_TITLE = 1;

        private static final int INDEX_PHOTO = 2;

        private static final int INDEX_NAME = 4;

        private static final int INDEX_LABEL = 6;

        private static final int INDEX_SOCIAL_STATUS = 7;

        @Override
        public void format(AccessibilityEvent event, Utterance utterance) {
            List<CharSequence> eventText = event.getText();
            StringBuilder utteranceText = utterance.getText();

            // guard against old version of the phone application
            if (eventText.size() == 1) {
                utteranceText.append(eventText.get(0));
                return;
            }

            CharSequence title = eventText.get(INDEX_UPPER_TITLE);
            if (title != null) {
                utteranceText.append(title);
                utteranceText.append(SPACE);
            }

            CharSequence name = eventText.get(INDEX_NAME);
            if (name == null) {
                return;
            }

            utteranceText.append(name);
            utteranceText.append(SPACE);

            if (!isPhoneNumber(name.toString())) {
                CharSequence label = eventText.get(INDEX_LABEL);
                if (label != null) {
                    utteranceText.append(label);
                    utteranceText.append(SPACE);
                }
                CharSequence photo = eventText.get(INDEX_PHOTO);
                if (photo != null) {
                    utteranceText.append(photo);
                    utteranceText.append(SPACE);
                }
                CharSequence socialStatus = eventText.get(INDEX_SOCIAL_STATUS);
                if (socialStatus != null) {
                    utteranceText.append(socialStatus);
                    utteranceText.append(SPACE);
                }
            }
        }

        /**
         * Returns if a <code>value</code> is a phone number.
         */
        private boolean isPhoneNumber(String value) {
            String valueNoDeshes = value.replaceAll("-", "");
            try {
                Long.parseLong(valueNoDeshes);
                return true;
            } catch (IllegalArgumentException iae) {
                return false;
            }
        }
    }

    /**
     * Gets the spoken equivalent of a character. Passing an argument longer
     * that one return the argument itself as the spoken equivalent.
     * </p>
     * Note: The argument is a {@link CharSequence} for efficiency to avoid
     *       multiple string creation.
     *
     * @param character The character to transform.
     * @return The spoken equivalent.
     */
    private static CharSequence getCharacterSpokenEquivalent(CharSequence character) {
        if (character.length() != 1) {
            return character;
        }

        String mapping = getPunctuationSpokenEquivalentMap().get(character);
        if (mapping != null) {
            return mapping;
        }

        return character;
    }

    /**
     * Gets the spoken equivalent map. If the map is not initialized
     * it is first create and populated.
     *
     * @return The spoken equivalent map.
     */
    private static Map<String, String> getPunctuationSpokenEquivalentMap() {
        if (sPunctuationSpokenEquivalentsMap.isEmpty()) {
            Context context = TalkBackService.asContext();

            sPunctuationSpokenEquivalentsMap.put("?",
                context.getString(R.string.punctuation_questionmark));
            sPunctuationSpokenEquivalentsMap.put(" ",
                context.getString(R.string.punctuation_space));
            sPunctuationSpokenEquivalentsMap.put(",",
                context.getString(R.string.punctuation_comma));
            sPunctuationSpokenEquivalentsMap.put(".",
                context.getString(R.string.punctuation_dot));
            sPunctuationSpokenEquivalentsMap.put("!",
                context.getString(R.string.punctuation_exclamation));
            sPunctuationSpokenEquivalentsMap.put("(",
                context.getString(R.string.punctuation_open_paren));
            sPunctuationSpokenEquivalentsMap.put(")",
                context.getString(R.string.punctuation_close_paren));
            sPunctuationSpokenEquivalentsMap.put("\"",
                context.getString(R.string.punctuation_double_quote));
            sPunctuationSpokenEquivalentsMap.put(";",
                context.getString(R.string.punctuation_semicolon));
            sPunctuationSpokenEquivalentsMap.put(":",
                context.getString(R.string.punctuation_colon));
        }

        return sPunctuationSpokenEquivalentsMap; 
    }
}
