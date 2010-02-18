package com.marvin.rocklock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenOnReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
    	Intent i = new Intent(context, RockLockActivity.class);
    	i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	context.startActivity(i);
    }
}