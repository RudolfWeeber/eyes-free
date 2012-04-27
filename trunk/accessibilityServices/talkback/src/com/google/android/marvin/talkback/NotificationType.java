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
import android.util.Log;

import com.google.android.marvin.utils.ClassLoadingManager;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * Type safe enumeration for various notification types.
 *
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public enum NotificationType {
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

    private static int ICON_SMS;
    private static int ICON_SMS_FAILED;
    private static int ICON_PLAY;
    private static int ICON_GMAIL;
    private static int ICON_EMAIL;

    private static boolean sHasLoadedIcons = false;

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

    private static final int INVALID_ICON = -1;

    /** The resource identifier for this notification type. */
    private final int mResId;

    /**
     * Creates a new notification type using the specified resource identifier.
     *
     * @param resId The resource identifier that describes this type of
     *            notification.
     */
    private NotificationType(int resId) {
        mResId = resId;
    }

    /**
     * @return The resource identifier for this type of notification.
     */
    public int getValue() {
        return mResId;
    }

    private static void loadIcons(Context context) {
        ICON_SMS =
                loadIcon(context, "com.android.mms", "com.android.mms.R$drawable",
                        "stat_notify_sms");
        ICON_SMS_FAILED =
                loadIcon(context, "com.android.mms", "com.android.mms.R$drawable",
                        "stat_notify_sms_failed");
        ICON_PLAY =
                loadIcon(context, "com.google.android.music", "com.android.music.R$drawable",
                        "stat_notify_musicplayer");
        ICON_GMAIL =
                loadIcon(context, "com.google.android.gm", "com.google.android.gm.R$drawable",
                        "stat_notify_email");
        ICON_EMAIL =
                loadIcon(context, "com.google.android.email", "com.android.email.R$drawable",
                        "stat_notify_email_generic");

        sHasLoadedIcons = true;
    }

    /**
     * Returns the notification type for a given icon.
     *
     * @param icon The notification icon to examine.
     * @return The notification type, or {@code null} if the notification type
     *         was not recognized.
     */
    public static NotificationType getNotificationTypeFromIcon(Context context, int icon) {
        if (!sHasLoadedIcons) {
            loadIcons(context);
        }

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

    /**
     * Loads the specified icon from the package, class, and field.
     *
     * @param packageName The package containing the icon.
     * @param className The class name containing the icon.
     * @param fieldName The field containing the icon resource identifier.
     * @return The resource identifier for the specified icon.
     */
    private static int loadIcon(Context context, String packageName, String className,
            String fieldName) {
        final ClassLoadingManager clm = ClassLoadingManager.getInstance();
        final Class<?> drawable = clm.loadOrGetCachedClass(context, className, packageName);

        if (drawable == null) {
            LogUtils.log(NotificationType.class, Log.WARN,
                    "Can't find class drawable in package: %s", packageName);
            return INVALID_ICON;
        }

        int icon = INVALID_ICON;

        try {
            icon = drawable.getDeclaredField(fieldName).getInt(null);
        } catch (Exception e) {
            LogUtils.log(NotificationType.class, Log.WARN,
                    "Failed to load drawable %s from package %s", fieldName, packageName);
        }

        return icon;
    }
}
