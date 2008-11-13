package com.google.tts;

import android.util.Log;
import java.lang.ref.WeakReference;

/**
 * The SpeechSynthesis class provides a high-level api to create and play
 * synthesized speech.
 */
public class SpeechSynthesis
{

    //
    // External API
    //

    /**
     * Constructor; pass a language code such as 0 for English.
     */
    public SpeechSynthesis(int languageCode) {
        native_setup(new WeakReference<SpeechSynthesis>(this), languageCode);
    }

    /**
     * Synthesize speech to a file.  The current implementation writes
     * a valid WAV file to the given path, assuming it is writable.
     * Something like "/sdcard/???.wav" is recommended.
     */
    public native final void synthesizeToFile(String text, String filename);

    //
    // Internal
    //

    static { System.loadLibrary("speechsynthesis"); }

    private final static String TAG = "SpeechSynthesis";

    private int mNativeContext; // accessed by native methods
  
    private native final void native_setup(Object weak_this, int languageCode);

    private native final void native_finalize();

    protected void finalize() { native_finalize(); }    
}
