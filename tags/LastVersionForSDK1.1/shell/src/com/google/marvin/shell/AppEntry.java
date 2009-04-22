package com.google.marvin.shell;

import java.util.ArrayList;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

public class AppEntry {
  private String title;
  private String packageName;
  private String className;
  private Drawable icon;
  private ArrayList<Param> params;
  private ResolveInfo rInfo;

  AppEntry(String appTitle, String appPackageName, String appClassName, Drawable appIcon,
      ArrayList<Param> parameters) {
    title = appTitle;
    packageName = appPackageName;
    className = appClassName;
    icon = appIcon;
    params = parameters;
  }

  AppEntry(String appTitle, ResolveInfo info, ArrayList<Param> parameters) {
    title = appTitle;
    packageName = null;
    className = null;
    icon = null;
    rInfo = info;
    params = parameters;
  }

  String getTitle() {
    return title;
  }

  String getPackageName() {
    if (packageName != null) {
      return packageName;
    }
    return rInfo.activityInfo.packageName;
  }

  String getClassName() {
    if (className != null) {
      return className;
    }
    return rInfo.activityInfo.name;
  }

  Drawable getIcon(PackageManager pm) {
    if (icon != null) {
      return icon;
    }
    return rInfo.loadIcon(pm);
  }

  ArrayList<Param> getParams() {
    return params;
  }

}
