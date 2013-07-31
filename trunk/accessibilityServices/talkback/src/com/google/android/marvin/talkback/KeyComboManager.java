/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.os.Build;
import android.view.KeyEvent;

import com.google.android.marvin.talkback.TalkBackService.KeyEventListener;

import java.util.LinkedList;

/**
 * Manages state related to detecting key combinations.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class KeyComboManager implements KeyEventListener {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;

    /** List of possible key combinations. */
    private final LinkedList<KeyCombo> mKeyCombos = new LinkedList<KeyCombo>();

    /** The number of keys currently being pressed. */
    private int mKeyCount;

    /** Whether the user performed a combo during the current interaction. */
    private boolean mPerformedCombo;

    /** Whether the user may be performing a combo and we should intercept keys. */
    private boolean mHasPartialMatch;

    /** The listener that receives callbacks when a combo is recognized. */
    private KeyComboListener mListener;

    /**
     * Loads default key combinations.
     */
    public void loadDefaultCombos() {
        // TODO(alanv): Load key combos from a preference or database.
        addCombo(R.id.key_combo_suspend_resume, (KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON),
                KeyEvent.KEYCODE_Z);
    }

    /**
     * Sets the listener that receives callbacks when the user performs key
     * combinations.
     *
     * @param listener The listener that receives callbacks.
     */
    public void setListener(KeyComboListener listener) {
        mListener = listener;
    }

    /**
     * Adds a key combination to the list of possible combinations.
     *
     * @param id The identifier for the key combination. Must be unique.
     * @param modifiers A bit mask of required modifiers keys. May be any
     *            combination of {@link KeyEvent}.META_ flags.
     * @param keyCode (optional) The non-modifier key used in the key combo.
     */
    public void addCombo(int id, int modifiers, int keyCode) {
        mKeyCombos.add(new KeyCombo(id, modifiers, keyCode));
    }

    /**
     * Handles incoming key events. May intercept keys if the user seems to be
     * performing a key combo.
     *
     * @param event The key event.
     * @return {@code true} if the key was intercepted.
     */
    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (mListener == null) {
            return false;
        }

        final int keyCode = event.getKeyCode();

        switch (event.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return onKeyDown(keyCode, event);
            case KeyEvent.ACTION_MULTIPLE:
                return mHasPartialMatch;
            case KeyEvent.ACTION_UP:
                return onKeyUp(keyCode, event);
        }

        return false;
    }

    private boolean onKeyDown(int keyCode, KeyEvent event) {
        mKeyCount++;

        // Only handle keys with modifiers.
        if (event.getModifiers() == 0) {
            return false;
        }

        // If the current set of keys is a partial combo, consume the event.
        mHasPartialMatch = false;

        for (KeyCombo keyCombo : mKeyCombos) {
            final int match = keyCombo.matches(event);
            if ((match == KeyCombo.EXACT_MATCH) && mListener.onComboPerformed(keyCombo.mId)) {
                mPerformedCombo = true;
                return true;
            }

            if (match == KeyCombo.PARTIAL_MATCH) {
                mHasPartialMatch = true;
            }
        }

        return mHasPartialMatch;
    }

    private boolean onKeyUp(int keyCode, KeyEvent event) {
        final boolean handled = mPerformedCombo;

        mKeyCount--;

        if (mKeyCount == 0) {
            // The interaction is over, reset the state.
            mPerformedCombo = false;
            mHasPartialMatch = false;
        }

        return handled;
    }

    public interface KeyComboListener {
        public boolean onComboPerformed(int id);
    }

    private static class KeyCombo {
        public static final int NO_MATCH = -1;
        public static final int PARTIAL_MATCH = 1;
        public static final int EXACT_MATCH = 2;

        private static final int META_SCREEN = (KeyEvent.META_ALT_ON | KeyEvent.META_CAPS_LOCK_ON
                | KeyEvent.META_CTRL_ON | KeyEvent.META_FUNCTION_ON | KeyEvent.META_META_ON
                | KeyEvent.META_NUM_LOCK_ON | KeyEvent.META_SCROLL_LOCK_ON | KeyEvent.META_SHIFT_ON
                | KeyEvent.META_SYM_ON);

        private final int mId;
        private final int mMetaMask;
        private final int mKeyCode;

        public KeyCombo(int id, int modifiers, int keyCode) {
            mId = id;
            mMetaMask = modifiers;
            mKeyCode = keyCode;
        }

        public int matches(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            final int metaState = (event.getMetaState() & META_SCREEN);

            // Handle exact matches first.
            if ((metaState == mMetaMask) && ((keyCode < 0) || (event.getKeyCode() == keyCode))) {
                return EXACT_MATCH;
            }

            // Otherwise, all modifiers must be down.
            if (KeyEvent.isModifierKey(mKeyCode) && ((mMetaMask & metaState) != 0)) {
                // Partial match.
                return PARTIAL_MATCH;
            }

            // No match.
            return NO_MATCH;
        }
    }
}