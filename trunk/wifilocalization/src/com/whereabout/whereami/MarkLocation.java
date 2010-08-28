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

import com.whereabout.common.Constants;
import com.whereabout.common.MapInfo;
import com.whereabout.common.Utils;
import com.whereabout.location.LocationManager;
import com.whereabout.location.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.CompoundButton.OnCheckedChangeListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Sample Activity to demonstrate how to use the WhereAbout LocationManager to
 * scan and save a location.
 * 
 * @author clchen@google.com (Charles L. Chen)
 *         chaitanyag@google.com (Chaitanya Gharpure)
 */
public class MarkLocation extends Activity {

    private static final String TAG = "MarkLocation";
    
    private static final int SCAN_DURATION = 20000;
    
    private static final int NUM_TEST_SCANS = 5;

    private MarkLocation self;

    private LocationManager lm;

    private ProgressDialog progressDialog;

    private long scanDuration = SCAN_DURATION;

    private Button viewPointButton, scanButton;
    
    private CheckBox testScanCheckbox;

    private EditText locationNameEditText;

    private Spinner mapPointsSpinner, mapSpinner, locationSpinner;

    private ArrayAdapter<String> mapSpinnerAdapter, mapPointSpinnerAdapter, locationSpinnerAdapter;

    private int pointOfScan;

    private Point[] pointsOnMap;

    private String[] pointNamesOnMap;
    
    private HashMap<String, String> locNameIdMap;
    
    private Location[] pointLatLonOnMap;

    private String selectedMap;

    private String[] mapNames;
    
    private String[] futureLocationNames;

    private String area = "AREA";

    private String building = "BUILDING";

    private String floor = "FLOOR";
    
    private String scanLocation = "";
    
    private String toastMessage = "";
    
    private int locationX = -1, locationY = -1;
    
    private boolean scanForTest = false;
    
    private boolean stillScanning = false;
    
    private int numScanReps;

    private Runnable locationSaver = new Runnable() {
        
        private boolean prevScanDone = false;
        
        public void run() {
            if (scanLocation.equals("none") || scanLocation.equals("")) {
                String loc = locationNameEditText.getText().toString().trim();
                scanLocation = loc.equals("none") ? "" : loc;
            }
            double lat = 0, lon = 0;
            if (pointsOnMap != null && pointsOnMap.length > 0 &&
                    pointOfScan < pointsOnMap.length) {
                locationX = pointsOnMap[pointOfScan].x;
                locationY = pointsOnMap[pointOfScan].y;
                lat = pointLatLonOnMap[pointOfScan].getLatitude();
                lon = pointLatLonOnMap[pointOfScan].getLongitude();
                if (!scanLocation.equals(""))
                    scanLocation += "-";
                scanLocation += pointNamesOnMap[pointOfScan];
            }
            if (scanLocation.equals("none") || scanLocation.equals("")) {
                Log.w(TAG, "Location name missing. Please enter a location name.");
                toastMessage = "Please enter a location name";
                stillScanning = false;
                return;
            }
            numScanReps = 1;
            if (scanForTest) {
                numScanReps = NUM_TEST_SCANS;
            }
            int scanFreq = scanForTest ? 10 : 4;
            scanDuration = scanForTest ? 5000 : SCAN_DURATION;

            stillScanning = true;
            for (int i = 0; i < numScanReps; i++) {
                final int rep = i;
                prevScanDone = false;
                lm.saveScan(Environment.getExternalStorageDirectory() + "/wifiscans", area,
                        building, floor, scanLocation, locationX, locationY, lat, lon, scanFreq,
                        scanDuration, new LocationManager.WifiLocationTaggingListener() {
                            public void onDoneSave(boolean success, String locationName) {
                                prevScanDone = true;
                            }
                });
                while (!prevScanDone) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            scanLocation = "";
            toastMessage = "Done!";
            stillScanning = false;
        }
    };

    private Runnable progressUpdater = new Runnable() {
        public void run() {
            int sleep = 1000;
            while (stillScanning) {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                progressDialog.incrementProgressBy(
                        (int) (100 / (scanDuration * numScanReps / sleep)));
            }
            progressDialog.setProgress(0);
            progressDialog.dismiss();
            self.runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(self, toastMessage, 1).show();
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        lm = new LocationManager(this);
        progressDialog = new ProgressDialog(this);

        setContentView(R.layout.mark_location);
        scanButton = (Button) findViewById(R.id.mark_scanButton);
        viewPointButton = (Button) findViewById(R.id.view_on_map);
        testScanCheckbox = (CheckBox) findViewById(R.id.test_data_checkbox);

        mapSpinner = (Spinner) findViewById(R.id.map_spinner);
        mapPointsSpinner = (Spinner) findViewById(R.id.map_location_spinner);
        locationSpinner = (Spinner) findViewById(R.id.future_location_spinner);

        mapSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        mapSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mapPointSpinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item);
        mapPointSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationSpinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item);
        locationSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        populateMapSpinner();

        testScanCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                scanForTest = isChecked;
            }
        });

        scanButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                progressDialog.setProgress(0);
                stillScanning = true;
                new Thread(locationSaver).start();
                new Thread(progressUpdater).start();
                progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                progressDialog.setTitle("Scanning");
                progressDialog.show();
            }
        });
        viewPointButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                if (!selectedMap.equals("none") && pointOfScan != -1) {
                    ArrayList<String> allLocs = Utils.getLocationsForMap(Constants.SCANS_DIR,
                            selectedMap);
                    int[] locIds = new int[allLocs.size()];
                    int k = 0;
                    for (String locName : allLocs) {
                        locIds[k++] =
                                Integer.parseInt(locName.substring(locName.lastIndexOf('-') + 1));
                    }
                    Intent i = new Intent();
                    i.setClass(MarkLocation.this, NonEditableMapActivity.class);
                    i.putExtra(Constants.KEY_MAP_NAME, selectedMap);
                    i.putExtra(Constants.KEY_SELECTED_POINT, pointOfScan);
                    i.putExtra(Constants.KEY_POINT_IDS, locIds);
                    startActivityForResult(i, 0);
                }
            }
        });
        mapSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                selectedMap = mapNames[position];
                if (!selectedMap.equals("none")) {
                    selectedMap = selectedMap.substring(0, selectedMap.indexOf('.'));
                    StringTokenizer st = new StringTokenizer(selectedMap, "_");
                    if (st.hasMoreTokens())
                        area = st.nextToken();
                    if (st.hasMoreTokens())
                        building = st.nextToken();
                    if (st.hasMoreTokens())
                        floor = st.nextToken();
                    mapPointsSpinner.setEnabled(true);
                    populateMapPointsSpinner(selectedMap);
                    populateFutureLocationSpinner(selectedMap);
                } else {
                    area = "AREA";
                    building = "BUILDING";
                    floor = "FLOOR";
                    mapPointsSpinner.setEnabled(false);
                }
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
        mapPointsSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                pointOfScan = -1;
                if (!pointNamesOnMap[position].equals("none")) {
                    pointOfScan = position;
                    if (locNameIdMap.containsKey(pointNamesOnMap[position])) {
                        locationNameEditText.setText(locNameIdMap.get(pointNamesOnMap[position]));
                    } else {
                        locationNameEditText.setText("");
                    }
                }
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        locationSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                scanLocation = futureLocationNames[position];
                locationNameEditText.setText(scanLocation);
            }
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        locationNameEditText = (EditText) findViewById(R.id.mark_locNameEditText);
    }

    @Override
    public void onDestroy() {
        lm.shutdown();
        stillScanning = false;
        super.onDestroy();
    }

    public void onViewInGoogleMaps(View v) {
        // TODO: Open an activity showing a Google map where the suer can go to an address
        // and touch the map to select a lat-lng.
    }
    
    private void populateMapSpinner() {
        ArrayList<String> maps = Utils.getMapNames(Constants.MAP_DIR);
        mapNames = new String[maps.size() + 1];
        maps.toArray(mapNames);
        mapNames[maps.size()] = "none";
        Utils.populateSpinner(mapSpinner, mapSpinnerAdapter, mapNames);
    }

    private void populateMapPointsSpinner(String mapName) {
        if (!mapName.endsWith(".map")) {
            mapName += ".map";
        }
        try {
            MapInfo mapInfo = Utils.getMapInfo(Constants.MAP_DIR + "/" + mapName);
            // Extract XY points in an array of Points
            pointsOnMap = new Point[mapInfo.allPoints.size()];
            mapInfo.allPoints.toArray(pointsOnMap);
            // Extract Lat-Lon in an array of Locations
            pointLatLonOnMap = new Location[mapInfo.allPoints.size()];
            mapInfo.allLatLon.toArray(pointLatLonOnMap);
            // Extract Ids in an array of Strings
            pointNamesOnMap = new String[mapInfo.allPoints.size() + 1];
            mapInfo.allIds.toArray(pointNamesOnMap);
            locNameIdMap = mapInfo.locNameIdMap;
            pointNamesOnMap[mapInfo.allPoints.size()] = "none";
            
            Utils.populateSpinner(mapPointsSpinner, mapPointSpinnerAdapter, pointNamesOnMap);
        } catch (IOException ioe) {
            Log.e(TAG, "Cannot popluate point for this map: " + mapName);
        }
    }

    private void populateFutureLocationSpinner(String mapName) {
        try {
            ArrayList<String> locs = Utils.getFutureLocationNames(Constants.MAP_DIR + "/" +
                    mapName + "-futurelocations.txt");
            if (locs == null) {
                return;
            }
            futureLocationNames = new String[locs.size() + 1];
            locs.toArray(futureLocationNames);
            futureLocationNames[locs.size()] = "none";
            Utils.populateSpinner(locationSpinner, locationSpinnerAdapter, futureLocationNames);
        } catch (IOException ioe) {
            Log.e(TAG, "Error reading future location names: " + ioe.getMessage());
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null) {
            return;
        }
        if (resultCode == Constants.GET_LOC_XY_RESULT) {
            locationX = data.getIntExtra(Constants.KEY_LOC_X, -1);
            locationY = data.getIntExtra(Constants.KEY_LOC_Y, -1);
            pointOfScan = data.getIntExtra(Constants.KEY_LOC_ID, pointNamesOnMap.length);
            mapPointsSpinner.setSelection(pointOfScan);
        }
    }
}
