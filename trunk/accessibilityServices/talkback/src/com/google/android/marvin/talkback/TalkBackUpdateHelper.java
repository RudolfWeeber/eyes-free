
package com.google.android.marvin.talkback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.utils.AutomationUtils;
import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.PackageManagerUtils;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.List;

class TalkBackUpdateHelper {
    /* package */ static final String KICKBACK_PACKAGE = "com.google.android.marvin.kickback";
    /* package */ static final String SOUNDBACK_PACKAGE = "com.google.android.marvin.soundback";

    /** The minimum version required for KickBack to disable itself. */
    /* package */ static final int KICKBACK_REQUIRED_VERSION = 5;

    /** The minimum version required for SoundBack to disable itself. */
    /* package */ static final int SOUNDBACK_REQUIRED_VERSION = 7;

    private static final String PREF_APP_VERSION = "app_version";
    private static final String SETTINGS_MANAGER_PACKAGE = "com.marvin.preferences";

    /**
     * Time in milliseconds after initialization to delay the posting of
     * TalkBack notifications.
     */
    private static final int NOTIFICATION_DELAY = 5000;

    /** Notification ID for the "TalkBack Settings" notification. */
    private static final int SETTINGS_NOTIFICATION_ID = 1;

    /**
     * Notification ID for the Gesture Change notification. This is also used in
     * the GestureChangeNotificationActivity to dismiss the notification.
     */
    /* package */static final int GESTURE_CHANGE_NOTIFICATION_ID = 2;

    /** Notification ID for the built-in gesture change notification. */
    /* package */static final int BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID = 3;

    private final Handler mHandler = new Handler();

    private final TalkBackService mService;
    private final NotificationManager mNotificationManager;
    private final SharedPreferences mSharedPreferences;

    public TalkBackUpdateHelper(TalkBackService service) {
        mService = service;
        mNotificationManager = (NotificationManager) mService.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mService);
    }

    public void showPendingNotifications() {
        // Revision 74 changes the gesture model for Jelly Bean and above.
        // This flag is used to ensure they accept the notification of this
        // change.
        final boolean userMustAcceptGestureChange = mSharedPreferences.getBoolean(
                mService.getString(R.string.pref_must_accept_gesture_change_notification), false);

        if (userMustAcceptGestureChange) {
            // Build the intent for when the notification is clicked.
            final Intent notificationIntent = new Intent(
                    mService, GestureChangeNotificationActivity.class);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

            NotificationPosterRunnable runnable = new NotificationPosterRunnable(
                    buildGestureChangeNotification(notificationIntent),
                    GESTURE_CHANGE_NOTIFICATION_ID);
            mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
        }
    }

    public void checkUpdate() {
        final int previousVersion = mSharedPreferences.getInt(PREF_APP_VERSION, -1);

        final PackageManager pm = mService.getPackageManager();
        final int currentVersion;

        try {
            final PackageInfo packageInfo = pm.getPackageInfo(mService.getPackageName(), 0);
            currentVersion = packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return;
        }

        if (previousVersion == currentVersion) {
            return;
        }

        final Editor editor = mSharedPreferences.edit();
        editor.putInt(PREF_APP_VERSION, currentVersion);

        // Revision 12 supports Accessibility Preferences for pre-ICS.
        if ((previousVersion < 12) && (Build.VERSION.SDK_INT < 14)) {
            // Display a notification about the settings activity only if
            // it is not currently installed.
            if (!PackageManagerUtils.hasPackage(mService, SETTINGS_MANAGER_PACKAGE)) {
                displaySettingsAvailableNotification();
            }
        }

        // Revision 42 supports coexistence with KickBack and SoundBack.
        if (previousVersion < 42) {
            // If KickBack is installed, disable vibration feedback by default.
            if (PackageManagerUtils.hasPackage(mService, KICKBACK_PACKAGE)) {
                final String prefVibration = mService.getString(R.string.pref_vibration_key);
                editor.putBoolean(prefVibration, false);
            }

            // If SoundBack is installed, disable sound feedback by default.
            if (PackageManagerUtils.hasPackage(mService, SOUNDBACK_PACKAGE)) {
                final String prefSoundback = mService.getString(R.string.pref_soundback_key);
                editor.putBoolean(prefSoundback, false);
            }
        }

        // Handle bug when updating from Revision 68 on Jelly Bean factory ROM
        if (needsExploreByTouchHelper(previousVersion)) {
            mService.addEventListener(new ExploreByTouchUpdateHelper(mService));
        }

        // Revision 74 changes the gesture model added in revision 68.
        if ((previousVersion >= 68) && (previousVersion < 74)
                && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)) {
            notifyUserOfGestureChanges();
        }

        // Revision 84 combines the TalkBack and continuous reading breakout
        // menus. References to the continuous reading menu are silently
        // remapped to the local breakout menu.
        if (previousVersion < 84) {
            remapContinuousReadingMenu();
        }

        // Revision 90 removes the "shake to read" checkbox preference and
        // replaces it with a list preference of shake velocity thresholds that
        // includes a default "Off" option.
        if (previousVersion < 90) {
            remapShakeToReadPref();
        }

        // Revision 97 moved granularity selection into a local context menu, so
        // the up-then-down and down-then-up gestures were remapped to help
        // users navigate past groups of things, like web content and lists.
        if ((previousVersion != -1) && (previousVersion < 97)
                && (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)) {
            notifyUserOfBuiltInGestureChanges();
        }

        editor.commit();
    }

    /**
     * When a user updates TalkBack on a pre-MR0 release of Jelly Bean that had
     * accessibility enabled via the Setup Wizard, touch exploration will
     * unexpectedly stop working.
     * <p>
     * We need to check for the following to determine if a user is in this state:
     * <ol>
     * <li>Are we affected by this bug? => Currently running a pre-MR1 version of Jelly Bean
     * <li>Did we just trigger the bug? => Upgrading from the factory ROM version of TalkBack
     * <li>Was explore by touch previously enabled? => Has already run the TalkBack tutorial
     * </ol>
     */
    private boolean needsExploreByTouchHelper(int previousVersion) {
        return (Build.VERSION.SDK_INT == 16) && (previousVersion == 68)
                && !mSharedPreferences.getBoolean(TalkBackService.PREF_FIRST_TIME_USER, true);
    }

    /**
     * Displays a notification that TalkBack now offers customizable settings by
     * downloading the Accessibility Settings Manager application.
     */
    private void displaySettingsAvailableNotification() {
        final Intent launchMarketIntent = new Intent(Intent.ACTION_VIEW);
        launchMarketIntent.setData(
                Uri.parse(mService.getString(R.string.settings_manager_market_uri)));

        final PendingIntent pendingIntent = PendingIntent.getActivity(
                mService, 0, launchMarketIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final String ticker = mService.getString(R.string.title_talkback_settings_available);
        final String contentTitle = mService.getString(R.string.title_talkback_settings_available);
        final String contentText = mService.getString(R.string.message_talkback_settings_available);
        final Notification notification = new NotificationCompat.Builder(mService)
                .setSmallIcon(R.drawable.ic_stat_info)
                        .setTicker(ticker)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setWhen(System.currentTimeMillis())
                        .build();

        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.flags |= Notification.FLAG_AUTO_CANCEL | Notification.FLAG_ONGOING_EVENT;

        mNotificationManager.notify(SETTINGS_NOTIFICATION_ID, notification);
    }

    /**
     * Persists old default gestures (or preserves explicit user-defined
     * gestures) and posts the gesture change notification.
     */
    private void notifyUserOfGestureChanges() {
        final Editor editor = mSharedPreferences.edit();

        // Manually persist old defaults until the user acknowledges the change.
        deprecateStringPreference(editor, R.string.pref_shortcut_down_and_left_key,
                R.string.pref_deprecated_shortcut_down_and_left_default);
        deprecateStringPreference(editor, R.string.pref_shortcut_down_and_right_key,
                R.string.pref_deprecated_shortcut_down_and_right_default);
        deprecateStringPreference(editor, R.string.pref_shortcut_up_and_left_key,
                R.string.pref_deprecated_shortcut_up_and_left_default);
        deprecateStringPreference(editor, R.string.pref_shortcut_up_and_right_key,
                R.string.pref_deprecated_shortcut_up_and_right_default);

        // Flag that this user needs to get through the notification flow.
        editor.putBoolean(
                mService.getString(R.string.pref_must_accept_gesture_change_notification), true);

        editor.commit();

        // Build the intent for when the notification is clicked.
        final Intent notificationIntent = new Intent(
                mService, GestureChangeNotificationActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        NotificationPosterRunnable runnable = new NotificationPosterRunnable(
                buildGestureChangeNotification(notificationIntent), GESTURE_CHANGE_NOTIFICATION_ID);
        mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
    }

    private void notifyUserOfBuiltInGestureChanges() {
        // Build the intent for when the notification is clicked.
        final Intent notificationIntent = new Intent(
                mService, NotificationActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        notificationIntent.putExtra(NotificationActivity.EXTRA_INT_DIALOG_TITLE,
                R.string.notification_title_talkback_gestures_changed);
        notificationIntent.putExtra(NotificationActivity.EXTRA_INT_DIALOG_MESSAGE,
                R.string.talkback_built_in_gesture_change_details);
        notificationIntent.putExtra(NotificationActivity.EXTRA_INT_NOTIFICATION_ID,
                BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);

        NotificationPosterRunnable runnable = new NotificationPosterRunnable(
                buildGestureChangeNotification(notificationIntent),
                BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);
        mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
    }

    private Notification buildGestureChangeNotification(Intent clickIntent) {
        final PendingIntent pendingIntent = PendingIntent.getActivity(
                mService, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        final String ticker = mService.getString(
                R.string.notification_title_talkback_gestures_changed);
        final String contentTitle = mService.getString(
                R.string.notification_title_talkback_gestures_changed);
        final String contentText = mService.getString(
                R.string.notification_message_talkback_gestures_changed);
        final Notification notification = new NotificationCompat.Builder(mService)
                .setSmallIcon(R.drawable.ic_stat_info)
                        .setTicker(ticker)
                        .setContentTitle(contentTitle)
                        .setContentText(contentText)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(false)
                        .setWhen(0).build();

        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.flags |= Notification.FLAG_ONGOING_EVENT;
        return notification;
    }

    /**
     * Persist an old default String preference that the user has set or, if
     * none, the old default value. Note, this method does not commit the
     * Editor.
     *
     * @param editor The Editor for the given SharedPreferences
     * @param resIdPrefKey The resource ID of the preference key to update
     * @param deprecatedDefaultResId The old default value for the preference
     */
    private void deprecateStringPreference(
            Editor editor, int resIdPrefKey, int deprecatedDefaultResId) {
        final String key = mService.getString(resIdPrefKey);
        final String oldDefault = mService.getString(deprecatedDefaultResId);
        final String userSetOrOldDefault = mSharedPreferences.getString(key, oldDefault);

        editor.putString(key, userSetOrOldDefault);
    }

    /**
     * Replaces any user-defined gesture mappings that reference the continuous
     * reading menu with the local breakout menu.
     */
    private void remapContinuousReadingMenu() {
        final Editor editor = mSharedPreferences.edit();
        final String targetValue = "READ_ALL_BREAKOUT";
        final String replaceValue = "LOCAL_BREAKOUT";
        final int[] gestureKeys = {
                R.string.pref_shortcut_down_and_left_key,
                R.string.pref_shortcut_down_and_right_key,
                R.string.pref_shortcut_left_and_down_key,
                R.string.pref_shortcut_left_and_up_key,
                R.string.pref_shortcut_right_and_down_key,
                R.string.pref_shortcut_right_and_up_key,
                R.string.pref_shortcut_up_and_left_key,
                R.string.pref_shortcut_up_and_right_key };

        for (int key : gestureKeys) {
            final String prefKey = mService.getString(key);
            if (mSharedPreferences.getString(prefKey, "").equals(targetValue)) {
                editor.putString(prefKey, replaceValue);
            }
        }

        editor.commit();
    }

    /**
     * Handles the conversion from the check box preference to the list
     * preference for the shake to read feature. Users who have previously
     * enabled the shake to read feature will be switched to the medium velocity
     * threshold.
     */
    private void remapShakeToReadPref() {
        final boolean oldPrefOn = SharedPreferencesUtils.getBooleanPref(mSharedPreferences,
                mService.getResources(), R.string.pref_shake_to_read_key,
                R.bool.pref_shake_to_read_default);

        if (oldPrefOn) {
            final Editor editor = mSharedPreferences.edit();
            editor.putString(mService.getString(R.string.pref_shake_to_read_threshold_key),
                    mService.getString(R.string.pref_shake_to_read_threshold_conversion_default));
            editor.putBoolean(mService.getString(R.string.pref_shake_to_read_key), false);
            editor.commit();
        }
    }

    /**
     * Runnable used for posting notifications to the
     * {@link NotificationManager} after a short delay.
     */
    private class NotificationPosterRunnable implements Runnable {
        private Notification mNotification;
        private int mId;

        public NotificationPosterRunnable(Notification n, int id) {
            mNotification = n;
            mId = id;
        }

        @Override
        public void run() {
            mNotificationManager.notify(mId, mNotification);
        }
    }

    /**
     * Event listener that helps handle a bug when upgrading TalkBack from the
     * Jelly Bean factory ROM version (68) on device running the Jelly Bean
     * factory ROM version of Android.
     */
    private static class ExploreByTouchUpdateHelper
            implements TalkBackService.AccessibilityEventListener {
        private final TalkBackService mService;

        public ExploreByTouchUpdateHelper(TalkBackService service) {
            mService = service;
        }

        @Override
        public void onAccessibilityEvent(AccessibilityEvent event) {
            // If we're able to process this event, remove this listener from
            // the service (even if we failed).
            if (attemptToProcess(event)) {
                mService.postRemoveEventListener(this);
            }
        }

        /**
         * Attempts to process an {@link AccessibilityEvent}. If the event
         * represents the appearance of the Explore by Touch dialog, attempts to
         * automatically accept the dialog (since the user won't be able to) and
         * returns {@code true}.
         *
         * @param event An accessibility event.
         * @return {@code true} if the event represented the target dialog.
         */
        public boolean attemptToProcess(AccessibilityEvent event) {
            // Is this a dialog event?
            if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                return false;
            }

            // Can we retrieve the title text?
            final List<CharSequence> eventText = event.getText();
            if ((eventText == null) || (eventText.size() == 0)) {
                return false;
            }

            // Does the title text match what we're expecting?
            final String title = AutomationUtils.getInternalString(
                    mService, "enable_explore_by_touch_warning_title");
            final CharSequence eventTitle = event.getText().get(0);
            if (!TextUtils.equals(title, eventTitle)) {
                return false;
            }

            // Can we retrieve the source node?
            final AccessibilityRecordCompat record = new AccessibilityRecordCompat(event);
            final AccessibilityNodeInfoCompat root = record.getSource();
            if (root == null) {
                LogUtils.log(this, Log.INFO, "Missing event source node");
                return true;
            }

            // Attempt to find and click the button.
            final String targetClassName = "android.widget.Button";
            final String targetText = mService.getString(android.R.string.ok);
            if (!AutomationUtils.performActionOnView(mService, root, targetClassName, targetText,
                    AccessibilityNodeInfoCompat.ACTION_CLICK, null)) {
                LogUtils.log(this, Log.INFO, "Failed to click the button");
                return true;
            }

            return true;
        }
    }
}
