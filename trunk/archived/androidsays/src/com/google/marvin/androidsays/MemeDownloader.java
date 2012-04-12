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
package com.google.marvin.androidsays;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;

/**
 * Handles the downloading of theme files for mem.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class MemeDownloader extends Activity {
  private String dataSource;


  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    dataSource = this.getIntent().getData().toString();
    setContentView(R.layout.main);
    (new Thread(new loader())).start();
  }

  public void runMem() {
    startApp("com.google.marvin.androidsays", "AndroidSays");
    finish();
  }

  private void startApp(String packageName, String className) {
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(packageName + "." + className);
      Intent intent = new Intent(myContext, appClass);
      startActivity(intent);
    } catch (NameNotFoundException e) {
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
  }

  public class loader implements Runnable {
    public void run() {
      Unzipper.unzip(dataSource);
      runMem();
    }

  }


}
