package com.google.marvin.shell;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;

import com.google.tts.TTS;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

public class AuditoryWidgets {
  private TTS tts;
  private MarvinShell parent;
  
  public AuditoryWidgets(TTS theTts, MarvinShell shell){
    tts = theTts;
    parent = shell;
  }
  
  public void announceBattery(){
    BroadcastReceiver battReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        context.unregisterReceiver(this);
        int rawlevel = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        String state = intent.getStringExtra("state");
        if (rawlevel >= 0 && scale > 0) {
          int batteryLevel = (rawlevel * 100) / scale;
          tts.speak(Integer.toString(batteryLevel), 0, null);
          tts.speak("%", 1, null);
        }
      }
    };
    IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    parent.registerReceiver(battReceiver, battFilter);
  }
  
  public void announceDate(){
    GregorianCalendar cal = new GregorianCalendar();
    int month = cal.get(Calendar.MONTH);
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int year = cal.get(Calendar.YEAR);
    String monthStr = "";
    switch (month) {
      case Calendar.JANUARY:
        monthStr = "January";
        break;
      case Calendar.FEBRUARY:
        monthStr = "February";
        break;
      case Calendar.MARCH:
        monthStr = "March";
        break;
      case Calendar.APRIL:
        monthStr = "April";
        break;
      case Calendar.MAY:
        monthStr = "May";
        break;
      case Calendar.JUNE:
        monthStr = "June";
        break;
      case Calendar.JULY:
        monthStr = "July";
        break;
      case Calendar.AUGUST:
        monthStr = "August";
        break;
      case Calendar.SEPTEMBER:
        monthStr = "September";
        break;
      case Calendar.OCTOBER:
        monthStr = "October";
        break;
      case Calendar.NOVEMBER:
        monthStr = "November";
        break;
      case Calendar.DECEMBER:
        monthStr = "December";
        break;
    }
    tts.speak(monthStr, 0, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak(Integer.toString(day), 1, null);
  }
  
  public void announceTime(){
    GregorianCalendar cal = new GregorianCalendar();
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int minutes = cal.get(Calendar.MINUTE);
    String ampm = "";
    if (hour == 0) {
      ampm = "midnight";
      hour = 12;
    } else if (hour == 12) {
      ampm = "noon";
    } else if (hour > 12) {
      hour = hour - 12;
      ampm = "PM";
    } else {
      ampm = "AM";
    }
    tts.speak(Integer.toString(hour), 0, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak(Integer.toString(minutes), 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak("[slnc]", 1, null);
    tts.speak(ampm, 1, null);
  }
  
  public void toggleAirplaneMode() {
    boolean setAirPlaneMode = !airplaneModeEnabled();
    if (!setAirPlaneMode) {
      tts.speak("disabled", 1, null);
    } else {
      tts.speak("enabled", 1, null);
    }
    Settings.System.putInt(parent.getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
        setAirPlaneMode ? 1 : 0);
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra("state", setAirPlaneMode);
    parent.sendBroadcast(intent);
  }
  
  public boolean airplaneModeEnabled() {
    ContentResolver cr = parent.getContentResolver();
    int x;
    try {
      x = Settings.System.getInt(cr, Settings.System.AIRPLANE_MODE_ON);
      if (x == 1) {
        return true;
      }
    } catch (SettingNotFoundException e) {
      // This setting is always there as it is part of the Android framework;
      // therefore, this exception is not reachable and nothing special needs
      // to be done here.
      e.printStackTrace();
    }
    return false;
  }
  
  public void announceWeather() {
    int version = 0;
    try {
      URLConnection cn;
      URL url = new URL("http://www.weather.gov/xml/current_obs/KPAO.rss");
      cn = url.openConnection();
      cn.connect();
      InputStream stream = cn.getInputStream();
      DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document weatherRssDoc = docBuild.parse(stream);
      NodeList titles = weatherRssDoc.getElementsByTagName("title");
      NodeList descriptions = weatherRssDoc.getElementsByTagName("description");
      String title = titles.item(2).getFirstChild().getNodeValue();
      String description = descriptions.item(1).getChildNodes().item(2).getNodeValue();
      tts.speak(title, 0, null);
      tts.speak(description, 1, null);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (SAXException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (ParserConfigurationException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (FactoryConfigurationError e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  public void callVoiceMail(){
    Uri phoneNumberURI = Uri.parse("tel:" + Uri.encode(parent.voiceMailNumber));
    Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberURI);
    parent.startActivity(intent);
  }
  
}
