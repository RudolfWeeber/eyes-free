/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.marvin.talkback.speechrules;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Default node processing rule. Returns a content description if available,
 * otherwise returns text.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
class RuleDefault implements NodeSpeechRule {
    @Override
    public boolean accept(AccessibilityNodeInfo node) {
        return true;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfo node, AccessibilityEvent event) {
        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        }

        final CharSequence text = node.getText();

        if (!TextUtils.isEmpty(text)) {
            return text;
        }

        return "";
    }
}
