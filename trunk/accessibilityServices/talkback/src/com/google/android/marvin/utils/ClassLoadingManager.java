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

package com.google.android.marvin.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import com.googlecode.eyesfree.utils.LogUtils;

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
    /** On SDK 16+, we should only load classes from system packages. */
    private static final boolean mSystemPackagesOnly = (Build.VERSION.SDK_INT >= 16);

    /** The singleton instance of this class. */
    private static ClassLoadingManager sInstance;

    /** Mapping from class names to classes form outside packages. */
    private final HashMap<String, Class<?>> mClassNameToOutsidePackageClassMap =
            new HashMap<String, Class<?>>();

    /**
     * A set of classes not found to be loaded. Used to avoid multiple attempts
     * that will fail.
     */
    private final HashMap<String, HashSet<String>> mNotFoundClassesMap =
            new HashMap<String, HashSet<String>>();

    /** Cache of installed packages to avoid class loading attempts */
    private final HashSet<String> mInstalledPackagesSet = new HashSet<String>();

    /**
     * Returns the singleton instance of this class.
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
     * Returns a class by given <code>className</code>. The loading proceeds as follows:
     * </br> 1. Try to load with the current context class loader (it caches
     * loaded classes). </br> 2. If (1) fails try if we have loaded the class
     * before and return it if that is the cases. </br> 3. If (2) failed, try to
     * create a package context and load the class. </p> Note: If the package
     * name is null and an attempt for loading of a package context is required
     * the it is extracted from the class name.
     *
     * @param context The context from which to first try loading the class.
     * @param className The name of the class to load.
     * @param packageName The name of the package to which the class belongs.
     * @return The class if loaded successfully, null otherwise.
     */
    public Class<?> loadOrGetCachedClass(Context context, CharSequence className,
            CharSequence packageName) {
        if (className == null) {
            return null;
        }

        final String classNameStr = className.toString();
        final String packageNameStr;

        // If we don't know the package name, get it from the class name.
        if (packageName == null) {
            packageNameStr = getPackageNameFromClass(classNameStr);
        } else {
            packageNameStr = packageName.toString();
        }

        // If we already know the class is missing, return immediately.
        if (isClassMissing(classNameStr, packageNameStr)) {
            return null;
        }

        // Try the current ClassLoader.
        try {
            return context.getClassLoader().loadClass(classNameStr);
        } catch (final ClassNotFoundException e) {
            // Do nothing.
        }

        // Try loading from an outside class.
        final Class<?> outsideClazz = loadOutsideClass(context, classNameStr, packageNameStr);
        if (outsideClazz != null) {
            return outsideClazz;
        }

        LogUtils.log(
                Log.DEBUG, "Failed to load class '%s' from package '%s'", className, packageName);

        addMissingClass(classNameStr, packageNameStr);

        return null;
    }

    /**
     * Builds a cache of installed packages.
     */
    private void buildInstalledPackagesCache(Context context) {
        final List<PackageInfo> installedPackages =
                context.getPackageManager().getInstalledPackages(0);

        for (final PackageInfo installedPackage : installedPackages) {
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
     * Extracts the package name from a class name.
     *
     * @param className The class name to parse.
     * @return The package portion of the class name.
     */
    private String getPackageNameFromClass(String className) {
        final int lastDotIndex = className.lastIndexOf(".");

        if (lastDotIndex < 0) {
            return null;
        }

        return className.substring(0, lastDotIndex);
    }

    /**
     * Attempts to load a class by creating a package context. Fails if the
     * class loader is unable to load the class. Cached and returns a
     * {@link Class} if successful.
     *
     * @param context The parent context.
     * @param className The class name to load.
     * @param packageName The application package that contains the class.
     * @return The class, or {@code null} if it could not be loaded.
     */
    private Class<?> loadOutsideClass(Context context, String className, String packageName) {
        // If we've already loaded this class, return it.
        final Class<?> cachedClazz = mClassNameToOutsidePackageClassMap.get(className);

        if (cachedClazz != null) {
            return cachedClazz;
        }

        // Check if the package is installed before attempting to load.
        if (!mInstalledPackagesSet.contains(packageName)) {
            LogUtils.log(Log.DEBUG, "Package not installed. Failed to load class: %s", className);
            return null;
        }

        // Check if the package is restricted before attempting to load.
        if (mSystemPackagesOnly
                && !PackageManagerUtils.isSystemPackage(context, packageName, true)) {
            LogUtils.log(Log.ERROR, "Package is not secure! Failed to load class: %s", className);

            return null;
        }

        // Attempt to load this class, even if it's unsafe to do so.
        try {
            final int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            final Context packageContext =
                    context.getApplicationContext().createPackageContext(packageName, flags);
            final Class<?> outsideClazz = packageContext.getClassLoader().loadClass(className);

            mClassNameToOutsidePackageClassMap.put(className, outsideClazz);

            return outsideClazz;
        } catch (final Exception e) {
            LogUtils.log(ClassLoadingManager.class, Log.ERROR,
                    "Error encountered. Failed to load outside class: %s", className);
        }

        return null;
    }

    private boolean isClassMissing(String className, String packageName) {
        if ((className == null) || (packageName == null)) {
            return true;
        }

        final HashSet<String> notFoundClassesSet = mNotFoundClassesMap.get(packageName);

        return ((notFoundClassesSet != null) && notFoundClassesSet.contains(className));
    }

    private void addMissingClass(String className, String packageName) {
        HashSet<String> notFoundClassesSet = mNotFoundClassesMap.get(packageName);

        if (notFoundClassesSet == null) {
            notFoundClassesSet = new HashSet<String>();

            mNotFoundClassesMap.put(packageName, notFoundClassesSet);
        }

        notFoundClassesSet.add(className);
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
