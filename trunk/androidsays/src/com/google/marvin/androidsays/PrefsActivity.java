// Copyright 2008 Google Inc. All Rights Reserved.

package com.google.marvin.androidsays;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.WindowManager;

/**
 * Displays preferences
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class PrefsActivity extends PreferenceActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Have the system blur any windows behind this one.
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
        WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

    addPreferencesFromResource(R.xml.prefs);
  }


}
