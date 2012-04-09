/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Notification;
import android.content.Context;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.utils.StringBuilderUtils;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.HashSet;

/**
 * This class is a cache for notifications. It supports adding, reading,
 * removing of notifications as well as formatting these notification for
 * presentation to the user.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
class NotificationCache {
    /** Map of notification types to cached messages. */
    private final HashMap<NotificationType, Set<CharSequence>> mTypeToMessagesMap =
            new HashMap<NotificationType, Set<CharSequence>>();

    /** The parent context. */
    private final Context mContext;

    /**
     * Creates a new instance.
     * 
     * @param context The parent context.
     */
    public NotificationCache(Context context) {
        mContext = context;
    }

    /**
     * Adds a notification from a given {@link AccessibilityEvent}.
     * 
     * @param event The event contain the notification.
     */
    public void add(AccessibilityEvent event) {
        final Parcelable parcelable = event.getParcelableData();

        if (!(parcelable instanceof Notification)) {
            return;
        }

        final Notification notification = (Notification) parcelable;
        final int icon = notification.icon;

        // Don't record phone calls because we get a missed call anyway.
        if (icon == NotificationType.ICON_PHONE_CALL) {
            return;
        }

        final NotificationType type = NotificationType.getNotificationTypeFromIcon(mContext, icon);

        // Don't record if we don't know what kind of notification it was.
        if (type == null) {
            return;
        }

        final CharSequence text = notification.tickerText;

        addNotification(type, text);
    }

    /**
     * Adds a notification for a given {@link NotificationType}.
     * 
     * @param type The {@link NotificationType}.
     * @param text The notification message.
     */
    private void addNotification(NotificationType type, CharSequence text) {
        if (text == null) {
            return;
        }

        Set<CharSequence> messages = mTypeToMessagesMap.get(type);

        if (messages == null) {
            messages = new HashSet<CharSequence>();
            mTypeToMessagesMap.put(type, messages);
        }

        messages.add(text);
    }

    /**
     * Removes all notifications from the cache.
     */
    public void clear() {
        mTypeToMessagesMap.clear();
    }

    /**
     * @return A summary of the notifications.
     */
    public CharSequence getFormattedSummary() {
        final StringBuilder summary = new StringBuilder();

        for (final Entry<NotificationType, Set<CharSequence>> entry :
                mTypeToMessagesMap.entrySet()) {
            final int count = entry.getValue().size();
            final int type = entry.getKey().getValue();

            StringBuilderUtils.appendWithSeparator(summary, count, mContext.getString(type));

            // TODO(alanv): This is a bad way to handle pluralization.
            if (count > 1) {
                summary.append('s');
            }

            summary.append('\n');
        }

        return summary;
    }
}
