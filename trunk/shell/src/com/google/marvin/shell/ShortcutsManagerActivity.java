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

package com.google.marvin.shell;

import com.google.marvin.utils.UserTask;
import com.google.marvin.widget.GestureOverlay.Gesture;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Allows users to customize their Eyes-Free Shell shortcuts.
 *
 * @author mgl@google.com (Matthew Lee)
 */
public class ShortcutsManagerActivity extends Activity {

    private static final int kNumButtons = 8;

    /**
     * Maps from index of button in shortcuts manager list (0 to 7) to digit
     * which on a phone corresponds to the directional gesture that the button
     * represents (1, 2, 3, 4, 6, 7, 8, or 9).
     */
    private static final HashMap<Integer, Integer> indexToButtonMapping = new HashMap<
            Integer, Integer>();
    static {
        indexToButtonMapping.put(0, 1);
        indexToButtonMapping.put(1, 2);
        indexToButtonMapping.put(2, 3);
        indexToButtonMapping.put(3, 4);
        indexToButtonMapping.put(4, 6);
        indexToButtonMapping.put(5, 7);
        indexToButtonMapping.put(6, 8);
        indexToButtonMapping.put(7, 9);
    }

    /**
     * Maps from directional gesture to index of button in shortcuts manager
     * list (0 to 7)
     */
    private static final HashMap<Integer, Integer> gestureToIndexMapping = new HashMap<
            Integer, Integer>();
    static {
        gestureToIndexMapping.put(Gesture.UPLEFT, 0);
        gestureToIndexMapping.put(Gesture.UP, 1);
        gestureToIndexMapping.put(Gesture.UPRIGHT, 2);
        gestureToIndexMapping.put(Gesture.LEFT, 3);
        gestureToIndexMapping.put(Gesture.RIGHT, 4);
        gestureToIndexMapping.put(Gesture.DOWNLEFT, 5);
        gestureToIndexMapping.put(Gesture.DOWN, 6);
        gestureToIndexMapping.put(Gesture.DOWNRIGHT, 7);
    }

    /**
     * Maps from index of button in shortcuts manager list (0 to 7) to
     * directional gesture
     */
    private static final HashMap<Integer, Integer> indexToGestureMapping = new HashMap<
            Integer, Integer>();
    static {
        indexToGestureMapping.put(0, Gesture.UPLEFT);
        indexToGestureMapping.put(1, Gesture.UP);
        indexToGestureMapping.put(2, Gesture.UPRIGHT);
        indexToGestureMapping.put(3, Gesture.LEFT);
        indexToGestureMapping.put(4, Gesture.RIGHT);
        indexToGestureMapping.put(5, Gesture.DOWNLEFT);
        indexToGestureMapping.put(6, Gesture.DOWN);
        indexToGestureMapping.put(7, Gesture.DOWNRIGHT);
    }

    private ShortcutsManagerActivity self;

    private ArrayList<AppEntry> launchableApps;

    private HashMap<Integer, MenuItem> menu;

    private Button[] buttons;

    // For each button, stores the currently selected position
    private Integer[] selectedIndices;

    // For each button, stores whether the button corresponds to a non-launch
    // shortcut, which cannot be changed by ShortcutsManagerActivity
    private Boolean[] fixedIndices;

    // Path to shortcuts file.
    private String efDirStr = "/sdcard/eyesfree/";

    private String filename = efDirStr + "shortcuts.xml";

    /** Called when the activity is first created. **/
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        buttons = new Button[kNumButtons];
        selectedIndices = new Integer[kNumButtons];
        fixedIndices = new Boolean[kNumButtons];
        for (int i = 0; i < selectedIndices.length; i++) {
            selectedIndices[i] = 0;
            fixedIndices[i] = false;
        }
        setContentView(R.layout.loading);
        new ProcessTask().execute();
    }

    private void loadUi() {
        setContentView(R.layout.shortcuts_manager);

        // Populate the selections in the AlertDialog
        ArrayList<String> launchableAppTitles = new ArrayList<String>();
        launchableAppTitles.add("(none)");

        // Get titles of launchable apps.
        for (int i = 0; i < launchableApps.size(); i++) {
            launchableAppTitles.add(launchableApps.get(i).getTitle());
        }

        // Find buttons.
        buttons[0] = (Button) findViewById(R.id.button1);
        buttons[1] = (Button) findViewById(R.id.button2);
        buttons[2] = (Button) findViewById(R.id.button3);
        buttons[3] = (Button) findViewById(R.id.button4);
        buttons[4] = (Button) findViewById(R.id.button6);
        buttons[5] = (Button) findViewById(R.id.button7);
        buttons[6] = (Button) findViewById(R.id.button8);
        buttons[7] = (Button) findViewById(R.id.button9);

        Button confirmButton = (Button) findViewById(R.id.confirm);

        // Load shortcuts.xml file
        menu = MenuLoader.loadMenu(this, filename);
        Iterator<Map.Entry<Integer, MenuItem>> it = menu.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Integer, MenuItem> entry = it.next();
            MenuItem item = entry.getValue();

            int buttonIndex = gestureToIndexMapping.get(entry.getKey());

            // Non-launch actions cannot be changed
            if (!item.action.equalsIgnoreCase("launch")) {
                fixedIndices[buttonIndex] = true;
                buttons[buttonIndex].setText(
                        indexToButtonMapping.get(buttonIndex) + " - Special action");
            } else {
                int appTitleIndex = launchableAppTitles.indexOf(item.label);

                // If title not in list of launchable apps, set to "none"
                if (appTitleIndex == -1) {
                    appTitleIndex = 0;
                }
                buttons[buttonIndex].setText(indexToButtonMapping.get(buttonIndex) + " - "
                        + launchableAppTitles.get(appTitleIndex));
                selectedIndices[buttonIndex] = appTitleIndex;

            }
        }

        // Set up buttons to listen for clicks and show AlertDialog on click.
        for (int i = 0; i < kNumButtons; i++) {
            final ArrayAdapter aa = new ArrayAdapter(
                    this, android.R.layout.simple_spinner_item, launchableAppTitles.toArray());
            final int gestureNumber = indexToButtonMapping.get(i);
            final int buttonIndex = i;
            buttons[i].setOnClickListener(new OnClickListener() {
                public void onClick(View view) {

                    // Non-launch actions cannot be changed, so clicking button
                    // does nothing
                    if (!fixedIndices[buttonIndex]) {

                        // Bring up dialog box
                        final Button button = (Button) view;
                        AlertDialog.Builder builder = new AlertDialog.Builder(self);

                        builder.setAdapter(aa, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface arg0, int index) {

                                // Update button text
                                button.setText(
                                        gestureNumber + " - " + aa.getItem(index).toString());

                                // Update stored selection
                                selectedIndices[buttonIndex] = index;

                                String name = aa.getItem(index).toString();
                                if ((index == 0) && name.equals("(none)")) {
                                    // Remove item from menu
                                    menu.remove(indexToGestureMapping.get(buttonIndex));
                                } else {
                                    // Create item and add to menu
                                    // Do a search to accomodate for the
                                    // index changing because of users
                                    // typing and filtering down the
                                    // list.
                                    MenuItem menuItem = null;
                                    for (int i = 0; i < launchableApps.size(); i++) {
                                        if (launchableApps.get(i).getTitle().equals(name)) {
                                            menuItem = new MenuItem(
                                                    name, "LAUNCH", "", launchableApps.get(i));
                                        }
                                    }
                                    if (menuItem != null) {
                                        menu.put(indexToGestureMapping.get(buttonIndex), menuItem);
                                    }
                                }
                            }
                        });

                        AlertDialog dialog = builder.create();
                        dialog.getListView().setTextFilterEnabled(true);
                        dialog.show();
                    }
                }
            });

        }

        confirmButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {
                try {
                    // Output xml
                    FileOutputStream fileOutputStream = new FileOutputStream(filename);
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(
                            fileOutputStream);

                    // Sort gestures by button number ordering
                    class GestureSorter implements Comparator {
                        public int compare(Object arg0, Object arg1) {
                            return ((Integer) arg0) - ((Integer) arg1);
                        }
                    }

                    ArrayList<Integer> keyList = new ArrayList<Integer>(menu.keySet());
                    Collections.sort(keyList, new GestureSorter());
                    Iterator<Integer> it = keyList.iterator();

                    String shellTag = "<shell>\n";
                    String shellCloseTag = "</shell>";

                    outputStreamWriter.write(shellTag);

                    while (it.hasNext()) {
                        int gesture = it.next();
                        MenuItem item = menu.get(gesture);
                        outputStreamWriter.write(item.toXml(gesture));
                    }

                    outputStreamWriter.write(shellCloseTag);
                    outputStreamWriter.close();

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                Intent mIntent = new Intent();
                setResult(RESULT_OK, mIntent);
                finish();
            }
        });
    }

    private class ProcessTask extends UserTask<Void, Void, ArrayList<AppEntry>> {
        @SuppressWarnings("unchecked")
        @Override
        public ArrayList<AppEntry> doInBackground(Void... params) {
            // search for all launchable apps
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            PackageManager pm = getPackageManager();
            List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
            ArrayList<AppEntry> appList = new ArrayList<AppEntry>();
            for (ResolveInfo info : apps) {
                String title = info.loadLabel(pm).toString();
                if (title.length() == 0) {
                    title = info.activityInfo.name.toString();
                }

                AppEntry entry = new AppEntry(title, info, null);
                appList.add(entry);
            }

            class AppEntrySorter implements Comparator {
                public int compare(Object arg0, Object arg1) {
                    String title0 = ((AppEntry) arg0).getTitle().toLowerCase();
                    String title1 = ((AppEntry) arg1).getTitle().toLowerCase();
                    return title0.compareTo(title1);
                }
            }
            Collections.sort(appList, new AppEntrySorter());

            // now that app tree is built, pass along to adapter
            return appList;
        }

        @Override
        public void onPostExecute(ArrayList<AppEntry> appList) {
            launchableApps = appList;
            loadUi();
        }
    }

}
