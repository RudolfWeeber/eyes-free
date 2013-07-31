/*
 * Copyright (C) 2011 Google Inc.
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

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import java.util.List;

public enum CursorGranularity {
    DEFAULT(R.id.object, R.string.granularity_default, Integer.MIN_VALUE),
    CHARACTER(R.id.character, R.string.granularity_character,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER),
    WORD(R.id.word, R.string.granularity_word,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD),
    LINE(R.id.line, R.string.granularity_line,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE),
    PARAGRAPH(R.id.paragraph, R.string.granularity_paragraph,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH),
    PAGE(R.id.page, R.string.granularity_page,
            AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE),
    WEB_SECTION(R.id.web_section, R.string.granularity_web_section, Integer.MIN_VALUE),
    WEB_LIST(R.id.web_list, R.string.granularity_web_list, Integer.MIN_VALUE),
    WEB_CONTROL(R.id.web_control, R.string.granularity_web_control, Integer.MIN_VALUE);

    /** Used to to represent a granularity with no framework value. */
    private static final int NO_VALUE = Integer.MIN_VALUE;

    /** The unique resource identifier associated with this granularity. */
    public final int keyId;

    /** The resource identifier for this granularity's user-visible name. */
    public final int resId;

    /**
     * The framework value for this granularity, passed as an argument to
     * {@link AccessibilityNodeInfoCompat#ACTION_NEXT_AT_MOVEMENT_GRANULARITY}.
     */
    public final int value;

    /**
     * Constructs a new granularity with the specified system identifier.
     * @param value The system identifier. See the GRANULARITY_ constants in
     *            {@link AccessibilityNodeInfoCompat} for a complete list.
     */
    private CursorGranularity(int keyId, int resId, int value) {
        this.keyId = keyId;
        this.value = value;
        this.resId = resId;
    }

    /**
     * Returns the granularity associated with a particular key.
     *
     * @param keyId The key associated with a granularity.
     * @return The granularity associated with the key, or {@code null} if the
     *         key is invalid.
     */
    public static CursorGranularity fromKey(int keyId) {
        for (CursorGranularity value : values()) {
            if (value.keyId == keyId) {
                return value;
            }
        }

        return null;
    }

    /**
     * Returns the next best granularity supported by a given node. The
     * returned granularity will always be equal to or larger than the
     * requested granularity.
     *
     * @param requested The requested granularity.
     * @param node The node to test.
     * @return The next best granularity supported by the node.
     */
    public static CursorGranularity getNextBestGranularity(
            CursorGranularity requested, AccessibilityNodeInfoCompat node) {
        final int bitmask = node.getMovementGranularities();

        for (CursorGranularity granularity : values()) {
            if (granularity.value == NO_VALUE) {
                continue;
            }

            if (granularity.value < requested.value) {
                // Don't return a smaller granularity.
                continue;
            }

            if ((bitmask & granularity.value) == granularity.value) {
                // This is a supported granularity.
                return granularity;
            }
        }

        // If we cannot find a supported granularity, use the default.
        return DEFAULT;
    }

    /**
     * Populates {@code result} with the {@link CursorGranularity}s represented
     * by the {@code bitmask} of granularity framework values. The
     * {@link #DEFAULT} granularity is always returned as the first item in the
     * list.
     *
     * @param bitmask A bit mask of granularity framework values.
     * @param hasWebContent Whether the view has web content.
     * @param result The list to populate with supported granularities.
     */
    public static void extractFromMask(
            int bitmask, boolean hasWebContent, List<CursorGranularity> result) {
        result.clear();
        result.add(DEFAULT);

        for (CursorGranularity value : values()) {
            if (value.value == NO_VALUE) {
                continue;
            }

            if ((bitmask & value.value) == value.value) {
                result.add(value);
            }
        }

        if (hasWebContent) {
            result.add(WEB_SECTION);
            result.add(WEB_LIST);
            result.add(WEB_CONTROL);
        }
    }

    /**
     * @return Whether {@code granularity} is a web-specific granularity.
     */
    public static boolean isWebGranularity(CursorGranularity requestedGranularity) {
        switch (requestedGranularity) {
            case WEB_SECTION:
            case WEB_LIST:
            case WEB_CONTROL:
                return true;
            default:
                return false;
        }
    }
}