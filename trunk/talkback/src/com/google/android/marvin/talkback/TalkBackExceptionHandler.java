// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.talkback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.ref.WeakReference;
import java.util.Date;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public class TalkBackExceptionHandler implements UncaughtExceptionHandler {
    /** The filename used for logging crash reports. */
    private static final String TALKBACK_CRASH_LOG = "talkback_crash.log";

    /** Notification identifier for the crash report notification. */
    private static final int CRASH_NOTIFICATION_ID = 1;

    /** The email addresses that receive TalkBack crash reports. */
    private static final String[] CRASH_REPORT_EMAILS = {
        "eyes.free.crash.reports@gmail.com"
    };

    private final WeakReference<Context> mContext;
    private final UncaughtExceptionHandler mDefault;

    private String mLastSpokenEvent;

    public TalkBackExceptionHandler(Context context) {
        mContext = new WeakReference<Context>(context);
        mDefault = Thread.getDefaultUncaughtExceptionHandler();
    }

    public void register() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    public void unregister() {
        Thread.setDefaultUncaughtExceptionHandler(null);
    }

    public void setLastSpokenEvent(AccessibilityEvent event) {
        mLastSpokenEvent = event.toString();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        final Context context = mContext.get();

        if (context == null) {
            unregister();
            mDefault.uncaughtException(thread, ex);
            return;
        }

        final PackageManager packageManager = context.getPackageManager();

        int version;

        try {
            version = packageManager.getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            version = 0;
        }

        final StringBuilder stackTrace = new StringBuilder();
        final String timestamp = new Date().toString();
        final String androidOsVersion =
                String.format("%d - %s", Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL);
        final String deviceInfo =
                String.format("%s, %s, %s", Build.MANUFACTURER, Build.MODEL, Build.PRODUCT);
        final String talkBackVersion = String.format("%d", version);

        stackTrace.append(ex.toString());
        stackTrace.append('\n');

        for (StackTraceElement element : ex.getStackTrace()) {
            stackTrace.append(element.toString());
            stackTrace.append('\n');
        }

        final String report =
                context.getString(R.string.template_crash_report_message, timestamp,
                        androidOsVersion, deviceInfo, talkBackVersion, stackTrace, mLastSpokenEvent);

        try {
            final FileOutputStream fos =
                    context.openFileOutput(TALKBACK_CRASH_LOG, Context.MODE_APPEND);
            fos.write(report.getBytes());
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (mDefault != null) {
            mDefault.uncaughtException(thread, ex);
        }
    }

    /**
     * Checks the TalkBack crash log and prompts the user to submit reports if
     * appropriate.
     */
    public void processCrashReport() {
        final CharSequence crashReport = loadReport();

        if (crashReport != null) {
            displayNotification(crashReport);
        }
    }

    /**
     * Attempts to load the most recently saved crash report. Returns
     * {@code null} on failure.
     * 
     * @return The contents of the most recently saved crash report (or
     *         {@code null} on failure.
     */
    private CharSequence loadReport() {
        final Context context = mContext.get();

        if (context == null) {
            return null;
        }

        final String logPath = context.getFilesDir() + File.separator + TALKBACK_CRASH_LOG;
        final File logFile = new File(logPath);

        if (!logFile.exists()) {
            return null;
        }

        final StringBuilder crashReport = new StringBuilder();

        try {
            final FileReader fileReader = new FileReader(logFile);
            final BufferedReader reader = new BufferedReader(fileReader);

            String line;

            while ((line = reader.readLine()) != null) {
                crashReport.append(line);
                crashReport.append('\n');
            }

            reader.close();
        } catch (FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            return null;
        }

        logFile.delete();

        return crashReport;
    }

    /**
     * Displays a notification that TalkBack has crashed and data can be sent to
     * developers to help improve TalkBack.
     */
    private void displayNotification(CharSequence crashReport) {
        final Context context = mContext.get();

        if (context == null) {
            return;
        }

        final Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("plain/text");
        emailIntent.putExtra(Intent.EXTRA_EMAIL, CRASH_REPORT_EMAILS);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.subject_crash_report_email));
        emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
                context.getString(R.string.header_crash_report_email) + crashReport);

        final Notification notification =
                new Notification(android.R.drawable.stat_notify_error,
                        context.getString(R.string.title_talkback_crash),
                        System.currentTimeMillis());
        notification.setLatestEventInfo(context, context.getString(R.string.title_talkback_crash),
                context.getString(R.string.message_talkback_crash), PendingIntent.getActivity(
                        context, 0, emailIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        notification.defaults |= Notification.DEFAULT_SOUND;
        notification.flags |= Notification.FLAG_AUTO_CANCEL;

        final NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(CRASH_NOTIFICATION_ID, notification);
    }
}
