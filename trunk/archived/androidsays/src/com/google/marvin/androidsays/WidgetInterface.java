
package com.google.marvin.androidsays;

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

        if (intent.getAction().indexOf("com.google.marvin.androidsays.flash.0") != -1) {
            RemoteViews myViews = buildUpdate(context);
            Resources res = context.getResources();

            if (myViews != null) {
                int buttonNumber = Integer.parseInt(intent.getAction().replaceAll(
                        "com.google.marvin.androidsays.flash.0", ""));
                Bitmap theImage = BitmapFactory.decodeResource(res, R.drawable.mini_flash);
                theImage = Bitmap.createBitmap(theImage);
                if (buttonNumber == 0) {
                    myViews.setImageViewBitmap(R.id.button00, theImage);
                } else if (buttonNumber == 1) {
                    myViews.setImageViewBitmap(R.id.button01, theImage);
                } else if (buttonNumber == 2) {
                    myViews.setImageViewBitmap(R.id.button02, theImage);
                } else {
                    myViews.setImageViewBitmap(R.id.button03, theImage);
                }
            }
            ComponentName thisWidget = new ComponentName(context, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, myViews);
        } else if (intent.getAction().indexOf("com.google.marvin.androidsays.unflash") != -1){
            Log.e("Widget", "unflash started");
            RemoteViews myViews = buildUpdate(context);
            Resources res = context.getResources();
            myViews.setImageViewResource(R.id.button00, R.drawable.mini_green);
            myViews.setImageViewResource(R.id.button01, R.drawable.mini_red);
            myViews.setImageViewResource(R.id.button02, R.drawable.mini_yellow);
            myViews.setImageViewResource(R.id.button03, R.drawable.mini_blue);  
            ComponentName thisWidget = new ComponentName(context, WidgetInterface.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(thisWidget, myViews);
            Log.e("Widget", "unflash finished");
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
        Resources res = context.getResources();

        Bitmap theImage = BitmapFactory.decodeResource(res, R.drawable.mini_green);
        theImage = Bitmap.createBitmap(theImage);
        targetRemoteView.setImageViewBitmap(R.id.button00, theImage);
        Intent i = new Intent("com.google.marvin.androidsays.00");
        PendingIntent pi = PendingIntent.getActivity(context, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT);
        targetRemoteView.setOnClickPendingIntent(R.id.button00, pi);

        theImage = BitmapFactory.decodeResource(res, R.drawable.mini_red);
        theImage = Bitmap.createBitmap(theImage);
        targetRemoteView.setImageViewBitmap(R.id.button01, theImage);
        i = new Intent("com.google.marvin.androidsays.01");
        pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        targetRemoteView.setOnClickPendingIntent(R.id.button01, pi);

        theImage = BitmapFactory.decodeResource(res, R.drawable.mini_yellow);
        theImage = Bitmap.createBitmap(theImage);
        targetRemoteView.setImageViewBitmap(R.id.button02, theImage);
        i = new Intent("com.google.marvin.androidsays.02");
        pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        targetRemoteView.setOnClickPendingIntent(R.id.button02, pi);

        theImage = BitmapFactory.decodeResource(res, R.drawable.mini_blue);
        theImage = Bitmap.createBitmap(theImage);
        targetRemoteView.setImageViewBitmap(R.id.button03, theImage);
        i = new Intent("com.google.marvin.androidsays.03");
        pi = PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        targetRemoteView.setOnClickPendingIntent(R.id.button03, pi);

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
