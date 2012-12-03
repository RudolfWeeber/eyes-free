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
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.SpeechCleanupUtils;
import com.google.android.marvin.talkback.SpeechController.SpeechParam;
import com.google.android.marvin.talkback.TalkBackService;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.utils.StringBuilderUtils;
import com.googlecode.eyesfree.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.List;

/**
 * This class contains custom formatters for presenting text edits.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public final class TextFormatters {
    // These must be synchronized with res/values/donottranslate.xml
    private static final int PREF_ECHO_ALWAYS = 0;
    private static final int PREF_ECHO_SOFTKEYS = 1;
    private static final int PREF_ECHO_NEVER = 2;

    /** Default pitch adjustment for text event feedback. */
    private static final float DEFAULT_PITCH = 1.2f;

    /** Default rate adjustment for text event feedback. */
    private static final float DEFAULT_RATE = 1.0f;

    /** Minimum delay between change and selection events. */
    private static final long SELECTION_DELAY = 100;

    /** Minimum delay between change events without an intervening selection. */
    private static final long CHANGED_DELAY = 100;

    /** Event time of the most recently processed change event. */
    private static long sChangedTimestamp = -1;

    /** Package name of the most recently processed change event. */
    private static CharSequence sChangedPackage = null;

    /** Whether we received a change event and are waiting for a selection. */
    private static boolean sAwaitingSelection;

    private TextFormatters() {
        // Not publicly instantiable.
    }

    /**
     * Formatter that returns an utterance to announce text replacement.
     */
    public static final class ChangedTextFormatter implements AccessibilityEventFormatter {
        @Override
        public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
            final long timestamp = event.getEventTime();

            // Drop change event if we're still waiting for a select event and
            // the change occurred too soon after the previous change.
            if (sAwaitingSelection && ((timestamp - sChangedTimestamp) < CHANGED_DELAY)) {
                return false;
            }

            if (!formatInternal(event, context, utterance)) {
                return false;
            }

            sAwaitingSelection = true;
            sChangedTimestamp = timestamp;
            sChangedPackage = event.getPackageName();

            if (!shouldEchoKeyboard(context)) {
                return false;
            }

            return true;
        }

        private boolean shouldEchoKeyboard(Context context) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final Resources res = context.getResources();
            final int keyboardPref = SharedPreferencesUtils.getIntFromStringPref(prefs, res,
                    R.string.pref_keyboard_echo_key, R.string.pref_keyboard_echo_default);

            switch (keyboardPref) {
                case PREF_ECHO_ALWAYS:
                    return true;
                case PREF_ECHO_SOFTKEYS:
                    final Configuration config = res.getConfiguration();
                    return (config.keyboard == Configuration.KEYBOARD_NOKEYS) ||
                            (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES);
                case PREF_ECHO_NEVER:
                    return false;
                default:
                    LogUtils.log(this, Log.ERROR, "Invalid keyboard echo preference value: %d",
                            keyboardPref);
                    return false;
            }
        }

        private boolean formatInternal(
                AccessibilityEvent event, TalkBackService context, Utterance utterance) {
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

            // If multi-character text was cleared, stop now.
            final boolean wasCleared = (event.getRemovedCount() > 1) && (event.getAddedCount() == 0)
                    && (event.getBeforeText().length() == event.getRemovedCount());
            if (wasCleared) {
                StringBuilderUtils.appendWithSeparator(utteranceText,
                        context.getString(R.string.value_text_cleared));
                return true;
            }

            CharSequence removedText = getRemovedText(event, context);
            CharSequence addedText = getAddedText(event, context);

            // Never say "replaced Hello with Hello".
            if (TextUtils.equals(addedText, removedText)) {
                LogUtils.log(this, Log.DEBUG, "Drop event, nothing changed");
                return false;
            }

            // Abort if either text is null (indicates an error).
            if ((removedText == null) || (addedText == null)) {
                LogUtils.log(this, Log.DEBUG, "Drop event, either added or removed was null");
                return false;
            }

            final int removedLength = removedText.length();
            final int addedLength = addedText.length();

            // Translate partial replacement into addition / deletion.
            if (removedLength > addedLength) {
                if (TextUtils.regionMatches(removedText, 0, addedText, 0, addedLength)) {
                    removedText = TextUtils.substring(removedText, addedLength, removedLength);
                    addedText = "";
                }
            } else if (addedLength > removedLength) {
                if (TextUtils.regionMatches(removedText, 0, addedText, 0, removedLength)) {
                    removedText = "";
                    addedText = TextUtils.substring(addedText, removedLength, addedLength);
                }
            }

            // Apply any speech clean up rules. Usually this means changing "A"
            // to "capital A" or "[" to "left bracket".
            final CharSequence cleanRemovedText = SpeechCleanupUtils.cleanUp(context, removedText);
            final CharSequence cleanAddedText = SpeechCleanupUtils.cleanUp(context, addedText);

            if (!TextUtils.isEmpty(cleanAddedText)) {
                // Text was added. This includes replacement.
                if (!appendLastWordIfNeeded(event, context, utteranceText)) {
                    StringBuilderUtils.appendWithSeparator(utteranceText, cleanAddedText);
                }
                return true;
            }

            if (!TextUtils.isEmpty(cleanRemovedText)) {
                // Text was only removed.
                StringBuilderUtils.appendWithSeparator(utteranceText, cleanRemovedText,
                        context.getString(R.string.value_text_removed));
                return true;
            }

            LogUtils.log(this, Log.DEBUG, "Drop event, cleaned up text was empty");
            return false;
        }

        private boolean appendLastWordIfNeeded(
                AccessibilityEvent event, Context context, StringBuilder utteranceText) {
            final CharSequence text = getEventText(event);
            final int fromIndex = event.getFromIndex();

            if (fromIndex > text.length()) {
                LogUtils.log(this, Log.WARN, "Received event with invalid fromIndex: %s", event);
                return false;
            }

            // Is this text a word boundary?
            if (!Character.isWhitespace(text.charAt(fromIndex))) {
                return false;
            }

            final int breakIndex = getPrecedingWhitespace(text, fromIndex);
            final CharSequence word = text.subSequence(breakIndex, fromIndex);

            // Did the user just type a word?
            if (TextUtils.getTrimmedLength(word) == 0) {
                return false;
            }

            StringBuilderUtils.appendWithSeparator(utteranceText, word);
            return true;
        }

        private static int getPrecedingWhitespace(CharSequence text, int fromIndex) {
            for (int i = (fromIndex - 1); i > 0; i--) {
                if (Character.isWhitespace(text.charAt(i))) {
                    return i;
                }
            }

            return 0;
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

            // Special case for deleting all the text in an EditText with a
            // hint, since the event text will contain the hint rather than an
            // empty string.
            if ((event.getAddedCount() == 0) && (event.getRemovedCount() == beforeText.length())) {
                return true;
            }

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
            final List<CharSequence> textList = event.getText();
            if ((textList == null) || (textList.size() > 1)) {
                LogUtils.log(this, Log.WARN, "getAddedText: Text list was null or bad size");
                return null;
            }

            // If the text was empty, the list will be empty. See the
            // implementation for TextView.onPopulateAccessibilityEvent().
            if (textList.size() == 0) {
                return "";
            }

            final CharSequence text = event.getText().get(0);
            if (text == null) {
                LogUtils.log(this, Log.WARN, "getAddedText: First text entry was null");
                return null;
            }

            final int addedBegIndex = event.getFromIndex();
            final int addedEndIndex = addedBegIndex + event.getAddedCount();
            if (!areValidIndices(text, addedBegIndex, addedEndIndex)) {
                LogUtils.log(this, Log.WARN, "getAddedText: Invalid indices (%d,%d) for \"%s\"",
                        addedBegIndex, addedEndIndex, text);
                return null;
            }

            return text.subSequence(addedBegIndex, addedEndIndex);
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

            return beforeText.subSequence(beforeBegIndex, beforeEndIndex);
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
        public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
            sAwaitingSelection = false;

            if (shouldIgnoreEvent(event)) {
                return false;
            }

            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final CharSequence text;

            if (event.getEventType() ==
                    AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
                text = AccessibilityEventUtils.getEventText(event);
            } else {
                text = getEventText(event);
            }

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
            if (TextUtils.isEmpty(text) || (count == 0)) {
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
         * Checks whether TalkBack should provide feedback for the event.
         *
         * @param event The text selected event to validate.
         * @return {@code true} if the event needs feedback.
         */
        private boolean shouldIgnoreEvent(AccessibilityEvent event) {
            final long selectedTimestamp = event.getEventTime();
            final CharSequence selectedPackage = event.getPackageName();

            // Ignore the first selection following a changed event.
            if ((sChangedTimestamp > 0)
                    && ((selectedTimestamp - sChangedTimestamp) < SELECTION_DELAY)
                    && TextUtils.equals(selectedPackage, sChangedPackage)) {
                sChangedTimestamp = -1;
                sChangedPackage = null;
                return true;
            }

            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final AccessibilityNodeInfoCompat source = record.getSource();

            // We shouldn't be getting text events from EditText fields that
            // don't have any type of focus.
            if ((source != null) && !(source.isAccessibilityFocused() || source.isFocused())) {
                return true;
            }

            return false;
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
