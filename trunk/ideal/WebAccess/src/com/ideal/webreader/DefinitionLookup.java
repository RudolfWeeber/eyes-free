/*
 * Copyright (C) 2010 The IDEAL Group
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

package com.ideal.webreader;

import com.ideal.webaccess.StringUtils;

import android.speech.tts.TextToSpeech;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Asynchronously fetches the definition for the given phrase and speaks it.
 */
public class DefinitionLookup {

    public static void lookupAndSpeak(final String phrase, final TextToSpeech tts) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    URL sourceURL = new URL("http://www.google.com/search?hl=en&q=define:+"
                            + URLEncoder.encode(phrase, "UTF-8"));
                    // obtain the connection
                    HttpURLConnection sourceConnection = (HttpURLConnection) sourceURL
                            .openConnection();
                    // add parameters to the connection
                    HttpURLConnection.setFollowRedirects(true);
                    // allow both GZip and Deflate (ZLib) encodings
                    sourceConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                    sourceConnection
                            .setRequestProperty("User-Agent", "Mozilla/5.0 ( compatible ) ");
                    sourceConnection.setRequestProperty("Accept", "[star]/[star]");

                    // establish connection, get response headers
                    sourceConnection.connect();

                    // obtain the encoding returned by the server
                    String encoding = sourceConnection.getContentEncoding();

                    InputStream stream = null;
                    // create the appropriate stream wrapper based on the
                    // encoding type
                    if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                        stream = new GZIPInputStream(sourceConnection.getInputStream());
                    } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                        stream = new InflaterInputStream(sourceConnection.getInputStream(),
                                new Inflater(true));
                    } else {
                        stream = sourceConnection.getInputStream();
                    }

                    StringBuffer htmlContent = new StringBuffer();
                    byte buf[] = new byte[128000];
                    do {
                        int numread = stream.read(buf);
                        if (numread <= 0) {
                            break;
                        } else {
                            htmlContent.append(new String(buf, 0, numread));
                        }
                    } while (true);

                    String startMarkerStr = "<ul type=\"disc\" class=std><li>";
                    String endMarkerStr = "<br>";
                    int startIndex = htmlContent.indexOf(startMarkerStr);
                    if (startIndex == -1) {
                        if (tts != null) {
                            tts.speak("Definition not found.", 2, null);
                        }
                        return;
                    }
                    startIndex = startIndex + startMarkerStr.length();
                    int endIndex = htmlContent.indexOf(endMarkerStr, startIndex);
                    if (endIndex == -1) {
                        if (tts != null) {
                            tts.speak("Definition not found.", 2, null);
                        }
                        return;
                    }
                    String definition = htmlContent.substring(startIndex, endIndex);
                    definition = StringUtils.unescapeHTML(definition);
                    definition = definition.replaceAll("<li>", " ");
                    if (tts != null) {
                        tts.speak("Definition for " + phrase + ": " + definition, 2, null);
                    }
                } catch (MalformedURLException e) {

                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        };

        new Thread(runnable).start();
    }
}
