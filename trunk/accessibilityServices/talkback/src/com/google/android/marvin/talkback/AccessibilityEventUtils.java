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
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.utils.ClassLoadingManager;
import com.google.android.marvin.utils.StringBuilderUtils;

import java.util.Iterator;
import java.util.List;

/**
 * This class contains utility methods.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 * @author alanv@google.com (Alan Viverette)
 */
public class AccessibilityEventUtils {
    private AccessibilityEventUtils() {
        // This class is not instantiable.
    }

    /**
     * Returns {@code true} if the specified event is a view group.
     *
     * @param event The event to test.
     * @return {@code true} if the specified node is a view group.
     */
    public static boolean isViewGroup(Context context, AccessibilityEvent event) {
        if (event == null) {
            return false;
        }

        return AccessibilityEventUtils.eventMatchesClassByType(context, event,
                android.view.ViewGroup.class);
    }

    /**
     * Determines if the generating class of an {@link AccessibilityEvent}
     * matches a given {@link Class} by type.
     *
     * @param event An {@link AccessibilityEvent} dispatched by the
     *            accessibility framework.
     * @param clazz A {@link Class} to match by type or inherited type.
     * @return {@code true} if the {@link AccessibilityEvent} object matches
     *         the {@link Class} by type or inherited type, {@code false}
     *         otherwise.
     */
    public static boolean eventMatchesClassByType(Context context, AccessibilityEvent event,
            Class<?> clazz) {
        if (event == null || clazz == null) {
            return false;
        }

        final ClassLoadingManager classLoader = ClassLoadingManager.getInstance();
        final CharSequence className = event.getClassName();
        final Class<?> nodeClass = classLoader.loadOrGetCachedClass(context, className, null);

        if (nodeClass == null) {
            return false;
        }

        return clazz.isAssignableFrom(nodeClass);
    }

    /**
     * Gets the text of an <code>event</code> by returning the content description
     * (if available) or by concatenating the text members (regardless of their
     * priority) using space as a delimiter.
     * @param event The event.
     *
     * @return The event text.
     */
    public static CharSequence getEventText(AccessibilityEvent event) {
        if (event == null) {
            return null;
        }

        final CharSequence contentDescription = event.getContentDescription();

        if (!TextUtils.isEmpty(contentDescription)) {
            return contentDescription;
        } else {
            return getEventAggregateText(event);
        }
    }

    /**
     * Gets the text of an <code>event</code> by concatenating the text members
     * (regardless of their priority) using space as a delimiter.
     *
     * @param event The event.
     * @return The event text.
     */
    public static CharSequence getEventAggregateText(AccessibilityEvent event) {
        final StringBuilder aggregator = new StringBuilder();
        final List<CharSequence> eventText = event.getText();
        final Iterator<CharSequence> it = eventText.iterator();

        while (it.hasNext()) {
            final CharSequence text = it.next();

            if (it.hasNext()) {
                StringBuilderUtils.appendWithSeparator(aggregator, text);
            } else {
                aggregator.append(text);
            }
        }

        return aggregator;
    }

    /**
     * @return If the <code>first</code> event is equal to the
     *         <code>second</code>.
     */
    public static boolean eventEquals(AccessibilityEvent first, AccessibilityEvent second) {
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
}
