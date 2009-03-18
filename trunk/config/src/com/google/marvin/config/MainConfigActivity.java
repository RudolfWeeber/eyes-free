package com.google.marvin.config;

import com.google.tts.R;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;

public class MainConfigActivity extends PreferenceActivity  {

  private MainConfigActivity self;


  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    addPreferencesFromResource(R.xml.prefs);
    Preference previewPref = findPreference("install");
    previewPref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
        Intent installerIntent = new Intent(self, InstallerActivity.class);
        self.startActivity(installerIntent);
        return true;
      }
    });
    getPackageManager().addPackageToPreferred(this.getPackageName());
  }




}
