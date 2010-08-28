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
import com.whereabout.location.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * Activity for adding points on a map where the Wifi data will be collected.
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class GenerateXYLocationsActivity extends Activity {
    private static final String TAG = "GenerateXYLocationsActivity";

    private MapView mapView = null;

    private MenuItem addMenuitem = null;
    
    private MenuItem connectMenuitem = null;

    private MenuItem viewMenuitem = null;

    private MenuItem clearMenuitem = null;

    private MenuItem saveMenuitem = null;

    private MenuItem deleteMenuitem = null;

    private Vibrator vibrator;

    private String mapName = "", mapImageFile = "", mapPath = "";
    
    private MapInfo mapInfo;
    
    private Handler displayHandler;
    
    private int connectionIndex = 0;
    
    private int[] connectionPoints = {-1, -1};
    
    /**
     * A thread for animating the Wifi logo while collecting signatures.
     */
    private Runnable displayer = new Runnable() {
        public void run() {
            if (mapInfo.allPoints.size() > 0) {
                mapView.showMultipleLocations(mapInfo.allPoints, mapInfo.allIds, -1);
                mapView.showConnectivity(mapInfo.connectivity);
                mapView.showLocation(mapView.getMapImageWidth() / 2,
                        mapView.getMapImageHeight() / 2);
            }
        }
    };
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getIntent().getExtras();
        displayHandler = new Handler();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mapName = bundle.getString(Constants.KEY_MAP_NAME);
        mapImageFile = bundle.getString(Constants.KEY_MAP_IMAGE_FILE);
        if (mapName == null) {
            Log.e(TAG, "Map name not provided.");
            return;
        } else {
            if (!mapName.endsWith(".map")) {
                mapName += ".map";
            }
            mapPath = Constants.MAP_DIR + "/" + mapName;
            loadPointsForMap(); // Loads points and map-image filename
        }
        if (mapImageFile == null) {
            Log.e(TAG, "Map Image file not provided.");
            return;
        } else if (mapImageFile.equals("none")) {
            try {
                saveAllPointsXml();
            } catch (IOException ioe) {
                Log.e(TAG, "Error saving map: " + ioe.getMessage());
            }
            finish();
            return;
        } else {
            mapView = new MapView(this, Environment.getExternalStorageDirectory()
                    + "/wifiscans/maps/images/" + mapImageFile);
            mapView.setWifiMapActionListener(new MapView.WifiMapActionListener() {
                public void onLongPressListener(Point p) {
                    vibrator.vibrate(50);
                    maybeShowLongPressOptions(p);
                }
                public void onClickListener(Point p) {
                    int ptIndex = getClosestPoint(p);
                    if (ptIndex != -1) {
                        connectionPoints[connectionIndex] = ptIndex;
                        connectionIndex = connectionIndex == 0 ? 1 : 0;
                        ArrayList<Integer> points = new ArrayList<Integer>();
                        if (connectionPoints[0] != -1)
                            points.add(connectionPoints[0]);
                        if (connectionPoints[1] != -1)
                            points.add(connectionPoints[1]);
                        mapView.highlightPoints(points);
                    }
                }
            });
            setContentView(mapView);
        }
        displayHandler.removeCallbacks(displayer);
        displayHandler.postDelayed(displayer, 100);
    }

    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.destroy();
        }
        super.onDestroy();
    }

    /**
     * Called when the menu is first created
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        addMenuitem = menu.add("Add point");
        connectMenuitem = menu.add("Connect");
        viewMenuitem = menu.add("View All points");
        clearMenuitem = menu.add("Clear All points");
        saveMenuitem = menu.add("Save map");
        deleteMenuitem = menu.add("Delete map");
        return true;
    }

    /**
     * Handles menu option selection.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == addMenuitem) {
            addLastSelectedPoint();
            viewAllPoints();
            return true;
        } else if (item == connectMenuitem) {
            if (connectionPoints[0] != -1 && connectionPoints[1] != -1) {
                mapInfo.addConnection(mapInfo.allIds.get(connectionPoints[0]),
                        mapInfo.allIds.get(connectionPoints[1]));
                mapView.showConnectivity(mapInfo.connectivity);
            }
            return true;
        } else if (item == viewMenuitem) {
            viewAllPoints();
            return true;
        } else if (item == saveMenuitem) {
            try {
                saveAllPointsXml();
            } catch (IOException ioe) {
                Log.e(TAG, "Error saving points.");
            }
            return true;
        } else if (item == clearMenuitem) {
            showClearMapConfirmation();
            return true;
        } else if (item == deleteMenuitem) {
            showDeleteMapConfirmation();
            return true;
        }
        return false;
    }

    /**
     * Show confirmation dialog for clearing points on the map.  
     */
    private void showClearMapConfirmation() {
      final AlertDialog dialog = new AlertDialog.Builder(this).create();
      dialog.setTitle(R.string.diag_title_clear_map);
      dialog.setMessage(getString(R.string.diag_clear_map));
      dialog.setButton(getString(R.string.ok_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            mapInfo.clear();
            viewAllPoints();
        }
      });
      dialog.setButton2(getString(R.string.cancel_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            dialog.dismiss();
        }
      });
      dialog.show();
    }
    
    /**
     * Show confirmation dialog for deleting the map.
     */
    private void showDeleteMapConfirmation() {
      final AlertDialog dialog = new AlertDialog.Builder(this).create();
      dialog.setTitle(R.string.diag_title_delete_map);
      dialog.setMessage(getString(R.string.diag_delete_map));
      dialog.setButton(getString(R.string.ok_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            deleteMap();
        }
      });
      dialog.setButton2(getString(R.string.cancel_butt), 
          new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface arg0, int arg1) {
            dialog.dismiss();
        }
      });
      dialog.show();
    }
    
    private void deleteMap() {
        File file = new File(mapPath);
        if (file.exists()) {
            file.renameTo(new File(mapPath + ".del"));
            finish();
        }
    }

    private void saveAllPointsXml() throws IOException {
        File dir = new File(Constants.MAP_DIR);
        File file = new File(mapPath);
        if (!dir.exists()) {
            dir.mkdir();
            if (!file.exists()) {
                file.createNewFile();
            }
        }
        FileWriter writer = new FileWriter(file);
        StringBuilder infoStr = new StringBuilder();
        infoStr.append("<?xml version=\"1.0\"?>");
        infoStr.append("<map>");
        infoStr.append("<image>" + mapImageFile  + "</image>");
        infoStr.append("<latlon><lat-top>" + mapInfo.latTop + "</lat-top><lon-left>" +
                mapInfo.lonLeft + "</lon-left><lat-bottom>" + mapInfo.latBottom + "</lat-bottom>" +
                "<lon-right>" + mapInfo.lonRight + "</lon-right></latlon>\n");
        for (int i = 0; i < mapInfo.allPoints.size(); i++) {
            Point p = mapInfo.allPoints.get(i);
            Location loc = mapInfo.allLatLon.get(i);
            // If the lat-lon bounds for the map are provided, compute the lat-lon form XY.
            if (mapInfo.latTop != 0 && mapInfo.lonLeft != 0 && mapInfo.latBottom != 0 &&
                    mapInfo.lonRight != 0) {
                loc = computeLatLonFromXY(mapInfo.latTop, mapInfo.lonLeft, mapInfo.latBottom,
                        mapInfo.lonRight, mapView.getMapImageWidth(), mapView.getMapImageHeight(),
                        p.x, p.y);
            }
            String locName = "";
            if (mapInfo.locNameIdMap.containsKey(mapInfo.allIds.get(i))) {
                locName = mapInfo.locNameIdMap.get(mapInfo.allIds.get(i));
            }
            infoStr.append("<point><id>" + mapInfo.allIds.get(i) + "</id><x>" + p.x + "</x>" +
                    "<y>" + p.y + "</y><lat>" + loc.getLatitude() + "</lat><lon>" +
                    loc.getLongitude() + "</lon><name>" + locName + "</name></point>\n");
        }
        infoStr.append("<connectivity>");
        Set<String> fromIds = mapInfo.connectivity.keySet();
        for (String from : fromIds) {
            String str = from;
            Set<String> tos = mapInfo.connectivity.get(from);
            for (String to : tos) {
                str += " " + to;
            }
            infoStr.append("<conn>" + str + "</conn>");
        }
        infoStr.append("</connectivity></map>");
        writer.write(infoStr.toString());
        writer.close();
    }

    private void loadPointsForMap() {
        File file = new File(mapPath);
        if (!file.exists()) {
            mapInfo = new MapInfo(mapImageFile, 0, 0, 0, 0);
            Log.w(TAG, "The requested map does not exist yet.");
            return;
        }
        try {
            mapInfo = Utils.getMapInfo(mapPath);
            mapImageFile = mapInfo.imageFile;
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Map file not found.");
            return;
        } catch (IOException ioe) {
            Log.e(TAG, "Cannot open map file to read.");
            return;
        }
    }

    private void addLastSelectedPoint() {
        int x = mapView.getLocationX();
        int y = mapView.getLocationY();
        Location loc = new Location("");
        if (mapInfo.latTop != 0 && mapInfo.lonLeft != 0 &&
                mapInfo.latBottom != 0 && mapInfo.lonRight != 0) {
            loc = computeLatLonFromXY(mapInfo.latTop, mapInfo.lonLeft, mapInfo.latBottom,
                    mapInfo.lonRight, mapView.getMapImageWidth(), mapView.getMapImageHeight(),
                    x, y);
        }
        mapInfo.createPoint(new Point(x, y), loc);
    }

    private void viewAllPoints() {
        mapView.showMultipleLocations(mapInfo.allPoints, mapInfo.allIds, -1);
        mapView.showConnectivity(mapInfo.connectivity);
    }

    private void maybeShowLongPressOptions(Point p) {
        int ptIndex = getClosestPoint(p);
        if (ptIndex != -1) {
            displayLongPressOptions(ptIndex);
        }
    }
    
    private int getClosestPoint(Point p) {
        Point closestPt;
        int closestPtId = -1;
        double minDist = 100000;
        for (int i = 0; i < mapInfo.allPoints.size(); i++) {
            Point pt = mapInfo.allPoints.get(i);
            double dist = Math.sqrt(((pt.y - p.y) * (pt.y - p.y)) + ((pt.x - p.x) * (pt.x - p.x)));
            if (dist < minDist) {
                minDist = dist;
                closestPt = pt;
                closestPtId = i;
            }
        }
        if (minDist < 20) {
            return closestPtId;
        }
        return -1;
    }

    private void displayLongPressOptions(final int ptId) {
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Point: " + ptId);
        ListView listView = new ListView(this);
        final String[] options = {"Delete", "Disconnect"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, options);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                if (position == 0) { // detele
                    mapInfo.removePoint(ptId);
                    viewAllPoints();
                    alertDialog.dismiss();
                } else if (position == 1) { // disconnect
                    mapInfo.removeConnections(mapInfo.allIds.get(ptId));
                    mapView.showConnectivity(mapInfo.connectivity);
                    alertDialog.dismiss();
                }
            }
        });
        alertDialog.setView(listView);
        alertDialog.show();
    }
    
    private Location computeLatLonFromXY(double lt1, double ln1, double lt2, double ln2,
            double x2, double y2, double x, double y) {
        // need to do -(y2 - y1) since pixel x,y grow +ve to the right of Y-axis and below the
        // X-axis. atan2 assumes the regular cartesian coordinatespace with +ve x,y in the
        // top-right quadrant.
        double theta = Math.atan2(-y2, x2) * 180 / Math.PI;
        double delta = Math.atan2(lt2 - lt1, ln2 - ln1) * 180 / Math.PI;
        double rotBy = (delta - theta) * Math.PI / 180;
        
        double HYPXY = Math.sqrt(y2*y2 + x2*x2);
        double HYPLT = Math.sqrt((lt2-lt1)*(lt2-lt1) + (ln2-ln1)*(ln2-ln1));
        double xyDist = Math.sqrt(y*y + x*x);
        double ltDist = xyDist / HYPXY * HYPLT;

        // Intermediate lat-lon
        double lni = x / HYPXY * HYPLT;
        double lti = - (y / HYPXY * HYPLT);

        // Rotate lat-lon
        double ln = (lni * Math.cos(rotBy) - lti * Math.sin(rotBy)) + ln1;
        double lt = (lni * Math.sin(rotBy) + lti * Math.cos(rotBy)) + lt1;
        Location loc = new Location("");
        loc.setLatitude(lt);
        loc.setLongitude(ln);
        return loc;
    }
}
