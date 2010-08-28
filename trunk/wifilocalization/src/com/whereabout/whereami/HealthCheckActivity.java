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

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.TextView;

public class HealthCheckActivity extends Activity {
    
    private WifiManager wifi = null;
    
    private TextView infoTxt;
    
    private String infoStr;
    
    private boolean isScanning = false;
    
    private Runnable wifiChecker = new Runnable() {
        public void run() {
            while (isScanning) {
                wifi.startScan();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                List<ScanResult> res = wifi.getScanResults();
                int numWifiAPs = 0;
                int maxStrength = -100;
                for (ScanResult r : res) {
                    if (r.BSSID == null) {
                        continue;
                    }
                    numWifiAPs++;
                    if (r.level > maxStrength) {
                        maxStrength = r.level;
                    }
                }
                infoStr = "# Wifi APs: " + numWifiAPs + "\n\n" + "Max strength: " +
                        maxStrength + "\n\n";
                if (numWifiAPs < 5 || maxStrength < -75) {
                    infoStr += "WEAK DATA!";
                }
                HealthCheckActivity.this.runOnUiThread(uiUpdater);
            }
        }
    };
    
    private Runnable uiUpdater = new Runnable() {
        public void run() {
            if (infoTxt != null)
                infoTxt.setText(infoStr);
        }
    };
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.healthcheck);
        infoTxt = (TextView) findViewById(R.id.health_info);
        wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        isScanning = true;
        new Thread(wifiChecker).start();
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        isScanning = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        isScanning = false;
    }
}
