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

package com.googlecode.eyesfree.inputmethod.voice;

import android.content.Context;

/**
 * Provides the logging facility for voice input events. This fires broadcasts back to
 * the voice search app which then logs on our behalf.
 *
 * Note that debug console logging does not occur in this class. If you want to
 * see console output of these logging events, there is a boolean switch to turn
 * on on the VoiceSearch side.
 */
public class VoiceInputLogger {
    private static VoiceInputLogger sVoiceInputLogger;

    // This flag is used to indicate when there are voice events that
    // need to be flushed.
    private boolean mHasLoggingInfo = false;

    /**
     * Returns the singleton of the logger.
     *
     * @param contextHint a hint context used when creating the logger instance.
     * Ignored if the singleton instance already exists.
     */
    public static synchronized VoiceInputLogger getLogger(Context contextHint) {
        if (sVoiceInputLogger == null) {
            sVoiceInputLogger = new VoiceInputLogger(contextHint);
        }
        return sVoiceInputLogger;
    }

    public VoiceInputLogger(Context context) {
    }

    public void flush() {
        if (hasLoggingInfo()) {
            setHasLoggingInfo(false);
        }
    }
    
    public void keyboardWarningDialogShown() {
        setHasLoggingInfo(true);
    }
    
    public void keyboardWarningDialogDismissed() {
        setHasLoggingInfo(true);
    }

    public void keyboardWarningDialogOk() {
        setHasLoggingInfo(true);
    }

    public void keyboardWarningDialogCancel() {
        setHasLoggingInfo(true);
    }

    public void settingsWarningDialogShown() {
        setHasLoggingInfo(true);
    }
    
    public void settingsWarningDialogDismissed() {
        setHasLoggingInfo(true);
    }

    public void settingsWarningDialogOk() {
        setHasLoggingInfo(true);
    }

    public void settingsWarningDialogCancel() {
        setHasLoggingInfo(true);
    }
    
    public void swipeHintDisplayed() {
        setHasLoggingInfo(true);
    }
    
    public void cancelDuringListening() {
        setHasLoggingInfo(true);
    }

    public void cancelDuringWorking() {
        setHasLoggingInfo(true);
    }

    public void cancelDuringError() {
        setHasLoggingInfo(true);
    }
    
    public void punctuationHintDisplayed() {
        setHasLoggingInfo(true);
    }
    
    public void error(int code) {
        setHasLoggingInfo(true);
    }

    public void start(String locale, boolean swipe) {
        setHasLoggingInfo(true);
    }
    
    public void voiceInputDelivered(int length) {
        setHasLoggingInfo(true);
    }

    public void textModifiedByTypingInsertion(int length) {
        setHasLoggingInfo(true);
    }

    public void textModifiedByTypingInsertionPunctuation(int length) {
        setHasLoggingInfo(true);
    }

    public void textModifiedByTypingDeletion(int length) {
        setHasLoggingInfo(true);
    }

    public void textModifiedByChooseSuggestion(int suggestionLength, int replacedPhraseLength,
                                               int index, String before, String after) {
        setHasLoggingInfo(true);
    }

    public void inputEnded() {
        setHasLoggingInfo(true);
    }
    
    public void voiceInputSettingEnabled() {
        setHasLoggingInfo(true);
    }
    
    public void voiceInputSettingDisabled() {
        setHasLoggingInfo(true);
    }

    private void setHasLoggingInfo(boolean hasLoggingInfo) {
        mHasLoggingInfo = hasLoggingInfo;
    }

    private boolean hasLoggingInfo(){
        return mHasLoggingInfo;
    }

}
