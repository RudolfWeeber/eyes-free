// Copyright 2008 Google Inc. All Rights Reserved.
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
  private Activity parent;

  public TTSVersionAlert(Context context) {
    super(context);
    parent = (Activity) context;
    setMessage(R.string.NO_TTS);


    OnClickListener installListener = new OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        Uri marketUri = Uri.parse(parent.getString(R.string.MARKET_URI));
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

    setPositiveButton(R.string.INSTALL_TTS, installListener);
    setNegativeButton(R.string.QUIT, quitListener);
  }

}
