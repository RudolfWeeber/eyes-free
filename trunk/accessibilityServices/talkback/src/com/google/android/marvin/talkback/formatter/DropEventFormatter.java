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

package com.google.android.marvin.mytalkback.formatter;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.mytalkback.TalkBackService;
import com.google.android.marvin.mytalkback.Utterance;
import com.google.android.marvin.mytalkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Formatter that will drop an even from the event processor.
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */

public final class DropEventFormatter implements AccessibilityEventFormatter {

    @Override
    public boolean format(AccessibilityEvent event, TalkBackService context, Utterance utterance) {
        // Returning false from an AccessibilityEventFormatter drops the event
        // from the rule processor.
        LogUtils.log(this, Log.VERBOSE, "Dropping event.");
        return false;
    }
}
