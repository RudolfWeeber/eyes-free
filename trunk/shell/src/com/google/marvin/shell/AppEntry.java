package com.google.marvin.shell;

import android.graphics.drawable.Drawable;

public class AppEntry {
  private String title;
  private String packageName;
  private String className;
  private Drawable icon;

  AppEntry(String appTitle, String appPackageName, String appClassName, Drawable appIcon) {
    title = appTitle;
    packageName = appPackageName;
    className = appClassName;
    icon = appIcon;
  }

  String getTitle() {
    return title;
  }

  String getPackageName() {
    return packageName;
  }

  String getClassName() {
    return className;
  }

  Drawable getIcon() {
    return icon;
  }


}
