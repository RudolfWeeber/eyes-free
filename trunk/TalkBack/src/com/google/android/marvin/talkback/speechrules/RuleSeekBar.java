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
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.google.android.marvin.talkback.AccessibilityNodeInfoUtils;
import com.google.android.marvin.talkback.R;

/**
 * Formats speech for SeekBar widgets.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class RuleSeekBar extends RuleDefault {
    private static final String SEPARATOR = " ";

    @Override
    public boolean accept(AccessibilityNodeInfo node) {
        return AccessibilityNodeInfoUtils
                .nodeMatchesClassByType(node, android.widget.SeekBar.class);
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfo node, AccessibilityEvent event) {
        final StringBuilder output = new StringBuilder();
        final CharSequence text = super.format(context, node, event);

        output.append(context.getString(R.string.template_seek_bar, text));
        output.append(SEPARATOR);

        if ((event != null) && (event.getItemCount() > 0)) {
            final int percent = (100 * event.getCurrentItemIndex()) / event.getItemCount();

            output.append(context.getString(R.string.template_percent, percent));
            output.append(SEPARATOR);
        }

        return output;
    }
}
