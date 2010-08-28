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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;

import android.content.Context;
import android.util.Log;

/**
 * This class does the Wifi based localization with the computation happening
 * remotely on the server.
 * 
 * @author akhil@google.com (Akhil Widwans)
 */
public class RemoteWifiLocalizer extends WifiLocalizer {

    public RemoteWifiLocalizer(Context ct, WifiScanner scanner) {
        super(ct, scanner);
    }

    /**
     * Computes a match score between the previously collected Wifi signature
     * and the known signatures.
     */
    @Override
    protected void computeMatch(String expectedLocation) {
        try {
            String matchLocationName = getMatchFromServer();
            return;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private String getMatchFromServer() throws Exception {
        Log.e("DEBUG SERVER MATCH", "NOW SERVER MATCH");
        String urlString = "http://whereaboutserver.appspot.com/searchLocation";
        String wifi = getWifiStringFromTables();
        Log.e("DEBUG SERVER MATCH", "wifi = " + wifi);
        try {
            // Instantiate an HttpClient
            HttpClient client = new DefaultHttpClient();
            Log.e("my debug", "got client");
            HttpPost httppost = new HttpPost(urlString);
            Log.e("DEBUG SERVER MATCH", "got httppost");
            NameValuePair[] params = new NameValuePair[1];
            Log.e("DEBUG SERVER MATCH", "created params");
            params[0] = new BasicNameValuePair("wifi", wifi);
            Log.e("DEBUG SERVER MATCH", "set wifi");
            httppost.setEntity(new UrlEncodedFormEntity(Arrays.asList(params)));
            Log.e("DEBUG SERVER MATCH", "set params as entity");
            HttpResponse response = client.execute(httppost);
            Log.e("POST RESPONSE ============>", "");
            String respStr = extractResponse(response);
            String respSub = respStr.substring(respStr.indexOf("Best Match Location"));
            Log.e("RESPONSE SUBSTRING = ", respSub);
            String serverMatchLocation = respSub.substring(respSub.indexOf(":"), respSub
                    .indexOf("<BR>"));
            Log.e("SERVER MATCH LOCATION = ", serverMatchLocation);
            return serverMatchLocation;
        } catch (MalformedURLException e) {
            throw e;
        } catch (IOException e) {
            throw e;
        }
    }

    private String getWifiStringFromTables() {
        Set<String> keys = wifiStrengthTable.keySet();
        String result = "";
        for (String key : keys) {
            String line = key + " " + wifiStrengthTable.get(key) + " " + wifiCountTable.get(key)
                    + "\n";
            result += line;
        }
        return result;
    }

    private String extractResponse(HttpResponse response) throws IOException {
        String responseStr = null;
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            InputStream instream = entity.getContent();
            byte[] tmp = new byte[20480];
            int bytesRead = instream.read(tmp);
            Log.e("DEBUG SERVER MATCH Bytes read = ", String.valueOf(bytesRead));
            responseStr = new String(tmp);
            Log.e("DEBUG SERVER MATCH Response = ", responseStr);
        }
        return responseStr;
    }

    @Override
    protected double compareScans(HashMap<String, Double> expected,
            HashMap<String, Double> actual) {
        // TODO Auto-generated method stub
        return 0;
    }
}
