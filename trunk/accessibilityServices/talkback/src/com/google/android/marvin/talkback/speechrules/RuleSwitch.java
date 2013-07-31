/*
 * Copyright (C) 2013 Google Inc.
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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.utils.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

/**
 * Formats speech for CompoundButton widgets.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class RuleSwitch extends RuleDefault {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesAnyClassByType(
                context, node, android.widget.Switch.class, android.widget.ToggleButton.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final SpannableStringBuilder output = new SpannableStringBuilder();
        final CharSequence text = (!TextUtils.isEmpty(node.getText())) ? node.getText()
                : AccessibilityEventUtils.getEventAggregateText(event);
        final CharSequence contentDescription = node.getContentDescription();

        // Prepend any contentDescription, if present
        StringBuilderUtils.appendWithSeparator(output, contentDescription);

        // Append node or event text
        final CharSequence switchDescription = context.getString(R.string.template_switch,
                (!TextUtils.isEmpty(text)) ? text : "");
        StringBuilderUtils.appendWithSeparator(output, switchDescription);

        // The text should contain the current state.  Explicitly speak state for ToggleButtons.
        if (TextUtils.isEmpty(text) || AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                context, node, android.widget.ToggleButton.class)) {
            final CharSequence state = context.getString(
                    node.isChecked() ? R.string.value_checked : R.string.value_not_checked);
            StringBuilderUtils.appendWithSeparator(output, state);
        }

        return output;
    }
}
