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

package com.whereabout.common;

import android.os.Environment;

/**
 * Constants for the WiFi tool app
 * 
 * @author chaitanyag@google.com (Chaitanya Gharpure)
 */
public class Constants {
    
    /**
     * The default directory path where the WiFi data is stored.
     */
    public static final String SCANS_DIR = Environment.getExternalStorageDirectory() +
            "/wifiscans";
    
    /**
     * The default directory path where the map files are stored.
     */
    public static final String MAP_DIR = Environment.getExternalStorageDirectory() +
            "/wifiscans/maps";

    public static int GET_LOC_XY_RESULT = 0;

    public static int SAVE_LOC_RESULT = 1;

    public static String KEY_MAP_IMAGE_FILE = "KEY_MAP_IMAGE_FILE";

    public static String KEY_MAP_NAME = "KEY_MAP_NAME";

    public static String KEY_SELECTED_POINT = "KEY_SELECTED_POINT";
    
    public static String KEY_LOC_X = "KEY_LOC_X";
    
    public static String KEY_LOC_Y = "KEY_LOC_Y";
    
    public static String KEY_LOC_ID = "KEY_LOC_ID";
    
    public static String KEY_DESTINATION = "KEY_DESTINATION";
    
    public static String KEY_POINT_IDS = "KEY_POINT_IDS";
}
