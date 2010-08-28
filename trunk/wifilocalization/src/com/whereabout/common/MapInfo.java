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

/**
 * Stores the information about a map consisting of locations at which Wifi data is collected.
 * This class is treated more like a data structure whose members can be accessed directly.
 * It has some convenience methods to work with the internal data structures. 
 * 
 * @author chaitanyag (Chaitanya Gharpure)
 */
public class MapInfo {
    /**
     * The name of the map image file.
     */
    public String imageFile;
    
    /**
     * The North bounding geographic latitude of the map.
     */
    public double latTop;

    /**
     * The West bounding geographic longitude of the map.
     */
    public double lonLeft;
    
    /**
     * The South bounding geographic latitude of the map.
     */
    public double latBottom;
    
    /**
     * The East bounding geographic longitude of the map.
     */
    public double lonRight;
    
    /**
     * List of IDs of all points on the map. 
     */
    public ArrayList<String> allIds = new ArrayList<String>();
    
    /**
     * List of all {@link Point}s on the map.
     */
    public ArrayList<Point> allPoints = new ArrayList<Point>();
    
    /**
     * List of all {@link Location}s on the map.
     */
    public ArrayList<Location> allLatLon = new ArrayList<Location>();
    
    /**
     * A map of point IDs to their respective {@links Point}s which store the XY coordinate.
     */
    public HashMap<String, Point> pointIdMap = new HashMap<String, Point>();
    
    /**
     * A map of point IDs to their location names.
     */
    public HashMap<String, String> locNameIdMap = new HashMap<String, String>();
    
    /**
     * A map of point IDs to a list of IDs of points to which it is connected.
     */
    public HashMap<String, HashSet<String>> connectivity =
            new HashMap<String, HashSet<String>>(); 
    
    public MapInfo() {}

    /**
     * Creates a MapInfo object.
     * 
     * @param imageFile The name of the map image file (jpg or png).
     * @param latTop The North bounding geographic latitude of the map.
     * @param lonLeft The West bounding geographic longitude of the map.
     * @param latBottom The South bounding geographic latitude of the map.
     * @param lonRight The East bounding geographic longitude of the map.
     */
    public MapInfo(String imageFile, double latTop, double lonLeft, double latBottom,
            double lonRight) {
        super();
        setParams(imageFile, latTop, lonLeft, latBottom, lonRight);
    }
    
    /**
     * Sets the parameters for this MapInfo object.
     * 
     * @param imageFile The name of the map image file (jpg or png).
     * @param latTop The North bounding geographic latitude of the map.
     * @param lonLeft The West bounding geographic longitude of the map.
     * @param latBottom The South bounding geographic latitude of the map.
     * @param lonRight The East bounding geographic longitude of the map.
     */
    public void setParams(String imageFile, double latTop, double lonLeft, double latBottom,
            double lonRight) {
        this.imageFile = imageFile;
        this.latTop = latTop;
        this.lonLeft = lonLeft;
        this.latBottom = latBottom;
        this.lonRight = lonRight;
    }
    
    /**
     * Clears this MapInfo object.
     */
    public void clear() {
        allPoints.clear();
        allIds.clear();
        allLatLon.clear();
        pointIdMap.clear();
        locNameIdMap.clear();
        connectivity.clear();
    }
    
    /**
     * Adds a point to this MapInfo object.
     * 
     * @param id The ID of the point.
     * @param point The {@link Point} object specifying the XY coordinate of the point.
     * @param location The {@link Location} object containing the geographic lat-lon of the point.
     */
    public void addPoint(String id, Point point, Location location) {
        allIds.add(id);
        allPoints.add(point);
        allLatLon.add(location);
        pointIdMap.put(id, point);
    }
    
    /**
     * Removes a point on this MapInfo object.
     * 
     * @param index The index from which to remove the point.
     */
    public void removePoint(int index) {
        pointIdMap.remove(allIds.get(index));
        allIds.remove(index);
        allPoints.remove(index);
        allLatLon.remove(index);
    }
    
    /**
     * Creates a new point by assigning a new ID.
     * 
     * @param point The {@link Point} object specifying the XY coordinate of the point.
     * @param latlon The {@link Location} object containing the geographic lat-lon of the point.
     */
    public void createPoint(Point point, Location latlon) {
        String id = "0";
        if (allIds.size() > 0) {
            id = (Integer.parseInt(allIds.get(allIds.size() - 1)) + 1) + "";
        }
        addPoint(id, point, latlon);
    }
    
    /**
     * Adds a connection between two point in this MapInfo object.
     * 
     * @param pt1 point ID.
     * @param pt2 point ID.
     */
    public void addConnection(String pt1, String pt2) {
        if (pointIdMap.containsKey(pt1) && pointIdMap.containsKey(pt2)) {
            // pt2 in pt1's list
            if (!connectivity.containsKey(pt1)) {
                connectivity.put(pt1, new HashSet<String>());
            }
            connectivity.get(pt1).add(pt2);
            // pt1 in pt2's list
            if (!connectivity.containsKey(pt2)) {
                connectivity.put(pt2, new HashSet<String>());
            }
            connectivity.get(pt2).add(pt1);
        }
    }
    
    /**
     * Removes all connection from the point with the specified ID.
     * 
     * @param pt1 point ID.
     */
    public void removeConnections(String pt1) {
        if (pointIdMap.containsKey(pt1) && connectivity.containsKey(pt1)) {
            HashSet<String> toIds = connectivity.get(pt1);
            for (String toId : toIds) {
                connectivity.get(toId).remove(pt1);
            }
            connectivity.remove(pt1);
        }
    }
}
