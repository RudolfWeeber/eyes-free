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

package com.google.android.marvin.talkback.formatter;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.google.android.marvin.talkback.speechrules.RuleImageView;

/**
 * Formatter that returns an utterance to announce ImageViews.
 *
 * This handles cases where the app author has failed to provide a
 * contentDescription by calling the image "Image #" so that users can still use
 * the app if they know what a particular image # will do based on experience.
 *
 * @author clchen@google.conm (Charles L Chen)
 */
public class ImageViewFormatter implements AccessibilityEventFormatter {
    private RuleImageView ruleImageView;

    public ImageViewFormatter(){
        super();
        ruleImageView = new RuleImageView();
    }

    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
        final AccessibilityNodeInfoCompat source = record.getSource();
        final CharSequence text = ruleImageView.format(context, source, event);
        utterance.getText().replace(0, utterance.getText().length(), text.toString());
        return true;
    }
}
