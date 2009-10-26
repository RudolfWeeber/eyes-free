/*
 * Copyright (C) 2009 Google Inc.
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
package com.google.marvin.translate;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * Connects to the AJAX-accessible Google Translate backend for online
 * translation.
 * 
 * @author alanv@google.com (Alan Viverette)
 */
public class GoogleTranslate extends Thread {
  private static final String TAG = "GoogleTranslate";

  private static final String TRANSLATE_URL =
      "http://ajax.googleapis.com/ajax/services/language/translate";

  private static final String QUERY_PARAM = "q";
  private static final String VERSION_PARAM = "v";
  private static final String LANGPAIR_PARAM = "langpair";
  private static final String LANGPAIR_SEPARATOR = "%7C";
  private static final String VERSION = "1.0";

  private static final int TIMEOUT = 5000;

  private String mStrUrl;
  private TranslationListener mCallback;

  public interface TranslationListener {
    public void onComplete(String result);

    public void onError(int code, String error);
  }

  /**
   * Translates a query from a source language to a destination language in a
   * separate thread. Takes a TranslationListener that returns the translation
   * result (or error status).
   * 
   * @param query The text to translate.
   * @param source The source language code.
   * @param target The target language code.
   * @param onComplete The translation completion callback.
   */
  public GoogleTranslate(String query, String source, String target, TranslationListener onComplete) {

    query = URLEncoder.encode(query);
    source = URLEncoder.encode(source);
    target = URLEncoder.encode(target);

    String strUrl = TRANSLATE_URL + "?";
    strUrl += QUERY_PARAM + "=" + query + "&";
    strUrl += VERSION_PARAM + "=" + VERSION + "&";
    strUrl += LANGPAIR_PARAM + "=" + source + LANGPAIR_SEPARATOR + target;

    mStrUrl = strUrl;
    mCallback = onComplete;
  }

  @Override
  public void run() {
    if (mCallback == null) {
      return;
    }

    try {
      Log.i(TAG, "Connecting to " + mStrUrl);
      URL url = new URL(mStrUrl);

      Log.i(TAG, "Opening connection with " + TIMEOUT + "ms timeout");
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setDoOutput(true);
      conn.setReadTimeout(TIMEOUT);
      conn.connect();

      Log.i(TAG, "Reading content");
      InputStreamReader reader = new InputStreamReader(conn.getInputStream());
      BufferedReader buffer = new BufferedReader(reader);
      String content = "";
      String line;

      while ((line = buffer.readLine()) != null) {
        content += line;
      }

      Log.i(TAG, "Received content:\n" + content);

      conn.disconnect();

      Log.i(TAG, "Interpreting JSON content");
      JSONObject json = new JSONObject(content.toString());
      int responseStatus = json.getInt("responseStatus");

      if (responseStatus == 200) {
        JSONObject responseData = json.getJSONObject("responseData");
        String translatedText = responseData.getString("translatedText");

        mCallback.onComplete(translatedText);
      } else {
        String responseDetails = json.getString("responseDetails");

        mCallback.onError(responseStatus, responseDetails);
      }
    } catch (IOException e) {
      mCallback.onError(0, e.toString());
    } catch (JSONException e) {
      mCallback.onError(0, e.toString());
    }

    mCallback = null;
  }
}
