// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands;


import com.google.android.marvin.commands.impls.BatteryLevelCommand;
import com.google.android.marvin.commands.impls.ConnectivityCommand;
import com.google.android.marvin.commands.impls.TimeAndDateCommand;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * The broadcast receiver that will process the commands defined in {@link UtilityCommands}.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class UtilityCommandsBroadcastReceiver extends BroadcastReceiver {
    
    private static final String TAG = "UserEventBroadcastReceiver";

    private Map<String, UtilityCommands> mIdToUserCommands;
    
    private Map<String, CommandExecutor> mIdToExecutor;
    
    private AccessibilityManager mAccessibilityManager;
    
    public UtilityCommandsBroadcastReceiver() {
        mIdToUserCommands = new HashMap<String, UtilityCommands>();
        for (UtilityCommands command : UtilityCommands.values()) {
            mIdToUserCommands.put(command.getDisplayName(), command);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mAccessibilityManager == null){
            mAccessibilityManager =
                (AccessibilityManager)context.getSystemService(Service.ACCESSIBILITY_SERVICE);
        }
        if (!mAccessibilityManager.isEnabled()) {
            return;
        }
        
        if (mIdToExecutor == null) {
            Log.e(TAG, "Creating new command handlers");
            mIdToExecutor = new HashMap<String, CommandExecutor>();
            mIdToExecutor.put(
                    UtilityCommands.BATTERY.getDisplayName(), 
                    new BatteryLevelCommand());
            mIdToExecutor.put(
                    UtilityCommands.TIME.getDisplayName(), 
                    new TimeAndDateCommand());
//            mIdToExecutor.put(
//                    UtilityCommands.LOCATION.getDisplayName(), 
//                    new LocationCommand());
            mIdToExecutor.put(
                    UtilityCommands.CONNECTIVITY.getDisplayName(), 
                    new ConnectivityCommand());
        }
        
        String userEventName = intent.getStringExtra(CommandConstants.EXTRA_COMMAND_NAME);
        Log.i(TAG, "got event " + userEventName);
        CommandExecutor command = mIdToExecutor.get(userEventName);
        if (command == null) {
            Log.e(TAG, "Unimplemented command " + userEventName);
        }
        String text = command.executeCommand(context);
        speakText(context, text);
    }
    
    private void speakText(Context context, String text) {
        Log.d(TAG, text);
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        event.setClassName(getClass().getCanonicalName());
        event.setPackageName(getClass().getPackage().getName());
        event.setEventTime(new Date().getTime());
        event.getText().add(text);
        
        mAccessibilityManager.sendAccessibilityEvent(event);
    }
}
