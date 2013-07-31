/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech.Engine;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.googlecode.eyesfree.utils.LogUtils;
import com.googlecode.eyesfree.utils.TtsEngineUtils;
import com.googlecode.eyesfree.utils.TtsEngineUtils.TtsEngineInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity used to load the list of available TTS engines and query their
 * available languages.
 * <p>
 * Clients should use {@link TextToSpeechManager} rather than interacting with
 * this activity directly.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class TtsDiscoveryProxyActivity extends Activity {
    /** Broadcast indicating that TTS engine discovery has started. */
    public static final String BROADCAST_TTS_DISCOVERY_STARTED =
            "com.google.android.marvin.talkback.TTS_DISCOVERY_STARTED";

    /**
     * Broadcast indicating that a TTS engine was discovered. Contains the
     * following extras:
     * <ul>
     * <li>{@link #EXTRA_ENGINE_NAME} - The engine's package name
     * <li>{@link #EXTRA_AVAILABLE_LANGUAGES} - The list of available languages
     * for the engine
     * </ul>
     */
    public static final String BROADCAST_TTS_DISCOVERED =
            "com.google.android.marvin.talkback.TTS_DISCOVERED";

    /** Broadcast indicating that TTS engine discovery has finished. */
    public static final String BROADCAST_TTS_DISCOVERY_FINISHED =
            "com.google.android.marvin.talkback.TTS_DISCOVERY_FINISHED";

    /**
     * String extra specifying an engine package name. Returned as an extra with
     * {@link #BROADCAST_TTS_DISCOVERED}. May be added as an extra when starting
     * {@link TtsDiscoveryProxyActivity} to specify a single engine for
     * discovery.
     */
    public static final String EXTRA_ENGINE_NAME = "engineName";

    /**
     * String array list extra specifying the list of available languages for
     * the engine package specified by {@link #EXTRA_ENGINE_NAME}. Returned as
     * an extra with {@link #BROADCAST_TTS_DISCOVERED}.
     */
    public static final String EXTRA_AVAILABLE_LANGUAGES = "availableLanguages";

    /** Request code bits used to tag requests coming from this activity. */
    private static final int REQUEST_CODE_CHECK_TTS_DATA = 0x42;

    /** The list of engines to request language data from. */
    private final List<TtsEngineInfo> mAvailableEngines = new ArrayList<TtsEngineInfo>();

    /** The local broadcast manager, used to send inter-package broadcasts. */
    private LocalBroadcastManager mBroadcastManager;

    /** The number of activity results received by this activity. */
    private int mResultsReceived;

    /** The number of received activity results required before finishing. */
    private int mExpectedResults;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBroadcastManager = LocalBroadcastManager.getInstance(this);

        startAndNotify();
    }

    /**
     * Initialized discovery and sends a broadcast intent.
     */
    private void startAndNotify() {
        LogUtils.log(this, Log.INFO, "Initializing TTS discovery proxy...");

        final Intent broadcastIntent = new Intent(BROADCAST_TTS_DISCOVERY_STARTED);
        mBroadcastManager.sendBroadcast(broadcastIntent);

        if (!handleIntent()) {
            finishAndNotify();
        }
    }

    /**
     * Finishes discovery and sends a broadcast intent. Calls {@link #finish()}
     * to exit the activity.
     */
    private void finishAndNotify() {
        LogUtils.log(this, Log.INFO, "Finished discovering TTS engines");

        final Intent broadcastIntent = new Intent(BROADCAST_TTS_DISCOVERY_FINISHED);
        mBroadcastManager.sendBroadcast(broadcastIntent);

        finish();
    }

    /**
     * Attempts to handle the intent that started the activity.
     *
     * @return {@code true} if the activity should wait for at least one call to
     *         {@link #onActivityResult} before finishing.
     */
    private boolean handleIntent() {
        final Intent intent = getIntent();
        final String engineName = intent.getStringExtra(EXTRA_ENGINE_NAME);
        final TtsEngineInfo engine = TtsEngineUtils.getEngineInfo(this, engineName);
        if (engine != null) {
            mAvailableEngines.add(engine);
        } else {
            mAvailableEngines.addAll(TtsEngineUtils.getEngines(this));
        }

        return queryAvailableEngines();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        final TtsEngineInfo engine = getEngineForRequestCode(requestCode);
        if (engine == null) {
            return;
        }

        LogUtils.log(this, Log.INFO, "Discovered TTS engine: %s", engine.name);

        handleCheckTtsDataResult(engine, data);

        mResultsReceived++;

        // If we've received results from all engines, we're done!
        if (mResultsReceived >= mExpectedResults) {
            finishAndNotify();
        }
    }

    /**
     * Parses a request code and returns the associated engine information.
     *
     * @param requestCode A request code.
     * @return The engine associated with the request code, or {@code null} if
     *         the request code is invalid.
     */
    private TtsEngineInfo getEngineForRequestCode(int requestCode) {
        // Ensure the request code contains our request code bits.
        if ((requestCode & REQUEST_CODE_CHECK_TTS_DATA) != REQUEST_CODE_CHECK_TTS_DATA) {
            return null;
        }

        // Extract the engine index from the request code.
        final int index = (requestCode >>> 8);
        if ((index < 0) || (index >= mAvailableEngines.size())) {
            return null;
        }

        return mAvailableEngines.get(index);
    }

    /**
     * Generates a request code for engine at the specified index.
     *
     * @param index The index of the engine for which to generate a request
     *            code.
     * @return A request code for engine at the specified index.
     */
    private int getRequestCodeForIndex(int index) {
        return ((index << 8) | REQUEST_CODE_CHECK_TTS_DATA);
    }

    /**
     * Handles the result of a TTS data check. Sends a
     * {@link #BROADCAST_TTS_DISCOVERED} broadcast if the returned data is
     * valid, or no-op otherwise.
     *
     * @param engine The engine that returned a data check result.
     * @param data The data associated with the result.
     */
    private void handleCheckTtsDataResult(TtsEngineInfo engine, Intent data) {
        if (data == null) {
            // broadcast failure for engine?
            return;
        }

        final ArrayList<String> availableLangs = data.getStringArrayListExtra(
                Engine.EXTRA_AVAILABLE_VOICES);
        if (availableLangs == null) {
            // broadcast failure for engine?
            return;
        }

        // Send a broadcast to TalkBack containing discovery information.
        final Intent broadcastIntent = new Intent(BROADCAST_TTS_DISCOVERED);
        broadcastIntent.putExtra(EXTRA_ENGINE_NAME, engine.name);
        broadcastIntent.putExtra(EXTRA_AVAILABLE_LANGUAGES, availableLangs);

        mBroadcastManager.sendBroadcast(broadcastIntent);
    }

    /**
     * Queries the engines in {@link #mAvailableEngines} by starting an
     * {@link Engine#ACTION_CHECK_TTS_DATA} activity for each engine using
     * {@link #startActivityForResult}.
     *
     * @return {@code true} if at least one activity was started.
     */
    private boolean queryAvailableEngines() {
        mExpectedResults = mAvailableEngines.size();
        if (mExpectedResults == 0) {
            return false;
        }

        for (int i = 0; i < mAvailableEngines.size(); i++) {
            final TtsEngineInfo engine = mAvailableEngines.get(i);
            final Intent checkTtsDataIntent = new Intent(Engine.ACTION_CHECK_TTS_DATA);
            checkTtsDataIntent.setPackage(engine.name);

            // Use the engine index as the request code so that we can associate
            // activity results with their respective engines.
            final int requestCode = getRequestCodeForIndex(i);
            startActivityForResult(checkTtsDataIntent, requestCode);
        }

        return true;
    }
}
