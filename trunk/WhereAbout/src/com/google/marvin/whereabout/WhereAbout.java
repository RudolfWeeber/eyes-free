/*
 * Copyright (C) 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.google.marvin.whereabout;

import com.google.tts.TTS;
import com.google.tts.TTS.InitListener;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

/**
 * This activity allows the user to obtain the current street, address,
 * or intersection. The location is displayed on the screen as well as
 * spoken to the user.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 *
 */
public class WhereAbout extends Activity implements Runnable {

  private ProgressDialog progressDiag = null;
  private TextView mainText = null;
  private LocationManager locationManager = null;
  private Location currentLocation = null;
  private TTS tts = null;
  private WhereAboutListener locListener = null;
  
  private boolean ttsLoaded = false;
  private long timeDown, pressTime;
  private float accuracy;
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    tts = new TTS(this, new InitListener() {
      public void onInit(int arg0) {
         ttsLoaded = true;
      }}, true);
    locationManager =
      (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    this.locListener = new WhereAboutListener();
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
        0, 0, locListener);
    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
        0, 0, locListener);
    setContentView(R.layout.main);
    mainText = (TextView) findViewById(R.id.mainText);
  }
  
  /** Called when a key is pressed down */
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      timeDown = System.currentTimeMillis();
      if (ttsLoaded) {
        tts.stop();
      }
    } else {
      super.onKeyDown(keyCode, event);
    }
    return false;
  }

  /** called when a key is released */ 
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      pressTime = System.currentTimeMillis() - timeDown;
      progressDiag =
          ProgressDialog.show(this, getString(R.string.progress_title),
          getString(R.string.progress_msg), true, false);
      if (ttsLoaded) {
        tts.speak(getString(R.string.progress_msg), 0, null);
      }
      (new Thread(this)).start();
    } else {
      super.onKeyUp(keyCode, event);
    }
    return false;
  }

  public synchronized void run() {
    // if pressTime > 500: Long press: intersection
    // else Short press: absolute street address
    locate(pressTime > 500);
  }
  
  /**
   * Obtains the address and presents it to the user by appending
   * appropriate prefixes.
   * @param abs Whether the full street address was requested
   */
  private void locate(boolean abs) {
    Location loc = null;
    String prefix = "";
    loc = currentLocation;
    if (loc != null) {
      accuracy = loc.getAccuracy();
      if (accuracy < 10) {              // Probably a GPS fix
        prefix = getString(R.string.abs_loc_prefix);
      } else if (accuracy < 200) {      // Probably a Wifi fix
        prefix = getString(R.string.wifi_loc_prefix);
      } else {                          // Probably a Cell tower fix
        prefix = getString(R.string.nw_loc_prefix);
      }
    } else {                            // Cannot find a fix
      loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
      prefix = getString(R.string.unknown_loc_prefix) + " " +
          getString(R.string.prev_loc_prefix);
    }
    String address = null;
    String speakStr = "";
    if (abs) {
      address = getAbsAddress(loc);
      speakStr = prefix + " " + address;
    } else {
      address = getIntersection(loc);
      if (address.contains(" and ")) {  // Intersection
        speakStr = prefix + " " + getString(R.string.insersection_prefix) +
            address;
      } else {  // Not an intersection
        speakStr = prefix + " " + address;
      }
    }
    Bundle data = new Bundle();
    data.putString("address", address);
    data.putString("speakstr", speakStr);
    Message msg = new Message();
    msg.setData(data);
    handler.sendMessage(msg);
  }

  /**
   * Dismisses th progress dialog, and displays result.
   */
  private Handler handler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
        progressDiag.dismiss();
        displayAndSpeak(msg.getData().getString("address"),
            msg.getData().getString("speakstr"));
    }
  };

  /**
   * Displays the specified string on the screen and speaks it.
   * @param dispStr The string to display
   * @param speakStr The string to speak
   */
  private void displayAndSpeak(String dispStr, String speakStr) {
    if (dispStr == null || speakStr == null) {
      return;
    }
    mainText.setText(dispStr);
    if (ttsLoaded) {
      Log.d("Locator", "Speaking: " + speakStr);
      tts.speak(speakStr, 0, null);
    }
  }

  /**
   * Obtains the reverse geocoded address for the specified location
   * @param currentLocation The location to reverse geocode
   * @return
   */
  private String getAbsAddress(Location loc) {
    
    String address = 
        StreetLocator.getAddress(loc.getLatitude(), loc.getLongitude());
    if (address != null) {
      return address;
    } else {
      return getString(R.string.failed_abs);
    }
  }
  
  /**
   * Obtains the street names at the specified location.
   * @param currentLocation The location to find streets names at
   * @return
   */
  private String getIntersection(Location loc) {
    String[] addr =
        StreetLocator.getStreetIntersection(loc.getLatitude(),
        loc.getLongitude());
    String address = "";
    if (addr.length == 0) {
      return getString(R.string.failed_intersection);
    }
    for (String ad : addr) {
      address += ad + " and ";
    }
    address = address.substring(0, address.length() - 4);
    return address;
  }
  
  @Override
  public void onDestroy() {
    locationManager.removeUpdates(locListener);
    super.onDestroy();
  }
  
  /**
   * Listener to register for location updates.  
   */
  private class WhereAboutListener implements LocationListener {
    /**
     * Called when the location changes.
     */
    public void onLocationChanged(Location arg0) {
      currentLocation = arg0;
    }

    /**
     * Called when the location provider's status changes.
     */
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        currentLocation = null;
      }
    }

    public void onProviderDisabled(String provider) {
      currentLocation = null;
    }
    
    public void onProviderEnabled(String provider) {
    }
  }  
}