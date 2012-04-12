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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;

/**
 * Helper class for monitoring packages on the system.
 */
public class BasePackageMonitor extends BroadcastReceiver {

    /**
     * The intent filter to match package modifications.
     */
    final IntentFilter mPackageFilter = new IntentFilter();

    /**
     * The context in which this monitor is registered.
     */
    private Context mRegisteredContext;

    /**
     * Creates a new instance.
     */
    public BasePackageMonitor() {
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mPackageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        mPackageFilter.addDataScheme("package");
    }

    /**
     * Register this monitor via the given <code>context</code>.
     */
    public void register(Context context) {
        if (mRegisteredContext != null) {
            throw new IllegalStateException("Already registered");
        }
        mRegisteredContext = context;
        context.registerReceiver(this, mPackageFilter);
    }

    /**
     * Unregister this monitor.
     */
    public void unregister() {
        if (mRegisteredContext == null) {
            throw new IllegalStateException("Not registered");
        }
        mRegisteredContext.unregisterReceiver(this);
        mRegisteredContext = null;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String packageName = getPackageName(intent);
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            onPackageAdded(packageName);
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
            onPackageRemoved(packageName);
        } else if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
            onPackageChanged(packageName);
        }
    }

    /**
     * @return The name of the package from an <code>intent</code>.
     */
    private String getPackageName(Intent intent) {
        Uri uri = intent.getData();
        String packageName = uri != null ? uri.getSchemeSpecificPart() : null;
        return packageName;
    }

    /**
     * Called when a <code>packageName</code> is added.
     */
    protected void onPackageAdded(String packageName) {}

    /**
     * Called when a <code>packageName</code> is removed.
     */
    protected void onPackageRemoved(String packageName) {}

    /**
     * Called when a <code>packageName</code> is changes.
     */
    protected void onPackageChanged(String packageName) {}
}
