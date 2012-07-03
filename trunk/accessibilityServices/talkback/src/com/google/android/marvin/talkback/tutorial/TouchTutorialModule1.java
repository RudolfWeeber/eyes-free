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
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

import com.google.android.marvin.talkback.R;

/**
 * Introduces using a finger to explore and interact with on-screen content.
 */
@TargetApi(16)
class TouchTutorialModule1 extends TutorialModule {
    private static final int TARGET_POSITION = 0;

    private final AppsAdapter mAppsAdapter;
    private final GridView mAllApps;

    public TouchTutorialModule1(Context context) {
        super(context);

        mAppsAdapter = null;
        mAllApps = null;
    }

    public TouchTutorialModule1(AccessibilityTutorialActivity parentTutorial) {
        super(parentTutorial, R.layout.accessibility_tutorial_1,
                R.string.accessibility_tutorial_lesson_1_title);

        mAppsAdapter = new AppsAdapter(
                getContext(), R.layout.accessibility_tutorial_app_icon,
                R.id.app_icon);

        mAllApps = (GridView) findViewById(R.id.all_apps);
        mAllApps.setAdapter(mAppsAdapter);

        setSkipVisible(true);
        setBackVisible(false);
        setNextVisible(true);
        setFinishVisible(false);
    }

    @Override
    public void onShown() {
        onTrigger0();
    }

    public void onTrigger0() {
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_1, true);

        // Next trigger is a focus event raised from an icon.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
                @Override
            public boolean onRequestSendAccessibilityEvent(
                    ViewGroup host, View child, AccessibilityEvent event) {
                if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
                    child.setTag(R.id.accessibility_tutorial_tag_touched, true);
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
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_2_more, true);

        // Next trigger is a focus event raised from an icon that hasn't been touched.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                        ViewGroup host, View child, AccessibilityEvent event) {
                if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                        && (child.getTag(R.id.accessibility_tutorial_tag_touched) == null)) {
                    child.setTag(R.id.accessibility_tutorial_tag_touched, true);
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger2();
                        }
                    });
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
    }

    private void onTrigger2() {
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_3, true);

        // Next trigger is a focus event raised from an icon.
        // TODO: Is there any way to ensure this is the result of focus traversal?
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                        ViewGroup host, View child, AccessibilityEvent event) {
                if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
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
        final CharSequence targetName = mAppsAdapter.getLabel(TARGET_POSITION);

        addInstruction(R.string.accessibility_tutorial_lesson_1_text_4, true, targetName);

        // Next trigger is a focus event raised from the target icon.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                    ViewGroup host, View child, AccessibilityEvent event) {
                if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                        && (mAllApps.getPositionForView(child) == TARGET_POSITION)) {
                    mAllApps.setAccessibilityDelegate(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger4();
                        }
                    });
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
    }

    private void onTrigger4() {
        final CharSequence targetName = mAppsAdapter.getLabel(TARGET_POSITION);

        addInstruction(R.string.accessibility_tutorial_lesson_1_text_5, true, targetName);

        // One possible trigger is leaving the target icon. This doesn't advance.
        mAllApps.setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override
            public boolean onRequestSendAccessibilityEvent(
                    ViewGroup host, View child, AccessibilityEvent event) {
                if ((event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                        && (mAllApps.getPositionForView(child) != TARGET_POSITION)) {
                    addInstruction(R.string.accessibility_tutorial_lesson_1_text_5_exited,
                            false, targetName);
                    mAllApps.setAccessibilityDelegate(null);
                }
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });

        // The other possible trigger is a click event raised from an target icon.
        mAllApps.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == TARGET_POSITION) {
                    mAllApps.setAccessibilityDelegate(null);
                    mAllApps.setOnItemClickListener(null);
                    installTriggerDelayed(new Runnable() {
                        @Override
                        public void run() {
                            onTrigger5();
                        }
                    });
                }
            }
        });
    }

    private void onTrigger5() {
        // This is the last trigger in this lesson.
        addInstruction(R.string.accessibility_tutorial_lesson_1_text_6,
                true, getContext().getString(R.string.accessibility_tutorial_next));
    }
}
