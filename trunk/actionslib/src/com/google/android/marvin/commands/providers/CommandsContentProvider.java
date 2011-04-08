// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.providers;

import com.google.android.marvin.commands.CommandsManager;
import com.google.android.marvin.commands.UtilityCommands;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class CommandsContentProvider extends ContentProvider {
    

    // debugging support
    private static final String TAG = "CommandsContentProvider";
    
    
    private static final String DATABASE_NAME = "commands.db";
    private static final String COMMANDS_TABLE = "commands";
    
   
    private static final int COMMANDS = 1;
    
    private static final UriMatcher sUriMatcher;
    private static final Map<String, String> sProjectionMap;
    
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(CommandsManager.AUTHORITY, COMMANDS_TABLE, COMMANDS);
        
        sProjectionMap = new HashMap<String, String>();
        sProjectionMap.put(CommandsManager.NAME_COLUMN, CommandsManager.NAME_COLUMN);
        sProjectionMap.put(CommandsManager.MODIFIER_KEY_COLUMN, 
                CommandsManager.MODIFIER_KEY_COLUMN);
        sProjectionMap.put(CommandsManager.KEY_CODE_COLUMN, CommandsManager.KEY_CODE_COLUMN);
        sProjectionMap.put(CommandsManager.ACTION_COLUMN, CommandsManager.ACTION_COLUMN);
    }
    
    private static class DatabaseHelper extends SQLiteOpenHelper {
        
        public DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {
            StringBuilder builder = new StringBuilder();
            builder.append("CREATE TABLE ");
            builder.append(COMMANDS_TABLE);
            builder.append(" (");
            builder.append("_id INTEGER PRIMARY KEY AUTOINCREMENT");
            builder.append(", ");
            builder.append(CommandsManager.NAME_COLUMN);
            builder.append(" TEXT"); 
            builder.append(", ");
            builder.append(CommandsManager.MODIFIER_KEY_COLUMN);
            builder.append(" INTEGER");
            builder.append(", ");
            builder.append(CommandsManager.KEY_CODE_COLUMN);
            builder.append(" INTEGER");
            builder.append(", ");
            builder.append(CommandsManager.ACTION_COLUMN);
            builder.append(" TEXT)");
            db.execSQL(builder.toString());
            
            initContent(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + COMMANDS_TABLE);
            onCreate(db);
        }
        
        /**
         * Initialize the database with the command entries from UserCommands.
         */
        private void initContent(SQLiteDatabase db) {
            for (UtilityCommands command : UtilityCommands.values()) {
                ContentValues values = new ContentValues();
                values.put(CommandsManager.NAME_COLUMN, command.getDisplayName());
                values.put(CommandsManager.MODIFIER_KEY_COLUMN, command.getModifier());
                values.put(CommandsManager.KEY_CODE_COLUMN, command.getKeyCode());
                values.put(CommandsManager.ACTION_COLUMN, command.getAction());
                db.insert(COMMANDS_TABLE, CommandsManager.NAME_COLUMN, values);
            }
        }
    }
    
    private DatabaseHelper mDatabaseHelper;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        try {
            SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
            int count = 0;
            switch (sUriMatcher.match(uri)) {
                case COMMANDS:
                    count = db.delete(COMMANDS_TABLE, selection, selectionArgs);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Uri " + uri);
            }
            getContext().getContentResolver().notifyChange(uri, null);
            return count; 
        } catch (SQLException e) {
            Log.e(TAG, "Failed to access database.", e);
            return 0;
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case COMMANDS:
                return "vnd.android.cursor.item/vnd.google.commands";
            default:
                throw new IllegalArgumentException("Unknown Uri " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        try {
            if (sUriMatcher.match(uri) != COMMANDS) { 
                throw new IllegalArgumentException("Unknown URI " + uri); 
            }
            ContentValues values;
            if (initialValues != null) {
                values = new ContentValues(initialValues);
            } else {
                values = new ContentValues();
            }
     
            SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
            long rowId = db.insert(COMMANDS_TABLE, CommandsManager.NAME_COLUMN, values);
            if (rowId > 0) {
                Uri noteUri = ContentUris.withAppendedId(CommandsManager.CONTENT_URI, rowId);
                getContext().getContentResolver().notifyChange(noteUri, null);
                return noteUri;
            }
        } catch (SQLException e) {
            Log.e(TAG, "Failed to insert row into " + uri, e);
        }
        return null;
    }

    @Override
    public boolean onCreate() {
        try {
            mDatabaseHelper = new DatabaseHelper(getContext());
        } catch (SQLException e) {
            Log.e(TAG, "Failed to access database.", e);
            return false;
        }
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        try {
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            switch (sUriMatcher.match(uri)) {
                case COMMANDS:
                    builder.setTables(COMMANDS_TABLE);
                    builder.setProjectionMap(sProjectionMap);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Uri " + uri);
            }
            SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
            Cursor c = builder.query(db, projection, selection, selectionArgs, null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to access database.", e);
            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        try {
            SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
            int count;
            switch (sUriMatcher.match(uri)) {
                case COMMANDS:
                    count = db.update(COMMANDS_TABLE, values, selection, selectionArgs);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown Uri " + uri);
            }
            getContext().getContentResolver().notifyChange(uri, null);
            return count;
        } catch (SQLException e) {
            Log.e(TAG, "Failed to access database.", e);
            return 0;
        }
    }

}
