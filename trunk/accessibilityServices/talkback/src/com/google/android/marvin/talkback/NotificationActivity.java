
package com.google.android.marvin.talkback;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.googlecode.eyesfree.utils.LogUtils;

public class NotificationActivity extends Activity {

    /**
     * An optional extra key that references the string resource ID of the title
     * to show in the notification dialog within this activity. Defaults to
     * "TalkBack" if not provided.
     */
    public static final String EXTRA_INT_DIALOG_TITLE = "title";

    /**
     * A required extra key that references the string resource ID of the
     * message to show in the notification dialog within this activity.
     */
    public static final String EXTRA_INT_DIALOG_MESSAGE = "message";

    /**
     * An optional extra key that references the string resource ID of the
     * button text to show in the notification dialog within this activity.
     * Defaults to "Ok" if not provided.
     */
    public static final String EXTRA_INT_DIALOG_BUTTON = "button";

    /**
     * An optional extra key that references the {@link Notification} ID of
     * notification to dismiss when the user accepts the notification dialog
     * within this activity.
     */
    public static final String EXTRA_INT_NOTIFICATION_ID = "notificationId";

    private AlertDialog mAlert;

    private int mNotificationId = Integer.MIN_VALUE;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle extras = getIntent().getExtras();
        if (extras == null) {
            LogUtils.log(this, Log.WARN, "NotificationActivity received an empty extras bundle.");
            finish();
        }

        mNotificationId = extras.getInt(EXTRA_INT_NOTIFICATION_ID, Integer.MIN_VALUE);

        final int titleRes = extras.getInt(EXTRA_INT_DIALOG_TITLE, -1);
        final int messageRes = extras.getInt(EXTRA_INT_DIALOG_MESSAGE, -1);
        final int buttonRes = extras.getInt(EXTRA_INT_DIALOG_BUTTON, -1);

        final CharSequence dialogTitle = (titleRes != -1) ? getString(titleRes)
                : getString(R.string.talkback_title);
        final CharSequence dialogMessage = (messageRes != -1) ? getString(messageRes) : null;
        final CharSequence acceptButtonText = (buttonRes != -1) ? getString(buttonRes)
                : getString(android.R.string.ok);

        if (TextUtils.isEmpty(dialogMessage)) {
            // No point in showing an empty message.
            finish();
        }

        final OnClickListener acceptButtonListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (mNotificationId != Integer.MIN_VALUE) {
                    dismissNotification();
                }
                dialog.dismiss();
                finish();
            }
        };

        mAlert = new AlertDialog.Builder(this)
                .setTitle(dialogTitle)
                .setMessage(dialogMessage)
                .setCancelable(false)
                .setPositiveButton(acceptButtonText, acceptButtonListener)
                .create();
        mAlert.show();
    }

    private void dismissNotification() {
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(mNotificationId);
    }
}
