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
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;
import com.googlecode.eyesfree.utils.StringBuilderUtils;

/**
 * Formats speech for SeekBar widgets.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class RuleSeekBar extends RuleDefault {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                android.widget.SeekBar.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final SpannableStringBuilder output = new SpannableStringBuilder();
        final CharSequence text = super.format(context, node, event);
        final CharSequence formattedText = context.getString(R.string.template_seek_bar, text);

        StringBuilderUtils.appendWithSeparator(output, formattedText);

        // TODO: We need to be getting this information from the node.
        if ((event != null) && (event.getItemCount() > 0)) {
            final int percent = (100 * event.getCurrentItemIndex()) / event.getItemCount();
            final CharSequence formattedPercent =
                    context.getString(R.string.template_percent, percent);

            StringBuilderUtils.appendWithSeparator(output, formattedPercent);
        }

        return output;
    }
}
