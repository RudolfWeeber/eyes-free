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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class is a cache for notifications. It supports adding, reading,
 * removing of notifications as well as formatting these notification for
 * presentation to the user.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class NotificationCache {

    private Map<NotificationType, List<String>> mTypeToMessageMap = new HashMap<NotificationType, List<String>>();

    /**
     * Creates a new instance.
     */
    NotificationCache() {
        /* do nothing */
    }

    /**
     * Adds a notifications for a given {@link NotificationType}.
     * 
     * @param type The {@link NotificationType}.
     * @param notification The notification message.
     * @return True if the notification was added , false otherwise.
     */
    public boolean addNotification(NotificationType type, String notification) {
        List<String> notifications = mTypeToMessageMap.get(type);
        if (notifications == null) {
            notifications = new ArrayList<String>();
            mTypeToMessageMap.put(type, notifications);
        }
        return notifications.add(notification);
    }

    /**
     * Removes a notification from a given type.
     * 
     * @param type The {@link NotificationType}.
     * @param notification The notification message.
     * @return True if removed, false otherwise.
     */
    public boolean removeNotification(NotificationType type, String notification) {
        return getNotificationsForType(type).remove(notification);
    }

    /**
     * Gets all notifications form a given {@link NotificationType}.
     * 
     * @param type The {@link NotificationType}.
     * @return The notifications as a list. If no notifications exist an empty
     *         list is returned.
     */
    public List<String> getNotificationsForType(NotificationType type) {
        List<String> notifications = mTypeToMessageMap.get(type);
        if (notifications == null) {
            notifications = Collections.emptyList();
        }
        return notifications;
    }

    /**
     * Remove all notifications of a given type.
     * 
     * @param type The {@link NotificationType}.
     * @return True if notifications are removed, false otherwise.
     */
    public boolean removeNotificationsForType(NotificationType type) {
        return (mTypeToMessageMap.remove(type) != null);
    }

    /**
     * Returns all notifications.
     * 
     * @return All notifications.
     */
    public List<String> getNotificationsAll() {
        List<String> notifications = new ArrayList<String>();
        for (NotificationType type : mTypeToMessageMap.keySet()) {
            notifications.addAll(getNotificationsForType(type));
        }
        return notifications;
    }

    /**
     * Removes all notifications from the cache.
     */
    public void removeNotificationsAll() {
        mTypeToMessageMap.clear();
    }

    /**
     * Returns all notifications for a given type.
     * 
     * @param type The {@link NotificationType}.
     * @return The formatted string.
     */
    public String getFormattedForType(NotificationType type) {
        StringBuilder formatted = new StringBuilder();
        List<String> notifications = getNotificationsForType(type);
        for (String notification : notifications) {
            formatted.append(getStringForResourceId(type.getValue()));
            formatted.append(" ");
            formatted.append(notification);
            formatted.append(" ");
        }
        return formatted.toString();
    }

    /**
     * Returns all notifications formatted for presentation.
     * 
     * @return The formatted string.
     */
    public String getFormattedAll() {
        StringBuilder formatted = new StringBuilder();
        for (NotificationType type : mTypeToMessageMap.keySet()) {
            formatted.append(getFormattedForType(type));
        }
        return formatted.toString();
    }

    /**
     * Returns a summary of the notifications.
     * 
     * @return The summary.
     */
    public String getFormattedSummary() {
        StringBuilder summary = new StringBuilder();
        for (Map.Entry<NotificationType, List<String>> entry : mTypeToMessageMap.entrySet()) {
            int count = entry.getValue().size();
            if (count > 0 && entry.getKey() != null) {
                summary.append(count);
                summary.append(" ");
                summary.append(getStringForResourceId(entry.getKey().getValue()));
                if (count > 1) {
                    summary.append("s");
                }
                summary.append("\n");
            }
        }
        return summary.toString();
    }

    /**
     * Returns a string for a resource id.
     * 
     * @param resourceId The resource id.
     * @return the string.
     */
    private String getStringForResourceId(int resourceId) {
        return TalkBackService.asContext().getResources().getString(resourceId);
    }
}
