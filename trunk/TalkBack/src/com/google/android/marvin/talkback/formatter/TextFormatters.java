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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * This class contains custom formatters for presenting text edits.
 * 
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class TextFormatters {
    private static final Context CONTEXT = TalkBackService.asContext();
    private static final String SEPARATOR = " ";

    /**
     * This table will force the TTS to speak out the punctuation by mapping
     * punctuation to its spoken equivalent.
     */
    private static final HashMap<String, CharSequence> sPunctuationSpokenEquivalentsMap =
            new HashMap<String, CharSequence>();

    private TextFormatters() {
        // Not publicly instantiable.
    }

    /**
     * Formatter that returns an utterance to announce text addition.
     */
    public static final class AddedTextFormatter implements Formatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance,
                Bundle args) {
            final StringBuilder utteranceText = utterance.getText();

            if (event.isPassword()) {
                final String message = context.getString(R.string.value_password_character_typed);
                utteranceText.append(message);
                return true;
            }

            final CharSequence text = Utils.getEventAggregateText(context, event);
            final int begIndex = event.getFromIndex();
            final int endIndex = begIndex + event.getAddedCount();

            if ((begIndex < 0) || (endIndex > text.length())) {
                return false;
            }

            final CharSequence addedText = text.subSequence(begIndex, endIndex);
            utteranceText.append(formatForSpeech(addedText));

            return true;
        }
    }

    /**
     * Formatter that returns an utterance to announce text removing.
     */
    public static final class RemovedTextFormatter implements Formatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance,
                Bundle args) {
            final StringBuilder utteranceText = utterance.getText();

            if (event.isPassword()) {
                utteranceText.append(context.getString(R.string.value_text_removed));
                return true;
            }

            final CharSequence beforeText = event.getBeforeText();
            final int begIndex = event.getFromIndex();
            final int endIndex = begIndex + event.getRemovedCount();

            if ((begIndex < 0) || (endIndex > beforeText.length())) {
                return false;
            }

            final CharSequence removedText = beforeText.subSequence(begIndex, endIndex);
            utteranceText.append(formatForSpeech(removedText));
            utteranceText.append(SEPARATOR);
            utteranceText.append(context.getString(R.string.value_text_removed));

            return true;
        }
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ReplacedTextFormatter implements Formatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance,
                Bundle args) {
            final StringBuilder utteranceText = utterance.getText();

            if (event.isPassword()) {
                final int removed = event.getRemovedCount();
                final int added = event.getAddedCount();
                final String formattedText =
                        context.getString(R.string.template_replaced_characters, removed, added);
                utteranceText.append(formattedText);
                return true;
            }

            final String text = Utils.getEventText(context, event).toString();
            final String beforeText = event.getBeforeText().toString();

            if (isTypedCharacter(text, beforeText)) {
                final CharSequence appendText = formatCharacterChange(text);

                if (TextUtils.isEmpty(appendText)) {
                    return false;
                }

                utteranceText.append(appendText);
            } else if (isRemovedCharacter(text, beforeText)) {
                final CharSequence appendText = formatCharacterChange(beforeText);

                if (TextUtils.isEmpty(appendText)) {
                    return false;
                }

                utteranceText.append(appendText);
                utteranceText.append(SEPARATOR);
                utteranceText.append(context.getString(R.string.value_text_removed));
            } else {
                final CharSequence appendText = formatTextChange(event, context, text, beforeText);

                if (TextUtils.isEmpty(appendText)) {
                    return false;
                }

                utteranceText.append(appendText);
            }

            return true;
        }

        private CharSequence formatTextChange(AccessibilityEvent event, Context context,
                String text, String beforeText) {
            final int beforeBegIndex = event.getFromIndex();
            final int beforeEndIndex = beforeBegIndex + event.getRemovedCount();

            if (beforeBegIndex < 0 || beforeEndIndex > beforeText.length()) {
                return null;
            }

            final CharSequence removedText =
                    formatForSpeech(beforeText.subSequence(beforeBegIndex, beforeEndIndex));

            final int addedBegIndex = event.getFromIndex();
            final int addedEndIndex = addedBegIndex + event.getAddedCount();

            if (addedBegIndex < 0 || addedEndIndex > text.length()) {
                return null;
            }

            final CharSequence addedText =
                    formatForSpeech(text.subSequence(addedBegIndex, addedEndIndex));

            final CharSequence formattedText =
                    context.getString(R.string.template_text_replaced, removedText, addedText);
            return formattedText;
        }

        private CharSequence formatCharacterChange(String text) {
            // This happens if the application replaces the entire text
            // while the user is typing. This logic leads to missing
            // the very rare case of the user replacing text with the
            // same text plus a single character but handles the much
            // more frequent case mentioned above.
            final int begIndex = text.length() - 1;
            final int endIndex = begIndex + 1;

            if ((begIndex < 0) || (endIndex > text.length())) {
                return null;
            }

            final CharSequence addedText = text.subSequence(begIndex, endIndex);

            return formatForSpeech(addedText);
        }

        /**
         * Returns if a character is added to the event <code>text</code> given
         * the event <code>beforeText</code>.
         */
        private boolean isTypedCharacter(String text, String beforeText) {
            if (text.length() != (beforeText.length() + 1)) {
                return false;
            }

            return text.startsWith(beforeText);
        }

        /**
         * Returns if a character is removed from the event <code>text</code>
         * given the event <code>beforeText</code>.
         */
        private boolean isRemovedCharacter(String text, String beforeText) {
            if ((text.length() + 1) != beforeText.length()) {
                return false;
            }

            return beforeText.startsWith(text);
        }
    }

    /**
     * Formatter that returns an utterance to announce text selection.
     */
    public static final class SelectedTextFormatter implements Formatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance,
                Bundle args) {
            final CharSequence text = Utils.getEventText(context, event);
            final int begIndex = event.getFromIndex();
            final int endIndex = event.getToIndex();

            if ((begIndex < 0) || (endIndex > text.length()) || (begIndex >= endIndex)) {
                return false;
            }

            final CharSequence selectedText = text.subSequence(begIndex, endIndex);
            utterance.getText().append(formatForSpeech(selectedText));
            
            return true;
        }
    }

    /**
     * Gets the spoken equivalent of a character. Passing an argument longer
     * that one return the argument itself as the spoken equivalent. </p> Note:
     * The argument is a {@link CharSequence} for efficiency to avoid multiple
     * string creation.
     * 
     * @param character The character to transform.
     * @return The spoken equivalent.
     */
    private static CharSequence formatForSpeech(CharSequence character) {
        if (character.length() != 1) {
            return character;
        }

        final CharSequence mapping = getPunctuationSpokenEquivalentMap().get(character);
        if (mapping != null) {
            return mapping;
        }

        return character;
    }

    /**
     * Gets the spoken equivalent map. If the map is not initialized it is first
     * create and populated.
     * 
     * @return The spoken equivalent map.
     */
    private static Map<String, CharSequence> getPunctuationSpokenEquivalentMap() {
        if (sPunctuationSpokenEquivalentsMap.isEmpty()) {
            loadMapping("?", R.string.punctuation_questionmark);
            loadMapping(" ", R.string.punctuation_space);
            loadMapping(",", R.string.punctuation_comma);
            loadMapping(".", R.string.punctuation_dot);
            loadMapping("!", R.string.punctuation_exclamation);
            loadMapping("(", R.string.punctuation_open_paren);
            loadMapping(")", R.string.punctuation_close_paren);
            loadMapping("\"", R.string.punctuation_double_quote);
            loadMapping(";", R.string.punctuation_semicolon);
            loadMapping(":", R.string.punctuation_colon);
        }

        return sPunctuationSpokenEquivalentsMap;
    }

    private static void loadMapping(String text, int resId) {
        final CharSequence spoken = CONTEXT.getString(resId);
        sPunctuationSpokenEquivalentsMap.put(text, spoken);
    }
}
