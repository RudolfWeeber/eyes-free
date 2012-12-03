
package com.googlecode.eyesfree.compat.speech;

import android.content.Context;

import com.googlecode.eyesfree.compat.CompatUtils;

import java.lang.reflect.Method;

public class SpeechRecognizerCompatUtils {
    private static final Class<?> CLASS_SpeechRecognizer = CompatUtils.getClass(
            "android.speech.SpeechRecognizer");
    private static final Method METHOD_isRecognitionAvailable = CompatUtils.getMethod(
            CLASS_SpeechRecognizer, "isRecognitionAvailable", Context.class);

    /**
     * Checks whether a speech recognition service is available on the system.
     * If this method returns {@code false},
     * {@link android.speech.SpeechRecognizer#createSpeechRecognizer(Context)}
     * will fail.
     *
     * @param context with which {@code SpeechRecognizer} will be created
     * @return {@code true} if recognition is available, {@code false} otherwise
     */
    public static boolean isRecognitionAvailable(Context context) {
        return (Boolean) CompatUtils.invoke(null, false, METHOD_isRecognitionAvailable, context);
    }
}
