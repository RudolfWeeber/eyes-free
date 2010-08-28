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
import com.whereabout.location.LocationManager.WifiStorageException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import android.app.Activity;
import android.app.ProgressDialog;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * Sample Activity to demonstrate how to use the WhereAbout LocationManager to
 * detect the current location.
 * 
 * @author clchen@google.com (Charles L. Chen)
 *         chaitanyag@google.com (Chaitanya Gharpure)
 */
public class WhereAmI extends Activity {

    private static final String TAG = "WhereAmI";

    private static enum ViewType {
        LOCATION_NAME, MAP
    }

    private LocationManager lm;

    private TextView nameTextView;

    private TextView infoTextView;

    private MapView mapView = null;

    private MenuItem switchViewMenuitem = null;

    private ViewType viewType = ViewType.MAP;

    private ProgressDialog progressDialog;

    private String currentMapName = "MTV_1950_4", prevMapName = "";

    private int currX = 0, currY = 0;
    
    private double currLat = 0, currLon = 0;
    
    private long downTime = 0;
    
    private boolean ttsStarted = false;
    
    private String vicinityStr = "";
    
    private TextToSpeech tts = null;
    
    private Runnable waitThread = new Runnable() {
        public void run() {
            while (!lm.isReady()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            WhereAmI.this.runOnUiThread(hideProgress);
        }
    };

    private Runnable hideProgress = new Runnable() {
        public void run() {
            progressDialog.hide();
        }
    };

    private LocationListener mLocationListener = new LocationListener() {
        public void onLocationChanged(final Location location) {
            WhereAmI.this.runOnUiThread(new Runnable() {
                public void run() {
                    Bundle extras = location.getExtras();
                    nameTextView.setText(extras.getStringArray(LocationManager.MATCH_LOCATIONS)[0]);

                    String[] matchMaps = extras.getStringArray(LocationManager.MATCH_MAPS);
                    String[] matchLocations = extras
                            .getStringArray(LocationManager.MATCH_LOCATIONS);
                    double[] matchScores = extras.getDoubleArray(LocationManager.MATCH_SCORES);
                    int[] matchXCoords = extras.getIntArray(LocationManager.MATCH_XCOORDS);
                    int[] matchYCoords = extras.getIntArray(LocationManager.MATCH_YCOORDS);
                    int x = extras.getInt(LocationManager.FINAL_X);
                    int y = extras.getInt(LocationManager.FINAL_Y);
                    
                    synchronized (WhereAmI.this) {
                        currX = x;
                        currY = y;
                        currLat = extras.getDouble(LocationManager.FINAL_LAT);
                        currLon = extras.getDouble(LocationManager.FINAL_LON);
                    }
                    
                    String info = "";
                    for (int i = 0; i < matchLocations.length; i++) {
                        info = info + matchLocations[i] + " (" + matchXCoords[i] + ", "
                                + matchYCoords[i] + ") : " + matchScores[i] + "\n\n";
                    }

                    if (matchLocations[0] != null) {
                        currentMapName = matchLocations[0].substring(0, matchLocations[0]
                                .lastIndexOf('_'));
                        if (viewType == ViewType.MAP) {
                            if (mapView != null) {
                                if (!currentMapName.equals(prevMapName)) {
                                    try {
                                        mapView.setImageBitmap(Constants.MAP_DIR + "/images/"
                                                + Utils.getMapInfo(Constants.MAP_DIR + "/" +
                                                        currentMapName + ".map").imageFile);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                                mapView.showLocationWithErrorCirle(x, y, 60);
                            } else {
                                initMapView();
                            }
                        } else {
                            nameTextView.setText(matchLocations[0]);
                            infoTextView.setText(vicinityStr + "\n" + info);
                        }
                        prevMapName = currentMapName;
                    }
                }
            });
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.where_am_i);
        nameTextView = (TextView) findViewById(R.id.location_name);
        infoTextView = (TextView) findViewById(R.id.location_info);
        
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            public void onInit(int status) {
                ttsStarted = true;
            }
        });
        
        lm = new LocationManager(this);
        try {
            lm.startWifiService();
        } catch (WifiStorageException e) {
            Log.e(TAG, "Failed starting WiFi service: " + e.getMessage());
            finish();
        }
        lm.requestLocationUpdates(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER, 1000, 0,
                mLocationListener);
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
        progressDialog.show();
        new Thread(waitThread).start();
    }

    @Override
    public void onDestroy() {
        if (lm != null) {
            lm.shutdown();
        }
        progressDialog.dismiss();
        if (mapView != null) {
            mapView.destroy();
            mapView.destroyDrawingCache();
        }
        if (tts != null) {
            tts.shutdown();
        }
        super.onDestroy();
    }

    /**
     * Called when the menu is first created
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        switchViewMenuitem = menu.add("Switch View");
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
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downTime = System.currentTimeMillis();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (System.currentTimeMillis() - downTime > 500) {
                int x = 0, y = 0;
                double lat = 0, lon = 0;
                String mapName = null;
                synchronized (WhereAmI.this) {
                    x = currX;
                    y = currY;
                    lat = currLat;
                    lon = currLon;
                    mapName = currentMapName;
                }
                MapInfo mapInfo = null;
                try {
                    mapInfo = Utils.getMapInfo(Constants.MAP_DIR + "/" + mapName + ".map");
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
                ArrayList<String> ids = getLocationsOfInterest(x, y, lat, lon, mapName, mapInfo);
                HashSet<String> locNameSet = new HashSet<String>();
                vicinityStr = "";
                if (ids == null || ids.size() == 0) {
                    vicinityStr = "No points of interest around you.";
                } else {
                    vicinityStr = "You are near";
                    for (String id : ids) {
                        String name = mapInfo.locNameIdMap.get(id);
                        if (!locNameSet.contains(name)) {
                            locNameSet.add(name);
                            vicinityStr += ", " + name.replace('~', ' ');
                        }
                    }
                    vicinityStr += ".";
                }
                if (ttsStarted) {
                    tts.speak(vicinityStr, TextToSpeech.QUEUE_FLUSH, null);
                }
            }
        }
        return false;
    }
    
    private ArrayList<String> getLocationsOfInterest(int x, int y, double lat, double lon,
            String mapName, MapInfo mapInfo) {
        ArrayList<String> interests = new ArrayList<String>();
        if ((x == 0 && y == 0 && lat == 0 && lon == 0) || mapName == null) return null;
        int i = 0;
        for (Point p : mapInfo.allPoints) {
            String id = mapInfo.allIds.get(i);
            String name = mapInfo.locNameIdMap.get(mapInfo.allIds.get(i));
            if (name != null) {
                double dist = Math.sqrt((p.y - y) * (p.y - y) + (p.x - x) * (p.x - x));
                if (dist < 100) {
                    interests.add(id);
                }
            }
            i++;
        }
        return interests;
    }
    
    private void switchView() {
        viewType = viewType == ViewType.LOCATION_NAME ? ViewType.MAP : ViewType.LOCATION_NAME;
        if (viewType == ViewType.LOCATION_NAME) {
            setContentView(R.layout.where_am_i);
            nameTextView = (TextView) findViewById(R.id.location_name);
            infoTextView = (TextView) findViewById(R.id.location_info);
        } else {
            initMapView();
        }
    }
    
    private void initMapView() {
        if (mapView != null) {
            mapView.destroy();
        }
        try {
            String mapImageFile = Constants.MAP_DIR
                    + "/images/"
                    + Utils.getMapInfo(Constants.MAP_DIR + "/" + currentMapName + ".map").
                    imageFile;
            File file = new File(mapImageFile);
            if (file.exists()) {
                mapView = new MapView(this, mapImageFile);
                setContentView(mapView);
            }
        } catch (IOException ioe) {
            Log.d(TAG, "Error switching to map view.");
            return;
        }
    }
}
