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

import com.googlecode.eyesfree.walkytalky.Compass.HeadingListener;
import com.googlecode.eyesfree.walkytalky.ReverseGeocoder.OnAddressLocatedListener;

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
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;

/**
 * Service that will take the user's current location and place it in the status
 * notification bar. Note that if the user is running TalkBack, the location
 * will automatically be spoken because of the way TalkBack handles status
 * notifications.
 * 
 * @author clchen@google.com (Charles L. Chen)
 * @author hiteshk@google.com (Hitesh Khandelwal)
 * @author credo@google.com (Tim Credo)
 */

public class PositionStatusNotificationService extends Service implements OnAddressLocatedListener {

    private boolean needReset = true;

    private SharedPreferences mPrefs;

    private boolean mSpeak = false;

    private TextToSpeech mTts;

    private Compass mCompass;

    private boolean mPostMessage = true;

    private PositionStatusNotificationService self;

    NotificationManager mNotificationManager;

    long locLastUpdateTime = 0;

    long gpsLocLastUpdateTime = 0;

    private long locationUpdateWaitTime = 15000;

    private LocationManager locationManager;

    private ReverseGeocoder locator = null;

    private Address lastAddress = null;

    private LocationListener locationListener = new LocationListener() {
        /*
         * Use GPS signal by default, fallbacks to network signal if GPS is
         * unavailable
         */
        public void onLocationChanged(Location location) {
            if (locLastUpdateTime + locationUpdateWaitTime < System.currentTimeMillis()) {
                locLastUpdateTime = System.currentTimeMillis();
                if (location.getProvider().equals("gps")) {
                    // GPS signal
                    locator.getAddressAsync(location.getLatitude(), location.getLongitude());
                    gpsLocLastUpdateTime = System.currentTimeMillis();
                } else if (gpsLocLastUpdateTime + 2 * locationUpdateWaitTime < System
                        .currentTimeMillis()) {
                    // Network signal
                    locator.getAddressAsync(location.getLatitude(), location.getLongitude());
                }
            }
        }

        public void onProviderDisabled(String arg0) {
            locationManager.removeUpdates(locationListener);
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

    public HeadingListener mHeadingListener = new HeadingListener() {
        @Override
        public void onHeadingChanged(String heading) {
            if (mPrefs.getBoolean("enable_compass", false) && (mTts != null)) {
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
            shutdown();
            final Service self = this;
            this.stopSelf();
            return;
        }

        // Need to do null checks here since it is possible that these
        // are already initialized. For example, if the user goes into
        // navigation and then turns off the screen.
        if (mTts == null) {
            mTts = new TextToSpeech(getApplicationContext(), null);
        }
        if (mPrefs == null) {
            mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        }
        if (mCompass == null) {
            mCompass = new Compass(this, mHeadingListener);
        }

        if (needReset) {
            self = this;
            needReset = false;
            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            locator = new ReverseGeocoder(self);
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER,
                    1000, 1, locationListener);
            locationManager.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER, 1000, 1, locationListener);
            locationManager.addGpsStatusListener(mGpsStatusListener);
        }
    }

    public void onAddressLocated(Address address) {
        if (address != null && address.isValid()) {
            String addressString = "Near " + address.getStreetNumber() + " " + address.getRoute();
            String fullAddress = address.getStreetNumber() + " " + address.getRoute() + ", "
                    + address.getCity() + ", " + address.getPostalCode();
            // If the city or postal code changes, announce the full address.
            if (lastAddress == null || !address.getCity().equals(lastAddress.getCity())
                    || !address.getPostalCode().equals(lastAddress.getPostalCode())) {
                addressString = "Near " + fullAddress;

            }
            lastAddress = address;
            sendNotification(addressString, addressString, "", fullAddress);
        }
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
            PendingIntent contentIntent = PendingIntent.getActivity(self, 0, notificationIntent, 0);

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
        shutdown();
        super.onDestroy();
    }

    public void shutdown() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
        }
        if (mNotificationManager != null) {
            mNotificationManager.cancel(1);
        }
        if (mTts != null) {
            mTts.shutdown();
        }
        if (mCompass != null) {
            mCompass.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent arg0) {
        // Do nothing - we will not be binding to this service
        return null;
    }
}
