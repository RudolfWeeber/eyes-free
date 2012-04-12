/*
 * Copyright (C) 2010 The IDEAL Group
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
package com.ideal.textenlarger;


import android.app.ListActivity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Presents the user with a list of all the apps they have installed.
 * If they uncheck an app, Text Enlarger will not run for that app.
 * This is needed to block Text Enlarger from running on incompatible apps.
 *
 * This was taken from a checkbox list tutorial at anddev.org:
 * http://www.anddev.org/extended_checkbox_list__extension_of_checkbox_text_list_tu-t5734.html
 */
public class ApplicationsListActivity extends ListActivity {
	
	private ExtendedCheckBoxListAdapter mListAdapter;
	
    // Create CheckBox List Adapter, cbla
    private String[] items = {
        "item"
    };
    
    private HashMap<String, String> labelToPackageName;
    private SharedPreferences prefs;
    private Editor prefsEditor;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsEditor = prefs.edit();
        
        if (!prefs.contains("com.android.browser")){
            prefsEditor.putBoolean("com.android.browser", false);
            prefsEditor.commit();
        }
        
        labelToPackageName = new HashMap<String, String>();
        ArrayList<String> appsList = new ArrayList<String>();
        PackageManager pmPack;
        pmPack = getPackageManager();
        List<PackageInfo> packinfo = pmPack.getInstalledPackages(PackageManager.GET_ACTIVITIES);
        for (int i = 0; i < packinfo.size(); i++) {
            if ((packinfo.get(i).applicationInfo != null)) {
                String label = packinfo.get(i).applicationInfo.loadLabel(pmPack).toString();
                appsList.add(label);
                labelToPackageName.put(label, packinfo.get(i).packageName);
            }
        }
        Collections.sort(appsList);
        items = appsList.toArray(new String[]{});
        
        setContentView(R.layout.main);
        
        // Build the list adapter
        mListAdapter = new ExtendedCheckBoxListAdapter(this);
        
        // Add some items        
        for (int k = 0; k < items.length; k++) {
            String packageName = labelToPackageName.get(items[k]);
            boolean checked = prefs.getBoolean(packageName, true);
            mListAdapter.addItem(new ExtendedCheckBox(items[k], checked));
        }
        
        // Bind it to the activity!
        setListAdapter(mListAdapter);
    }
    
    public void setCheckedState(String label, boolean value){
        String packageName = labelToPackageName.get(label);
        prefsEditor.putBoolean(packageName, value);
        prefsEditor.commit();       
    }
    
}
