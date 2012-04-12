/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.compat.content.pm;

import android.content.pm.PackageManager;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PackageManagerCompatUtils {
    private static final Method METHOD_hasSystemFeature = CompatUtils.getMethod(
            PackageManager.class, "hasSystemFeature", String.class);

    private static final Field FIELD_FEATURE_TELEPHONY = CompatUtils.getField(
            PackageManager.class, "FEATURE_TELEPHONY");
    private static final Field FIELD_FEATURE_TOUCHSCREEN = CompatUtils.getField(
            PackageManager.class, "FEATURE_TOUCHSCREEN");

    /**
     * Feature for {@link PackageManager#getSystemAvailableFeatures} and
     * {@link #hasSystemFeature}: The device has a telephony radio with data
     * communication support.
     */
    public static final String FEATURE_TELEPHONY = (String) CompatUtils.getFieldValue(
            null, null, FIELD_FEATURE_TELEPHONY);

    /**
     * Feature for {@link PackageManager#getSystemAvailableFeatures} and
     * {@link PackageManager#hasSystemFeature}: The device's display has a touch
     * screen.
     * <p>
     * Returns {@code null} if this feature is not supported by the current SDK.
     * </p>
     */
    public static final String FEATURE_TOUCHSCREEN = (String) CompatUtils.getFieldValue(
            null, null, FIELD_FEATURE_TOUCHSCREEN);

    /**
     * Check whether the given feature name is one of the available features as
     * returned by {@link PackageManager#getSystemAvailableFeatures()}.
     *
     * @param receiver The package manager.
     * @param name The feature name, or {@code null} to return the default
     *            value.
     * @param defaultValue The default value to return on error.
     * @return Returns true if the devices supports the feature, else false.
     */
    public static boolean hasSystemFeature(
            PackageManager receiver, String name, boolean defaultValue) {
        if (name == null) {
            return defaultValue;
        }

        if (METHOD_hasSystemFeature == null) {
            return defaultValue;
        }

        return (Boolean) CompatUtils.invoke(
                receiver, defaultValue, METHOD_hasSystemFeature, name);
    }
}
