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

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * Type safe enumeration for various notification types.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public enum NotificationType implements Parcelable {
    TEXT_MESSAGE(R.string.notification_type_text_message), TEXT_MESSAGE_FAILED(
            R.string.notification_type_failed_text_message), MISSED_CALL(
            R.string.notification_type_missed_call), USB_CONNECTED(
            R.string.notification_type_usb_connected),
    MUTE(R.string.notification_type_status_mute), CHAT(R.string.notification_type_status_chat),
    ERROR(R.string.notification_type_status_error), MORE(R.string.notification_type_status_more),
    SDCARD(R.string.notification_type_status_sdcard), SDCARD_USB(
            R.string.notification_type_status_sdcard_usb), SYNC(R.string.notification_status_sync),
    SYNC_NOANIM(R.string.notification_type_status_sync_noanim), VOICEMAIL(
            R.string.notification_type_status_voicemail), PLAY(R.string.notification_status_play),
    EMAIL(R.string.notification_type_new_email);

    private int mValue;

    private static final int ICON_SMS = loadIcon("com.android.mms", "com.android.mms.R$drawable",
            "stat_notify_sms");

    private static final int ICON_SMS_FAILED = loadIcon("com.android.mms",
            "com.android.mms.R$drawable", "stat_notify_sms_failed");

    private static final int ICON_PLAY = loadIcon("com.google.android.music",
            "com.android.music.R$drawable", "stat_notify_musicplayer");

    private static final int ICON_GMAIL = loadIcon("com.google.android.gm",
            "com.google.android.gm.R$drawable", "stat_notify_email");

    private static final int ICON_EMAIL = loadIcon("com.google.android.email",
            "com.android.email.R$drawable", "stat_notify_email_generic");

    // INCLUDED in android.jar (accessible via API)
    private static final int ICON_MISSED_CALL = android.R.drawable.stat_notify_missed_call;

    private static final int ICON_MUTE = android.R.drawable.stat_notify_call_mute;

    private static final int ICON_CHAT = android.R.drawable.stat_notify_chat;

    private static final int ICON_ERROR = android.R.drawable.stat_notify_error;

    private static final int ICON_MORE = android.R.drawable.stat_notify_more;

    private static final int ICON_SDCARD = android.R.drawable.stat_notify_sdcard;

    private static final int ICON_SDCARD_USB = android.R.drawable.stat_notify_sdcard_usb;

    private static final int ICON_SYNC = android.R.drawable.stat_notify_sync;

    private static final int ICON_SYNC_NOANIM = android.R.drawable.stat_notify_sync_noanim;

    private static final int ICON_VOICEMAIL = android.R.drawable.stat_notify_voicemail;

    // This constant must be public to reference it to screen out phone calls
    public static final int ICON_PHONE_CALL = android.R.drawable.stat_sys_phone_call;

    private static final int INVALID_ICON = -1;

    private NotificationType(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
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

    public static final Parcelable.Creator<NotificationType> CREATOR =
            new Parcelable.Creator<NotificationType>() {
                @Override
                public NotificationType createFromParcel(Parcel parcel) {
                    int value = parcel.readInt();
                    return getMemberForValue(value);
                }

                @Override
                public NotificationType[] newArray(int size) {
                    return new NotificationType[size];
                }
            };

    public static NotificationType getNotificationTypeFromIcon(int icon) {

        // Can't use switch because not all of the icon fields are constant
        if (icon == ICON_SMS) {
            return NotificationType.TEXT_MESSAGE;
        } else if (icon == ICON_SMS_FAILED) {
            return NotificationType.TEXT_MESSAGE_FAILED;
        } else if (icon == ICON_MISSED_CALL) {
            return NotificationType.MISSED_CALL;
        } else if (icon == ICON_MUTE) {
            return NotificationType.MUTE;
        } else if (icon == ICON_CHAT) {
            return NotificationType.CHAT;
        } else if (icon == ICON_ERROR) {
            return NotificationType.ERROR;
        } else if (icon == ICON_MORE) {
            return NotificationType.MORE;
        } else if (icon == ICON_SDCARD) {
            return NotificationType.SDCARD;
        } else if (icon == ICON_SDCARD_USB) {
            return NotificationType.SDCARD_USB;
        } else if (icon == ICON_SYNC) {
            return NotificationType.SYNC;
        } else if (icon == ICON_SYNC_NOANIM) {
            return NotificationType.SYNC_NOANIM;
        } else if (icon == ICON_VOICEMAIL) {
            return NotificationType.VOICEMAIL;
        } else if (icon == ICON_EMAIL) {
            return NotificationType.EMAIL;
        } else if (icon == ICON_GMAIL) {
            return NotificationType.EMAIL;
        } else if (icon == ICON_PLAY) {
            return NotificationType.PLAY;
        } else {
            LogUtils.log(NotificationType.class, Log.WARN, "Unknown notification %d", icon);
            return null;
        }
    }

    public static int loadIcon(String packageName, String className, String fieldName) {
        final ClassLoadingManager clm = ClassLoadingManager.getInstance();
        final Context ctx = TalkBackService.asContext();
        final Class<?> drawable = clm.loadOrGetCachedClass(ctx, className, packageName);

        if (drawable == null) {
            LogUtils.log(NotificationType.class, Log.WARN,
                    "Can't find class drawable in package: %s", packageName);
            return INVALID_ICON;
        }

        try {
            return drawable.getDeclaredField(fieldName).getInt(null);
        } catch (Exception e) {
            LogUtils.log(NotificationType.class, Log.WARN,
                    "Can't find drawable: %s in package: %s", fieldName, packageName, e.toString());

            return INVALID_ICON;
        }
    }
}
