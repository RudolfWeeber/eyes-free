/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.whereabout.whereami;

import com.whereabout.location.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Launcher extends Activity {

    private WifiManager wifi;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.launcher);

        Button mappingButton = (Button) findViewById(R.id.launcher_mapping);
        mappingButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent();
                i.setClass(Launcher.this, MappingDemoActivity.class);
                startActivity(i);
            }
        });

        Button locationButton = (Button) findViewById(R.id.launcher_localization);
        locationButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent();
                i.setClass(Launcher.this, LocationDemoActivity.class);
                startActivity(i);
            }
        });

        /*Button testingButton = (Button) findViewById(R.id.launcher_testing);
        testingButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent();
                i.setClass(self, TestingDemoActivity.class);
                startActivity(i);
            }
        });*/
        wifi = (WifiManager) getSystemService(WIFI_SERVICE);
        if (!wifi.isWifiEnabled()) {
          showWifiEnableConformation();
        } else {
            init();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void init() {
        showNotice();
    }
    
    /**
     * Show confirmation to ask if the WiFi should be enabled.
     */
    private void showWifiEnableConformation() {
      final AlertDialog updateDialog = new AlertDialog.Builder(this).create();
      updateDialog.setTitle(R.string.diag_title_wifi_enable);
      updateDialog.setMessage(getString(R.string.diag_wifi_enable));
      updateDialog.setButton(getString(R.string.ok_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            wifi.setWifiEnabled(true);
            while (!wifi.isWifiEnabled()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            init();
        }
      });
      updateDialog.setButton2(getString(R.string.cancel_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
          finish();
        }
      });
      updateDialog.show();
    }
    
    /**
     * Show information about the app.
     */
    private void showNotice() {
      final AlertDialog updateDialog = new AlertDialog.Builder(this).create();
      updateDialog.setTitle("WhereAbout");
      updateDialog.setMessage("This is a demo app that will estimate your location indoors. " +
              "For this to work you need to first tag WiFi locations of interest. All WiFi data " +
              "collected by this app will be stored on the local SD card.");
      updateDialog.setButton(getString(R.string.ok_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            updateDialog.dismiss();
        }
      });
      updateDialog.setButton2(getString(R.string.cancel_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            updateDialog.dismiss();
            finish();
        }
      });
      updateDialog.show();
    }
}
