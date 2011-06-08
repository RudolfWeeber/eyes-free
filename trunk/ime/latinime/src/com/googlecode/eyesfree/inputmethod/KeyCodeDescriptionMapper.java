/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.googlecode.eyesfree.inputmethod;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard.Key;
import android.text.TextUtils;

import com.googlecode.eyesfree.inputmethod.latin.KeyboardSwitcher;
import com.googlecode.eyesfree.inputmethod.latin.LatinKeyboardView;
import com.googlecode.eyesfree.inputmethod.latin.R;

import java.util.HashMap;

public class KeyCodeDescriptionMapper {
    private static KeyCodeDescriptionMapper sInstance;

    public static KeyCodeDescriptionMapper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new KeyCodeDescriptionMapper(context);
        }

        return sInstance;
    }

    // Map of key labels to spoken description resource IDs
    private final HashMap<CharSequence, Integer> mKeyLabelMap;

    // Map of key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mKeyCodeMap;

    // Map of shifted key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mShiftedKeyCodeMap;

    // Map of shift-locked key codes to spoken description resource IDs
    private final HashMap<Integer, Integer> mShiftLockedKeyCodeMap;

    private KeyCodeDescriptionMapper(Context context) {
        final Resources res = context.getResources();

        mKeyLabelMap = new HashMap<CharSequence, Integer>();
        mKeyCodeMap = new HashMap<Integer, Integer>();
        mShiftedKeyCodeMap = new HashMap<Integer, Integer>();
        mShiftLockedKeyCodeMap = new HashMap<Integer, Integer>();

        // Label substitutions for when the key label should not be spoken
        mKeyLabelMap.put(res.getText(R.string.label_alpha_key),
                R.string.spoken_description_to_alpha);
        mKeyLabelMap.put(res.getText(R.string.label_symbol_key),
                R.string.spoken_description_to_symbol);
        mKeyLabelMap.put(res.getText(R.string.label_phone_key),
                R.string.spoken_description_to_numeric);

        // Manual label substitutions for key labels with no string resource
        mKeyLabelMap.put(":-)", R.string.spoken_description_smiley);

        // Symbols that most TTS engines can't speak
        mKeyCodeMap.put((int) '.', R.string.spoken_description_period);
        mKeyCodeMap.put((int) ',', R.string.spoken_description_comma);
        mKeyCodeMap.put((int) '(', R.string.spoken_description_left_parenthesis);
        mKeyCodeMap.put((int) ')', R.string.spoken_description_right_parenthesis);
        mKeyCodeMap.put((int) ':', R.string.spoken_description_colon);
        mKeyCodeMap.put((int) ';', R.string.spoken_description_semicolon);
        mKeyCodeMap.put((int) '!', R.string.spoken_description_exclamation_mark);
        mKeyCodeMap.put((int) '?', R.string.spoken_description_question_mark);
        mKeyCodeMap.put((int) '\"', R.string.spoken_description_double_quote);
        mKeyCodeMap.put((int) '\'', R.string.spoken_description_single_quote);
        mKeyCodeMap.put((int) '*', R.string.spoken_description_star);
        mKeyCodeMap.put((int) '#', R.string.spoken_description_pound);
        mKeyCodeMap.put((int) ' ', R.string.spoken_description_space);

        // Non-ASCII symbols (must use escape codes!)
        mKeyCodeMap.put((int) '\u2022', R.string.spoken_description_dot);
        mKeyCodeMap.put((int) '\u221A', R.string.spoken_description_square_root);
        mKeyCodeMap.put((int) '\u03C0', R.string.spoken_description_pi);
        mKeyCodeMap.put((int) '\u0394', R.string.spoken_description_delta);
        mKeyCodeMap.put((int) '\u2122', R.string.spoken_description_trademark);
        mKeyCodeMap.put((int) '\u2105', R.string.spoken_description_care_of);
        mKeyCodeMap.put((int) '\u2026', R.string.spoken_description_ellipsis);
        mKeyCodeMap.put((int) '\u201E', R.string.spoken_description_low_double_quote);

        // Special non-character codes defined in Keyboard
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_DELETE, R.string.spoken_description_delete);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_RETURN, R.string.spoken_description_return);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_OPTIONS, R.string.spoken_description_settings);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_SHIFT, R.string.spoken_description_shift);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_VOICE, R.string.spoken_description_mic);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_TAB, R.string.spoken_description_tab);

        // Additional TalkBack-specific keys
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_BACK, R.string.spoken_description_back);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_HOME, R.string.spoken_description_home);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_SEARCH, R.string.spoken_description_search);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_MENU, R.string.spoken_description_menu);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_CALL, R.string.spoken_description_call);
        mKeyCodeMap.put(LatinKeyboardView.KEYCODE_ENDCALL, R.string.spoken_description_end_call);

        // Shifted versions of non-character codes defined in LatinKeyboardView
        mShiftedKeyCodeMap.put(LatinKeyboardView.KEYCODE_SHIFT, R.string.spoken_description_shift_shifted);

        // Shift-locked versions of non-character codes defined in LatinKeyboardView
        mShiftLockedKeyCodeMap.put(LatinKeyboardView.KEYCODE_SHIFT, R.string.spoken_description_caps_lock);
    }

    /**
     * Returns the description of the action performed by a specified key based
     * on the current keyboard state.
     * <p>
     * The order of precedence for key descriptions is:
     * <ol>
     * <li>Manually-defined based on the key label</li>
     * <li>Automatic or manually-defined based on the key code</li>
     * <li>Automatically based on the key label</li>
     * <li>{code null} for keys with no label or key code defined</li>
     * </p>
     *
     * @param res The package's resources.
     * @param switcher The keyboard switcher for the keyboard on which the key resides.
     * @param key The key from which to obtain a description.
     * @return a character sequence describing the action performed by pressing
     *         the key
     */
    public CharSequence getDescriptionForKey(Resources res, KeyboardSwitcher switcher, Key key) {
        if (!TextUtils.isEmpty(key.label)) {
            final String label = key.label.toString().trim();

            if (mKeyLabelMap.containsKey(label)) {
                return res.getText(mKeyLabelMap.get(label));
            } else if (label.length() == 1) {
                return getDescriptionForKeyCode(res, switcher, key);
            } else {
                return label;
            }
        } else if (key.codes != null) {
            return getDescriptionForKeyCode(res, switcher, key);
        }

        return null;
    }

    /**
     * Returns a character sequence describing what will happen when the
     * specified key is pressed based on its key code.
     * <p>
     * The order of precedence for key code descriptions is:
     * <ol>
     * <li>Manually-defined shift-locked description</li>
     * <li>Manually-defined shifted description</li>
     * <li>Manually-defined normal description</li>
     * <li>Automatic based on the character represented by the key code</li>
     * <li>Fall-back for undefined or control characters</li>
     * </ol>
     * </p>
     *
     * @param res The package's resources.
     * @param switcher The keyboard switcher for the keyboard on which the key resides.
     * @param key The key from which to obtain a description.
     * @return a character sequence describing the action performed by pressing
     *         the key
     */
    private CharSequence getDescriptionForKeyCode(Resources res, KeyboardSwitcher switcher, Key key) {
        final int code = key.codes[0];

        if (switcher.isShiftLocked() && mShiftLockedKeyCodeMap.containsKey(code)) {
            return res.getText(mShiftLockedKeyCodeMap.get(code));
        } else if (switcher.isShiftedOrShiftLocked() && mShiftedKeyCodeMap.containsKey(code)) {
            return res.getText(mShiftedKeyCodeMap.get(code));
        } else if (mKeyCodeMap.containsKey(code)) {
            return res.getText(mKeyCodeMap.get(code));
        } else if (Character.isDefined(code) && !Character.isISOControl(code)) {
            return Character.toString((char) code);
        } else {
            return res.getString(R.string.spoken_description_unknown, code);
        }
    }
}
