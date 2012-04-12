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


package com.googlecode.eyesfree.keyboardtutor;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

/**
 * This activity displays information on the name of the application, the current version,
 * and links to Google's tos and privacy policy.
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class AboutActivity extends Activity {
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.about_page);        
        
        Button privacyPolicyButton = (Button)findViewById(R.id.privacy_policy_button);
        privacyPolicyButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW, 
                        Uri.parse("http://m.google.com/privacy"));        
                startActivity(intent);
            }
        });
        
        Button tosButton = (Button)findViewById(R.id.terms_of_service_button);
        tosButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(
                        Intent.ACTION_VIEW, 
                        Uri.parse("http://m.google.com/tos"));        
                startActivity(intent);
            }
        });
        
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);

            String versionName = packageInfo.versionName;
            
            TextView version = (TextView)findViewById(R.id.version_text);
            version.setText(getString(R.string.version_name, versionName));
        } catch (NameNotFoundException e) {
            Log.d("AboutActivity", "Couldn't find package name.", e);
        }
    }
}
