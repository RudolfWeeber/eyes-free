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
package com.google.android.marvin.commands.impls;

import com.google.android.marvin.actionslib.R;
import com.google.android.marvin.commands.impls.StreetLocator.StreetLocatorListener;

import android.content.Context;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

/**
 * Guide uses the magnetic compass, GPS/Network location provider, and the
 * Google Maps API to generate a meaningful spoken string to let users know
 * where they are.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class Guide implements Runnable, StreetLocatorListener {
    class GiveUpTimer implements Runnable {
        public void run() {
            try {
                Thread.sleep(10000);
                if (!mGotResponse) {
                    String heading = mCompass.getCurrentHeading();
                    if (heading.length() > 1) {
                        mTts.speak(heading, 0, null);
                    }
                    mTts.speak("Location not found.", 1, null);
                    mSelf.shutdown();
                }
                mGiveUpTimerThread = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private LocationListener networkLocationListener = new LocationListener() {
        public void onLocationChanged(Location arg0) {
            mNetworkLoc = arg0;
            mNetworkLocLastUpdateTime = System.currentTimeMillis();
            mNetworkFixCount++;
            mTts.playEarcon(mContext.getString(R.string.earcon_tock), 1, null);
            if (mNetworkFixCount > mMinFixCount) {
                mGotResponse = true;
                mGiveUpTimerThread = null;
                unregisterLocationServices();
                log("Network location", "Lat: " + arg0.getLatitude() + ", Long: "
                        + arg0.getLongitude());
                log("Network location", "Accuracy: " + arg0.getAccuracy());
                (new Thread(mSelf)).start();
            }
        }

        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            mNetworkLoc = null;
            mNetworkLocLastUpdateTime = -1;
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
            if (arg1 != LocationProvider.AVAILABLE) {
                unregisterLocationServices();
                mNetworkLoc = null;
                mNetworkLocLastUpdateTime = -1;
                (new Thread(mSelf)).start();
            }
        }

    };

    private LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location arg0) {
            mGpsLoc = arg0;
            mGpsLocLastUpdateTime = System.currentTimeMillis();
            mGpsFixCount++;
            mTts.playEarcon(mContext.getString(R.string.earcon_tock), 1, null);
            if (mGpsFixCount > mMinFixCount) {
                mGotResponse = true;
                mGiveUpTimerThread = null;
                unregisterLocationServices();
                log("GPS location", "Lat: " + arg0.getLatitude() + ", Long: "
                        + arg0.getLongitude());
                log("GPS location", "Accuracy: " + arg0.getAccuracy());
                (new Thread(mSelf)).start();
            }
        }

        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            mGpsLoc = null;
            mGpsLocLastUpdateTime = -1;
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    };

    // This is a fix for the Droid - the status listener must be set or GPS will
    // not work right.
    GpsStatus.Listener dummyGpsStatusListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
        }
    };

    private long mNetworkLocLastUpdateTime = -1;

    private long mGpsLocLastUpdateTime = -1;

    private long mLastLocateTime = 0;

    private Location mNetworkLoc = null;

    private Location mGpsLoc = null;

    private StreetLocator mLocator = null;

    private int mGpsFixCount = 0;

    private int mNetworkFixCount = 0;

    private int mMinFixCount = 0; // lockwood suggested this should be 5, but 5

    // seems to take way too long

    private Thread mGiveUpTimerThread = null;

    private boolean mGotResponse = false;

    private Context mContext;

    private Guide mSelf;

    private Compass mCompass;
    
    private TextToSpeech mTts;

    public Guide(Context parentActivity, TextToSpeech tts) {
        mSelf = this;
        mContext = parentActivity;
        mLocator = new StreetLocator(this);
        mCompass = new Compass(mContext);
        this.mTts = tts;
        LocationManager locationManager = (LocationManager) mContext
                .getSystemService(Context.LOCATION_SERVICE);
    }

    public void speakLocation(boolean useGps) {
        if (mGiveUpTimerThread != null) {
            mTts.speak("Determining your location.", 0, null);
            return;
        }
        mGiveUpTimerThread = new Thread(new GiveUpTimer());
        mGiveUpTimerThread.start();
        mGotResponse = false;

        LocationManager locationManager = (LocationManager) mContext
                .getSystemService(Context.LOCATION_SERVICE);

        mNetworkLocLastUpdateTime = -1;
        mGpsLocLastUpdateTime = -1;
        mLastLocateTime = 0;
        mNetworkLoc = null;
        mGpsLoc = null;

        currentLocation = null;
        currentAddress = "";
        currentIntersection = "";

        if (useGps) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                    gpsLocationListener);
        } else {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0,
                    networkLocationListener);
        }
    }

    public synchronized void run() {
        locate();
    }

    private void locate() {
        // Ignore all events after the first event if there is a burst of events
        if (System.currentTimeMillis() - mLastLocateTime < 5000) {
            return;
        }
        mLastLocateTime = System.currentTimeMillis();
        long gpsTimeAdvantage = 300000;
        currentLocation = null;
        boolean usingGPS = false;
        if ((mNetworkLoc == null) && (mGpsLoc == null)) {
            mTts.speak("Location not found.", 1, null);
            mSelf.shutdown();
            return;
        } else if ((mNetworkLoc == null) && (mGpsLoc != null)) {
            currentLocation = mGpsLoc;
            usingGPS = true;
        } else if ((mNetworkLoc != null) && (mGpsLoc == null)) {
            currentLocation = mNetworkLoc;
        } else {
            if (mGpsLocLastUpdateTime + gpsTimeAdvantage > mNetworkLocLastUpdateTime) {
                currentLocation = mGpsLoc;
                usingGPS = true;
            } else {
                currentLocation = mNetworkLoc;
            }
        }

        String heading = mCompass.getCurrentHeading();
        if (heading.length() > 1) {
            mTts.speak(heading, 0, null);
        }

        if (usingGPS) {
            mTts.speak("G P S", 1, null);
        } else {
            mTts.speak("network", 1, null);
        }
        if (currentLocation != null) {
            mLocator.getAddressAsync(currentLocation.getLatitude(), currentLocation.getLongitude());
        } else {
            if (currentIntersection.length() + currentAddress.length() < 1) {
                mTts.speak("Location not found.", 1, null);
                mSelf.shutdown();
            }
        }

    }

    private void unregisterLocationServices() {
        LocationManager locationManager = (LocationManager) mContext
                .getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(networkLocationListener);
        locationManager.removeUpdates(gpsLocationListener);
        locationManager.removeGpsStatusListener(dummyGpsStatusListener);
        mGpsFixCount = 0;
        mNetworkFixCount = 0;
    }

    public void shutdown() {
        unregisterLocationServices();
        mCompass.shutdown();
    }

    private void log(String tag, String message) {
        // Comment out the following line to turn off logging.
        // Log.i(tag, message);
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

            mTts.speak("Near " + currentAddress, 1, null);
        }

        // If there was no location, just give up.
        // Otherwise, try to get the intersection.
        if (currentLocation != null) {
            mLocator.getStreetIntersectionAsync(currentLocation.getLatitude(), currentLocation
                    .getLongitude());
        } else {
            mSelf.shutdown();
        }
    }

    public void onIntersectionLocated(String[] streetnames) {
        if (streetnames.length == 0) {
            // No intersection, try to get front/back streets
            mLocator.getStreetsInFrontAndBackAsync(currentLocation.getLatitude(), currentLocation
                    .getLongitude(), mCompass.getCurrentHeadingValue());
            return;
        }
        currentIntersection = "";
        for (String ad : streetnames) {
            if (currentAddress.indexOf(ad) == -1) {
                currentIntersection += ad + " and ";
            }
        }
        if (currentIntersection.length() > 5) {
            currentIntersection = currentIntersection
                    .substring(0, currentIntersection.length() - 4);
            currentIntersection = " Nearby streets are: " + currentIntersection;
            mTts.speak(currentIntersection, 1, null);
            mSelf.shutdown();
        } else {
            // No intersection, try to get front/back streets
            mLocator.getStreetsInFrontAndBackAsync(currentLocation.getLatitude(), currentLocation
                    .getLongitude(), mCompass.getCurrentHeadingValue());
        }
    }

    public void onFrontBackLocated(String[] streetsFront, String[] streetsBack) {
        currentIntersection = "";
        if (streetsFront.length > 0) {
            for (String ad : streetsFront) {
                if (currentAddress.indexOf(ad) == -1) {
                    currentIntersection += ad + " and ";
                }
            }
            if (currentIntersection.length() > 5) {
                currentIntersection = currentIntersection.substring(0,
                        currentIntersection.length() - 4);
                mTts.speak("Ahead. " + currentIntersection, 1, null);
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
                currentIntersection = currentIntersection.substring(0,
                        currentIntersection.length() - 4);
                mTts.speak("Behind. " + currentIntersection, 1, null);
            }
        }
        mSelf.shutdown();
    }

}
