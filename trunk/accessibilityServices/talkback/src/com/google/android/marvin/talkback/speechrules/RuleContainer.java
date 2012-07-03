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
import android.view.accessibility.AccessibilityEvent;
import android.widget.AbsListView;
import android.widget.TabWidget;

import com.google.android.marvin.talkback.R;
import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;

/**
 * Formats speech for {@link AbsListView} and {@link TabWidget} widgets.
 */
public class RuleContainer implements NodeSpeechRule {
    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        return AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node, AbsListView.class)
                || AccessibilityNodeInfoUtils.nodeMatchesClassByType(
                        context, node, TabWidget.class);
    }

    @Override
    public CharSequence format(
            Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final int childCount = node.getChildCount();
        return formatWithChildren(context, node, childCount);
    }

    private CharSequence formatWithChildren(
            Context context, AccessibilityNodeInfoCompat node, int childCount) {
        final CharSequence type;

        if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                android.widget.GridView.class)) {
            type = context.getString(R.string.value_gridview);
        } else if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node,
                android.widget.TabWidget.class)) {
            type = context.getString(R.string.value_tabwidget);
        } else {
            type = context.getString(R.string.value_listview);
        }

        return context.getString(R.string.template_container, type, childCount);
    }
}
