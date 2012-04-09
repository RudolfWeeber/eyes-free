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

package com.googlecode.eyesfree.compat.speech.tts;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Constructor;
import java.util.HashMap;

public class TextToSpeechCompatUtils {
    private static final Constructor<?> CONSTRUCTOR_LLS = CompatUtils.getConstructor(
            TextToSpeech.class, Context.class, TextToSpeech.OnInitListener.class, String.class);

    private TextToSpeechCompatUtils() {
        // This class is non-instantiable.
    }

    public static TextToSpeech newTextToSpeech(Context context,
            TextToSpeech.OnInitListener listener, String engine) {
        final TextToSpeech result = (TextToSpeech) CompatUtils.newInstance(CONSTRUCTOR_LLS,
                context, listener, engine);

        if (result != null) {
            return result;
        }

        return new TextToSpeech(context, listener);
    }
    
    public static class EngineCompatUtils {
        /**
         * Intent for starting a TTS service. Services that handle this intent must
         * extend TextToSpeechService. Normal applications should not use this
         * intent directly, instead they should talk to the TTS service using the
         * the methods in this class.
         */
        public static final String INTENT_ACTION_TTS_SERVICE =
                "android.intent.action.TTS_SERVICE";

        /**
         * Parameter key to specify the speech volume relative to the current stream
         * type volume used when speaking text. Volume is specified as a float
         * ranging from 0 to 1 where 0 is silence, and 1 is the maximum volume (the
         * default behavior).
         * 
         * @see TextToSpeech#speak(String, int, HashMap)
         * @see TextToSpeech#playEarcon(String, int, HashMap)
         */
        public static final String KEY_PARAM_VOLUME = "volume";

        /**
         * Parameter key to specify how the speech is panned from left to right when
         * speaking text. Pan is specified as a float ranging from -1 to +1 where -1
         * maps to a hard-left pan, 0 to center (the default behavior), and +1 to
         * hard-right.
         * 
         * @see TextToSpeech#speak(String, int, HashMap)
         * @see TextToSpeech#playEarcon(String, int, HashMap)
         */
        public static final String KEY_PARAM_PAN = "pan";

        private EngineCompatUtils() {
            // This class is non-instantiable.
        }
    }
}
