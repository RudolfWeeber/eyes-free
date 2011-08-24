/*
 * Copyright (C) 2010 Google Inc.
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

package com.googlecode.eyesfree.walkytalky;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Generates status notifications of the user's position as they walk.
 * 
 * @author clchen@google.com (Charles L. Chen)
 * @author hiteshk@google.com (Hitesh Khandelwal)
 */

public class WalkyTalky extends Activity {
    /** Request code for NewLocationActivity. */
    private static final int NEW_LOCATION_REQUEST = 0;

    /** Tag used for logging. */
    public static final String TAG = "WalkyTalky";

    private WalkyTalky self;
    
    private BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            startNotifications();
        }
    };

    @Override
    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;

        registerReceiver(mScreenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    private void startNotifications() {
        // Start Position Notification service
        Intent iPSNS = new Intent(self, PositionStatusNotificationService.class);
        iPSNS.putExtra("ACTION", "Start");
        iPSNS.putExtra("SPEAK", false);
        iPSNS.putExtra("POST_MESSAGE", true);
        self.startService(iPSNS);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Start New location activity
        Intent newLoc = new Intent(self, NewLocationActivity.class);
        newLoc.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivityIfNeeded(newLoc, NEW_LOCATION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NEW_LOCATION_REQUEST && (resultCode == RESULT_OK)) {
            launchDriveAbout(data.getStringExtra("LOC"));
        } else if (requestCode == NEW_LOCATION_REQUEST && (resultCode == RESULT_FIRST_USER)) {
            // Back button pressed in NewLocationActivity
            finish();
        }
    }

    private void launchDriveAbout(String destination) {
        String dest = destination;
        try {
            dest = URLEncoder.encode(destination, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Intent iNavigate = new Intent("android.intent.action.VIEW");
        iNavigate.setData(Uri.parse("http://maps.google.com/maps?myl=saddr&daddr=" + dest
                + "&dirflg=w&nav=1"));
        iNavigate.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        iNavigate.setClassName("com.google.android.apps.maps",
                "com.google.android.maps.driveabout.app.NavigationActivity");

        // Make DriveAbout do a warning chime before it speaks a direction
        iNavigate.putExtra("CHIME_BEFORE_SPEECH", true);
        try {
            startActivity(iNavigate);
        } catch (ActivityNotFoundException e) {
            // you need to install driveabout
            AlertDialog alertDialog = new AlertDialog.Builder(this).create();
            alertDialog.setTitle("Google Maps required");
            alertDialog.setMessage("Google Maps navigation is required to use this functionality.");
            alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        }

        startNotifications();
    }

    @Override
    public void onDestroy() {
        Intent i = new Intent(self, PositionStatusNotificationService.class);
        i.putExtra("ACTION", "STOP");
        self.startService(i);
        unregisterReceiver(mScreenOffReceiver);
        super.onDestroy();
    }
}
