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
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * This class implements the WifiDataStore for doing operations locally on the
 * device. This class assumes that data is stored and read as a custom-formatted flat file.
 *
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class LocalWifiDataStore extends WifiDataStore {

    private static final String TAG = "LocalWifiDataStore";
    
    private static final String REVERSE_INDEX_FILE = "reverse.index";

    private String rootDirectoryPath;

    private String areaToLoad;

    private WifiHttpClient httpClient;
    
    public LocalWifiDataStore(String rootPath) {
        super();
        httpClient = new WifiHttpClient();
        Log.d(TAG, "setting root=" + rootPath);
        rootDirectoryPath = rootPath;
    }
    
    /**
     * Reads the Wifi signatures from the existing maps.
     */
    private boolean readWifiMaps() {
        File f = new File(rootDirectoryPath);
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            int i = 0;
            for (File file : files) {
                if (stopReloading) {
                    return false;
                }
                // Ignore the temp file and files not ending in .scan
                if (file.isDirectory() || file.getName().equals("temp.scan") ||
                    file.getName().endsWith("index") ||
                    (areaToLoad != null && !file.getName().matches(areaToLoad))) {
                    continue;
                }
                loadFromFile(file);
            }
        } else {
            Log.e(TAG, "Error loading data. Directory does not exist: " + rootDirectoryPath);
            return false;
        }
        if (isDataClean()) {
            buildReverseIndex();
            saveReverseIndexToFile(wifiMacAddrToLocationMap, rootDirectoryPath +
                    "/" + REVERSE_INDEX_FILE);
            reloaded = true;
            return true;
        }
        return false;
    }
    
    private boolean readFilteredWifiMaps(HashMap<String, Double> strengthTable) {
        reloaded = false;
        stopReloading = false;
        clearAllCache();
        wifiMacAddrToLocationMap =
            loadReverseIndexFromFile(rootDirectoryPath + "/" + REVERSE_INDEX_FILE);
        if (wifiMacAddrToLocationMap == null) {
            Log.e(TAG, "Error reading reverse index.");
            return false;
        }
        HashSet<String> preloadLocations = new HashSet<String>();
        for (String macAddr : strengthTable.keySet()) {
            // Only use the strong APs to find which locations to use for preload.
            if (strengthTable.get(macAddr) > -70 &&
                wifiMacAddrToLocationMap.containsKey(macAddr)) {
                preloadLocations.addAll(wifiMacAddrToLocationMap.get(macAddr));
            }
        }
        if (preloadLocations.size() == 0) {
            return false;
        }
        for (String fileName : preloadLocations) {
            File f = new File(rootDirectoryPath + "/" + fileName);
            if (stopReloading) {
                return false;
            }
            if (f.exists() && (areaToLoad == null || f.getName().matches(areaToLoad))) {
                loadFromFile(f);
            }
        }
        if (isDataClean()) {
            reloaded = true;
            return true;
        }
        return false;
    }

    private String locName = "unknown";
    private String areaName = "unknown";
    private String buildingName = "unknown";
    private String floorName = "unknown";
    private String line = "";
    private int x = -1, y = -1;
    private double lat = 0, lon = 0;
    
    private static final int BUFFER_SIZE = 1024;
    
    private void loadFromFile(File file) {
        // TODO(chaitanyag): Remove this try-catch after we start getting cleaner data.
        // * This is only temporary *
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file), BUFFER_SIZE);
            HashMap<String, Double> strengthMap = new HashMap<String, Double>();
            HashMap<String, Double> freqMap = new HashMap<String, Double>();
            HashMap<String, Double> stddevMap = new HashMap<String, Double>();
            line = "";
            locName = "unknown";
            areaName = "unknown";
            buildingName = "unknown";
            floorName = "unknown";
            x = -1;
            y = -1;
            lat = 0;
            lon = 0;
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")) {
                    StringTokenizer st = new StringTokenizer(line, " ");
                    String first = st.nextToken();
                    if (first.equals("Loc:")) {
                        locName = line.substring(line.indexOf(" ") + 1);
                    } else if (first.equals("Area:")) {
                        areaName = line.substring(line.indexOf(" ") + 1);
                    } else if (first.equals("Building:")) {
                        buildingName = line.substring(line.indexOf(" ") + 1);
                    } else if (first.equals("Floor:")) {
                        floorName = line.substring(line.indexOf(" ") + 1);
                    } else if (first.equals("Pos:")) {
                        x = Integer.parseInt(st.nextToken());
                        y = Integer.parseInt(st.nextToken());
                    } else if (first.equals("LatLon:")) {
                        lat = Double.parseDouble(st.nextToken());
                        lon = Double.parseDouble(st.nextToken());
                    } else {
                        double freq = Double.parseDouble(st.nextToken());
                        allMacAddresses.add(first);
                        freqMap.put(first, freq);
                        strengthMap.put(first, Double.parseDouble(st.nextToken()));
                        if (st.hasMoreTokens()) {
                            stddevMap.put(first, Double.parseDouble(st.nextToken()));
                        } else {
                            stddevMap.put(first, 0.0);
                        }
                    }
                }
            }
            areaNames.add(areaName + "_" + buildingName + "_" + floorName);
            locationNames.add(locName);
            fullLocationNames.add(areaName + "_" + buildingName + "_" + floorName + "_" + locName);
            locationCoords.add(new Point(x, y));
            // Set provider as WIFI location service
            Location latlon = new Location("");
            latlon.setLatitude(lat);
            latlon.setLongitude(lon);
            latlonCoords.add(latlon);
            wifiStrengthMaps.add(strengthMap);
            wifiFreqMaps.add(freqMap);
            wifiStddevMaps.add(stddevMap);
        } catch (NullPointerException e) {
            Log.d(TAG, "Failed to load file: " + file.getName());
            e.printStackTrace();
        } catch (NumberFormatException e) {
            Log.d(TAG, "Failed to load file: " + file.getName());
            e.printStackTrace();
        } catch (NoSuchElementException e) {
            Log.d(TAG, "Failed to load file: " + file.getName());
            e.printStackTrace();
        } catch (IOException e) {
            Log.d(TAG, "Failed to load file: " + file.getName());
            e.printStackTrace();
        } catch (Exception e) {
            Log.d(TAG, "Failed to load file: " + file.getName());
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void clearAllCache() {
        wifiStrengthMaps.clear();
        wifiFreqMaps.clear();
        wifiStddevMaps.clear();
        wifiMacAddrToLocationMap.clear();
        locationNames.clear();
        fullLocationNames.clear();
        areaNames.clear();
        locationCoords.clear();
        latlonCoords.clear();
        allMacAddresses.clear();
    }

    private boolean isDataClean() {
        if (locationNames.size() != areaNames.size()
                || locationNames.size() != fullLocationNames.size()
                || locationNames.size() != locationCoords.size()
                || locationNames.size() != latlonCoords.size()
                || locationNames.size() != wifiStrengthMaps.size()
                || locationNames.size() != wifiFreqMaps.size()
                || locationNames.size() != wifiStddevMaps.size()) {
            return false;
        }
        return true;
    }

    private boolean buildReverseIndex() {
        synchronized (this) {
            if (wifiMacAddrToLocationMap == null) {
                wifiMacAddrToLocationMap = new HashMap<String, Set<String>>();
            }
            if (wifiStrengthMaps.size() == 0) {
                return false;
            }
            for (int i = 0; i < wifiStrengthMaps.size(); i++) {
                buildReverseIndexFor(i);
            }
        }
        return true;
    }

    private void buildReverseIndexFor(int i) {
        if (i < 0 || i >= locationNames.size()) {
            Log.w(TAG, "Location with index " + i + " does not exist");
            return;
        }
        String locationName = locationNames.get(i);
        String mapName = areaNames.get(i);
        HashMap<String, Double> strengthTable = wifiStrengthMaps.get(i);
        HashMap<String, Double> freqTable = wifiFreqMaps.get(i);
        for (String addr : strengthTable.keySet()) {
            Set<String> locationsForMac = wifiMacAddrToLocationMap.get(addr);
            if (locationsForMac == null) {
                locationsForMac = new HashSet<String>();
                wifiMacAddrToLocationMap.put(addr, locationsForMac);
            }
            if (freqTable.get(addr) > 0.7 && strengthTable.get(addr) > -75) {
                locationsForMac.add(mapName + "_" + locationName);
            }
        }
    }
    
    private void saveReverseIndexToFile(HashMap<String, Set<String>> reverseIndex, String path) {
        File f = new File(path);
        File parentDir = f.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        try {
            FileWriter wr = new FileWriter(f);
            for (String key : reverseIndex.keySet()) {
                StringBuilder str = new StringBuilder();
                str.append(key);
                for (String loc : reverseIndex.get(key)) {
                    str.append("," + loc);
                }
                if (reverseIndex.size() > 0) {
                    wr.write(str.toString() + "\n");
                }
            }
        } catch (IOException e) {
            Log.d(TAG, "Error saving reverse index to file.");
        }
    }
    
    private HashMap<String, Set<String>> loadReverseIndexFromFile(String path) {
        HashMap<String, Set<String>> reverseIndex = new HashMap<String, Set<String>>();
        File f = new File(path);
        if (!f.exists()) {
            return null;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String line = "";
            while((line = reader.readLine()) != null) {
                String[] toks = line.split(",");
                Set<String> locSet = reverseIndex.containsKey(toks[0]) ?
                        reverseIndex.get(toks[0]) : new HashSet<String>(); 
                for (int i = 1; i < toks.length; i++) {
                    locSet.add(toks[i]);
                }
                reverseIndex.put(toks[0], locSet);
            }
            return reverseIndex;
        } catch (IOException e) {
            Log.d(TAG, "Error saving reverse index to file.");
        }
        return null;
    }

    private String saveScan(String rootDir, String map, String locationName, int x, int y,
            double lat, double lon, HashMap<String, Double> wifiStrengthTable,
            HashMap<String, Double> wifiFreqTable, HashMap<String, Double> wifiStddevTable) {
        if (wifiStrengthTable.size() == 0) {
            return null;
        }
        try {
            if (rootDir == null) {
                rootDir = rootDirectoryPath;
            }

            final File dir = new File(rootDir);
            final File tempFile = new File(dir, "temp.scan");

            if (!dir.exists()) {
                dir.mkdirs();
                if (!tempFile.exists()) {
                    tempFile.createNewFile();
                }
            }

            FileWriter writer = new FileWriter(tempFile);
            for (String key : wifiStrengthTable.keySet()) {
                double avgStrength = wifiStrengthTable.get(key);
                writer.write(key + " " + wifiFreqTable.get(key) + " " +
                        wifiStrengthTable.get(key) + " " + wifiStddevTable.get(key) + "\n");
            }

            StringTokenizer st = new StringTokenizer(map, "_");
            writer.write("Area: " + st.nextToken() + "\n");
            writer.write("Building: " + st.nextToken() + "\n");
            writer.write("Floor: " + st.nextToken() + "\n");
            writer.write("Loc: " + locationName + "\n");
            writer.write("Pos: " + x + " " + y + "\n");
            writer.write("LatLon: " + lat + " " + lon + "\n");
            writer.close();

            final String locationTitle = map.replace(' ', '-') + "_"
                    + locationName.replace(' ', '-');
            final File targetFile = new File(dir, locationTitle);

            tempFile.renameTo(targetFile);

            return locationTitle;
        } catch (IOException e) {
            Log.d(TAG, "Error opening file.");
        }
        return null;
    }

    private void dumpRawScans(String rootDir, String map, String locationName, int x, int y,
            double lat, double lon, ArrayList<HashMap<String, Double>> wifiAllScans) {
        if (wifiAllScans.size() == 0) {
            return;
        }
        try {
            if (rootDir == null) {
                rootDir = rootDirectoryPath;
            }
            final String locationTitle = map.replace(' ', '-') + "_"
                    + locationName.replace(' ', '-');
            final File dir = new File(rootDir + "/dump");
            final File targetFile = new File(dir, locationTitle);

            if (!dir.exists()) {
                dir.mkdirs();
                if (!targetFile.exists()) {
                    targetFile.createNewFile();
                }
            }
            StringBuilder wifiLocStrBuilder = new StringBuilder(); 
            FileWriter writer = new FileWriter(targetFile);
            for (HashMap<String, Double> scanMap : wifiAllScans) {
                for (String key : scanMap.keySet()) {
                    wifiLocStrBuilder.append(key + " ");
                    wifiLocStrBuilder.append(scanMap.get(key) + " ");
                }
                wifiLocStrBuilder.append("\n");
            }
            StringTokenizer st = new StringTokenizer(map, "_");
            wifiLocStrBuilder.append("Area: " + st.nextToken() + "\n");
            wifiLocStrBuilder.append("Building: " + st.nextToken() + "\n");
            wifiLocStrBuilder.append("Floor: " + st.nextToken() + "\n");
            wifiLocStrBuilder.append("Loc: " + locationName + "\n");
            wifiLocStrBuilder.append("Pos: " + x + " " + y + "\n");
            wifiLocStrBuilder.append("LatLon" + lat + " " + lon + "\n");
            writer.write(wifiLocStrBuilder.toString());
            writer.close();
        } catch (IOException e) {
            Log.d(TAG, "Error opening file.");
        }
    }
    
    private void saveCachedScansForMapLocally(String map) {
        if (!isDataClean()) {
            return;
        }
        int i = 0;
        for (String mapName : areaNames) {
            if (mapName.equals(map)) {
                Point p = locationCoords.get(i);
                Location loc = latlonCoords.get(i);
                saveScan(rootDirectoryPath, map, locationNames.get(i), p.x, p.y, loc.getLatitude(),
                        loc.getLongitude(), wifiStrengthMaps.get(i), wifiFreqMaps.get(i),
                        wifiStddevMaps.get(i));
            }
            i++;
        }
    }
    
    @Override
    public boolean reload(String rootPath, String area, HashMap<String, Double> strengthTable) {
        Log.d(TAG, "setting root=" + rootPath);
        try {
            rootDirectoryPath = rootPath;
            areaToLoad = area;
            if (strengthTable == null) {
                return readWifiMaps();
            } else {
                return readFilteredWifiMaps(strengthTable);
            }
        } catch (Exception e) {
            Log.w(TAG, "problem during reload(): " + e.toString());
            return false;
        }
    }

    @Override
    public void stopReload() {
        stopReloading = true;
    }
    
    @Override
    public boolean[] refreshData(String root, String url, String[] maps) {
        synchronized (this) {
            Log.d(TAG, "setting root=" + root);
            rootDirectoryPath = root;
            reloaded = false;
            boolean[] success = new boolean[maps.length];
            clearAllCache();
            for (int i = 0; i < maps.length; i++) {
                success[i] = httpClient.refreshData(url, maps[i], wifiStrengthMaps, wifiFreqMaps,
                        wifiStddevMaps, locationNames, areaNames, locationCoords, latlonCoords);
                deleteScan(maps[i], "");
                saveCachedScansForMapLocally(maps[i]);
            }
            buildReverseIndex();
            reloaded = true;
            return success;
        }
    }

    @Override
    public String getWifiDataVersion(String url, String map) {
        return httpClient.getWifiDataVersion(url, map);
    }

    @Override
    public String[] getAllLocationsByPrefix(String area, String prefix) {
        ArrayList<String> locNames = new ArrayList<String>();
        int i = 0;
        for (String loc : locationNames) {
            if ((area == null || areaNames.get(i).matches(area)) && loc.startsWith(prefix)) {
                locNames.add(areaNames.get(i) + "_" + loc);
            }
            i++;
        }
        allLocations = new String[locNames.size()];
        i = 0;
        for (String loc : locNames) {
            allLocations[i++] = loc;
        }
        return allLocations;
    }

    @Override
    public String getXYForLocation(String area, String loc) {
        int i = 0;
        for (String locName : locationNames) {
            if ((area == null || areaNames.get(i).matches(area)) && locName.equals(loc)) {
                return (locationCoords.get(i).x + ":" + locationCoords.get(i).y);
            }
            i++;
        }
        return null;
    }

    @Override
    public String getLatLonForLocation(String area, String loc) {
        int i = 0;
        for (String locName : locationNames) {
            if ((area == null || area.equalsIgnoreCase(areaNames.get(i))) && locName.equals(loc)) {
                return (latlonCoords.get(i).getLatitude() + ":" + latlonCoords.get(i)
                        .getLongitude());
            }
            i++;
        }
        return null;
    }

    @Override
    public String saveScan(String rootDir, String map, String locationName, int x, int y,
            double lat, double lon, HashMap<String, Double> wifiStrengthTable,
            HashMap<String, Integer> wifiCountTable,
            HashMap<String, ArrayList<Double>> wifiAllStrengths,
            ArrayList<HashMap<String, Double>> wifiAllScans, int numScans) {
        HashMap<String, Double> wifiFreqTable = new HashMap<String, Double>();
        HashMap<String, Double> wifiStddevTable = new HashMap<String, Double>();
        
        if (wifiStrengthTable.size() == 0) {
            return null;
        }
        Set<String> keys = wifiStrengthTable.keySet();
        for (String key : keys) {
            int count = wifiCountTable.get(key);
            double strength = wifiStrengthTable.get(key);
            ArrayList<Double> rawStrengths = wifiAllStrengths.get(key);
            double avgStrength = strength / count;
            double stddev = 0;
            for (double strn : rawStrengths) {
                stddev += (strn - avgStrength) * (strn - avgStrength);
            }
            wifiFreqTable.put(key, count / (double) numScans);
            wifiStddevTable.put(key, Math.sqrt(stddev / rawStrengths.size()));
            wifiStrengthTable.put(key, strength / count);
        }
        String locationTitle = saveScan(rootDir, map, locationName, x, y, lat, lon,
                wifiStrengthTable, wifiFreqTable, wifiStddevTable);
        if (locationTitle != null) {
            final File targetFile = new File(rootDir + "/" + locationTitle);
            loadFromFile(targetFile);
            buildReverseIndexFor(locationNames.size() - 1);
            dumpRawScans(rootDir, map, locationName, x, y, lat, lon, wifiAllScans);
        }
        return locationTitle;
    }

    @Override
    public void destroy() {
        rootDirectoryPath = null;
        areaToLoad = null;
        httpClient = null;
        super.destroy();
    }
    
    /**
     * Deletes the scan file for the specified map and for the location starting with a specified
     * string
     *
     * @param map
     * @param location
     */
    public void deleteScan(String map, String location) {
        File dir = new File(rootDirectoryPath);
        if (!dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        for (int i = 0; i < files.length; i++) {
            if (files[i].getName().startsWith(map + "_" + location)) {
                files[i].delete();
            }
        }
    }
}
