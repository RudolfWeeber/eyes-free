package com.google.marvin.shell;

import com.google.marvin.shell.StreetLocator.StreetLocatorListener;

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

  private MarvinShell parent;

  private Guide self;
  private Compass compass;

  public Guide(MarvinShell parentActivity) {
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

    long currentTime = System.currentTimeMillis();
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
    long time = 0;
    boolean usingGPS = false;
    if ((networkLoc == null) && (gpsLoc == null)) {
      parent.tts.speak("Unable to determine location. Please retry later.", 0, null);
      return;
    } else if ((networkLoc == null) && (gpsLoc != null)) {
      currentLocation = gpsLoc;
      time = gpsLocLastUpdateTime;
      usingGPS = true;
    } else if ((networkLoc != null) && (gpsLoc == null)) {
      currentLocation = networkLoc;
      time = networkLocLastUpdateTime;
    } else {
      if (gpsLocLastUpdateTime + gpsTimeAdvantage > networkLocLastUpdateTime) {
        currentLocation = gpsLoc;
        time = gpsLocLastUpdateTime;
        usingGPS = true;
      } else {
        currentLocation = networkLoc;
        time = networkLocLastUpdateTime;
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



  /**
   * Obtains the reverse geocoded address for the specified location
   * 
   * @param currentLocation The location to reverse geocode
   * @return The exact address
   */
  private String getAbsAddress(Location currentLocation) {

    String address =
        locator.getAddress(currentLocation.getLatitude(), currentLocation.getLongitude());
    if (address != null) {
      return address;
    } else {
      return "";
    }
  }

  /**
   * Obtains the street names at the specified location.
   * 
   * @param currentLocation The location to find streets names at
   * @return String The street names at the intersection
   */
  private String getIntersection(Location currentLocation) {
    String[] addr =
        locator
            .getStreetIntersection(currentLocation.getLatitude(), currentLocation.getLongitude());
    String address = "";
    if (addr.length == 0) {
      return "";
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
    currentAddress = address;
    if (address.length() > 0) {
      parent.tts.speak("Near " + address, 1, null);
    }
    if (currentLocation != null) {
      locator.getStreetIntersectionAsync(currentLocation.getLatitude(), currentLocation
          .getLongitude());
    }
  }

  public void onIntersectionLocated(String[] streetnames) {
    if (streetnames.length == 0) {
      return;
    }
    currentIntersection = " Nearby streets are: ";
    for (String ad : streetnames) {
      currentIntersection += ad + " and ";
    }
    currentIntersection = currentIntersection.substring(0, currentIntersection.length() - 4);
    if (currentIntersection.length() > 0) {
      parent.tts.speak(currentIntersection, 1, null);
    }
    if (currentIntersection.length() + currentAddress.length() < 1) {
      parent.tts.speak("Unable to determine address from lat long. Please try again later.", 1,
          null);
    }
  }

}
