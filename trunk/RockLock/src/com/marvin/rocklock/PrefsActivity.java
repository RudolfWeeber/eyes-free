package com.marvin.rocklock;

import android.os.Bundle;
import android.preference.PreferenceActivity;

/**
 * Prefs activity to set whether or not Rock Lock is used as a pre-lock screen.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class PrefsActivity extends PreferenceActivity  {

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.prefs);
  }




}
