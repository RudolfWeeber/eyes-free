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

import com.googlecode.eyesfree.utils.AccessibilityNodeInfoUtils;


class RuleSimpleTemplate extends RuleDefault {
    private final String mTargetClassName;
    private final Class<?> mTargetClass;
    private final int mResId;

    public RuleSimpleTemplate(Class<?> targetClass, int resId) {
        mTargetClassName = null;
        mTargetClass = targetClass;
        mResId = resId;
    }

    public RuleSimpleTemplate(String targetClassName, int resId) {
        mTargetClassName = targetClassName;
        mTargetClass = null;
        mResId = resId;
    }

    @Override
    public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
        if (mTargetClass != null) {
            return AccessibilityNodeInfoUtils.nodeMatchesClassByType(context, node, mTargetClass);
        }

        if (mTargetClassName != null) {
            return AccessibilityNodeInfoUtils.nodeMatchesClassByName(node, mTargetClassName);
        }

        return false;
    }

    @Override
    public CharSequence
            format(Context context, AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
        final CharSequence text = super.format(context, node, event);
        return context.getString(mResId, text);
    }
}
