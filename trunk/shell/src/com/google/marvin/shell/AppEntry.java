package com.google.marvin.shell;

import java.util.ArrayList;

import android.graphics.drawable.Drawable;

public class AppEntry {
  private String title;
  private String packageName;
  private String className;
  private Drawable icon;
  private ArrayList<Param> params;

  AppEntry(String appTitle, String appPackageName, String appClassName, Drawable appIcon, ArrayList<Param> parameters){
    title = appTitle;
    packageName = appPackageName;
    className = appClassName;
    icon = appIcon;
    params = parameters;
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

  ArrayList<Param> getParams() {
    return params;
  }

}


