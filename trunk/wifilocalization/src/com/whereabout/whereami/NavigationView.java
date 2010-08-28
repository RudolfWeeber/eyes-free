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
import com.whereabout.location.LocationManager.WifiLocalizationListener;
import com.whereabout.location.LocationManager.WifiStorageException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

/**
 * This activity displays the path from the current location to the destination.
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class NavigationView extends Activity {

    private static final String TAG = "NavigationView";
    
    private MenuItem refreshMenuitem = null;
    
    private LocationManager lm;
    
    private MotionListener motionListener;
    
    protected SensorManager sensorManager;

    private MapView mapView = null;

    private String mapName = "", mapImageFile = "", mapPath = "";
    
    private String destinationName;

    private MapInfo mapInfo = null;
    
    private ArrayList<Point> pathPoints = new ArrayList<Point>();
    
    private ArrayList<String> path = new ArrayList<String>();
    
    private Point currentLocation;
    
    private ProgressDialog progressDialog;
    
    private int startPointOnPathIndex = 0;
    
    private float[] orientationSensorValues = {0 , 0, 0};

    private Runnable navigator = new Runnable() {
        public void run() {
            mapView.showRoute(pathPoints);
        }
    };

    private Runnable locator = new Runnable() {
        public void run() {
            if (currentLocation == null && pathPoints != null && !pathPoints.isEmpty()) {
                currentLocation = pathPoints.get(0);
            }
            mapView.showLocationWithErrorCirle(currentLocation.x, currentLocation.y, 60);
            mapView.showDirection(-orientationSensorValues[0]);
        }
    };
    
    private Runnable dialogHider = new Runnable() {
        public void run() {
            progressDialog.hide();
        }
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getIntent().getExtras();
        mapName = bundle.getString(Constants.KEY_MAP_NAME);
        mapImageFile = bundle.getString(Constants.KEY_MAP_IMAGE_FILE);
        destinationName = bundle.getString(Constants.KEY_DESTINATION);
        
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        motionListener = new MotionListener();
        sensorManager.registerListener(motionListener,
            sensorManager.getSensorList(Sensor.TYPE_ORIENTATION).get(0),
            SensorManager.SENSOR_DELAY_UI);
        
        lm = new LocationManager(this);
        try {
            lm.startWifiService();
        } catch (WifiStorageException e) {
            Log.e(TAG, "Failed starting WiFi service: " + e.getMessage());
            finish();
        }
        Bundle extras = new Bundle();
        extras.putString(LocationManager.ROOT_DIR_NAME,
                Environment.getExternalStorageDirectory() + "/wifiscans");
        extras.putString(LocationManager.LOAD_FOR_AREA, mapName + ".*");
        lm.sendExtraCommand(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER,
                LocationManager.COMMAND_LOAD_FOR, extras);

        if (mapName == null || destinationName == null) {
            Log.e(TAG, "Map name not provided.");
            finish();
            return;
        } else {
            mapPath = Constants.MAP_DIR + "/" + mapName;
            if (!mapPath.endsWith(".map")) {
                mapPath += ".map";
            }
            File file = new File(mapPath);
            if (!file.exists()) {
                Log.w(TAG, "The requested map does not exist yet.");
                return;
            }
            try {
                mapInfo = Utils.getMapInfo(mapPath);
                if (mapImageFile == null) {
                    mapImageFile = mapInfo.imageFile;
                }
            } catch (FileNotFoundException fnfe) {
                Log.e(TAG, "Map file not found.");
                return;
            } catch (IOException ioe) {
                Log.e(TAG, "Cannot open map file to read.");
                return;
            }
        }
        if (mapImageFile == null) {
            Log.e(TAG, "Map Image file not provided.");
            finish();
            return;
        } else {
            mapView = new MapView(this, Constants.MAP_DIR + "/images/" + mapImageFile);
            setContentView(mapView);
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("Finding route...");
            localize();
        }
    }
    
    @Override
    public void onDestroy() {
        lm.shutdown();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
        super.onDestroy();
    }
    
    /**
     * Called when the menu is first created
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        refreshMenuitem = menu.add("Refresh");
        return true;
    }

    /**
     * Handles menu option selection.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == refreshMenuitem) {
            localize();
            return true;
        }
        return false;
    }
    
    private void localize() {
        progressDialog.show();
        lm.localizeWithWifi(3000, new WifiLocalizationListener() {
            public void onWifiLocalizationResult(int status, Location result) {
                if (result == null) {
                    NavigationView.this.runOnUiThread(dialogHider);
                    return;
                }
                final String locationName = result == null ? null :
                    result.getExtras().getStringArray(LocationManager.MATCH_LOCATIONS)[0];
                int x = result.getExtras().getIntArray(LocationManager.MATCH_XCOORDS)[0];
                int y = result.getExtras().getIntArray(LocationManager.MATCH_YCOORDS)[0];
                if (locationName != null) {
                    try {
                        Point p = Utils.getXYForWifiLocation(Constants.SCANS_DIR, mapName, destinationName);
                        path = Utils.computePath(mapInfo, x, y, p.x, p.y);
                        if (path != null && path.size() > 0) {
                            pathPoints.clear();
                            for (String pt : path) {
                                if (mapInfo.pointIdMap.containsKey(pt)) {
                                    pathPoints.add(mapInfo.pointIdMap.get(pt));
                                }
                            }
                            NavigationView.this.runOnUiThread(navigator);
                            startPointOnPathIndex = 0;
                            lm.requestLocationUpdates(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER, 1000, 0,
                                    mLocationListener);
                        }
                    } catch (IOException ioe) {
                        Log.e(TAG, "Error finding path to destination.");
                    }
                }
                NavigationView.this.runOnUiThread(dialogHider);
            }
        });
    }
    
    int outOfPathCount = 0, maybeOutOfPathCount = 0;
    
    private LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(final Location location) {
            NavigationView.this.runOnUiThread(new Runnable() {
                public void run() {
                    Bundle extras = location.getExtras();
                    String[] locs = extras.getStringArray(LocationManager.MATCH_LOCATIONS);
                    int[] xCoords = extras.getIntArray(LocationManager.MATCH_XCOORDS);
                    int[] yCoords = extras.getIntArray(LocationManager.MATCH_YCOORDS);
                    double[] scores = extras.getDoubleArray(LocationManager.MATCH_SCORES);
                    int matchLocIndex = 0;
                    for (int i = 0; i < locs.length; i++, matchLocIndex++) {
                        int matchPtIndex = startPointOnPathIndex;
                        for (int j = startPointOnPathIndex;
                             j < pathPoints.size();
                             j++, matchPtIndex++) {
                            Point p = pathPoints.get(j);
                            if (xCoords[i] == p.x && yCoords[i] == p.y) {
                                startPointOnPathIndex = matchPtIndex;
                                break;
                            }
                        }
                        if (matchPtIndex < pathPoints.size()) {
                            currentLocation = pathPoints.get(matchPtIndex);
                            break;
                        }
                    }
                    if (matchLocIndex != 0) {
                        maybeOutOfPathCount++;
                    } else {
                        maybeOutOfPathCount = 0;
                    }
                    if (matchLocIndex == locs.length ||
                        scores[matchLocIndex] > 100) {
                        outOfPathCount++;
                    } else {
                        outOfPathCount = 0;
                    }
                    if (outOfPathCount > 5 || maybeOutOfPathCount > 10) {
                        currentLocation = new Point(xCoords[0], yCoords[0]);
                        progressDialog.setMessage("Rerouting...");
                        localize();
                    }
                    NavigationView.this.runOnUiThread(locator);
                }
            });
        }

        public void onProviderDisabled(String provider) {
            // TODO Auto-generated method stub

        }

        public void onProviderEnabled(String provider) {
            // TODO Auto-generated method stub

        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
            // TODO Auto-generated method stub

        }
    };
    
    protected class MotionListener implements SensorEventListener {
        public MotionListener() {
        }
    
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                orientationSensorValues = event.values;
            }     
        }
    }
}
