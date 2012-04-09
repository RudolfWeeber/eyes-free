/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.utils;

/**
 * A collection of color processing methods and constants.
 */
public class ColorUtils {
    /**
     * Matrix used to invert hue and value.
     *
     * @see android.graphics.ColorMatrix
     */
    public static final float[] MATRIX_INVERT = new float[] {
       -1,  0,  0, 0, 255,
        0, -1,  0, 0, 255,
        0,  0, -1, 0, 255,
        0,  0,  0, 1,   0
    };

    /**
     * Matrix used to invert hue.
     *
     * @see android.graphics.ColorMatrix
     */
    public static final float[] MATRIX_INVERT_COLOR = new float[] {
           0, 0.5f,  0.5f, 0, 0,
        0.5f,    0,  0.5f, 0, 0,
        0.5f, 0.5f,     0, 0, 0,
           0,    0,     0, 1, 0
    };

    /**
     * Matrix used to increase contrast.
     *
     * @see android.graphics.ColorMatrix
     */
    public static final float[] MATRIX_HIGH_CONTRAST = new float[] {
        3, 0, 0, 0, 0,
        0, 3, 0, 0, 0,
        0, 0, 3, 0, 0,
        0, 0, 0, 1, 0
    };
}
