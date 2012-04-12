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

package com.googlecode.eyesfree.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.util.Log;


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * This class manages efficient loading of classes.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author alanv@google.com (Alan Viverette)
 */
public class ClassLoadingManager implements InfrastructureStateListener {

    /**
     * The singleton instance of this class.
     */
    private static ClassLoadingManager sInstance;

    /**
     * Mapping from class names to classes form outside packages.
     */
    private final HashMap<String, Class<?>> mClassNameToOutsidePackageClassMap =
            new HashMap<String, Class<?>>();

    /**
     * A set of classes not found to be loaded. Used to avoid multiple attempts
     * that will fail.
     */
    private final HashMap<String, HashSet<String>> mNotFoundClassesMap =
            new HashMap<String, HashSet<String>>();

    /**
     * Cache of installed packages to avoid class loading attempts
     */
    private final HashSet<String> mInstalledPackagesSet = new HashSet<String>();

    /**
     * The singleton instance of this class.
     * 
     * @return The singleton instance of this class.
     */
    public static ClassLoadingManager getInstance() {
        if (sInstance == null) {
            sInstance = new ClassLoadingManager();
        }
        return sInstance;
    }
    
    @Override
    public void onInfrastructureStateChange(Context context, boolean isInitialized) {
        if (isInitialized) {
            buildInstalledPackagesCache(context);
            mPackageMonitor.register(context);
        } else {
            clearInstalledPackagesCache();
            mPackageMonitor.unregister();
        }
    }

    /**
     * Builds a cache of installed packages.
     */
    private void buildInstalledPackagesCache(Context context) {
        final List<PackageInfo> installedPackages =
                context.getPackageManager().getInstalledPackages(0);

        for (PackageInfo installedPackage : installedPackages) {
            addInstalledPackageToCache(installedPackage.packageName);
        }
    }

    /**
     * Adds the specified package to the installed package cache.
     * 
     * @param packageName The package name to add.
     */
    private void addInstalledPackageToCache(String packageName) {
        synchronized (this) {
            mInstalledPackagesSet.add(packageName);
            mNotFoundClassesMap.remove(packageName);
        }
    }

    /**
     * Removes the specified package from the installed package cache.
     * 
     * @param packageName The package name to remove.
     */
    private void removeInstalledPackageFromCache(String packageName) {
        synchronized (this) {
            mInstalledPackagesSet.remove(packageName);
        }
    }

    /**
     * Clears the installed package cache.
     */
    private void clearInstalledPackagesCache() {
        synchronized (this) {
            mInstalledPackagesSet.clear();
        }
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
    public Class<?> loadOrGetCachedClass(Context context, CharSequence className,
            CharSequence packageName) {
        if (className == null) {
            LogUtils.log(Log.DEBUG, "Class name was null. Failed to load class: %s", className);
            return null;
        }

        final String classNameStr = className.toString();
        final String packageNameStr;

        // If we don't know the package name, get it from the class name.
        if (packageName == null) {
            final int lastDotIndex = classNameStr.lastIndexOf(".");

            if (lastDotIndex < 0) {
                LogUtils.log(Log.DEBUG, "Missing package name. Failed to load class: %s", className);
                return null;
            }

            packageNameStr = classNameStr.substring(0, lastDotIndex);
        } else {
            packageNameStr = packageName.toString();
        }

        // If we failed loading this class once, don't bother trying again.
        HashSet<String> notFoundClassesSet = mNotFoundClassesMap.get(packageName);
        if (notFoundClassesSet != null && notFoundClassesSet.contains(className)) {
            return null;
        }

        // Try the current ClassLoader.
        try {
            return context.getClassLoader().loadClass(classNameStr);
        } catch (ClassNotFoundException e) {
            // Do nothing.
        }

        // See if we have a cached class.
        final Class<?> clazz = mClassNameToOutsidePackageClassMap.get(className);
        if (clazz != null) {
            return clazz;
        }

        // Check if the package is installed before attempting to load.
        if (!mInstalledPackagesSet.contains(packageName)) {
            LogUtils.log(Log.DEBUG, "Package not installed. Failed to load class: %s", className);
            return null;
        }

        // Attempt to load class by creating a package context.
        try {
            final int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            final Context packageContext =
                    context.getApplicationContext().createPackageContext(packageNameStr, flags);
            final Class<?> outsideClazz = packageContext.getClassLoader().loadClass(classNameStr);

            mClassNameToOutsidePackageClassMap.put(classNameStr, outsideClazz);

            if (outsideClazz != null) {
                return outsideClazz;
            }
        } catch (Exception e) {
            LogUtils.log(ClassLoadingManager.class, Log.ERROR,
                    "Error encountered. Failed to load outside class: %s", className);
        }

        if (notFoundClassesSet == null) {
            notFoundClassesSet = new HashSet<String>();

            mNotFoundClassesMap.put(packageNameStr, notFoundClassesSet);
        }

        notFoundClassesSet.add(classNameStr);

        LogUtils.log(Log.DEBUG, "Failed to load class: %s", className);

        return null;
    }

    /**
     * Monitor to keep track of the installed packages.
     */
    private final BasePackageMonitor mPackageMonitor = new BasePackageMonitor() {
        @Override
        protected void onPackageAdded(String packageName) {
            addInstalledPackageToCache(packageName);
        }

        @Override
        protected void onPackageRemoved(String packageName) {
            removeInstalledPackageFromCache(packageName);
        }

        @Override
        protected void onPackageChanged(String packageName) {
            // Do nothing.
        }
    };
}
