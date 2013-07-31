/*
 * Copyright (C) 2012 Google Inc.
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
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.FullScreenReadController.AutomaticReadingState;
import com.google.android.marvin.talkback.TalkBackService.AccessibilityEventListener;
import com.google.android.marvin.utils.AutomationUtils;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.WebInterfaceUtils;

/**
 * Processor for speaking web content (e.g. anything that support HTML element
 * navigation).
 *
 * <p>
 * Requires API 16+.
 * </p>
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class ProcessorWebContent implements AccessibilityEventListener {
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN;

    private static final int MASK_ACCEPTED_EVENT_TYPES =
            AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED
            | AccessibilityEventCompat.TYPE_VIEW_HOVER_ENTER;

    private static final String PACKAGE_SETTINGS = "com.android.settings";

    private static final String RES_NAME_SCRIPT_INJECTION_TITLE =
            "accessibility_toggle_script_injection_preference_title";

    private final TalkBackService mService;

    private final FullScreenReadController mFullScreenReadController;

    private final SpeechController mSpeechController;

    private AccessibilityNodeInfoCompat mLastNode;

    public ProcessorWebContent(TalkBackService context) {
        mService = context;
        mFullScreenReadController = context.getFullScreenReadController();
        mSpeechController = context.getSpeechController();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Only announce relevant events
        if (!AccessibilityEventUtils.eventMatchesAnyType(event, MASK_ACCEPTED_EVENT_TYPES)) {
            return;
        }

        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();

        // Drop consecutive qualifying events from the same node.
        if (mLastNode != null && mLastNode.equals(source)) {
            return;
        } else {
            if (mLastNode != null) {
                mLastNode.recycle();
            }
            mLastNode = source;
        }

        // Only announce nodes that have web content.
        if (!WebInterfaceUtils.hasWebContent(source)) {
            return;
        }

        if (WebInterfaceUtils.isScriptInjectionEnabled(mService)) {
            // Instruct accessibility script to announce the page title as long
            // as continuous reading isn't active.
            if (mFullScreenReadController.getReadingState()
                    != AutomaticReadingState.ENTERING_WEB_CONTENT) {
                WebInterfaceUtils.performSpecialAction(
                        source, WebInterfaceUtils.ACTION_READ_PAGE_TITLE_ELEMENT);
            } else {
                // Reset the state for full screen reading now that we've moved
                // into web content.
                mFullScreenReadController.interrupt();
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            // On versions that include a script injection preference, inform
            // the user that script injection is disabled.
            final String preferenceName = AutomationUtils.getPackageString(
                    mService, PACKAGE_SETTINGS, RES_NAME_SCRIPT_INJECTION_TITLE);
            if (preferenceName != null) {
                final CharSequence announcement = mService.getString(
                        R.string.hint_script_injection, preferenceName);
                mSpeechController.speak(
                        announcement, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
            }
        }
    }
}
