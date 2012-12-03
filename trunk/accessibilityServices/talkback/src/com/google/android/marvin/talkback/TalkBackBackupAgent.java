package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

/**
 * Persists TalkBack preferences to the cloud.
 */
@TargetApi(8)
public class TalkBackBackupAgent extends BackupAgentHelper {
    private static final String PREFS_DEFAULT = "com.google.android.marvin.talkback";

    private static final String KEY_PREFS_BACKUP = "prefsBackup";

    @Override
    public void onCreate() {
        final SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(
                this, PREFS_DEFAULT);

        addHelper(KEY_PREFS_BACKUP, helper);
    }
}
