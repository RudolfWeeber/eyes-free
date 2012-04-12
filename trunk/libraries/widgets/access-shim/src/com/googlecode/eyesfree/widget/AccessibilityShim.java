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

package com.googlecode.eyesfree.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;

/**
 * <p>
 * The AccessibilityShim class inserts itself into an {@link Activity} or
 * {@link Dialog}'s view heirarchy and adds Touch Exploration. It is only
 * enabled when {@link AccessibilityManager} is enabled.
 * </p>
 * <p>
 * To add Touch Exploration to your activity, add the following line to your
 * onCreate() method after any calls to setContentView():
 * </p>
 * <p>
 * <code>AccessiblityShim.attachToActivity(this);</code>
 * <p>
 * If your activity uses dialogs, also add the following line at the end of your
 * onCreateDialog() method:
 * </p>
 * <p>
 * <code>AccessiblityShim.attachToDialog(dialog);</code>
 * </p>
 *
 * @author alanv@google.com (Alan Viverette)
 */
public class AccessibilityShim {
    private static final String KEY_DISABLE_EXPLORATION = "disableTouchExploration";

    /**
     * If Accessibility is enabled, adds touch exploration to a view heirarchy.
     *
     * @param decorView The view to which touch exploration will be added.
     */
    public static void attachToView(View decorView) {
        final Context context = decorView.getContext();

        if (!isAccessibilityEnabled(context)) {
            return;
        }

        if (isTouchExplorationDisabled(context)) {
            return;
        }

        final AccessibleFrameLayout frame = new AccessibleFrameLayout(decorView.getContext());

        if (decorView instanceof ViewGroup) {
            frame.inject((ViewGroup) decorView);
        }
    }

    /**
     * Returns <code>true</code> if the user has enabled Accessibility within
     * the system's settings.
     *
     * @param context The application's {@link Context}.
     * @return Returns <code>true</code> if Accessibility is enabled.
     */
    private static boolean isAccessibilityEnabled(Context context) {
        final int enabled = Settings.Secure.getInt(context.getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, -1);

        return (enabled == 1);
    }

    /**
     * Returns <code>true</code> if the application has explicitly disabled
     * touch exploration using a meta tag.
     * <p>
     * <code><meta-data android:name="disableTouchExploration" android:value="true" /> * </code>
     * .
     * </p>
     *
     * @param context The application's {@link Context}.
     * @return Returns <code>true</code> if the application has explicitly
     *         disabled touch exploration.
     */
    private static boolean isTouchExplorationDisabled(Context context) {
        try {
            final ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(), PackageManager.GET_META_DATA);

            if (appInfo.metaData != null && appInfo.metaData.containsKey(KEY_DISABLE_EXPLORATION)) {
                return true;
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Enables touch exploration within an activity's view heirarchy. If your
     * application uses dialogs, you must also use attachToDialog inside of your
     * activity's onCreateDialog method.
     *
     * @param activity The activity to which touch exploration will be added.
     */
    public static void attachToActivity(Activity activity) {
        final View decorView = activity.getWindow().getDecorView();

        attachToView(decorView);
    }

    /**
     * Enables touch exploration within a dialog's view heirarchy.
     *
     * @param dialog The dialog to which touch exploration will be added.
     */
    public static void attachToDialog(Dialog dialog) {
        final View decorView = dialog.getWindow().getDecorView();

        attachToView(decorView);
    }

    private AccessibilityShim() {
        // This class is non-instantiable.
    }
}
