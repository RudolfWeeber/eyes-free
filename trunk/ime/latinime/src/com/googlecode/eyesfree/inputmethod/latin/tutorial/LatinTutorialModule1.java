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
import android.view.View;

import com.googlecode.eyesfree.inputmethod.latin.AccessibilityUtils;
import com.googlecode.eyesfree.inputmethod.latin.LatinIME;
import com.googlecode.eyesfree.inputmethod.latin.R;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class LatinTutorialModule1 extends TutorialModule implements View.OnClickListener {
    private static final int FLAG_STARTED = 0;

    public LatinTutorialModule1(Context context, TutorialController controller) {
        super(context, controller, R.layout.tutorial_1_introduction);

        findViewById(R.id.tutorial_1_content).setOnClickListener(this);
        findViewById(R.id.tutorial_continue).setOnClickListener(this);
    }

    @Override
    public void onShown() {
        super.onShown();

        if (!hasFlag(FLAG_STARTED)) {
            setFlag(FLAG_STARTED, true);
            findViewById(R.id.tutorial_continue).setEnabled(false);

            if (!AccessibilityUtils.isAccessibilityEnabled(getContext())) {
                addInstruction(R.string.need_accessibility_message);
            } else if (!AccessibilityUtils.isInputMethodEnabled(getContext(), LatinIME.class)) {
                addInstruction(R.string.need_enable_message);
            } else if (!AccessibilityUtils.isInputMethodDefault(getContext(), LatinIME.class)) {
                addInstruction(R.string.need_default_message);
            } else {
                addInstruction(R.string.tutorial_1_message_1);
                findViewById(R.id.tutorial_continue).setEnabled(true);
            }
        }
    }

    @Override
    public void onInstructionRead(int resId) {
        if (resId == R.string.tutorial_1_message_1) {
            addInstruction(R.string.tutorial_1_message_2);
        } else if (resId == R.string.tutorial_1_message_2) {
            addInstruction(R.string.tutorial_1_message_3);
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.tutorial_1_content || v.getId() == R.id.tutorial_continue) {
            getController().next();
        }
    }
}
