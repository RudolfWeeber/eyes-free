// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import android.content.Context;
import android.content.SharedPreferences;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * A phone state listener that saves the latest value to shared preferences.
 * 
 * @author clsimon@google.com (Cheryl Simon)
 *
 */
public class SavingPhoneStateListener extends PhoneStateListener {
    public static final String PREFERENCES_NAME = "phoneStatePreferences";
    public static final String VOICE_LEVEL = "voice";
    
    private boolean inService = true;
    private Context context;
    
    public SavingPhoneStateListener(Context context) {
        this.context = context;
    }

    @Override
    public void onServiceStateChanged(ServiceState service) {
        if (service.getState() != ServiceState.STATE_IN_SERVICE) {
            inService = false;
        } else {
            inService = true;
        }
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        StringBuilder builder = new StringBuilder().append("signal strength ");
        int voiceSignalStrength;
        TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        
        if (signalStrength.isGsm()) {
            voiceSignalStrength = gsmSignalStrengthToBars(signalStrength.getGsmSignalStrength());
            builder.append("is gsm ")
                .append(signalStrength.getGsmSignalStrength());
        } else if (tm.getNetworkType() == TelephonyManager.NETWORK_TYPE_CDMA) {
            voiceSignalStrength = cdmaStrengthToBars(signalStrength.getCdmaDbm(), signalStrength.getCdmaEcio());
            builder.append("CDMA ")
                .append(signalStrength.getCdmaDbm());
        } else {
            voiceSignalStrength = evdoStrengthToBars(signalStrength.getEvdoDbm(), signalStrength.getEvdoSnr());
            builder.append(" Evdo ")
            .append(signalStrength.getEvdoDbm());
        }
        SharedPreferences settings = context.getSharedPreferences(PREFERENCES_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(VOICE_LEVEL, voiceSignalStrength);
        editor.commit();
        builder.append(" ")
            .append(voiceSignalStrength)
            .append("bars");

        Log.d("SavingPhoneStateListener", builder.toString());
    }
    
    private int gsmSignalStrengthToBars(int strength) {
        if ((strength == -1) || !inService) {
            return -1;
        } else if (strength <= 0 || strength == 99) {
            return 0;
        } else if (strength >= 16) {
            return 4;
        } else if (strength >= 8) {
            return 3;
        } else if (strength >= 4) {
            return 2;
        } else {
            return 1;
        }
    }
    
    private int cdmaStrengthToBars(int dbm, int ecio) {
        int dbmLevel;
        if (!inService) {
            dbmLevel = -1;
        } else if (dbm >= -75) {
            dbmLevel = 4;
        } else if (dbm >= -85) {
            dbmLevel = 3;
        } else if (dbm >= -95) {
            dbmLevel = 2;
        } else if (dbm >= -100) {
            dbmLevel = 1;
        } else {
            dbmLevel = -1;
        }
        
        int ecioLevel;
        
        if (!inService) {
            ecioLevel = -1;
        } else if (ecio >= -90) {
            ecioLevel = 4;
        } else if (ecio >= -110) {
            ecioLevel = 3;
        } else if (ecio >= -130) {
            ecioLevel = 2;
        } else if (ecio >= -150) {
            ecioLevel = 1;
        } else {
            ecioLevel = -1;
        }
        
        return dbmLevel < ecioLevel ? dbmLevel : ecioLevel;
    }
    
    private int evdoStrengthToBars(int dbm, int snr) {
        int dbmLevel;
        if (!inService) {
            dbmLevel = -1;
        } else if (dbm >= -65) {
            dbmLevel = 4;
        } else if (dbm >= -75) {
            dbmLevel = 3;
        } else if (dbm >= -90) {
            dbmLevel = 2;
        } else if (dbm >= -105) {
            dbmLevel = 1;
        } else {
            dbmLevel = -1;
        }
        
        int snrLevel;
        if (!inService) {
            snrLevel = -1;
        } else if (snr >= 7) {
            snrLevel = 4;
        } else if (snr >= 5) {
            snrLevel = 3;
        } else if (snr >= 3) {
            snrLevel = 2;
        } else if (snr >= 1) {
            snrLevel = 1;
        } else {
            snrLevel = -1;
        }
        
        return dbmLevel < snrLevel ? dbmLevel : snrLevel;
    }
    
}
