// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.marvin.shell;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * @author credo@google.com (Tim Credo)
 */
public class SettingsShortcutChooserActivity extends ListActivity {

    static HashMap<String, String> settingsActions;

    static {
        settingsActions = new HashMap<String, String>();
        settingsActions.put(
                "Accessibility Settings", android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        settingsActions.put(
                "Application Settings", android.provider.Settings.ACTION_APPLICATION_SETTINGS);
        settingsActions.put(
                "Bluetooth Settings", android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        settingsActions.put("Date Settings", android.provider.Settings.ACTION_DATE_SETTINGS);
        settingsActions.put(
                "Input Method Settings", android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
        settingsActions.put("Internal Storage Settings",
                android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        settingsActions.put("Locale Settings", android.provider.Settings.ACTION_LOCALE_SETTINGS);
        settingsActions.put("Location Source Settings",
                android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        settingsActions.put("Manage Applications",
                android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        settingsActions.put(
                "Memory Card Settings", android.provider.Settings.ACTION_MEMORY_CARD_SETTINGS);
        settingsActions.put("Privacy Settings", android.provider.Settings.ACTION_PRIVACY_SETTINGS);
        settingsActions.put(
                "Security Settings", android.provider.Settings.ACTION_SECURITY_SETTINGS);
        settingsActions.put("Sound Settings", android.provider.Settings.ACTION_SOUND_SETTINGS);
        settingsActions.put("Wifi Settings", android.provider.Settings.ACTION_WIFI_SETTINGS);
        settingsActions.put(
                "Wireless Settings", android.provider.Settings.ACTION_WIRELESS_SETTINGS);
    }

    ArrayList<String> availableActions;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PackageManager pm = getPackageManager();
        availableActions = new ArrayList<String>();
        for (String key : settingsActions.keySet()) {
            if (pm.resolveActivity(new Intent(settingsActions.get(key)), 0) != null) {
                availableActions.add(key);
            }
        }
        Collections.sort(availableActions);
        ListAdapter adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, availableActions);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        String title = availableActions.get(position);
        String action = settingsActions.get(title);
        Intent data = new Intent();
        data.putExtra("TITLE", title);
        data.putExtra("ACTION", action);
        this.setResult(Activity.RESULT_OK, data);
        finish();
    }
}
