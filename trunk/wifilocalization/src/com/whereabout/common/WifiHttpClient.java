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
import android.text.format.DateUtils;
import android.util.Log;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Class implementing fetching and saving of Wifi data via HTTP.
 *
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class WifiHttpClient {
    private static final String TAG = "WifiHttpClient";

    private static final int SMALL_BUFFER_SIZE = 128;
    private static final int LARGE_BUFFER_SIZE = 8192;

    private static final boolean TRACE_REQUESTS = true;

    private static final int SECOND_IN_MILLIS = (int) DateUtils.SECOND_IN_MILLIS;

    private static final String HEADER_ACCEPT_ENCODING = "Accept-Encoding";
    private static final String ENCODING_GZIP = "gzip";

    private static final String USER_AGENT = "whereabout/0 (gzip)";

    private DefaultHttpClient httpClient;

    public WifiHttpClient() {
        final HttpParams params = new BasicHttpParams();

        // Use generous timeouts for slow mobile networks
        HttpConnectionParams.setConnectionTimeout(params, 20 * SECOND_IN_MILLIS);
        HttpConnectionParams.setSoTimeout(params, 20 * SECOND_IN_MILLIS);

        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpProtocolParams.setUserAgent(params, USER_AGENT);

        final DefaultHttpClient client = new DefaultHttpClient(params);

        if (TRACE_REQUESTS) {
            client.addRequestInterceptor(new HttpRequestInterceptor() {
                public void process(HttpRequest request, HttpContext context) {
                    Log.v(TAG, "outgoing request: " + request.getRequestLine());
                }
            });
        }

        client.addRequestInterceptor(new HttpRequestInterceptor() {
            public void process(HttpRequest request, HttpContext context) {
                // Add header to accept gzip content
                if (!request.containsHeader(HEADER_ACCEPT_ENCODING)) {
                    request.addHeader(HEADER_ACCEPT_ENCODING, ENCODING_GZIP);
                }
            }
        });

        client.addResponseInterceptor(new HttpResponseInterceptor() {
            public void process(HttpResponse response, HttpContext context) {
                // Inflate any responses compressed with gzip
                final HttpEntity entity = response.getEntity();
                final Header encoding = entity.getContentEncoding();
                if (encoding != null) {
                    for (HeaderElement element : encoding.getElements()) {
                        if (element.getName().equalsIgnoreCase(ENCODING_GZIP)) {
                            response.setEntity(new InflatingEntity(response.getEntity()));
                            break;
                        }
                    }
                }
            }
        });

        httpClient = client;
    }

    /**
     * Simple {@link HttpEntityWrapper} that inflates the wrapped
     * {@link HttpEntity} by passing it through {@link GZIPInputStream}.
     */
    private static class InflatingEntity extends HttpEntityWrapper {
        public InflatingEntity(HttpEntity wrapped) {
            super(wrapped);
        }

        @Override
        public InputStream getContent() throws IOException {
            return new GZIPInputStream(wrappedEntity.getContent());
        }

        @Override
        public long getContentLength() {
            return -1;
        }
    }

    private static Pattern sSpace = Pattern.compile(" ");

    /**
     * Refreshes the Wifi data for the specified map and populates the data structures provided.
     *
     * @param url The URL to fetch the data from
     * @param map The map name for which to refresh the Wifi data
     * @param wifiStrengthMaps
     * @param wifiFreqMaps
     * @param wifiStddevMaps
     * @param locationNames
     * @param areaNames
     * @param locationCoords
     * @param latlonCoords
     * @return true if the refresh was successful, false otherwise
     */
    public boolean refreshData(String url, String map,
            ArrayList<HashMap<String, Double>> wifiStrengthMaps,
            ArrayList<HashMap<String, Double>> wifiFreqMaps,
            ArrayList<HashMap<String, Double>> wifiStddevMaps,
            ArrayList<String> locationNames,
            ArrayList<String> areaNames,
            ArrayList<Point> locationCoords,
            ArrayList<Location> latlonCoords) {
        BufferedReader reader = null;
        try {
            HttpPost method = new HttpPost(url);
            method.addHeader("Pragma", "no-cache");
            method.addHeader("Content-Type", "application/x-www-form-urlencoded");
            List <NameValuePair> loginInfo = new ArrayList <NameValuePair>();
            loginInfo.add(new BasicNameValuePair("mapname", map));
            loginInfo.add(new BasicNameValuePair("type", "exact"));
            loginInfo.add(new BasicNameValuePair("locationname", ""));
            loginInfo.add(new BasicNameValuePair("boxlowxorlat", "0"));
            loginInfo.add(new BasicNameValuePair("boxlowyorlong", "0"));
            loginInfo.add(new BasicNameValuePair("boxhighxorlat", "0"));
            loginInfo.add(new BasicNameValuePair("boxhighyorlong", "0"));
            HttpEntity entity = new UrlEncodedFormEntity(loginInfo, HTTP.UTF_8);
            method.setEntity(entity);
            HttpResponse res = httpClient.execute(method);
            reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()),
                    LARGE_BUFFER_SIZE);
            String line = "", locName = "", mapName = "";
            int x = -1, y = -1;
            double lat = 0, lon = 0;
            HashMap<String, Double> strengthMap = null;
            HashMap<String, Double> freqMap = null;
            HashMap<String, Double> stddevMap = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Location Map = ")) {
                    mapName = line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim();
                    strengthMap = new HashMap<String, Double>();
                    freqMap = new HashMap<String, Double>();
                    stddevMap = new HashMap<String, Double>();
                } else if (line.contains("Location Created Date = ")) {
                    areaNames.add(mapName);
                    locationNames.add(locName);
                    locationCoords.add(new Point(x, y));
                    /* Set provider as WIFI location service */
                    Location latlon = new Location("");
                    latlon.setLatitude(lat);
                    latlon.setLongitude(lon);
                    latlonCoords.add(latlon);
                    wifiStrengthMaps.add(strengthMap);
                    wifiFreqMaps.add(freqMap);
                    wifiStddevMaps.add(stddevMap);
                } else if (line.contains("WIFI Signature = ")) {
                    final String input = line
                            .substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim();
                    final String[] tokens = sSpace.split(input);
                    if (tokens.length == 4) {
                        strengthMap.put(tokens[0], Double.parseDouble(tokens[2]));
                        freqMap.put(tokens[0], Double.parseDouble(tokens[1]));
                        stddevMap.put(tokens[0], Double.parseDouble(tokens[3]));
                    }
                } else if (line.contains("Location Name = ")) {
                    locName = line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim();
                } else if (line.contains("Local X")) {
                    x = (int) Double.parseDouble(
                            line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim());
                } else if (line.contains("Local Y")) {
                    y = (int) Double.parseDouble(
                            line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim());
                } else if (line.contains("Location Latitude = ")) {
                    lat = Double.parseDouble(
                            line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim());
                } else if (line.contains("Location Longitude = ")) {
                    lon = Double.parseDouble(
                            line.substring(line.indexOf("=") + 1, line.indexOf("<BR>")).trim());
                }
            }
            return true;
        } catch (UnsupportedEncodingException uee) {
            Log.e(TAG, "Error refreshing data: " + uee.getMessage());
        } catch (ClientProtocolException cpe) {
            Log.e(TAG, "Error refreshing data: " + cpe.getMessage());
        } catch (IOException ioe) {
            Log.e(TAG, "Error refreshing data: " + ioe.getMessage());
        } catch (Exception e) {
            // TODO(jsharkey): remove this paranoid catch-all at some point
            Log.e(TAG, "Error refreshing data: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
        }
        return false;
    }

    public boolean saveScanRemote(String url, String rootDir, String map, String locationName,
            int x, int y, double lat, double lon, HashMap<String, Double> wifiStrengthTable,
            HashMap<String, Integer> wifiCountTable,
            HashMap<String, ArrayList<Double>> wifiAllStrengths, int numScans) {

        String wifiStr = "";
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
            stddev = Math.sqrt(stddev / rawStrengths.size());
            wifiStr += key + " " + count / (double) numScans + " " + strength / count + " " +
                    stddev + "\n";
        }
        try {
            HttpPost method = new HttpPost(url);
            method.addHeader("Pragma", "no-cache");
            method.addHeader("Content-Type", "application/x-www-form-urlencoded");
            List <NameValuePair> loginInfo = new ArrayList <NameValuePair>();
            loginInfo.add(new BasicNameValuePair("mapname", map));
            loginInfo.add(new BasicNameValuePair("name", locationName));
            loginInfo.add(new BasicNameValuePair("update", "no"));
            loginInfo.add(new BasicNameValuePair("lat", lat + ""));
            loginInfo.add(new BasicNameValuePair("long", lon + ""));
            loginInfo.add(new BasicNameValuePair("x", x + ""));
            loginInfo.add(new BasicNameValuePair("y", y + ""));
            loginInfo.add(new BasicNameValuePair("wifi", wifiStr));
            loginInfo.add(new BasicNameValuePair("key",
                    "496e646f6f72576966694c6f63616c697a6174696f6e"));
            HttpEntity entity = new UrlEncodedFormEntity(loginInfo, HTTP.UTF_8);
            method.setEntity(entity);
            HttpResponse res = httpClient.execute(method);
            int statusCode = res.getStatusLine().getStatusCode();
            return statusCode >= 200 && statusCode < 300;
        } catch (UnsupportedEncodingException uee) {
            uee.printStackTrace();
        } catch (ClientProtocolException cpe) {
            cpe.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return false;
    }

    public String getWifiDataVersion(String url, String map) {
        BufferedReader reader = null;
        try {
            HttpGet method = new HttpGet(url + "?mapName=" + map);
            method.addHeader("Pragma", "no-cache");
            method.addHeader("Content-Type", "application/x-www-form-urlencoded");
            HttpResponse res = httpClient.execute(method);
            int statusCode = res.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                return "0";
            }
            reader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()),
                    SMALL_BUFFER_SIZE);
            String line = "", locName = "", mapName = "";
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")) {
                    return line.trim();
                }
            }
            return "0";
        } catch (UnsupportedEncodingException uee) {
            Log.e(TAG, "Error refreshing data: " + uee.getMessage());
        } catch (ClientProtocolException cpe) {
            Log.e(TAG, "Error refreshing data: " + cpe.getMessage());
        } catch (IOException ioe) {
            Log.e(TAG, "Error refreshing data: " + ioe.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing reader: " + e.getMessage());
                }
            }
        }
        return "0";
    }
}
