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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class contains utility methods.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author alanv@google.com (Alan Viverette)
 */
public class Utils {
    private static final Context CONTEXT = TalkBackService.asContext();
    private static final CharSequence SEPARATOR = " ";

    /** Invalid version code for a package. */
    public static final int INVALID_VERSION_CODE = -1;

    /**
     * @return The package version code or {@link #INVALID_VERSION_CODE} if the
     *         package does not exist.
     */
    public static int getVersionCode(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        try {
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            LogUtils.log(Utils.class, Log.ERROR, "Could not find package: %s\n%s", packageName,
                    e.toString());
            return INVALID_VERSION_CODE;
        }
    }

    /**
     * @return The package version name or <code>null</code> if the package does
     *         not exist.
     */
    public static String getVersionName(Context context, String packageName) {
        final PackageManager packageManager = context.getPackageManager();

        try {
            final PackageInfo packageInfo = packageManager.getPackageInfo(packageName, 0);
            return packageInfo.versionName;
        } catch (NameNotFoundException e) {
            LogUtils.log(Utils.class, Log.ERROR, "Could not find package: %s\n%s", packageName,
                    e.toString());
            return null;
        }
    }

    /**
    /**
     * Returns {@code true} if the specified node is a view group or has
     * children.
     *
     * @param node The node to test.
     * @return {@code true} if the specified node is a view group or has
     *         children.
     */
    public static boolean isViewGroup(AccessibilityEvent event, AccessibilityNodeInfo node) {
        if (event != null && Utils.eventMatchesClassByType(event, android.view.ViewGroup.class)) {
            return true;
        }

        if (node != null
                && ((node.getChildCount() > 0) || AccessibilityNodeInfoUtils
                        .nodeMatchesClassByType(node, android.view.ViewGroup.class))) {
            return true;
        }

        return false;
    }

    /**
     * Determines if the generating class of an {@link AccessibilityEvent}
     * matches a given {@link Class} by type.
     *
     * @param event An {@link AccessibilityEvent} dispatched by the
     *            accessibility framework.
     * @param clazz A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityNodeInfo} object matches
     *         the {@link Class} by type or inherited type, {@code false}
     *         otherwise.
     */
    public static boolean eventMatchesClassByType(AccessibilityEvent event, Class<?> clazz) {
        if (event == null || clazz == null) {
            return false;
        }

        final ClassLoadingManager classLoader = ClassLoadingManager.getInstance();
        final CharSequence className = event.getClassName();
        final Class<?> nodeClass = classLoader.loadOrGetCachedClass(CONTEXT, className, null);

        if (nodeClass == null) {
            return false;
        }

        return clazz.isAssignableFrom(nodeClass);
    }

    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     *
     * @param context The context from which to load required resources.
     * @param event The event.
     * @return The event text.
     */
    public static CharSequence getEventText(Context context, AccessibilityEvent event) {
        final CharSequence contentDescription = event.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return new StringBuilder(contentDescription);
        } else {
            return getEventAggregateText(context, event);
        }
    }

    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     *
     * @param context The context from which to load required resources.
     * @param event The event.
     * @return The event text.
     */
    public static CharSequence getEventAggregateText(Context context, AccessibilityEvent event) {
        final StringBuilder aggregator = new StringBuilder();
        final List<CharSequence> eventText = event.getText();

        for (CharSequence text : event.getText()) {
            aggregator.append(text);
            aggregator.append(SEPARATOR);
        }

        return aggregator;
    }

    /**
     * Loads a map of key strings to value strings from array resources.
     *
     * @param context The parent context.
     * @param keysResource A resource identifier for the array of key strings.
     * @param valuesResource A resource identifier for the array of value
     *            strings.
     * @return A map of keys to values.
     */
    public static Map<String, String> loadMapFromStringArrays(Context context, int keysResource,
            int valuesResource) {
        final Resources res = context.getResources();
        final String[] keys = res.getStringArray(keysResource);
        final String[] values = res.getStringArray(valuesResource);

        if (keys.length != values.length) {
            throw new IllegalArgumentException("Array size mismatch");
        }

        final Map<String, String> map = new HashMap<String, String>();

        for (int i = 0; i < keys.length; i++) {
            map.put(keys[i], values[i]);
        }

        return map;
    }

    /**
     * @return If the <code>first</code> event is equal to the
     *         <code>second</code>.
     */
    public static boolean accessibilityEventEquals(AccessibilityEvent first,
            AccessibilityEvent second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getEventType() != second.getEventType()) {
            return false;
        }
        if (first.getPackageName() == null) {
            if (second.getPackageName() != null) {
                return false;
            }
        } else if (!first.getPackageName().equals(second.getPackageName())) {
            return false;
        }
        if (first.getClassName() == null) {
            if (second.getClassName() != null) {
                return false;
            }
        } else if (!first.getClassName().equals(second.getClassName())) {
            return false;
        }
        if (!first.getText().equals(second.getText())) { // never null
            return false;
        }
        if (first.getContentDescription() == null) {
            if (second.getContentDescription() != null) {
                return false;
            }
        } else if (!first.getContentDescription().equals(second.getContentDescription())) {
            return false;
        }
        if (first.getBeforeText() == null) {
            if (second.getBeforeText() != null) {
                return false;
            }
        } else if (!first.getBeforeText().equals(second.getBeforeText())) {
            return false;
        }
        if (first.getParcelableData() != null) {
            // do not compare parcelable data it may not implement equals
            // correctly

            return false;
        }
        if (first.getAddedCount() != second.getAddedCount()) {
            return false;
        }
        if (first.isChecked() != second.isChecked()) {
            return false;
        }
        if (first.isEnabled() != second.isEnabled()) {
            return false;
        }
        if (first.getFromIndex() != second.getFromIndex()) {
            return false;
        }
        if (first.isFullScreen() != second.isFullScreen()) {
            return false;
        }
        if (first.getCurrentItemIndex() != second.getCurrentItemIndex()) {
            return false;
        }
        if (first.getItemCount() != second.getItemCount()) {
            return false;
        }
        if (first.isPassword() != second.isPassword()) {
            return false;
        }
        if (first.getRemovedCount() != second.getRemovedCount()) {
            return false;
        }
        if (first.getEventTime() != second.getEventTime()) {
            return false;
        }
        return true;
    }

    public static void addToListOrCreate(Bundle bundle, String listKey, int value) {
        ArrayList<Integer> list = bundle.getIntegerArrayList(listKey);

        if (list == null) {
            list = new ArrayList<Integer>();
            bundle.putIntegerArrayList(listKey, list);
        }

        list.add(value);
    }
}
