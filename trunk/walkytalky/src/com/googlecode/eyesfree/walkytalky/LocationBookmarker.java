/*
 * Copyright (C) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.walkytalky;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import java.util.ArrayList;

/**
 * Enables the user to bookmark their current location
 *
 * @author clchen@google.com (Charles L. Chen)
 */

public class LocationBookmarker extends Activity {
    private static final String FAVORITE_DESTINATIONS_PREFS_KEY = "FAVORITE_DESTINATIONS";

    private EditText addressEditText;

    private Button saveButton;

    private ArrayList<String> favoriteDestinations;

    private SharedPreferences prefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String address = getIntent().getExtras().getString("LOCATION");

        favoriteDestinations = new ArrayList<String>();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String[] savedDests = prefs.getString(FAVORITE_DESTINATIONS_PREFS_KEY, "").split("\n");
        for (int i = 0; i < savedDests.length; i++) {
            favoriteDestinations.add(savedDests[i]);
        }

        setContentView(R.layout.locationbookmarker);

        addressEditText = (EditText) findViewById(R.id.location_EditText);
        addressEditText.setText(address);

        saveButton = (Button) findViewById(R.id.saveLocation_Button);
        saveButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                favoriteDestinations.add(0, addressEditText.getText().toString());
                String savedDests = "";
                for (int i = 0; i < favoriteDestinations.size(); i++) {
                    savedDests = savedDests + favoriteDestinations.get(i) + "\n";
                }
                Editor editor = prefs.edit();
                editor.putString(FAVORITE_DESTINATIONS_PREFS_KEY, savedDests);
                editor.commit();
                finish();
            }
        });
    }

}
