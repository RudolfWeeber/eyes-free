package com.google.marvin.config;

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
