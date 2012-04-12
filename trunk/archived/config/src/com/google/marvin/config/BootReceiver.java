package com.google.marvin.config;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    	Intent startMarvinIntent = new Intent(context, MarvinHomeScreen.class);
    	startMarvinIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	context.startActivity(startMarvinIntent);
    }
}