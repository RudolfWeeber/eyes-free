/**
 * Shortcuts Manager for Eyes-Free Shell
 * 
 * @author mgl@google.com (Matthew Lee)
 */

package com.google.marvin.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.google.marvin.utils.UserTask;
import com.google.marvin.widget.TouchGestureControlOverlay.Gesture;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ShortcutsManagerActivity extends Activity {
    private static final HashMap<Integer, Integer> indexToGestureMapping = new HashMap<Integer, Integer>();
    static {
        indexToGestureMapping.put(0, 1);
        indexToGestureMapping.put(1, 2);
        indexToGestureMapping.put(2, 3);
        indexToGestureMapping.put(3, 4);
        indexToGestureMapping.put(4, 6);
        indexToGestureMapping.put(5, 7);
        indexToGestureMapping.put(6, 8);
        indexToGestureMapping.put(7, 9);
    }

    private ShortcutsManagerActivity self;

    final String newline = System.getProperty("line.separator");

    private ArrayList<AppEntry> launchableApps;

    private Button[] b;

    private Integer[] selectedIndices;

    private String efDirStr = "/sdcard/eyesfree/";

    private String filename = efDirStr + "shortcuts.xml";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = this;
        b = new Button[8];
        selectedIndices = new Integer[8];
        for (int i=0; i<selectedIndices.length; i++){
            selectedIndices[i] = 0;
        }
        setContentView(R.layout.loading);
        new ProcessTask().execute();
    }

    private String itemXml(int gesture, AppEntry app) {
        StringBuilder sb = new StringBuilder();
        Formatter formatter = new Formatter(sb, Locale.US);

        formatter
                .format(
                        "  <item gesture='%d' label='%s' action='LAUNCH'>%s    <appInfo package='%s' class='%s' />%s  </item>%s",
                        gesture, app.getTitle(), newline, app.getPackageName(), app.getClassName(),
                        newline, newline);
        return formatter.toString();
    }

    private void loadUi() {
        setContentView(R.layout.shortcuts_manager);

        final int[] buttons = {
                1, 2, 3, 4, 6, 7, 8, 9
        };

        ArrayList<String> launchableAppTitles = new ArrayList<String>();
        launchableAppTitles.add("(none)");

        for (int i = 0; i < launchableApps.size(); i++) {
            launchableAppTitles.add(launchableApps.get(i).getTitle());
        }

        // TODO(mgl): See if there is a better way to do this.
        b[0] = (Button) findViewById(R.id.button1);
        b[1] = (Button) findViewById(R.id.button2);
        b[2] = (Button) findViewById(R.id.button3);
        b[3] = (Button) findViewById(R.id.button4);
        b[4] = (Button) findViewById(R.id.button6);
        b[5] = (Button) findViewById(R.id.button7);
        b[6] = (Button) findViewById(R.id.button8);
        b[7] = (Button) findViewById(R.id.button9);

        Button confirmButton = (Button) findViewById(R.id.confirm);

        for (int i = 0; i < 8; i++) {
            final ArrayAdapter aa = new ArrayAdapter(this, android.R.layout.simple_spinner_item,
                    launchableAppTitles.toArray());
            final int buttonId = indexToGestureMapping.get(i);
            final int buttonIndex = i;
            b[i].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    final Button button = (Button) view;
                    AlertDialog.Builder builder = new AlertDialog.Builder(self);
                    builder.setAdapter(aa, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface arg0, int index) {
                            button.setText(buttonId + " - " + aa.getItem(index).toString());
                            selectedIndices[buttonIndex] = index;
                        }
                    });
                    builder.create().show();
                }
            });

        }

        HashMap<Gesture, MenuItem> menu = MenuLoader.loadMenu(filename);

        Iterator<Map.Entry<Gesture, MenuItem>> it = menu.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<Gesture, MenuItem> entry = it.next();
            int appTitleIndex = launchableAppTitles.indexOf(entry.getValue().label);
            if (appTitleIndex != -1) {
                int bIndex = Arrays.binarySearch(buttons, entry.getKey().ordinal() + 1);
                Log.e("appTitleIndex", appTitleIndex + "");
                Log.e("bIndex", bIndex + "");
                b[bIndex].setText(indexToGestureMapping.get(bIndex) + " - "
                        + launchableAppTitles.get(appTitleIndex));
                selectedIndices[bIndex] = appTitleIndex;
            }
        }

        confirmButton.setOnClickListener(new View.OnClickListener() {

            public void onClick(View view) {

                try {
                    FileOutputStream fos = new FileOutputStream(filename);
                    OutputStreamWriter osw = new OutputStreamWriter(fos);
                    String shellTag = "<shell>";
                    String shellCloseTag = "</shell>";

                    osw.write(shellTag);
                    osw.write(newline);

                    String xmlEntry = "";

                    for (int i = 0; i < 8; i++) {
                        int listPos = selectedIndices[i];
                        if (listPos > 0) {
                            AppEntry entry = launchableApps.get(listPos - 1);
                            xmlEntry = itemXml(buttons[i], entry);
                            Log.e("debug", xmlEntry);
                            osw.write(xmlEntry);
                        }
                    }

                    osw.write(shellCloseTag);
                    osw.close();

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

            class appEntrySorter implements Comparator {
                public int compare(Object arg0, Object arg1) {
                    String title0 = ((AppEntry) arg0).getTitle().toLowerCase();
                    String title1 = ((AppEntry) arg1).getTitle().toLowerCase();
                    return title0.compareTo(title1);
                }
            }
            Collections.sort(appList, new appEntrySorter());

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
