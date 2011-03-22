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

package com.google.android.marvin.aime;

import android.content.Context;
import android.graphics.Rect;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;

import java.text.BreakIterator;
import java.util.HashSet;
import java.util.Locale;

/**
 * Basic implementation of TextNavigation. Also sends events to accesibility framework.
 *
 * @author hiteshk@google.com (Hitesh Khandelwal)
 */
public class AccessibleInputConnection extends InputConnectionWrapper implements TextNavigation {
    /** Tag used for logging. */
    private static final String TAG = "AccessibleInputConnection";

    /** Debug flag. Set this to {@code false} for release. */
    private static final boolean DEBUG = false;

    /** Flag for navigating to next unit. */
    private static final int NAVIGATE_NEXT = 1;

    /** Flag for navigating to previous unit. */
    private static final int NAVIGATE_PREVIOUS = 2;

    /**
     * This is an arbitrary parcelable that's sent with an AccessibilityEvent to
     * prevent elimination of events with identical text.
     */
    private static final Parcelable JUNK_PARCELABLE = new Rect();

    /** String to speak when the cursor is at the end */
    private final String mCursorAtEnd;

    /** Handle to IME context. */
    private final Context mContext;

    /** Handle to current InputConnection to the editor. */
    private final InputConnection mIC;

    /** Extracted text from editor. */
    private ExtractedText mExtractedText;

    /*
     * Difference between Java and Android BreakIterator:<br>
     * - In Java all Iterator instances can share a common instance of CharacterIterator, while in
     * Android each Iterator instance keeps its own copy of CharacterIterator.<br>
     * - In Java setText(CharacterIterator newText) keeps argument CharacterIterator intact, while
     * Android resets the its position 0.<br>
     * - In Java last() is a valid index for preceding(), while in Android it is not a valid index
     * for preceding().<br>
     */

    /*
     * Why there is no logical line navigation in API?<br>
     * - No information about markups for logical line breaks. TextView uses Layout for fetching
     * line information.<br>
     * - InputConnection don't provide access to the dimension and statistics about TextView.<br>
     * - One workaround, is to emulate dpad_down key, but that is asynchronous, with no simple way
     * to know when key event is actually executed. Also not scalable with large text in View.
     */

    /*
     * Note: For Java line iterator, a line boundary occurs after the termination of a sequence of
     * whitespace characters. But we want to iterate over hard line breaks only. Hence we need to
     * implement wrapper navigation methods for line iterator, which ignores pre-specified list of
     * characters.
     */

    /** List of characters ignored by word iterator. */
    private final HashSet<Character> mIgnoredCharsForWord = new HashSet<Character>();

    /** List of characters ignored by Line iterator. */
    private final HashSet<Character> mIgnoredCharsForLine = new HashSet<Character>();

    /** Character iterator instance. */
    private BreakIterator mCharIterator;

    /** Word iterator instance. */
    private BreakIterator mWordIterator;

    /** Sentence iterator instance. */
    private BreakIterator mSentenceIterator;

    /** Line iterator instance. */
    private BreakIterator mLineIterator;

    /** AccessibilityManager instance, for sending events to accesibility framework */
    private AccessibilityManager mAccessibilityManager;

    /** Condition for enabling and disabling sending of accessibility events. */
    private boolean mSendAccessibilityEvents;

    /** Request sent to editor, for extracting text. */
    private ExtractedTextRequest mRequest;

    /**
     * Creates an instance of AccessibleInputConnection using the default
     * {@link Locale}.
     *
     * @param context IME's context.
     * @param inputConnection Handle to current input connection. It is
     *            responsibility of user to keep the input connection updated.
     * @param sendAccessibilityEvents Set <code>true</code> to enable sending
     *            accessibility events.
     * @param ignoredCharsForWord List of ignored characters for word iteration.
     */
    public AccessibleInputConnection(Context context, InputConnection inputConnection,
            boolean sendAccessibilityEvents, char[] ignoredCharsForWord) {
        this(context, inputConnection, sendAccessibilityEvents, ignoredCharsForWord,
                Locale.getDefault());
    }

    /**
     * Creates an instance of AccessibleInputConnection based on the specified
     * {@link Locale}.
     *
     * @param context IME's context.
     * @param inputConnection Handle to current input connection. It is
     *            responsibility of user to keep the input connection updated.
     * @param sendAccessibilityEvents Set <code>true</code> to enable sending
     *            accessibility events.
     * @param ignoredCharsForWord List of ignored characters for word iteration.
     */
    public AccessibleInputConnection(Context context, InputConnection inputConnection,
            boolean sendAccessibilityEvents, char[] ignoredCharsForWord, Locale locale) {
        super(inputConnection, true);
        if (inputConnection == null) {
            throw new IllegalArgumentException("Input connection must be non-null");
        }

        mIC = inputConnection;
        mContext = context;
        mAccessibilityManager = (AccessibilityManager) mContext
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        mSendAccessibilityEvents = sendAccessibilityEvents;

        mCharIterator = BreakIterator.getCharacterInstance(locale);
        mWordIterator = BreakIterator.getWordInstance(locale);
        mSentenceIterator = BreakIterator.getSentenceInstance(locale);
        mLineIterator = BreakIterator.getLineInstance(locale);
        for (int i = 0; i < ignoredCharsForWord.length; i++) {
            mIgnoredCharsForWord.add(ignoredCharsForWord[i]);
        }
        // Escape whitespace for line iterator
        mIgnoredCharsForLine.add(' ');

        mRequest = new ExtractedTextRequest();
        mRequest.hintMaxLines = Integer.MAX_VALUE;
        mRequest.flags = InputConnection.GET_TEXT_WITH_STYLES;

        mCursorAtEnd = mContext.getResources().getString(R.string.cursor_at_end_position);
    }

    /**
     * Checks validity of a <code>granularity</code>.
     *
     * @param granularity Value could be either {@link TextNavigation#GRANULARITY_CHAR},
     *            {@link TextNavigation#GRANULARITY_WORD},
     *            {@link TextNavigation#GRANULARITY_SENTENCE},
     *            {@link TextNavigation#GRANULARITY_PARAGRAPH} or
     *            {@link TextNavigation#GRANULARITY_ENTIRE_TEXT}
     * @throws IllegalArgumentException If granularity is invalid.
     */
    public static void checkValidGranularity(int granularity) {
        boolean correctGranularity = (granularity == TextNavigation.GRANULARITY_CHAR
                || granularity == TextNavigation.GRANULARITY_WORD
                || granularity == TextNavigation.GRANULARITY_SENTENCE
                || granularity == TextNavigation.GRANULARITY_PARAGRAPH
                || granularity == TextNavigation.GRANULARITY_ENTIRE_TEXT);
        if (!correctGranularity) {
            throw new IllegalArgumentException("granularity");
        }
    }

    /**
     * Checks validity of an <code>action</code>.
     *
     * @param action Value could be either {@link TextNavigation#ACTION_MOVE} or
     *            {@link TextNavigation#ACTION_EXTEND}.
     * @throws IllegalArgumentException If action is invalid.
     */
    public static void checkValidAction(int action) {
        boolean correctAction = (action == ACTION_MOVE || action == ACTION_EXTEND);
        if (!correctAction) {
            throw new IllegalArgumentException("action");
        }
    }

    public Position next(int granularity, int action) {
        if (DEBUG) {
            Log.i(TAG, "Next: " + granularity + " " + action);
        }
        checkValidGranularity(granularity);
        checkValidAction(action);

        if (granularity != TextNavigation.GRANULARITY_ENTIRE_TEXT) {
            return navigateNext(granularity, action);
        }

        // Text granularity = Entire text
        return navigateEntireText(NAVIGATE_NEXT, action);
    }

    public Position previous(int granularity, int action) {
        if (DEBUG) {
            Log.i(TAG, "Previous: " + granularity + " " + action);
        }
        checkValidGranularity(granularity);
        checkValidAction(action);

        if (granularity != TextNavigation.GRANULARITY_ENTIRE_TEXT) {
            return navigatePrevious(granularity, action);
        }

        // Text granularity = Entire text
        return navigateEntireText(NAVIGATE_PREVIOUS, action);
    }

    public Position get(int granularity) {
        if (DEBUG) {
            Log.i(TAG, "Get: " + granularity);
        }
        checkValidGranularity(granularity);

        if (granularity == TextNavigation.GRANULARITY_WORD
                || granularity == TextNavigation.GRANULARITY_SENTENCE
                || granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
            return getCurrentUnit(granularity);
        } else if (granularity == TextNavigation.GRANULARITY_CHAR) {
            return getNextChar();
        } else if (granularity == TextNavigation.GRANULARITY_ENTIRE_TEXT) {
            return getContent();
        }
        return null;
    }

    /**
     * @return Returns True, if sending accessibility events, is enabled.
     */
    public boolean isSendAccessibilityEvents() {
        return mSendAccessibilityEvents;
    }

    /**
     * To enable or disable sending accessibility events.
     */
    public void setSendAccessibilityEvents(boolean sendAccessibilityEvents) {
        mSendAccessibilityEvents = sendAccessibilityEvents;
    }

    /**
     * Sends a character sequence to be read aloud.
     *
     * @param description The {@link CharSequence} to be read aloud.
     */
    public void trySendAccessiblityEvent(CharSequence description) {
        if (!mAccessibilityManager.isEnabled() || !mSendAccessibilityEvents
                || TextUtils.isEmpty(description)) {
            if (DEBUG) {
                Log.e(TAG, "Not sending accessiblity event");
            }
            return;
        }

        if (DEBUG) {
            Log.i(TAG, "Spell: " + description);
        }

        // TODO We need to add an AccessibilityEvent type for IMEs.
        AccessibilityEvent event = AccessibilityEvent.obtain(
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setPackageName(mContext.getPackageName());
        event.setClassName(getClass().getName());
        event.setAddedCount(description.length());
        event.setEventTime(SystemClock.uptimeMillis());
        event.getText().add(description);

        // TODO Do we still need to add parcelable data so that we don't get
        // eliminated by TalkBack as a duplicate event? Setting the event time
        // should be enough.
        event.setParcelableData(JUNK_PARCELABLE);

        mAccessibilityManager.sendAccessibilityEvent(event);
    }

    /**
     * Extract text from editor, by sending a request.
     */
    private void fetchTextFromView() {
        mExtractedText =
                mIC.getExtractedText(mRequest, InputConnection.GET_EXTRACTED_TEXT_MONITOR);
    }

    /**
     * Returns whether this input connection is currently connected to a text box.
     *
     * @return <code>true</code> if this input connection is currently connected to a text box.
     */
    public boolean hasExtractedText() {
        fetchTextFromView();
        return (mExtractedText != null && mExtractedText.text != null);
    }

    /**
     * Returns the current extracted text or <code>null</code> if not connected to a text box.
     *
     * @return the current extracted text or <code>null</code> if not connected to a text box.
     */
    public CharSequence getExtractedText() {
        return hasExtractedText() ? mExtractedText.text : null;
    }

    /**
     * Update text in editor, with new Selection.
     *
     * @param start Selection start.
     * @param end Selection end.
     */
    private void updateTextInView(int start, int end) {
        if (DEBUG) {
            Log.i(TAG, "Start: " + start + " End: " + end);
        }
        mIC.finishComposingText();
        mIC.setSelection(start, end);
    }

    /**
     * Get iterator based on <code>granularity</code> used.
     */
    private BreakIterator getCurrentIterator(int granularity) {
        if (granularity == TextNavigation.GRANULARITY_CHAR) {
            return mCharIterator;
        } else if (granularity == TextNavigation.GRANULARITY_WORD) {
            return mWordIterator;
        } else if (granularity == TextNavigation.GRANULARITY_SENTENCE) {
            return mSentenceIterator;
        } else if (granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
            return mLineIterator;
        }
        return null;
    }

    /**
     * Get a list of ignored characters based on <code>granularity</code> used.
     */
    private HashSet<Character> getCurrentIgnoredChars(int granularity) {
        if (granularity == TextNavigation.GRANULARITY_WORD) {
            return mIgnoredCharsForWord;
        } else if (granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
            return mIgnoredCharsForLine;
        }
        return null;
    }

    /**
     * Checks, if the character is present in the list of ignored characters for the specified
     * <code>granularity</code>.
     */
    private boolean isIgnoredChar(int granularity, int index) {
        if (DEBUG) {
            Log.i(TAG, "granularity: " + granularity + " index: " + index);
        }
        if (granularity == TextNavigation.GRANULARITY_WORD) {
            boolean validIndex = index < mExtractedText.text.length();
            char charOnRight = validIndex ? mExtractedText.text.charAt(index) : '0';
            boolean nullList = getCurrentIgnoredChars(granularity) == null;
            return validIndex && !nullList
                    && getCurrentIgnoredChars(granularity).contains(charOnRight);
        } else if (granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
            boolean validIndex = index > 0;
            char charOnLeft = validIndex ? mExtractedText.text.charAt(index - 1) : '0';
            boolean nullList = getCurrentIgnoredChars(granularity) == null;
            return validIndex && !nullList
                    && getCurrentIgnoredChars(granularity).contains(charOnLeft);
        }
        return false;
    }

    /**
     * Returns the boundary following the current boundary, for line iterator.
     */
    private int nextLineIterator() {
        int currentIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).current();
        if (currentIndex == mExtractedText.text.length()) {
            return BreakIterator.DONE;
        }
        int nextIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).next();
        while (nextIndex != BreakIterator.DONE) {
            if (!isIgnoredChar(TextNavigation.GRANULARITY_PARAGRAPH, nextIndex)) {
                break;
            }
            nextIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).next();
        }
        return nextIndex;
    }

    /**
     * Returns the boundary preceding the current boundary, for line iterator.
     */
    private int previousLineIterator() {
        int currentIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).current();
        if (currentIndex == 0) {
            return BreakIterator.DONE;
        }
        int previousIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).previous();
        while (previousIndex != BreakIterator.DONE) {
            if (!isIgnoredChar(TextNavigation.GRANULARITY_PARAGRAPH, previousIndex)) {
                break;
            }
            previousIndex = getCurrentIterator(TextNavigation.GRANULARITY_PARAGRAPH).previous();
        }
        return previousIndex;
    }

    /**
     * Implementation of navigating to next unit.
     */
    private Position navigateNext(int granularity, int action) {
        // Each time, I need to fetch the text and set cursor position, because user can directly
        // change cursor position without using any iterator.

        // Fetch text from editor and update local variables
        fetchTextFromView();
        getCurrentIterator(granularity).setText(mExtractedText.text.toString());
        int selectionStart = mExtractedText.selectionStart;
        int selectionEnd = mExtractedText.selectionEnd;
        int textLength = mExtractedText.text.length();

        if (selectionEnd == textLength) {
            // Handle corner case when cursor is at end of the text, Android implementation deviates
            // from standard Java implementation, check comments above.
            return null;
        }
        int nextIndex = getCurrentIterator(granularity).following(selectionEnd);

        // We dont need to loop when nextIndex == textLength
        while (nextIndex != BreakIterator.DONE) {
            if (!isIgnoredChar(granularity, nextIndex)) {
                if (action == ACTION_MOVE) {
                    updateTextInView(nextIndex, nextIndex);
                    // Be careful, if we are going to use mIterator again
                    int unitEndIndex;
                    if (granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
                        unitEndIndex = nextLineIterator();
                    } else {
                        unitEndIndex = getCurrentIterator(granularity).next();
                    }

                    if (DEBUG) {
                        Log.i(TAG, "nextIndex: " + nextIndex + " unitEndIndex: " + unitEndIndex);
                    }

                    if (unitEndIndex != BreakIterator.DONE) {
                        // Send new unit
                        CharSequence spell = mExtractedText.text.subSequence(nextIndex,
                                unitEndIndex);
                        trySendAccessiblityEvent(spell.toString());
                    } else {
                        trySendAccessiblityEvent(mCursorAtEnd);
                    }
                    // Position of new unit encountered
                    return Position.obtain(nextIndex, unitEndIndex, false);
                } else if (action == ACTION_EXTEND) {
                    updateTextInView(selectionStart, nextIndex);
                    if (DEBUG) {
                        Log.i(TAG,
                                "selectionStart: " + selectionStart + " nextIndex: " + nextIndex);
                    }
                    // Send additional text selected.
                    CharSequence spell = mExtractedText.text.subSequence(selectionEnd, nextIndex);
                    trySendAccessiblityEvent(spell.toString());
                    // Position of selection
                    return Position.obtain(selectionStart, nextIndex, true);
                }
                return null;
            }
            nextIndex = getCurrentIterator(granularity).next();
        }
        return null;
    }

    /**
     * Implementation of navigating to previous unit.
     */
    private Position navigatePrevious(int granularity, int action) {
        // Fetch text from editor and update local variables
        fetchTextFromView();
        getCurrentIterator(granularity).setText(mExtractedText.text.toString());
        int selectionStart = mExtractedText.selectionStart;
        int selectionEnd = mExtractedText.selectionEnd;
        int textLength = mExtractedText.text.length();

        // Selection extension, always refers to moving selectionEnd only.
        int previousIndex;
        if (selectionEnd == textLength) {
            // Handle corner case when cursor is at end of the text, Android implementation deviates
            // from standard Java implementation, check comments above.
            getCurrentIterator(granularity).last();
            previousIndex = getCurrentIterator(granularity).previous();
        } else {
            previousIndex = getCurrentIterator(granularity).preceding(selectionEnd);
        }

        // We dont need to loop when previousIndex == 0
        while (previousIndex != BreakIterator.DONE) {
            if (!isIgnoredChar(granularity, previousIndex)) {
                if (action == ACTION_MOVE) {
                    updateTextInView(previousIndex, previousIndex);
                    // We are issuing next again because we don't want ignored chars
                    int unitEndIndex;
                    if (granularity == TextNavigation.GRANULARITY_PARAGRAPH) {
                        unitEndIndex = previousLineIterator();
                    } else {
                        unitEndIndex = getCurrentIterator(granularity).next();
                    }

                    if (DEBUG) {
                        Log.i(TAG, "previousIndex: " + previousIndex + " unitEndIndex: "
                                + unitEndIndex);
                    }
                    if (unitEndIndex != BreakIterator.DONE) {
                        CharSequence spell = mExtractedText.text.subSequence(previousIndex,
                                unitEndIndex);
                        trySendAccessiblityEvent(spell.toString());
                    }
                    return Position.obtain(previousIndex, unitEndIndex, false);
                } else if (action == ACTION_EXTEND) {
                    updateTextInView(selectionStart, previousIndex);
                    if (DEBUG) {
                        Log.i(TAG, "selectionStart: " + selectionStart + " previousIndex: "
                                + previousIndex);
                    }
                    // We dont need to issue next again, including ignored chars
                    CharSequence spell = mExtractedText.text.subSequence(previousIndex,
                            selectionEnd);
                    trySendAccessiblityEvent(spell.toString());
                    return Position.obtain(selectionStart, previousIndex, true);
                }
            }
            previousIndex = getCurrentIterator(granularity).previous();
        }
        return null;
    }

    /**
     * Navigating, when text unit is Entire text itself.<br>
     * It is implemented separately, because we don't have iterator for this granularity.
     */
    private Position navigateEntireText(int direction, int action) {
        // Fetch text from editor and update local variables
        fetchTextFromView();
        int selectionStart = mExtractedText.selectionStart;
        int selectionEnd = mExtractedText.selectionEnd;
        int textLength = mExtractedText.text.length();
        int newPosition = direction == NAVIGATE_NEXT ? textLength : 0;

        if (DEBUG) {
            Log.i(TAG, "selectionStart: " + selectionStart + " selectionEnd: " + selectionEnd
                    + " textLength: " + textLength + " newPosition: " + newPosition);
        }

        if (action == ACTION_MOVE) {
            updateTextInView(newPosition, newPosition);
            // No Accessibility event fired for next
            if (direction == NAVIGATE_PREVIOUS) {
                trySendAccessiblityEvent(mExtractedText.text.toString());
            }
            // Position of new unit encountered
            return Position.obtain(newPosition, newPosition, false);
        } else if (action == ACTION_EXTEND) {
            updateTextInView(selectionStart, newPosition);
            // Send additional text selected.
            int lowerIndex = (direction == NAVIGATE_NEXT) ? selectionEnd : newPosition;
            int higherUpper = (direction == NAVIGATE_NEXT) ? newPosition : selectionEnd;
            CharSequence spell = mExtractedText.text.subSequence(lowerIndex, higherUpper);
            trySendAccessiblityEvent(spell.toString());
            // Position of selection
            return Position.obtain(selectionStart, newPosition, true);
        }
        return null;
    }

    /**
     * Fetch character on right of the cursor position.
     */
    private Position getNextChar() {
        fetchTextFromView();
        getCurrentIterator(TextNavigation.GRANULARITY_CHAR)
                .setText(mExtractedText.text.toString());
        int selectionStart = mExtractedText.selectionStart;
        int selectionEnd = mExtractedText.selectionEnd;
        int textLength = mExtractedText.text.length();

        int nextPosition;
        if (selectionStart != selectionEnd || selectionStart == textLength) {
            // Return, if in selection mode, or at the end of the text
            return null;
        } else {
            nextPosition = getCurrentIterator(TextNavigation.GRANULARITY_CHAR)
                    .following(selectionStart);
        }

        if (DEBUG) {
            Log.i(TAG, "selectionStart: " + selectionStart + " nextPosition: " + nextPosition);
        }
        if (nextPosition != BreakIterator.DONE) {
            CharSequence spell = mExtractedText.text.subSequence(selectionStart, nextPosition);
            trySendAccessiblityEvent(spell.toString());
            return Position.obtain(selectionStart, nextPosition, false);
        }
        return null;
    }

    /**
     * Implementation of getting current word or sentence. We need a different version for
     * getNextChar(), because of case when, selectionStart == textLength.
     */
    private Position getCurrentUnit(int granularity) {
        // Fetch text from editor and update local variables
        fetchTextFromView();
        getCurrentIterator(granularity).setText(mExtractedText.text.toString());
        int selectionStart = mExtractedText.selectionStart;
        int selectionEnd = mExtractedText.selectionEnd;
        int textLength = mExtractedText.text.length();

        // Return, if in selection mode
        if (selectionStart != selectionEnd) {
            return null;
        }

        int unitStartIndex = selectionStart;
        int unitEndIndex = selectionStart;
        if (selectionStart == textLength) {
            // Handle corner case when cursor is at end of the text.
            getCurrentIterator(granularity).last();
            unitStartIndex = getCurrentIterator(granularity).previous();
            unitEndIndex = getCurrentIterator(granularity).next();
        } else {
            boolean onRightEdgeOfWord = isIgnoredChar(granularity, unitStartIndex);
            boolean onRightEdgeOfSentence = getCurrentIterator(granularity)
                    .isBoundary(unitStartIndex);

            if (granularity == TextNavigation.GRANULARITY_WORD && onRightEdgeOfWord) {
                unitStartIndex = getCurrentIterator(granularity).preceding(selectionStart);
            } else if (granularity == TextNavigation.GRANULARITY_SENTENCE
                    && onRightEdgeOfSentence) {
                unitEndIndex = getCurrentIterator(granularity).following(selectionStart);
            } else {
                // In beginning or between the current unit
                unitEndIndex = getCurrentIterator(granularity).following(selectionStart);
                unitStartIndex = getCurrentIterator(granularity).previous();
            }
        }

        if (DEBUG) {
            Log.i(TAG, "startIndex: " + unitStartIndex + " endIndex: " + unitEndIndex);
        }
        CharSequence spell = mExtractedText.text.subSequence(unitStartIndex, unitEndIndex);
        trySendAccessiblityEvent(spell.toString());
        return Position.obtain(unitStartIndex, unitEndIndex, false);
    }

    private Position getContent() {
        fetchTextFromView();
        trySendAccessiblityEvent(mExtractedText.text.toString());
        return Position.obtain(0, mExtractedText.text.length() - 1, false);
    }
}
