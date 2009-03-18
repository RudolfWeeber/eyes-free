package com.google.marvin.config;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Canvas;
import android.net.Uri;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

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
        Uri marketUri = Uri.parse("market://search?q=pname:" + packageName);
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
        ctx.startActivity(marketIntent);
      }
    });
  }

  public void updateApps(ArrayList<AppDesc> apps) {
    appDescs = new ArrayList<AppDesc>();
    for (int i = 0; i < apps.size(); i++) {
      AppDesc app = apps.get(i);
      if (!appAlreadyInstalled(app.getPackageName())) {
        appDescs.add(app);
      }
    }
    if (appDescs.size() < 1) {
      appDescs.add(new AppDesc("", "All eyes-free apps installed",
          "All applications currently available from the Eyes-Free project have been installed."));
    }
  }

  private boolean appAlreadyInstalled(String packageName) {
    try {
      Context myContext = ctx.createPackageContext(packageName, 0);
    } catch (NameNotFoundException e) {
      return false;
    }
    return true;
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
