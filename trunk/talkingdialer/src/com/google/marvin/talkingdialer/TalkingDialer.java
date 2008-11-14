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
