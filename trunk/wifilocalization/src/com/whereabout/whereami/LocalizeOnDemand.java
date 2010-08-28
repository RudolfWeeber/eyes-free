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

import com.whereabout.location.LocationManager;
import com.whereabout.location.R;
import com.whereabout.location.LocationManager.WifiLocalizationListener;
import com.whereabout.location.LocationManager.WifiStorageException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * This activity demonstrates how to request for WiFi-based location on demand.
 * 
 * @author chaitanyag@google.com (Your Name Here)
 */
public class LocalizeOnDemand extends Activity {
    private static final String TAG = "LocalizeOnDemand";
    
    private LocationManager lm;

    private ProgressDialog progressDialog;

    private LocalizeOnDemand self;

    private TextView locName;

    private static enum ViewType {
        LOCATION_NAME, MAP
    }

    private ViewType viewType = ViewType.LOCATION_NAME;

    private MapView mapView = null;

    private MenuItem switchViewMenuitem = null, getLocationMenuitem = null;

    private String currentMapName;

    private Runnable waitThread = new Runnable() {
        public void run() {
            while (!lm.isReady()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            LocalizeOnDemand.this.runOnUiThread(hideProgress);
        }
    };

    private Runnable hideProgress = new Runnable() {
        public void run() {
            progressDialog.hide();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.localize_on_demand);
        
        self = this;
        lm = new LocationManager(this);
        try {
            lm.startWifiService();
        } catch (WifiStorageException e) {
            Log.e(TAG, "Failed starting Wifi service: " + e.getMessage());
            finish();
        }

        Bundle extras = new Bundle();
        extras.putString(LocationManager.ROOT_DIR_NAME, Environment.getExternalStorageDirectory()
                + "/wifiscans");
        // To load scans for a specific map, do: e.g.
        // extras.putString(LocationManager.LOAD_FOR_AREA,
        // "SF_Exploratorium_1.*");
        // This will load all scans starting with "SF_Exploratorium_1".
        extras.putString(LocationManager.LOAD_FOR_AREA, null);
        lm.sendExtraCommand(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER,
                LocationManager.COMMAND_LOAD_FOR, extras);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Loading data...");
        locName = (TextView) findViewById(R.id.localize_on_demand_name);
        Button localizeButton = (Button) findViewById(R.id.localize_on_demand_button);
        localizeButton.setOnClickListener(new OnClickListener() {
            public void onClick(View arg0) {
                getLocation();
            }
        });
        progressDialog.show();
        new Thread(waitThread).start();
    }

    private void getLocation() {
        progressDialog.setMessage("Determining location...");
        progressDialog.show();
        lm.localizeWithWifi(4000, new WifiLocalizationListener() {
            public void onWifiLocalizationResult(int status, Location result) {
                final String locationName = result == null ? "Location cannot be determined."
                        : result.getExtras().getStringArray(LocationManager.MATCH_LOCATIONS)[0]
                                + " " + result.getExtras().getDoubleArray(
                                        LocationManager.MATCH_LATITUDES)[0]
                                + " " + result.getExtras().getDoubleArray(
                                        LocationManager.MATCH_LONGITUDES)[0];
                Bundle extras = result.getExtras();
                String[] matchLocations = extras.getStringArray(LocationManager.MATCH_LOCATIONS);
                final int[] matchXCoords = extras.getIntArray(LocationManager.MATCH_XCOORDS);
                final int[] matchYCoords = extras.getIntArray(LocationManager.MATCH_YCOORDS);
                if (matchLocations[0] != null) {
                    currentMapName = matchLocations[0].substring(0, matchLocations[0]
                            .lastIndexOf('_'));
                }
                Runnable UiUpdater = new Runnable() {
                    public void run() {
                        progressDialog.hide();
                        if (viewType == ViewType.MAP) {
                            mapView.showLocationWithErrorCirle(matchXCoords[0], matchYCoords[0],
                                            60);
                        } else {
                            locName.setText(locationName);
                        }
                    }
                };
                self.runOnUiThread(UiUpdater);
            }
        });
    }

    /**
     * Called when the menu is first created
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        switchViewMenuitem = menu.add("Switch View");
        getLocationMenuitem = menu.add("Get Location");
        return true;
    }

    /**
     * Handles menu option selection.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == switchViewMenuitem) {
            switchView();
            return true;
        } else if (item == getLocationMenuitem) {
            getLocation();
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        if (lm != null) {
            lm.shutdown();
        }
    }

    private void switchView() {
        viewType = viewType == ViewType.LOCATION_NAME ? ViewType.MAP : ViewType.LOCATION_NAME;
        if (viewType == ViewType.LOCATION_NAME) {
            setContentView(R.layout.localize_on_demand);
            locName = (TextView) findViewById(R.id.localize_on_demand_name);
        } else {
            if (mapView == null) {
                String mapImageFile = Environment.getExternalStorageDirectory()
                        + "/wifiscans/maps/images/" + currentMapName + ".jpg";
                mapView = new MapView(this, mapImageFile);
            }
            setContentView(mapView);
        }
    }
}
