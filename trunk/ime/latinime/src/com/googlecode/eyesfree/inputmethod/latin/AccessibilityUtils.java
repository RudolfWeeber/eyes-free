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

package com.googlecode.eyesfree.inputmethod.latin;

import android.content.Context;
import android.content.res.Resources;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import com.googlecode.eyesfree.inputmethod.KeyCodeDescriptionMapper;

/**
 * Utility functions for accessibility support.
 */
public class AccessibilityUtils {
    private final Context mContext;
    private final AccessibilityManager mAccessibilityManager;
    private final KeyCodeDescriptionMapper mKeyCodeMapper;

    public AccessibilityUtils(Context context) {
        mContext = context;
        mAccessibilityManager = (AccessibilityManager) context
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        mKeyCodeMapper = KeyCodeDescriptionMapper.getInstance(context);
    }

    /**
     * Returns true if accessibility is enabled.
     *
     * @return true is accessibility is enabled.
     */
    public boolean isAccessibilityEnabled() {
        return mAccessibilityManager.isEnabled();
    }

    /**
     * Speaks a key's action after it has been released. Does not speak letter
     * keys since typed keys are already spoken aloud by TalkBack.
     *
     * @param key The primary code of the released key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onRelease(Key key, KeyboardSwitcher switcher) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }
        final int primaryCode = key.codes[0];
        int resId = -1;
        switch (primaryCode) {
            case Keyboard.KEYCODE_SHIFT: {
                if (switcher.isAlphabetMode()) {
                    if (switcher.isShiftedOrShiftLocked()) {
                        if (switcher.isShiftLocked()) {
                            resId = R.string.spoken_description_shift_locked;
                        } else {
                            resId = R.string.spoken_description_shift_on;
                        }
                    } else {
                        resId = R.string.spoken_description_shift_off;
                    }
                } else {
                    if (switcher.isShiftedOrShiftLocked()) {
                        resId = R.string.spoken_description_alt_on;
                    } else {
                        resId = R.string.spoken_description_alt_off;
                    }
                }
                break;
            }
            case Keyboard.KEYCODE_MODE_CHANGE: {
                if (switcher.isAlphabetMode()) {
                    resId = R.string.spoken_description_symbols_off;
                } else {
                    resId = R.string.spoken_description_symbols_on;
                }
                break;
            }
            case LatinKeyboardView.KEYCODE_BACK:
                resId = R.string.spoken_description_back;
                break;
            case LatinKeyboardView.KEYCODE_HOME:
                resId = R.string.spoken_description_home;
                break;
            case LatinKeyboardView.KEYCODE_SEARCH:
                resId = R.string.spoken_description_search;
                break;
            case LatinKeyboardView.KEYCODE_MENU:
                resId = R.string.spoken_description_menu;
                break;
            case LatinKeyboardView.KEYCODE_CALL:
                resId = R.string.spoken_description_call;
                break;
            case LatinKeyboardView.KEYCODE_ENDCALL:
                resId = R.string.spoken_description_end_call;
                break;
        }

        if (resId >= 0) {
            speakDescription(mContext.getResources().getText(resId));
        }
    }

    /**
     * Speak key description for accessibility. If a key has an explicit
     * description defined in keycodes.xml, that will be used. Otherwise, if the
     * key is a Unicode character, then its character will be used.
     *
     * @param res The resources for the current context.
     * @param key The primary code of the pressed key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onPress(Resources res, KeyboardSwitcher switcher, Key key) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }

        final CharSequence description = mKeyCodeMapper.getDescriptionForKey(res, switcher, key);

        if (description != null) {
            speakDescription(description);
        }
    }

    /**
     * Sends a character sequence to be read aloud.
     *
     * @param description The {@link CharSequence} to be read aloud.
     */
    public void speakDescription(CharSequence description) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }

        // TODO Contact Loquendo so we can remove this workaround.
        if (Character.isLetterOrDigit(description.charAt(0))) {
            description = description + ".";
        }

        // TODO We need to add an AccessibilityEvent type for IMEs.
        AccessibilityEvent event = AccessibilityEvent
                .obtain(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
        event.setPackageName(mContext.getPackageName());
        event.setClassName(getClass().getName());
        event.setEventTime(SystemClock.uptimeMillis());
        event.setBeforeText("");
        event.setAddedCount(description.length());
        event.setRemovedCount(0);
        event.setFromIndex(0);
        event.getText().add(description);

        mAccessibilityManager.sendAccessibilityEvent(event);
    }

    public static boolean isAccessibilityEnabled(Context context) {
        final int accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0);

        return accessibilityEnabled == 1;
    }

    public static boolean isInputMethodEnabled(Context context, Class<?> imeClass) {
        final String targetImePackage = imeClass.getPackage().getName();
        final String targetImeClass = imeClass.getSimpleName();
        final String enabledImeIds = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_INPUT_METHODS);

        return enabledImeIds != null && enabledImeIds.contains(targetImePackage)
                && enabledImeIds.contains(targetImeClass);
    }

    public static boolean isInputMethodDefault(Context context, Class<?> imeClass) {
        final String targetImePackage = imeClass.getPackage().getName();
        final String targetImeClass = imeClass.getSimpleName();
        final String defaultImeId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.DEFAULT_INPUT_METHOD);

        return defaultImeId != null && defaultImeId.contains(targetImePackage)
                && defaultImeId.contains(targetImeClass);
    }
}
