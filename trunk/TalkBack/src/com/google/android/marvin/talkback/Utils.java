/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.util.HashMap;
import java.util.List;

/**
 * This class contains utility methods.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class Utils {
    private static final String LOG_TAG = "Utils";

    /**
     * Mapping from class names to classes form outside package - serves as
     * cache for performance
     */
    private static final HashMap<String, Class<?>> sClassNameToOutsidePackageClassMap = new HashMap<String, Class<?>>();

    private Utils() {
        /* do nothing - decreasing constructor visibility */
    }

    /**
     * Returns a class by given <code>className</code>. The loading proceeds as
     * follows: </br> 1. Try to load with the current context class loader (it
     * caches loaded classes). </br> 2. If (1) fails try if we have loaded the
     * class before and return it if that is the cases. </br> 3. If (2) failed,
     * try to create a package context and load the class. </p> Note: If the
     * package name is null and an attempt for loading of a package context is
     * required the it is extracted from the class name.
     * 
     * @param context The context from which to first try loading the class.
     * @param className The name of the class to load.
     * @param packageName The name of the package to which the class belongs.
     * @return The class if loaded successfully, null otherwise.
     */
    public static Class<?> loadOrGetCachedClass(Context context, String className,
            String packageName) {
        try {
            // try the current ClassLoader first
            return context.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException cnfe) {
            // do we have a cached class
            Class<?> clazz = sClassNameToOutsidePackageClassMap.get(className);
            if (clazz != null) {
                return clazz;
            }
            // no package - get it from the class name
            if (packageName == null) {
                int lastDotIndex = className.lastIndexOf(".");
                if (lastDotIndex > -1) {
                    packageName = className.substring(0, lastDotIndex);
                } else {
                    return null;
                }
            }
            // all failed - try via creating a package context
            try {
                int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                Context packageContext = context.getApplicationContext().createPackageContext(
                        packageName, flags);
                clazz = packageContext.getClassLoader().loadClass(className);
                sClassNameToOutsidePackageClassMap.put(className, clazz);
                return clazz;
            } catch (NameNotFoundException nnfe) {
                Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                        + nnfe);
            } catch (ClassNotFoundException cnfe2) {
                Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                        + cnfe);
            }

            return null;
        }
    }

    /**
     * @return The name of the current {@link android.app.Activity} given
     *         through the current <code>context</code>.
     */
    public static String getCurrentActivityName(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Service.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            return tasks.get(0).topActivity.getClassName();
        }
        return null;
    }
}
