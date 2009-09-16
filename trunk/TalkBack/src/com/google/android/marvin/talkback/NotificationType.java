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

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Type safe enumeration for various notification types.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 *
 */
public enum NotificationType implements Parcelable {
    TEXT_MESSAGE(R.string.notification_type_text_message),
    TEXT_MESSAGE_FAILED(R.string.notification_type_text_message_failed),
    MISSED_CALL(R.string.notification_type_missed_call),
    STATUS_NOTIFICATION(R.string.notification_type_status_notification),
    USB_CONNECTED(R.string.notification_type_usb_connected),
    ALERT_DIALOG(R.string.notification_type_alert_dialog),
    SHORT_MESSAGE(R.string.notification_type_short_message),
    MUTE(R.string.notification_status_mute),
    CHAT(R.string.notification_status_chat),
    MORE(R.string.notification_status_more),
    SDCARD(R.string.notification_status_sdcard),
    SDCARD_USB(R.string.notification_status_sdcard_usb),
    SYNC(R.string.notification_status_sync),
    SYNC_NOANIM(R.string.notification_status_sync_noanim),
    VOICEMAIL(R.string.notification_status_voicemail),
    ERROR(R.string.notification_status_error),
    PLAY(R.string.notification_status_play);

    private int mValue;

    private NotificationType(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    /**
     * {@inheritDoc Parcelable#describeContents()}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@inheritDoc Parcelable#writeToParcel(Parcel, int)}
     */
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mValue);
    }

    /**
     * Gets an enumeration member for value.
     *
     * @param value The value.
     * @return The enumeration member.
     */
    private static NotificationType getMemberForValue(int value) {
        for (NotificationType type : values()) {
            if (type.mValue == value) {
                return type;
            }
        }
        return null;
    }

    /**
     * @see Parcelable.Creator
     */
    public static final Parcelable.Creator<NotificationType> CREATOR
            = new Parcelable.Creator<NotificationType>() {
        public NotificationType createFromParcel(Parcel parcel) {
            int value = parcel.readInt();
            return getMemberForValue(value);
        }

        public NotificationType[] newArray(int size) {
            return new NotificationType[size];
        }
    };
}

