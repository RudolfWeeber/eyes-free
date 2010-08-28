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

import com.whereabout.common.WifiDataStore;
import com.whereabout.common.WifiScanner;

import android.content.Context;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.util.Log;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;

/**
 * This class implements Wifi scanning and landmark detection.
 *
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public abstract class WifiLocalizer {

    private static final String TAG = "WifiLocalizer";

    /**
     * Number of top matching locations to be stored
     */
    protected static final int MAX_MATCHES = 20;

    /**
     * Scanning frequency during voting-based localization
     */
    protected static final int VOTING_SCAN_FREQ = 4;

    /**
     * Maximum number of result samples to use for voting. 
     */
    protected static final int MAX_VOTING_SAMPLES = 10;

    /**
     * Stores the Wifi signal strengths of the Wifi APs seen during the previous scan session
     */
    protected HashMap<String, Double> wifiStrengthTable = null;

    /**
     * Stores the # of sightings of Wifi APs during the previous scan session
     */
    protected HashMap<String, Integer> wifiCountTable = null;

    /**
     * Number of scans during the previous scan session
     */
    protected int numScans = 0;

    /**
     * Stores location names of matching locations
     */
    protected String[] allMatchingLocations;

    /**
     * Stores match scores of matching locations
     */
    protected double[] allMatchingProximities;

    /**
     * Stores X and Y coordinates of matching locations
     */
    protected int[] allXCoords, allYCoords;

    /**
     * Stores latitude and longitude of matching locations
     */
    protected double[] allLatitudes, allLongitudes;

    /**
     * The final X-Y coordinate after interpolation between matching locations
     */
    protected int finalX, finalY;

    /**
     * The final latitude-longitude after interpolation between matching locations
     */
    protected double finalLat, finalLon;

    /**
     * Whether to use the voting-based algorithm to localize
     */
    protected boolean useVoting = false;

    /**
     * Whether voting-based algorithm is currently running
     */
    protected boolean isVoting = false;

    /**
     * Number of samples to take for the voting-based localization
     */
    protected int numSamplesForVoting = 0;

    /**
     * Stores the results from each sample during voting. This is later used to actually do the
     * voting and rank the locations by their average match scores
     */
    protected ArrayList<HashMap<String, LocationInfo>> votingSamples = null;

    protected ArrayList<String> matchingLocations = null;

    protected ArrayList<Double> matchingLocScores = null;

    protected ArrayList<Point> matchingLocCoords = null;

    protected ArrayList<Location> matchingLatlonCoords = null;

    protected ArrayList<String> finalMatchingLocations = null;

    protected ArrayList<Double> finalMatchingLocScores = null;

    protected ArrayList<Point> finalMatchingLocCoords = null;

    protected ArrayList<Location> finalMatchingLatlonCoords = null;
    
    protected Context context = null;

    protected WifiScanner wifiScanner = null;

    protected WifiDataStore dataStore = null;
    
    protected SensorManager sensorManager = null; 

    private Thread votingThread = null;
    
    private Thread continuousPositioningThread = null;

    private String expectedLocation;
    
    private boolean runPositioningLoop = false;
    
    private boolean locationUpdated = false;
    
    private boolean isDeviceMoving = false;
    
    private MotionSensorEventListener sensorEventListener = new MotionSensorEventListener();

    /**
     * This class stores the information about a location.
     *
     * @author chaitanyag@google.com (Chaitanya Gharpure)
     */
    private static class LocationInfo {
        /**
         * Name of the location
         */
        public String locationName;
        /**
         * Match score with the current Wifi signature
         */
        public double matchScore;
        /**
         * X-Y coordinate of the location
         */
        public Point xy;
        /**
         * Latitude-Longitude of the location
         */
        public Location latlon;

        public LocationInfo(String locationName, double matchScore, Point xy, Location latlon) {
            super();
            this.locationName = locationName;
            this.matchScore = matchScore;
            this.xy = xy;
            this.latlon = latlon;
        }
    }

    /** Constructor */
    public WifiLocalizer(Context ct, WifiScanner scanner) {
        context = ct;
        wifiScanner = scanner;
        wifiStrengthTable = new HashMap<String, Double>();
        wifiCountTable = new HashMap<String, Integer>();
        matchingLocations = new ArrayList<String>();
        matchingLocScores = new ArrayList<Double>();
        matchingLocCoords = new ArrayList<Point>();
        matchingLatlonCoords = new ArrayList<Location>();
        finalMatchingLocations = new ArrayList<String>();
        finalMatchingLocScores = new ArrayList<Double>();
        finalMatchingLocCoords = new ArrayList<Point>();
        finalMatchingLatlonCoords = new ArrayList<Location>();
        allMatchingLocations = new String[MAX_MATCHES];
        allMatchingProximities = new double[MAX_MATCHES];
        allXCoords = new int[MAX_MATCHES];
        allYCoords = new int[MAX_MATCHES];
        allLatitudes = new double[MAX_MATCHES];
        allLongitudes = new double[MAX_MATCHES];
        votingSamples = new ArrayList<HashMap<String, LocationInfo>>();
    }

    /**
     * Starts scanning for WiFi signals.
     * 
     * @param freq Number of scans per second.
     * @param voting Whether to use the voting approach for the subsequent location computation.
     * @param samples Number of samples to use for voting. Ignore if voting is false.
     */
    public void startScan(int freq, boolean voting, int samples) {
        clearPreviousResults();
        if (useVoting) {
            return;
        }
        useVoting = voting;
        numSamplesForVoting = samples;
        if (useVoting) {
            votingSamples.clear();
            votingThread = new Thread(votingWifiLocalizer);
            votingThread.start();
        } else {
            wifiScanner.startScan(freq);
        }
    }

    /**
     * Stops collecting Wifi signals and computes the location.
     * 
     * @param expectedLocation The expected location name, which might be used for logging.
     *     Can be null.
     */
    public void stopScanningForLocation(String expectedLocation) {
        this.expectedLocation = expectedLocation;
        if (useVoting) {
            isVoting = false;
            useVoting = false;
            try {
                votingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
        } else {
            stopScan();
            computeLocation(expectedLocation);
        }
    }

    /**
     * Stops collecting Wifi signals.
     */
    public void stopScan() {
        wifiScanner.stopScan();
        synchronized (wifiScanner) {
            wifiStrengthTable = wifiScanner.getWifiStrengthTable();
            wifiCountTable = wifiScanner.getWifiCountTable();
            numScans = wifiScanner.getNumScans();
        }
        Log.d("WifiLocalizer", "Stopped scanning for location");
    }

    /**
     * Computes the location.
     * 
     * @param expectedLocation The expected location name, which might be used for logging.
     *     Can be null.
     */
    public void computeLocation(String expectedLocation) {
        computeMatch(expectedLocation);
    }

    /**
     * Returns a list of all matching locations ordered in the decreasing order of their
     * match scores.
     * 
     * @return Array of location names.
     */
    public String[] getLocations() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLocations.size(); i++) {
            allMatchingLocations[i] = finalMatchingLocations.get(i);
        }
        return allMatchingLocations;
    }

    /**
     * Returns an array of proximity values for the matching locations indicated by
     * match scores. Lower the match score, better is the match.
     * 
     * @return Array of match scores.
     */
    public double[] getProximities() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLocScores.size(); i++) {
            allMatchingProximities[i] = finalMatchingLocScores.get(i);
        }
        return allMatchingProximities;
    }

    /**
     * Returns an array of X coordinates for the matching locations.
     * 
     * @return Array of integers.
     */
    public int[] getXCoordinates() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLocCoords.size(); i++) {
            allXCoords[i] = finalMatchingLocCoords.get(i).x;
        }
        return allXCoords;
    }

    /**
     * Returns an array of Y coordinates for the matching locations.
     * 
     * @return Array of integers.
     */
    public int[] getYCoordinates() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLocCoords.size(); i++) {
            allYCoords[i] = finalMatchingLocCoords.get(i).y;
        }
        return allYCoords;
    }

    /**
     * Returns an array of geographic latitudes for the matching locations.
     * 
     * @return Array of doubles.
     */
    public double[] getLatitudes() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLatlonCoords.size(); i++) {
            allLatitudes[i] = finalMatchingLatlonCoords.get(i).getLatitude();
        }
        return allLatitudes;
    }

    /**
     * Returns an array of geographic longitudes for the matching locations.
     * 
     * @return Array of doubles.
     */
    public double[] getLongitudes() {
        for (int i = 0; i < MAX_MATCHES && i < finalMatchingLocCoords.size(); i++) {
            allLongitudes[i] = finalMatchingLatlonCoords.get(i).getLongitude();
        }
        return allLongitudes;
    }

    /**
     * Returns what the positioning algorithm thinks is the most accurate X-coordinate.
     * 
     * @return X-coordinate
     */
    public int getX() {
        return finalX;
    }

    /**
     * Returns what the positioning algorithm thinks is the most accurate Y-coordinate.
     * 
     * @return X-coordinate
     */
    public int getY() {
        return finalY;
    }

    /**
     * Returns what the positioning algorithm thinks is the most accurate latitude.
     * 
     * @return Geographic latitude.
     */
    public double getLatitude() {
        return finalLat;
    }

    /**
     * Returns what the positioning algorithm thinks is the most accurate longitude.
     * 
     * @return Geographic longitude.
     */
    public double getLongitude() {
        return finalLon;
    }

    /**
     * Starts the continuous positioning thread.
     * 
     * @param wifiScanFrequency WiFi scanning frequency in # scans/sec.
     * @param wifiScanDuration Duration of WiFi scans in ms.
     */
    public void startContinuousPositioning(int wifiScanFrequency, long wifiScanDuration) {
        locationUpdated = false;
        runPositioningLoop = true;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorEventListener.reset();
        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        if (continuousPositioningThread == null) {
            continuousPositioningThread = new Thread(continuousPositioningLoop);
            continuousPositioningThread.start();
        }
    }
    
    public void stopContinuousPositioning() {
        locationUpdated = false;
        runPositioningLoop = false;
        sensorManager.unregisterListener(sensorEventListener);
        continuousPositioningThread.interrupt();
        try {
            continuousPositioningThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean isLocationUpdated() {
        boolean ret = locationUpdated;
        locationUpdated = false;
        return ret;
    }
    
    /**
     * Inserts the location sorted by match score.
     */
    protected void insertMatchingLocation(double score, String location, String area, Point p,
            Location latlon) {
        int len = matchingLocations.size();
        int i = 0;
        // No need to sort if we are still voting.
        if (isVoting) {
            i = len;
        } else {
            for (i = 0; i < len; i++) {
                double sc = matchingLocScores.get(i);
                if (sc > score) {
                    break;
                }
            }
        }
        matchingLocations.add(i, area + "_" + location);
        matchingLocScores.add(i, score);
        matchingLocCoords.add(i, p);
        matchingLatlonCoords.add(i, latlon);
    }

    protected void copyResultsToFinal() {
        finalMatchingLocations.clear();
        finalMatchingLocScores.clear();
        finalMatchingLocCoords.clear();
        finalMatchingLatlonCoords.clear();
        int len = matchingLocations.size();
        for (int i = 0; i < len; i++) {
            finalMatchingLocations.add(matchingLocations.get(i));
            finalMatchingLocScores.add(matchingLocScores.get(i));
            finalMatchingLocCoords.add(matchingLocCoords.get(i));
            finalMatchingLatlonCoords.add(matchingLatlonCoords.get(i));
        }
    }
    
    /**
     * Computes a match score between the previously collected Wifi signature and the known
     * signatures. This method expects the expected location for logging purposes.
     *
     * @param expectedLocation The expected location for logging. Send "" if no logging is needed
     */
    abstract protected void computeMatch(String expectedLocation);

    /**
     * Computes the match score between the specified Wifi scans.
     * @param expected The wifi scan readings of the expected location
     * @param actual The Wifi scan readings of the actual scan location to classify
     * @return Returns the match score -- lower the score, better the match
     */
    abstract protected double compareScans(HashMap<String, Double> expected,
            HashMap<String, Double> actual);

    protected void clearPreviousResults() {
        matchingLocations.clear();
        matchingLocCoords.clear();
        matchingLatlonCoords.clear();
        matchingLocScores.clear();
    }
    
    /**
     * Adds the result of the previous location computation to the voting samples.
     */
    private void addResultToVotingSamples() {
        HashMap<String, LocationInfo> result = new HashMap<String, LocationInfo>();
        int i = 0;
        for (String location: matchingLocations) {
            LocationInfo info = new LocationInfo(location, matchingLocScores.get(i),
                    matchingLocCoords.get(i), matchingLatlonCoords.get(i));
            result.put(location, info);
            i++;
        }
        if (votingSamples.size() == 30) {
            votingSamples.remove(0);
        }
        votingSamples.add(result);
    }

    /**
     * Voting is done by looking at the localization results in the voting samples and ranking
     * them by their match scores. Lower the score, better the match.
     */
    private void doVoting() {
        HashMap<String, Integer> locationSightings = new HashMap<String, Integer>();
        HashMap<String, Double> locationScores = new HashMap<String, Double>();
        HashMap<String, Point> locationXY = new HashMap<String, Point>();
        HashMap<String, Location> locationLatLon = new HashMap<String, Location>();
        int i = 0;
        for (HashMap<String, LocationInfo> sampleResult : votingSamples) {
            Set<String> locations = sampleResult.keySet();
            for (String loc: locations) {
                LocationInfo info = sampleResult.get(loc);
                if (!locationScores.containsKey(loc)) {
                    locationScores.put(loc, info.matchScore);
                    locationXY.put(loc, info.xy);
                    locationLatLon.put(loc, info.latlon);
                    locationSightings.put(loc, 1);
                } else {
                    locationScores.put(loc,
                            locationScores.get(loc) + info.matchScore);
                    locationSightings.put(loc, locationSightings.get(loc) + 1);
                }
                i++;
            }
        }
        matchingLocations.clear();
        matchingLocScores.clear();
        matchingLocCoords.clear();
        matchingLatlonCoords.clear();
        Set<String> locations = locationScores.keySet();
        i = 0;
        for (String loc : locations) {
            int index = loc.lastIndexOf('_');
            insertMatchingLocation(locationScores.get(loc) / locationSightings.get(loc),
                    loc.substring(index + 1), loc.substring(0, index), locationXY.get(loc),
                    locationLatLon.get(loc));
            i++;
        }
    }
    
    private class MotionSensorEventListener implements SensorEventListener {
        private static final int BUFFER_LEN = 20;
        
        private float[][] buffer = new float[3][BUFFER_LEN];
        
        private float[] var = new float[3];
        
        private int st = 0, en = 0;
        
        private long startMovingTime = 0;
        
        private boolean startedMoving = false; 
        
        private boolean checkMovement() {
            int cnt = 0;
            for (int i = 0; i < 3; i++) var[i] = 0;
            for (int i = (st + 1) % BUFFER_LEN; i != en; i = (i + 1) % BUFFER_LEN, cnt++) {
                int p = i - 1 < 0 ? BUFFER_LEN - 1 : i - 1;
                for (int k = 0; k < 3; k++) var[k] += Math.abs(buffer[k][i] - buffer[k][p]);
            }
            if (var[0] > 4 || var[1] > 4 || var[2] > 4) {
                if (!startedMoving) {
                    startedMoving = true;
                    startMovingTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - startMovingTime > 1500) {
                    return true;
                }
            } else {
                startedMoving = false;
            }
            return false;
        }
        
        public void reset() {
            st = 0;
            en = 0;
        }
        
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                for (int i = 0; i < 3; i++) {
                    buffer[i][en] = event.values[i];
                }
                en = (en + 1) % BUFFER_LEN;
                if (en == st) {
                    st = (st + 1) % BUFFER_LEN;
                }
                isDeviceMoving = checkMovement();
            }
        }
    }
    
    /**
     * This thread performs continuous localization and indicates when a location update is
     * available.
     */
    private Runnable continuousPositioningLoop = new Runnable() {
        public void run() {
            try {
                while (runPositioningLoop) {
                    if (!useVoting) {
                        if (wifiScanner.isScanningWifi()) {
                            stopScanningForLocation("");
                            if (!isDeviceMoving) {
                                addResultToVotingSamples();
                                doVoting();
                            } else {
                                votingSamples.clear();
                            }
                            interpolateXY();
                            interpolateLatLon();
                            copyResultsToFinal();
                            locationUpdated = true;
                        }
                        startScan(4, false, 0);
                    }
                    Thread.sleep(1000);
                }
                stopScan();
            } catch (InterruptedException e) {
                Log.e(TAG, "Wifi localization interrupted: " + e.getMessage());
            }
        }
    };
    
    /**
     * This thread implements the voting-based localization algorithm.
     */
    private Runnable votingWifiLocalizer = new Runnable() {

        private ArrayList<HashMap<String, Double>> wifiStrengthTableHistory =
                new ArrayList<HashMap<String, Double>>();
        private ArrayList<HashMap<String, Integer>> wifiCountTableHistory =
            new ArrayList<HashMap<String, Integer>>();
        private ArrayList<Integer> wifiNumScansHistory = new ArrayList<Integer>();

        public void run() {
            int sampleCount = 0;
            isVoting = true;
            wifiStrengthTableHistory.clear();
            wifiCountTableHistory.clear();
            wifiNumScansHistory.clear();
            wifiScanner.waitWhileFlushing();
            while (isVoting && (numSamplesForVoting == 0 || sampleCount < numSamplesForVoting)) {
                try {
                    wifiScanner.startScan(VOTING_SCAN_FREQ);
                    Thread.sleep(500);
                    stopScan();
                    wifiStrengthTableHistory.add(wifiStrengthTable);
                    wifiCountTableHistory.add(wifiCountTable);
                    wifiNumScansHistory.add(numScans);
                } catch (InterruptedException ie) {
                    break;
                }
                sampleCount++;
            }
            computeLocationForHistory();
            isVoting = false;
            doVoting();
            interpolateXY();
            interpolateLatLon();
            copyResultsToFinal();
            useVoting = false;
        }

        /**
         * Computes location for each Wifi scan in the history. The results of this computation
         * are used to vote for the winning location.
         */
        private void computeLocationForHistory() {
            int len = wifiStrengthTableHistory.size();
            for (int i = 0; i < len; i++) {
                wifiStrengthTable = wifiStrengthTableHistory.get(i);
                wifiCountTable = wifiCountTableHistory.get(i);
                numScans = wifiNumScansHistory.get(i);
                computeLocation(expectedLocation);
                addResultToVotingSamples();
            }
        }
    };

    protected void interpolateXY() {
        if (matchingLocations.size() < 2) {
            return;
        }
        // TODO(chaitanyag): Improve this logic. Maybe consider the locations
        // with valid xy if the top location does not have a good match.
        if ((matchingLocCoords.get(0).x == -1 && matchingLocCoords.get(0).y == -1)
                || (matchingLocCoords.get(1).x == -1 && matchingLocCoords.get(1).y == -1)) {
            finalX = matchingLocCoords.get(0).x;
            finalY = matchingLocCoords.get(0).y;
            return;
        }
        double score0 = matchingLocScores.get(0);
        
        int x = matchingLocCoords.get(0).x;
        int y = matchingLocCoords.get(0).y;
        
        double mindist = 1000000;
        int minloc = 0;
        for (int i = 1; i < matchingLocations.size(); i++) {
            int xi = matchingLocCoords.get(i).x;
            int yi = matchingLocCoords.get(i).y;
            double dist = Math.sqrt(((y - yi) * (y - yi)) + ((x - xi) * (x - xi)));
            if (dist < mindist) {
                mindist = dist;
                minloc = i;
            }
        }
        double score1 = matchingLocScores.get(minloc);
        double aff0 = score0 / (score0 + score1);
        double xdiff = matchingLocCoords.get(0).x - matchingLocCoords.get(minloc).x;
        double ydiff = matchingLocCoords.get(0).y - matchingLocCoords.get(minloc).y;
        finalX = (int) (matchingLocCoords.get(0).x - (xdiff * aff0));
        finalY = (int) (matchingLocCoords.get(0).y - (ydiff * aff0));
    }
    
    protected void interpolateLatLon() {
        if (matchingLocations.size() < 2) {
            return;
        }
        // TODO(chaitanyag): Improve this logic. Maybe consider the locations
        // with
        // valid lat-lon if the top location does not have a good match.
        double lt0 = matchingLatlonCoords.get(0).getLatitude();
        double ln0 = matchingLatlonCoords.get(0).getLongitude();
        
        double mindist = 1000000;
        int minloc = 0;
        for (int i = 1; i < matchingLocations.size(); i++) {
            double lti = matchingLatlonCoords.get(i).getLatitude();
            double lni = matchingLatlonCoords.get(i).getLongitude();
            double dist = Math.sqrt(((lni - ln0) * (lni - ln0)) + ((lti - lt0) * (lti - lt0)));
            if (dist < mindist) {
                mindist = dist;
                minloc = i;
            }
        }
        double lt = matchingLatlonCoords.get(minloc).getLatitude();
        double ln = matchingLatlonCoords.get(minloc).getLongitude();
        
        if ((lt0 == 0 && ln0 == 0) || (lt == 0 && ln == 0)) {
            finalLat = lt0;
            finalLon = ln0;
            return;
        }
        double score0 = Math.max(15, matchingLocScores.get(0));
        double score1 = Math.max(15, matchingLocScores.get(1));
        double aff0 = score0 / (score0 + score1);
        double ltdiff = lt0 - lt;
        double lndiff = ln0 - ln;
        finalLat = lt0 - (ltdiff * aff0);
        finalLon = ln0 - (lndiff * aff0);
    }
    
    /**
     * Stop scanning WiFi and nulls the data structures to mark for garbage collection.
     * Call this in your shutdown pipeline.
     */
    public void destroy() {
        runPositioningLoop = false;
        wifiScanner.stopScan();
        try {
            if (continuousPositioningThread != null) {
                continuousPositioningThread.interrupt();
                continuousPositioningThread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        wifiStrengthTable = null;
        wifiCountTable = null;
        matchingLocations = null;
        matchingLocScores = null;
        matchingLocCoords = null;
        matchingLatlonCoords = null;
        finalMatchingLocations = null;
        finalMatchingLocScores = null;
        finalMatchingLocCoords = null;
        finalMatchingLatlonCoords = null;
        allMatchingLocations = null;
        allMatchingProximities = null;
        allXCoords = null;
        allYCoords = null;
        allLatitudes = null;
        allLongitudes = null;
        votingSamples = null;
    }
}
