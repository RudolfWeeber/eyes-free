
package com.marvin.rocklock;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;
import android.util.Log;

public class BootHandlerService extends Service {
    private BroadcastReceiver screenwakeup = new BroadcastReceiver() {

        public static final String TAG = "screen wakeup";

        public static final String Screen = "android.intent.action.SCREEN_ON";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(Screen)) {
                return;
            }

            Log.e(TAG, Screen);

            Intent i = new Intent(context, RockLockActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        }
    };

    @Override
    public IBinder onBind(Intent arg0) {
        // Do nothing
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        this.startForeground(0, null);

        IntentFilter onfilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        this.registerReceiver(screenwakeup, onfilter);
        Log.e("BootHandlerService", "0");
    }

}
