
package com.google.marvin.paw;


import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class WidgetInterface extends AppWidgetProvider {
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.e("widget", "onUpdate");

        // To prevent any ANR timeouts, we perform the update in a service
        context.startService(new Intent(context, UpdateService.class));
    }

    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (intent.getAction().indexOf("com.google.marvin.paw.idle") != -1) {
            RemoteViews myViews = buildUpdate(context);
            Resources res = context.getResources();

            myViews.setImageViewResource(R.id.image, R.drawable.android_idle_anim);
            
            ComponentName thisWidget = new ComponentName(context, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, myViews);
        } else if (intent.getAction().indexOf("com.google.marvin.paw.dizzy") != -1){
            RemoteViews myViews = buildUpdate(context);
            Resources res = context.getResources();

            myViews.setImageViewResource(R.id.image, R.drawable.android_dizzy_anim);
            
            ComponentName thisWidget = new ComponentName(context, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, myViews);
        } else if (intent.getAction().indexOf("com.google.marvin.paw.dance") != -1){
            RemoteViews myViews = buildUpdate(context);
            Resources res = context.getResources();

            myViews.setImageViewResource(R.id.image, R.drawable.android_dance_anim);
            
            ComponentName thisWidget = new ComponentName(context, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, myViews);
        } else if (intent.getAction().indexOf("com.google.marvin.androidsays.showScore") != -1){            
            Toast.makeText(context, "Your score is: " + intent.getStringExtra("score"), 1).show();
        }
    }

    /**
     * Build the widget here. Will block until the online API returns.
     */
    public static RemoteViews buildUpdate(Context context) {
        RemoteViews updateViews = null;
        updateViews = new RemoteViews(context.getPackageName(), R.layout.widget_contents_2x2);
        loadWidget(context, updateViews);
        return updateViews;
    }

    public static boolean loadWidget(Context context, RemoteViews targetRemoteView) {
        // Hard code this for now...
    	// TODO: Draw widget here
        Resources res = context.getResources();
        
        Intent i = new Intent("com.google.marvin.paw.startService");
        PendingIntent pi = PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        //targetRemoteView.setOnClickPendingIntent(R.id.button00, pi);

        targetRemoteView.setOnClickPendingIntent(R.id.image, pi);
        return true;
    }

    public static class UpdateService extends Service {
        @Override
        public void onStart(Intent intent, int startId) {
            Log.e("widget", "0");
            // Build the widget update for today
            RemoteViews updateViews = buildUpdate(this);

            // Push update for this widget to the home screen
            ComponentName thisWidget = new ComponentName(this, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }

        @Override
        public IBinder onBind(Intent intent) {
            // We don't need to bind to this service
            return null;
        }
    }
}
