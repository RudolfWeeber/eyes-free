/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.SpeechCleanupUtils;
import com.google.android.marvin.talkback.SpeechController.SpeechParam;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;

import java.util.List;

/**
 * This class contains custom formatters for presenting text edits.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public final class TextFormatters {
    /** Default pitch adjustment for text event feedback. */
    private static final float DEFAULT_PITCH = 1.2f;

    /** Default rate adjustment for text event feedback. */
    private static final float DEFAULT_RATE = 1.0f;

    private TextFormatters() {
        // Not publicly instantiable.
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ChangedTextFormatter implements AccessibilityEventFormatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
            // Text changes should use a different voice from labels.
            final Bundle params = new Bundle();
            params.putFloat(SpeechParam.PITCH, DEFAULT_PITCH);
            params.putFloat(SpeechParam.RATE, DEFAULT_RATE);
            utterance.getMetadata().putBundle(Utterance.KEY_METADATA_SPEECH_PARAMS, params);

            final StringBuilder utteranceText = utterance.getText();
            final boolean shouldSpeakPasswords = SecureCompatUtils.shouldSpeakPasswords(context);

            if (event.isPassword() && !shouldSpeakPasswords) {
                return formatPassword(event, context, utteranceText);
            }

            if (!passesSanityCheck(event)) {
                LogUtils.log(this, Log.ERROR, "Inconsistent text change event detected");
                return false;
            }

            CharSequence removedText = getRemovedText(event, context);
            CharSequence addedText = getAddedText(event, context);

            // Never say "replaced Hello with Hello".
            if (TextUtils.equals(addedText, removedText)) {
                return false;
            }

            // Abort if either text is null (indicates an error).
            if ((removedText == null) || (addedText == null)) {
                return false;
            }

            final int removedLength = removedText.length();
            final int addedLength = addedText.length();

            // Eliminate overlap. This is necessary to avoid saying
            // "replaced Hell with Hello" when auto-correct is active.
            if ((addedLength > removedLength)
                    && TextUtils.regionMatches(addedText, 0, removedText, 0, removedLength)) {
                addedText = TextUtils.substring(addedText, removedLength, addedLength);
                removedText = "";
            } else if ((removedLength > addedLength)
                    && TextUtils.regionMatches(removedText, 0, addedText, 0, addedLength)) {
                addedText = "";
                removedText = TextUtils.substring(removedText, addedLength, removedLength);
            }

            // Apply any speech clean up rules. Usually this means changing "A"
            // to "capital A" or "[" to "left bracket".
            final CharSequence cleanedRemovedText =
                    SpeechCleanupUtils.cleanUp(context, removedText);
            final CharSequence cleanedAddedText = SpeechCleanupUtils.cleanUp(context, addedText);

            // Abort if either text is null (indicates an error).
            if ((cleanedRemovedText == null) || (cleanedAddedText == null)) {
                return false;
            }

            final boolean added = !TextUtils.isEmpty(cleanedAddedText);
            final boolean removed = !TextUtils.isEmpty(cleanedRemovedText);

            if (added && removed) {
                final String formattedText = context.getString(
                        R.string.template_text_replaced, cleanedRemovedText, cleanedAddedText);
                StringBuilderUtils.appendWithSeparator(utteranceText, formattedText);
            } else if (added) {
                StringBuilderUtils.appendWithSeparator(utteranceText, cleanedAddedText);
            } else if (removed) {
                StringBuilderUtils.appendWithSeparator(utteranceText, cleanedRemovedText,
                        context.getString(R.string.value_text_removed));
            } else {
                return false;
            }

            return true;
        }

        /**
         * Checks whether the event's reported properties match its actual
         * properties, e.g. does the added count minus the removed count reflect
         * the actual change in length between the current and previous text
         * contents.
         *
         * @param event The text changed event to validate.
         * @return {@code true} if the event properties are valid.
         */
        private boolean passesSanityCheck(AccessibilityEvent event) {
            final CharSequence afterText = getEventText(event);
            final CharSequence beforeText = event.getBeforeText();

            if (afterText == null || beforeText == null) {
                return false;
            }

            final int diff = (event.getAddedCount() - event.getRemovedCount());

            return ((beforeText.length() + diff) == afterText.length());
        }

        /**
         * Attempts to extract the text that was added during an event.
         *
         * @param event The source event.
         * @param context The application context.
         * @return The added text, or {@code null} on error.
         */
        private CharSequence getAddedText(AccessibilityEvent event, Context context) {
            final CharSequence text = getEventText(event);

            if (text == null) {
                return null;
            }

            final int addedBegIndex = event.getFromIndex();
            final int addedEndIndex = addedBegIndex + event.getAddedCount();

            if (!areValidIndices(text, addedBegIndex, addedEndIndex)) {
                return null;
            }

            final CharSequence addedText = text.subSequence(addedBegIndex, addedEndIndex);

            return addedText;
        }

        /**
         * Attempts to extract the text that was removed during an event.
         *
         * @param event The source event.
         * @param context The application context.
         * @return The removed text, or {@code null} on error.
         */
        private CharSequence getRemovedText(AccessibilityEvent event, Context context) {
            final CharSequence beforeText = event.getBeforeText();

            if (beforeText == null) {
                return null;
            }

            final int beforeBegIndex = event.getFromIndex();
            final int beforeEndIndex = beforeBegIndex + event.getRemovedCount();

            if (!areValidIndices(beforeText, beforeBegIndex, beforeEndIndex)) {
                return null;
            }

            final CharSequence removedText = beforeText.subSequence(beforeBegIndex, beforeEndIndex);

            return removedText;
        }

        /**
         * Formats "secure" password feedback from event text.
         *
         * @param event The source event.
         * @param context The application context.
         * @param utteranceText The utterance text to populate.
         * @return {@code false} on error.
         */
        private boolean formatPassword(AccessibilityEvent event, Context context,
                final StringBuilder utteranceText) {
            final int removed = event.getRemovedCount();
            final int added = event.getAddedCount();

            if ((added == 0) && (removed == 0)) {
                return false;
            } else if ((added == 1) && (removed == 0)) {
                StringBuilderUtils.appendWithSeparator(utteranceText,
                        context.getString(R.string.symbol_bullet));
            } else if ((added == 0) && (removed == 1)) {
                StringBuilderUtils.appendWithSeparator(utteranceText,
                        context.getString(R.string.symbol_bullet),
                        context.getString(R.string.value_text_removed));
            } else {
                final CharSequence formattedText =
                        context.getString(R.string.template_replaced_characters, removed, added);
                StringBuilderUtils.appendWithSeparator(utteranceText, formattedText);
            }

            return true;
        }
    }

    /**
     * Formatter that returns an utterance to announce text selection.
     */
    public static final class SelectedTextFormatter implements AccessibilityEventFormatter {
        @Override
        public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final CharSequence text = getEventText(event);
            final int count = event.getItemCount();
            final StringBuilder utteranceText = utterance.getText();
            final boolean shouldSpeakPasswords = SecureCompatUtils.shouldSpeakPasswords(context);

            if (event.isPassword() && !shouldSpeakPasswords) {
                return formatPassword(event, context, utteranceText);
            }

            // Don't provide selection feedback when there's no text. We have to
            // check the item count separately to avoid speaking hint text,
            // which always has an item count of zero even though the event text
            // is not empty.
            if (TextUtils.isEmpty(text) || (count <= 0)) {
                return false;
            }

            final int begIndex = event.getFromIndex();
            final int endIndex = record.getToIndex();

            if (!areValidIndices(text, begIndex, endIndex)) {
                return false;
            }

            final CharSequence selectedText;

            if ((begIndex == endIndex) && (endIndex < text.length())) {
                // Cursor movement event, read the character at the cursor.
                selectedText = text.subSequence(begIndex, endIndex + 1);
            } else {
                selectedText = text.subSequence(begIndex, endIndex);
            }

            final CharSequence cleanedText = SpeechCleanupUtils.cleanUp(context, selectedText);
            StringBuilderUtils.appendWithSeparator(utteranceText, cleanedText);
            return true;
        }

        /**
         * Formats "secure" password feedback from event text.
         *
         * @param event The source event.
         * @param context The application context.
         * @param utteranceText The utterance text to populate.
         * @return {@code false} on error.
         */
        private boolean formatPassword(AccessibilityEvent event, Context context,
                final StringBuilder utteranceText) {
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final int fromIndex = event.getFromIndex();
            final int toIndex = record.getToIndex();

            if (toIndex <= fromIndex) {
                return false;
            }

            final CharSequence formattedText = context.getString(
                    R.string.template_password_selected, fromIndex, toIndex);
            StringBuilderUtils.appendWithSeparator(utteranceText, formattedText);
            return true;
        }
    }

    /**
     * Returns whether a set of indices are valid for a given
     * {@link CharSequence}.
     *
     * @param text The sequence to examine.
     * @param begin The beginning index.
     * @param end The end index.
     * @return {@code true} if the indices are valid.
     */
    private static boolean areValidIndices(CharSequence text, int begin, int end) {
        return (begin >= 0) && (end <= text.length()) && (begin <= end);
    }

    /**
     * Returns the text for an event sent from a {@link android.widget.TextView}
     * widget.
     *
     * @param event The source event.
     * @return The widget text, or {@code null}.
     */
    private static CharSequence getEventText(AccessibilityEvent event) {
        final List<CharSequence> eventText = event.getText();

        if (eventText.isEmpty()) {
            return "";
        }

        return eventText.get(0);
    }
}
