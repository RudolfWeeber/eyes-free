/*
 * Copyright (C) 2008 Google Inc.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * This class implements methods to get street address from lat-lon using
 * reverse geocoding API through HTTP.
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class ReverseGeocoder {
    /**
     * Interface for the callbacks to be used when a street is located
     */
    public interface OnAddressLocatedListener {
        public void onAddressLocated(Address address);
    }

    private OnAddressLocatedListener cb;

    private static final String ENCODING = "UTF-8";

    // URL for obtaining reverse geocoded location
    private static final String URL_GEO_STRING = "http://maps.google.com/maps/api/geocode/json?sensor=false&latlng=";

    public ReverseGeocoder(OnAddressLocatedListener callback) {
        cb = callback;
    }

    /**
     * Queries the map server and obtains the reverse geocoded address of the
     * specified location.
     * 
     * @param lat The latitude in degrees
     * @param lon The longitude in degrees
     */
    public void getAddressAsync(double lat, double lon) {
        final double latitude = lat;
        final double longitude = lon;
        /**
         * Runnable for fetching the address asynchronously
         */
        class AddressThread implements Runnable {
            public void run() {
                cb.onAddressLocated(getAddress(latitude, longitude));
            }
        }
        (new Thread(new AddressThread())).start();
    }

    /**
     * Queries the map server and obtains the reverse geocoded address of the
     * specified location.
     * 
     * @param lat The latitude in degrees
     * @param lon The longitude in degrees
     * @return Returns the reverse geocoded address
     */
    public Address getAddress(double lat, double lon) {
        try {
            String resp = getResult(makeGeoURL(lat, lon));
            return new Address(resp);
        } catch (MalformedURLException mue) {
        } catch (IOException e) {
        }
        return null;
    }

    /**
     * Sends a request to the specified URL and obtains the result from the
     * sever.
     * 
     * @param url The URL to connect to
     * @return the server response
     * @throws IOException
     */
    private String getResult(URL url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.setDoOutput(true);
        InputStream is = conn.getInputStream();
        String result = toString(is);
        return result;
    }

    /**
     * Prepares the URL to connect to the reverse geocoding server from the
     * specified location coordinates.
     * 
     * @param lat latitude in degrees of the location to reverse geocode
     * @param lon longitude in degrees of the location to reverse geocode
     * @return URL The Geo URL created based on the given lat/lon
     * @throws MalformedURLException
     */
    private URL makeGeoURL(double lat, double lon) throws MalformedURLException {
        StringBuilder url = new StringBuilder();
        url.append(URL_GEO_STRING).append(lat).append(",").append(lon);
        return new URL(url.toString());
    }

    /**
     * Reads an InputStream and returns its contents as a String.
     * 
     * @param inputStream The InputStream to read from.
     * @return The contents of the InputStream as a String.
     */
    private static String toString(InputStream inputStream) throws IOException {
        StringBuilder outputBuilder = new StringBuilder();
        String string;
        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, ENCODING));
            while (null != (string = reader.readLine())) {
                outputBuilder.append(string).append('\n');
            }
        }
        return outputBuilder.toString();
    }

    /**
     * Replaces the short forms in the address by their longer forms, so that
     * TTS speaks the addresses properly
     * 
     * @param addr The address from which to replace short forms
     * @return the modified address string
     */
    public static String extendShorts(String addr) {
        addr = addr.replace("St,", "Street");
        addr = addr.replace("St.", "Street");
        addr = addr.replace("Rd", "Road");
        addr = addr.replace("Fwy", "Freeway");
        addr = addr.replace("Pkwy", "Parkway");
        addr = addr.replace("Blvd", "Boulevard");
        addr = addr.replace("Expy", "Expressway");
        addr = addr.replace("Ave", "Avenue");
        addr = addr.replace("Dr", "Drive");
        return addr;
    }
}
