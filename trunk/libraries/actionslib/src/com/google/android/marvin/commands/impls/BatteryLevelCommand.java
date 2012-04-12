// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import com.google.android.marvin.actionslib.R;
import com.google.android.marvin.commands.CommandExecutor;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

/**
 * A command to speak the current battery level.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class BatteryLevelCommand implements CommandExecutor {
    
    public String executeCommand(Context context) {
        IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent intent = context.getApplicationContext().registerReceiver(null, battFilter);

        int rawlevel = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        int status = intent.getIntExtra("status", -1);
        String message = "";
        if (rawlevel >= 0 && scale > 0) {
            int batteryLevel = (rawlevel * 100) / scale;
            message = Integer.toString(batteryLevel) + "%";
        }
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
            message = message + " " + context.getString(R.string.charging);
        }
        return message;
    }
}
