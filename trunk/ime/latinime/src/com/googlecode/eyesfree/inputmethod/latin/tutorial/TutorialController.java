/*
 * Copyright (C) 2011 Google Inc.
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

package com.googlecode.eyesfree.inputmethod.latin.tutorial;

import android.content.Intent;

/**
 * @author alanv@google.com (Alan Viverette)
 */
public interface TutorialController {
    /** Moves to the next tutorial. */
    public void next();

    /**
     * Skips the specified number of tutorials. Calling skip(0) is equivalent to
     * calling {@link TutorialController#next()}.
     *
     * @param count The number of tutorials to skip.
     */
    public void skip(int count);

    /** Moves to the previous tutorial. */
    public void previous();

    /**
     * Shows the tutorial at the specified index.
     *
     * @param index The index of the tutorial to show.
     */
    public void show(int index);

    /**
     * Returns the current keyboard mode.
     *
     * @return The current keyboard mode.
     * @see com.googlecode.eyesfree.inputmethod.latin.KeyboardSwitcher#getKeyboardMode()
     */
    public int getKeyboardMode();

    /**
     * Requests a current keyboard mode update.
     */
    public void requestKeyboardModeUpdate();

    /**
     * Attempts to set the current keyboard mode.
     *
     * @param mode The desired mode.
     * @see com.googlecode.eyesfree.inputmethod.latin.KeyboardSwitcher#setKeyboardMode(int)
     */
    public void setKeyboardMode(int mode);

    /**
     * Queues text for speaking.
     *
     * @param text The text to speak.
     * @param utteranceId The utterance id for completion callbacks.
     */
    public void speak(String text, int utteranceId);

    /**
     * Runs the specified runnable on the UI thread.
     *
     * @param r The runnable to run.
     */
    public void runOnUiThread(Runnable r);

    /**
     * Starts an activity with the given intent and returns to the tutorial
     * activity's onActivityResult(int, int, Intent) method with the specified
     * request code.
     *
     * @param intent
     * @param requestCode
     * @see android.app.Activity#startActivityForResult(Intent, int)
     */
    public void startActivityForResult(Intent intent, int requestCode);

    /**
     * Closes and exits the tutorial.
     */
    public void finish();
}
