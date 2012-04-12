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

import com.google.android.marvin.talkback.TalkBackService.InfrastructureStateListener;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * This class manages efficient loading of classes.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class ClassLoadingManager implements InfrastructureStateListener {

    /**
     * Tag used for logging.
     */
    private static final String LOG_TAG = "ClassLoadingManager";

    /**
     * The singleton instance of this class.
     */
    private static ClassLoadingManager sInstance;

    /**
     * Mapping from class names to classes form outside packages.
     */
    private final HashMap<String, Class<?>> mClassNameToOutsidePackageClassMap = new HashMap<String, Class<?>>();

    /**
     * A set of classes not found to be loaded. Used to avoid multiple attempts
     * that will fail.
     */
    private final HashSet<String> mNotFoundClassesSet = new HashSet<String>();

    /**
     * Cache of installed packages to avoid class loading attempts
     */
    private final HashSet<String> mInstalledPackagesSet = new HashSet<String>();

    /**
     * Lock for mutex access to the cache.
     */
    private final Object mLock = new Object();

    /**
     * {@link Context} for accessing
     */
    private final Context mContext;

    /**
     * Monitor to keep track of the installed packages;
     */
    private final BasePackageMonitor mPackageMonitor;

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

    /**
     * Creates a new instance.
     */
    private ClassLoadingManager() {
        mContext = TalkBackService.asContext();
        mPackageMonitor = new BasePackageMonitor() {
            @Override
            protected void onPackageAdded(String packageName) {
                synchronized (mLock) {
                    mInstalledPackagesSet.add(packageName);
                    // TODO (svetoslavganov): we can be more efficient
                    mNotFoundClassesSet.clear();
                }
            }

            @Override
            protected void onPackageRemoved(String packageName) {
                synchronized (mLock) {
                    mInstalledPackagesSet.remove(packageName);
                }
            }
        };
    }

    @Override
    public void onInfrastructureStateChange(boolean isInitialized) {
        if (isInitialized) {
            buildInstalledPackagesCache();
            mPackageMonitor.register(mContext);
        } else {
            clearInstalledPackagesCache();
            mPackageMonitor.unregister();
        }
    }

    /**
     * Builds a cache of installed packages.
     */
    private void buildInstalledPackagesCache() {
        synchronized (mLock) {
            List<PackageInfo> installedPackages = mContext.getPackageManager()
                    .getInstalledPackages(0);
            for (PackageInfo installedPackage : installedPackages) {
                mInstalledPackagesSet.add(installedPackage.packageName);
            }
        }
    }

    /**
     * Clears the installed package cache.
     */
    private void clearInstalledPackagesCache() {
        synchronized (mLock) {
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
    public Class<?> loadOrGetCachedClass(Context context, String className, String packageName) {
        // if we failed once loading this class - no bother trying again
        if (mNotFoundClassesSet.contains(className)) {
            return null;
        }
        try {
            // try the current ClassLoader first
            return context.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException cnfe) {
            // do we have a cached class
            Class<?> clazz = mClassNameToOutsidePackageClassMap.get(className);
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
            // no cached class - check if the package is installed first
            if (!mInstalledPackagesSet.contains(packageName)) {
                return null;
            }
            // all failed - try via creating a package context
            try {
                int flags = (Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                try {
                    Context packageContext = context.getApplicationContext().createPackageContext(
                            packageName, flags);
                    clazz = packageContext.getClassLoader().loadClass(className);
                    mClassNameToOutsidePackageClassMap.put(className, clazz);
                } catch (NullPointerException npe) {
                    String s = "";
                }
                return clazz;
            } catch (NameNotFoundException nnfe) {
                Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                        + nnfe);
            } catch (ClassNotFoundException cnfe2) {
                Log.e(LOG_TAG, "Error during loading an event source class: " + className + " "
                        + cnfe);
                mNotFoundClassesSet.add(className);
            }

            return null;
        }
    }
}
