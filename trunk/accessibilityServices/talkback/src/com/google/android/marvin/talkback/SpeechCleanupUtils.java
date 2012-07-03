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

import java.util.HashMap;

/**
 * Utilities for cleaning up speech text.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class SpeechCleanupUtils {
    /** Map containing string to speech conversions. */
    private static HashMap<String, Object> sCleanupMap;

    /**
     * Populates the cleanup map with default conversions.
     */
    private static void populateCleanupMap() {
        sCleanupMap = new HashMap<String, Object>();

        sCleanupMap.put("&", R.string.symbol_ampersand);
        sCleanupMap.put("<", R.string.symbol_angle_bracket_left);
        sCleanupMap.put(">", R.string.symbol_angle_bracket_right);
        sCleanupMap.put("\'", R.string.symbol_apostrophe);
        sCleanupMap.put("*", R.string.symbol_asterisk);
        sCleanupMap.put("@", R.string.symbol_at_sign);
        sCleanupMap.put("\\", R.string.symbol_backslash);
        sCleanupMap.put("•", R.string.symbol_bullet);
        sCleanupMap.put("^", R.string.symbol_caret);
        sCleanupMap.put("¢", R.string.symbol_cent);
        sCleanupMap.put(":", R.string.symbol_colon);
        sCleanupMap.put(",", R.string.symbol_comma);
        sCleanupMap.put("©", R.string.symbol_copyright);
        sCleanupMap.put("{", R.string.symbol_curly_bracket_left);
        sCleanupMap.put("}", R.string.symbol_curly_bracket_right);
        sCleanupMap.put("°", R.string.symbol_degree);
        sCleanupMap.put("$", R.string.symbol_dollar_sign);
        sCleanupMap.put("…", R.string.symbol_ellipsis);
        sCleanupMap.put("\u2014", R.string.symbol_em_dash);
        sCleanupMap.put("\u2013", R.string.symbol_en_dash);
        sCleanupMap.put("€", R.string.symbol_euro);
        sCleanupMap.put("!", R.string.symbol_exclamation_mark);
        sCleanupMap.put("`", R.string.symbol_grave_accent);
        sCleanupMap.put("-", R.string.symbol_hyphen_minus);
        sCleanupMap.put("„", R.string.symbol_low_double_quote);
        sCleanupMap.put("¶", R.string.symbol_paragraph_mark);
        sCleanupMap.put("(", R.string.symbol_parenthesis_left);
        sCleanupMap.put(")", R.string.symbol_parenthesis_right);
        sCleanupMap.put("%", R.string.symbol_percent);
        sCleanupMap.put(".", R.string.symbol_period);
        sCleanupMap.put("π", R.string.symbol_pi);
        sCleanupMap.put("#", R.string.symbol_pound);
        sCleanupMap.put("£", R.string.symbol_pound_sterling);
        sCleanupMap.put("?", R.string.symbol_question_mark);
        sCleanupMap.put("\"", R.string.symbol_quotation_mark);
        sCleanupMap.put("®", R.string.symbol_registered_trademark);
        sCleanupMap.put(";", R.string.symbol_semicolon);
        sCleanupMap.put("/", R.string.symbol_slash);
        sCleanupMap.put(":-)", R.string.symbol_smiley);
        sCleanupMap.put(" ", R.string.symbol_space);
        sCleanupMap.put("[", R.string.symbol_square_bracket_left);
        sCleanupMap.put("]", R.string.symbol_square_bracket_right);
        sCleanupMap.put("√", R.string.symbol_square_root);
        sCleanupMap.put("™", R.string.symbol_trademark);
        sCleanupMap.put("_", R.string.symbol_underscore);
        sCleanupMap.put("|", R.string.symbol_vertical_bar);
        sCleanupMap.put("\n", R.string.symbol_new_line);
    }

    /**
     * Cleans up text for speech. Converts symbols to their spoken equivalents.
     *
     * @param context The context used to resolve string resources.
     * @param text The text to clean up.
     * @return Cleaned up text.
     */
    public static CharSequence cleanUp(Context context, CharSequence text) {
        if (sCleanupMap == null) {
            populateCleanupMap();
        }

        if (text == null) {
            return null;
        }

        final String key = text.toString();
        final Object value = sCleanupMap.get(key);

        if (value instanceof String) {
            return (String) value;
        }

        if (value instanceof Integer) {
            final String localized = context.getString((Integer) value);
            sCleanupMap.put(key, localized);
            return localized;
        }

        if ((key.length() == 1) && Character.isUpperCase(key.charAt(0))) {
            final String localized = context.getString(R.string.template_capital_letter, text);
            sCleanupMap.put(key, localized);
            return localized;
        }

        return text;
    }

}
