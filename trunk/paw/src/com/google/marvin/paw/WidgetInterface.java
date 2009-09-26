package com.google.marvin.paw;

import org.htmlparser.parserapplications.StringExtractor;
import org.htmlparser.util.ParserException;

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
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class WidgetInterface extends AppWidgetProvider {
	Context ctx;

	public void onUpdate(Context context, AppWidgetManager appWidgetManager,
			int[] appWidgetIds) {
		ctx = context;

		Log.e("widget", "onUpdate");

		// To prevent any ANR timeouts, we perform the update in a service
		context.startService(new Intent(context, UpdateService.class));
	}

	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);

		ctx = context;

		if (intent.getAction().indexOf("com.google.marvin.paw.idle") != -1) {
			RemoteViews myViews = buildUpdate(context);
			Resources res = context.getResources();

			myViews.setImageViewResource(R.id.image,
					R.drawable.android_idle_anim);

			ComponentName thisWidget = new ComponentName(context,
					WidgetInterface.class);
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			manager.updateAppWidget(thisWidget, myViews);
		} else if (intent.getAction().indexOf("com.google.marvin.paw.dizzy") != -1) {
			RemoteViews myViews = buildUpdate(context);
			Resources res = context.getResources();

			myViews.setImageViewResource(R.id.image,
					R.drawable.android_dizzy_anim);

			ComponentName thisWidget = new ComponentName(context,
					WidgetInterface.class);
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			manager.updateAppWidget(thisWidget, myViews);
		} else if (intent.getAction().indexOf("com.google.marvin.paw.dance") != -1) {
			RemoteViews myViews = buildUpdate(context);
			Resources res = context.getResources();

			myViews.setImageViewResource(R.id.image,
					R.drawable.android_dance_anim);

			ComponentName thisWidget = new ComponentName(context,
					WidgetInterface.class);
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			manager.updateAppWidget(thisWidget, myViews);
		} else if (intent.getAction().indexOf("com.google.marvin.paw.sleep") != -1) {
			RemoteViews myViews = buildUpdate(context);
			Resources res = context.getResources();

			myViews.setImageViewResource(R.id.image, R.drawable.sleep_anim);

			ComponentName thisWidget = new ComponentName(context,
					WidgetInterface.class);
			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			manager.updateAppWidget(thisWidget, myViews);
		} else if (intent.getAction().indexOf(
				"com.google.marvin.paw.action.websearch") != -1) {
			// getSearchResults("weather san jose");
			getSearchResults(intent.getStringExtra("query"));
		}
	}

	/**
	 * Build the widget here. Will block until the online API returns.
	 */
	public static RemoteViews buildUpdate(Context context) {
		RemoteViews updateViews = null;
		updateViews = new RemoteViews(context.getPackageName(),
				R.layout.widget_contents_2x2);
		loadWidget(context, updateViews);
		return updateViews;
	}

	public static boolean loadWidget(Context context,
			RemoteViews targetRemoteView) {
		// Hard code this for now...
		// TODO: Draw widget here
		Resources res = context.getResources();

		Intent i = new Intent("com.google.marvin.paw.startService");
		PendingIntent pi = PendingIntent.getActivity(context, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT);
		// targetRemoteView.setOnClickPendingIntent(R.id.button00, pi);

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
			ComponentName thisWidget = new ComponentName(this,
					WidgetInterface.class);
			AppWidgetManager manager = AppWidgetManager.getInstance(this);
			manager.updateAppWidget(thisWidget, updateViews);
		}

		@Override
		public IBinder onBind(Intent intent) {
			// We don't need to bind to this service
			return null;
		}
	}

	public void getSearchResults(String query) {
		(new Thread(new searchResultsFetcher(query))).start();
	}

	class searchResultsFetcher implements Runnable {
		String q;

		public searchResultsFetcher(String query) {
			q = query;
		}

		public void run() {
			// Intent i = new Intent("android.intent.action.VIEW",
			// Uri.parse("http://www.google.com/m?q=" + q));
			// i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			// ctx.startActivity(i);

			String contents = OneBoxScraper.processGoogleResults(q);
			if (contents.length() > 0) {
				Intent synthIntent = new Intent("com.google.marvin.paw.doSynth");
				synthIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				synthIntent.putExtra("message", contents);
				ctx.startActivity(synthIntent);
			} else {
				Intent i = new Intent("android.intent.action.VIEW", Uri
						.parse("http://www.google.com/m?q=" + q));
				i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				ctx.startActivity(i);
			}

		}
	}

}
