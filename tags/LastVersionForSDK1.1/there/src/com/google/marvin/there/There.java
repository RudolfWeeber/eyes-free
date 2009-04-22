package com.google.marvin.there;

import com.google.tts.TTS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class There extends Activity {

  private There self;
  
  public TTS tts;
  private DbManager db;

  private PlacesListView placesListView;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    self = this;
    db = new DbManager(this);
  }
  
  public void onResume(){
    super.onResume();
    refreshLocationsList();
  }

  
  private void refreshLocationsList() {
    placesListView = null;
    
    placesListView = new PlacesListView(this);

    String[] from = new String[] {"Name"};
    int[] to = new int[] {R.id.text1};

    Cursor c = db.getCursor();
    startManagingCursor(c);

    SimpleCursorAdapter cAdapter = new SimpleCursorAdapter(this, R.layout.place, c, from, to);
    placesListView.setAdapter(cAdapter);
    placesListView.setOnItemClickListener(new OnItemClickListener(){
      public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Intent intent = new Intent(self, NavigationActivity.class);
        intent.putExtra("DESTINATION", ((TextView)arg1).getText());
        startActivity(intent);   
      }      
    });

    setContentView(placesListView);
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(0, 0, 0, "Mark current location").setIcon(
        android.R.drawable.ic_menu_edit);
    return super.onCreateOptionsMenu(menu);
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case 0:
        int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
        Intent intent = new Intent(this, SetLocationForm.class);
        startActivity(intent);
        break;
    }
    return super.onOptionsItemSelected(item);
  }


}
