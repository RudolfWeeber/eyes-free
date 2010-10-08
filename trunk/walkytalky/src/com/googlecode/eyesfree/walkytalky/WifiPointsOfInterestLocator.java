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

import com.whereabout.common.MapInfo;
import com.whereabout.common.Utils;
import com.whereabout.location.LocationManager;

import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Finds the points of interest that are nearby given a map and a Location
 * object that has wifi data extras.
 * 
 * @author chaitaynag@google.com (Chaitanya Gharpure)
 * @author clchen@google.com (Charles L. Chen)
 */
public class WifiPointsOfInterestLocator {
    private MapInfo mapInfo;

    private String currentMap;

    public WifiPointsOfInterestLocator() {
    }

    private void setMap(String mapName) {
        if (mapName.equals(currentMap)) {
            return;
        }
        try {
            mapInfo = Utils.getMapInfo(com.whereabout.common.Constants.MAP_DIR + "/" + mapName
                    + ".map");
            currentMap = mapName;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ArrayList<String> getWifiLocationsOfInterestIds(Location loc) {
        ArrayList<String> interests = new ArrayList<String>();

        Bundle extras = loc.getExtras();

        String[] matchMaps = extras.getStringArray(LocationManager.MATCH_MAPS);
        String[] matchLocations = extras.getStringArray(LocationManager.MATCH_LOCATIONS);
        double[] matchScores = extras.getDoubleArray(LocationManager.MATCH_SCORES);
        int[] matchXCoords = extras.getIntArray(LocationManager.MATCH_XCOORDS);
        int[] matchYCoords = extras.getIntArray(LocationManager.MATCH_YCOORDS);
        int x = extras.getInt(LocationManager.FINAL_X);
        int y = extras.getInt(LocationManager.FINAL_Y);

        if ((x == 0 && y == 0) || matchLocations == null) {
            return null;
        }
        if ((matchLocations[0] != null) && (matchScores[0] < 10)) {
            setMap(matchLocations[0].substring(0, matchLocations[0].lastIndexOf('_')));
        } else {
            return null;
        }
        int i = 0;
        for (Point p : mapInfo.allPoints) {
            String id = mapInfo.allIds.get(i);
            String name = mapInfo.locNameIdMap.get(mapInfo.allIds.get(i));
            if (name != null) {
                double dist = Math.sqrt((p.y - y) * (p.y - y) + (p.x - x) * (p.x - x));
                if (dist < 100) {
                    interests.add(id);
                }
            }
            i++;
        }
        return interests;
    }

    public ArrayList<String> getWifiLocationsOfInterest(Location loc) {
        ArrayList<String> locationsOfInterest = new ArrayList<String>();
        ArrayList<String> ids = getWifiLocationsOfInterestIds(loc);
        if (ids == null) {
            return null;
        }
        for (String id : ids) {
            String name = mapInfo.locNameIdMap.get(id);
            if (!locationsOfInterest.contains(name)) {
                locationsOfInterest.add(name);
            }
        }
        return locationsOfInterest;
    }

}
