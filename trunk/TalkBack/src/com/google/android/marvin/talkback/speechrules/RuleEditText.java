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

import com.google.android.marvin.talkback.AccessibilityNodeInfoUtils;
import com.google.android.marvin.talkback.R;

/**
 * Processes editable text fields.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
class RuleEditText implements NodeSpeechRule {
    @Override
    public boolean accept(AccessibilityNodeInfo node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node,
                android.widget.EditText.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfo node, AccessibilityEvent event) {
        final CharSequence text = getText(context, node);
        return context.getString(R.string.template_edit_box, text);
    }

    /**
     * Inverts the default priorities of text and content description. If the
     * field is a password, only returns the content description or "password".
     * 
     * @param context
     * @param node
     * @return A text description of the editable text area.
     */
    private CharSequence getText(Context context, AccessibilityNodeInfo node) {
        final CharSequence text = node.getText();

        if (!TextUtils.isEmpty(text) && !node.isPassword()) {
            return text;
        }

        final CharSequence contentDescription = node.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        }

        if (node.isPassword()) {
            return context.getString(R.string.value_password);
        }

        return "";
    }
}
