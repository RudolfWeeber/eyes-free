/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.marvin.home;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;

/**
 * Alternate home screen that dispatches to the actual shell home
 * screen replacement.
 *
 * @author sdoyon@google.com (Stephane Doyon)
 */

public class MarvinHomeScreen extends Activity {
  public final String packageName = "com.google.marvin.shell";
  public final String shellClass = "MarvinShell";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    try {
      super.onCreate(savedInstanceState);
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(packageName + "." + shellClass);
      Intent intent = new Intent(myContext, appClass);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      startActivity(intent);
      finish();
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }
}
