/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.google.android.marvin.utils;

public class StringBuilderUtils {
    private static final String DEFAULT_SEPARATOR = " ";

    /**
     * Appends string representations of the specified arguments to a
     * {@link StringBuilder}, creating one if the supplied builder is
     * {@code null}.
     *
     * @param builder An existing {@link StringBuilderUtils}, or {@code null} to
     *            create one.
     * @param args The objects to append to the builder.
     * @return A builder with the specified objects appended.
     */
    public static StringBuilder appendWithSeparator(StringBuilder builder, Object... args) {
        if (builder == null) {
            builder = new StringBuilder();
        }

        for (Object arg : args) {
            if (arg == null) {
                continue;
            }

            final String strArg = String.valueOf(arg);
            if (strArg.length() == 0) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(DEFAULT_SEPARATOR);
            }

            builder.append(strArg);
        }

        return builder;
    }
}
