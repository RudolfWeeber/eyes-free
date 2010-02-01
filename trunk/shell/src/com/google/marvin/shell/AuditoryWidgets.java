
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
import android.provider.Settings.SettingNotFoundException;
import android.speech.RecognizerIntent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.telephony.ServiceState;
import android.util.Log;
import android.provider.Settings.System;

import com.google.tts.TTS;
import com.google.tts.TextToSpeechBeta;

import java.text.SimpleDateFormat;
import java.util.Calendar;

// Most of the logic for determining strength levels is based on the code here:
// http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob_plain;f=services/java/com/android/server/status/StatusBarPolicy.java

public class AuditoryWidgets {
    private TextToSpeechBeta tts;

    private MarvinShell parent;

    private Guide guide;

    private boolean useGpsThisTime;

    private int voiceSignalStrength;
    
    private int callState = TelephonyManager.CALL_STATE_IDLE;

    public AuditoryWidgets(TextToSpeechBeta theTts, MarvinShell shell) {
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
            public void onCallStateChanged(int state, String incomingNumber){
                callState = state;
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTH | PhoneStateListener.LISTEN_SERVICE_STATE | PhoneStateListener.LISTEN_CALL_STATE);
    }

    public void shutdown() {
        if (guide != null) {
            guide.shutdown();
        }
        try {
            TelephonyManager tm = (TelephonyManager) parent
                    .getSystemService(Context.TELEPHONY_SERVICE);
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

    private void speakDataNetworkInfo() {
        String info = parent.getString(R.string.no_data_network);
        ConnectivityManager cManager = (ConnectivityManager) parent
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                info = parent.getString(R.string.mobile_data_network);
                TelephonyManager tm = (TelephonyManager) parent
                        .getSystemService(Context.TELEPHONY_SERVICE);
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
                info = wInfo.getSSID() + " " + wifiSignalStrength + " "
                        + parent.getString(R.string.bars);
            }
        }
        tts.speak(info, 1, null);
    }

    private void speakVoiceNetworkInfo() {
        TelephonyManager tm = (TelephonyManager) parent.getSystemService(Context.TELEPHONY_SERVICE);
        String voiceNetworkOperator = tm.getNetworkOperatorName();
        String voiceNetworkStrength = voiceSignalStrength + " " + parent.getString(R.string.bars);
        if (voiceSignalStrength == -1) {
            voiceNetworkStrength = "";
        }
        String voiceNetworkInfo = voiceNetworkOperator + ", " + voiceNetworkStrength;
        tts.speak(voiceNetworkInfo, 1, null);
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
                tts.speak(message, 0, null);
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

        String timeStr = Integer.toString(hour) + " " + Integer.toString(minutes) + " " + ampm;

        tts.speak(timeStr + " " + monthStr + " " + Integer.toString(day), 0, null);
    }

    public void callVoiceMail() {
        Uri phoneNumberURI = Uri.parse("tel:" + Uri.encode(parent.voiceMailNumber));
        Intent intent = new Intent(Intent.ACTION_CALL, phoneNumberURI);
        parent.startActivity(intent);
    }

    public void speakLocation() {
        guide = new Guide(parent);
        guide.speakLocation(useGpsThisTime);
        useGpsThisTime = !useGpsThisTime;
    }

    public void startAppLauncher() {
        parent.switchToAppLauncherView();
    }

    public void launchVoiceSearch() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        parent.startActivityForResult(intent, MarvinShell.voiceRecoCode);
    }
    
    public int getCallState(){
        return callState;
    }

}
