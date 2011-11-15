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

package com.google.android.marvin.talkback.formatter;

import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utils;
import com.google.android.marvin.talkback.Utterance;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains custom formatters for presenting text edits.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class TextFormatters {

    private static final String SPACE = " ";

    /**
     * This table will force the TTS to speak out the punctuation by mapping
     * punctuation to its spoken equivalent.
     */
    private static final HashMap<String, String> sPunctuationSpokenEquivalentsMap = new HashMap<String, String>();

    /**
     * Creates a new instance.
     */
    private TextFormatters() { /* hide constructor */}

    /**
     * Formatter that returns an utterance to announce text addition.
     */
    public static final class AddedTextFormatter implements Formatter {

        @Override
        public void format(AccessibilityEvent event, Context context, Utterance utterance,
                Object args) {
            if (event.isPassword()) {
                String message = context.getString(R.string.value_password_character_typed);
                utterance.getText().append(message);
            } else {
                StringBuilder text = Utils.getEventText(context, event);
                int begIndex = event.getFromIndex();
                int endIndex = begIndex + event.getAddedCount();                
                if (begIndex >= 0 && endIndex <= text.length()) {
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

        @Override
        public void format(AccessibilityEvent event, Context context, Utterance utterance,
                Object args) {
            if (event.isPassword()) {
                utterance.getText().append(context.getString(R.string.value_text_removed));
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
                utteranceText.append(context.getString(R.string.value_text_removed));
            }
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ReplacedTextFormatter implements Formatter {

        @Override
        public void format(AccessibilityEvent event, Context context, Utterance utterance,
                Object args) {
            StringBuilder utteranceText = utterance.getText();

            if (event.isPassword()) {
                String template = context.getString(R.string.template_replaced_characters);
                String populatedTemplate = String.format(template, event.getRemovedCount(), event
                        .getAddedCount());
                utteranceText.append(populatedTemplate);
            } else {
                String text = Utils.getEventText(context, event).toString();
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
                    utteranceText.append(context.getString(R.string.value_text_removed));
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

                        String template = context.getString(R.string.template_text_replaced);
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
            // intentional use of the TalkBack context
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
