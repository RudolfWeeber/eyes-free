package com.google.marvin.whereabout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.util.Log;

public class HttpUtil {
  
  private static final String ENCODING = "UTF-8";
  
  /**
   * Sends a request to the specified URL and obtains the result from
   * the sever. 
   * @param url The URL to connect to
   * @return the server response
   * @throws IOException
   */
  public static String getResult(URL url) throws IOException {
    return toString(getResultInputStream(url));
  }

  public static InputStream getResultInputStream(URL url) throws IOException {
    Log.d("Locator", url.toString());
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    Log.d("Locator", "After open connection");
    conn.setDoInput(true);
    conn.setDoOutput(true);
    return conn.getInputStream();
  }
  
  /**
   * Reads an InputStream and returns its contents as a String.
   * @param inputStream The InputStream to read from.
   * @return The contents of the InputStream as a String.
   * @throws Exception
   */
  private static String toString(InputStream inputStream) throws IOException {
    StringBuilder outputBuilder = new StringBuilder();
    String string;
    if (inputStream != null) {
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(inputStream, ENCODING));
      while (null != (string = reader.readLine())) {
          outputBuilder.append(string).append('\n');
      }
    }
    return outputBuilder.toString();
  }  
}
