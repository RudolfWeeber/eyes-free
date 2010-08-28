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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * This activity displays the non-editable map of points. A specific point can be selected
 * via a long press and returned back to the calling activity.
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class NonEditableMapActivity extends Activity {

    private static final String TAG = "NonEditableMapActivity";

    private MapView mapView = null;

    private String mapName = "", mapImageFile = "", mapPath = "";

    private int selectedPoint = -1;

    private Handler displayHandler = null;

    private Vibrator vibrator;
    
    private MapInfo mapInfo = null;
    
    private ArrayList<Integer> highlightPoints = null;

    /**
     * A thread for animating the Wifi logo while collecting signatures.
     */
    private Runnable displayer = new Runnable() {
        public void run() {
            if (mapInfo.allPoints.size() > 0) {
                Point p = new Point(mapView.getMapImageWidth() / 2, mapView.getMapImageHeight() / 2);
                if (selectedPoint != -1) {
                    p = mapInfo.allPoints.get(selectedPoint);
                }
                if (highlightPoints != null && highlightPoints.size() > 0) {
                    mapView.highlightPoints(highlightPoints);
                }
                mapView.showMultipleLocations(mapInfo.allPoints, mapInfo.allIds, selectedPoint);
                mapView.showConnectivity(mapInfo.connectivity);
                mapView.showLocation(p.x, p.y);
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getIntent().getExtras();
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        displayHandler = new Handler();
        mapName = bundle.getString(Constants.KEY_MAP_NAME);
        mapImageFile = bundle.getString(Constants.KEY_MAP_IMAGE_FILE);
        if (bundle.containsKey(Constants.KEY_SELECTED_POINT)) {
            selectedPoint = bundle.getInt(Constants.KEY_SELECTED_POINT);
        }
        if (bundle.containsKey(Constants.KEY_POINT_IDS)) {
            int[] pointIds = bundle.getIntArray(Constants.KEY_POINT_IDS);
            highlightPoints = new ArrayList<Integer>(pointIds.length);
            for (int i = 0; i < pointIds.length; i++) {
                highlightPoints.add(pointIds[i]);
            }
        }

        if (mapName == null) {
            Log.e(TAG, "Map name not provided.");
            finish();
            return;
        } else {
            if (!mapName.endsWith(".map")) {
                mapName += ".map";
            }
            mapPath = Constants.MAP_DIR + "/" + mapName;
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
            mapView.setWifiMapActionListener(new MapView.WifiMapActionListener() {
                public void onLongPressListener(Point p) {
                    vibrator.vibrate(50);
                    maybeShowLongPressOptions(p);
                }
                public void onClickListener(Point p) {
                }
            });            setContentView(mapView);
            displayHandler.removeCallbacks(displayer);
            displayHandler.postDelayed(displayer, 100);
        }
    }
    
    @Override
    public void onDestroy() {
        if (mapView != null) {
            mapView.destroy();
        }
        super.onDestroy();
    }

    private void maybeShowLongPressOptions(Point p) {
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
            displayLongPressOptions(closestPtId);
        }
    }
    
    private void displayLongPressOptions(final int ptId) {
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Point: " + ptId);
        ListView listView = new ListView(this);
        final String[] options = {"Select"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, options);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                if (position == 0) { // select
                    alertDialog.dismiss();
                    returnLocation(mapInfo.allPoints.get(ptId), ptId);
                }
            }
        });
        alertDialog.setView(listView);
        alertDialog.show();
    }
    
    private void returnLocation(Point p, int pt) {
        Intent retIntent = new Intent();
        retIntent.putExtra(Constants.KEY_LOC_X, p.x);
        retIntent.putExtra(Constants.KEY_LOC_Y, p.y);
        retIntent.putExtra(Constants.KEY_LOC_ID, pt);
        setResult(Constants.GET_LOC_XY_RESULT, retIntent);
        finish();
    }
}
