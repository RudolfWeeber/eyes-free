package com.google.marvin.there;

import com.google.tts.TTS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

public class NavigationActivity extends Activity {

  private LocationManager locationManager;
  private Location currentLocation;
  private LocationListener gpsLocationListener = new LocationListener() {
    public void onLocationChanged(Location arg0) {
      currentLocation = arg0;
      distanceTextView.setText(Double.toString(currentLocation.getAccuracy()));
    }

    public void onProviderDisabled(String arg0) {
      currentLocation = null;
    }

    public void onProviderEnabled(String arg0) {
    }

    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
      if (arg1 != LocationProvider.AVAILABLE) {
        currentLocation = null;
        distanceTextView.setText("GPS Unavailable");
      }
    }
  };


  public TTS tts;
  private TTS.InitListener ttsInitListener = new TTS.InitListener() {
    public void onInit(int arg0) {
      tts.speak("Navigation started. Acquiring GPS.", 0, null);
    }
  };

  private Place destination;
  private TextView distanceTextView;
  private Compass compass;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent startingIntent = getIntent();
    String placeName = startingIntent.getStringExtra("DESTINATION");

    DbManager db = new DbManager(this);

    destination = db.get(placeName);

    if (destination == null) {
      finish();
    }

    compass = new Compass(this);

    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);

    setContentView(R.layout.main);
    distanceTextView = (TextView) findViewById(R.id.distanceTextView);

    tts = new TTS(this, ttsInitListener, true);
  }


  @Override
  protected void onDestroy() {
    compass.shutdown();
    locationManager.removeUpdates(gpsLocationListener);
    tts.shutdown();
    super.onDestroy();
  }

  // Distance returned is in m
  private double distanceFromDestination(Location startLocation) {
    if (startLocation == null) {
      return -1;
    }
    double lat1 = startLocation.getLatitude();
    double lon1 = startLocation.getLongitude();
    double lat2 = Double.parseDouble(destination.lat);
    double lon2 = Double.parseDouble(destination.lon);

    double R = 6371; // earth's mean radius in km
    double dLat = (lat2 - lat1) * Math.PI / 180.0;
    double dLon = (lon2 - lon1) * Math.PI / 180.0;


    lat1 = lat1 * Math.PI / 180.0;
    lat2 = lat2 * Math.PI / 180.0;

    double a =
        Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2)
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    double d = R * c;
    return d * 1000;
  }


  private double getBearingToDestination(Location startLocation) {
    if (startLocation == null) {
      return -1;
    }
    double lat1 = startLocation.getLatitude();
    double lon1 = startLocation.getLongitude();
    double lat2 = Double.parseDouble(destination.lat);
    double lon2 = Double.parseDouble(destination.lon);
    lat1 = lat1 * Math.PI / 180.0;
    lat2 = lat2 * Math.PI / 180.0;
    double dLon = (lon2 - lon1) * Math.PI / 180.0;

    double y = Math.sin(dLon) * Math.cos(lat2);
    double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
    return ((Math.atan2(y, x) * 180 / Math.PI) + 360) % 360;
  }

  private void speakSimpleDirections() {
    if (tts == null) {
      return;
    }
    boolean closeToDest = false;
    Location myLocation = currentLocation;
    if (myLocation == null) {
      tts.speak("GPS unavailable, please retry later.", 0, null);
      return;
    }
    double distance = distanceFromDestination(myLocation);
    double accuracy = currentLocation.getAccuracy();
    if (accuracy > 50) {
      tts.speak("GPS signal weak.", 0, null);
    } else {
      if (distance < accuracy) {
        closeToDest = true;
        tts.speak("You are near your destination.", 0, null);
      } else {
        long roundedDistance = Math.round(distance);
        tts.speak("You are about " + roundedDistance + " meters away from your destination.", 0, null);        
      }
    }
    if (!closeToDest) {
      double currentHeading = compass.getCurrentHeadingValue();
      if (currentHeading == -1) {
        tts.speak("Please calibrate the compass by shaking your handset.", 1, null);
        return;
      }
      double bearingToDest = getBearingToDestination(myLocation);
      double headingDiff = bearingToDest - currentHeading;
      if (headingDiff < 0) {
        headingDiff = headingDiff + 360;
      }
      int index = (int) ((headingDiff * 100 + 1125) / 2250);
      tts.speak(HEADING_NAMES[index], 1, null);
    }

  }


  /** Called when a key is pressed down */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    Log.i("Nav activity", "key down");
    if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      Log.i("Nav activity", "center key");
      speakSimpleDirections();
    } else {
      super.onKeyDown(keyCode, event);
    }
    return false;
  }

  private static final String[] HEADING_NAMES =
      {"straight ahead", "ahead and a bit to the right", "ahead and to the right",
          "to the right and a bit ahead", "to the right", "to the right",
          "turn around, then to the left", "turn around, then a bit to the left", "turn around",
          "turn around, then a bit to the right", "turn around, then to the right", "to the left",
          "to the left", "to the left and a bit ahead", "ahead and to the left",
          "ahead and a bit to the left", "ahead"};

}
