/*
 * Copyright 2010 Google Inc.
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

package com.marvin.preferences;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

/**
 * The main activity for the Accessibility Settings Manager. This class
 * essentially acts as a launcher for the preference activities for
 * accessibility services installed on the device. An activity that wishes to be
 * listed here should declare an intent filter with both of the following.
 * action: android.intent.action.MAIN category:
 * android.accessibilityservice.SERVICE_SETTINGS
 *
 * @author caseyburkhardt@google.com (Casey Burkhardt)
 */
public class AccessibilityServicesListActivity extends ListActivity {

    public static final String ACTIVITY_ACCESSIBILITY_SERVICE_SETTINGS =
            "android.accessibilityservice.SERVICE_SETTINGS";

    private ArrayAdapter<AccessibilityServiceResolveInfo> mAppListAdapter;

    private List<AccessibilityServiceResolveInfo> mAppList;

    private PackageManager mPackageManager;
    
    private AccessibilityServicesListActivity self = this;
    
    @Override
    public void onResume() {
        super.onResume();
        mPackageManager = getPackageManager();
        mAppList = getCompatibleServicesList();
        mAppListAdapter = new ArrayAdapter<AccessibilityServiceResolveInfo>(
                this, R.layout.list_layout, R.id.list_item, mAppList);
        setListAdapter(mAppListAdapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        AccessibilityServiceResolveInfo info = mAppListAdapter.getItem((int) id);
        Intent launchIntent = new Intent();
        launchIntent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        launchIntent.setClassName(
                info.mResolveInfo.activityInfo.packageName, info.mResolveInfo.activityInfo.name);
        try {
            startActivity(launchIntent);
        } catch (ActivityNotFoundException e) {
            // If for some reason the activity can't be resolved, display an
            // error.
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.launch_failed_message)).setCancelable(true)
                    .setPositiveButton(
                            getString(R.string.ok_button), new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    dialog.dismiss();
                                }
                            }).create().show();
        }
    }

    /**
     * Creates a List of ResolveInfo objects based on activities responding to
     * the following intent filter. action: android.intent.action.MAIN category:
     * android.accessibilityservice.SERVICE_SETTINGS
     *
     *@return an ArrayList containing ResolveInfo objects for each
     *         accessibility service that has defined a settings activity.
     */
    private List<AccessibilityServiceResolveInfo> getCompatibleServicesList() {
        Intent targetIntent = new Intent(Intent.ACTION_MAIN);
        targetIntent.addCategory(ACTIVITY_ACCESSIBILITY_SERVICE_SETTINGS);
        return convertResolveInfoList(
                mPackageManager.queryIntentActivities(targetIntent, 0));
    }

    /**
     * Converts a given list of ResolveInfo objects to AccessibilityServiceInfo
     * objects
     *
     * @param list a list of ResolveInfo objects
     * @return a list of AccessibilityServiceObjects
     */
    public List<AccessibilityServiceResolveInfo> convertResolveInfoList(
            List<ResolveInfo> list) {
        ArrayList<AccessibilityServiceResolveInfo> result = new ArrayList<AccessibilityServiceResolveInfo>();
        for (ResolveInfo info : list) {
            result.add(new AccessibilityServiceResolveInfo(info));
        }
        return result;
    }

    /**
     * A simple wrapper of the ResolveInfo class used to override toString so our
     * list items appear correctly when using an ArrayAdapter.
     */
    private class AccessibilityServiceResolveInfo {
        private ResolveInfo mResolveInfo;

        public AccessibilityServiceResolveInfo(ResolveInfo info) {
            mResolveInfo = info;
        }

        @Override
        public String toString() {
            String result = mResolveInfo
                    .loadLabel(self.getPackageManager()).toString();
            if (result.length() == 0) {
                if (mResolveInfo.activityInfo.labelRes != 0) {
                    result = getString(mResolveInfo.activityInfo.labelRes);
                } else {
                    result = mResolveInfo.activityInfo.name.toString();
                }
            }
            return String.format(self.getString(R.string.template_list_item_text), result);
        }
    }
}
