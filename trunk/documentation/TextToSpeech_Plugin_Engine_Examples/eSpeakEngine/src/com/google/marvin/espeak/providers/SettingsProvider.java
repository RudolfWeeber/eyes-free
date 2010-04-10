
package com.google.marvin.espeak.providers;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;

public class SettingsProvider extends ContentProvider {
    public static final Uri CONTENT_URI = Uri
            .parse("content://com.google.marvin.espeak.providers.settingsprovider");

    private class SettingsCursor extends MatrixCursor {
        private String settings;

        public SettingsCursor(String[] columnNames) {
            super(columnNames);
            // TODO Auto-generated constructor stub
        }

        public void putSettings(String settings) {
            this.settings = settings;
        }

        @Override
        public int getCount() {
            return 1;
        }

        @Override
        public String getString(int column) {
            return settings;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String[] dummyColumns = {
                "", ""
        };
        SettingsCursor cursor = new SettingsCursor(dummyColumns);
        cursor.putSettings(Environment.getExternalStorageDirectory() + "/espeakdata/");
        return cursor;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

}
