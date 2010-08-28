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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

import android.content.Context;

/**
 * This class does the Wifi based localization and at the same time dumps debug log
 * messages in files.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class LocalWifiLocalizerDebugger extends LocalWifiLocalizer {

    private FileWriter writer = null;
    private FileWriter detailwriter = null;
    
    public LocalWifiLocalizerDebugger(Context ct, WifiScanner scanner) {
        super(ct, scanner);
        dataStore = scanner.getDataStore();
    }

    @Override
    protected double compareScans(HashMap<String, Double> expected,
            HashMap<String, Double> actual) {
        int numMatchingKeys = 0;
        debug(detailwriter, "*** Location: " + locationCurrentlyMatchingWith + "\n");
        for (String key : actual.keySet()) {
            if (expected.containsKey(key)) {
                debug(detailwriter, key + " : actual = " + actual.get(key) + " : expected = " +
                        expected.get(key) + "\n");
            }
            else { 
                debug(detailwriter, "- " + key + " : " + actual.get(key) + "\n");
            }
        }
        for (String mackey : expected.keySet()) {
            if (!actual.containsKey(mackey)) {
                debug(detailwriter, "+ " + mackey + " : " + expected.get(mackey)  + "\n");
            }
        }
        double matchScore = super.compareScans(expected, actual);
        debug(detailwriter, "*** MatchScore: " + matchScore + "\n---------------------\n\n");
        return matchScore;
    }
    
    /**
     * Computes a match score between the previously collected Wifi signature
     * and the known signatures.
     */
    @Override
    protected void computeMatch(String expectedLocation) {
        synchronized (wifiScanner) {
            try {
                //=================================================================================
                initDebug(expectedLocation);
                for (String key : wifiStrengthTable.keySet()) {
                    double strength = wifiStrengthTable.get(key) / wifiCountTable.get(key);
                    debug(writer, key + " " + strength + "\n");
                }
                debug(detailwriter, "- : Currently seeing but not expected.\n");
                debug(detailwriter, "+ : Currently not seeing but expected.\n\n");
                //=================================================================================
            
                super.computeMatch(expectedLocation);
                
                //=================================================================================
                for (int i = 0; i < matchingLocations.size(); i++) {
                    debug(writer, i + " : " + matchingLocations.get(i) + " : " +
                            matchingLocScores.get(i) + "\n");
                }
                debug(writer, "Expected location: " + expectedLocation + "\n");
                writer.close();
                detailwriter.close();
                //=================================================================================
            } catch (IOException ioe) {}
        }
    }
    
    private void initDebug(String fileName) throws IOException {
        File dirFile = new File("/sdcard/wifiscans/debug");
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File debugFile = new File("/sdcard/wifiscans/debug/" +
                System.currentTimeMillis() + "-" + fileName);
        File debugDetailFile = new File("/sdcard/wifiscans/debug/" +
                System.currentTimeMillis() + "-" + fileName + "-details");
        if (!debugFile.exists()) {
            debugFile.createNewFile();
        }
        writer = new FileWriter(debugFile);
        detailwriter = new FileWriter(debugDetailFile);
    }
    
    private void debug(final FileWriter writer, final String str) {
        try {
            if (writer != null) {
                writer.write(str);
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
