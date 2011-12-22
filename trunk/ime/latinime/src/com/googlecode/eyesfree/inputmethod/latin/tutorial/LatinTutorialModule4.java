/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import com.google.android.marvin.aime.TextNavigation;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import com.googlecode.eyesfree.inputmethod.latin.LatinIME;
import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinTutorialModule4 extends TutorialModule implements View.OnClickListener,
        View.OnFocusChangeListener, SelectionEditText.SelectionListener,
        LatinIMETutorial.KeyboardBroadcastListener {
    private static final int FLAG_STARTED = 0;
    private static final int FLAG_CHAR_READY = 1;
    private static final int FLAG_CHAR_DONE = 2;
    private static final int FLAG_WORD_MODE = 3;
    private static final int FLAG_WORD_DONE = 4;
    private static final int FLAG_CHAR_MODE = 5;

    // Use counts for triggering successful "use" of a feature.
    private static final int DPAD_USE_TRIGGER = 3;
    private static final int ALT_DPAD_USE_TRIGGER = 2;

    /**
     * Counter for character-level navigation events in Text Navigation
     * tutorial.
     */
    private int mDpadUseCount = 0;

    /** Counter for word-level navigation events in Text Navigation tutorial. */
    private int mAltDpadUseCount = 0;

    public LatinTutorialModule4(Context context, TutorialController controller) {
        super(context, controller, R.layout.tutorial_4_text_navigation);

        findViewById(R.id.tutorial_previous).setOnClickListener(this);
        findViewById(R.id.tutorial_exit).setOnClickListener(this);

        SelectionEditText editText = (SelectionEditText) findViewById(R.id.tutorial_4_edittext);
        editText.setOnClickListener(this);
        editText.setSelectionListener(this);
        editText.setOnFocusChangeListener(this);
    }

    @Override
    public void onShown() {
        super.onShown();

        if (!hasFlag(FLAG_STARTED)) {
            setFlag(FLAG_STARTED, true);
            addInstruction(R.string.tutorial_4_message_1);
        }
    }

    @Override
    public void onInstructionRead(int resId) {
        if (resId == R.string.tutorial_4_message_1) {
            setFlag(FLAG_CHAR_READY, true);
            addInstruction(R.string.tutorial_4_message_2);
            findViewById(R.id.tutorial_4_edittext).requestFocusFromTouch();
            findViewById(R.id.tutorial_4_edittext).performClick();
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tutorial_previous) {
            getController().previous();
        } else if (v.getId() == R.id.tutorial_exit) {
            getController().finish();
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.tutorial_4_edittext) {
            // TODO(alanv): Do focus and selection need to be different
            // prompts?
        }
    }

    @Override
    public void onSelectionChanged(
            SelectionEditText editText, int oldSelStart, int oldSelEnd, int selStart, int selEnd) {
        if (editText.getId() == R.id.tutorial_4_edittext) {
            int diff = Math.abs((selStart - oldSelStart) + (selEnd - oldSelEnd));
            if (diff == 2 && hasFlag(FLAG_CHAR_READY) && !hasFlag(FLAG_CHAR_DONE)) {
                mDpadUseCount++;

                if (mDpadUseCount >= DPAD_USE_TRIGGER) {
                    setFlag(FLAG_CHAR_DONE, true);
                    addInstruction(R.string.tutorial_4_character_ok);
                }
            } else if (diff > 2 && hasFlag(FLAG_CHAR_DONE) && !hasFlag(FLAG_WORD_DONE)) {
                mAltDpadUseCount++;

                if (mAltDpadUseCount >= ALT_DPAD_USE_TRIGGER) {
                    setFlag(FLAG_WORD_DONE, true);
                    addInstruction(R.string.tutorial_4_word_ok);
                }
            }
        }
    }

    @Override
    public void onKeyboardBroadcast(String action, Bundle extras) {
        if (LatinIME.BROADCAST_GRANULARITY_CHANGE.equals(action)) {
            int granularity = extras.getInt(LatinIME.EXTRA_GRANULARITY, -1);
            if (granularity == TextNavigation.GRANULARITY_WORD && hasFlag(FLAG_CHAR_DONE)
                    && !hasFlag(FLAG_WORD_MODE)) {
                setFlag(FLAG_WORD_MODE, true);
                addInstruction(R.string.tutorial_4_word_mode);
            } else if (granularity == TextNavigation.GRANULARITY_CHAR && hasFlag(FLAG_WORD_DONE)
                    && !hasFlag(FLAG_CHAR_MODE)) {
                setFlag(FLAG_CHAR_MODE, true);
                addInstruction(R.string.tutorial_4_char_mode);
            }
        }
    }
}
