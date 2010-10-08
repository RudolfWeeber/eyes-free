/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.eyesfree.walkytalky;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import com.googlecode.eyesfree.walkytalky.Compass.HeadingListener;
import com.googlecode.eyesfree.walkytalky.StreetLocator.StreetLocatorListener;
import com.whereabout.location.LocationManager;

import java.util.ArrayList;

/**
 * Service that will take the user's current location and place it in the status notification bar.
 * Note that if the user is running TalkBack, the location will automatically be spoken because of
 * the way TalkBack handles status notifications.
 *
 * @author clchen@google.com (Charles L. Chen), hiteshk@google.com (Hitesh Khandelwal)
 */

public class PositionStatusNotificationService extends Service implements StreetLocatorListener {
    private static final String TAG = "PSNS"; // Tag used for logging

    private boolean isIndoors = false;

    private boolean needReset = true;

    private SharedPreferences mPrefs;

    private boolean mSpeak = false;

    private TextToSpeech mTts;
    
    private Compass mCompass;

    private boolean mPostMessage = true;

    private PositionStatusNotificationService self;

    NotificationManager mNotificationManager;

    Location location;

    long locLastUpdateTime = 0;

    long gpsLocLastUpdateTime = 0;

    private long locationUpdateWaitTime = 15000;

    private LocationManager locationManager;

    private WifiPointsOfInterestLocator wifiPoiLocator;

    private ArrayList<String> previousWifiPoi;

    private StreetLocator locator = null;

    private String currentAddress = "";

    private String currentIntersection = "";

    private String currentFrontBackStreets = "";
    
    private String previousCity = "";
    private String previousRawStateZip = "";

    private LocationListener locationListener = new LocationListener() {
        /*
         * Use GPS signal by default, fallbacks to network signal if GPS is unavailable
         */
        public void onLocationChanged(Location arg0) {
            location = arg0;
            if (!isIndoors
                    && (locLastUpdateTime + locationUpdateWaitTime < System.currentTimeMillis())) {
                locLastUpdateTime = System.currentTimeMillis();
                if (location.getProvider().equals("gps")) {
                    // GPS signal
                    updateLocationString();
                    gpsLocLastUpdateTime = System.currentTimeMillis();
                } else if (gpsLocLastUpdateTime + 2 * locationUpdateWaitTime < System
                        .currentTimeMillis()) {
                    // Network signal
                    updateLocationString();
                }
            }
        }

        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            location = null;
            gpsLocLastUpdateTime = -1;
            locLastUpdateTime = -1;
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    };

    GpsStatus.Listener mGpsStatusListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
            // This is a dummy listener. It's here because the Motorola Droid
            // GPS does not function correctly unless a status listener is set
            // for it.
        }
    };

    private LocationListener mWifiLocationListener = new LocationListener() {
        public void onLocationChanged(final Location loc) {
            boolean hasNewPoints = false;
            ArrayList<String> pointsOfInterest = wifiPoiLocator.getWifiLocationsOfInterest(loc);
            if (pointsOfInterest == null) {
                isIndoors = false;
                return;
            } else {
                isIndoors = true;
            }
            if (pointsOfInterest.size() > 0) {
                for (int i = 0; i < pointsOfInterest.size(); i++) {
                    if (!previousWifiPoi.contains(pointsOfInterest.get(i))) {
                        hasNewPoints = true;
                        break;
                    }
                }
            }
            if (hasNewPoints) {
                String message = "Near ";
                for (int i = 0; i < pointsOfInterest.size(); i++) {
                    message = message + pointsOfInterest.get(i) + " and ";
                }
                message = message.substring(0, message.lastIndexOf(" and "));
                sendNotification(message, message, "", message);
                previousWifiPoi = pointsOfInterest;
            }
        }

        public void onProviderDisabled(String provider) {
        }

        public void onProviderEnabled(String provider) {
        }

        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
    }
    
    public HeadingListener mHeadingListener = new HeadingListener(){
        @Override
        public void onHeadingChanged(String heading) {
            if (mPrefs.getBoolean("enable_compass", false) && (mTts != null)){
                mTts.speak("Heading " + heading, 0, null);
            }
        }        
    };

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        this.setForeground(true);

        String action = intent.getExtras().getString("ACTION");
        if (action.equals("STOP")) {
            unregisterLocationServices();
            if (mNotificationManager != null) {
                mNotificationManager.cancel(1);
            }
            final Service self = this;
            this.stopSelf();
            return;
        }

        mTts = new TextToSpeech(getApplicationContext(), null);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mCompass = new Compass(this, mHeadingListener);

        if (needReset) {
            self = this;
            needReset = false;
            previousWifiPoi = new ArrayList<String>();
            wifiPoiLocator = new WifiPointsOfInterestLocator();
            mNotificationManager = (NotificationManager) getSystemService(
                    Context.NOTIFICATION_SERVICE);
            locator = new StreetLocator(self);
            locationManager = new LocationManager(this);
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                    1000, 1, locationListener);
            locationManager.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER, 1000, 1, locationListener);
            locationManager.addGpsStatusListener(mGpsStatusListener);
            // Turned off for now - will enable once we push out a stable version of wifi localizer service
            // Enable wifi indoor location
            /*
             locationManager.startWifiService();
             locationManager.requestLocationUpdates(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER,
             1000, 0, mWifiLocationListener);
            Bundle extras = new Bundle();
            extras.putString(LocationManager.ROOT_DIR_NAME,
                    Environment.getExternalStorageDirectory() + "/wifiscans");
            // To load scans for a specific map, do: e.g.
            // extras.putString(LocationManager.LOAD_FOR_AREA,
            // "SF_Exploratorium_1.*");
            // This will load all scans starting with "SF_Exploratorium_1".
            extras.putString(LocationManager.LOAD_FOR_AREA, null);
            locationManager.sendExtraCommand(LocationManager.INDOOR_WIFI_LOCATION_PROVIDER,
                    LocationManager.COMMAND_LOAD_FOR, extras);
            */
        }
    }

    private void updateLocationString() {
        Location currentLocation = null;
        currentLocation = location;

        if (currentLocation != null) {
            locator.getAddressAsync(currentLocation.getLatitude(), currentLocation.getLongitude());
            locator.getStreetIntersectionAsync(currentLocation.getLatitude(),
                    currentLocation.getLongitude());
        }
    }

    public void onAddressLocated(String address) {
        currentAddress = "";
        String rawAddress = "";
        if (address.length() > 0) {            
            // Drop the country
            address = address.substring(0, address.lastIndexOf(","));
            // Extract the state and zip and insert spaces in the state name
            // that the synthesizer will do the right thing.
            String rawStateZip = address.substring(address.lastIndexOf(",") + 1);
            String streetCity = address.substring(0, address.lastIndexOf(","));
            String street = streetCity.substring(0, streetCity.lastIndexOf(","));
            String city = streetCity.substring(streetCity.lastIndexOf(",") + 1);
            
            String stateZip = "";
            if (!previousRawStateZip.equals(rawStateZip)){                
                String zip = rawStateZip.substring(rawStateZip.lastIndexOf(" ") + 1);
                String state = rawStateZip.substring(0, rawStateZip.lastIndexOf(" ") + 1);
                for (int i = 0; i < state.length(); i++) {
                    stateZip = stateZip + state.charAt(i) + " ";
                }
                stateZip = stateZip + zip;     
                previousRawStateZip = rawStateZip;
            }
            if (!previousCity.equals(city)){   
                previousCity = city;
            } else {
                city = "";                
            }
            
            currentAddress = "Near " + street;
            if (city.length() > 0){
                currentAddress = currentAddress + ", " + city;
            }
            currentAddress = currentAddress + ". ";
            if (stateZip.length() > 0){
                currentAddress = currentAddress + stateZip + ". ";
            }
            rawAddress = street + ", " + previousCity + ", " + previousRawStateZip;
        }
        sendNotification(currentAddress, currentAddress, "", rawAddress);
    }
    
    public void onIntersectionLocated(String[] streetnames) {
        currentIntersection = "";
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
            currentIntersection = currentIntersection.substring(0,
                    currentIntersection.length() - 4);
            currentIntersection = " Nearby streets are: " + currentIntersection;
        }
        if (currentIntersection.indexOf("Unknown road") != -1) {
            currentIntersection = "";
        }
    }

    public void onFrontBackLocated(String[] streetsFront, String[] streetsBack) {
        currentFrontBackStreets = "";
        String currentFrontStreet = "";
        String currentBackStreet = "";
        if (streetsFront.length > 0) {
            for (String ad : streetsFront) {
                if (currentAddress.indexOf(ad) == -1) {
                    currentFrontStreet += ad + " and ";
                }
            }
            if (currentFrontStreet.length() > 5) {
                currentFrontStreet = currentFrontStreet.substring(0,
                        currentFrontStreet.length() - 4);
                currentFrontStreet = "Ahead. " + currentFrontStreet;
            }
        }
        if (currentFrontStreet.indexOf("Unknown road") != -1) {
            currentFrontStreet = "";
        }

        if (streetsBack.length > 0) {
            for (String ad : streetsBack) {
                if (currentAddress.indexOf(ad) == -1) {
                    currentBackStreet += ad + " and ";
                }
            }
            if (currentBackStreet.length() > 5) {
                currentBackStreet = currentBackStreet.substring(0, currentBackStreet.length() - 4);
                currentBackStreet = " Behind. " + currentBackStreet;
            }
        }
        if (currentBackStreet.indexOf("Unknown road") != -1) {
            currentBackStreet = "";
        }

        currentFrontBackStreets = currentFrontStreet + currentBackStreet;
    }

    private void unregisterLocationServices() {
        locationManager.removeUpdates(locationListener);
        locationManager.removeUpdates(mWifiLocationListener);
    }

    public void sendNotification(String ticker, String title, String text, String callbackMsg) {
        mSpeak = mPrefs.getBoolean("speak", false);
        mPostMessage = mPrefs.getBoolean("post_status", true);
        // Post the message to status bar
        if (mPostMessage) {
            long when = System.currentTimeMillis();

            Notification notification = new Notification(android.R.drawable.ic_dialog_map, ticker,
                    when);
            Context context = getApplicationContext();

            Intent notificationIntent = new Intent(self, LocationBookmarker.class);
            notificationIntent.putExtra("LOCATION", callbackMsg);
            PendingIntent contentIntent =
                    PendingIntent.getActivity(self, 0, notificationIntent, 0);

            notification.setLatestEventInfo(context, title, text, contentIntent);
            mNotificationManager.notify(1, notification);
        }

        // Speak the text
        if (mSpeak) {
            mTts.speak(ticker, 0, null);
        }
    }
    
    @Override
    public void onDestroy() {
        unregisterLocationServices();
        if (mNotificationManager != null) {
            mNotificationManager.cancel(1);
        }
        if (locationManager != null) {
            locationManager.shutdown();
        }
        if (mTts != null) {
            mTts.shutdown();
        }
        if (mCompass != null){
            mCompass.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Do nothing - we will not be binding to this service
        return null;
    }
}
