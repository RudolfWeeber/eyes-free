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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.graphics.Point;
import android.location.Location;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Utility functions for reading WiFi data and maps.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class Utils {

    private static final int BUFFER_SIZE = 1024;

    /**
     * Returns the names of maps stored in the specified directory.
     * @param dir The directory path.
     * @return An array of map names.
     */
    public static ArrayList<String> getMapNames(String dir) {
        ArrayList<String> maps = new ArrayList<String>();
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            int i = 0;
            for (File file : files) {
                String fileName = file.getName();
                // Ignore the directories
                if (file.isFile() && fileName.endsWith(".map")) {
                    maps.add(fileName);
                }
            }
        }
        return maps;
    }

    /**
     * Returns the names of locations contained in a map.
     * @param dir The path of the directory in which the WiFi data is stored.
     * @param map The map name.
     * @return An array of location names.
     */
    public static ArrayList<String> getLocationsForMap(String dir, String map) {
        ArrayList<String> locations = new ArrayList<String>();
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            int i = 0;
            for (File file : files) {
                String fileName = file.getName();
                // Ignore the directories
                if (file.isFile() && !fileName.equals("temp.scan") && !fileName.endsWith(".index")
                        && fileName.startsWith(map)) {
                    locations.add(fileName.substring(fileName.lastIndexOf('_') + 1));
                }
            }
        }
        return locations;
    }

    /**
     * Returns a list of all image files (jpg and png) in the specified directory.
     * @param dir The directory path.
     * @return An array of image file names.
     */
    public static ArrayList<String> getMapImageNames(String dir) {
        ArrayList<String> images = new ArrayList<String>();
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            int i = 0;
            for (File file : files) {
                String fileName = file.getName();
                // Ignore the directories
                if (file.isFile()
                        && (fileName.endsWith(".jpg") || fileName.endsWith(".JPG")
                                || fileName.endsWith(".png") || fileName.endsWith(".PNG"))) {
                    images.add(fileName);
                }
            }
        }
        return images;
    }

    /**
     * Returns a list of location names from the specified file. The file should have one location name
     * on each line.
     * @param filePath The path to the file containing location names.
     * @return An array of location names.
     * @throws IOException
     */
    public static ArrayList<String> getFutureLocationNames(String filePath) throws IOException {
        File f = new File(filePath);
        if (!f.exists()) {
            return null;
        }
        ArrayList<String> locationNames = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader(f), BUFFER_SIZE);
        String line = "";
        while ((line = reader.readLine()) != null) {
            locationNames.add(line);
        }
        return locationNames;
    }

    /**
     * Returns the MapInfo object containing details of the specified map.
     * @param path The path to map file.
     * @return MapInfo object containing the map details. 
     * @throws IOException
     */
    public static MapInfo getMapInfo(String path) throws IOException {
        MapInfo info = new MapInfo();
        String imageFile = "";
        double latTop = 0, lonLeft = 0, latBottom = 0, lonRight = 0;
        Document doc = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            doc = db.parse(new FileInputStream(new File(path)));
            Node imageNode = doc.getElementsByTagName("image").item(0);
            imageFile = imageNode.getFirstChild().getNodeValue();
            latTop = Double.parseDouble(doc.getElementsByTagName("lat-top").item(0).getFirstChild()
                    .getNodeValue());
            lonLeft = Double.parseDouble(doc.getElementsByTagName("lon-left").item(0)
                    .getFirstChild().getNodeValue());
            latBottom = Double.parseDouble(doc.getElementsByTagName("lat-bottom").item(0)
                    .getFirstChild().getNodeValue());
            lonRight = Double.parseDouble(doc.getElementsByTagName("lon-right").item(0)
                    .getFirstChild().getNodeValue());
            info.setParams(imageFile, latTop, lonLeft, latBottom, lonRight);
            NodeList pointNodes = doc.getElementsByTagName("point");
            for (int i = 0; i < pointNodes.getLength(); i++) {
                Node ptNode = pointNodes.item(i);
                NodeList children = ptNode.getChildNodes();
                String id = "", name = "";
                int x = 0, y = 0;
                double lat = 0, lon = 0;
                for (int k = 0; k < children.getLength(); k++) {
                    Node nd = children.item(k);
                    if (nd.getFirstChild() == null)
                        continue;
                    String val = nd.getFirstChild().getNodeValue();
                    if (nd.getNodeName().equals("id")) {
                        id = val;
                    } else if (nd.getNodeName().equals("x")) {
                        x = Integer.parseInt(val);
                    } else if (nd.getNodeName().equals("y")) {
                        y = Integer.parseInt(val);
                    } else if (nd.getNodeName().equals("lat")) {
                        lat = Double.parseDouble(val);
                    } else if (nd.getNodeName().equals("lon")) {
                        lon = Double.parseDouble(val);
                    } else if (nd.getNodeName().equals("name")) {
                        name = val;
                    }
                }
                Location loc = new Location("");
                loc.setLatitude(lat);
                loc.setLongitude(lon);
                Point p = new Point(x, y);
                info.allIds.add(id);
                info.allPoints.add(p);
                info.allLatLon.add(loc);
                info.pointIdMap.put(id, p);
                if (!name.equals("")) {
                    info.locNameIdMap.put(id, name);
                }
            }
            NodeList connNodes = doc.getElementsByTagName("conn");
            for (int i = 0; i < connNodes.getLength(); i++) {
                Node conn = connNodes.item(i);
                String[] idStrs = conn.getFirstChild().getNodeValue().split(" ");
                for (int k = 1; k < idStrs.length; k++) {
                    info.addConnection(idStrs[0], idStrs[k]);
                }
            }
        } catch (ParserConfigurationException pce) {
        } catch (SAXException se) {
        }
        return info;
    }

    /**
     * Populates the spinner with the specified array of strings.
     */
    public static void populateSpinner(Spinner spinner, ArrayAdapter<String> adapter,
            String[] options) {
        adapter.clear();
        for (String str : options) {
            adapter.add(str);
        }
        spinner.setAdapter(adapter);
    }

    /**
     * Returns the X-Y coordinate of the location specified by the directory, area name and
     * location name.
     * @param dir The directory path.
     * @param area The area name.
     * @param locName The location name.
     * @return The X-Y coordinate.
     * @throws IOException
     */
    public static Point getXYForWifiLocation(String dir, String area, String locName)
            throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(new File(dir + "/" + area + "_"
                + locName)), BUFFER_SIZE);
        String line = "";
        Point p = null;
        while ((line = reader.readLine()) != null) {
            String[] tokens = line.split(" ");
            if (tokens != null && tokens.length == 3 && tokens[0].equals("Pos:")) {
                p = new Point(Integer.parseInt(tokens[1]), Integer.parseInt(tokens[2]));
                break;
            }
        }
        reader.close();
        return p;
    }
    
    /**
     * Computes a path from the starting X-Y coordinate to the ending X-Y coordinate using
     * Dijkstra's single-source shortest path algorithm.
     * @param mapInfo The MapInfo object containing the map details.
     * @param sx The start X-coordinate.
     * @param sy The start Y-coordinate.
     * @param dx The destination X-coordinate.
     * @param dy The destination Y-coordinate.
     * @return The path as a sequence of point IDs.
     */
    public static ArrayList<String> computePath(MapInfo mapInfo, int sx, int sy, int dx, int dy) {
        String source = getClosestPointId(mapInfo, sx, sy);
        String destination = getClosestPointId(mapInfo, dx, dy);
        ArrayList<String> revpath = new ArrayList<String>();
        ArrayList<String> path = new ArrayList<String>();
        HashSet<String> done = new HashSet<String>();
        revpath = dijkstras(mapInfo, source, destination);
        if (revpath != null && !revpath.isEmpty()) {
            revpath.add(source);
            int len = revpath.size();
            for (int i = len - 1; i >= 0; i--) {
                path.add(revpath.get(i));
            }
        }
        return path;
    }

    private static ArrayList<String> dijkstras(MapInfo mapInfo, String src, String dest) {
        final double INF = 100000;
        final String UNDEF = "UNDEF";
        final HashMap<String, Double> dist = new HashMap<String, Double>();
        final HashMap<String, String> prev = new HashMap<String, String>();
        ArrayList<String> path = new ArrayList<String>();
        PriorityQueue<String> Q = new PriorityQueue<String>(mapInfo.connectivity.size(),
                new Comparator<String>() {
                    public int compare(String str1, String str2) {
                        if (dist.get(str1) < dist.get(str2)) {
                            return 1;
                        } else if (dist.get(str1) == dist.get(str2)) {
                            return 0;
                        } else
                            return -1;
                    }
                });
        for (String vt : mapInfo.connectivity.keySet()) {
            dist.put(vt, INF);
            prev.put(vt, UNDEF);
        }
        dist.put(src, 0.0);
        for (String vt : mapInfo.connectivity.keySet()) {
            Q.add(vt);
        }
        while (!Q.isEmpty()) {
            // String U = Q.poll();
            // TODO(chaitanyag): Use the Priority Queues method to get min.
            // Somehow it was not working, so temporarily using a linear
            // findmin.
            String U = removeMin(Q, dist);
            if (dist.get(U) == INF) {
                break;
            }
            Point p1 = mapInfo.pointIdMap.get(U);
            for (String V : mapInfo.connectivity.get(U)) {
                Point p2 = mapInfo.pointIdMap.get(V);
                double d =
                        Math.sqrt((p2.y - p1.y) * (p2.y - p1.y) + (p2.x - p1.x) * (p2.x - p1.x));
                double alt = dist.get(U) + d;
                if (alt < dist.get(V)) {
                    dist.put(V, alt);
                    prev.put(V, U);
                }
            }
        }
        String curr = dest;
        path.add(dest);
        while (true) {
            if (curr == null || dist.get(curr) == null) {
                return null;
            }
            if (curr.equals(UNDEF) || dist.get(curr) == 0.0) {
                break;
            }
            String pre = prev.get(curr);
            path.add(pre);
            curr = pre;
        }
        return path;
    }

    private static String removeMin(PriorityQueue<String> Q, HashMap<String, Double> dist) {
        double minDist = 100000;
        String minKey = "";
        for (String key : Q) {
            if (dist.get(key) < minDist) {
                minKey = key;
                minDist = dist.get(key);
            }
        }
        Q.remove(minKey);
        return minKey;
    }

    private static String getClosestPointId(MapInfo mapInfo, int x, int y) {
        double minDist = 100000;
        int closest = 0;
        for (int i = 0; i < mapInfo.allPoints.size(); i++) {
            Point p = mapInfo.allPoints.get(i);
            double dist = Math.sqrt((p.y - y) * (p.y - y) + (p.x - x) * (p.x - x));
            if (dist < 10) {
                closest = i;
                break;
            }
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return mapInfo.allIds.get(closest);
    }
}
