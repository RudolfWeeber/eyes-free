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

package com.google.android.marvin.talkback.speechrules;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Rule for speaking web content (e.g. anything that support HTML element
 * navigation).
 */
public class RuleWebContent extends RuleDefault {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.supportsAnyAction(node,
                AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT,
                AccessibilityNodeInfoCompat.ACTION_PREVIOUS_HTML_ELEMENT);
    }

    @Override
    public CharSequence format(
            Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final StringBuilder builder = new StringBuilder();
        final CharSequence text = super.format(context, node, event);

        if (!TextUtils.isEmpty(text)) {
            builder.append(text);
            builder.append(' ');
        }

        builder.append(context.getString(R.string.value_web_view));

        return builder;
    }
}
