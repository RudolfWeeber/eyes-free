package com.google.marvin.shell;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

public class Guide implements Runnable {

  private LocationListener networkLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      networkLoc = arg0;
      networkLocLastUpdate = System.currentTimeMillis();
      networkFixCount++;
      parent.tts.speak("[tock]", 1, null);
      if (networkFixCount > minFixCount) {
        unregisterLocationServices();
        Log.i("Network location", "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
        Log.i("Network location", "Accuracy: " + arg0.getAccuracy());
        (new Thread(self)).start();
      }
    }

    public void onProviderDisabled(String arg0) {
      unregisterLocationServices();
      networkLoc = null;
      networkLocLastUpdate = -1;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        unregisterLocationServices();
        networkLoc = null;
        networkLocLastUpdate = -1;
        (new Thread(self)).start();
      }
    }

  };

  private LocationListener gpsLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      gpsLoc = arg0;
      gpsLocLastUpdate = System.currentTimeMillis();
      gpsFixCount++;
      parent.tts.speak("[tock]", 1, null);
      if (gpsFixCount > minFixCount) {
        unregisterLocationServices();
        Log.i("GPS location", "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
        Log.i("GPS location", "Accuracy: " + arg0.getAccuracy());
        (new Thread(self)).start();
      }
    }

    public void onProviderDisabled(String arg0) {
      unregisterLocationServices();
      gpsLoc = null;
      gpsLocLastUpdate = -1;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        unregisterLocationServices();
        gpsLoc = null;
        gpsLocLastUpdate = -1;
        LocationManager locationManager =
            (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
            networkLocationListener);
      }
    }

  };

  private LocationListener dummyLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
    }
    public void onProviderDisabled(String arg0) {
    }
    public void onProviderEnabled(String arg0) {
    }
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
    }
  };

  private long networkLocLastUpdate = -1;
  private long gpsLocLastUpdate = -1;
  private long lastLocateTime = 0;
  private Location networkLoc = null;
  private Location gpsLoc = null;
  private StreetLocator locator = null;

  private int gpsFixCount = 0;
  private int networkFixCount = 0;
  private int minFixCount = 0; // lockwood suggested this should be 5, but 5
  // seems to take way too long

  private MarvinShell parent;

  private Guide self;

  public Guide(MarvinShell parentActivity) {
    self = this;
    parent = parentActivity;
    locator = new StreetLocator();
    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);
    // Run the dummy listener a bit more often than once per hour to ensure that
    // the GPS ephemeris data is fresh so that when the location is trying to be
    // determined, a GPS fix can be acquired quickly.
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000000, 0,
        dummyLocationListener);
  }

  private long lastTouchTime = 0;
  private long switchTime = 30000;

  public void speakLocation() {
    // parent.tts.speak("Obtaining location.", 0, null);

    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);

    long currentTime = System.currentTimeMillis();
    if (currentTime - lastTouchTime > switchTime) {
      lastTouchTime = currentTime;
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
          gpsLocationListener);
    } else {
      lastTouchTime = 0;
      locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
          networkLocationListener);
    }
  }


  public synchronized void run() {
    locate();
  }


  private void locate() {
    // Ignore all events after the first event if there is a burst of events
    if (System.currentTimeMillis() - lastLocateTime < 5000) {
      return;
    }
    lastLocateTime = System.currentTimeMillis();
    long gpsTimeAdvantage = 300000;
    Location loc = null;
    long time = 0;
    boolean usingGPS = false;
    if ((networkLoc == null) && (gpsLoc == null)) {
      parent.tts.speak("Unable to determine location. Please retry later.", 0, null);
      return;
    } else if ((networkLoc == null) && (gpsLoc != null)) {
      loc = gpsLoc;
      time = gpsLocLastUpdate;
      usingGPS = true;
    } else if ((networkLoc != null) && (gpsLoc == null)) {
      loc = networkLoc;
      time = networkLocLastUpdate;
    } else {
      if (gpsLocLastUpdate + gpsTimeAdvantage > networkLocLastUpdate) {
        loc = gpsLoc;
        time = gpsLocLastUpdate;
        usingGPS = true;
      } else {
        loc = networkLoc;
        time = networkLocLastUpdate;
      }
    }

    String address = getIntersection(loc);
    if (address.contains(" and ")) { // Intersection
      address = "Near the intersection of " + address;
    } else {
      address = "Near " + getAbsAddress(loc);
    }

    parent.tts.speak(address, 0, null);
    if (usingGPS) {
      parent.tts.speak("G P S", 1, null);
    } else {
      parent.tts.speak("network", 1, null);
    }


  }



  /**
   * Obtains the reverse geocoded address for the specified location
   * 
   * @param currentLocation The location to reverse geocode
   * @return
   */
  private String getAbsAddress(Location currentLocation) {

    String address =
        locator.getAddress(currentLocation.getLatitude(), currentLocation.getLongitude());
    if (address != null) {
      return address;
    } else {
      return "Unable to determine address.";
    }
  }

  /**
   * Obtains the street names at the specified location.
   * 
   * @param currentLocation The location to find streets names at
   * @return
   */
  private String getIntersection(Location currentLocation) {
    String[] addr =
        locator
            .getStreetIntersection(currentLocation.getLatitude(), currentLocation.getLongitude());
    String address = "";
    if (addr.length == 0) {
      return "Unable to determine address.";
    }
    for (String ad : addr) {
      address += ad + " and ";
    }
    address = address.substring(0, address.length() - 4);
    return address;
  }

  private void unregisterLocationServices() {
    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);
    locationManager.removeUpdates(networkLocationListener);
    locationManager.removeUpdates(gpsLocationListener);
    gpsFixCount = 0;
    networkFixCount = 0;
  }

  public void shutdown() {
    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);
    locationManager.removeUpdates(dummyLocationListener);
    unregisterLocationServices();
  }

}
