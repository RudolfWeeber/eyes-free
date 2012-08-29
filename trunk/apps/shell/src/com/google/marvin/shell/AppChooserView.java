/*
 * Copyright (C) 2008 Google Inc.
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

import java.util.ArrayList;
import java.util.Collections;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.speech.tts.TextToSpeech;
import android.util.AttributeSet;

/**
 * Allows the user to select the application they wish to start by moving
 * through the list of applications.
 *
 * @author clchen@google.com (Charles L. Chen)
 * @author sainsley@google.com (Sam Ainsley)
 */
public class AppChooserView extends ChooserView<AppInfo> {

    public AppChooserView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAppList(ArrayList<AppInfo> installedApps) {
        items = installedApps;
    }

    @Override
    public boolean matchesSearch(int index) {
        String title = items.get(index).getTitle().toLowerCase();
        return title.startsWith(currentString.toLowerCase());
    }

    @Override
    public void speakCurrentItem(boolean interrupt) {
        String name = items.get(currentIndex).getTitle();
        if (interrupt) {
            parent.tts.speak(name, TextToSpeech.QUEUE_FLUSH, null);
        } else {
            parent.tts.speak(name, TextToSpeech.QUEUE_ADD, null);
        }
        invalidate();
    }

    @Override
    public void startActionHandler() {
        currentString = "";
        parent.onAppSelected(items.get(currentIndex));
    }

    @Override
    public String getCurrentItemName() {
        return items.get(currentIndex).getTitle();
    }

    public void addApplication(AppInfo app) {
        synchronized (items) {
            items.add(app);
            Collections.sort(items);
        }
        currentIndex = 0;
        currentString = "";
    }

    public void removePackage(String packageName) {
        synchronized (items) {
            for (int i = 0; i < items.size(); ++i) {
                if (items.get(i).getPackageName().equals(packageName)) {
                    items.remove(i);
                    i--;
                }
            }
        }
        currentIndex = 0;
        currentString = "";
    }

    public boolean applicationExists(AppInfo app) {
        return items.contains(app);
    }

    public void uninstallCurrentApp() {
        String targetPackageName = items.get(currentIndex).getPackageName();
        Intent i = new Intent();
        i.setAction("android.intent.action.DELETE");
        i.setData(Uri.parse("package:" + targetPackageName));
        parent.startActivity(i);
    }

    public void showCurrentAppInfo() {
        String targetPackageName = items.get(currentIndex).getPackageName();
        Intent i = new Intent();
        try {
            // android.settings.APPLICATION_DETAILS_SETTINGS is the correct, SDK
            // blessed way of doing this - but it is only available starting in
            // GINGERBREAD.
            i.setAction("android.settings.APPLICATION_DETAILS_SETTINGS");
            i.setData(Uri.parse("package:" + targetPackageName));
            parent.startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                // If it isn't possible to use
                // android.settings.APPLICATION_DETAILS_SETTINGS,
                // try it again with the "pkg" magic key. See
                // ManageApplications.APP_PKG_NAME in:
                // src/com/android/settings/ManageApplications.java
                // under
                // http://android.git.kernel.org/?p=platform/packages/apps/Settings.git
                i.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails");
                i.putExtra("pkg", targetPackageName);
                parent.startActivity(i);
            } catch (ActivityNotFoundException e2) {
            }
        }
    }
}
