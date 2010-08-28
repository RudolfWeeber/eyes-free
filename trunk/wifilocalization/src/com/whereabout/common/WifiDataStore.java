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

import android.graphics.Point;
import android.location.Location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * Class for loading and querying the Wifi data.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public abstract class WifiDataStore {
    public WifiDataStore() {
        wifiStrengthMaps = new ArrayList<HashMap<String, Double>>();
        wifiFreqMaps = new ArrayList<HashMap<String, Double>>();
        wifiStddevMaps = new ArrayList<HashMap<String, Double>>();
        wifiMacAddrToLocationMap = new HashMap<String, Set<String>>();
        locationNames = new ArrayList<String>();
        fullLocationNames = new HashSet<String>();
        areaNames = new ArrayList<String>();
        locationCoords = new ArrayList<Point>();
        latlonCoords = new ArrayList<Location>();
        allMacAddresses = new HashSet<String>();
    }

    /**
     * List of WiFi scans, where each element in the ArrayList is one scan consisting of a map of
     * WiFi AP MAC addresses and their signal strengths.  
     */
    public ArrayList<HashMap<String, Double>> wifiStrengthMaps = null;

    /**
     * List of WiFi scans, where each element in the ArrayList is one scan consisting of a map of
     * WiFi AP MAC addresses and their observation frequency.  
     */
    public ArrayList<HashMap<String, Double>> wifiFreqMaps = null;

    /**
     * List of WiFi scans, where each element in the ArrayList is one scan consisting of a map of
     * WiFi AP MAC addresses and the standard deviation of their signal strengths.  
     */
    public ArrayList<HashMap<String, Double>> wifiStddevMaps = null;

    /**
     * A map of WiFi AP MAC addresses to the locations where that AP was seen.
     */
    public HashMap<String, Set<String>> wifiMacAddrToLocationMap = null;

    /**
     * List of all location names.
     */
    public ArrayList<String> locationNames = null;

    /**
     * List of corresponding area names for all locations.
     */
    public ArrayList<String> areaNames = null;

    /**
     * List of corresponding location X-Y coordinates for all locations.
     */
    public ArrayList<Point> locationCoords = null;

    /**
     * List of corresponding lat-lon coordinates for all locations.
     */
    public ArrayList<Location> latlonCoords = null;

    /**
     * Set of all full location  names in the format: <area>_<building>_<floor>_<location-name>. 
     */
    public HashSet<String> fullLocationNames = null;

    /**
     * Set of all observed WiFi AP MAC addresses.
     */
    public HashSet<String> allMacAddresses = null;

    /**
     * Array of all location names.
     */
    protected String[] allLocations;

    /**
     * Whether WiFi data has finished loading.
     */
    protected boolean reloaded = false;

    /**
     * Whether to stop reloading WiFi data.
     */
    protected boolean stopReloading = false;


    /**
     * Reloads the WiFi data from the specified path.
     * @param rootPath The path to the directory where the WiFi data files are stored.
     * @param area The area name for which to load WiFi data.
     * @param strengthTable This map of WiFi AP MAC addresses to signal strengths is used to filter
     *     the WiFi data to load initially. If null, all WiFi data is loaded.
     * @return True, if WiFi data is loaded successfully, false otherwise.
     */
    public abstract boolean reload(String rootPath, String area,
            HashMap<String, Double> strengthTable);

    /**
     * Stops loading WiFi data.
     */
    public abstract void stopReload();

    /**
     * Refresh the WiFi data in the specified directory, from the remote source specified by
     *     the URL. To implement this method, fetch the WiFi data from the URL by specifyying the
     *     map name as a query parameter in the format required by the server. 
     * @param root The path of the directory where the new data is stored. 
     * @param url the URL to fetch the new WiFi data from.
     * @param maps Array of map names to get the WiFi data for.
     * @return Array of boolean, with each boolean indicating whether the refresh was successful
     *     for the corresponding map name.
     */
    public abstract boolean[] refreshData(String root, String url, String[] maps);

    /**
     * Get the version number of the WiFi data for the specified map name.
     * @param url The server URL to fetch the WiFi data version number from.
     * @param map The map name to fetch the WiFi data version number of.
     * @return The version number string.
     */
    public abstract String getWifiDataVersion(String url, String map);

    /**
     * Returns an array of names of all locations in the specified area and starting with the
     *     specified prefix. 
     * @param area The area name to filter locations by.
     * @param prefix The prefix string for filtering location by name.
     * @return Array of location names.
     */
    public abstract String[] getAllLocationsByPrefix(String area, String prefix);

    /**
     * Returns the X-Y coordinate of the specified location.
     * @param area The area name.
     * @param loc The location name.
     * @return The X-Y coordinate represented as a string "X:Y".
     */
    public abstract String getXYForLocation(String area, String loc);

    /**
     * Returns the Lat-Lon of the specified location.
     * @param area The area name.
     * @param loc The location name.
     * @return The Lat-Lon pair represented as a string "X:Y".
     */
    public abstract String getLatLonForLocation(String area, String loc);

    /**
     * Saves the WiFi scan to the specified directory on the device.  
     * @param rootDir The directory path to save the WiFi scan to.
     * @param map The map name, typically in the format <area>_<building>_<floor>.
     * @param locationName The location name where the WiFi scan was taken.
     * @param x The X-coordinate of the location on a map. 
     * @param y The Y-coordinate of the location on a map.
     * @param lat The geographic latitude of the location.
     * @param lon The geographic longitude of the location.
     * @param wifiStrengthTable The map of WiFi AP MAC addresses and signal strengths.
     * @param wifiCountTable The map of WiFi AP MAC addresses and obseravation counts.
     * @param wifiAllStrengths The map of WiFi AP MAC addresses and signal strengths.
     * @param wifiAllScans The raw WiFi data for all individual scans.
     * @param numScans
     * @return Returns the full name of of the location by which the data is saved.
     */
    public abstract String saveScan(String rootDir, String map, String locationName, int x, int y,
            double lat, double lon, HashMap<String, Double> wifiStrengthTable,
            HashMap<String, Integer> wifiCountTable,
            HashMap<String, ArrayList<Double>> wifiAllStrengths,
            ArrayList<HashMap<String, Double>> wifiAllScans, int numScans);

    /**
     * Indicates whether loading of WiFi data is finished.
     * @return True, if loading finished, false otherwise.
     */
    public boolean doneReloading() {
        return reloaded;
    }

    /**
     * Nulls the data structures so that the objects are flagged for garbage collection.
     * Call this in the shutdown pipeline.
     */
    public void destroy() {
        wifiStrengthMaps = null;
        wifiFreqMaps = null;
        wifiStddevMaps = null;
        wifiMacAddrToLocationMap = null;
        locationNames = null;
        fullLocationNames = null;
        areaNames = null;
        locationCoords = null;
        latlonCoords = null;
        allMacAddresses = null;
    }
}
