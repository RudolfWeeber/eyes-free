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
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.tutorial.InstrumentedListView.ListViewListener;


/**
 * Introduces using two fingers to scroll through a list.
 */
@TargetApi(16)
class TouchTutorialModule2 extends TutorialModule {
    private static final int MIN_SCROLLED_ITEMS = 3;

    private final AppsAdapter mAppsAdapter;
    private final InstrumentedListView mAllApps;

    public TouchTutorialModule2(Context context) {
        super(context);

        mAppsAdapter = null;
        mAllApps = null;
    }

    public TouchTutorialModule2(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.accessibility_tutorial_2,
                R.string.accessibility_tutorial_lesson_2_title);

        mAppsAdapter = new AppsAdapter(
                getContext(), android.R.layout.simple_list_item_1,
                android.R.id.text1) {
            @Override
            protected void populateView(TextView text, CharSequence label, Drawable icon) {
                text.setText(label);
                text.setCompoundDrawables(icon, null, null, null);
            }
        };

        mAllApps = (InstrumentedListView) findViewById(R.id.list_view);
        mAllApps.setAdapter(mAppsAdapter);

        setSkipVisible(false);
        setBackVisible(true);
        setNextVisible(true);
        setFinishVisible(false);
    }

    @Override
    public void onShown() {
        onTrigger0();
    }

    private void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_1, true);

        // Next trigger is a focus event raised from a list item.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override
            public boolean onRequestSendAccessibilityEvent(
                    ViewGroup host, View child, AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger1();
                        }
                    });
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
    }

    private void onTrigger1() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_2, true);

        // Next trigger is a scroll event raised from the list.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public void sendAccessibilityEventUnchecked(View host, AccessibilityEvent event) {
                if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)
                        && (mAllApps.getFirstVisiblePosition() > MIN_SCROLLED_ITEMS)) {
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger2();
                        }
                    });
                }
                super.sendAccessibilityEventUnchecked(host, event);
            }
        });
    }

    private void onTrigger2() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_3, true);

        // Next trigger is a focus event raised from an icon
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                        ViewGroup host, View child, AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger3();
                        }
                    });
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
    }

    private void onTrigger3() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_4, true);

        // Next trigger is a scroll action sent to the list.
        mAllApps.setInstrumentation(new ListViewListener() {
            @Override
            public void onPerformAccessibilityAction(int action) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
                    mAllApps.setInstrumentation(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger4();
                        }
                    });
                }
            }
        });
    }

    private void onTrigger4() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_5, true);

        // Next trigger is a focus event raised from an icon
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                        ViewGroup host, View child, AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger5();
                        }
                    });
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
    }

    private void onTrigger5() {
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_6, true);

        // Next trigger is a scroll action sent to the list.
        mAllApps.setInstrumentation(new ListViewListener() {
            @Override
            public void onPerformAccessibilityAction(int action) {
                if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
                    mAllApps.setInstrumentation(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger6();
                        }
                    });
                }
            }
        });
    }

    private void onTrigger6() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_2_text_7,
                true, getContext().getString(R.string.accessibility_tutorial_next));
    }
}
