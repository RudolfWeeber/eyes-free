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
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.googlecode.eyesfree.inputmethod.latin.LatinIME;
import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinTutorialModule2 extends TutorialModule implements View.OnClickListener,
        View.OnFocusChangeListener, LatinIMETutorial.KeyboardModeListener, LatinIMETutorial.KeyboardBroadcastListener, View.OnKeyListener {
    private static final int FLAG_STARTED = 0;
    private static final int FLAG_DPAD_OPENED = 1;
    private static final int FLAG_FIRST_FOCUS_READY = 2;
    private static final int FLAG_FIRST_FOCUS_DONE = 3;
    private static final int FLAG_SECOND_FOCUS_DONE = 4;
    private static final int FLAG_SECOND_CLICK_DONE = 5;
    private static final int FLAG_LEFT_DPAD_READY = 6;
    private static final int FLAG_LEFT_DPAD = 7;
    private static final int FLAG_ENTERED_DPAD = 8;
    private static final int FLAG_SEARCH_PRESSED = 9;

    public LatinTutorialModule2(Context context, TutorialController controller) {
        super(context, controller, R.layout.tutorial_2_directional_pad);

        findViewById(R.id.tutorial_previous).setOnClickListener(this);
        findViewById(R.id.tutorial_continue).setOnClickListener(this);

        View first = findViewById(R.id.tutorial_2_first);
        first.setOnFocusChangeListener(this);
        first.setContentDescription("");

        View second = findViewById(R.id.tutorial_2_second);
        second.setOnClickListener(this);
        second.setOnFocusChangeListener(this);
        second.setContentDescription("");
    }

    @Override
    public void onShown() {
        super.onShown();

        if (!hasFlag(FLAG_STARTED)) {
            setFlag(FLAG_STARTED, true);
            getController().requestKeyboardModeUpdate();
        }
    }

    @Override
    public void onInstructionRead(int resId) {
        if (resId == R.string.tutorial_2_message_1) {
            addInstruction(R.string.tutorial_2_message_2);
        } else if (resId == R.string.tutorial_2_message_2) {
            addInstruction(R.string.tutorial_2_message_3);
        } else if (resId == R.string.tutorial_2_message_3) {
            addInstruction(R.string.tutorial_2_message_4);
            setFlag(FLAG_FIRST_FOCUS_READY, true);
        } else if (resId == R.string.tutorial_2_second_tapped) {
            addInstruction(R.string.tutorial_2_second_tapped_2);
            setFlag(FLAG_LEFT_DPAD_READY, true);
        } else if (resId == R.string.tutorial_2_entered_dpad) {
            addInstruction(R.string.tutorial_2_dpad_buttons);
        }
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (v.getId() == R.id.tutorial_2_first) {
            if (hasFlag(FLAG_FIRST_FOCUS_READY) && !hasFlag(FLAG_FIRST_FOCUS_DONE)) {
                addInstruction(R.string.tutorial_2_first_selected);
                v.setContentDescription(null);
                setFlag(FLAG_FIRST_FOCUS_DONE, true);
            }
        } else if (v.getId() == R.id.tutorial_2_second) {
            if (hasFlag(FLAG_FIRST_FOCUS_DONE) && !hasFlag(FLAG_SECOND_FOCUS_DONE)) {
                addInstruction(R.string.tutorial_2_second_selected);
                setFlag(FLAG_SECOND_FOCUS_DONE, true);
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tutorial_previous) {
            getController().previous();
        } else if (v.getId() == R.id.tutorial_continue) {
            getController().next();
        } else if (v.getId() == R.id.tutorial_2_second) {
            if (hasFlag(FLAG_SECOND_FOCUS_DONE) && !hasFlag(FLAG_SECOND_CLICK_DONE)) {
                addInstruction(R.string.tutorial_2_second_tapped);
                v.setContentDescription(null);
                setFlag(FLAG_SECOND_CLICK_DONE, true);
            }
        }
    }

    @Override
    public void onKeyboardModeChanged(int mode) {
        LatinTutorialLogger.log(Log.INFO, "Module 2 saw keyboard mode change to %d", mode);

        switch (mode) {
            case LatinIME.FORCE_DPAD:
                if (!hasFlag(FLAG_DPAD_OPENED)) {
                    setFlag(FLAG_DPAD_OPENED, true);
                    addInstruction(R.string.tutorial_2_message_1);
                }
                break;
            case LatinIME.FORCE_HIDDEN:
                break;
            default:
                break;
        }
    }

    @Override
    public void onKeyboardModeUpdated(int mode) {
        if (mode != LatinIME.FORCE_DPAD) {
            LatinTutorialLogger.log(Log.INFO, "Switching to DPAD mode");
            getController().setKeyboardMode(LatinIME.FORCE_DPAD);
        } else {
            onKeyboardModeChanged(LatinIME.FORCE_DPAD);
        }
    }

    @Override
    public void onKeyboardBroadcast(String action, Bundle extras) {
        if (LatinIME.BROADCAST_LEFT_KEYBOARD_AREA.equals(action)) {
            if (hasFlag(FLAG_LEFT_DPAD_READY) && !hasFlag(FLAG_LEFT_DPAD)) {
                setFlag(FLAG_LEFT_DPAD, true);
                addInstruction(R.string.tutorial_2_left_dpad);
            }
        } else if (LatinIME.BROADCAST_ENTERED_KEYBOARD_AREA.equals(action)) {
            if (hasFlag(FLAG_LEFT_DPAD) && !hasFlag(FLAG_ENTERED_DPAD)) {
                setFlag(FLAG_ENTERED_DPAD, true);
                addInstruction(R.string.tutorial_2_entered_dpad);
            }
        }
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            if (hasFlag(FLAG_ENTERED_DPAD) && !hasFlag(FLAG_SEARCH_PRESSED)) {
                setFlag(FLAG_SEARCH_PRESSED, true);
                addInstruction(R.string.tutorial_2_search_pressed);
                return true;
            }
        }

        return false;
    }
}
