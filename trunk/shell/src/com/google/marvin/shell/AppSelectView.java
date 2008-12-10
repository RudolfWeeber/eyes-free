/*
 * Copyright (C) 2008 Google Inc.
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

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Handles the user interaction with the Marvin Shell
 * 
 * @author clchen@google.com (Charles L. Chen)
 */
public class AppSelectView extends View {
  private static final long[] PATTERN = {0, 1, 40, 41};

  private final double left = 0;
  private final double upleft = Math.PI * .25;
  private final double up = Math.PI * .5;
  private final double upright = Math.PI * .75;
  private final double downright = -Math.PI * .75;
  private final double down = -Math.PI * .5;
  private final double downleft = -Math.PI * .25;
  private final double right = Math.PI;
  private final double rightWrap = -Math.PI;

  private HashMap<Integer, MenuItem> applications;

  private MarvinShell parent;

  private double downX;
  private double downY;
  public int currentValue;

  private Vibrator vibe;

  public AppSelectView(Context context) {
    super(context);
    parent = (MarvinShell) context;
    loadApps();
    setClickable(true);
    setFocusable(true);
    setFocusableInTouchMode(true);
    requestFocus();
    downX = 0;
    downY = 0;
    currentValue = -1;
    vibe = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
  }

  private void loadApps() {
    applications = new HashMap<Integer, MenuItem>();
    applications.put(4, new MenuItem("Text input", "com.google.marvin.slidetext", "SlideText"));
    applications.put(6, new MenuItem("Camera", "com.android.camera", "Camera"));
    applications.put(7,
        new MenuItem("Android Says", "com.google.marvin.androidsays", "AndroidSays"));
    applications.put(8, new MenuItem("Compass", "com.google.marvin.compass", "TalkingCompass"));
    applications.put(0, new MenuItem("Text input", "com.google.marvin.brailler", "Brailler"));
    applications.put(-2, new MenuItem("O C R", "com.android.ocr", "OcrActivity"));
    // applications.put(9, new MenuItem("Text input",
    // "com.google.marvin.brailler", "Brailler"));
  }


  @Override
  public boolean onTouchEvent(MotionEvent event) {
    int action = event.getAction();
    float x = event.getX();
    float y = event.getY();
    if (action == MotionEvent.ACTION_DOWN) {
      downX = x;
      downY = y;
      currentValue = -1;
      return true;
    } else if (action == MotionEvent.ACTION_UP) {
      int prevVal = currentValue;
      currentValue = evalMotion(x, y);
      // Do some correction if the user lifts up on deadspace
      if (currentValue == -1) {
        currentValue = prevVal;
      }
      // The user never got a number that wasn't deadspace,
      // so assume 5.
      if (currentValue == -1) {
        currentValue = 5;
      }
      invalidate();
      doAction();
      return true;
    } else {
      int prevVal = currentValue;
      currentValue = evalMotion(x, y);
      // Do nothing since we want a deadzone here;
      // restore the state to the previous value.
      if (currentValue == -1) {
        currentValue = prevVal;
        return true;
      }
      invalidate();
      if (prevVal != currentValue) {
        if (currentValue == 2) {
          announceTime();
        } else if (currentValue == 9) {
          parent.tts.speak("Weather", 0, null);
        } else if (currentValue == 3) {
          parent.tts.speak("Battery", 0, null);
        } else if (currentValue == 1) {
          parent.tts.speak("Airplane mode", 0, null);
        } else if (applications.containsKey(currentValue)) {
          MenuItem item = applications.get(currentValue);
          parent.tts.speak(item.title, 0, null);
        } else {
          parent.tts.speak("[tock]", 0, null);
        }
        vibe.vibrate(PATTERN, -1);
      }
      return true;
    }
  }


  private void doAction() {
    Context myContext;
    switch (currentValue) {
      case 2:
        announceDate();
        break;
      case 3:
        announceBattery();
        break;
      case 1:
        toggleAirplaneMode();
        break;
      case 9:
        announceWeather();
        break;
      default:
        if (applications.containsKey(currentValue)) {
          MenuItem item = applications.get(currentValue);
          startApp(item.packageName, item.className);
        }
    }
    currentValue = -1;
  }

  public int evalMotion(double x, double y) {
    float rTolerance = 25;
    double thetaTolerance = (Math.PI / 16);

    boolean movedFar = false;

    double r = Math.sqrt(((downX - x) * (downX - x)) + ((downY - y) * (downY - y)));

    if (r < rTolerance) {
      return 5;
    }
    if (r > 10 * rTolerance) {
      movedFar = true;
    }
    double theta = Math.atan2(downY - y, downX - x);

    if (Math.abs(theta - left) < thetaTolerance) {
      return 4;
    } else if (Math.abs(theta - upleft) < thetaTolerance) {
      return 1;
    } else if (Math.abs(theta - up) < thetaTolerance) {
      return 2;
    } else if (Math.abs(theta - upright) < thetaTolerance) {
      return 3;
    } else if (Math.abs(theta - downright) < thetaTolerance) {
      return 9;
    } else if (Math.abs(theta - down) < thetaTolerance) {
      if (movedFar) {
        return 0;
      } else {
        return 8;
      }
    } else if (Math.abs(theta - downleft) < thetaTolerance) {
      if (movedFar) {
        return -2;
      } else {
        return 7;
      }
    } else if ((theta > right - thetaTolerance) || (theta < rightWrap + thetaTolerance)) {
      return 6;
    }

    // Off by more than the threshold, so it doesn't count
    return -1;
  }

  @Override
  public void onDraw(Canvas canvas) {
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(Color.WHITE);
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(Typeface.DEFAULT_BOLD);

    String message = "Home";
    if (currentValue == 2) {
      message = "Time & Date";
    } else if (currentValue == 3) {
      message = "Battery";
    } else if (currentValue == 1) {
      message = "Airplane";
    } else if (currentValue == 9) {
      message = "Weather";
    } else if (applications.containsKey(currentValue)) {
      MenuItem item = applications.get(currentValue);
      message = item.title;
    }

    int x = getWidth() / 2;
    int y = getHeight() / 2;
    paint.setTextSize(50);
    y -= paint.ascent() / 2;
    canvas.drawText(message, x, y, paint);

    // Display airplane mode prominently
    if (airplaneModeEnabled()) {
      y = 20;
      y -= paint.ascent() / 2;
      canvas.drawText("Airplane", x, y, paint);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_MENU:
        String message = "Home";
        if (airplaneModeEnabled()) {
          message = "Airplane mode";
        }
        parent.tts.speak(message, 0, null);
        return true;
      case KeyEvent.KEYCODE_CALL:
        startApp("com.google.marvin.talkingdialer", "TalkingDialer");
        return true;
      case KeyEvent.KEYCODE_BACK:
        return true;
    }
    return false;
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        long duration = event.getEventTime() - event.getDownTime();
        if (duration > 3000) {
          startApp("com.android.launcher", "Launcher");
          return true;
        } else {
          return true;
        }
    }
    return false;
  }


  private void startApp(String packageName, String className) {
    try {
      int flags = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;
      Context myContext = parent.createPackageContext(packageName, flags);
      Class<?> appClass = myContext.getClassLoader().loadClass(packageName + "." + className);
      Intent intent = new Intent(myContext, appClass);
      parent.startActivity(intent);
    } catch (NameNotFoundException e) {
      parent.tts.speak("Application not installed.", 0, null);
      e.printStackTrace();
    } catch (ClassNotFoundException e) {
      parent.tts.speak("Application not installed.", 0, null);
      e.printStackTrace();
    }
  }

  private void announceBattery() {
    BroadcastReceiver battReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        context.unregisterReceiver(this);
        int rawlevel = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        String state = intent.getStringExtra("state");
        if (rawlevel >= 0 && scale > 0) {
          int batteryLevel = (rawlevel * 100) / scale;
          parent.tts.speak(Integer.toString(batteryLevel), 0, null);
          parent.tts.speak("%", 1, null);
        }
      }
    };
    IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    parent.registerReceiver(battReceiver, battFilter);
  }

  private void announceTime() {
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
    parent.tts.speak(Integer.toString(hour), 0, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak(Integer.toString(minutes), 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak(ampm, 1, null);
  }

  private void announceDate() {
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
    parent.tts.speak(monthStr, 0, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak("[slnc]", 1, null);
    parent.tts.speak(Integer.toString(day), 1, null);
  }

  private void announceWeather() {
    int version = 0;
    try {
      URLConnection cn;
      URL url = new URL("http://www.weather.gov/xml/current_obs/KSJC.rss");
      cn = url.openConnection();
      cn.connect();
      InputStream stream = cn.getInputStream();
      DocumentBuilder docBuild = DocumentBuilderFactory.newInstance().newDocumentBuilder();
      Document weatherRssDoc = docBuild.parse(stream);
      NodeList titles = weatherRssDoc.getElementsByTagName("title");
      NodeList descriptions = weatherRssDoc.getElementsByTagName("description");
      String title = titles.item(2).getFirstChild().getNodeValue();
      String description = descriptions.item(1).getChildNodes().item(2).getNodeValue();
      parent.tts.speak(title, 0, null);
      parent.tts.speak(description, 1, null);
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

  private void toggleAirplaneMode() {
    boolean setAirPlaneMode = !airplaneModeEnabled();
    if (!setAirPlaneMode) {
      parent.tts.speak("disabled", 1, null);
    } else {
      parent.tts.speak("enabled", 1, null);
    }
    Settings.System.putInt(parent.getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
        setAirPlaneMode ? 1 : 0);
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra("state", setAirPlaneMode);
    parent.sendBroadcast(intent);
  }

  public boolean airplaneModeEnabled() {
    ContentResolver cr = getContext().getContentResolver();
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

}
