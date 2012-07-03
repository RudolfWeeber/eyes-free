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
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.google.android.marvin.talkback.SpeechController.QueuingMode;
import com.google.android.marvin.utils.WeakReferenceHandler;

/**
 * Watches changes in device orientation.
 */
public class OrientationMonitor {
    private final Context mContext;
    private final SpeechController mSpeechController;
    private final Display mDefaultDisplay;

    private int mLastRotation;

    @SuppressWarnings("deprecation")
    public OrientationMonitor(Context context, SpeechController speechController) {
        mContext = context;
        mSpeechController = speechController;

        final WindowManager windowManager = (WindowManager) mContext.getSystemService(
                Context.WINDOW_SERVICE);
        mDefaultDisplay = windowManager.getDefaultDisplay();
        mLastRotation = mDefaultDisplay.getOrientation();
    }

    public void shutdown() {
        // Do nothing.
    }

    public void onConfigurationChanged(Configuration newConfig) {
        mHandler.startAnnounceTimeout();
    }

    @SuppressWarnings("deprecation")
    private void announceCurrentRotation() {
        final int rotation = mDefaultDisplay.getOrientation();

        if (rotation == mLastRotation) {
            return;
        }

        mLastRotation = rotation;

        final int resId = getDescriptionForRotation(rotation);

        if (resId > 0) {
            mSpeechController.cleanUpAndSpeak(mContext.getString(resId), QueuingMode.QUEUE, null);
        }
    }

    private static int getDescriptionForRotation(int rotation) {
        switch (rotation) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                return R.string.orientation_portrait;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                return R.string.orientation_landscape;
            default:
                return 0;
        }
    }

    private final OrientationHandler mHandler = new OrientationHandler(this);

    private static class OrientationHandler extends WeakReferenceHandler<OrientationMonitor> {
        private static final int ANNOUNCE_ROTATION = 1;

        private static final long ANNOUNCE_ROTATION_DELAY = 250;

        public OrientationHandler(OrientationMonitor parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, OrientationMonitor parent) {
            switch (msg.what) {
                case ANNOUNCE_ROTATION:
                    parent.announceCurrentRotation();
                    break;
            }
        }

        public void startAnnounceTimeout() {
            removeMessages(ANNOUNCE_ROTATION);
            sendEmptyMessageDelayed(ANNOUNCE_ROTATION, ANNOUNCE_ROTATION_DELAY);
        }
    }
}
