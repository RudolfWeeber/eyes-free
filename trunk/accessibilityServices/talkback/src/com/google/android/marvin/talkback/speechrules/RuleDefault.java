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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

/**
 * Default node processing rule. Returns a content description if available,
 * otherwise returns text.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class RuleDefault implements NodeSpeechRule, NodeHintRule {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return true;
    }

    @Override
    public CharSequence format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);

        if (!TextUtils.isEmpty(nodeText)) {
            return nodeText;
        }

        return "";
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        // Disabled actionable items don't have any hint text.
        if (AccessibilityNodeInfoUtils.isActionableForAccessibility(node) && !node.isEnabled()) {
            return context.getString(R.string.value_disabled);
        }

        final SpannableStringBuilder builder = new SpannableStringBuilder();
        final int actions = node.getActions();

        // Don't read both the checkable AND clickable hints!
        if (node.isCheckable()) {
            StringBuilderUtils.appendWithSeparator(builder,
                    NodeHintHelper.getHintString(context, R.string.template_hint_checkable));
        } else if (AccessibilityNodeInfoUtils.isClickable(node)) {
            StringBuilderUtils.appendWithSeparator(builder,
                    NodeHintHelper.getHintString(context, R.string.template_hint_clickable));
        }

        // Long clickable is long.
        if (AccessibilityNodeInfoUtils.isLongClickable(node)) {
            StringBuilderUtils.appendWithSeparator(builder,
                    NodeHintHelper.getHintString(context, R.string.template_hint_long_clickable));
        }

        return builder;
    }
}
