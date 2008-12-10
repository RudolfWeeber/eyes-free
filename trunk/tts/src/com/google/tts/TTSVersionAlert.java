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
package com.google.tts;

import android.app.Activity;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;

/**
 * Creates an alert message that gives the user the option to install the
 * missing TTS that is needed through the Android Market.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TTSVersionAlert extends Builder {
  // These strings must be in the Java file itself in order for this to be
  // packed into a .jar file.
  private final static String NO_TTS = 
    "Please install the required TTS before running this application.";
  private final static String MARKET_URI = "market://details?id=-8633716544499463376";
  private final static String INSTALL_TTS = "Install the TTS";
  private final static String QUIT = "Quit";
  
  private Activity parent;

  public TTSVersionAlert(Context context) {
    super(context);
    parent = (Activity) context;
    setMessage(NO_TTS);


    OnClickListener installListener = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Uri marketUri = Uri.parse(MARKET_URI);
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
        parent.startActivity(marketIntent);
        parent.finish();
      }
    };

    OnClickListener quitListener = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        parent.finish();
      }
    };

    setPositiveButton(INSTALL_TTS, installListener);
    setNegativeButton(QUIT, quitListener);
  }

}
