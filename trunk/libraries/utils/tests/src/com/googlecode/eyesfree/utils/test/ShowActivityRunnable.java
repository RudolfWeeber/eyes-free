/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.googlecode.eyesfree.utils.test;

import android.app.Activity;
import android.view.Window;
import android.view.WindowManager;

/**
 * Runnable for forcing the screen on and dismissing the keyboard.
 */
/* package */class ShowActivityRunnable implements Runnable {
    private final Activity mActivity;

    public ShowActivityRunnable(Activity activity) {
        mActivity = activity;
    }

    @Override
    public void run() {
        final Window window = mActivity.getWindow();
        if (window == null) {
            throw new RuntimeException("Missing activity window");
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }
}
