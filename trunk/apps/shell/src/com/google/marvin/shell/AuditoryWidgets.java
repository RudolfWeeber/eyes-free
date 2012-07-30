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

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
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
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

// Most of the logic for determining strength levels is based on the code here:
// http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=services/java/com/android/server/status/StatusBarPolicy.java

/**
 * Collection of one shot speech widgets for the home screen
 * 
 * @author clchen@google.com (Charles L. Chen)
 * @author credo@google.com (Tim Credo)
 */
public class AuditoryWidgets {
    private TextToSpeech tts;

    private MarvinShell parent;

    private Guide guide;

    private boolean useGpsThisTime;

    private int voiceSignalStrength;

    private int callState = TelephonyManager.CALL_STATE_IDLE;

    /**
     * Map user-facing widget descriptions to the strings used to indicate them
     * in XML.
     */
    public HashMap<String, String> descriptionToWidget;

    public AuditoryWidgets(TextToSpeech theTts, MarvinShell shell) {
        tts = theTts;
        parent = shell;
        useGpsThisTime = true;
        voiceSignalStrength = 0;
        TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(new PhoneStateListener() {
            private boolean inService = true;

                @Override
            public void onServiceStateChanged(ServiceState service) {
                if (service.getState() != ServiceState.STATE_IN_SERVICE) {
                    inService = false;
                } else {
                    inService = true;
                }
            }

                @Override
            public void onSignalStrengthChanged(int asu) {
                if ((asu == -1) || !inService) {
                    voiceSignalStrength = -1;
                } else if (asu <= 0 || asu == 99) {
                    voiceSignalStrength = 0;
                } else if (asu >= 16) {
                    voiceSignalStrength = 4;
                } else if (asu >= 8) {
                    voiceSignalStrength = 3;
                } else if (asu >= 4) {
                    voiceSignalStrength = 2;
                } else {
                    voiceSignalStrength = 1;
                }
            }

                @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                callState = state;
            }
        },
                PhoneStateListener.LISTEN_SIGNAL_STRENGTH | PhoneStateListener.LISTEN_SERVICE_STATE
                | PhoneStateListener.LISTEN_CALL_STATE);

        descriptionToWidget = new HashMap<String, String>();
        descriptionToWidget.put(parent.getString(R.string.applications), "APPLAUNCHER");
        descriptionToWidget.put(parent.getString(R.string.autosync_toggle), "TOGGLE_AUTOSYNC");
        descriptionToWidget.put(parent.getString(R.string.battery), "BATTERY");
        descriptionToWidget.put(parent.getString(R.string.bluetooth_toggle), "TOGGLE_BLUETOOTH");
        descriptionToWidget.put(parent.getString(R.string.location), "LOCATION");
        descriptionToWidget.put(parent.getString(R.string.search_widget), "VOICE_SEARCH");
        descriptionToWidget.put(parent.getString(R.string.signal), "CONNECTIVITY");
        descriptionToWidget.put(parent.getString(R.string.time), "TIME_DATE");
        descriptionToWidget.put(parent.getString(R.string.voicemail), "VOICEMAIL");
        descriptionToWidget.put(parent.getString(R.string.wifi_toggle), "TOGGLE_WIFI");
        descriptionToWidget.put(
                parent.getString(R.string.open_notifications), "OPEN_NOTIFICATIONS");
        descriptionToWidget.put(
                parent.getString(R.string.android_launcher), "ANDROID_LAUNCHER");
    }

    public void shutdown() {
        if (guide != null) {
            guide.shutdown();
        }
        try {
            TelephonyManager tm = (TelephonyManager) parent.getSystemService(
                    Context.TELEPHONY_SERVICE);
            tm.listen(new PhoneStateListener() {
            }, PhoneStateListener.LISTEN_NONE);
        } catch (RuntimeException e) {
            // There will be a runtime exception if shutdown is being forced by
            // the
            // user.
            // Ignore the error here as shutdown will be called a second time
            // when the
            // shell is destroyed.
        }
    }

    /**
     * Run the widget code corresponding to the given string.
     */
    public void runWidget(String widgetName) {
        if (widgetName.equals("TIME_DATE")) {
            announceTime();
        } else if (widgetName.equals("BATTERY")) {
            announceBattery();
        } else if (widgetName.equals("VOICEMAIL")) {
            tts.playEarcon(parent.getString(R.string.earcon_tick), TextToSpeech.QUEUE_FLUSH, null);
            callVoiceMail();
        } else if (widgetName.equals("LOCATION")) {
            tts.playEarcon(parent.getString(R.string.earcon_tick), TextToSpeech.QUEUE_FLUSH, null);
            speakLocation();
        } else if (widgetName.equals("CONNECTIVITY")) {
            announceConnectivity();
        } else if (widgetName.equals("APPLAUNCHER")) {
            parent.switchToAppChooserView();
        } else if (widgetName.equals("VOICE_SEARCH")) {
            launchVoiceSearch();
        } else if (widgetName.equals("TOGGLE_BLUETOOTH")) {
            toggleBluetooth();
        } else if (widgetName.equals("TOGGLE_AUTOSYNC")) {
            toggleAutosync();
        } else if (widgetName.equals("TOGGLE_WIFI")) {
            toggleWifi();
        } else if (widgetName.equals("OPEN_NOTIFICATIONS")) {
            openNotifications();
        } else if (widgetName.equals("ANDROID_LAUNCHER")) {
            launchDefaultHomeScreen();
        }
    }

    private void speakDataNetworkInfo() {
        String info = parent.getString(R.string.no_data_network);
        ConnectivityManager cManager = (ConnectivityManager) parent.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                info = parent.getString(R.string.mobile_data_network);
                TelephonyManager tm = (TelephonyManager) parent.getSystemService(
                        Context.TELEPHONY_SERVICE);
                if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
                    info = parent.getString(R.string.threeg_data_network);
                } else if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_EDGE) {
                    info = parent.getString(R.string.edge_data_network);
                }
            } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                WifiManager wManager = (WifiManager) parent.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wInfo = wManager.getConnectionInfo();
                int wifiSignalStrength = WifiManager.calculateSignalLevel(wInfo.getRssi(), 4);
                info = parent.getString(R.string.wifi) + wInfo.getSSID() + " "
                        + parent.getString(R.string.bars, wifiSignalStrength);
            }
        }
        tts.speak(info, TextToSpeech.QUEUE_ADD, null);
    }

    private void speakVoiceNetworkInfo() {
        TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
        String voiceNetworkOperator = tm.getNetworkOperatorName();
        String voiceNetworkStrength = parent.getString(R.string.bars, voiceSignalStrength);
        if (voiceSignalStrength == -1) {
            voiceNetworkStrength = "";
        }
        String voiceNetworkInfo = voiceNetworkOperator + ", " + voiceNetworkStrength;
        tts.speak(voiceNetworkInfo, TextToSpeech.QUEUE_ADD, null);
    }

    public void announceConnectivity() {
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
            e.printStackTrace();
        }

        tts.stop();
        speakVoiceNetworkInfo();
        speakDataNetworkInfo();
        tts.speak(bluetooth, TextToSpeech.QUEUE_ADD, null);
        tts.speak(gps, TextToSpeech.QUEUE_ADD, null);
    }

    public void announceBattery() {
        BroadcastReceiver battReceiver = new BroadcastReceiver() {
                @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                int rawlevel = intent.getIntExtra("level", -1);
                int scale = intent.getIntExtra("scale", -1);
                int status = intent.getIntExtra("status", -1);
                String message = "";
                if (rawlevel >= 0 && scale > 0) {
                    int batteryLevel = (rawlevel * 100) / scale;
                    message = Integer.toString(batteryLevel) + "%";
                    // tts.speak(Integer.toString(batteryLevel), 0, null);
                    // tts.speak("%", 1, null);
                }
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    // parent.tts.playEarcon(TTSEarcon.SILENCE, 1, null);
                    // tts.speak(parent.getString(R.string.charging), 1, null);
                    message = message + " " + parent.getString(R.string.charging);
                }
                tts.speak(message, TextToSpeech.QUEUE_FLUSH, null);
            }
        };
        IntentFilter battFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        parent.registerReceiver(battReceiver, battFilter);
    }

    public void announceTime() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM");
        String monthStr = monthFormat.format(cal.getTime());
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

        String timeStr = Integer.toString(hour) + ":" + Integer.toString(minutes) + " " + ampm;

        tts.speak(timeStr + " " + monthStr + " " + Integer.toString(day), TextToSpeech.QUEUE_FLUSH,
                null);
    }

    public void callVoiceMail() {
        Uri phoneNumberURI = Uri.parse("voicemail:");
        Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberURI);
        parent.startActivity(intent);
    }

    public void speakLocation() {
        guide = new Guide(parent);
        guide.speakLocation(useGpsThisTime);
        useGpsThisTime = !useGpsThisTime;
    }

    public void launchVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        try {
            parent.startActivityForResult(intent, MarvinShell.REQUEST_CODE_VOICE_RECO);
        } catch (ActivityNotFoundException anf) {
            parent.tts.speak(parent.getString(R.string.search_not_available),
                    TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    public void launchDefaultHomeScreen() {
        parent.startActivity(parent.getSystemHomeIntent());
    }

    public int getCallState() {
        return callState;
    }

    /**
     * Toggles the state of the BluetoothAdapter.
     */
    public void toggleBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {
            if (adapter.disable()) {
                parent.tts.speak(parent.getString(R.string.bluetooth_turning_off),
                        TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            if (adapter.enable()) {
                parent.tts.speak(parent.getString(R.string.bluetooth_turning_on),
                        TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    /**
     * Toggles the state of the WifiManager.
     */
    public void toggleWifi() {
        WifiManager manager = (WifiManager) parent.getSystemService(Context.WIFI_SERVICE);
        if (manager.isWifiEnabled()) {
            if (manager.setWifiEnabled(false)) {
                parent.tts.speak(
                        parent.getString(R.string.wifi_off), TextToSpeech.QUEUE_FLUSH, null);
            }
        } else {
            if (manager.setWifiEnabled(true)) {
                parent.tts.speak(
                        parent.getString(R.string.wifi_on), TextToSpeech.QUEUE_FLUSH, null);
            }
        }
    }

    /**
     * Toggles sync settings for apps. that request data in the background.
     */
    public void toggleAutosync() {
        ConnectivityManager manager = (ConnectivityManager) parent.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        boolean backgroundData = manager.getBackgroundDataSetting();
        if (ContentResolver.getMasterSyncAutomatically()) {
            ContentResolver.setMasterSyncAutomatically(false);
            parent.tts.speak(
                    parent.getString(R.string.autosync_off), TextToSpeech.QUEUE_FLUSH, null);
        } else {
            ContentResolver.setMasterSyncAutomatically(true);
            parent.tts.speak(
                    parent.getString(R.string.autosync_on), TextToSpeech.QUEUE_FLUSH, null);
            if (!backgroundData) {
                parent.tts.speak(parent.getString(R.string.background_data_warning),
                        TextToSpeech.QUEUE_ADD, null);
            }
        }
    }

    /**
     * Opens the notifications status bar.
     */
    public void openNotifications() {
        try {
            Object service = parent.getSystemService("statusbar");
            Class<?> statusBarManager = Class.forName("android.app.StatusBarManager");
            Method expand = statusBarManager.getMethod("expand");
            expand.invoke(service);
        } catch (Exception e) {
        }
    }
}
