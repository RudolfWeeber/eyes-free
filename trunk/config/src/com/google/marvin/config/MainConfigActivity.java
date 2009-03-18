package com.google.marvin.config;

import com.google.tts.R;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

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
  }




}
