package com.google.marvin.config;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * A ListView for dealing with applications that can be installed.
 * This list will filter out already installed apps and not display them.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class AppListView extends ListView {
  private AppListAdapter appListAdapter;
  private ArrayList<AppDesc> appDescs;
  private Context ctx;

  public AppListView(Context context) {
    super(context);
    ctx = context;

    ArrayList<AppDesc> appDescs = new ArrayList<AppDesc>();
    AppDesc dummyApp = new AppDesc("", "", "");
    appDescs.add(dummyApp);
    appListAdapter = new AppListAdapter(ctx, appDescs);
    setAdapter(appListAdapter);

    setOnItemClickListener(new OnItemClickListener() {
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long rowId) {
        String packageName = appListAdapter.getPackageName((int) rowId);
        ctx.startActivity(Utils.getMarketIntent(packageName));
      }
    });
  }

  public void updateApps(ArrayList<AppDesc> apps) {
    appDescs = new ArrayList<AppDesc>();
    for (int i = 0; i < apps.size(); i++) {
      AppDesc app = apps.get(i);
      if (!Utils.applicationInstalled(ctx, app.getPackageName())) {
        appDescs.add(app);
      }
    }
    if (appDescs.size() < 1) {
      appDescs.add(new AppDesc("", ctx.getString(R.string.allAppsInstalledTitle), ctx.getString(R.string.allAppsInstalledMessage)));
    }
  }

  @Override
  protected void onDraw(Canvas canvas) {
    // Only try to update the adapter if there are new apps to update it with.
    if (appDescs != null) {
      appListAdapter = null;
      appListAdapter = new AppListAdapter(ctx, appDescs);
      setAdapter(appListAdapter);
      appDescs = null;
    }
    super.onDraw(canvas);
  }

}
