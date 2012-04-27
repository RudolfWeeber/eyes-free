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

package com.google.android.marvin.talkback.formatter.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.accessibility.AccessibilityEvent;

import com.google.android.marvin.talkback.R;
import com.google.android.marvin.talkback.Utterance;
import com.google.android.marvin.talkback.formatter.EventSpeechRule.AccessibilityEventFormatter;
import com.googlecode.eyesfree.utils.SharedPreferencesUtils;

import java.util.List;

/**
 * Formatter that returns an utterance to announce the incoming call screen.
 *
 * NOTE: This is required for pre-GB compatibility.
 *
 * @author svetoslavganov@google.com (Svetoslav Ganov)
 */
public final class InCallScreenFormatter implements AccessibilityEventFormatter {

    // Indices of the text elements for a in-call-screen event
    private static final int INDEX_UPPER_TITLE = 1;

    private static final int INDEX_PHOTO = 2;

    private static final int INDEX_NAME = 4;

    private static final int INDEX_LABEL = 6;

    private static final int INDEX_SOCIAL_STATUS = 7;

    private static final String SPACE = " ";

    @Override
    public boolean format(AccessibilityEvent event, Context context, Utterance utterance) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean speakCallerId = SharedPreferencesUtils.getBooleanPref(prefs,
                context.getResources(), R.string.pref_caller_id_key, R.bool.pref_caller_id_default);

        if (!speakCallerId) {
            // Don't speak the caller ID screen.
            return true;
        }

        final List<CharSequence> eventText = event.getText();
        final StringBuilder utteranceText = utterance.getText();

        // guard against old version of the phone application
        if (eventText.size() == 1) {
            utteranceText.append(eventText.get(0));
            return true;
        }

        final CharSequence title = eventText.get(INDEX_UPPER_TITLE);
        final CharSequence name = eventText.get(INDEX_NAME);

        if (title != null) {
            utteranceText.append(title);
            utteranceText.append(SPACE);
        }

        if (name == null) {
            return true;
        }

        utteranceText.append(name);
        utteranceText.append(SPACE);

        if (!isPhoneNumber(name.toString())) {
            final CharSequence label = eventText.get(INDEX_LABEL);
            final CharSequence photo = eventText.get(INDEX_PHOTO);
            final CharSequence socialStatus = eventText.get(INDEX_SOCIAL_STATUS);

            if (label != null) {
                utteranceText.append(label);
                utteranceText.append(SPACE);
            }

            if (photo != null) {
                utteranceText.append(photo);
                utteranceText.append(SPACE);
            }

            if (socialStatus != null) {
                utteranceText.append(socialStatus);
                utteranceText.append(SPACE);
            }
        }

        return true;
    }

    /**
     * Returns if a <code>value</code> is a phone number.
     */
    private boolean isPhoneNumber(String value) {
        String valueNoDeshes = value.replaceAll("-", "");
        try {
            Long.parseLong(valueNoDeshes);
            return true;
        } catch (IllegalArgumentException iae) {
            return false;
        }
    }
}