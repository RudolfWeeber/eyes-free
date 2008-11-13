// Copyright 2008 Google Inc. All Rights Reserved.



package com.google.marvin.talkingdialer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Talking dialer for eyes-free dialing
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkingDialer extends Activity {

  private final int reqCode = 1;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    setContentView(R.layout.main);
    Intent intent = new Intent(this, SlideDial.class);
    startActivityForResult(intent, reqCode);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == reqCode) {
      if (resultCode == Activity.RESULT_CANCELED) {
        finish();
        return;
      }
      String dataStr = data.getData().toString();
      String phoneNumber = Uri.decode(dataStr);
      Uri phoneNumberURI = Uri.parse("tel:" + phoneNumber);
      Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberURI);
      startActivity(intent);
      finish();
    }
  }

}
