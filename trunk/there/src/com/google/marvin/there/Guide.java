package com.google.marvin.there;


import com.google.marvin.there.StreetLocator.StreetLocatorListener;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;

/**
 * Guide uses the magnetic compass, GPS/Network location provider, and the
 * Google Maps API to generate a meaningful spoken string to let users know
 * where they are.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class Guide implements Runnable, StreetLocatorListener {

  private LocationListener networkLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      networkLoc = arg0;
      networkLocLastUpdateTime = System.currentTimeMillis();
      networkFixCount++;
      parent.tts.speak("[tock]", 1, null);
      if (networkFixCount > minFixCount) {
        unregisterLocationServices();
        log("Network location", "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
        log("Network location", "Accuracy: " + arg0.getAccuracy());
        (new Thread(self)).start();
      }
    }

    public void onProviderDisabled(String arg0) {
      unregisterLocationServices();
      networkLoc = null;
      networkLocLastUpdateTime = -1;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        unregisterLocationServices();
        networkLoc = null;
        networkLocLastUpdateTime = -1;
        (new Thread(self)).start();
      }
    }

  };

  private LocationListener gpsLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      gpsLoc = arg0;
      gpsLocLastUpdateTime = System.currentTimeMillis();
      gpsFixCount++;
      parent.tts.speak("[tock]", 1, null);
      if (gpsFixCount > minFixCount) {
        unregisterLocationServices();
        log("GPS location", "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
        log("GPS location", "Accuracy: " + arg0.getAccuracy());
        (new Thread(self)).start();
      }
    }

    public void onProviderDisabled(String arg0) {
      unregisterLocationServices();
      gpsLoc = null;
      gpsLocLastUpdateTime = -1;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        unregisterLocationServices();
        gpsLoc = null;
        gpsLocLastUpdateTime = -1;
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

  private boolean triedGpsLastTime = false;
  private long networkLocLastUpdateTime = -1;
  private long gpsLocLastUpdateTime = -1;
  private long lastLocateTime = 0;
  private Location networkLoc = null;
  private Location gpsLoc = null;
  private StreetLocator locator = null;

  private int gpsFixCount = 0;
  private int networkFixCount = 0;
  private int minFixCount = 0; // lockwood suggested this should be 5, but 5
  // seems to take way too long

  private There parent;

  private Guide self;
  private Compass compass;

  public Guide(There parentActivity) {
    self = this;
    parent = parentActivity;
    locator = new StreetLocator(this);
    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);
    // Run the dummy listener a bit more often than once per hour to ensure that
    // the GPS ephemeris data is fresh so that when the location is trying to be
    // determined, a GPS fix can be acquired quickly.
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000000, 0,
        dummyLocationListener);
    compass = new Compass(parent);
  }


  public void speakLocation() {
    LocationManager locationManager =
        (LocationManager) parent.getSystemService(Context.LOCATION_SERVICE);

    networkLocLastUpdateTime = -1;
    gpsLocLastUpdateTime = -1;
    lastLocateTime = 0;
    networkLoc = null;
    gpsLoc = null;

    currentLocation = null;
    currentAddress = "";
    currentIntersection = "";

    if (triedGpsLastTime) {
      locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
          networkLocationListener);
      triedGpsLastTime = false;
    } else {
      locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
          gpsLocationListener);
      triedGpsLastTime = true;
    }

    String heading = compass.getCurrentHeading();
    if (heading.length() > 1) {
      parent.tts.speak(heading, 0, null);
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
    currentLocation = null;
    boolean usingGPS = false;
    if ((networkLoc == null) && (gpsLoc == null)) {
      parent.tts.speak("Unable to determine location. Please retry later.", 0, null);
      return;
    } else if ((networkLoc == null) && (gpsLoc != null)) {
      currentLocation = gpsLoc;
      usingGPS = true;
    } else if ((networkLoc != null) && (gpsLoc == null)) {
      currentLocation = networkLoc;
    } else {
      if (gpsLocLastUpdateTime + gpsTimeAdvantage > networkLocLastUpdateTime) {
        currentLocation = gpsLoc;
        usingGPS = true;
      } else {
        currentLocation = networkLoc;
      }
    }

    if (usingGPS) {
      parent.tts.speak("G P S", 1, null);
    } else {
      parent.tts.speak("network", 1, null);
    }
    if (currentLocation != null) {
      locator.getAddressAsync(currentLocation.getLatitude(), currentLocation.getLongitude());
    } else {
      if (currentIntersection.length() + currentAddress.length() < 1) {
        parent.tts.speak("Unable to determine location. Please try again later.", 1, null);
      }
    }

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
    compass.shutdown();
  }

  private void log(String tag, String message) {
    // Comment out the following line to turn off logging.
    Log.i(tag, message);
  }


  private Location currentLocation;
  private String currentAddress;
  private String currentIntersection;

  public void onAddressLocated(String address) {
    currentAddress = "";
    if (address.length() > 0) {
      // Drop the country
      address = address.substring(0, address.lastIndexOf(","));
      // Extract the state and zip and insert spaces in the state name
      // that the synthesizer will do the right thing.
      String rawStateZip = address.substring(address.lastIndexOf(",") + 1);
      String zip = rawStateZip.substring(rawStateZip.lastIndexOf(" ") + 1);
      String state = rawStateZip.substring(0, rawStateZip.lastIndexOf(" ") + 1);
      String stateZip = "";
      for (int i = 0; i < state.length(); i++) {
        stateZip = stateZip + state.charAt(i) + " ";
      }
      stateZip = stateZip + zip;
      currentAddress = address.substring(0, address.lastIndexOf(",")) + ". " + stateZip;

      parent.tts.speak("Near " + currentAddress, 1, null);
    }
    if (currentLocation != null) {
      double heading = compass.getCurrentHeadingValue();
      if (heading != -1) {
        locator.getStreetsInFrontAndBackAsync(currentLocation.getLatitude(), currentLocation
            .getLongitude(), compass.getCurrentHeadingValue());
      }
    }
  }

  public void onIntersectionLocated(String[] streetnames) {
    if (streetnames.length == 0) {
      return;
    }
    currentIntersection = "";
    for (String ad : streetnames) {
      if (currentAddress.indexOf(ad) == -1) {
        currentIntersection += ad + " and ";
      }
    }
    if (currentIntersection.length() > 5) {
      currentIntersection = currentIntersection.substring(0, currentIntersection.length() - 4);
      currentIntersection = " Nearby streets are: " + currentIntersection;
      parent.tts.speak(currentIntersection, 1, null);

    }
    if (currentIntersection.length() + currentAddress.length() < 1) {
      parent.tts.speak("Unable to determine address from lat long. Please try again later.", 1,
          null);
    }
  }


  public void onFrontBackLocated(String[] streetsFront, String[] streetsBack) {
    String currentIntersection = "";
    boolean spokeSomething = false;
    if (streetsFront.length > 0) {
      for (String ad : streetsFront) {
        if (currentAddress.indexOf(ad) == -1) {
          currentIntersection += ad + " and ";
        }
      }
      if (currentIntersection.length() > 5) {
        currentIntersection = currentIntersection.substring(0, currentIntersection.length() - 4);
        parent.tts.speak("Ahead. " + currentIntersection, 1, null);
        spokeSomething = true;
      }
    }

    currentIntersection = "";
    if (streetsBack.length > 0) {
      for (String ad : streetsBack) {
        if (currentAddress.indexOf(ad) == -1) {
          currentIntersection += ad + " and ";
        }
      }
      if (currentIntersection.length() > 5) {
        currentIntersection = currentIntersection.substring(0, currentIntersection.length() - 4);
        parent.tts.speak("Behind. " + currentIntersection, 1, null);
        spokeSomething = true;
      }
    }

    if (!spokeSomething) {
      locator.getStreetIntersectionAsync(currentLocation.getLatitude(), currentLocation
          .getLongitude());
    }
  }

}
