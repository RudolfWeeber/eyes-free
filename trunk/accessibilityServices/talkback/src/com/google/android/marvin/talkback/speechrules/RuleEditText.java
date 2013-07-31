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
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Processes editable text fields.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class RuleEditText implements NodeSpeechRule, NodeHintRule {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                android.widget.EditText.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final CharSequence text = getText(context, node);
        return context.getString(R.string.template_edit_box, text);
    }

    @Override
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node) {
        // Disabled items don't have any hint text.
        if (!node.isEnabled()) {
            return context.getString(R.string.value_disabled);
        }

        return NodeHintHelper.getHintString(context, R.string.template_hint_edit_text);
    }

    /**
     * Inverts the default priorities of text and content description. If the
     * field is a password, only returns the content description or "password".
     *
     * @param context
     * @param node
     * @return A text description of the editable text area.
     */
    private CharSequence getText(Context context, AccessibilityNodeInfoCompat node) {
        final CharSequence text = node.getText();
        final boolean shouldSpeakPasswords = SecureCompatUtils.shouldSpeakPasswords(context);

        if (!TextUtils.isEmpty(text) && (!node.isPassword() || shouldSpeakPasswords)) {
            return text;
        }

        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        }

        if (node.isPassword() && !shouldSpeakPasswords) {
            return context.getString(R.string.value_password);
        }

        return "";
    }
}
