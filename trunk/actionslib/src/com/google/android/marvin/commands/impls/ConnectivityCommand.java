// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import com.google.android.marvin.actionslib.R;
import com.google.android.marvin.commands.CommandExecutor;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings.SettingNotFoundException;
import android.provider.Settings.System;
import android.telephony.TelephonyManager;

/**
 * A command that provides the current connectivity, including phone, data, gps and bluetooth.
 *
 * Note: This command assumes that someone else (who is persistent) will be listening for changes to
 * the phone state and saving the updates into a shared preference via the
 * {@link SavingPhoneStateListener}.
 *
 * TODO(clsimon): Find a better way to get this async data.
 *
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class ConnectivityCommand implements CommandExecutor {

    @Override
    public String executeCommand(Context context) {
        String bluetooth = "";
        String gps = "";
        try {
            ContentResolver cr = context.getContentResolver();
            if (System.getInt(cr, System.BLUETOOTH_ON) == 1) {
                bluetooth = context.getString(R.string.bluetooth);
            }
            String locationProviders = System.getString(cr, System.LOCATION_PROVIDERS_ALLOWED);
            if ((locationProviders.length() > 0) && locationProviders.contains("gps")) {
                gps = context.getString(R.string.gps);
            }

        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        StringBuilder builder = new StringBuilder();
        builder.append(getVoiceNetworkInfo(context));
        builder.append(getDataNetworkInfo(context));
        builder.append(bluetooth);
        builder.append(gps);
        return builder.toString();
    }


    private String getDataNetworkInfo(Context context) {
        String info = context.getString(R.string.no_data_network);
        ConnectivityManager cManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return context.getString(R.string.no_data_network);
        }
        if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
            info = context.getString(R.string.mobile_data_network);
            TelephonyManager tm = (TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE);
            if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_UMTS) {
                info = context.getString(R.string.threeg_data_network);
            } else if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_EDGE) {
                info = context.getString(R.string.edge_data_network);
            }
        } else if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiManager wManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            WifiInfo wInfo = wManager.getConnectionInfo();
            int wifiSignalStrength = WifiManager.calculateSignalLevel(wInfo.getRssi(), 4);
            info = context.getString(R.string.wifi) + wInfo.getSSID() + " " + wifiSignalStrength
                    + " " + context.getString(R.string.bars);
        }
        return info;
    }

    private String getVoiceNetworkInfo(Context context) {
        TelephonyManager tm =
            (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String voiceNetworkOperator = tm.getNetworkOperatorName();
        // get the voice signal strength from a shared preference that should be updated with the
        // latest.
        int voiceSignalStrength = SavingPhoneStateListener.getVoiceLevel();

        String voiceNetworkStrength = voiceSignalStrength + " " + context.getString(R.string.bars);
        if (voiceSignalStrength == -1) {
            voiceNetworkStrength = "";
        }
        String voiceNetworkInfo = voiceNetworkOperator + ", " + voiceNetworkStrength;
        return voiceNetworkInfo;
    }
}
