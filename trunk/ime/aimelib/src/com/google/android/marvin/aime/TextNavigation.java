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

/**
 * This interface allows to navigate through text by character, word, sentence and paragraph.
 *
 * @author hiteshk@google.com (Hitesh Khandelwal)
 */
public interface TextNavigation {
    /** Flag to indicate moving cursor. */
    public static final int ACTION_MOVE = 0;

    /** Flag to indicate extending selection. */
    public static final int ACTION_EXTEND = 1;

    /** Flag for navigating by character. */
    public static final int GRANULARITY_CHAR = 0;

    /** Flag for navigating by word. */
    public static final int GRANULARITY_WORD = 1;

    /** Flag for navigating by sentence. */
    public static final int GRANULARITY_SENTENCE = 2;

    /** Flag for navigating by paragraph (hard line breaks). */
    public static final int GRANULARITY_PARAGRAPH = 3;

    /** Flag for entire content. */
    public static final int GRANULARITY_ENTIRE_TEXT = 4;

    /** Number of granularity types. */
    public static final int NUM_GRANULARITY_TYPES = 5;

    /**
     * Move cursor or extend selection to the beginning of next unit.
     *
     * @param granularity Granularity for navigation. Value could be {@link #GRANULARITY_CHAR},
     *            {@link #GRANULARITY_WORD}, {@link #GRANULARITY_SENTENCE},
     *            {@link #GRANULARITY_PARAGRAPH} or {@link #GRANULARITY_ENTIRE_TEXT}
     * @param action Set action need to be performed on Selection. Value could be either
     *            {@link #ACTION_MOVE} or{@link #ACTION_EXTEND}.
     * @return Start and end positions of the unit. Returns null if <code>granularity</code> or
     *         <code>action</code> is invalid.
     */
    public Position next(int granularity, int action);

    /**
     * Move cursor or extend selection to the beginning of previous unit.
     *
     * @param granularity Granularity for navigation. Value could be {@link #GRANULARITY_CHAR},
     *            {@link #GRANULARITY_WORD}, {@link #GRANULARITY_SENTENCE},
     *            {@link #GRANULARITY_PARAGRAPH} or {@link #GRANULARITY_ENTIRE_TEXT}
     * @param action Set action need to be performed on Selection. Value could be either
     *            {@link #ACTION_MOVE} or{@link #ACTION_EXTEND}.
     * @return Start and end positions of the unit. Returns null if <code>granularity</code> or
     *         <code>action</code> is invalid.
     */
    public Position previous(int granularity, int action);

    /**
     * Get current text unit, pointed by cursor position. For character as granularity, current
     * character is the character on the right of cursor position.
     *
     * @param granularity Granularity for navigation. Value could be {@link #GRANULARITY_CHAR},
     *            {@link #GRANULARITY_WORD}, {@link #GRANULARITY_SENTENCE},
     *            {@link #GRANULARITY_PARAGRAPH} or {@link #GRANULARITY_ENTIRE_TEXT}
     * @return Start and end positions of the unit. Returns null if <code>granularity</code> or
     *         <code>action</code> is invalid, or text is in selection mode.
     */
    public Position get(int granularity);
}
