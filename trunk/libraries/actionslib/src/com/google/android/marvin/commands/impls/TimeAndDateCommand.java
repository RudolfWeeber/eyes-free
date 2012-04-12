// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import com.google.android.marvin.actionslib.R;
import com.google.android.marvin.commands.CommandExecutor;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * A command to speak the current time and date.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class TimeAndDateCommand implements CommandExecutor {

    @Override
    public String executeCommand(Context context) {

        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM");
        String monthStr = monthFormat.format(cal.getTime());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minutes = cal.get(Calendar.MINUTE);
        String ampm = "";
        if (hour == 0) {
            ampm = context.getString(R.string.midnight);
            hour = 12;
        } else if (hour == 12) {
            ampm = context.getString(R.string.noon);
        } else if (hour > 12) {
            hour = hour - 12;
            ampm = context.getString(R.string.pm);
        } else {
            ampm = context.getString(R.string.am);
        }
        String timeStr = Integer.toString(hour) + " " + Integer.toString(minutes) + " " + ampm;

        return timeStr + " " + monthStr + " " + Integer.toString(day);
    }

}
