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

package com.googlecode.eyesfree.compat.media;

import android.os.Build;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

public class AudioSystemCompatUtils {
    private static final Class<?> CLASS_AudioSystem = CompatUtils.getClass(
            "android.media.AudioSystem");
    private static final Method METHOD_isStreamActive = CompatUtils.getMethod(
            CLASS_AudioSystem, "isStreamActive", int.class, int.class);
    private static final Method METHOD_LEGACY_isStreamActive = CompatUtils.getMethod(
            CLASS_AudioSystem, "isStreamActive", int.class);

    /**
     * Calls into AudioSystem to check the current status of audio streams.
     * <p>
     * The inPastMs value is only used by the system on API 11+.
     * @param stream The stream ID to query
     * @param inPastMs The time interval allowance, in milliseconds, for
     *            checking active streams
     * @return {@code true} if the stream is or was open in the past
     *         {@code inPastMs} milliseconds, {@code false} otherwise
     */
    public static boolean isStreamActive(int stream, int inPastMs) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return (Boolean) CompatUtils.invoke(
                    null, false, METHOD_isStreamActive, stream, inPastMs);
        } else {
            return (Boolean) CompatUtils.invoke(null, false, METHOD_LEGACY_isStreamActive, stream);
        }
    }
}
