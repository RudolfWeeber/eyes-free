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
import com.whereabout.common.Utils;
import com.whereabout.location.R;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemSelectedListener;

/**
 * This activity allows user to select a map and a destination location to
 * navigate to.
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class Navigation extends Activity {
    
    private Button navigateButton;

    private Spinner locationsSpinner, mapSpinner;

    private ArrayAdapter<String> mapSpinnerAdapter, locationsSpinnerAdapter;

    private String[] locationNames;
    
    private String selectedMap, selectedDestination;
    
    private String[] mapNames;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.navigate);
        navigateButton = (Button) findViewById(R.id.navigateButton);

        mapSpinner = (Spinner) findViewById(R.id.map_spinner);
        locationsSpinner = (Spinner) findViewById(R.id.map_location_spinner);

        mapSpinnerAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        mapSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        locationsSpinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item);
        locationsSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        populateMapSpinner();

        navigateButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                Intent i = new Intent();
                i.putExtra(Constants.KEY_DESTINATION, selectedDestination);
                i.putExtra(Constants.KEY_MAP_NAME, selectedMap);
                i.setClass(Navigation.this, NavigationView.class);
                startActivity(i);
            }
        });
        mapSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                selectedMap = mapNames[position];
                selectedMap = selectedMap.substring(0, selectedMap.indexOf('.'));
                populateLocationsSpinner(selectedMap);
            }
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
        locationsSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                selectedDestination = locationNames[position];
            }
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void populateMapSpinner() {
        ArrayList<String> maps = Utils.getMapNames(Constants.MAP_DIR);
        mapNames = new String[maps.size()];
        maps.toArray(mapNames);
        Utils.populateSpinner(mapSpinner, mapSpinnerAdapter, mapNames);
    }

    private void populateLocationsSpinner(String mapName) {
        ArrayList<String> locations = Utils.getLocationsForMap(Constants.SCANS_DIR, mapName);
        locationNames = new String[locations.size()];
        locations.toArray(locationNames);
        Utils.populateSpinner(locationsSpinner, locationsSpinnerAdapter, locationNames);
    }
}
