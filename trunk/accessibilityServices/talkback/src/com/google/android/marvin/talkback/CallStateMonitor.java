/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.googlecode.eyesfree.utils.InfrastructureStateListener;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * {@link BroadcastReceiver} for detecting incoming calls.
 */
class CallStateMonitor extends BroadcastReceiver implements InfrastructureStateListener {
    private static final IntentFilter STATE_CHANGED_FILTER = new IntentFilter(
            TelephonyManager.ACTION_PHONE_STATE_CHANGED);

    private final TalkBackService mService;
    private final TelephonyManager mTelephonyManager;

    /** Handler to transfer broadcasts to the service thread. */
    private final CallStateHandler mHandler = new CallStateHandler(this);

    /** Whether the infrastructure has been initialized. */
    private boolean mInfrastructureInitialized;

    public CallStateMonitor(TalkBackService context) {
        mService = context;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mHandler.onReceive(intent);
    }

    private void internalOnReceive(Intent intent) {
        final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (!mInfrastructureInitialized) {
            LogUtils.log(CallStateMonitor.class, Log.WARN, "Service not initialized during "
                    + "broadcast.");
            return;
        }

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            mService.interruptAllFeedback();
        }
    }

    @Override
    public void onInfrastructureStateChange(Context context, boolean isInitialized) {
        mInfrastructureInitialized = isInitialized;
    }

    public IntentFilter getFilter() {
        return STATE_CHANGED_FILTER;
    }

    /**
     * Returns the current device call state
     *
     * @return One of the call state constants from {@link TelephonyManager}.
     */
    public int getCurrentCallState() {
        return mTelephonyManager.getCallState();
    }

    private static class CallStateHandler extends BroadcastHandler<CallStateMonitor> {
        public CallStateHandler(CallStateMonitor parent) {
            super(parent);
        }

        @Override
        public void handleOnReceive(Intent intent, CallStateMonitor parent) {
            parent.internalOnReceive(intent);
        }
    }
}
