/*
 * Copyright (C) 2012 Google Inc.
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

package com.googlecode.eyesfree.compat.view;

import android.view.WindowManager.LayoutParams;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Field;

public class WindowManagerCompatUtils {
    public static class LayoutParamsCompatUtils {
        private static final Class<?> CLASS_LayoutParams = LayoutParams.class;
        private static final Field FIELD_buttonBrightness = CompatUtils.getField(
                CLASS_LayoutParams, "buttonBrightness");

        /**
         * This can be used to override the standard behavior of the button and
         * keyboard backlights. A value of less than 0, the default, means to
         * use the standard backlight behavior. 0 to 1 adjusts the brightness
         * from dark to full bright.
         */
        public static void setButtonBrightness(LayoutParams receiver, float buttonBrightness) {
            CompatUtils.setFieldValue(receiver, FIELD_buttonBrightness, buttonBrightness);
        }
    }
}
