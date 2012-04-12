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

import com.google.android.marvin.utils.InfrastructureStateListener;
import com.googlecode.eyesfree.utils.LogUtils;

/**
 * {@link BroadcastReceiver} for detecting incoming calls.
 */
class CallStateMonitor extends BroadcastReceiver implements InfrastructureStateListener {
    private static final IntentFilter STATE_CHANGED_FILTER = new IntentFilter(
            TelephonyManager.ACTION_PHONE_STATE_CHANGED);

    private final SpeechController mSpeechController;
    private final PreferenceFeedbackController mFeedbackController;

    /** Handler to transfer broadcasts to the service thread. */
    private final BroadcastHandler mHandler = new BroadcastHandler() {
        @Override
        public void handleOnReceive(Intent intent) {
            internalOnReceive(intent);
        }
    };

    /** Whether the infrastructure has been initialized. */
    private boolean mInfrastructureInitialized;

    public CallStateMonitor(SpeechController speechController,
            PreferenceFeedbackController feedbackController) {
        mSpeechController = speechController;
        mFeedbackController = feedbackController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        mHandler.onReceive(intent);
    }

    private void internalOnReceive(Intent intent) {
        if (!mInfrastructureInitialized) {
            LogUtils.log(CallStateMonitor.class, Log.WARN, "Service not initialized during "
                    + "broadcast.");
            return;
        }

        final String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            mSpeechController.interrupt();
            mFeedbackController.interrupt();
        }
    }

    @Override
    public void onInfrastructureStateChange(Context context, boolean isInitialized) {
        mInfrastructureInitialized = isInitialized;
    }

    public IntentFilter getFilter() {
        return STATE_CHANGED_FILTER;
    }
}
