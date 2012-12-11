/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

/**
 * Utility methods for testing utility methods.
 * <p>
 * <strong>Note:</strong> May access hidden or private APIs. This library should
 * not be used in production code.
 */
public class TestingUtils {
    private static final String TAG = TestingUtils.class.getSimpleName();

    private static final Class<?> CLASS_AccessibilityNodeInfo = CompatUtils.getClass(
            "android.view.accessibility.AccessibilityNodeInfo");
    private static final Method METHOD_AccessibilityNodeInfo_setSealed = CompatUtils.getMethod(
            CLASS_AccessibilityNodeInfo, "setSealed", boolean.class);

    /**
     * Sets if this instance is sealed.
     *
     * @param sealed Whether is sealed.
     */
    public static void AccessibilityNodeInfo_setSealed(
            AccessibilityNodeInfoCompat node, boolean sealed) {
        final Object info = node.getInfo();
        if (info == null) {
            Log.e(TAG, "Compat node was missing internal node");
            return;
        }

        CompatUtils.invoke(info, null, METHOD_AccessibilityNodeInfo_setSealed, sealed);
    }
}
