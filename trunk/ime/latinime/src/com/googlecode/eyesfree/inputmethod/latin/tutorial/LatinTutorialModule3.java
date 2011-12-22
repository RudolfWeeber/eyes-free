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

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import com.googlecode.eyesfree.inputmethod.latin.LatinIME;
import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinTutorialModule3 extends TutorialModule implements View.OnClickListener,
        View.OnFocusChangeListener, TextWatcher, LatinIMETutorial.KeyboardModeListener,
        LatinIMETutorial.KeyboardBroadcastListener {
    private static final int FLAG_STARTED = 0;
    private static final int FLAG_TYPING_MODE = 1;
    private static final int FLAG_HELLO_READY = 2;
    private static final int FLAG_HELLO_DONE = 3;
    private static final int FLAG_LEFT_KEYBOARD = 6;
    private static final int FLAG_TYPED_NOTHING = 7;
    private static final int FLAG_OUTSIDE = 8;
    private static final int FLAG_HIDDEN_MODE = 9;
    private static final int FLAG_NAVIGATING_MODE = 10;

    private final String mTextHello;

    public LatinTutorialModule3(Context context, TutorialController controller) {
        super(context, controller, R.layout.tutorial_3_talking_keyboard);

        findViewById(R.id.tutorial_previous).setOnClickListener(this);
        findViewById(R.id.tutorial_continue).setOnClickListener(this);

        TextView editText = (TextView) findViewById(R.id.tutorial_3_edittext);
        editText.setOnFocusChangeListener(this);
        editText.addTextChangedListener(this);

        mTextHello = context.getString(R.string.tutorial_3_text_hello);
    }

    @Override
    public void onShown() {
        super.onShown();

        if (!hasFlag(FLAG_STARTED)) {
            addInstruction(R.string.tutorial_3_message_1);
            setFlag(FLAG_STARTED, true);
        }
    }

    @Override
    public void onInstructionRead(int resId) {
        if (resId == R.string.tutorial_3_message_1) {
            findViewById(R.id.tutorial_3_edittext).requestFocusFromTouch();
            addInstruction(R.string.tutorial_3_message_2);
        } else if (resId == R.string.tutorial_3_message_3) {
            addInstruction(R.string.tutorial_3_message_4);
        } else if (resId == R.string.tutorial_3_message_4) {
            addInstruction(R.string.tutorial_3_message_5);
            setFlag(FLAG_HELLO_READY, true);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tutorial_previous) {
            getController().previous();
        } else if (v.getId() == R.id.tutorial_continue) {
            getController().next();
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.tutorial_3_edittext) {
            if (hasFlag(FLAG_TYPED_NOTHING) && !hasFlag(FLAG_OUTSIDE) && !hasFocus) {
                setFlag(FLAG_OUTSIDE, true);
                addInstruction(R.string.tutorial_3_outside);
            }
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (hasFlag(FLAG_HELLO_READY) && !hasFlag(FLAG_HELLO_DONE)) {
            String text = s.toString().toLowerCase();
            if (text.contains(mTextHello)) {
                setFlag(FLAG_HELLO_DONE, true);
                addInstruction(R.string.tutorial_3_hello_ok);
            }
        }
    }

    @Override
    public void onKeyboardBroadcast(String action, Bundle extras) {
        if (LatinIME.BROADCAST_LEFT_KEYBOARD_AREA.equals(action)) {
            if (hasFlag(FLAG_HELLO_DONE) && !hasFlag(FLAG_LEFT_KEYBOARD)) {
                setFlag(FLAG_LEFT_KEYBOARD, true);
                addInstruction(R.string.tutorial_3_read_back);
            }
        } else if (LatinIME.BROADCAST_UP_OUTSIDE_KEYBOARD.equals(action)) {
            if (hasFlag(FLAG_LEFT_KEYBOARD) && !hasFlag(FLAG_TYPED_NOTHING)) {
                setFlag(FLAG_TYPED_NOTHING, true);
                addInstruction(R.string.tutorial_3_finger_lifted);
            }
        }
    }

    @Override
    public void onKeyboardModeUpdated(int mode) {
        // Do nothing.
    }

    @Override
    public void onKeyboardModeChanged(int mode) {
        switch (mode) {
            case LatinIME.FORCE_HIDDEN:
                if (hasFlag(FLAG_STARTED) && !hasFlag(FLAG_TYPING_MODE)) {
                    addInstruction(R.string.tutorial_3_wrong_mode);
                } else if (hasFlag(FLAG_OUTSIDE) && !hasFlag(FLAG_HIDDEN_MODE)) {
                    setFlag(FLAG_HIDDEN_MODE, true);
                    addInstruction(R.string.tutorial_3_mode_hidden);
                }
                break;
            case LatinIME.FORCE_DPAD:
                if (hasFlag(FLAG_HIDDEN_MODE) && !hasFlag(FLAG_NAVIGATING_MODE)) {
                    setFlag(FLAG_NAVIGATING_MODE, true);
                    addInstruction(R.string.tutorial_3_mode_navigating);
                }
                break;
            case LatinIME.FORCE_CACHED:
                if (hasFlag(FLAG_STARTED) && !hasFlag(FLAG_TYPING_MODE)) {
                    setFlag(FLAG_TYPING_MODE, true);
                    addInstruction(R.string.tutorial_3_message_3);
                }
                break;
        }
    }
}
