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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.AccessibilityNodeInfoUtils;
import com.google.android.marvin.talkback.R;

/**
 * Processes images that are not image buttons.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class RuleImageView extends RuleDefault {
    @Override
    public boolean accept(AccessibilityNodeInfo node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(node,
                android.widget.ImageView.class)
                && !AccessibilityNodeInfoUtils.nodeMatchesClassByType(node,
                        android.widget.ImageButton.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfo node, AccessibilityEvent event) {
        final CharSequence text = super.format(context, node, event);
        final boolean isActionable = AccessibilityNodeInfoUtils.isActionable(node);
        final boolean hasLabel = !TextUtils.isEmpty(text);

        if (isActionable && !hasLabel) {
            // Log an error and announce a number for actionable unlabeled
            // images.
            Log.e("TalkBack", "Unlabeled image in " + node.getPackageName());
            final int nodeInt = (node.hashCode() % 100);
            return context.getString(R.string.template_unlabeled_image_view, nodeInt);
        } else if (!isActionable && hasLabel) {
            // Append "image" to non-actionable labeled images.
            return context.getString(R.string.template_image_view, text);
        } else {
            // Otherwise, just return the label.
            return text;
        }
    }

}
