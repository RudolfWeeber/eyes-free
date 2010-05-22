package com.marvin.rocklock;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

/**
 * Prefs activity to set whether or not Rock Lock is used as a pre-lock screen.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class PrefsActivity extends PreferenceActivity  {

  private PrefsActivity self;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    addPreferencesFromResource(R.xml.prefs);
  }




}
