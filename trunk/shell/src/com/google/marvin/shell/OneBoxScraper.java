/*
 * Copyright (C) 2010 Google Inc.
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

package com.google.marvin.shell;

import org.htmlparser.parserapplications.StringExtractor;
import org.htmlparser.util.ParserException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for scraping the one box and finding the result
 *
 * @author clchen@google.com (Charles L. Chen)
 */
final public class OneBoxScraper {

    private OneBoxScraper() {
    }

    public static String processGoogleResults(String query, String baseUrl) {
        String processedResult = "";
        try {
            String url = baseUrl + URLEncoder.encode(query, "UTF-8");
            StringExtractor se = new StringExtractor(url);
            String results = se.extractStrings(true);

            // Uncomment this line to see the raw dump;
            // very useful when trying to come up with scraping rules
            // Log.e("OneBoxScraper Debug", results);

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
                int indexOfFlightTracker = results.indexOf(
                        "www.flightstats.com", indexOfTrackStatus);
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
            // This is tricky, the market line could be the first or the second
            // line
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
            if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
                int firstLineBreak = results.indexOf("\n");
                String firstLine = results.substring(0, firstLineBreak);

                Pattern vsScorePattern = Pattern.compile("[a-zA-Z ]+[0-9]+ - [a-zA-Z ]+[0-9]+");
                Pattern recordScorePattern = Pattern.compile("[a-zA-Z ]+ \\([0-9]+-[0-9]+\\)");
                Matcher vsScoreMatcher = vsScorePattern.matcher(firstLine);
                Matcher recordScoreMatcher = recordScorePattern.matcher(firstLine);

                if (vsScoreMatcher.find()) {
                    processedResult = vsScoreMatcher.group();
                } else if (recordScoreMatcher.find()) {
                    processedResult = recordScoreMatcher.group();
                }
            }
            // World cup
            if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
                int firstLineBreak = results.indexOf("\n");
                String firstLine = results.substring(0, firstLineBreak);
                int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
                String secondLine = results.substring(firstLineBreak + 1, secondLineBreak);
                int thirdLineBreak = results.indexOf("\n", secondLineBreak + 1);
                String thirdLine = results.substring(secondLineBreak + 1, thirdLineBreak);
                int fourthLineBreak = results.indexOf("\n", thirdLineBreak + 1);
                String fourthLine = results.substring(thirdLineBreak + 1, fourthLineBreak);
                if (firstLine.contains("2010 FIFA World Cup(tm)")
                        && fourthLine.equals("Upcoming matches:")) {
                    processedResult = secondLine + "\n" + thirdLine;
                }
            }

            // Special case for eyes-free shell: Speak the first location result
            if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
                int firstLineBreak = results.indexOf("\n");
                String firstLine = results.substring(0, firstLineBreak);
                String localResultsStr = "Local results ";
                if (firstLine.indexOf(localResultsStr) == 0) {
                    int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
                    int thirdLineBreak = results.indexOf("\n", secondLineBreak + 1);
                    int fourthLineBreak = results.indexOf("\n", thirdLineBreak + 1);
                    int fifthLineBreak = results.indexOf("\n", fourthLineBreak + 1);

                    // <http://www.google.com/m?defaultloc=Mountain+View%2C+CA+94043&amp;site=local&amp;q=costco+94043&amp;latlng=15926316227166107848&amp;mp=1&amp;zp&amp;source=m&amp;ct=res&amp;oi=local_result&amp;sa=X&amp;ei=Ll3CSvGMNZCNtge0z-83&amp;cd=1&amp;resnum=1>Costco
                    String thirdLine = results.substring(secondLineBreak + 1, thirdLineBreak);
                    // 1000 N Rengstorff Ave, Mountain View, C.A. 94043
                    String fourthLine = results.substring(thirdLineBreak + 1, fourthLineBreak);
                    // <wtai://wp/mc;6509881841>(650) 9881841 - Ratings: 3/5
                    String fifthLine = results.substring(fourthLineBreak + 1, fifthLineBreak);

                    processedResult = thirdLine.substring(thirdLine.indexOf(">") + 1) + "\n";
                    processedResult = processedResult + fourthLine + "\n";
                    processedResult = processedResult
                            + fifthLine.substring(fifthLine.indexOf(">") + 1);
                }
            }
            // Special case for eyes-free shell: Speak the first location result
            if ((processedResult.length() < 1) && (results.indexOf("\n") != -1)) {
                int firstLineBreak = results.indexOf("\n");
                int secondLineBreak = results.indexOf("\n", firstLineBreak + 1);
                int thirdLineBreak = results.indexOf("\n", secondLineBreak + 1);

                // <http://www.google.com/m?defaultloc=Mountain+View%2C+CA+94043&amp;site=local&amp;q=costco+94043&amp;latlng=15926316227166107848&amp;mp=1&amp;zp&amp;source=m&amp;ct=res&amp;oi=local_result&amp;sa=X&amp;ei=Ll3CSvGMNZCNtge0z-83&amp;cd=1&amp;resnum=1>Costco
                String firstLine = results.substring(0, firstLineBreak);
                // 1000 N Rengstorff Ave, Mountain View, C.A. 94043
                String secondLine = results.substring(firstLineBreak + 1, secondLineBreak);
                // <wtai://wp/mc;6509881841>(650) 9881841 - Ratings: 3/5
                String thirdLine = results.substring(secondLineBreak + 1, thirdLineBreak);

                Pattern addressPattern = Pattern.compile(
                        "[0-9a-zA-Z ]+, [a-zA-Z ]+, [a-zA-Z. ]+ [0-9]+");
                Matcher addressMatcher = addressPattern.matcher(secondLine);
                Pattern phonePattern = Pattern.compile("\\([0-9][0-9][0-9]\\) [0-9-]+");
                Matcher phoneMatcher = phonePattern.matcher(thirdLine);

                if (addressMatcher.find() && phoneMatcher.find()) {
                    processedResult = firstLine.substring(firstLine.indexOf(">") + 1) + "\n";
                    processedResult = processedResult + secondLine + "\n";
                    processedResult = processedResult
                            + thirdLine.substring(thirdLine.indexOf(">") + 1);
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
                    processedResult = "PAW_YOUTUBE:" + firstLine.substring(
                            firstLine.indexOf("<") + 1, firstLine.indexOf(">"));
                }
            }
            // Try to read the first result if there is no preceding link
            // since this will usually be a onebox of some sort.
            if ((processedResult.length() < 1) && (results.indexOf("<") != -1)) {
                int endIndex = results.indexOf("<", 0);
                if (endIndex != -1) {
                    processedResult = results.substring(0, endIndex + 1);
                }
                // If this is the weather box, try to trim it down by cutting it
                // off at humidity
                if ((processedResult.length() > 1) && (processedResult.indexOf("%") != -1)) {
                    processedResult = processedResult.substring(
                            0, processedResult.indexOf("%") + 1);
                }
            }
            // Log.e("processedResultLength", processedResult.length() + "");
        } catch (ParserException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return processedResult;
    }
}
