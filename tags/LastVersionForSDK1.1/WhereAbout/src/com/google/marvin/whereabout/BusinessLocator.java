package com.google.marvin.whereabout;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.HasAttributeFilter;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;

import android.util.Log;

public class BusinessLocator {
  // URL for obtaining businesses
  private static final String URL_BUSINESS =
    "http://maps.google.com/maps?q=*%20loc:";
  
  /** Private Constructor for this utility class */
  private BusinessLocator() {
  }
  
  public static String[] getBusinesses(double lat, double lon) {
    String[] bsList = null;
    try {
      String html = HttpUtil.getResult(makeBusinessURL(lat, lon));
      Parser p = new Parser();
      p.setInputHTML(html);
      
      NodeList buss = p.extractAllNodesThatMatch(
          new HasAttributeFilter("class", "name lname"));
      Log.d("Business", "Businesses: " + buss.size());
      
      int len = buss.size() / 2;
      bsList = new String[len];
      Business bs = new Business();
      for (int i = 0; i < len; i++) {
        Node n = buss.elementAt(i);
        String tmp = n.getFirstChild().getFirstChild().getText();
        tmp = tmp.substring(tmp.indexOf("latlng=") + 7);
        StringTokenizer st = new StringTokenizer(tmp, ",");
        bs.lat = Double.parseDouble(st.nextToken()) / 1000000;
        bs.lon = Double.parseDouble(st.nextToken()) / 1000000;
        bs.title = n.getFirstChild().getFirstChild().getFirstChild()
            .getFirstChild().getText();
        Log.d("Business", bs.title);
        NodeList tmpList = n.getNextSibling().getChildren();
        int tmpListLen = tmpList.size();
        for (int k = 0; k < tmpListLen; k++) {
          Node infoNode = tmpList.elementAt(k);
          if (infoNode.getText().indexOf("sxaddr") >= 0) {
            NodeList aNodes = infoNode.getChildren();
            int aNodesLen = aNodes.size();
            String address = "";
            for (int j = 0; j < aNodesLen; j++) {
              Node addrSubNode = aNodes.elementAt(j);
              if (addrSubNode.getText().indexOf("span") >= 0) {
                address += addrSubNode.getFirstChild().getText() + " ";
              }
              bs.address = address;
            }
          } else if (infoNode.getFirstChild() != null &&
                     infoNode.getText().indexOf("span") >= 0) {
            Node tmpNode = infoNode.getFirstChild().getFirstChild(); 
            if (tmpNode != null) {
              bs.tel = tmpNode.getText();
            } else {
              bs.dir = infoNode.getFirstChild().getText();
            }
          }
        }
        bsList[i] = bs.title + "$" + bs.address + "$" + bs.tel + "$" +
            bs.lat + "$" + bs.lon + "$" + bs.dir;
        Log.d("Business", bsList[i]);
      }
    } catch (IOException e) {
      Log.d("Business", "Error reading from Map server: " + e.getMessage());
      Log.d("Business", "Trace: " + Log.getStackTraceString(e.fillInStackTrace()));
    } catch (ParserException pce) {
      Log.d("Business", "Could not parse HTML: " + pce.toString());
    }
    return bsList;
  }
  
  /**
   * Prepares the URL to connect to the Google Maps server to obtain list of
   * businesses around the specified lat and long.
   * @param lat latitude in degrees of the location to reverse geocode
   * @param lon longitude in degrees of the location to reverse geocode
   * @return
   * @throws MalformedURLException
   */
  private static URL makeBusinessURL(double lat, double lon)
      throws MalformedURLException {
    StringBuilder url = new StringBuilder();    
    url.append(URL_BUSINESS).append(lat).append(",").append(lon);
    return new URL(url.toString());
  }
}
