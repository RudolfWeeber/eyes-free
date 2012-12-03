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
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.marvin.talkback.R;

public interface NodeHintRule {
    /**
     * Determines whether this rule should process the specified node.
     *
     * @param context The parent context.
     * @param node The node to filter.
     * @return {@code true} if this rule should process the node.
     */
    public boolean accept(Context context, AccessibilityNodeInfoCompat node);

    /**
     * Processes the specified node and returns hint text to speak, or
     * {@code null} if the node should not be spoken.
     *
     * @param context The parent context.
     * @param node The node to process.
     * @return A spoken hint description, or {@code null} if the node should not
     *         be spoken.
     */
    public CharSequence getHintText(Context context, AccessibilityNodeInfoCompat node);

    public static class NodeHintHelper {
        private static final int mActionResId;

        static {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mActionResId = R.string.value_double_tap;
            } else {
                mActionResId = R.string.value_single_tap;
            }
        }

        /**
         * Returns a hint string populated with the version-appropriate action
         * string.
         *
         * @param context The parent context.
         * @param hintResId The hint string's resource identifier.
         * @return A populated hint string.
         */
        public static CharSequence getHintString(Context context, int hintResId) {
            return context.getString(hintResId, context.getString(mActionResId));
        }
    }
}
