/*
 * Copyright (C) 2008 Google Inc.
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
package com.google.marvin.espeak;

import android.util.Log;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;


/**
 * The SpeechSynthesis class provides a high-level api to create and play
 * synthesized speech.
 * 
 * @author dmazzoni@gmail.com (Dominic Mazzoni)
 */
@SuppressWarnings("unused")
public class SpeechSynthesis {
	private SynthReadyCallback cb;
	
	public interface SynthReadyCallback{
		void onSynthReadyListener(short[] audioData);
	}
	
	//
	// External API
	//

	/**
	 * Constructor; pass a language code such as "en" for English.
	 */
	public SpeechSynthesis(SynthReadyCallback callback, String language,
			int languageVariant, int speechRate) {
		cb = callback;
		native_setup(new WeakReference<SpeechSynthesis>(this), language,
				languageVariant, speechRate);
	}

	/**
	 * Synthesize speech; results will be passed back to the TTS API using a
	 * callback.
	 */
	public native final void synth(String text);

	/**
	 * Stops synthesis
	 */
	public native final void stop();

	/**
	 * Sets the language
	 */
	public native final void setLanguage(String language, int languageVariant);

	/**
	 * Sets the speech rate
	 */
	public native final void setSpeechRate(int speechRate);

	//
	// Internal
	//

	static {
		System.loadLibrary("espeakengine");
	}

	private final static String TAG = "SpeechSynthesis";

	private int mNativeContext; // accessed by native methods

	private native final void native_setup(Object weak_this, String language,
			int languageVariant, int speechRate);

	private native final void native_finalize();

	protected void finalize() {
		native_finalize();
	}

	/**
	 * Callback from the C layer
	 */
	@SuppressWarnings("unused")
	private static void postNativeSpeechSynthesizedInJava(Object tts_ref,
			short[] audioData) {
		SpeechSynthesis nativeTTS = (SpeechSynthesis) ((WeakReference) tts_ref)
				.get();
		nativeTTS.cb.onSynthReadyListener(audioData);
	}
}
