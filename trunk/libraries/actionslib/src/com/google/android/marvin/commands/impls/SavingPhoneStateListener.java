// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.android.marvin.commands.impls;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

/**
 * A phone state listener that saves the latest value to shared preferences.
 *
 * @author clsimon@google.com (Cheryl Simon)
 */
public class SavingPhoneStateListener extends PhoneStateListener {
    /** The shared signal strength in bars. */
    private static int sVoiceLevel = -1;

    private final TelephonyManager mTelephonyManager;

    /** Whether the phone has service. */
    private boolean mInService = true;

    public SavingPhoneStateListener(Context context) {
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    /**
     * @return the most recently set signal strength in bars
     */
    public static int getVoiceLevel() {
        return sVoiceLevel;
    }

    /**
     * Sets the signal strength in bars.
     *
     * @param level The signal strength in bars.
     */
    private static void setVoiceLevel(int level) {
        sVoiceLevel = level;
    }

    @Override
    public void onServiceStateChanged(ServiceState service) {
        if (service.getState() != ServiceState.STATE_IN_SERVICE) {
            mInService = false;
        } else {
            mInService = true;
        }
    }

    @Override
    public void onSignalStrengthsChanged(SignalStrength signalStrength) {
        final int voiceSignalStrength = signalStrengthToBars(signalStrength);

        setVoiceLevel(voiceSignalStrength);
    }

    /**
     * Computes a signal strength in bars from a {@link SignalStrength} object.
     *
     * @param signalStrength The detected signal strength.
     * @return the signal strength in bars
     */
    private int signalStrengthToBars(SignalStrength signalStrength) {
        if (signalStrength.isGsm()) {
            return gsmSignalStrengthToBars(signalStrength.getGsmSignalStrength());
        } else if (mTelephonyManager.getNetworkType() == TelephonyManager.NETWORK_TYPE_CDMA) {
            return cdmaStrengthToBars(signalStrength.getCdmaDbm(), signalStrength.getCdmaEcio());
        } else {
            return evdoStrengthToBars(signalStrength.getEvdoDbm(), signalStrength.getEvdoSnr());
        }
    }

    private int gsmSignalStrengthToBars(int strength) {
        if ((strength == -1) || !mInService) {
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
        if (!mInService) {
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

        if (!mInService) {
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
        if (!mInService) {
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
        if (!mInService) {
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
