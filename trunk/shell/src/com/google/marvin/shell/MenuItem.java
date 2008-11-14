package com.google.marvin.shell;

/**
 * Holds the information for an application in the shell
 * that is needed to start that application from the shell.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MenuItem {
  public String title;
  public String packageName;
  public String className;
  
  public MenuItem(String appTitle, String packagename, String classname){
    title = appTitle;
    packageName = packagename;
    className = classname;
  }
}
