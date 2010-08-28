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

/**
 * Activity for creating and editing maps.
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
import com.whereabout.common.Constants;
import com.whereabout.common.Utils;
import com.whereabout.location.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import java.util.ArrayList;

public class CreateMapActivity extends Activity {

    private static final String TAG = "CreateMapActivity";

    private MenuItem editMenuitem = null;

    private Spinner mapImageSpinner;

    private Button createMapButton;

    private EditText txtMapArea, txtMapBuilding, txtMapFloor;

    private ArrayAdapter<String> mapImageSpinnerAdapter;

    private String[] mapNames;

    private String[] mapImageNames;

    private String mapImageFileName, mapName;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.create_map);

        mapImageSpinner = (Spinner) findViewById(R.id.map_image_spinner);
        createMapButton = (Button) findViewById(R.id.create_map);
        txtMapArea = (EditText) findViewById(R.id.map_area);
        txtMapBuilding = (EditText) findViewById(R.id.map_building);
        txtMapFloor = (EditText) findViewById(R.id.map_floor);
        mapImageSpinnerAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item);
        mapImageSpinnerAdapter
                .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        populateMapImageSpinner();

        mapImageSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3) {
                mapImageFileName = mapImageNames[position];
            }

            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        createMapButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                String area = txtMapArea.getText().toString().trim().replace(' ', '-');
                String building = txtMapBuilding.getText().toString().trim().replace(' ', '-');
                String floor = txtMapFloor.getText().toString().trim().replace(' ', '-');
                if (area.trim().equals("")) {
                    area = "area";
                }
                if (building.trim().equals("")) {
                    building = "building";
                }
                if (floor.trim().equals("")) {
                    floor = "floor";
                }
                Log.d(TAG, "Creating: " + area + "_" + building + "_" + floor);
                openEditMapActivity(CreateMapActivity.this, area + "_" + building + "_"
                        + floor, mapImageFileName);
            }
        });
    }

    /**
     * Called when the menu is first created
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        editMenuitem = menu.add("Edit map");
        return true;
    }

    /**
     * Handles menu option selection.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == editMenuitem) {
            displayExistingMapsForEdit();
        }
        return false;
    }

    private void displayExistingMapsForEdit() {
        getMapNames();
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Select Map");
        ListView listView = new ListView(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, mapNames);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                mapName = mapNames[position];
                openEditMapActivity(CreateMapActivity.this, mapName, null);
                alertDialog.dismiss();
            }
        });
        alertDialog.setView(listView);
        alertDialog.show();
    }

    public static void openEditMapActivity(Context ct, String mapName, String mapImageName) {
        Intent i = new Intent();
        i.setClass(ct, GenerateXYLocationsActivity.class);
        i.putExtra(Constants.KEY_MAP_NAME, mapName);
        i.putExtra(Constants.KEY_MAP_IMAGE_FILE, mapImageName);
        ct.startActivity(i);
    }
    
    private void getMapNames() {
        ArrayList<String> maps = Utils.getMapNames(Constants.MAP_DIR);
        mapNames = new String[maps.size()];
        maps.toArray(mapNames);
    }

    private void populateMapImageSpinner() {
        ArrayList<String> images = Utils.getMapImageNames(Constants.MAP_DIR + "/images");
        mapImageNames = new String[images.size() + 1];
        images.toArray(mapImageNames);
        mapImageNames[mapImageNames.length - 1] = "none";
        Utils.populateSpinner(mapImageSpinner, mapImageSpinnerAdapter, mapImageNames);
    }
}
