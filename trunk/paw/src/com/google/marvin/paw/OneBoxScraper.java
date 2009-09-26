package com.google.marvin.paw;

import org.htmlparser.parserapplications.StringExtractor;
import org.htmlparser.util.ParserException;

import android.util.Log;

public class OneBoxScraper {

	public static String processGoogleResults(String query) {
		String processedResult = "";
		try {
			String URL = "http://www.google.com/m?q=" + query;
			StringExtractor se = new StringExtractor(URL);
			String results = se.extractStrings(true);
			
			Log.e("PAW Debug", results);

			// Check for known one box types
			if (results.indexOf("Weather for") == 0) {
				int indexOfHumidity = results.indexOf("Humidity");
				if (indexOfHumidity != -1) {
					int endIndex = results.indexOf("%", indexOfHumidity);
					if (endIndex != -1) {
						processedResult = results.substring(0, endIndex + 1);
					}
				}
			}
		} catch (ParserException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return processedResult;
	}
}
