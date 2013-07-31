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

import android.content.Context;
import android.text.TextUtils;
import android.util.SparseIntArray;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for cleaning up speech text.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class SpeechCleanupUtils {
    /** The regular expression used to match consecutive identical characters */
    // Double escaping of regex characters is required. "\\1" refers to the
    // first capturing group between the outer nesting of "[]"s and "{2,}"
    // refers to two or more additional repetitions thereof.
    private static final
            String CONSECUTIVE_CHARACTER_REGEX = "([\\-\\\\/|!@#$%^&*\\(\\)=_+\\[\\]\\{\\}.?;'\":<>])\\1{2,}";

    /** The Pattern used to match consecutive identical characters */
    private static Pattern CONSECUTIVE_CHARACTER_PATTERN = Pattern.compile(
            CONSECUTIVE_CHARACTER_REGEX);

    /** Map containing string to speech conversions. */
    private static final SparseIntArray UNICODE_MAP = new SparseIntArray();

    static {
        UNICODE_MAP.put('&', R.string.symbol_ampersand);
        UNICODE_MAP.put('<', R.string.symbol_angle_bracket_left);
        UNICODE_MAP.put('>', R.string.symbol_angle_bracket_right);
        UNICODE_MAP.put('\'', R.string.symbol_apostrophe);
        UNICODE_MAP.put('*', R.string.symbol_asterisk);
        UNICODE_MAP.put('@', R.string.symbol_at_sign);
        UNICODE_MAP.put('\\', R.string.symbol_backslash);
        UNICODE_MAP.put('\u2022', R.string.symbol_bullet);
        UNICODE_MAP.put('^', R.string.symbol_caret);
        UNICODE_MAP.put('¢', R.string.symbol_cent);
        UNICODE_MAP.put(':', R.string.symbol_colon);
        UNICODE_MAP.put(',', R.string.symbol_comma);
        UNICODE_MAP.put('©', R.string.symbol_copyright);
        UNICODE_MAP.put('{', R.string.symbol_curly_bracket_left);
        UNICODE_MAP.put('}', R.string.symbol_curly_bracket_right);
        UNICODE_MAP.put('°', R.string.symbol_degree);
        UNICODE_MAP.put('\u00F7', R.string.symbol_division);
        UNICODE_MAP.put('$', R.string.symbol_dollar_sign);
        UNICODE_MAP.put('…', R.string.symbol_ellipsis);
        UNICODE_MAP.put('\u2014', R.string.symbol_em_dash);
        UNICODE_MAP.put('\u2013', R.string.symbol_en_dash);
        UNICODE_MAP.put('€', R.string.symbol_euro);
        UNICODE_MAP.put('!', R.string.symbol_exclamation_mark);
        UNICODE_MAP.put('`', R.string.symbol_grave_accent);
        UNICODE_MAP.put('-', R.string.symbol_hyphen_minus);
        UNICODE_MAP.put('„', R.string.symbol_low_double_quote);
        UNICODE_MAP.put('\u00D7', R.string.symbol_multiplication);
        UNICODE_MAP.put('\n', R.string.symbol_new_line);
        UNICODE_MAP.put('¶', R.string.symbol_paragraph_mark);
        UNICODE_MAP.put('(', R.string.symbol_parenthesis_left);
        UNICODE_MAP.put(')', R.string.symbol_parenthesis_right);
        UNICODE_MAP.put('%', R.string.symbol_percent);
        UNICODE_MAP.put('.', R.string.symbol_period);
        UNICODE_MAP.put('π', R.string.symbol_pi);
        UNICODE_MAP.put('#', R.string.symbol_pound);
        UNICODE_MAP.put('£', R.string.symbol_pound_sterling);
        UNICODE_MAP.put('?', R.string.symbol_question_mark);
        UNICODE_MAP.put('"', R.string.symbol_quotation_mark);
        UNICODE_MAP.put('®', R.string.symbol_registered_trademark);
        UNICODE_MAP.put(';', R.string.symbol_semicolon);
        UNICODE_MAP.put('/', R.string.symbol_slash);
        UNICODE_MAP.put(' ', R.string.symbol_space);
        UNICODE_MAP.put('[', R.string.symbol_square_bracket_left);
        UNICODE_MAP.put(']', R.string.symbol_square_bracket_right);
        UNICODE_MAP.put('√', R.string.symbol_square_root);
        UNICODE_MAP.put('™', R.string.symbol_trademark);
        UNICODE_MAP.put('_', R.string.symbol_underscore);
        UNICODE_MAP.put('|', R.string.symbol_vertical_bar);
        UNICODE_MAP.put('\u00a5', R.string.symbol_yen);
        UNICODE_MAP.put('\u00ac', R.string.symbol_not_sign);
        UNICODE_MAP.put('\u00a6', R.string.symbol_broken_bar);
        UNICODE_MAP.put('\u00b5', R.string.symbol_micro_sign);
        UNICODE_MAP.put('\u2248', R.string.symbol_almost_equals);
        UNICODE_MAP.put('\u2260', R.string.symbol_not_equals);
        UNICODE_MAP.put('\u00a4', R.string.symbol_currency_sign);
        UNICODE_MAP.put('\u00a7', R.string.symbol_section_sign);
        UNICODE_MAP.put('\u2191', R.string.symbol_upwards_arrow);
        UNICODE_MAP.put('\u2190', R.string.symbol_leftwards_arrow);
    }

    /**
     * Cleans up text for speech. Converts symbols to their spoken equivalents.
     *
     * @param context The context used to resolve string resources.
     * @param text The text to clean up.
     * @return Cleaned up text.
     */
    public static CharSequence cleanUp(Context context, CharSequence text) {
        if ((text == null) || (text.length() != 1)) {
            return text;
        }

        return getCleanValueFor(context, text.charAt(0));
    }

    /**
     * Collapses repeated consecutive characters in a CharSequence by matching
     * against {@link #CONSECUTIVE_CHARACTER_REGEX}.
     *
     * @param context Context for retrieving resources
     * @param text The text to process
     * @return The text with consecutive identical characters collapsed
     */
    public static CharSequence collapseRepeatedCharacters(Context context, CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }

        // TODO(caseyburkhardt): Add tests
        Matcher matcher = CONSECUTIVE_CHARACTER_PATTERN.matcher(text);
        while (matcher.find()) {
            final String replacement = context.getString(R.string.character_collapse_template,
                    matcher.group().length(), getCleanValueFor(context, matcher.group().charAt(0)));
            final int matchFromIndex = matcher.end() - matcher.group().length()
                    + replacement.length();
            text = matcher.replaceFirst(replacement);
            matcher = CONSECUTIVE_CHARACTER_PATTERN.matcher(text);
            matcher.region(matchFromIndex, text.length());
        }

        return text;
    }

    /**
     * Returns the "clean" value for the specified character.
     */
    public static String getCleanValueFor(Context context, char key) {
        final int resId = UNICODE_MAP.get(key);

        if (resId != 0) {
            return context.getString(resId);
        }

        if (Character.isUpperCase(key)) {
            return context.getString(R.string.template_capital_letter, Character.toString(key));
        }

        return Character.toString(key);
    }
}
