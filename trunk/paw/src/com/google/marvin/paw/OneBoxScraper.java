package com.google.marvin.paw;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.htmlparser.parserapplications.StringExtractor;
import org.htmlparser.util.ParserException;

import android.util.Log;

public class OneBoxScraper {

  public static String processGoogleResults(String query) {
    String processedResult = "";
    try {
      String URL = "http://www.google.com/m?q=" + URLEncoder.encode(query, "UTF-8");
      StringExtractor se = new StringExtractor(URL);
      String results = se.extractStrings(true);

      //Uncomment this line to see the raw dump;
      //very useful when trying to come up with scraping rules
      //Log.e("PAW Debug", results);

      /* Check for known one box types */
      // Weather
      if ((processedResult.length() < 1) && (results.indexOf("Weather for") == 0)) {
        int indexOfHumidity = results.indexOf("Humidity");
        if (indexOfHumidity != -1) {
          int endIndex = results.indexOf("%", indexOfHumidity);
          if (endIndex != -1) {
            processedResult = results.substring(0, endIndex + 1);
          }
        }
      }
      // Flight tracker
      if ((processedResult.length() < 1) && (results.indexOf("Track status of ") != -1)) {
        int indexOfTrackStatus = results.indexOf("Track status of ");
        int indexOfFlightTracker = results.indexOf("www.flightstats.com", indexOfTrackStatus);
        if (indexOfFlightTracker != -1) {
          processedResult = results.substring(indexOfTrackStatus, indexOfFlightTracker);
        }
      }
      // Calculator
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        String firstLine = results.substring(0, results.indexOf("\n"));
        if (firstLine.indexOf(" = ") != -1) {
          processedResult = firstLine;
        }
      }
      // Finance
      // This is tricky, the market line could be the first or the second line
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if ((firstLine.indexOf(" NASDAQ") != -1) || (firstLine.indexOf(" NYSE") != -1)) {
          // Copy the Symbol Market line
          if (firstLine.indexOf(">") != -1) {
            processedResult = firstLine.substring(firstLine.indexOf(">") + 1) + "\n";
          }
          int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
          String secondLine = results.substring(firstLineBreak + 1, secondLineBreak);
          secondLine = secondLine.replace(" +", " Up by ").replace(" -", " Down by ");
          processedResult = processedResult + secondLine + "\n";
          int thirdLineBreak = results.indexOf("\n", secondLineBreak + 1);
          String thirdLine = results.substring(secondLineBreak + 1, thirdLineBreak);
          processedResult = processedResult + thirdLine;
        }
      }
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int zerothLineBreak = results.indexOf("\n");
        int firstLineBreak = results.indexOf("\n", zerothLineBreak + 1);
        String firstLine = results.substring(zerothLineBreak + 1, firstLineBreak);
        if ((firstLine.indexOf(" NASDAQ") != -1) || (firstLine.indexOf(" NYSE") != -1)) {
          // Copy the Symbol Market line
          if (firstLine.indexOf(">") != -1) {
            processedResult = firstLine.substring(firstLine.indexOf(">") + 1) + "\n";
          }
          int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
          String secondLine = results.substring(firstLineBreak + 1, secondLineBreak);
          secondLine = secondLine.replace(" +", " Up by ").replace(" -", " Down by ");
          processedResult = processedResult + secondLine + "\n";
          int thirdLineBreak = results.indexOf("\n", secondLineBreak + 1);
          String thirdLine = results.substring(secondLineBreak + 1, thirdLineBreak);
          processedResult = processedResult + thirdLine;
        }
      }
      // Dictionary
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if (firstLine.indexOf("Web definitions for ") != -1) {
          if (firstLine.indexOf(">") != -1) {
            processedResult = firstLine.substring(firstLine.indexOf(">") + 1) + "\n";
          }
          int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
          String secondLine = results.substring(firstLineBreak + 1, secondLineBreak);
          processedResult = processedResult + secondLine + "\n";
        }
      }
      // Time
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if ((firstLine.indexOf(":") != -1)
            && ((firstLine.indexOf("am ") != -1) || (firstLine.indexOf("pm ") != -1))) {
          processedResult = firstLine;
        }
      }
      // Sports
      /*
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if ((firstLine.indexOf("News results for") == -1)
            && (firstLine.toLowerCase().indexOf(query.toLowerCase()) != -1)) {
          
          processedResult = firstLine;
        }
      }
      */
      /* The following will result in a special action that is not speech */
      // Local search
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        String localResultsStr = "Local results ";
        if (firstLine.indexOf(localResultsStr) == 0) {
          processedResult = "PAW_MAPS:" + URLEncoder.encode(query, "UTF-8");
        }
      }
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int zerothLineBreak = results.indexOf("\n");
        int firstLineBreak = results.indexOf("\n", zerothLineBreak + 1);
        String firstLine = results.substring(zerothLineBreak + 1, firstLineBreak);
        String localResultsStr = "Local results ";
        if (firstLine.indexOf(localResultsStr) == 0) {
          processedResult = "PAW_MAPS:" + URLEncoder.encode(query, "UTF-8");
        }
      }
      // YouTube
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if (firstLine.indexOf("<http://www.youtube.com/watch?") == 0) {
          processedResult =
              "PAW_YOUTUBE:"
                  + firstLine.substring(firstLine.indexOf("<") + 1, firstLine.indexOf(">"));
        }
      }

    } catch (ParserException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    return processedResult;
  }
}
