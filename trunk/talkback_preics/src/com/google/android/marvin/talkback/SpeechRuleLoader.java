/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.google.android.marvin.talkback;

import com.google.android.marvin.talkback.TalkBackService.InfrastructureStateListener;

import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.Arrays;

/**
 * This class is responsible for loading speech rules.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public class SpeechRuleLoader implements InfrastructureStateListener {

    /**
     * Tag used for logging.
     */
    private static final String LOG_TAG = SpeechRuleLoader.class.getSimpleName();

    /**
     * The {@link SpeechRuleProcessor} that manages speech rules.
     */
    private final SpeechRuleProcessor mSpeechRuleProcessor;

    /**
     * Flag if the device is a phone.
     */
    private final boolean mDeviceIsPhone;

    /**
     * The directory that contains external speech strategies.
     */
    private final File mExternalSpeechStrategyDirectory;

    /**
     * File observer for grabbing external speech strategies.
     */
    private final FileObserver mFileObserver;

    /**
     * Creates a new instance.
     *
     * @param packageName The TalkBack package name.
     * @param speechRuleProcessor The {@link SpeechRuleProcessor} to which to add rules.
     * @param deviceIsPhone Flag if the device is a phone.
     */
    SpeechRuleLoader(String packageName, SpeechRuleProcessor speechRuleProcessor,
            boolean deviceIsPhone) {
        mSpeechRuleProcessor = speechRuleProcessor;
        mDeviceIsPhone = deviceIsPhone;
        mExternalSpeechStrategyDirectory = new File(Environment.getExternalStorageDirectory(),
                "/Android/data/" + packageName + "/speechstrategy");

        final String speechStrategyDirectory = mExternalSpeechStrategyDirectory.toString();
        // Note: FileObserver.MODIFY is fired in both create and modify cases, so
        //       we register only for FileObserver.MODIFY and FileObserver.DELETE
        //       to avoid multiple reloads 
        int flags = FileObserver.DELETE | FileObserver.MODIFY; 
        mFileObserver = new FileObserver(speechStrategyDirectory, flags) {
            @Override
            public void onEvent(int event, String speechStrategyRelativePath) {
                switch (event) {
                    case FileObserver.DELETE:
                        unloadExternalSpeechStrategy(speechStrategyRelativePath);
                        break;
                    case FileObserver.MODIFY:
                        loadExternalSpeechStrategy(speechStrategyRelativePath);
                        break;
                }
            }
        };
    }

    /**
     * Load all speech rules - internal and external.
     */
    public void loadSpeechRules() {
        // add user defined external speech strategies first or if
        // no such create the external speech strategy directory
        if (hasExternalSpeechRulesDirectory()) {
            loadExternalSpeechStrategies();
        } else {
            createExternalSpeechRulesDirectory();
        }

        // add speech strategy for third-party apps; later this may be loaded
        // dynamically from another file
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_thirdparty);

        // add speech strategy for specific built-in Android apps
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_apps);

        if (!mDeviceIsPhone) {
            // add the speech strategy for Google TV; this should always be
            // after the application specific ones but before the generic
            mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy_googletv);
        }

        // add generic speech strategy for views in any app; this should always
        // be last so that the app-specific rules above can override the
        // generic rules
        mSpeechRuleProcessor.addSpeechStrategy(R.raw.speechstrategy);
    }

    /**
     * Creates the external speech rule directory on the SD card if such.
     */
    private void createExternalSpeechRulesDirectory() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            try {
                if (mExternalSpeechStrategyDirectory.mkdirs()) {
                    Log.d(LOG_TAG, "Created external speech rules directory: "
                            + mExternalSpeechStrategyDirectory);
                }
            } catch (SecurityException se) {
                Log.w(LOG_TAG, "Could not create external speech rules directory.", se);
            }
        } else {
            Log.w(LOG_TAG, "Could not create external speech rules directory."
                    + " No external storage.");
        }
    }

    /**
     * @return True if the external speech rule strategy exists.
     */
    private boolean hasExternalSpeechRulesDirectory() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return mExternalSpeechStrategyDirectory.exists();
        }
        return false;
    }

    /**
     * Loads the external speech rules in alphabetical order.
     */
    private void loadExternalSpeechStrategies() {
        String[] speechStrategyPaths = mExternalSpeechStrategyDirectory.list();
        // we load in alphabetical order
        Arrays.sort(speechStrategyPaths);
        for (String speechStrategyPath : speechStrategyPaths) {
            loadExternalSpeechStrategy(speechStrategyPath);
        }
    }

    /**
     * Loads an external speech strategy with the given
     * <code>speechStrategyPath</code>.
     */
    private void loadExternalSpeechStrategy(String speechStrategyRelativePath) {
        File speechStrategyFile = new File(mExternalSpeechStrategyDirectory,
                speechStrategyRelativePath);
        mSpeechRuleProcessor.addSpeechStrategy(speechStrategyFile);
        Log.i(LOG_TAG, "Loaded external speech strategy: " + speechStrategyRelativePath);   
    }

    /**
     * Unloads an external speech strategy with the given
     * <code>speechStrategyPath</code>.
     */
    private void unloadExternalSpeechStrategy(String speechStrategyRelativePath) {
        File speechStrategyFile = new File(mExternalSpeechStrategyDirectory,
                speechStrategyRelativePath);
        mSpeechRuleProcessor.removeSpeechStrategy(speechStrategyFile);
        Log.i(LOG_TAG, "Removed external speech strategy: " + speechStrategyRelativePath);
    }

    @Override
    public void onInfrastructureStateChange(boolean isInitialized) {
        if (isInitialized) {
            mFileObserver.startWatching();
        } else {
            mFileObserver.stopWatching();
        }
    }
}
