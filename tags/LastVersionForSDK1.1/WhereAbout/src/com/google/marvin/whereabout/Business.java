package com.google.marvin.whereabout;

public class Business {
  public String title;
  public String address;
  public double lat, lon;
  public String tel;
  public double dist;
  public String dir;
  
  public Business() {}
  
  public Business(String title, String address, String tel, double lat,
      double lon, String dir) {
   this.title = title;
   this.address = address;
   this.lat = lat; this.lon = lon;
   this.tel = tel;
   this.dir = dir;
  }
}
