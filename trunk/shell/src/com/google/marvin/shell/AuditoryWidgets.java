package com.google.marvin.shell;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.provider.Settings.System;

import com.google.tts.TTS;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

// Most of the logic for determining strength levels is based on the code here:
// http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=services/java/com/android/server/status/StatusBarPolicy.java

public class AuditoryWidgets {
  private TTS tts;
  private MarvinShell parent;
  private final ReentrantLock speakingTimeLock = new ReentrantLock();
  private Guide guide;

  private int voiceSignalStrength;

  public AuditoryWidgets(TTS theTts, MarvinShell shell) {
    tts = theTts;
    parent = shell;
    guide = new Guide(parent);
    voiceSignalStrength = 0;
    TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(new PhoneStateListener() {
      public void onSignalStrengthChanged(int asu) {
        if (asu <= 0 || asu == 99)
          voiceSignalStrength = 0;
        else if (asu >= 16)
          voiceSignalStrength = 4;
        else if (asu >= 8)
          voiceSignalStrength = 3;
        else if (asu >= 4)
          voiceSignalStrength = 2;
        else
          voiceSignalStrength = 1;
      }
    }, PhoneStateListener.LISTEN_SIGNAL_STRENGTH);
  }

  public void shutdown() {
    guide.shutdown();
    TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
    tm.listen(new PhoneStateListener() {}, PhoneStateListener.LISTEN_NONE);
  }

  private void speakDataNetworkInfo() {
    String info = parent.getString(R.string.no_data_network);
    ConnectivityManager cManager =
        (ConnectivityManager) parent.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo networkInfo = cManager.getActiveNetworkInfo();
    if (networkInfo != null) {
      if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
        info = parent.getString(R.string.mobile_data_network);
        TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
          info = parent.getString(R.string.threeg_data_network);
        } else if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_EDGE) {
          info = parent.getString(R.string.edge_data_network);
        }
      } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
        tts.speak(parent.getString(R.string.wifi), 1, null);
        WifiManager wManager = (WifiManager) parent.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wInfo = wManager.getConnectionInfo();
        int wifiSignalStrength = WifiManager.calculateSignalLevel(wInfo.getRssi(), 4);
        info =  wInfo.getSSID() + " " + wifiSignalStrength + " "
                + parent.getString(R.string.bars);
      }
    }
    tts.speak(info, 1, null);
  }

  private void speakVoiceNetworkInfo() {
    TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
    String voiceNetworkOperator = tm.getNetworkOperatorName();
    String voiceNetworkStrength = voiceSignalStrength + " " + parent.getString(R.string.bars);
    String voiceNetworkInfo = voiceNetworkOperator + ", " + voiceNetworkStrength;
    tts.speak(voiceNetworkInfo, 1, null);
  }

  public void announceConnectivity() {
    if (airplaneModeEnabled()) {
      tts.speak(parent.getString(R.string.airplane_mode), 0, null);
      return;
    }

    int btStatus = 0;
    String bluetooth = "";
    String gps = "";
    try {
      ContentResolver cr = parent.getContentResolver();
      if (System.getInt(cr, System.BLUETOOTH_ON) == 1) {
        bluetooth = parent.getString(R.string.bluetooth);
      }
      String locationProviders = System.getString(cr, System.LOCATION_PROVIDERS_ALLOWED);
      if ((locationProviders.length() > 0) && locationProviders.contains("gps")) {
        gps = parent.getString(R.string.gps);
      }

    } catch (SettingNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    tts.stop();
    speakVoiceNetworkInfo();
    speakDataNetworkInfo();
    tts.speak(bluetooth, 1, null);
    tts.speak(gps, 1, null);
  }

  public void announceBattery() {
    BroadcastReceiver battReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        context.unregisterReceiver(this);
        int rawlevel = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", -1);
        String state = intent.getStringExtra("state");
        int status = intent.getIntExtra("status", -1);
        if (rawlevel >= 0 && scale > 0) {
          int batteryLevel = (rawlevel * 100) / scale;
          tts.speak(Integer.toString(batteryLevel), 0, null);
          tts.speak("%", 1, null);
        }
        if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
          tts.speak("[slnc]", 1, null);
          tts.speak(parent.getString(R.string.charging), 1, null);
        }
      }
    };
    IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    parent.registerReceiver(battReceiver, battFilter);
  }

  public void announceDate() {
    Calendar cal = Calendar.getInstance();
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int year = cal.get(Calendar.YEAR);
    SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM");
    String monthStr = monthFormat.format(cal.getTime());
    try {
      boolean canSpeak = speakingTimeLock.tryLock(1000, TimeUnit.MILLISECONDS);
      if (canSpeak) {
        tts.speak("[slnc]", 1, null);
        tts.speak("[slnc]", 1, null);
        tts.speak("[slnc]", 1, null);
        tts.speak(monthStr, 1, null);
        tts.speak("[slnc]", 1, null);
        tts.speak("[slnc]", 1, null);
        tts.speak("[slnc]", 1, null);
        tts.speak(Integer.toString(day), 1, null);
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      speakingTimeLock.unlock();
    }
  }

  public void announceTime() {
    GregorianCalendar cal = new GregorianCalendar();
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int minutes = cal.get(Calendar.MINUTE);
    String ampm = "";
    if (hour == 0) {
      ampm = parent.getString(R.string.midnight);
      hour = 12;
    } else if (hour == 12) {
      ampm = parent.getString(R.string.noon);
    } else if (hour > 12) {
      hour = hour - 12;
      ampm = parent.getString(R.string.pm);
    } else {
      ampm = parent.getString(R.string.am);
    }
    try {
      boolean canSpeak = speakingTimeLock.tryLock(1000, TimeUnit.MILLISECONDS);
      if (canSpeak) {
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
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      speakingTimeLock.unlock();
    }
  }

  public void toggleAirplaneMode() {
    boolean setAirPlaneMode = !airplaneModeEnabled();
    if (!setAirPlaneMode) {
      tts.speak(parent.getString(R.string.disabled), 1, null);
    } else {
      tts.speak(parent.getString(R.string.enabled), 1, null);
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
      // This setting is always there as it is part of the Android
      // framework;
      // therefore, this exception is not reachable and nothing special
      // needs
      // to be done here.
      e.printStackTrace();
    }
    return false;
  }

  public void callVoiceMail() {
    Uri phoneNumberURI = Uri.parse("tel:" + Uri.encode(parent.voiceMailNumber));

    // Uri phoneNumberURI = Uri.parse("tel:" + Uri.encode("18056377243"));
    Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberURI);
    parent.startActivity(intent);
  }

  public void speakLocation() {
    guide.speakLocation();
  }

}
