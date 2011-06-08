/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.googlecode.eyesfree.inputmethod.latin;

import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.view.InflateException;

import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

public class KeyboardSwitcher implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final int MODE_NONE = 0;
    public static final int MODE_TEXT = 1;
    public static final int MODE_SYMBOLS = 2;
    public static final int MODE_PHONE = 3;
    public static final int MODE_URL = 4;
    public static final int MODE_EMAIL = 5;
    public static final int MODE_IM = 6;
    public static final int MODE_WEB = 7;
    public static final int MODE_DPAD = 8;
    public static final int MODE_HIDDEN = 9;

    // Main keyboard layouts with the settings key
    public static final int KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY =
            R.id.mode_normal_with_settings_key;
    public static final int KEYBOARDMODE_URL_WITH_SETTINGS_KEY =
            R.id.mode_url_with_settings_key;
    public static final int KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY =
            R.id.mode_email_with_settings_key;
    public static final int KEYBOARDMODE_IM_WITH_SETTINGS_KEY =
            R.id.mode_im_with_settings_key;
    public static final int KEYBOARDMODE_WEB_WITH_SETTINGS_KEY =
            R.id.mode_webentry_with_settings_key;

    // Symbols keyboard layout with the settings key
    public static final int KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY =
            R.id.mode_symbols_with_settings_key;

    public static final String DEFAULT_LAYOUT_ID = "0";
    public static final String PREF_KEYBOARD_LAYOUT = "pref_keyboard_layout_20100902";
    private static final int[] THEMES = new int [] { R.layout.input_gingerbread};

    // Ids for each characters' color in the keyboard
    private static final int CHAR_THEME_COLOR_WHITE = 0;
    private static final int CHAR_THEME_COLOR_BLACK = 1;

    // Resource ids for non-themed keyboards
    private static final int KBD_DPAD = R.xml.kbd_dpad;
    private static final int KBD_DPAD_KEYS = R.xml.kbd_dpad_keys;
    private static final int KBD_HIDDEN = R.xml.kbd_hidden;

    // Tables which contains resource ids for each character theme color
    private static final int[] KBD_PHONE = new int[] {R.xml.kbd_phone, R.xml.kbd_phone_black};
    private static final int[] KBD_PHONE_SYMBOLS = new int[] {
        R.xml.kbd_phone_symbols, R.xml.kbd_phone_symbols_black};
    private static final int[] KBD_SYMBOLS = new int[] {
        R.xml.kbd_symbols, R.xml.kbd_symbols_black};
    private static final int[] KBD_SYMBOLS_SHIFT = new int[] {
        R.xml.kbd_symbols_shift, R.xml.kbd_symbols_shift_black};
    private static final int[] KBD_QWERTY = new int[] {R.xml.kbd_qwerty, R.xml.kbd_qwerty_black};

    private static final int SYMBOLS_MODE_STATE_NONE = 0;
    private static final int SYMBOLS_MODE_STATE_BEGIN = 1;
    private static final int SYMBOLS_MODE_STATE_SYMBOL = 2;

    private LatinKeyboardView mInputView;
    private static final int[] ALPHABET_MODES = {
        KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_URL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY,
        KEYBOARDMODE_IM_WITH_SETTINGS_KEY,
        KEYBOARDMODE_WEB_WITH_SETTINGS_KEY };

    private final LatinIME mInputMethodService;

    private KeyboardId mSymbolsId;
    private KeyboardId mSymbolsShiftedId;

    private KeyboardId mCurrentId;
    private final HashMap<KeyboardId, SoftReference<LatinKeyboard>> mKeyboards;

    private int mMode = MODE_NONE; /** One of the MODE_XXX values */
    private int mImeOptions;
    private boolean mIsSymbols;
    /** mIsAutoCompletionActive indicates that auto completed word will be input instead of
     * what user actually typed. */
    private boolean mIsAutoCompletionActive;
    private boolean mHasVoice;
    private boolean mVoiceOnPrimary;
    private boolean mPreferSymbols;
    private int mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;

    // Indicates whether or not we show directional pad keys
    private boolean mHasDpadKeys;

    private int mLastDisplayWidth;
    private LanguageSwitcher mLanguageSwitcher;
    private Locale mInputLocale;

    private int mLayoutId;

    public KeyboardSwitcher(LatinIME ims) {
        mInputMethodService = ims;

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ims);
        mLayoutId = Integer.valueOf(prefs.getString(PREF_KEYBOARD_LAYOUT, DEFAULT_LAYOUT_ID));
        updateDpadKeysState(prefs);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mKeyboards = new HashMap<KeyboardId, SoftReference<LatinKeyboard>>();
        mSymbolsId = makeSymbolsId(false);
        mSymbolsShiftedId = makeSymbolsShiftedId(false);
    }

    /**
     * Sets the input locale, when there are multiple locales for input.
     * If no locale switching is required, then the locale should be set to null.
     * button.
     */
    public void setLanguageSwitcher(LanguageSwitcher languageSwitcher) {
        mLanguageSwitcher = languageSwitcher;
        mInputLocale = mLanguageSwitcher.getInputLocale();
    }

    private KeyboardId makeSymbolsId(boolean hasVoice) {
        return new KeyboardId(KBD_SYMBOLS[getCharColorId()],
                KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY,
                false, hasVoice);
    }

    private KeyboardId makeSymbolsShiftedId(boolean hasVoice) {
        return new KeyboardId(KBD_SYMBOLS_SHIFT[getCharColorId()],
                KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY,
                false, hasVoice);
    }

    public void makeKeyboards(boolean forceCreate) {
        mSymbolsId = makeSymbolsId(mHasVoice && !mVoiceOnPrimary);
        mSymbolsShiftedId = makeSymbolsShiftedId(mHasVoice && !mVoiceOnPrimary);

        if (forceCreate) mKeyboards.clear();
        // Configuration change is coming after the keyboard gets recreated. So don't rely on that.
        // If keyboards have already been made, check if we have a screen width change and
        // create the keyboard layouts again at the correct orientation
        int displayWidth = mInputMethodService.getMaxWidth();
        if (displayWidth == mLastDisplayWidth) return;
        mLastDisplayWidth = displayWidth;
        if (!forceCreate) mKeyboards.clear();
    }

    /**
     * Represents the parameters necessary to construct a new LatinKeyboard,
     * which also serve as a unique identifier for each keyboard type.
     */
    private static class KeyboardId {
        // TODO: should have locale and portrait/landscape orientation?
        public final int mXml;
        public final int mKeyboardMode; /** A KEYBOARDMODE_XXX value */
        public final boolean mEnableShiftLock;
        public final boolean mHasVoice;

        private final int mHashCode;

        public KeyboardId(int xml, int mode, boolean enableShiftLock, boolean hasVoice) {
            this.mXml = xml;
            this.mKeyboardMode = mode;
            this.mEnableShiftLock = enableShiftLock;
            this.mHasVoice = hasVoice;

            this.mHashCode = Arrays.hashCode(new Object[] {
               xml, mode, enableShiftLock, hasVoice
            });
        }

        public KeyboardId(int xml, boolean hasVoice) {
            this(xml, 0, false, hasVoice);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof KeyboardId && equals((KeyboardId) other);
        }

        private boolean equals(KeyboardId other) {
            return other.mXml == this.mXml
                && other.mKeyboardMode == this.mKeyboardMode
                && other.mEnableShiftLock == this.mEnableShiftLock
                && other.mHasVoice == this.mHasVoice;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }
    }

    public void setVoiceMode(boolean enableVoice, boolean voiceOnPrimary) {
        if (enableVoice != mHasVoice || voiceOnPrimary != mVoiceOnPrimary) {
            mKeyboards.clear();
        }
        mHasVoice = enableVoice;
        mVoiceOnPrimary = voiceOnPrimary;
        setKeyboardMode(mMode, mImeOptions, mHasVoice, mIsSymbols);
    }

    private boolean hasVoiceButton(boolean isSymbols) {
        return mHasVoice && (isSymbols != mVoiceOnPrimary);
    }

    public void setKeyboardMode(int mode) {
        setKeyboardMode(mode, mImeOptions, mHasVoice);
    }

    public void setKeyboardMode(int mode, int imeOptions, boolean enableVoice) {
        mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        mPreferSymbols = mode == MODE_SYMBOLS;
        if (mode == MODE_SYMBOLS) {
            mode = MODE_TEXT;
        }
        try {
            setKeyboardMode(mode, imeOptions, enableVoice, mPreferSymbols);
        } catch (RuntimeException e) {
            LatinImeLogger.logOnException(mode + "," + imeOptions + "," + mPreferSymbols, e);
        }
    }

    private void setKeyboardMode(int mode, int imeOptions, boolean enableVoice, boolean isSymbols) {
        if (mInputView == null) return;

        mMode = mode;
        mImeOptions = imeOptions;
        if (enableVoice != mHasVoice) {
            // TODO clean up this unnecessary recursive call.
            setVoiceMode(enableVoice, mVoiceOnPrimary);
        }
        mIsSymbols = isSymbols;

        mInputView.setPreviewEnabled(mInputMethodService.getPopupOn());
        KeyboardId id = getKeyboardId(mode, imeOptions, isSymbols);
        LatinKeyboard keyboard = null;
        keyboard = getKeyboard(id);

        if (mode == MODE_PHONE) {
            mInputView.setPhoneKeyboard(keyboard);
        }

        if (mode == MODE_DPAD) {
            mInputView.setLongPressEnabled(true);
        } else {
            mInputView.setLongPressEnabled(false);
        }

        mCurrentId = id;
        mInputView.setKeyboard(keyboard);
        keyboard.setShifted(false);
        keyboard.setShiftLocked(keyboard.isShiftLocked());
        keyboard.setImeOptions(mInputMethodService.getResources(), mMode, imeOptions);
        keyboard.setColorOfSymbolIcons(mIsAutoCompletionActive, isBlackSym());
    }

    private LatinKeyboard getKeyboard(KeyboardId id) {
        SoftReference<LatinKeyboard> ref = mKeyboards.get(id);
        LatinKeyboard keyboard = (ref == null) ? null : ref.get();
        if (keyboard == null) {
            Resources orig = mInputMethodService.getResources();
            Configuration conf = orig.getConfiguration();
            Locale saveLocale = conf.locale;
            conf.locale = mInputLocale;
            orig.updateConfiguration(conf, null);
            keyboard = new LatinKeyboard(mInputMethodService, id.mXml, id.mKeyboardMode);
            keyboard.setVoiceMode(hasVoiceButton(id.mXml == R.xml.kbd_symbols
                    || id.mXml == R.xml.kbd_symbols_black), mHasVoice);

            if (id.mEnableShiftLock) {
                keyboard.enableShiftLock();
            }
            mKeyboards.put(id, new SoftReference<LatinKeyboard>(keyboard));

            conf.locale = saveLocale;
            orig.updateConfiguration(conf, null);
        }
        return keyboard;
    }

    private KeyboardId getKeyboardId(int mode, int imeOptions, boolean isSymbols) {
        boolean hasVoice = hasVoiceButton(isSymbols);
        int charColorId = getCharColorId();
        // TODO: generalize for any KeyboardId
        int keyboardRowsResId = KBD_QWERTY[charColorId];
        if (isSymbols) {
            if (mode == MODE_PHONE) {
                return new KeyboardId(KBD_PHONE_SYMBOLS[charColorId], hasVoice);
            } else {
                return new KeyboardId(KBD_SYMBOLS[charColorId],
                        KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY,
                        false, hasVoice);
            }
        }
        switch (mode) {
            case MODE_NONE:
                LatinImeLogger.logOnWarning(
                        "getKeyboardId:" + mode + "," + imeOptions + "," + isSymbols);
                // $FALL-THROUGH$
            case MODE_TEXT:
                return new KeyboardId(keyboardRowsResId,
                        KEYBOARDMODE_NORMAL_WITH_SETTINGS_KEY,
                        true, hasVoice);
            case MODE_SYMBOLS:
                return new KeyboardId(KBD_SYMBOLS[charColorId],
                        KEYBOARDMODE_SYMBOLS_WITH_SETTINGS_KEY,
                        false, hasVoice);
            case MODE_PHONE:
                return new KeyboardId(KBD_PHONE[charColorId], hasVoice);
            case MODE_URL:
                return new KeyboardId(keyboardRowsResId,
                        KEYBOARDMODE_URL_WITH_SETTINGS_KEY, true, hasVoice);
            case MODE_EMAIL:
                return new KeyboardId(keyboardRowsResId,
                        KEYBOARDMODE_EMAIL_WITH_SETTINGS_KEY, true, hasVoice);
            case MODE_IM:
                return new KeyboardId(keyboardRowsResId,
                        KEYBOARDMODE_IM_WITH_SETTINGS_KEY, true, hasVoice);
            case MODE_WEB:
                return new KeyboardId(keyboardRowsResId,
                        KEYBOARDMODE_WEB_WITH_SETTINGS_KEY, true, hasVoice);
            case MODE_DPAD:
                return new KeyboardId(mHasDpadKeys ? KBD_DPAD_KEYS : KBD_DPAD, hasVoice);
            case MODE_HIDDEN:
                return new KeyboardId(KBD_HIDDEN, hasVoice);
        }
        return null;
    }

    public int getKeyboardMode() {
        return mMode;
    }

    public boolean isDpadKeysEnabled() {
        return mHasDpadKeys;
    }

    public boolean isAlphabetMode() {
        if (mCurrentId == null) {
            return false;
        }
        int currentMode = mCurrentId.mKeyboardMode;
        for (Integer mode : ALPHABET_MODES) {
            if (currentMode == mode) {
                return true;
            }
        }
        return false;
    }

    public void setShifted(boolean shifted) {
        if (mInputView != null) {
            mInputView.setShifted(shifted);
        }
    }

    public void setShiftLocked(boolean shiftLocked) {
        if (mInputView != null) {
            mInputView.setShiftLocked(shiftLocked);
        }
    }

    public boolean isShiftedOrShiftLocked() {
        if (mInputView != null) {
            return mInputView.isShifted();
        }
        return false;
    }

    public boolean isShiftLocked() {
        if (mInputView != null) {
            return mInputView.isShiftLocked();
        }
        return false;
    }

    public void toggleShift() {
        if (isAlphabetMode())
            return;
        if (mCurrentId.equals(mSymbolsId) || !mCurrentId.equals(mSymbolsShiftedId)) {
            LatinKeyboard symbolsShiftedKeyboard = getKeyboard(mSymbolsShiftedId);
            mCurrentId = mSymbolsShiftedId;
            mInputView.setKeyboard(symbolsShiftedKeyboard);
            // Symbol shifted keyboard has an ALT key that has a caps lock style indicator. To
            // enable the indicator, we need to call enableShiftLock() and setShiftLocked(true).
            // Thus we can keep the ALT key's Key.on value true while LatinKey.onRelease() is
            // called.
            symbolsShiftedKeyboard.enableShiftLock();
            symbolsShiftedKeyboard.setShiftLocked(true);
            symbolsShiftedKeyboard.setImeOptions(mInputMethodService.getResources(),
                    mMode, mImeOptions);
        } else {
            LatinKeyboard symbolsKeyboard = getKeyboard(mSymbolsId);
            mCurrentId = mSymbolsId;
            mInputView.setKeyboard(symbolsKeyboard);
            // Symbol keyboard has an ALT key that has a caps lock style indicator. To disable the
            // indicator, we need to call enableShiftLock() and setShiftLocked(false).
            symbolsKeyboard.enableShiftLock();
            symbolsKeyboard.setShifted(false);
            symbolsKeyboard.setImeOptions(mInputMethodService.getResources(), mMode, mImeOptions);
        }
    }

    public void toggleSymbols() {
        setKeyboardMode(mMode, mImeOptions, mHasVoice, !mIsSymbols);
        if (mIsSymbols && !mPreferSymbols) {
            mSymbolsModeState = SYMBOLS_MODE_STATE_BEGIN;
        } else {
            mSymbolsModeState = SYMBOLS_MODE_STATE_NONE;
        }
    }

    public boolean isAccessibilityEnabled() {
        return mInputView != null && mInputView.isAccessibilityEnabled();
    }

    public boolean hasDistinctMultitouch() {
        return mInputView != null && mInputView.hasDistinctMultitouch();
    }

    /**
     * Updates state machine to figure out when to automatically switch back to alpha mode.
     * Returns true if the keyboard needs to switch back
     */
    public boolean onKey(int key) {
        // Switch back to alpha mode if user types one or more non-space/enter characters
        // followed by a space/enter
        switch (mSymbolsModeState) {
            case SYMBOLS_MODE_STATE_BEGIN:
                if (key != LatinIME.KEYCODE_SPACE && key != LatinIME.KEYCODE_ENTER && key > 0) {
                    mSymbolsModeState = SYMBOLS_MODE_STATE_SYMBOL;
                }
                break;
            case SYMBOLS_MODE_STATE_SYMBOL:
                if (key == LatinIME.KEYCODE_ENTER || key == LatinIME.KEYCODE_SPACE) return true;
                break;
        }
        return false;
    }

    public LatinKeyboardView getInputView() {
        return mInputView;
    }

    public void recreateInputView() {
        changeLatinKeyboardView(mLayoutId, true);
    }

    private void changeLatinKeyboardView(int newLayout, boolean forceReset) {
        if (mLayoutId != newLayout || mInputView == null || forceReset) {
            if (mInputView != null) {
                mInputView.closing();
            }
            if (THEMES.length <= newLayout) {
                newLayout = Integer.valueOf(DEFAULT_LAYOUT_ID);
            }

            LatinIMEUtil.GCUtils.getInstance().reset();
            boolean tryGC = true;
            for (int i = 0; i < LatinIMEUtil.GCUtils.GC_TRY_LOOP_MAX && tryGC; ++i) {
                try {
                    mInputView = (LatinKeyboardView) mInputMethodService.getLayoutInflater(
                            ).inflate(THEMES[newLayout], null);
                    tryGC = false;
                } catch (OutOfMemoryError e) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + newLayout, e);
                } catch (InflateException e) {
                    tryGC = LatinIMEUtil.GCUtils.getInstance().tryGCOrWait(
                            mLayoutId + "," + newLayout, e);
                }
            }
            mInputView.setOnKeyboardActionListener(mInputMethodService.mKeyboardActionListener);
            mLayoutId = newLayout;
        }
        mInputMethodService.mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (mInputMethodService) {
                    if (mInputView != null && mInputView.getParent() == null) {
                        mInputMethodService.setInputView(mInputView);
                    }
                }
                mInputMethodService.updateInputViewShown();
            }});
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PREF_KEYBOARD_LAYOUT.equals(key)) {
            changeLatinKeyboardView(
                    Integer.valueOf(sharedPreferences.getString(key, DEFAULT_LAYOUT_ID)), false);
        } else if (LatinIMESettings.PREF_DPAD_KEYS.equals(key)) {
            updateDpadKeysState(sharedPreferences);
            recreateInputView();
        }
    }

    public boolean isBlackSym () {
        if (mInputView != null && mInputView.getSymbolColorScheme() == 1) {
            return true;
        }
        return false;
    }

    private int getCharColorId () {
        if (isBlackSym()) {
            return CHAR_THEME_COLOR_BLACK;
        } else {
            return CHAR_THEME_COLOR_WHITE;
        }
    }

    public void onAutoCompletionStateChanged(boolean isAutoCompletion) {
        if (isAutoCompletion != mIsAutoCompletionActive) {
            LatinKeyboardView keyboardView = getInputView();
            mIsAutoCompletionActive = isAutoCompletion;
            keyboardView.invalidateKey(((LatinKeyboard) keyboardView.getKeyboard())
                    .onAutoCompletionStateChanged(isAutoCompletion));
        }
    }

    private void updateDpadKeysState(SharedPreferences prefs) {
        Resources resources = mInputMethodService.getResources();
        mHasDpadKeys = prefs.getBoolean(LatinIMESettings.PREF_DPAD_KEYS,
                resources.getBoolean(R.bool.default_dpad_keys));
    }
}
