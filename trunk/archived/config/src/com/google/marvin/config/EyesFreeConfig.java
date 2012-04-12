package com.google.marvin.config;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class EyesFreeConfig extends Activity {
  
  private ListView appList; 
  private AppListAdapter appListAdapter; 
  
  
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appList = new ListView(this);
        appListAdapter = new AppListAdapter(this, getAppsList());
        appList.setAdapter(appListAdapter);        
        appList.setOnItemClickListener(new OnItemClickListener(){
          public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long rowId) {
            String packageName = appListAdapter.getPackageName((int) rowId);
            Uri marketUri = Uri.parse("market://search?q=pname:" + packageName);
            Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
            startActivity(marketIntent);
          }   
        });        
        setContentView(appList);
    }
    
    
    private ArrayList<AppDesc> getAppsList(){
      ArrayList<AppDesc> apps = new ArrayList<AppDesc>();
      AppDesc testApp = new AppDesc("com.google.tts", "TTS Lib", "This is the text to speech library.");
      apps.add(testApp);
      return apps;
    }
    
    
    
}