/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.TalkBackService.EventListener;
import com.google.android.marvin.talkback.tutorial.AccessibilityTutorialActivity;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Produces continuous vibration feedback during framework gesture recognition.
 * <p>
 * Requires API 17+.
 * </p>
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(17)
class ProcessorGestureVibrator implements EventListener {

    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = 17;

    private static final int FEEDBACK_DELAY = 70;

    private final TalkBackService mService;

    private final PreferenceFeedbackController mFeedbackController;

    private final CursorController mCursorController;

    private final Handler mHandler;

    private final Runnable mFeedbackRunnable;

    public ProcessorGestureVibrator(TalkBackService context) {
        mService = context;
        mFeedbackController = context.getFeedbackController();
        mCursorController = context.getCursorController();
        mHandler = new Handler();
        mFeedbackRunnable = new Runnable() {

            @Override
            public void run() {
                mFeedbackController.playRepeatedVibration(
                        R.array.gesture_recognition_repeating_pattern, 2);
                mFeedbackController.playSound(R.raw.gesture_begin, 1.0f, 0.5f);
            }
        };
    }

    @Override
    public void process(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEventCompat.TYPE_TOUCH_INTERACTION_START:
                AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
                // Don't silence speech on first touch if the tutorial is active
                // or if a WebView is active. This works around an issue where
                // the IME is unintentionally dismissed by WebView's
                // performAction implementation.
                if (!AccessibilityTutorialActivity.isTutorialActive()
                        && !AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                                mService, currentNode, android.webkit.WebView.class)) {
                    mService.interruptAllFeedback();
                }
                break;
            case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START:
                mHandler.postDelayed(mFeedbackRunnable, FEEDBACK_DELAY);
                break;
            case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END:
                mHandler.removeCallbacks(mFeedbackRunnable);
                mFeedbackController.cancelVibration();
                break;
        }
    }
}
