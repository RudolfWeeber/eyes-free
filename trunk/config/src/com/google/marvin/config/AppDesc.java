package com.google.marvin.config;

/**
 * Class that holds the package name, title, and description of an app.
 * These are used to display info about the app to the user and to go to
 * the app on Market.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class AppDesc {
  private String packageName;
  private String title;
  private String description;
  
  public AppDesc(String packageName, String title, String description){
    this.packageName = packageName;
    this.title = title;
    this.description = description;
  }
  
  public String getPackageName(){
    return packageName;
  }
  
  public String getTitle(){
    return title;
  }
  
  public String getDescription(){
    return description;
  }
  
}
