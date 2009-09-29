package com.google.marvin.paw;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            //Log.e("PAW Debug", "Weather: " + processedResult);
          }
        }
      }
      // Flight tracker
      if ((processedResult.length() < 1) && (results.indexOf("Track status of ") != -1)) {
        int indexOfTrackStatus = results.indexOf("Track status of ");
        int indexOfFlightTracker = results.indexOf("www.flightstats.com", indexOfTrackStatus);
        if (indexOfFlightTracker != -1) {
          processedResult = results.substring(indexOfTrackStatus, indexOfFlightTracker);
          //Log.e("PAW Debug", "Flight tracker: " + processedResult);
        }
      }
      // Calculator
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        String firstLine = results.substring(0, results.indexOf("\n"));
        if (firstLine.indexOf(" = ") != -1) {
          processedResult = firstLine;
          //Log.e("PAW Debug", "Calculator: " + processedResult);
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
          //Log.e("PAW Debug", "Finance: " + processedResult);
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
          //Log.e("PAW Debug", "Finance: " + processedResult);
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
          //Log.e("PAW Debug", "Dictionary: " + processedResult);
        }
      }
      // Time
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);
        if ((firstLine.indexOf(":") != -1)
            && ((firstLine.indexOf("am ") != -1) || (firstLine.indexOf("pm ") != -1))) {
          processedResult = firstLine;
          //Log.e("PAW Debug", "Time: " + processedResult);
        }
      }
      // Sports
      if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
        int firstLineBreak = results.indexOf("\n");
        String firstLine = results.substring(0, firstLineBreak);

        Pattern vsScorePattern = Pattern.compile("[a-zA-Z ]+[0-9]+ - [a-zA-Z ]+[0-9]+");
        Pattern recordScorePattern = Pattern.compile("[a-zA-Z ]+ \\([0-9]+-[0-9]+\\)");
        Matcher vsScoreMatcher = vsScorePattern.matcher(firstLine);
        Matcher recordScoreMatcher = recordScorePattern.matcher(firstLine);
        
        if (vsScoreMatcher.find()) {          
          processedResult = vsScoreMatcher.group();
          //Log.e("PAW Debug", "Sports: " + processedResult);
        } else if (recordScoreMatcher.find()) {          
          processedResult = recordScoreMatcher.group();
          //Log.e("PAW Debug", "Sports: " + processedResult);
        }
      }
     
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
