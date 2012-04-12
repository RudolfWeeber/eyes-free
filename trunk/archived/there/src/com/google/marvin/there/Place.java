package com.google.marvin.there;

public class Place {
  public String name;
  public String desc;
  public String lat;
  public String lon;
  
  public Place(String placeName, String placeDesc, String placeLat, String placeLon){
    name = placeName;
    desc = placeDesc;
    lat = placeLat;
    lon = placeLon;
  }
}
