/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.kickback;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Accessibility service that provides haptic feedback.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 *
 */
public class KickBackService extends AccessibilityService {
    private static final String TALKBACK_PACKAGE = "com.google.android.marvin.talkback";

    /** The minimum version required for KickBack to disable itself. */
    private static final int TALKBACK_REQUIRED_VERSION = 42;

    private static final String LOG_TAG = "KickBackService";

    private static final int NOTIFICATION_TIMEOUT_MILLIS = 80;

    private static final long[] VIEW_CLICKED_PATTERN = new long[] {0, 100};
    private static final long[] VIEW_FOCUSED_OR_SELECTED_PATTERN = new long[] {0, 15, 10, 15};
    private static final long[] NOTIFICATION_OR_WINDOW_STATE_CHANGED_PATTERN =
        new long[] {0, 25, 50, 25, 50, 25};

    private Vibrator mVibrator;

    /** Whether this service should disable itself. */
    private boolean mDisabled;

    @Override
    public void onCreate() {
         super.onCreate();

         // If TalkBack r42 or higher is installed, this service should be quiet.
         final int talkBackVersion = PackageManagerUtils.getVersionCode(this, TALKBACK_PACKAGE);

         if (talkBackVersion >= TALKBACK_REQUIRED_VERSION) {
             mDisabled = true;
             return;
         }

         mVibrator = (Vibrator) getSystemService(Service.VIBRATOR_SERVICE);
    }

    @Override
    public void onServiceConnected() {
        if (mDisabled) {
            return;
        }

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_HAPTIC;
        info.notificationTimeout = NOTIFICATION_TIMEOUT_MILLIS;
        info.flags = AccessibilityServiceInfo.DEFAULT;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mDisabled) {
            return;
        }

        int eventType = event.getEventType();
        switch (eventType) {
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED :
                mVibrator.vibrate(NOTIFICATION_OR_WINDOW_STATE_CHANGED_PATTERN, -1);
                return;
            case AccessibilityEvent.TYPE_VIEW_CLICKED :
                mVibrator.vibrate(VIEW_CLICKED_PATTERN, -1);
                break;
            case AccessibilityEvent.TYPE_VIEW_FOCUSED :
                mVibrator.vibrate(VIEW_FOCUSED_OR_SELECTED_PATTERN, -1);
                break;
            case AccessibilityEvent.TYPE_VIEW_SELECTED :
                mVibrator.vibrate(VIEW_FOCUSED_OR_SELECTED_PATTERN, -1);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED :
                mVibrator.vibrate(NOTIFICATION_OR_WINDOW_STATE_CHANGED_PATTERN, -1);
                break;
            default :
                Log.w(LOG_TAG, "Unknown accessibility event type " + eventType);
        }
    }

    @Override
    public void onInterrupt() {
        mVibrator.cancel();
    }
}
