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

import com.whereabout.common.WifiScanner;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;

import android.content.Context;

/**
 * This class does the Wifi based localization with the computation happening on
 * the phone.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class LocalWifiLocalizer extends WifiLocalizer {
    protected String locationCurrentlyMatchingWith = "";
        
    public LocalWifiLocalizer(Context ct, WifiScanner scanner) {
        super(ct, scanner);
        dataStore = scanner.getDataStore();
    }

    @Override
    protected double compareScans(HashMap<String, Double> expected,
            HashMap<String, Double> actual) {
        double matchScore = 0;
        int totalCount = 0;
        int numMatchingKeys = 0;
        for (String key : actual.keySet()) {
            double v1 = actual.get(key);
            if (expected.containsKey(key)) {
                if (expected.get(key) > -100) {
                    double v2 = expected.get(key);
                    matchScore += ((v1 - v2) * (v1 - v2));
                    totalCount++;
                }
                if (expected.get(key) > -85) {
                    numMatchingKeys++;
                }
            }
        }
        if (matchScore > 0) {
            int strongExpectedPoints = 0;
            int strongUnexpectedPoints = 0;
            for (String key : expected.keySet()) {
                if (!actual.containsKey(key)) {
                    // Assign -100 signal strength to wifi APs that are not seen
                    // but expected.
                    if (expected.get(key) > -75) {
                        matchScore += ((expected.get(key) + 100) *
                                (expected.get(key) + 100));
                        totalCount++;
                    }
                }
                if (expected.get(key) > -85) {
                    // Count points that are seen and expected to be strong
                    strongExpectedPoints++;
                }
            }
            // Count strong points that are seen but not expected
            for (String key : actual.keySet()) {
                if (!expected.containsKey(key) && actual.get(key) > -75 &&
                    dataStore.allMacAddresses.contains(key)) {
                    strongUnexpectedPoints++;
                }
            }
            if (((double) numMatchingKeys / (strongExpectedPoints + strongUnexpectedPoints)) <
                    0.25) {
                matchScore = 100000;
            } else {
                for (String key : actual.keySet()) {
                    double v1 = actual.get(key);
                    // Assign -100 signal strength to wifi APs that are seen but not expected.
                    if (!expected.containsKey(key) && v1 > -75 &&
                        dataStore.allMacAddresses.contains(key)) {
                        matchScore += ((v1 + 100) * (v1 + 100));
                        totalCount++;
                    }
                }
                matchScore = Math.sqrt(matchScore) *
                        ((double) (strongExpectedPoints + strongUnexpectedPoints) /
                        numMatchingKeys);
            }
        } else {
            matchScore = 100000;
        }
        if (matchScore != 100000) {
            matchScore /= totalCount;
        }
        return matchScore;
    }
    
    /**
     * Computes a match score between the previously collected Wifi signature
     * and the known signatures.
     */
    @Override
    protected void computeMatch(String expectedLocation) {
        synchronized (dataStore) {
        synchronized (wifiScanner) {
            Set<String> validLocations = new HashSet<String>();
            int bestMatchMap = -1;
            clearPreviousResults();
            
            // Find all valid locations by looking at the reverse index
            for (String addr : wifiStrengthTable.keySet()) {
                double strength = wifiStrengthTable.get(addr) / wifiCountTable.get(addr);
                if (strength > -75)  {
                    // Locations that have seen previously seen this Wifi point (addr)
                    Set<String> addrs = dataStore.wifiMacAddrToLocationMap.get(addr);
                    if (addrs != null) {
                        validLocations.addAll(addrs);
                    }
                }
                wifiStrengthTable.put(addr, strength);
                wifiCountTable.put(addr, 1);
            }
    
            for (int i = 0; i < dataStore.wifiStrengthMaps.size(); i++) {
                if (!validLocations.contains(dataStore.areaNames.get(i) + "_" +
                        dataStore.locationNames.get(i))) {
                    continue;
                }
                locationCurrentlyMatchingWith = dataStore.locationNames.get(i);
                HashMap<String, Double> mapTable = dataStore.wifiStrengthMaps.get(i);
                double matchScore = compareScans(mapTable, wifiStrengthTable);
                if (matchScore < 100000) {
                    insertMatchingLocation(matchScore, dataStore.locationNames.get(i),
                            dataStore.areaNames.get(i), dataStore.locationCoords.get(i),
                            dataStore.latlonCoords.get(i));
                }
            }
        }
        }
    }
}

