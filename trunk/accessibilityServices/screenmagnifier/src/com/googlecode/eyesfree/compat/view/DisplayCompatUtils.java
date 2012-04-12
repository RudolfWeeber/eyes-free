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

package com.googlecode.eyesfree.compat.view;

import android.util.DisplayMetrics;
import android.view.Display;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

public class DisplayCompatUtils {
    private static final Class<?> CLASS_Display = Display.class;
    private static final Method METHOD_getRealMetrics = CompatUtils.getMethod(CLASS_Display,
            "getRealMetrics");

    private DisplayCompatUtils() {
        // This class is non-instantiable.
    }

    /**
     * Gets display metrics based on the real size of this display.
     */
    public static void getRealMetrics(Display receiver, DisplayMetrics outMetrics) {
        CompatUtils.invoke(receiver, null, METHOD_getRealMetrics, outMetrics);
    }
}
