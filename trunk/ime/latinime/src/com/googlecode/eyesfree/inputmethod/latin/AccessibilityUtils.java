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
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.TreeMap;

/**
 * Utility functions for accessibility support.
 */
public class AccessibilityUtils {
    /**
     * This is an arbitrary parcelable that's sent with an AccessibilityEvent to
     * prevent elimination of events with identical text.
     */
    private static final Parcelable JUNK_PARCELABLE = new Rect();

    private TreeMap<Integer, CharSequence> mDescriptions;

    private final Context mContext;

    private final AccessibilityManager mAccessibilityManager;

    public AccessibilityUtils(Context context) {
        mContext = context;
        mAccessibilityManager = (AccessibilityManager) context.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
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
     * @param primaryCode The primary code of the released key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onRelease(int primaryCode, KeyboardSwitcher switcher) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }
        int resId = -1;
        switch (primaryCode) {
            case Keyboard.KEYCODE_SHIFT: {
                if (switcher.isShiftedOrShiftLocked()) {
                    if (switcher.isShiftLocked()) {
                        resId = R.string.description_shift_locked;
                    } else {
                        resId = R.string.description_shift_on;
                    }
                } else {
                    resId = R.string.description_shift_off;
                }
                break;
            }
            case Keyboard.KEYCODE_MODE_CHANGE: {
                if (switcher.isAlphabetMode()) {
                    resId = R.string.description_symbols_off;
                } else {
                    resId = R.string.description_symbols_on;
                }
                break;
            }
            case LatinKeyboardView.KEYCODE_BACK:
                resId = R.string.description_back_key;
                break;
            case LatinKeyboardView.KEYCODE_HOME:
                resId = R.string.description_home_key;
                break;
            case LatinKeyboardView.KEYCODE_SEARCH:
                resId = R.string.description_search_key;
                break;
            case LatinKeyboardView.KEYCODE_MENU:
                resId = R.string.description_menu_key;
                break;
            case LatinKeyboardView.KEYCODE_CALL:
                resId = R.string.description_call_key;
                break;
            case LatinKeyboardView.KEYCODE_ENDCALL:
                resId = R.string.description_back_key;
                break;
        }
        if (resId >= 0) {
            speakDescription(mContext.getResources().getText(resId));
        }
    }

    /**
     * @param key
     * @param mKeyboard
     */
    public void speakKey(Key key, Keyboard mKeyboard) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }
        CharSequence description = describeKey(key.codes[0]);
        if (description == null && key.label != null) {
            description = key.label;
        } else if (description == null && Character.isDefined(key.codes[0])) {
            description = Character.toString((char) key.codes[0]);
        }
        if (description != null) {
            speakDescription(description);
        }
    }

    /**
     * Speak key description for accessibility. If a key has an explicit
     * description defined in keycodes.xml, that will be used. Otherwise, if the
     * key is a Unicode character, then its character will be used.
     *
     * @param primaryCode The primary code of the pressed key.
     * @param switcher The input method's {@link KeyboardSwitcher}.
     */
    public void onPress(int primaryCode, KeyboardSwitcher switcher) {
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }
        // TODO Use the current keyboard state to read "Switch to symbols"
        // instead of just "Symbols" (and similar for shift key).
        CharSequence description = describeKey(primaryCode);
        if (description == null && Character.isDefined((char) primaryCode)) {
            description = Character.toString((char) primaryCode);
        }
        if (description != null) {
            speakDescription(description);
        }
    }

    /**
     * Returns a text description for a given key code. If the key does not have
     * an explicit description, returns <code>null</code>.
     *
     * @param keyCode An integer key code.
     * @return A {@link CharSequence} describing the key or <code>null</code> if
     *         no description is available.
     */
    private CharSequence describeKey(int keyCode) {
        // If not loaded yet, load key descriptions from XML file.
        if (mDescriptions == null) {
            mDescriptions = loadDescriptions();
        }

        return mDescriptions.get(keyCode);
    }

    /**
     * Loads key descriptions from resources.
     */
    private TreeMap<Integer, CharSequence> loadDescriptions() {
        TreeMap<Integer, CharSequence> descriptions = new TreeMap<Integer, CharSequence>();
        TypedArray array = mContext.getResources().obtainTypedArray(R.array.key_descriptions);

        // Key descriptions are stored as a key code followed by a string.
        for (int i = 0; i < array.length() - 1; i += 2) {
            int code = array.getInteger(i, 0);
            CharSequence desc = array.getText(i + 1);
            descriptions.put(code, desc);
        }

        array.recycle();

        return descriptions;
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

    public static boolean isAccessibilityEnabled(Context context) {
        final int accessibilityEnabled = Settings.Secure.getInt(
                context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);

        return accessibilityEnabled == 1;
    }

    public static boolean isInputMethodEnabled(Context context, Class<?> imeClass) {
        final String targetImePackage = imeClass.getPackage().getName();
        final String targetImeClass = imeClass.getSimpleName();
        final String enabledImeIds = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);

        return enabledImeIds != null && enabledImeIds.contains(targetImePackage)
                && enabledImeIds.contains(targetImeClass);
    }

    public static boolean isInputMethodDefault(Context context, Class<?> imeClass) {
        final String targetImePackage = imeClass.getPackage().getName();
        final String targetImeClass = imeClass.getSimpleName();
        final String defaultImeId = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

        return defaultImeId != null && defaultImeId.contains(targetImePackage)
                && defaultImeId.contains(targetImeClass);
    }
}
