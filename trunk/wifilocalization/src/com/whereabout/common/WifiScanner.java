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

package com.whereabout.common;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * This class implements creation of new Wifi landmarks.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class WifiScanner {

    private static final long MAX_TIME = 30000; // ms
    
    private static final long FLUSH_TIME = 2000; // ms

    private static final int SCAN_FREQ = 4;
    
    private static final int FLUSH_FREQ = 10;

    private Context context = null;

    private WifiManager wifi = null;

    private WifiDataStore dataStore = null;

    private WakeLock wakeLock = null;

    private HashMap<String, ArrayList<Double>> wifiAllStrengths =
            new HashMap<String, ArrayList<Double>>();

    private ArrayList<HashMap<String, Double>> wifiAllScans =
        new ArrayList<HashMap<String, Double>>();

    private HashMap<String, Double> wifiStrengthTable = new HashMap<String, Double>();

    private HashMap<String, Integer> wifiCountTable = new HashMap<String, Integer>();

    private int scanNo = 0;

    private int scanFreq = SCAN_FREQ;
    
    private long maxScanTime = MAX_TIME;

    private boolean isScanning = false;
    
    private boolean doneFlushing = true;

    /** Constructor */
    public WifiScanner(Context ct, WifiDataStore store) {
        context = ct;
        dataStore = store;
        wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * The thread that collected Wifi signals from which a representative
     * signature is computed.
     */
    private Runnable stepChecker = new Runnable() {
        public void run() {
            while (isScanning || !doneFlushing) {
                wifi.startScan();
                try {
                    Thread.sleep(1000 / scanFreq);
                } catch (InterruptedException ie) {}
                List<ScanResult> res = wifi.getScanResults();
                if (res == null) {
                    isScanning = false;
                    return;
                }
                if (res.size() > 0 && isScanning && doneFlushing) {
                    String str = "";
                    scanNo++;
                    synchronized (WifiScanner.this) {
                        HashMap<String, Double> scanMap = new HashMap<String, Double>();
                        for (ScanResult r : res) {
                            if (r.BSSID == null) {
                                continue;
                            }
                            double strength = r.level;
                            int count = 1;
                            scanMap.put(r.BSSID, (double) r.level);
                            if (!wifiAllStrengths.containsKey(r.BSSID)) {
                                wifiAllStrengths.put(r.BSSID, new ArrayList<Double>());
                            }
                            wifiAllStrengths.get(r.BSSID).add(strength);
                            if (wifiStrengthTable.containsKey(r.BSSID)) {
                                strength += wifiStrengthTable.get(r.BSSID);
                            }
                            if (wifiCountTable.containsKey(r.BSSID)) {
                                count += wifiCountTable.get(r.BSSID);
                            }
                            wifiStrengthTable.put(r.BSSID, strength);
                            wifiCountTable.put(r.BSSID, count);
                            str += scanNo + " " + count + " " + r.BSSID + " " + r.level + "\n";
                        }
                        wifiAllScans.add(scanMap);
                    }
                    if (scanNo * (1000 / scanFreq) >= maxScanTime) {
                        stopScan();
                        return;
                    }
                } else if (!doneFlushing) {
                    scanNo++;
                }
                if (scanNo * (1000 / scanFreq) >= FLUSH_TIME && !doneFlushing) {
                    doneFlushing = true;
                    scanNo = 0;
                    return;
                }
            }
        }
    };

    /**
     * Returns all the raw WiFi scans that were collected between the previous calls to
     * startScan and stopScan.
     * @return All raw scans.
     */
    public ArrayList<HashMap<String, Double>> getAllWifiScans() {
        return wifiAllScans;
    }

    /**
     * Returns the map of WiFi AP MAC addresses to their signal strengths obtained from the
     * previous scan operation.
     * @return MAC address -> signal strength map.
     */
    public HashMap<String, Double> getWifiStrengthTable() {
        return wifiStrengthTable;
    }

    /**
     * Returns a copy of the map of WiFi AP MAC addresses to their signal strengths obtained from
     * the previous scan operation.
     * @return Copy of MAC address -> signal strength map.
     */
    public HashMap<String, Double> getCopyOfWifiStrengthTable() {
        HashMap<String, Double> table = new HashMap<String, Double>();
        synchronized (this) {
            for (String key : wifiStrengthTable.keySet()) {
                table.put(key, wifiStrengthTable.get(key) / wifiCountTable.get(key));
            }
        }
        return table;
    }

    /**
     * Returns the map of WiFi AP MAC addresses to their observation counts obtained from the
     * previous scan operation.
     * @return a {@link HashMap} object containing MAC addresses and observation counts.
     */
    public HashMap<String, Integer> getWifiCountTable() {
        return wifiCountTable;
    }

    /**
     * Returns the number of scans taken between the previous calls to startScan and stopScan.
     * @return Number of scans.
     */
    public int getNumScans() {
        return scanNo;
    }

    /**
     * Returns an instance to the WiFi data store used by this WifiScanner.
     * @return a {@link WifiDataStore} object.
     */
    public WifiDataStore getDataStore() {
        return dataStore;
    }

    /**
     * Makes the current thread wait while the scanner is flushing the stale WiFi data.
     */
    public void waitWhileFlushing() {
        try {
            while (!doneFlushing) {
                Thread.sleep(50);
            }
        } catch (InterruptedException ie) {}
    }

    /**
     * Starts scanning Wifi signals. The scanning continues until stopScan is called. All
     * WiFi signals scanned between startScan and stopScan can be obtained using
     * getWifiStrengthTable or getCopyOfWifiStrengthTable which represent the mean signal strengths
     * over the multiple scans made.
     * @param freq The freq of scanning in number of scans per second.
     */
    public void startScan(int freq) {
        waitWhileFlushing();
        scanFreq = (freq == 0) ? SCAN_FREQ : freq;
        synchronized (this) {
            wifiStrengthTable.clear();
            wifiCountTable.clear();
            wifiAllStrengths.clear();
            wifiAllScans.clear();
        }
        scanNo = 0;
        isScanning = true;
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire();
        }
        new Thread(stepChecker).start();
    }

    /**
     * Stops scanning Wifi signals.
     */
    public void stopScan() {
        if (!isScanning) {
            return;
        }
        isScanning = false;
        Log.d("WifiScanner", "Stopping Wifi scanning...");
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    /**
     * Erases the scans in the buffer and starts afresh.
     */
    public void resetScan() {
        isScanning = false;
        scanNo = 0;
    }

    /**
     * Starts flushing stale WiFi data in a new thread.
     */
    public void flushWifiScans() {
        doneFlushing = false;
        scanFreq = FLUSH_FREQ;
        new Thread(stepChecker).start();
    }

    /**
     * Saves the recently collected computed Wifi signature with the specified
     * area and location name
     * 
     * @param map The map name.
     * @param locationName The location name.
     * @return True if the data was successfully saved, false otherwise
     */
    public String saveScan(String rootDir, String map, String locationName, int x, int y,
            double lat, double lon) {
        Log.d("WifiScanner", locationName);
        synchronized (this) {
            if (wifiStrengthTable.size() == 0) {
                return null;
            }
            return dataStore.saveScan(rootDir, map, locationName, x, y, lat, lon, wifiStrengthTable,
                    wifiCountTable, wifiAllStrengths, wifiAllScans, scanNo);
        }
    }

    /**
     * Sets the time on milliseconds for which to scan WiFi signals. stopScan is called
     * automatically after the specified time elapses since the previous call to startScan.
     * @param scanTime Duration to scan for in milliseconds.
     */
    public void setScanTime(long scanTime) {
        maxScanTime = scanTime;
    }

    /**
     * Whether Wifi signal scanning is "ON".
     * 
     * @return Returns true if scanning, false otherwise.
     */
    public boolean isScanningWifi() {
        return isScanning;
    }
    
    /**
     * Whether the scanner is done flushing the wifi cache.
     * 
     * @return Returns true if flushing, false otherwise.
     */
    public boolean isFlushing() {
        return !doneFlushing;
    }

    /**
     * Nulls the data structures so that the objects are flagged for garbage collection.
     * Call this in the shutdown pipeline. 
     */
    public void destroy() {
        wifiAllStrengths = null;
        wifiAllScans = null;
        wifiStrengthTable = null;
        wifiCountTable = null;        
    }
}
