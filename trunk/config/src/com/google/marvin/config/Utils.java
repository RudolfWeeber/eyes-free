package com.google.marvin.config;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;

/**
 * A collection of commonly used utility functions.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */

public class Utils {
  public static boolean applicationInstalled(Context ctx, String packageName) {
    try {
      Context myContext = ctx.createPackageContext(packageName, 0);
    } catch (NameNotFoundException e) {
      return false;
    }
    return true;
  }

  public static Intent getMarketIntent(String packageName) {
    Uri marketUri = Uri.parse("market://search?q=pname:" + packageName);
    Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
    return marketIntent;
  }

  public static Intent getAppStartIntent(Context ctx, String packageName, String className) {
    Intent intent = null;
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = ctx.createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(className);
      intent = new Intent(myContext, appClass);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    } catch (NameNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } 
    return intent;
  }
}
