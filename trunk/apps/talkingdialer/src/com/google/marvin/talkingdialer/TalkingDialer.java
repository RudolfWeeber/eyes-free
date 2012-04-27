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
    private static final int REQUEST_DIALER = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        // TODO: This is bad practice. The dialer activity should handle
        // launching the ACTION_CALL intent.
        final Intent intent = new Intent(this, SlideDial.class);
        startActivityForResult(intent, REQUEST_DIALER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_DIALER) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
            }

            final String phoneNumber = data.getStringExtra(SlideDial.EXTRA_NUMBER);
            final Uri phoneNumberUri = Uri.parse("tel:" + Uri.encode(phoneNumber));
            final Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberUri);

            startActivity(intent);
            finish();
        }
    }

}
