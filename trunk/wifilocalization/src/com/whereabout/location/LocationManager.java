/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.whereabout.location;

import com.whereabout.wifiservice.IWifiLocationService;

import com.whereabout.common.LocalWifiDataStore;
import com.whereabout.common.WifiDataStore;
import com.whereabout.common.WifiScanner;

import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Wrapper class for the WifiLocalizationService that makes it look like any
 * other Location provider. Note that this LocationManager is nearly identical
 * to the one in the Android Framework (and it calls up to the one that is in
 * the Android Framework), except for the addition of the
 * INDOOR_WIFI_LOCATION_PROVIDER.
 *
 * @author clchen@google.com (Charles L. Chen)
 *         chaitanyag@google.com (Chaitanya Gharpure)
 */
public class LocationManager {

    private static final String TAG = "LocationManager";

    /**
     * The default directory path where the WiFi data is stored.
     */
    public static final String SCANS_DIR = Environment.getExternalStorageDirectory() +
            "/wifiscans";

    /**
     * The default directory path where the map files are stored.
     */
    public static final String MAP_DIR = Environment.getExternalStorageDirectory() +
            "/wifiscans/maps";

    /**
     * the name of the indoor Wifi location provider.
     */
    public static final String INDOOR_WIFI_LOCATION_PROVIDER = "INDOOR_WIFI_LOCATION_PROVIDER";

    /**
     * Command to load data from a specific director and for selected maps.
     */
    public static final String COMMAND_LOAD_FOR = "COMMAND_LOAD_FOR";

    /**
     * Command to refresh the wifi scan data from the specified url if needed. This command is
     * currently unsupported.
     */
    public static final String COMMAND_REFRESH_DATA = "COMMAND_REFRESH_DATA";

    /**
     * Path for the data directory was invalid.
     */
    public static final int STATUS_INVALID_ROOT = 0;

    /**
     * Key used to retrieve map names for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_MAPS = "MATCH_MAPS";

    /**
     * Key used to retrieve match scores for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_SCORES = "MATCH_SCORES";

    /**
     * Key used to retrieve all matching location names from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_LOCATIONS = "MATCH_LOCATIONS";

    /**
     * Key used to retrieve X-coordinates for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_XCOORDS = "MATCH_XCOORDS";

    /**
     * Key used to retrieve Y-coordinates for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_YCOORDS = "MATCH_YCOORDS";

    /**
     * Key used to retrieve latitudes for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_LATITUDES = "MATCH_LATITUDES";

    /**
     * Key used to retrieve longitudes for all matching locations from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String MATCH_LONGITUDES = "MATCH_LONGITUDES";

    /**
     * Key used to retrieve the name of the best location estimate from the {@link Bundle} object
     * obtained by calling {@link Location.getExtras}. 
     */
    public static final String FINAL_LOCATION_NAME = "FINAL_LOCATION_NAME";

    /**
     * Key used to retrieve the X-coordinate of the best location estimate from the {@link Bundle}
     * object obtained by calling {@link Location.getExtras}. 
     */
    public static final String FINAL_X = "FINAL_X";

    /**
     * Key used to retrieve the Y-coordinate of the best location estimate from the {@link Bundle}
     * object obtained by calling {@link Location.getExtras}. 
     */
    public static final String FINAL_Y = "FINAL_Y";

    public static final String FINAL_LAT = "FINAL_LAT";

    public static final String FINAL_LON = "FINAL_LON";

    public static final String ROOT_DIR_NAME = "ROOT_DIR_NAME";

    public static final String LOAD_FOR_AREA = "LOAD_FOR_AREA";

    public static final String REFRESH_URL = "REFRESH_URL";

    public static final String REFRESH_MAPS = "REFRESH_MAPS";

    public static final String REFRESH_ASYNC = "REFRESH_ASYNC";

    private Context parent;

    private android.location.LocationManager platformLocationManager;

    private final ComponentName mWifiService;

    private IWifiLocationService wifiService = null;

    private ServiceConnection mServiceConnection = null;

    private String mRootDirPath;

    private String loadForArea = null;

    private Thread wifiScanningLoopThread = null;

    private Thread saveLocationThread = null;

    private ArrayList<LocationListener> wifiLocationListeners = null;

    private final Lock serviceStartLock = new ReentrantLock();

    private final Condition serviceStarted  = serviceStartLock.newCondition();

    private WifiDataStore dataStore = null;

    private WifiScanner wifiScanner = null;

    private boolean quitSave = false;
    
    private boolean runWifiScanningLoop = false;

    public class WifiStorageException extends Exception {
        @Override
        public String getMessage() {
            return "External storage required to store WiFi data not found.";
        }
    }
    
    /**
     * Creates a new LocationManager object setting the root directory to the default value of
     * "/sdcard/wifiscans".
     * 
     * @param context The activity context
     */
    public LocationManager(Context context) {
        this(context, null, Environment.getExternalStorageDirectory() + "/wifiscans");
    }

    /**
     * Creates a new LocationManager object setting the root directory to the specified path.
     * 
     * @param context The activity context
     * @param wifiService Not used, pass null
     * @param rootDirPath The path to the root directory where tagged WiFi signatures will be
     *      saved and read from for localization  
     */
    public LocationManager(Context context, ComponentName wifiService, String rootDirPath) {
        parent = context;
        platformLocationManager = (android.location.LocationManager) parent
                .getSystemService(Context.LOCATION_SERVICE);
        wifiLocationListeners = new ArrayList<LocationListener>();
        mWifiService = wifiService;
        mRootDirPath = rootDirPath;
        dataStore = new LocalWifiDataStore(rootDirPath);
        wifiScanner = new WifiScanner(context, dataStore);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public boolean addGpsStatusListener(GpsStatus.Listener listener) {
        return platformLocationManager.addGpsStatusListener(listener);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void addProximityAlert(double latitude, double longitude, float radius, long expiration,
            PendingIntent intent) {
        platformLocationManager.addProximityAlert(latitude, longitude, radius, expiration, intent);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void addTestProvider(String name, boolean requiresNetwork, boolean requiresSatellite,
            boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude,
            boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy) {
        platformLocationManager.addTestProvider(name, requiresNetwork, requiresSatellite,
                requiresCell, hasMonetaryCost, supportsAltitude, supportsSpeed, supportsBearing,
                powerRequirement, accuracy);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void clearTestProviderEnabled(String provider) {
        platformLocationManager.clearTestProviderEnabled(provider);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void clearTestProviderLocation(String provider) {
        platformLocationManager.clearTestProviderLocation(provider);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void clearTestProviderStatus(String provider) {
        platformLocationManager.clearTestProviderStatus(provider);
    }

    /**
     * Gets the list of location providers from {@link android.location.LocationManager}, adds
     * INDOOR_WIFI_LOCATION_PROVIDER to it, and returns the combined list.
     */
    public List<String> getAllProviders() {
        List<String> providers = platformLocationManager.getAllProviders();
        providers.add(INDOOR_WIFI_LOCATION_PROVIDER);
        return providers;
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        // TODO: Put in the criteria for matching against IndoorWifi!
        return platformLocationManager.getBestProvider(criteria, enabledOnly);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public GpsStatus getGpsStatus(GpsStatus status) {
        return platformLocationManager.getGpsStatus(status);
    }

    /**
     * Delegates to {@link android.location.LocationManager}. Currently not supported for
     * indoor WiFi location provider. 
     */
    public Location getLastKnownLocation(String provider) {
        // TODO: handle the special case of IndoorWifi
        return platformLocationManager.getLastKnownLocation(provider);
    }

    /**
     * Delegates to {@link android.location.LocationManager}. Currently not supported for
     * indoor WiFi location provider. 
     */
    public LocationProvider getProvider(String name) {
        // TODO: handle the special case of IndoorWifi
        return platformLocationManager.getProvider(name);
    }

    /**
     * Delegates to {@link android.location.LocationManager}. Currently not supported for
     * indoor WiFi location provider. 
     */
    public List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        // TODO: handle the special case of IndoorWifi
        return platformLocationManager.getProviders(criteria, enabledOnly);
    }

    /**
     * Gets the list of location providers from {@link android.location.LocationManager}, adds
     * INDOOR_WIFI_LOCATION_PROVIDER to it, and returns the combined list.
     * 
     * @param enabledOnly if true then only the providers which are currently enabled are returned.
     * @return list of Strings containing names of the providers.
     */
    public List<String> getProviders(boolean enabledOnly) {
        List<String> providers = platformLocationManager.getProviders(enabledOnly);
        if (enabledOnly && wifiService != null)
            providers.add(INDOOR_WIFI_LOCATION_PROVIDER);
        else if (!enabledOnly)
            providers.add(INDOOR_WIFI_LOCATION_PROVIDER);
        return providers;
    }

    /**
     * Returns the status of INDOOR_WIFI_LOCATION_PROVIDER if requested, otherwise delegates to
     * {@link android.location.LocationManager}.
     * 
     * @param provider The location provider name.
     * @return true if the provider is enabled, false otherwise.
     */
    public boolean isProviderEnabled(String provider) {
        if (provider.equals(INDOOR_WIFI_LOCATION_PROVIDER)) {
            return wifiService != null;
        }
        return platformLocationManager.isProviderEnabled(provider);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void removeGpsStatusListener(GpsStatus.Listener listener) {
        platformLocationManager.removeGpsStatusListener(listener);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void removeProximityAlert(PendingIntent intent) {
        platformLocationManager.removeProximityAlert(intent);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void removeTestProvider(String provider) {
        platformLocationManager.removeTestProvider(provider);
    }

    /**
     * Removes any current registration for location updates of the current activity with the given
     * LocationListener. Following this call, updates will no longer occur for this listener.
     * @param listener {@link LocationListener} object that no longer needs location updates.

     */
    public void removeUpdates(LocationListener listener) {
        if (wifiLocationListeners.contains(listener)) {
            wifiLocationListeners.remove(listener);
            if (wifiLocationListeners.size() == 0) {
                runWifiScanningLoop = false;
                if (wifiService != null) {
                    try {
                        // This decrements the service ref count in the wifi service
                        wifiService.stopContinuousPositioning();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        platformLocationManager.removeUpdates(listener);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void removeUpdates(PendingIntent intent) {
        platformLocationManager.removeUpdates(intent);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            PendingIntent intent) {
        platformLocationManager.requestLocationUpdates(provider, minTime, minDistance, intent);
    }

    /**
     * Registers the specified listener to receive location updates. If the provider is
     * INDOOR_WIFI_LOCATION_PROVIDER, minTime and minDistance parameters are ignored, and
     * updates are sent about every 1 second, otherwise delegates to
     * {@link android.location.LocationManager}.
     * 
     * @param provider The location provider to get updates from. 
     * @param minTime Minimum duration in ms between updates. Ignored for
     *     INDOOR_WIFI_LOCATION_PROVIDER.
     * @param minDistance Minimum distance between location updates. Ignored for
     *     INDOOR_WIFI_LOCATION_PROVIDER.
     * @param listener The {@link LocationListener} interested in getting the location updates.
     */
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            LocationListener listener) {
        if (provider.equals(INDOOR_WIFI_LOCATION_PROVIDER)) {
            if (!wifiLocationListeners.contains(listener)) {
                wifiLocationListeners.add(listener);
            }
            if (!runWifiScanningLoop) {
                resetWifiScanner();
            }
        } else {
            platformLocationManager
                    .requestLocationUpdates(provider, minTime, minDistance, listener);
        }
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void requestLocationUpdates(String provider, long minTime, float minDistance,
            LocationListener listener, Looper looper) {
        platformLocationManager.requestLocationUpdates(provider, minTime, minDistance, listener,
                looper);
    }

    /**
     * Sends extra commands to the location provider. This method specifically handles extra
     * commands for the indoor Wifi location provider.
     *
     * @param provider The name of the location provider
     * @param command The command string. Currently only COMMAND_LOAD_FOR is supported for the
     *     indoor Wifi location provider. For other providers the call is delegated to
     *     {@link android.location.LocationManager}
     * @param extras The parameters for the command
     * @return whether the command was successfully delivered to the location provider
     */
    public boolean sendExtraCommand(String provider, String command, Bundle extras) {
        if (provider.equals(INDOOR_WIFI_LOCATION_PROVIDER)) {
            if (command.equals(COMMAND_LOAD_FOR)) {
                loadDataFor(extras.getString(ROOT_DIR_NAME), extras.getString(LOAD_FOR_AREA));
                return true;
            } /*else if (command.equals(COMMAND_REFRESH_DATA)) {
                refreshData(extras.getString(REFRESH_URL), extras.getStringArray(REFRESH_MAPS),
                        extras.getString(ROOT_DIR_NAME), extras.getBoolean(REFRESH_ASYNC, true));
                return true;
            }*/ else {
                return false;
            }
        } else {
            return platformLocationManager.sendExtraCommand(provider, command, extras);
        }
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void setTestProviderEnabled(String provider, boolean enabled) {
        platformLocationManager.setTestProviderEnabled(provider, enabled);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void setTestProviderLocation(String provider, Location loc) {
        platformLocationManager.setTestProviderLocation(provider, loc);
    }

    /**
     * Delegates to {@link android.location.LocationManager}.
     */
    public void setTestProviderStatus(String provider, int status, Bundle extras,
            long updateTime) {
        platformLocationManager.setTestProviderStatus(provider, status, extras, updateTime);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // private methods 
    ///////////////////////////////////////////////////////////////////////////////////////////////
    
    private void loadDataFor(String root, String area) {
        Log.d(TAG, "loadDataFor() root=" + mRootDirPath);
        mRootDirPath = root;
        loadForArea = area;
        try {
            if (wifiService != null) {
                if (!wifiService.setRoot(mRootDirPath, loadForArea)) {
                    dispatchStatusUpdate(STATUS_INVALID_ROOT, null);
                }
            }
        } catch (RemoteException re) {
            Log.e(TAG, "Error loading data from " + mRootDirPath + ", for " + loadForArea);
        }
    }

    private void refreshData(final String url, final String[] maps, final String root,
            final boolean async) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    serviceStartLock.lock();
                    // Since this call might be happening on a specific thread, we block
                    // and wait for the service.
                    if (serviceStarted.await(5, TimeUnit.SECONDS)) {
                        wifiService.refreshData(url, maps, root, async);
                    } else {
                        Log.w(TAG, "unable to acquire service reference");
                    }
                    serviceStartLock.unlock();
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Error refreshing data from " + url);
                } catch (RemoteException re) {
                    Log.e(TAG, "Error refreshing data from " + url);
                } 
            }
        }).start();
    }

    private void resetWifiScanner() {
        new Thread(new Runnable() {
            public void run() {
                try {
                    while (wifiService == null) {
                        Thread.sleep(100);
                    }
                    // This increments the service ref count in the wifi service
                    wifiService.startContinuousPositioning(4, 1000);
                } catch (InterruptedException ie) {
                    Log.e(TAG, "Error starting continuous positioning.");
                } catch (RemoteException re) {
                    Log.e(TAG, "Error starting continuous positioning.");
                } 
            }
        }).start();
        runWifiScanningLoop = true;
        wifiScanningLoopThread = new Thread(wifiScanningLoop);
        wifiScanningLoopThread.start();
    }

    private void dispatchLocationUpdates(Location loc) {
        for (int i = 0; i < wifiLocationListeners.size(); i++) {
            wifiLocationListeners.get(i).onLocationChanged(loc);
        }
    }

    private void dispatchStatusUpdate(int status, Bundle extras) {
        for (int i = 0; i < wifiLocationListeners.size(); i++) {
            wifiLocationListeners.get(i).onStatusChanged(INDOOR_WIFI_LOCATION_PROVIDER,
                    status, extras);
        }
    }

    /**
     * This thread performs continuous localization and makes callbacks for location updates to
     * the registered listeners.
     */
    private Runnable wifiScanningLoop = new Runnable() {
        public void run() {
            try {
                while (runWifiScanningLoop) {
                    Thread.sleep(50);
                    if (wifiService != null) {
                        if (!wifiService.isLocationUpdated() || !wifiService.isReady() ||
                            wifiService.isRefreshingData()) {
                            continue;
                        }
                        Bundle extras = buildLocationResultBundle();
                        if (extras.getStringArray(MATCH_LOCATIONS).length > 0) {
                            Location loc = new Location(INDOOR_WIFI_LOCATION_PROVIDER);
                            // What should these be????
                            loc.setAccuracy((float) .5);
                            // Once interpolation is in place use wifiService.getLatitude() and
                            // wifiService.getLongitude()
                            loc.setLatitude(extras.getDoubleArray(MATCH_LATITUDES)[0]);
                            loc.setLongitude(extras.getDoubleArray(MATCH_LONGITUDES)[0]);
                            loc.setExtras(extras);
                            dispatchLocationUpdates(loc);
                        }
                    }
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Error detecting Wifi location: " + e.getMessage());
            } catch (InterruptedException e) {
                Log.e(TAG, "Wifi localization interrupted: " + e.getMessage());
            }
        }
    };

    /**
     * Creates a {@link Bundle} from the latest results provided by the Wifi location service.
     *
     * @return The bundle containing the localization results.
     * @throws RemoteException
     */
    private Bundle buildLocationResultBundle() throws RemoteException {
        String[] locations = wifiService.getLocations();
        double[] proximities = wifiService.getProximities();
        int[] xCoords = wifiService.getXCoordinates();
        int[] yCoords = wifiService.getYCoordinates();
        double[] latitudes = wifiService.getLatitudes();
        double[] longitudes = wifiService.getLongitudes();
        if (locations.length > 0) {
            Bundle extrasBundle = new Bundle();
            extrasBundle.putDoubleArray(MATCH_SCORES, proximities);
            extrasBundle.putStringArray(MATCH_LOCATIONS, locations);
            extrasBundle.putIntArray(MATCH_XCOORDS, xCoords);
            extrasBundle.putIntArray(MATCH_YCOORDS, yCoords);
            extrasBundle.putDoubleArray(MATCH_LATITUDES, latitudes);
            extrasBundle.putDoubleArray(MATCH_LONGITUDES, longitudes);
            extrasBundle.putInt(FINAL_X, wifiService.getX());
            extrasBundle.putInt(FINAL_Y, wifiService.getY());
            extrasBundle.putDouble(FINAL_LAT, wifiService.getLatitude());
            extrasBundle.putDouble(FINAL_LON, wifiService.getLongitude());
            return extrasBundle;
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////
    // Additional public methods not delegating to {@link android.location.LocationManager}. 
    ///////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Starts the WiFi localization service.
     * 
     * @throws WifiStorageException Throws this exception if external storage is not found or
     *         not mounted.
     */
    public void startWifiService() throws WifiStorageException {
        if (!android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED)) {
            Log.e(TAG, "SD card does not exist, or is not mounted.");
            throw new WifiStorageException();
        }
        if (wifiService == null) {
            mServiceConnection = new ServiceConnection() {
                public void onServiceConnected(ComponentName name, IBinder service) {
                    wifiService = IWifiLocationService.Stub.asInterface(service);
                    try {
                        serviceStartLock.lock();
                        serviceStarted.signal();
                        serviceStartLock.unlock();
                        wifiService.setRoot(mRootDirPath, loadForArea);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Error loading data from " + mRootDirPath +
                                ", for " + loadForArea);
                    }
                }

                public void onServiceDisconnected(ComponentName name) {
                    wifiService = null;
                    Log.d(TAG, "Service disconnected...");
                }
            };
            Intent serviceIntent = new Intent("android.intent.action.USE_WIFI_SERVICE");
            serviceIntent.addCategory("android.intent.category.WIFI_SERVICE");

            // Use explicit component when requested
            if (mWifiService != null) {
                serviceIntent.setComponent(mWifiService);
            }
            parent.bindService(serviceIntent, mServiceConnection, Service.BIND_AUTO_CREATE);
        }
    }

    /**
     * Shuts down this location manager and does the necessary cleanup.
     */
    public void shutdown() {
        runWifiScanningLoop = false;
        wifiServiceBusy = true;
        quitSave = true;
        wifiScanner.stopScan();
        try {
            if (wifiScanningLoopThread != null) {
                wifiScanningLoopThread.join();
            }
            if (saveLocationThread != null) {
                saveLocationThread.join();
            }
            if (wifiService != null) {
                wifiService.stopContinuousPositioning();
            }
            parent.unbindService(mServiceConnection);
            wifiScanner.destroy();
            dataStore.destroy();
        } catch (RemoteException e) {
            Log.e(TAG, "Error shutting down Wifi location service: " + e.getMessage());
        } catch (InterruptedException e) {
            // Do nothing
        } catch (IllegalArgumentException e) {
            // Do nothing and fail silently since an error here indicates that
            // binding never succeeded in the first place.
        }
    }

    /**
     * Returns names of all locations where Wifi scans were taken.
     * @return An array of location names
     */
    public String[] getAllWifiScanLocations() {
        if (wifiService == null) {
            Log.e(TAG, "WifiService not yet initialized.");
            return null;
        }
        try {
            return wifiService.getAllLocationsByPrefix(null, "");
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting scan locations: " + e.getMessage());
        }
        return new String[0];
    }

    /**
     * Returns all locations in the map that matches the regular expression
     * specified and whose names start with the specified prefix.
     * @param map Regular expression for the map in which scans were taken.
     * @param prefix locations starting with this prefix are returned
     * @return An array of location names
     */
    public String[] getWifiScanLocationsIn(String map, String prefix) {
        if (wifiService == null) {
            Log.e(TAG, "WifiService not yet initialized.");
            return null;
        }
        try {
            return wifiService.getAllLocationsByPrefix(map, prefix);
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting scan locations: " + e.getMessage());
        }
        return new String[0];
    }

    /**
     * Indicates whether the service has loaded all data and is ready to receive requests.
     * @return true if the service is ready, false otherwise
     */
    public boolean isReady() {
        try {
            return wifiService != null && !wifiService.isRefreshingData() && wifiService.isReady();
        } catch (RemoteException e) {
            Log.e(TAG, "Error getting scan locations: " + e.getMessage());
        }
        return false;
    }

    /**
     * Asynchronously collects Wifi scans and tags them with the specified meta data.
     * 
     * @param dir The directory to save the scan to
     * @param area
     * @param building
     * @param floor
     * @param location
     * @param x X-coordinate of the location on the map image
     * @param y Y-coordinate of the location on the map image
     * @param lat Latitude for this location
     * @param lon Longitude for this location
     * @param scanFreq Number of WiFi scans to take per second
     * @param duration Duration in ms for which to collect WiFi scans
     */
    public void saveScan(final String dir, final String area, final String building,
            final String floor, final String location,  final int x, final int y, final double lat,
            final double lon, final int scanFreq, final long duration,
            final WifiLocationTaggingListener doneListener) {
        saveLocationThread = new Thread(new Runnable() {
            public void run() {
                // Flush cached scans
                wifiScanner.flushWifiScans();
                while (wifiScanner.isFlushing()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                // Start scanning
                wifiScanner.setScanTime(duration);
                wifiScanner.startScan(scanFreq);
                while (wifiScanner.isScanningWifi()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (quitSave) return;
                wifiScanner.saveScan(dir, area + "_" + building + "_" + floor, location,
                        x, y, lat, lon);
                if (doneListener != null) {
                    doneListener.onDoneSave(true, dir + "_" + area + "_" + floor + "_" + location);
                }
            }
        });
        saveLocationThread.start();
    }

    /**
     * Implement this listener to receive a callback when save operation is done.
     */
    public interface WifiLocationTaggingListener {
        /**
         * This method is called when the save operation is done.
         * @param success true if data was saved successfully, false otherwise.
         * @param locationName the location name by which the data was saved.
         */
        public void onDoneSave(boolean success, String locationName);
    }

    /**
     * Implement this listener to receive callbacks from the LocationManager.
     */
    public interface WifiLocalizationListener {
        /**
         * This method will be called when the location result is obtained..
         * @param status This will always be 0.
         * @param result the {@link Location} object that contains the information about the
         *     computed location. Call getExtras() on this object to obtain a {@link Bundle}
         *     object containing additional information such as all matching location names,
         *     and their match scores sorted in decreasing order, along with the map names,
         *     X-Y coordinate and lat-lon for all matching locations. Use the constants in
         *     {@link com.whereabout.location.LocationManager} to retrieve various fields in
         *     the {@link Bundle} object.
         */
        public void onWifiLocalizationResult(int status, Location result);
    }

    boolean wifiServiceBusy = false;

    /**
     * Provides a way to do on-demand localization using Wifi.
     *
     * @param scanDuration The duration of scanning in ms. It might take more time before results
     *     are available since the method takes additional time to wait for the service to be ready
     *     and to flush the cached Wifi scans.
     * @param callback The callback listener to call when localization is done.
     * @return Whether request for localization was successful.
     */
    public boolean localizeWithWifi(final long scanDuration,
                                    final WifiLocalizationListener callback) {
        if (wifiServiceBusy) {
            return false;
        }
        wifiServiceBusy = true;
        Runnable OnDemandWifiLocalizer = new Runnable() {
            public void run() {
                try {
                    while (wifiService == null || !wifiService.isReady() ||
                           wifiService.isRefreshingData()) {
                        Thread.sleep(100);
                    }
                    // Flush previous Wifi scans
                    wifiService.flushWifiScans();
                    while (wifiService.isFlushing()) {
                        Thread.sleep(50);
                    }
                    wifiService.startScanningForLocation(10, true, 0);
                    Thread.sleep(scanDuration);
                    wifiService.stopScanningForLocation("");

                    Bundle extras = buildLocationResultBundle();
                    Location result = new Location(INDOOR_WIFI_LOCATION_PROVIDER);
                    result.setAccuracy((float) .5);
                    result.setLatitude(extras.getDoubleArray(MATCH_LATITUDES)[0]);
                    result.setLongitude(extras.getDoubleArray(MATCH_LONGITUDES)[0]);
                    result.setExtras(extras);
                    callback.onWifiLocalizationResult(0, result);
                    wifiServiceBusy = false;
                } catch (RemoteException e) {
                    Log.e(TAG, "Error detecting Wifi location: " + e.getMessage());
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error detecting Wifi location: " + e.getMessage());
                }

            }
        };
        new Thread(OnDemandWifiLocalizer).start();
        return true;
    }

    /**
     * This is currently a place-holder for a method that runs accuracy tests that computes
     * sensitivity and specificity by classifying the data stored in the specified directory.
     *
     * @param dir The directory in which test data is stored
     * @param file The tests are ran for the data files starting with this string
     * @return Returns the specificity and sensitivity formatted as a string
     *     "specificity:sensitivity", e.g. "0.85:0.96666"
     */
    public String runAccuracyTest(String dir, String file) {
        return "0:0";
    }
}
