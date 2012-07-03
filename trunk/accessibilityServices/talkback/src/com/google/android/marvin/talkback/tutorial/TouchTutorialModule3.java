/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.marvin.talkback.tutorial;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.google.android.marvin.talkback.R;

/**
 * Introduces text navigation granularity.
 */
@TargetApi(16)
class TouchTutorialModule3 extends TutorialModule {
    private static final int MIN_REQUIRED_TRAVERSALS = 3;

    private final TextView mTextView;

    public TouchTutorialModule3(Context context) {
        super(context);

        mTextView = null;
    }

    public TouchTutorialModule3(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.accessibility_tutorial_3,
                R.string.accessibility_tutorial_lesson_3_title);

        mTextView = (TextView) findViewById(R.id.text_view);

        setSkipVisible(false);
        setBackVisible(true);
        setNextVisible(false);
        setFinishVisible(true);
    }

    @Override
    public void onShown() {
        onTrigger0();
    }

    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_1, true);

        // Next trigger is a focus event raised from the text view.
        mTextView.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void sendAccessibilityEvent(View host, int eventType) {
                super.sendAccessibilityEvent(host, eventType);

                if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger1();
                        }
                    });
                }
            }
        });
    }

    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_2, true);

        // Next trigger is a traversal event raised from the text view.
        mTextView.setAccessibilityDelegate(new AccessibilityDelegate() {
            private int mTraversalCount = 0;

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                final boolean result = super.performAccessibilityAction(host, action, arguments);

                if (action == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY) {
                    if (++mTraversalCount >= MIN_REQUIRED_TRAVERSALS) {
                        mTextView.setAccessibilityDelegate(null);
                        installTriggerDelayed(new Runnable() {
                            @Override
                            public void run() {
                                onTrigger2();
                            }
                        });
                    }
                }

                return result;
            }
        });
    }

    private void onTrigger2() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_3_text_3,
                true, getContext().getString(R.string.accessibility_tutorial_finish));
    }
}
