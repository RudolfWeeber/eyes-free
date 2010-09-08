/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;

import java.util.List;

/**
 * This class contains utility methods.
 * 
 * @author svetoslavganov@google.com (Svetoslav R. Ganov)
 */
public class Utils {

    /**
     * @return The name of the current {@link android.app.Activity} given
     *         through the current <code>context</code>.
     */
    public static String getCurrentActivityName(Context context) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Service.ACTIVITY_SERVICE);
        List<ActivityManager.RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            return tasks.get(0).topActivity.getClassName();
        }
        return null;
    }
}
