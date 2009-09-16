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

import java.util.List;
import com.google.android.marvin.talkback.ITalkBackServiceStateChangedCallback;
import com.google.android.marvin.talkback.NotificationType;

/**
 * Provides information about the state of the cached notifications.
 */
interface ITalkBackNotificationState {
    /**
     * Returns all notifications.
     *
     * @return All notifications.
     */
    List<String> getNotificationsAll();

    /**
     * Returns all notifications formatted for presentation.
     *
     * @param type The {@link NotificationType}.
     * @return The formatted string.
     */
    String getFormattedForType(in NotificationType type);

    /**
     * Returns all notifications formatted for presentation.
     *
     * @return The formatted string.
     */
    String getFormattedAll();

    /**
     * returns a summary of the notifications.
     *
     * @return The summary.
     */
    String getFormattedSummary();

    /**
     * Registers a callback to notify clients that the {@link TalkBackService} state
     * has changed. Clients are responsible for polling for specific data upon notification.
     *
     * @param callback The {@link ITalkBackServiceStateChangedCallback}.
     * @return True if the callback is added, false otherwise.
     */
    boolean registerStateChangedCallback(ITalkBackServiceStateChangedCallback callback);

    /**
     * Unregisters a callback to notify clients that the {@link TalkBackService} state
     * has changed.
     *
     * @param callback The {@link ITalkBackServiceStateChangedCallback}.
     * @return True if the callback is removed, false otherwise.
     */
    boolean unregisterStateChangedCallback(ITalkBackServiceStateChangedCallback callback);
}
