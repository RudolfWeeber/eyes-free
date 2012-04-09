/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.marvin.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import com.google.android.marvin.talkback.AccessibilityEventUtils;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Utilities for interacting with the {@link PackageManager}.
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class PackageManagerUtils {
    /** Invalid version code for a package. */
    public static final int INVALID_VERSION_CODE = -1;

    /**
     * @return The package version code or {@link #INVALID_VERSION_CODE} if the
     *         package does not exist.
     */
    public static int getVersionCode(Context context, String packageName) {
        final PackageInfo packageInfo = getPackageInfo(context, packageName);

        if (packageInfo == null) {
            LogUtils.log(AccessibilityEventUtils.class, Log.ERROR, "Could not find package: %s",
                    packageName);
            return INVALID_VERSION_CODE;
        }

        return packageInfo.versionCode;
    }

    /**
     * @return The package version name or <code>null</code> if the package does not
     *         exist.
     */
    public static String getVersionName(Context context, String packageName) {
        final PackageInfo packageInfo = getPackageInfo(context, packageName);

        if (packageInfo == null) {
            LogUtils.log(AccessibilityEventUtils.class, Log.ERROR, "Could not find package: %s",
                    packageName);
            return null;
        }

        return packageInfo.versionName;
    }

    /**
     * @return Whether the package is installed on the device.
     */
    public static boolean hasPackage(Context context, String packageName) {
        return (getPackageInfo(context, packageName) != null);
    }

    private static PackageInfo getPackageInfo(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        try {
            return packageManager.getPackageInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns whether the specified package is a system package.
     *
     * @param context The parent context.
     * @param packageName The application package to test.
     * @param includeUpdates Whether to include updated system packages.
     * @return {@code true} if the specified package is a system package
     */
    public static boolean isSystemPackage(
            Context context, String packageName, boolean includeUpdates) {
        boolean result = false;

        try {
            final PackageManager pm = context.getPackageManager();
            final ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            final boolean system = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            final boolean updated = (appInfo.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

            result = system || (includeUpdates && updated);
        } catch (final NameNotFoundException e1) {
            e1.printStackTrace();
        }

        return result;
    }
}
