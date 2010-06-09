/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.marvin.compass;

import com.google.marvin.compass.StreetLocator.StreetLocatorListener;
import com.google.tts.TTSEarcon;
import com.google.tts.TextToSpeechBeta;
import com.whereabout.location.LocationManager;
import com.whereabout.location.LocationManager.WifiLocalizationListener;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Config;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Provides spoken feedback augmented by non-speech audio and tactile feedback
 * to turn an Android handset into a wearable compass.
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class TalkingCompass extends Activity implements StreetLocatorListener {
    private static final int MUTE_SPEECHMODE = 0;

    private static final int DEFAULT_SPEECHMODE = 1;

    private static final int VERBOSE_SPEECHMODE = 2;

    private static final int NUM_SPEECH_MODES = 3;

    private static final int NORTH = 0;

    private static final int EAST = 90;

    private static final int SOUTH = 180;

    private static final int WEST = 270;

    private static final String[] DIRECTION_NAMES = {
            "north", "north north east", "north east", "east north east", "east",
            "east south east", "south east", "south south east", "south", "south south west",
            "south west", "west south west", "west", "west north west", "north west",
            "north north west", "north"
    };

    private static final int TTS_MIN_VER = 1;

    private static final String TAG = "Compass";

    private static final int MIN_STABLECOUNT = 50;

    private static final int STABLECOUNT_AFTER_MODESETTING = 25;

    private static final int STABLECOUNT_FOR_VERBOSE = -100;

    private static final int STABLECOUNT_FOR_CALIBRATION = -200;

    // Degrees of tolerance for a reading to be considered stable
    private static final int STABLE_TOLERANCE = 5;

    private static final int CARDINAL_TOLERANCE = 1;

    private static final int NORTH_LEFT_MAX = 359;

    private static final int NORTH_RIGHT_MAX = 1;

    private static final long[] VIBE_PATTERN = {
            0, 1, 40, 41
    };

    private SensorManager sensorManager;

    private CompassView view;

    private float currentHeading;

    private float lastStableHeading;

    private int stableCount;

    private int speechMode;

    private int lastCardinalDir;

    private Vibrator vibe;

    private boolean sensorOk;

    private TalkingCompass self;

    private TextToSpeechBeta tts;

    private long networkLocLastUpdateTime = -1;

    private long gpsLocLastUpdateTime = -1;
    
    private long locationUpdateWaitTime = 15000;
    private LocationManager locationManager;
    private Location gpsLoc = null;

    private Location networkLoc = null;

    private StreetLocator locator = null;

    private boolean usingGps = false;

    private String currentAddress = "";

    private String currentIntersection = "";
    private String currentFrontBackStreets = "";

    /**
     * Handles the sensor events for changes to readings and accuracy
     */
    private final SensorListener mListener = new SensorListener() {
        public void onSensorChanged(int sensor, float[] values) {
            currentHeading = values[0]; // Values are yaw (heading), pitch, and
            // roll.
            if (view != null) {
                view.invalidate();
            }
            processDirection();
        }

        public void onAccuracyChanged(int arg0, int arg1) {
            sensorOk = (arg1 == SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
        }
    };

    private TextToSpeechBeta.OnInitListener ttsInitListener = new TextToSpeechBeta.OnInitListener() {
        public void onInit(int status, int version) {

            if (view != null) {
                view.setVisibility(View.GONE);
                view = null;
            }
            Log.e("Compass debug", 3 + "");
            view = new CompassView(self);
            setContentView(view);
            stableCount = 0;
            currentHeading = 0;
            lastStableHeading = 0;
            speechMode = DEFAULT_SPEECHMODE;
            lastCardinalDir = 0;
            tts.speak(getString(R.string.compass), 0, null);
            Log.e("Compass debug", 4 + "");
        }
    };

    private LocationListener networkLocationListener = new LocationListener() {
        public void onLocationChanged(Location arg0) {
            networkLoc = arg0;
            if (networkLocLastUpdateTime + locationUpdateWaitTime < System.currentTimeMillis()){
                updateLocationString();
                networkLocLastUpdateTime = System.currentTimeMillis();
            }
            Log.e("Network location", "Lat: " + arg0.getLatitude() + ", Long: "
                    + arg0.getLongitude());
            Log.e("Network location", "Accuracy: " + arg0.getAccuracy());
        }

        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            networkLoc = null;
            networkLocLastUpdateTime = -1;
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }

    };

    private LocationListener gpsLocationListener = new LocationListener() {
        public void onLocationChanged(Location arg0) {
            gpsLoc = arg0;
            if (gpsLocLastUpdateTime + locationUpdateWaitTime < System.currentTimeMillis()){
                updateLocationString();
                gpsLocLastUpdateTime = System.currentTimeMillis();
            }
            Log.e("GPS location", "Lat: " + arg0.getLatitude() + ", Long: " + arg0.getLongitude());
            Log.e("GPS location", "Accuracy: " + arg0.getAccuracy());
        }

        public void onProviderDisabled(String arg0) {
            unregisterLocationServices();
            gpsLoc = null;
            gpsLocLastUpdateTime = -1;
        }

        public void onProviderEnabled(String arg0) {
        }

        public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        }
    };
    
    GpsStatus.Listener mGpsStatusListener = new GpsStatus.Listener(){
        public void onGpsStatusChanged(int event) {
            // TODO Auto-generated method stub
            
        }        
    };
    

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        self = this;
        vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        sensorOk = true;
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        tts = new TextToSpeechBeta(this, ttsInitListener);
        locator = new StreetLocator(this);
        locationManager = new LocationManager(this);
        locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 1000, 1,
                gpsLocationListener);
        locationManager.requestLocationUpdates(android.location.LocationManager.NETWORK_PROVIDER, 0, 0,
                networkLocationListener);
        locationManager.addGpsStatusListener(mGpsStatusListener);     
    }

    @Override
    protected void onResume() {
        if (Config.LOGD) {
            Log.d(TAG, "onResume");
        }
        super.onResume();
        sensorManager.registerListener(mListener, SensorManager.SENSOR_ORIENTATION,
                SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onStop() {
        if (Config.LOGD) {
            Log.d(TAG, "onStop");
        }
        sensorManager.unregisterListener(mListener);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        tts.shutdown();
        unregisterLocationServices();
        locationManager.shutdown();
        super.onDestroy();
    }

    private void updateLocationString() {
        Location currentLocation = null;
        long gpsTimeAdvantage = 300000;

        if (gpsLocLastUpdateTime + gpsTimeAdvantage > networkLocLastUpdateTime) {
            currentLocation = gpsLoc;
            usingGps = true;
        } else {
            currentLocation = networkLoc;
            usingGps = false;
        }
        
        //currentLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        //usingGps = true;

        if (currentLocation != null) {
            locator.getAddressAsync(currentLocation.getLatitude(), currentLocation.getLongitude());
            locator.getStreetIntersectionAsync(currentLocation.getLatitude(), currentLocation
                    .getLongitude());
            locator.getStreetsInFrontAndBackAsync(currentLocation.getLatitude(), currentLocation
                    .getLongitude(), currentHeading);
        }
    }

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
            currentAddress = "Near " + address.substring(0, address.lastIndexOf(",")) + ". "
                    + stateZip;
        }
        Log.e("currentAddress", currentAddress);
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
            currentIntersection = currentIntersection
                    .substring(0, currentIntersection.length() - 4);
            currentIntersection = " Nearby streets are: " + currentIntersection;
        }
        if (currentIntersection.indexOf("Unknown road") != -1){
            currentIntersection = "";
        }
        Log.e("currentIntersection", currentIntersection);
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
        if (currentFrontStreet.indexOf("Unknown road") != -1){
            currentFrontStreet = "";
        }

        if (streetsBack.length > 0) {
            for (String ad : streetsBack) {
                if (currentAddress.indexOf(ad) == -1) {
                    currentBackStreet += ad + " and ";
                }
            }
            if (currentBackStreet.length() > 5) {
                currentBackStreet = currentBackStreet.substring(0,
                        currentBackStreet.length() - 4);
                currentBackStreet = " Behind. " + currentBackStreet;
            }
        }
        if (currentBackStreet.indexOf("Unknown road") != -1){
            currentBackStreet = "";
        }
        
        currentFrontBackStreets = currentFrontStreet + currentBackStreet;
        Log.e("currentFrontBackStreets", currentFrontBackStreets);
    }

    protected void processDirection() {
        // Do not speak immediately - wait until the sensor readings have been
        // stable for some time.
        
        if (Math.abs(lastStableHeading - currentHeading) < STABLE_TOLERANCE) {
            stableCount++;
        } else {
            lastStableHeading = currentHeading;
            stableCount = 0;
        }
//        if (stableCount > MIN_STABLECOUNT) {
//            speakDirection();
//        }

        // Do not try bother determining if a new cardinal direction
        // was reached if the sensors are not functioning correctly.
        if (!sensorOk) {
            return;
        }
        boolean newCardinalDir = false;
        int candidateCardinal = findCardinalDir(currentHeading);

        if (candidateCardinal != lastCardinalDir) {
            newCardinalDir = true;
            lastCardinalDir = candidateCardinal;
        }

        if (newCardinalDir) {
            vibe.vibrate(VIBE_PATTERN, -1);
        }
    }

    private int findCardinalDir(float heading) {
        if ((heading > NORTH_LEFT_MAX) || (heading < NORTH_RIGHT_MAX)) {
            return NORTH;
        } else if ((heading > EAST - CARDINAL_TOLERANCE) && (heading < EAST + CARDINAL_TOLERANCE)) {
            return EAST;
        } else if ((heading > SOUTH - CARDINAL_TOLERANCE) && (heading < SOUTH + CARDINAL_TOLERANCE)) {
            return SOUTH;
        } else if ((heading > WEST - CARDINAL_TOLERANCE) && (heading < WEST + CARDINAL_TOLERANCE)) {
            return WEST;
        } else {
            return -1;
        }
    }

    public void setAndSpeakCurrentMode(int newSpeechMode) {
        speechMode = (newSpeechMode + NUM_SPEECH_MODES) % NUM_SPEECH_MODES;
        String text = "";
        switch (speechMode) {
            case VERBOSE_SPEECHMODE:
                stableCount = STABLECOUNT_AFTER_MODESETTING;
                text = getString(R.string.verbose);
                break;
            case DEFAULT_SPEECHMODE:
                stableCount = STABLECOUNT_AFTER_MODESETTING;
                text = getString(R.string.default_);
                break;
            case MUTE_SPEECHMODE:
                text = getString(R.string.muted);
                break;
        }
        tts.speak(text, 0, null);
    }

    public void speakDirection() {
        stableCount = 0;
        if (!sensorOk) {
            tts.speak(getString(R.string.calibrate), 0, null);
            stableCount = STABLECOUNT_FOR_CALIBRATION;
            return;
        }
        if (speechMode == MUTE_SPEECHMODE) {
            return;
        }

        tts.speak(directionToString(currentHeading), 0, null);
        
        String currentLocationUtterance = currentAddress + " " + currentIntersection + " " + currentFrontBackStreets;
        if (currentLocationUtterance.length() > 2){
            if (usingGps){
                currentLocationUtterance = getString(R.string.gps) + currentLocationUtterance;
            } else {
                currentLocationUtterance = getString(R.string.network) + currentLocationUtterance;                
            }
            tts.speak(currentLocationUtterance, 1, null);
        }

        if (speechMode == VERBOSE_SPEECHMODE) {
            stableCount = STABLECOUNT_FOR_VERBOSE;
            int degrees = Math.round(currentHeading);
            tts.speak(Integer.toString(degrees), 1, null);
        }
    }

    public static String directionToString(float heading) {
        int index = (int) ((heading * 100 + 1125) / 2250);
        return DIRECTION_NAMES[index];
    }

    private void unregisterLocationServices() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(networkLocationListener);
        locationManager.removeUpdates(gpsLocationListener);
    }


    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_SEARCH){
            boolean localizationStarted = locationManager.localizeWithWifi(5000, new WifiLocalizationListener(){
                public void onWifiLocalizationResult(int arg0, Location result) {
                    final String locationName = result == null ? "Location cannot be determined." :
                        result.getExtras().getStringArray(LocationManager.MATCH_LOCATIONS)[0];
                    tts.speak(locationName, 0, null);
                }
            });
            if (localizationStarted){
                // TODO: Restore the previous speech mode after location name is spoken
                speechMode = MUTE_SPEECHMODE;
                tts.speak("Scanning", 0, null);
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    
    private class CompassView extends View {
        private Paint paint = new Paint();

        private Path path = new Path();

        private float downY;

        public CompassView(Context context) {
            super(context);

            // Construct a wedge-shaped path
            path.moveTo(0, -50);
            path.lineTo(-20, 60);
            path.lineTo(0, 50);
            path.lineTo(20, 60);
            path.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(Color.WHITE);

            paint.setAntiAlias(true);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);

            int w = canvas.getWidth();
            int h = canvas.getHeight();
            int cx = w / 2;
            int cy = h / 2;

            canvas.translate(cx, cy);
            canvas.rotate(-currentHeading);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downY = event.getY();
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getY() + 100 < downY) {
                    setAndSpeakCurrentMode(speechMode + 1);
                } else if (event.getY() - 100 > downY) {
                    setAndSpeakCurrentMode(speechMode - 1);
                } else {
                    speakDirection();
                }
                return true;
            }
            return false;
        }
    }
}