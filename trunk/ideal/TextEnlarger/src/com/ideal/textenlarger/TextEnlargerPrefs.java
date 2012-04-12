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

import android.app.Dialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

/**
 * Pref settings for the Text Enlarger.
 */
public class TextEnlargerPrefs extends PreferenceActivity {

    private Preference mAppSettings = null;

    private Preference mHelp = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        addPreferencesFromResource(R.xml.prefs);
        
        if (!TextEnlargerService.phoneCheckPassed()){
            Intent i = new Intent();
            i.setAction("android.intent.action.VIEW");
            i.addCategory("android.intent.category.BROWSABLE");
            Uri uri = Uri.parse("http://apps4android.org/textenlarger/unsupported_device.html");
            i.setData(uri);
            startActivity(i);
            finish();
            return;
        }
        
        if (!TextEnlargerService.isServiceInitialized()) {
            showEnableDialog();
        }

        final PreferenceActivity self = this;

        mAppSettings = findPreference("app_settings");
        mAppSettings.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent();
                i.setClass(self, ApplicationsListActivity.class);
                startActivity(i);
                return true;
            }
        });

        mHelp = findPreference("help");
        mHelp.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                Intent i = new Intent();
                i.setAction("android.intent.action.VIEW");
                i.addCategory("android.intent.category.BROWSABLE");
                Uri uri = Uri.parse("http://apps4android.org/textenlarger");
                i.setData(uri);
                startActivity(i);
                return true;
            }
        });
    }

    private void showEnableDialog() {
        Builder enableMessage = new Builder(this);

        String titleText = "Please enable Text Enlarger";
        enableMessage.setTitle(titleText);

        enableMessage
                .setMessage("You need to enable the Text Enlarger under the Accessibility settings before you can use it.");

        enableMessage.setPositiveButton("Take me to the Accessibility settings",
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent();
                        i.setClassName("com.android.settings",
                                "com.android.settings.AccessibilitySettings");
                        startActivity(i);
                        finish();
                    }
                });

        enableMessage.setNegativeButton("Quit", new Dialog.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        });

        enableMessage.setCancelable(true);
        enableMessage.show();
    }
}
