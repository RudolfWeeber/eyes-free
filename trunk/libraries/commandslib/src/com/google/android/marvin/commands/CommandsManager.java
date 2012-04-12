// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * A utility class for accessing the collection of available commands.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class CommandsManager {
    
    /**
     * The action for a broadcast that will announce changes to the available commands or command
     * shortcuts.
     */
    public static final String COMMAND_UPDATE_ACTION = "com.google.android.marvin.commands.UPDATE";
    
    public static final String NAME_COLUMN = "name";
    public static final String MODIFIER_KEY_COLUMN = "modifierKey";
    public static final String KEY_CODE_COLUMN = "keyCode";
    public static final String ACTION_COLUMN = "action";
    
    public static final String AUTHORITY = 
        "com.google.android.marvin.commands.providers.CommandsContentProvider";

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/commands");
    
    private static final String TAG = "CommandsAccessor";
    
    /**
     * Get a list of all available commands.
     */
    public List<Command> getAvailableCommands(Context context) {
        List<Command> availableCommands = new ArrayList<Command>();
        
        ContentResolver resolver = context.getContentResolver();
        
        Cursor cursor = resolver.query(CONTENT_URI, null, null, null, null);
        if (cursor == null) {
            Log.d(TAG, "No commands content provider found.");
            return availableCommands;
        }
        if (cursor.moveToFirst()) {
            int nameColumn = cursor.getColumnIndex("name");
            int modifierKeyColumn = cursor.getColumnIndex("modifierKey");
            int keyCodeColumn = cursor.getColumnIndex("keyCode");
            int actionColumn = cursor.getColumnIndex("action");
            do {
                Command command = new CommandImpl(
                        cursor.getString(nameColumn),
                        cursor.getInt(modifierKeyColumn),
                        cursor.getInt(keyCodeColumn),
                        cursor.getString(actionColumn));
                availableCommands.add(command);
            } while (cursor.moveToNext());
        }
        cursor.close();
        Log.d(TAG, availableCommands.toString());
        return availableCommands;
    }
    
    /**
     * For each Command in {@code commands}, add to the content provider if it does not already
     * have it.
     * @param commands
     */
    public void addCommands(List<Command> commands, Context context) {
        for (Command command : commands) {
            addCommand(context, command, false);
        }
    }

    protected void addCommand(Context context, Command command, boolean update) {
        ContentResolver resolver = context.getContentResolver();

        String[] projection = new String[] { NAME_COLUMN };
        
        StringBuilder selection = new StringBuilder()
            .append(NAME_COLUMN)
            .append("=\"")
            .append(command.getDisplayName())
            .append("\" AND ")
            .append(ACTION_COLUMN)
            .append("=\"")
            .append(command.getAction())
            .append("\"");
        
        Cursor cursor = resolver.query(
            CONTENT_URI, 
            projection, 
            selection.toString(),
            null,
            null);
        if (cursor == null) {
            Log.d(TAG, "No commands content provider found.");
            return;
        }
        ContentValues values = new ContentValues();
        values.put(NAME_COLUMN, command.getDisplayName());
        values.put(MODIFIER_KEY_COLUMN, command.getModifier());
        values.put(KEY_CODE_COLUMN, command.getKeyCode());
        values.put(ACTION_COLUMN, command.getAction());
        if (!cursor.moveToNext()) {
            // need to add command
            resolver.insert(CONTENT_URI, values);
        } else if (update) {
            resolver.update(CONTENT_URI, values, selection.toString(), new String[] { });
        }
        cursor.close();
    }
    

    public void updateCommandShortcut(Context context, Command command) {
        addCommand(context, command, true);
        Intent updateIntent = new Intent(COMMAND_UPDATE_ACTION);
        context.sendBroadcast(updateIntent);
    }


}
