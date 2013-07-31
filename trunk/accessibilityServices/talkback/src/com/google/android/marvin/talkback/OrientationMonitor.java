/*
 * Copyright (C) 2012 Google Inc.
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

import android.content.Context;
import android.content.res.Configuration;
import android.os.Message;
import android.os.PowerManager;

import com.googlecode.eyesfree.utils.WeakReferenceHandler;

/**
 * Watches changes in device orientation.
 */
public class OrientationMonitor {
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final PowerManager mPowerManager;

    /** The orientation of the most recently received configuration. */
    private int mLastOrientation;

    public OrientationMonitor(TalkBackService context) {
        mContext = context;
        mSpeechController = context.getSpeechController();
        mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    /**
     * Called by {@link TalkBackService} when the configuration changes.
     *
     * @param newConfig The new configuration.
     */
    public void onConfigurationChanged(Configuration newConfig) {
        final int orientation = newConfig.orientation;
        if (orientation == mLastOrientation) {
            return;
        }

        mLastOrientation = orientation;
        mHandler.startAnnounceTimeout(orientation);
    }

    private void announceCurrentRotation(int orientation) {
        if (!mPowerManager.isScreenOn()) {
            // Don't announce rotation when the screen is off.
            return;
        }

        final int resId;

        switch (orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                resId = R.string.orientation_portrait;
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                resId = R.string.orientation_landscape;
                break;
            default:
                return;
        }

        mSpeechController.speak(mContext.getString(resId),
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    private final OrientationHandler mHandler = new OrientationHandler(this);

    private static class OrientationHandler extends WeakReferenceHandler<OrientationMonitor> {
        private static final int ANNOUNCE_ROTATION = 1;

        /** The delay in milliseconds before announcing rotation changes. */
        private static final long DELAY_ANNOUNCE_ROTATION = 250;

        public OrientationHandler(OrientationMonitor parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, OrientationMonitor parent) {
            switch (msg.what) {
                case ANNOUNCE_ROTATION:
                    parent.announceCurrentRotation(msg.arg1);
                    break;
            }
        }

        public void startAnnounceTimeout(int orientation) {
            removeMessages(ANNOUNCE_ROTATION);

            final Message msg = obtainMessage(ANNOUNCE_ROTATION, orientation, 0);
            sendMessageDelayed(msg, DELAY_ANNOUNCE_ROTATION);
        }
    }
}
