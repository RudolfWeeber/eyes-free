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

package com.googlecode.eyesfree.utils;

import android.text.TextUtils;

public class StringBuilderUtils {
    /**
     * Breaking separator inserted between text, intended to make TTS pause an
     * appropriate amount. Using a period breaks pronunciation of street
     * abbreviations, and using a new line doesn't work in eSpeak.
     */
    /* package */static final String DEFAULT_BREAKING_SEPARATOR = ", ";

    /**
     * Non-breaking separator inserted between text. Used when text already ends
     * with some sort of breaking separator or non-alphanumeric character.
     */
    /* package */static final String DEFAULT_SEPARATOR = " ";

    /**
     * Appends string representations of the specified arguments to a
     * {@link StringBuilder}, creating one if the supplied builder is
     * {@code null}.
     *
     * @param builder An existing {@link StringBuilderUtils}, or {@code null} to
     *            create one.
     * @param args The objects to append to the builder.
     * @return A builder with the specified objects appended.
     */
    public static StringBuilder appendWithSeparator(StringBuilder builder, Object... args) {
        if (builder == null) {
            builder = new StringBuilder();
        }

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            final String strArg = String.valueOf(arg);
            if (strArg.length() == 0) {
                continue;
            }

            if (builder.length() > 0) {
                if (needsBreakingSeparator(builder)) {
                    builder.append(DEFAULT_BREAKING_SEPARATOR);
                } else {
                    builder.append(DEFAULT_SEPARATOR);
                }
            }

            builder.append(strArg);
        }

        return builder;
    }

    /**
     * Returns whether the text needs a breaking separator (e.g. a period
     * followed by a space) appended before more text is appended.
     * <p>
     * If text ends with a letter or digit (according to the current locale)
     * then this method will return {@code true}.
     */
    private static boolean needsBreakingSeparator(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }

        return Character.isLetterOrDigit(text.charAt(text.length() - 1));
    }
}
