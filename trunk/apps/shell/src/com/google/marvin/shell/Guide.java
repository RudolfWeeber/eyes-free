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

package com.google.marvin.shell;

import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import com.google.marvin.shell.StreetLocator.StreetLocatorListener;

/**
 * Guide uses the magnetic compass, GPS/Network location provider, and the
 * Google Maps API to generate a meaningful spoken string to let users know
 * where they are.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class Guide implements Runnable, StreetLocatorListener {
    class GiveUpTimer implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(10000);
                if (!gotResponse) {
                    String heading = compass.getCurrentHeading();
                    if (heading.length() > 1) {
                        parent.tts.speak(heading, TextToSpeech.QUEUE_FLUSH, null);
                    }
                    parent.tts.speak("Location not found.", TextToSpeech.QUEUE_ADD, null);
                    self.shutdown();
                }
                giveUpTimerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private LocationListener networkLocationListener = new LocationListener() {
            @Override
        public void onLocationChanged(Location arg0) {
            networkLoc = arg0;
            networkLocLastUpdateTime = System.currentTimeMillis();
            networkFixCount++;
            parent.tts.playEarcon(parent.getString(R.string.earcon_tock), 1, null);
            if (networkFixCount > minFixCount) {
                gotResponse = true;
                giveUpTimerThread = null;
                unregisterLocationServices();
                log("Network location",
                        "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
                log("Network location", "Accuracy: " + arg0.getAccuracy());
                (new Thread(self)).start();
            }
        }

            @Override
        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            networkLoc = null;
            networkLocLastUpdateTime = -1;
        }

            @Override
        public void onProviderEnabled(String arg0) {
        }

            @Override
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
            @Override
        public void onLocationChanged(Location arg0) {
            gpsLoc = arg0;
            gpsLocLastUpdateTime = System.currentTimeMillis();
            gpsFixCount++;
            parent.tts.playEarcon(
                    parent.getString(R.string.earcon_tock), TextToSpeech.QUEUE_ADD, null);
            if (gpsFixCount > minFixCount) {
                gotResponse = true;
                giveUpTimerThread = null;
                unregisterLocationServices();
                log("GPS location",
                        "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
                log("GPS location", "Accuracy: " + arg0.getAccuracy());
                (new Thread(self)).start();
            }
        }

            @Override
        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            gpsLoc = null;
            gpsLocLastUpdateTime = -1;
        }

            @Override
        public void onProviderEnabled(String arg0) {
        }

            @Override
        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    };

    // This is a fix for the Droid - the status listener must be set or GPS will
    // not work right.
    GpsStatus.Listener dummyGpsStatusListener = new GpsStatus.Listener() {
            @Override
        public void onGpsStatusChanged(int event) {
        }
    };

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

    private Thread giveUpTimerThread = null;

    private boolean gotResponse = false;

    private MarvinShell parent;

    private Guide self;

    private Compass compass;

    public Guide(MarvinShell parentActivity) {
        self = this;
        parent = parentActivity;
        locator = new StreetLocator(this);
        compass = new Compass(parent);
        LocationManager locationManager = (LocationManager) parent.getSystemService(
                Context.LOCATION_SERVICE);
    }

    public void speakLocation(boolean useGps) {
        if (giveUpTimerThread != null) {
            parent.tts.speak("Determining your location.", TextToSpeech.QUEUE_FLUSH, null);
            return;
        }
        giveUpTimerThread = new Thread(new GiveUpTimer());
        giveUpTimerThread.start();
        gotResponse = false;

        LocationManager locationManager = (LocationManager) parent.getSystemService(
                Context.LOCATION_SERVICE);

        networkLocLastUpdateTime = -1;
        gpsLocLastUpdateTime = -1;
        lastLocateTime = 0;
        networkLoc = null;
        gpsLoc = null;

        currentLocation = null;
        currentAddress = "";
        currentIntersection = "";

        if (useGps) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0, 0, gpsLocationListener);
        } else {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0, 0, networkLocationListener);
        }
    }

    @Override
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
            parent.tts.speak("Location not found.", TextToSpeech.QUEUE_ADD, null);
            self.shutdown();
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

        String heading = compass.getCurrentHeading();
        if (heading.length() > 1) {
            parent.tts.speak(heading, TextToSpeech.QUEUE_FLUSH, null);
        }

        if (usingGPS) {
            parent.tts.speak("G P S", TextToSpeech.QUEUE_ADD, null);
        } else {
            parent.tts.speak("network", TextToSpeech.QUEUE_ADD, null);
        }
        if (currentLocation != null) {
            locator.getAddressAsync(currentLocation.getLatitude(), currentLocation.getLongitude());
        } else {
            if (currentIntersection.length() + currentAddress.length() < 1) {
                parent.tts.speak("Location not found.", TextToSpeech.QUEUE_ADD, null);
                self.shutdown();
            }
        }

    }

    private void unregisterLocationServices() {
        LocationManager locationManager = (LocationManager) parent.getSystemService(
                Context.LOCATION_SERVICE);
        locationManager.removeUpdates(networkLocationListener);
        locationManager.removeUpdates(gpsLocationListener);
        locationManager.removeGpsStatusListener(dummyGpsStatusListener);
        gpsFixCount = 0;
        networkFixCount = 0;
    }

    public void shutdown() {
        unregisterLocationServices();
        compass.shutdown();
    }

    private void log(String tag, String message) {
        // Comment out the following line to turn off logging.
        // Log.i(tag, message);
    }

    private Location currentLocation;

    private String currentAddress;

    private String currentIntersection;

    @Override
    public void onAddressLocated(String address) {
        currentAddress = "";
        if (address.length() > 0) {
            currentAddress = cleanupSpokenAddress(address);
            parent.tts.speak("Near " + currentAddress, TextToSpeech.QUEUE_ADD, null);
        }

        // If there was no location, just give up.
        // Otherwise, try to get the intersection.
        if (currentLocation == null) {
            self.shutdown();
        }
    }

    // Cleans up the spoken address for US style addresses.
    private String cleanupSpokenAddress(String originalAddress) {
        try {
            // Drop the country
            String address = originalAddress.substring(0, originalAddress.lastIndexOf(","));
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
            return address.substring(0, address.lastIndexOf(",")) + ". " + stateZip;
        } catch (StringIndexOutOfBoundsException e) {
            return originalAddress;
        }
    }
}
