/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.whereabout.wifiservice;

import com.whereabout.wifiservice.IWifiLocationService.Stub;

import com.whereabout.common.LocalWifiDataStore;
import com.whereabout.common.WifiDataStore;
import com.whereabout.common.WifiScanner;

import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

/**
 * This class implements the Wifi-based indoor localization service. It allows
 * clients to create new Wifi locations and detect Wifi landmarks on-the-fly.
 *
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class WifiLocalizationService extends Service {

    private static final String TAG = "WifiLocalizationService";

    private static final String ACTION = "android.intent.action.USE_WIFI_SERVICE";

    private static final String CATEGORY = "android.intent.category.WIFI_SERVICE";

    private static final String ROOT_DIR =
            Environment.getExternalStorageDirectory() + "/wifiscans";

    private static final String PREFS_WIFI_STORE = "com.google.android.wifiservice.PREFS_WIFI";
    
    private static final boolean FORCE_REFRESH = false;

    private WifiScanner wifiScanner = null;

    private WifiLocalizer wifiLocalizer = null;

    private WifiDataStore dataStore = null;

    private String rootDirectoryPath = null;

    private boolean isRefreshing = false;

    private String refreshUrl;

    private String[] refreshMaps;

    private Thread loadDataThread = null;
    
    private int serviceRefCount = 0;
    
    private Runnable refreshData = new Runnable() {

        public void run() {
            isRefreshing = true;
            String mapRegex = "[";
            String[] reloadMaps = new String[0];
            ArrayList<String> reloadMapsList = new ArrayList<String>();
            ArrayList<Integer> reloadMapsVersion = new ArrayList<Integer>();
            int newVersion = 0;
            String versionCheckUrl = refreshUrl.substring(0, refreshUrl.lastIndexOf('/') + 1) +
                    "getMapVersion";
            for (int i = 0, count = 0; i < refreshMaps.length; i++) {
                newVersion = doRefresh(versionCheckUrl, refreshMaps[i]);
                if (FORCE_REFRESH) newVersion = 1;
                if (newVersion > 0) {
                    reloadMapsList.add(refreshMaps[i]);
                    reloadMapsVersion.add(newVersion);
                }
                mapRegex += refreshMaps[i] + "|";
            }
            mapRegex = mapRegex.substring(0, mapRegex.length() - 1) + "].*";
            boolean success[] = {};
            Log.d(TAG, "about to reload maps: " + reloadMapsList.toString());
            if (reloadMapsList.size() > 0) {
                reloadMaps = new String[reloadMapsList.size()];
                reloadMapsList.toArray(reloadMaps);
                success = dataStore.refreshData(rootDirectoryPath, refreshUrl, reloadMaps);
            }
            dataStore.reload(rootDirectoryPath, mapRegex, null);
            boolean ret = true;
            for (int i = 0; i < reloadMaps.length; i++) {
                if (success[i]) {
                    SharedPreferences settings =
                        getSharedPreferences(PREFS_WIFI_STORE, 0);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putInt(versionCheckUrl + ":" + reloadMaps[i],
                            reloadMapsVersion.get(i));
                    editor.commit();
                } else {
                    ret = false;
                }
            }
            isRefreshing = false;
        }

    };

    private int doRefresh(String url, String map) {
        SharedPreferences settings = getSharedPreferences(PREFS_WIFI_STORE, 0);
        int newVersion = 0;
        try {
            newVersion = Integer.parseInt(dataStore.getWifiDataVersion(url, map));
        } catch (NumberFormatException nfe) {
            Log.w(TAG, "Invalid Version number for map: " + map);
            return 0;
        }
        int version = settings.getInt(url + ":" + map, 0);
        Log.d(TAG, "for " + map + ": local version=" + version + ", remote=" + newVersion);
        return  newVersion > version ? newVersion : 0;
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate() {
        super.onCreate();
        serviceRefCount = 0;
        dataStore = new LocalWifiDataStore(ROOT_DIR);
        wifiScanner = new WifiScanner(this, dataStore);
        wifiLocalizer = new LocalWifiLocalizer(this, wifiScanner);
    }

    @Override
    public void onDestroy() {
        dataStore.stopReload();
        try {
            loadDataThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        dataStore.destroy();
        wifiScanner.destroy();
        wifiLocalizer.destroy();
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        if (ACTION.equals(intent.getAction())) {
            for (String category : intent.getCategories()) {
                if (category.equals(CATEGORY)) {
                    return mBinder;
                }
            }
        }
        return null;
    }

    /**
     * Stub for exposing the service's interface.
     */
    private final IWifiLocationService.Stub mBinder = new Stub() {

        /**
         * Sets the path of the directory where the Wifi data is stored, and loads the
         * data from that path for the specified maps.
         *
         *  @param root The path to the data directory
         *  @param map Data is loaded for all maps matching this regular expression
         */
        public boolean setRoot(String root, final String map) {
            rootDirectoryPath = root;
            loadDataThread = new Thread(new Runnable() {
                public void run() {
                    flushWifiScans();
                    while (wifiScanner.isFlushing()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    wifiScanner.setScanTime(1000);
                    wifiScanner.startScan(4);
                    while (wifiScanner.isScanningWifi()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    dataStore.reload(rootDirectoryPath, map,
                            wifiScanner.getCopyOfWifiStrengthTable());
                    dataStore.reload(rootDirectoryPath, map, null);
                }
            });
            loadDataThread.start();
            return true;
        }

        /**
         * Indicates whether the service is currently refreshing the WiFi signatures.
         */
        public boolean isRefreshingData() {
            return isRefreshing;
        }

        /**
         * Refreshes the Wifi data for the specified maps and stores the data in the specified
         * root directory. After refreshing, the specified root if set as the new root directory
         * and the data from this directory is loaded in the memory for localization. The effect
         * is similar to calling setRoot. The Wifi data is refreshed only if new data version is
         * available for the corresponding map.
         *
         * @param url The url to fetch the data from
         * @param maps The map names for which to refresh Wifi data
         * @param root The root directory in which to store the new Wifi data
         */
        public boolean refreshData(String url, String[] maps, String root, boolean async) {
            WifiLocalizationService.this.refreshUrl = url;
            WifiLocalizationService.this.refreshMaps = maps;
            WifiLocalizationService.this.rootDirectoryPath = root;
            if (async) {
                new Thread(refreshData).start();
            } else {
                // Warn when called on UI thread
                final Looper looper = Looper.myLooper();
                if (looper != null && looper.equals(getMainLooper())) {
                    Log.w(TAG, "REFRESHING DATA FROM MAIN THREAD!!");
                }
                refreshData.run();
            }
            return true;
        }

        /**
         * Indicates whether the service has done loading the data and is ready to accept
         * requests.
         *
         * @return true if the service is ready, false otherwise
         */
        public boolean isReady() {
            return dataStore.doneReloading();
        }

        /**
         * Whether the Wifi service is currently scanning Wifi signals.
         *
         * @return true if scanning, false otherwise
         */
        public boolean isScanningWifi() {
            return wifiScanner.isScanningWifi();
        }

        /**
         * Starts scanning Wifi signals for detecting location.
         *
         * @param freq The frequency of scanning in # scans/s
         * @param voting Whether to use voting-based localization algorithm. If true,
         *     freq is ignored
         * @param samples Number of samples to use for voting. If this is 0, or if voting is false,
         *     this parameter is ignored. If 0, it is necessary to call stopScanningForLocation in
         *     order to get the voting results
         */
        public void startScanningForLocation(int freq, boolean voting, int samples) {
            wifiLocalizer.startScan(freq, voting, samples);
        }

        /**
         * Stops scanning Wifi signals for detecting location.
         *
         * @param expectedLocation The name of the location that the last Wifi signature is
         *     expected to be classified as
         */
        public void stopScanningForLocation(String expectedLocation) {
            wifiLocalizer.stopScanningForLocation(expectedLocation);
        }

        /**
         * Starts computing WiFi location estimate continuously.
         * 
         * @param wifiScanFrequency WiFi scanning frequency in # scans/sec
         * @param wifiScanDuration Duration of WiFi scans in ms
         */
        public void startContinuousPositioning(int wifiScanFrequency, long wifiScanDuration) {
            serviceRefCount++;
            wifiLocalizer.startContinuousPositioning(wifiScanFrequency, wifiScanDuration);
        }

        /**
         * Stops the continuous positioning loop.
         */
        public void stopContinuousPositioning() {
            if (--serviceRefCount == 0) {
                wifiLocalizer.stopContinuousPositioning();
            }
        }

        /**
         * Returns all locations in the specified map starting with the specified prefix.
         *
         * @param map Locations in maps matching with this regular expression are considered.
         * @param prefix Locations starting with this prefix are considered
         * @return An array of location names
         */
        public String[] getAllLocationsByPrefix(String map, String prefix) {
            return dataStore.getAllLocationsByPrefix(map, prefix);
        }

        /**
         * Returns the X-Y coordinate of the specified map and location.
         *
         * @param map The name of the map specified as area_building_floor
         * @param loc The names of the location whose X-Y coordinate is desired
         * @return X-Y coordinate in the form of a String formatted as "x:y", e.g. "25:240"
         */
        public String getXYForLocation(String map, String loc) {
            return dataStore.getXYForLocation(map, loc);
        }

        /**
         * Returns the Lat-Lng of the specified map and location.
         *
         * @param map The name of the map specified as area_building_floor
         * @param loc The names of the location whose Lat-Lng coordinate is desired
         * @return Lat-Lng in the form of a String formatted as "lat:lng"
         */
        public String getLatLonForLocation(String map, String loc) {
            return dataStore.getLatLonForLocation(map, loc);
        }

        /**
         * Get all known locations in the vicinity.
         *
         * @return Array of locations
         */
        public String[] getLocations() {
            return wifiLocalizer.getLocations();
        }

        /**
         * Get the proximity metric for all known locations in the vicinity.
         *
         * @return Array of proximity values
         */
        public double[] getProximities() {
            return wifiLocalizer.getProximities();
        }

        /**
         * Get X coordinates of all known locations in the vicinity. The X coordinate value is
         * the pixel # of that location as specified from the left edge of the map image.
         *
         * @return Array of X coordinates
         */
        public int[] getXCoordinates() {
            return wifiLocalizer.getXCoordinates();
        }

        /**
         * Get Y coordinates of all known locations in the vicinity. The Y coordinate value is
         * the pixel # of that location as specified from the top edge of the map image.
         *
         * @return Array of Y coordinates
         */
        public int[] getYCoordinates() {
            return wifiLocalizer.getYCoordinates();
        }

        /**
         * Get latitudes of all known locations in the vicinity.
         *
         * @return Array of latitudes
         */
        public double[] getLatitudes() {
            return wifiLocalizer.getLatitudes();
        }

        /**
         * Get longitudes of all known locations in the vicinity.
         *
         * @return Array of longitudes
         */
        public double[] getLongitudes() {
            return wifiLocalizer.getLongitudes();
        }

        /**
         * Returns the estimated X-coordinate which is the pixel # from left edge of the map image
         * associated with this location.
         *
         *  @return The X coordinate
         */
        public int getX() {
            return wifiLocalizer.getX();
        }

        /**
         * Returns the estimated Y-coordinate which is the pixel # from top edge of the map image
         * associated with this location.
         *
         *  @return The Y coordinate
         */
        public int getY() {
            return wifiLocalizer.getY();
        }

        /**
         * Returns the estimated latitude of the current location.
         *
         *  @return The latitude in degrees
         */
        public double getLatitude() {
            return wifiLocalizer.getLatitude();
        }

        /**
         * Returns the estimated longitude of the current location.
         *
         *  @return The longitude in degrees
         */
        public double getLongitude() {
            return wifiLocalizer.getLongitude();
        }

        /**
         * Flushes the cached Wifi scans.
         */
        public void flushWifiScans() {
            wifiScanner.flushWifiScans();
        }

        /**
         * Indicates whether the service is currently in the middle of flushing cached Wifi scans.
         *
         * @return true if flushing, false otherwise
         */
        public boolean isFlushing() {
            return wifiScanner.isFlushing();
        }
        
        /**
         * Indicates whether location is updated. 
         */
        public boolean isLocationUpdated() {
            return wifiLocalizer.isLocationUpdated();
        }
    };
}
