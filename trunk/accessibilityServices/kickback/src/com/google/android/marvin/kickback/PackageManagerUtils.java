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

package com.google.android.marvin.kickback;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Utilities for interacting with the {@link PackageManager}.
 *
 * @author alanv@google.com (Alan Viverette)
 */
class PackageManagerUtils {
    /** Invalid version code for a package. */
    public static final int INVALID_VERSION_CODE = -1;

    /**
     * @return The package version code or {@link #INVALID_VERSION_CODE} if the
     *         package does not exist.
     */
    public static int getVersionCode(Context context, String packageName) {
        final PackageInfo packageInfo = getPackageInfo(context, packageName);

        if (packageInfo == null) {
            return INVALID_VERSION_CODE;
        }

        return packageInfo.versionCode;
    }

    private static PackageInfo getPackageInfo(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        try {
            return packageManager.getPackageInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }
}
