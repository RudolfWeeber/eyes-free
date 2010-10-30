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

package com.google.android.marvin.talkback.formatter.phone;

import com.google.android.marvin.talkback.Formatter;
import com.google.android.marvin.talkback.NotificationType;
import com.google.android.marvin.talkback.Utils;
import com.google.android.marvin.talkback.Utterance;

import android.app.Notification;
import android.content.Context;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityEvent;

/**
 * Formatter that returns an utterance to announce text replacement.
 *
 * @author svetoslavganov@google.conm (Svetoslav R. Ganov)
 */
public final class NotificationFormatter implements Formatter {

    private static final String SPACE = " ";

    @Override
    public void format(AccessibilityEvent event, Context context, Utterance utterance, Object args) {
        StringBuilder utteranceText = utterance.getText();
        StringBuilder eventText = Utils.getEventText(context, event);
        
        NotificationType type;
        Parcelable parcelable = event.getParcelableData();
        if (parcelable instanceof Notification) {
            int icon = ((Notification) parcelable).icon;
            // special case since the notification appears several
            // times while the phone call goes through different phases
            // resulting in multiple announcement of the fact that a phone
            // call is in progress which the user is already aware of
            if (icon == NotificationType.ICON_PHONE_CALL) {
                type = null; // ignore this notification
            } else {
                type = NotificationType.getNotificationTypeFromIcon(icon);
            }
        } else {
            type = null;
        }

        if (type != null) {
            CharSequence typeText = context.getResources().getString(type.getValue());
            utteranceText.append(typeText);
            utteranceText.append(SPACE);
        }
        utteranceText.append(eventText);
    }
}
